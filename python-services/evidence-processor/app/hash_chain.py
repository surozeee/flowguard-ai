import hashlib
import json
from typing import Any


def canonical_json(payload: dict[str, Any]) -> str:
    return json.dumps(payload, sort_keys=True, separators=(",", ":"), default=str)


def sha256_hex(prefix: str, prev_hash: str, body: dict[str, Any]) -> str:
    material = f"{prefix}|{prev_hash}|{canonical_json(body)}".encode("utf-8")
    return "sha256:" + hashlib.sha256(material).hexdigest()
