from app.hash_chain import canonical_json, sha256_hex


def test_canonical_json_is_stable():
    payload = {"b": 2, "a": 1}
    assert canonical_json(payload) == '{"a":1,"b":2}'


def test_sha256_hex_chains_prefix_and_previous():
    body = {"event": "EVIDENCE", "seq": 1}
    first = sha256_hex("evidence", "genesis", body)
    second = sha256_hex("evidence", first, {"event": "EVIDENCE", "seq": 2})
    assert first.startswith("sha256:")
    assert second.startswith("sha256:")
    assert first != second
