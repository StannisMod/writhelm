# SPDX-License-Identifier: AGPL-3.0-only
"""Linux model-free installed-client acceptance: concurrent startup, four chats in one folder,
first view exits while the second stays usable, then explicit normal service stop.
Usage: python3 parallel-ui-client.py INSTALLATION_EVIDENCE_ROOT
"""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time

if sys.platform != "linux" or len(sys.argv) != 2:
    raise SystemExit("Requires Linux and an explicit disposable installed root")
root = Path(sys.argv[1]).resolve()
wrapper = root / "client/installation/writhelm"
registration = root / "client/state/opencode/service.json"
marker = root / "client/lifecycle/service-owner.json"
ready_marker = root / "client/lifecycle/service-ready.json"
assert wrapper.is_file() and not registration.exists() and not marker.exists() and not ready_marker.exists()
work = Path(tempfile.mkdtemp(prefix="shared-chat-folder-", dir=root))
helper = Path(__file__).with_name("automatic-ui-client.py")
command = [sys.executable, str(helper), str(root), "--attached", "--work-directory", str(work), "--expected-sessions", "4"]
environment = {"PATH": os.defpath, "LANG": "C.UTF-8", "HOME": str(root / "client/home")}

def read(file, limit=16384):
    with file.open("rb") as stream:
        raw = stream.read(limit + 1)
    assert len(raw) <= limit
    return json.loads(raw)

def outcome(child):
    out, err = child.communicate(timeout=100)
    assert child.returncode == 0, "TUI acceptance failed; inspect its private receipts"
    result = json.loads(out.strip().splitlines()[-1])
    assert result["passed"] and result["ui_exit"] == 0
    return result

# Start together: neither test view is the lifetime owner, and neither performs manual bootstrap.
first = subprocess.Popen(command + ["--minimum-lifetime", "15"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
second = subprocess.Popen(command + ["--minimum-lifetime", "25"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
result = {"passed": False, "submitted_prompts": 0}
try:
    deadline = time.monotonic() + 30
    while (not registration.exists() or not ready_marker.exists()) and time.monotonic() < deadline:
        if first.poll() is not None and second.poll() is not None:
            break
        time.sleep(0.1)
    assert registration.exists() and marker.exists() and ready_marker.exists(), "Shared service startup did not become ready"
    original_owner = read(marker)
    original_native = read(registration)
    a = outcome(first)
    assert second.poll() is None, "Second view must still be running when the first exits"
    assert read(marker) == original_owner and read(registration) == original_native
    b = outcome(second)
    assert read(marker) == original_owner and read(registration) == original_native
    requests = list((root / "client/private/session-creations").glob("session-creation-*.json"))
    assert len(requests) <= 1024
    chats = {record["sessionID"] for record in map(read, requests) if record["directory"] == str(work)}
    controllers = set()
    enrollment = list((root / "client/private/enrollment").glob("*.command"))
    assert len(enrollment) <= 4096
    for file in enrollment:
        record = read(file)
        if record.get("sessionId") in chats and isinstance(record.get("controllerId"), str):
            controllers.add(record["controllerId"])
    assert len(chats) == 4 and len(controllers) == 4
    result.update(first_view_exit=0, second_view_exit=0, second_view_survived_first=True,
                  same_service_owner=True, same_native_process=True, distinct_chats=len(chats),
                  distinct_controllers=len(controllers), history_message_counts=a["history_message_counts"],
                  second_history_message_counts=b["history_message_counts"])
finally:
    # Normal stop of only the known disposable instance. This does not clear any unknown marker.
    stopped = subprocess.run([str(wrapper), "--stop-service"], env=environment,
                             stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=45)
    result["explicit_service_stop_exit"] = stopped.returncode
    result["native_registration_remains"] = registration.exists()
    result["service_owner_marker_remains"] = marker.exists()
    result["service_ready_marker_remains"] = ready_marker.exists()
    result["passed"] = (result.get("distinct_controllers") == 4 and stopped.returncode == 0
                        and not registration.exists() and not marker.exists() and not ready_marker.exists())
    receipt = root / ("parallel-ui-acceptance-" + str(time.time_ns()) + ".json")
    with os.fdopen(os.open(receipt, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w") as stream:
        json.dump(result, stream, indent=2)
    print(json.dumps(result, sort_keys=True))
    if not result["passed"]:
        raise SystemExit(1)
