import asyncio
import json
import logging
from contextlib import asynccontextmanager
from typing import Any

import redis.asyncio as redis
from aiokafka import AIOKafkaConsumer, AIOKafkaProducer
from fastapi import FastAPI

from app.config import settings
from app.hash_chain import sha256_hex

from app.logging_config import configure_logging

configure_logging(settings.log_level)
log = logging.getLogger("evidence-processor")

redis_client: redis.Redis | None = None
consumer_task: asyncio.Task | None = None


async def process_evidence(message: dict[str, Any], producer: AIOKafkaProducer) -> None:
    assert redis_client is not None
    experiment_id = str(message.get("experimentId", ""))
    investigation_id = str(message.get("investigationId", ""))
    prev_key = f"chain:prev:{investigation_id}"
    prev_hash = await redis_client.get(prev_key)
    prev = prev_hash.decode() if prev_hash else "sha256:genesis"

    record_hash = sha256_hex("evidence", prev, message)
    manifest_hash = sha256_hex(
        "manifest",
        record_hash,
        {
            "experimentId": experiment_id,
            "verdict": message.get("verdict"),
            "oracleVersion": message.get("oracleVersion"),
        },
    )

    enriched = {
        **message,
        "prevHash": prev,
        "recordHash": record_hash,
        "manifestHash": manifest_hash,
    }

    await redis_client.set(prev_key, record_hash)
    await redis_client.set(f"evidence:processed:{experiment_id}", json.dumps(enriched))
    await redis_client.publish(f"investigation:{investigation_id}", json.dumps(enriched))

    await producer.send_and_wait(
        settings.topic_evidence_processed,
        json.dumps(enriched).encode("utf-8"),
    )
    log.info("Processed evidence experimentId=%s verdict=%s", experiment_id, message.get("verdict"))


async def kafka_loop() -> None:
    assert redis_client is not None
    producer = AIOKafkaProducer(bootstrap_servers=settings.kafka_bootstrap_servers)
    await producer.start()
    consumer = AIOKafkaConsumer(
        settings.topic_evidence,
        bootstrap_servers=settings.kafka_bootstrap_servers,
        group_id="flowguard-evidence-processor",
        auto_offset_reset="earliest",
    )
    await consumer.start()
    log.info("Kafka consumer started on %s", settings.topic_evidence)
    try:
        async for msg in consumer:
            try:
                payload = json.loads(msg.value.decode("utf-8"))
                await process_evidence(payload, producer)
            except Exception:
                log.exception("Failed to process Kafka message")
    finally:
        await consumer.stop()
        await producer.stop()


@asynccontextmanager
async def lifespan(_: FastAPI):
    global redis_client, consumer_task
    redis_client = redis.from_url(settings.redis_url, decode_responses=False)
    consumer_task = asyncio.create_task(kafka_loop())
    yield
    if consumer_task:
        consumer_task.cancel()
        try:
            await consumer_task
        except asyncio.CancelledError:
            pass
    if redis_client:
        await redis_client.aclose()


app = FastAPI(title="FlowGuard Evidence Processor", version="0.1.0", lifespan=lifespan)


@app.get("/health")
async def health() -> dict[str, object]:
    assert redis_client is not None
    await redis_client.ping()
    kafka_status = "UP" if consumer_task and not consumer_task.done() else "STARTING"
    return {
        "status": "UP",
        "service": "evidence-processor",
        "dependencies": {"redis": "UP", "kafkaConsumer": kafka_status},
    }


@app.get("/evidence/{experiment_id}")
async def get_evidence(experiment_id: str) -> dict[str, Any]:
    assert redis_client is not None
    raw = await redis_client.get(f"evidence:processed:{experiment_id}")
    if not raw:
        return {"status": "NOT_FOUND", "experimentId": experiment_id}
    return json.loads(raw)
