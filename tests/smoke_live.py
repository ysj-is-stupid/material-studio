"""Smoke-check a running Compose stack through the public frontend URL."""

import argparse
from datetime import datetime
import http.cookiejar
import json
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PNG = ROOT / "examples" / "assets" / "demo-coast.png"
SECOND_PNG = ROOT / "examples" / "assets" / "demo-sunset.png"


def client():
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))


def request(opener, base, path, method="GET", body=None, content_type=None):
    headers = {"Accept": "application/json"}
    if content_type:
        headers["Content-Type"] = content_type
    req = urllib.request.Request(base + path, data=body, headers=headers, method=method)
    try:
        with opener.open(req, timeout=20) as response:
            return response.status, response.headers, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.headers, error.read()


def json_request(opener, base, path, method="GET", payload=None):
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    status, _, data = request(opener, base, path, method, body,
                              "application/json" if body is not None else None)
    return status, json.loads(data.decode("utf-8"))


def upload(opener, base, path):
    status, result = upload_bytes(opener, base, path.read_bytes(), path.name)
    assert status == 201, (status, result)
    return result["assetId"]


def upload_bytes(opener, base, file_bytes, name, mime="image/png", template_id="travel-postcard"):
    boundary = "smoke" + uuid.uuid4().hex
    parts = [
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"templateId\"\r\n\r\n{template_id}\r\n".encode(),
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\nContent-Type: {mime}\r\n\r\n".encode(),
        file_bytes,
        f"\r\n--{boundary}--\r\n".encode(),
    ]
    status, _, data = request(opener, base, "/api/assets", "POST", b"".join(parts),
                              f"multipart/form-data; boundary={boundary}")
    result = json.loads(data.decode("utf-8"))
    return status, result


def await_task(opener, base, task_id, timeout=35):
    deadline = time.monotonic() + timeout
    observed = []
    while time.monotonic() < deadline:
        status, task = json_request(opener, base, f"/api/tasks/{task_id}")
        assert status == 200, (status, task)
        state = (task["status"], task["currentStage"])
        if not observed or observed[-1] != state:
            observed.append(state)
        if task["status"] in ("SUCCEEDED", "FAILED"):
            return task, observed
        time.sleep(0.25)
    raise AssertionError(("task did not finish", task_id, observed))


def main(base):
    own = client()
    other = client()
    status, health = json_request(own, base, "/api/health")
    assert status == 200 and health == {"status": "UP", "database": "UP"}, health
    status, templates = json_request(own, base, "/api/templates")
    assert status == 200 and len(templates["items"]) >= 3, templates
    assert len(templates["categories"]) >= 2, templates
    assert any(item["requiredImageCount"] == 1 for item in templates["items"])
    assert any(item["requiredImageCount"] == 2 for item in templates["items"])
    assert any(item["title"] == "旅行明信片" for item in templates["items"]), templates
    status, identity = json_request(own, base, "/api/identity")
    assert status == 200 and identity["viewerId"] and identity["slot"] == "A"
    status, other_identity = json_request(other, base, "/api/identity")
    assert status == 200 and other_identity["viewerId"] != identity["viewerId"]

    status, invalid_image = upload_bytes(own, base, b"not an image", "bad.png")
    assert status == 415 and invalid_image["code"] == "INVALID_IMAGE_TYPE", (status, invalid_image)
    status, empty_image = upload_bytes(own, base, b"", "empty.png")
    assert status == 422 and empty_image["code"] == "EMPTY_FILE", (status, empty_image)
    status, oversized = upload_bytes(own, base, b"x" * 5_242_881, "large.png")
    assert status == 413 and oversized["code"] == "FILE_TOO_LARGE", (status, oversized)
    status, missing_template = upload_bytes(own, base, PNG.read_bytes(), "sample.png", template_id="missing")
    assert status == 404 and missing_template["code"] == "TEMPLATE_NOT_FOUND", (status, missing_template)

    first = upload(own, base, PNG)
    second = upload(own, base, SECOND_PNG)
    status, _, _ = request(other, base, f"/api/assets/{first}/preview")
    assert status == 404, status
    status, invalid = json_request(own, base, "/api/tasks", "POST",
                                   {"templateId": "travel-postcard", "assetIds": [first]})
    assert status == 422 and invalid["code"] == "INVALID_TASK", (status, invalid)
    status, foreign_assets = json_request(other, base, "/api/tasks", "POST",
                                          {"templateId": "travel-postcard", "assetIds": [first, second]})
    assert status == 422 and foreign_assets["code"] == "INVALID_TASK", (status, foreign_assets)

    status, submitted = json_request(own, base, "/api/tasks", "POST", {
        "templateId": "travel-postcard", "assetIds": [first, second],
        "simulation": {"failStage": "NONE", "stepDelayMs": 600},
    })
    assert status == 202 and submitted["status"] == "QUEUED", (status, submitted)
    success_id = submitted["taskId"]
    status, forbidden = json_request(other, base, f"/api/tasks/{success_id}")
    assert status == 404 and forbidden["code"] == "TASK_NOT_FOUND", (status, forbidden)
    status, submitted_failure = json_request(own, base, "/api/tasks", "POST", {
        "templateId": "travel-postcard", "assetIds": [first, second],
        "simulation": {"failStage": "GENERATE", "stepDelayMs": 600},
    })
    assert status == 202, (status, submitted_failure)
    status, queued = json_request(own, base, f"/api/tasks/{submitted_failure['taskId']}")
    assert status == 200 and queued["status"] == "QUEUED", queued

    success, success_states = await_task(own, base, success_id)
    assert success["status"] == "SUCCEEDED" and success["resultUrl"]
    assert [(event["stage"], event["state"]) for event in success["stageHistory"]] == [
        ("ASSET_CHECK", "SUCCEEDED"), ("GENERATE", "SUCCEEDED"), ("FINALIZE", "SUCCEEDED")
    ], success
    status, _, image = request(own, base, success["resultUrl"])
    assert status == 200 and image.startswith(b"\x89PNG\r\n\x1a\n"), status

    failed, failure_states = await_task(own, base, submitted_failure["taskId"])
    assert failed["status"] == "FAILED" and failed["currentStage"] == "GENERATE"
    assert "模拟生成" in failed["failureReason"], failed
    assert [(event["stage"], event["state"]) for event in failed["stageHistory"]] == [
        ("ASSET_CHECK", "SUCCEEDED"), ("GENERATE", "FAILED")
    ], failed
    extra_failures = []
    for stage, expected in (("ASSET_CHECK", ["ASSET_CHECK"]),
                            ("FINALIZE", ["ASSET_CHECK", "GENERATE", "FINALIZE"])):
        status, accepted = json_request(own, base, "/api/tasks", "POST", {
            "templateId": "travel-postcard", "assetIds": [first, second],
            "simulation": {"failStage": stage, "stepDelayMs": 100},
        })
        assert status == 202, (status, accepted)
        terminal, _ = await_task(own, base, accepted["taskId"])
        assert terminal["status"] == "FAILED" and terminal["currentStage"] == stage, terminal
        assert [event["stage"] for event in terminal["stageHistory"]] == expected, terminal
        assert terminal["stageHistory"][-1]["state"] == "FAILED", terminal
        extra_failures.append(accepted["taskId"])
    status, slow_accepted = json_request(own, base, "/api/tasks", "POST", {
        "templateId": "travel-postcard", "assetIds": [first, second],
        "simulation": {"failStage": "ASSET_CHECK", "stepDelayMs": 3000},
    })
    assert status == 202, (status, slow_accepted)
    slow_task, _ = await_task(own, base, slow_accepted["taskId"])
    assert slow_task["status"] == "FAILED" and len(slow_task["stageHistory"]) == 1, slow_task
    slow_event = slow_task["stageHistory"][0]
    delay_seconds = (datetime.fromisoformat(slow_event["finishedAt"])
                     - datetime.fromisoformat(slow_event["startedAt"])).total_seconds()
    assert delay_seconds >= 2.8, delay_seconds
    status, still_success = json_request(own, base, f"/api/tasks/{success_id}")
    assert status == 200 and still_success["status"] == "SUCCEEDED", still_success
    status, history = json_request(own, base, "/api/tasks")
    assert status == 200 and {success_id, failed["id"], *extra_failures, slow_task["id"]}.issubset(
        {item["id"] for item in history["items"]})
    status, b_identity = json_request(own, base, "/api/identity/switch", "POST", {"slot": "B"})
    assert status == 200 and b_identity["slot"] == "B" and b_identity["viewerId"] != identity["viewerId"]
    status, b_history = json_request(own, base, "/api/tasks")
    assert status == 200 and b_history["items"] == [], b_history
    status, hidden = json_request(own, base, f"/api/tasks/{success_id}")
    assert status == 404 and hidden["code"] == "TASK_NOT_FOUND", (status, hidden)
    status, restored = json_request(own, base, "/api/identity/switch", "POST", {"slot": "A"})
    assert status == 200 and restored["viewerId"] == identity["viewerId"]
    status, restored_history = json_request(own, base, "/api/tasks")
    assert status == 200 and {item["id"] for item in restored_history["items"]} == {item["id"] for item in history["items"]}
    status, other_history = json_request(other, base, "/api/tasks")
    assert status == 200 and other_history["items"] == [], other_history
    print(json.dumps({
        "result": "PASS", "templates": len(templates["items"]),
        "successTask": success_id, "successStates": success_states,
        "failedTask": failed["id"], "failureStates": failure_states,
        "otherFailedStages": ["ASSET_CHECK", "FINALIZE"],
        "configuredDelaySeconds": round(delay_seconds, 2),
        "resultBytes": len(image), "historyCount": len(history["items"]),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://localhost:3000")
    args = parser.parse_args()
    main(args.base.rstrip("/"))
