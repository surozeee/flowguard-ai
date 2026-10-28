from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    kafka_bootstrap_servers: str = Field(default="localhost:9092", validation_alias="KAFKA_BOOTSTRAP_SERVERS")
    redis_url: str = Field(default="redis://localhost:6379/0", validation_alias="REDIS_URL")
    topic_investigations: str = "flowguard.investigations"
    topic_experiments: str = "flowguard.experiments"
    topic_evidence: str = "flowguard.evidence"
    topic_evidence_processed: str = "flowguard.evidence.processed"
    log_level: str = Field(default="INFO", validation_alias="LOG_LEVEL")


settings = Settings()
