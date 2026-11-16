# SPDX-License-Identifier: AGPL-3.0-only
"""Linux model-free acceptance of an already installed Writhelm wrapper.

Usage: python3 automatic-ui-client.py INSTALLATION_EVIDENCE_ROOT
The installer must already have emitted ROOT/client/installation/writhelm. This driver only opens
an empty folder, uses ordinary New session/exit keys and reads native state. It never submits a
prompt, POSTs a session, creates service identities or imports a user's login/profile.
"""
import sys
if sys.platform != "linux":
    raise SystemExit("This acceptance adapter requires Linux")

import base64
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import pty
import select
import signal
import struct
import subprocess
import tempfile
import termios
import time
import urllib.parse
import urllib.request

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("root")
parser.add_argument("--attached", action="store_true", help="Leave the shared service running; do not explicitly stop this disposable instance")
parser.add_argument("--minimum-lifetime", type=float, default=0, help="Delay normal exit to overlap another test UI (0..30 seconds)")
parser.add_argument("--work-directory", help="Existing directory for same-folder overlap acceptance; otherwise create an empty folder")
parser.add_argument("--expected-sessions", type=int, default=2, help="Number of admission-confirmed sessions required before exit (2..8)")
args = parser.parse_args()
assert 0 <= args.minimum_lifetime <= 30
assert 2 <= args.expected_sessions <= 8
root = Path(args.root).resolve()
wrapper = root / "client/installation/writhelm"
assert wrapper.is_file() and os.access(wrapper, os.X_OK)
work = Path(args.work_directory).resolve() if args.work_directory else Path(tempfile.mkdtemp(prefix="ui-open-folder-", dir=root))
assert work.is_dir()
store_existed = (root / "client/private/instance/store").exists()
master, slave = pty.openpty()
fcntl.ioctl(slave, termios.TIOCSWINSZ, struct.pack("HHHH", 36, 120, 0, 0))
environment = {"PATH": os.defpath, "TERM": "xterm-256color", "LANG": "C.UTF-8", "HOME": str(root / "client/home")}

def terminal():
    os.setsid()
    fcntl.ioctl(slave, termios.TIOCSCTTY, 0)

child = subprocess.Popen([str(wrapper), str(work)], stdin=slave, stdout=slave, stderr=slave,
                         env=environment, preexec_fn=terminal)
os.close(slave)
registration = root / "client/state/opencode/service.json"
screen = bytearray()
sessions, histories = [], {}
native_pid = None
new_key = exiting = passed = False
start = last_read = time.monotonic()
connected = 0

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

http = urllib.request.build_opener(NoRedirect)

def read_native(path):
    config = json.loads(registration.read_text())
    origin = urllib.parse.urlsplit(config["url"])
    assert origin.scheme == "http" and origin.hostname == "127.0.0.1" and not origin.username
    auth = base64.b64encode(("opencode:" + config["password"]).encode()).decode()
    request = urllib.request.Request(config["url"] + path, headers={"Authorization": "Basic " + auth})
    with http.open(request, timeout=2) as response:
        data = response.read(65537)
    assert len(data) <= 65536
    return json.loads(data)

try:
    while time.monotonic() - start < 45 and child.poll() is None:
        readable, _, _ = select.select([master], [], [], 0.2)
        if readable:
            try:
                data = os.read(master, 65536)
            except OSError:
                break
            screen.extend(data)
            assert len(screen) <= 4 * 1024 * 1024, "Bounded terminal capture exceeded"
            if b"\x1b[6n" in data:
                os.write(master, b"\x1b[1;1R")
            if b"\x1b[c" in data:
                os.write(master, b"\x1b[?1;2c")
        age = time.monotonic() - start
        if connected >= 1 and not new_key and len(screen) > 100:
            os.write(master, b"\x18n")  # Native session.new, documented default <leader>n.
            new_key = True
        if registration.exists() and time.monotonic() - last_read > 1:
            last_read = time.monotonic()
            try:
                result = read_native("/api/session?directory=" + urllib.parse.quote(str(work), safe=""))
                sessions = [s for s in result["data"] if s.get("location", {}).get("directory") == str(work)]
                native_pid = read_native("/api/info")["pid"]
                for session in sessions:
                    histories[session["id"]] = len(read_native("/api/session/" + session["id"] + "/context")["data"])
                connected = 0
                for session in sessions:
                    key = hashlib.sha256(session["id"].encode()).hexdigest()
                    witness = root / ("client/private/diagnostics/activation-" + key + ".json")
                    if witness.exists() and json.loads(witness.read_text()) == {"stage": "service-connected", "pid": native_pid}:
                        connected += 1
            except Exception:
                pass  # Startup/stop races cannot satisfy the final positive assertions below.
        if connected >= args.expected_sessions and age >= args.minimum_lifetime and not exiting:
            os.write(master, b"\x18q")  # Native app.exit; no task text or prompt submission.
            exiting = True
        if age > 40 and child.poll() is None:
            os.write(master, b"\x03")
            if age > 42:
                break
    deadline = time.monotonic() + 8
    while child.poll() is None and time.monotonic() < deadline:
        ready, _, _ = select.select([master], [], [], 0.2)
        if ready:
            try:
                screen.extend(os.read(master, 65536))
                assert len(screen) <= 4 * 1024 * 1024, "Bounded terminal capture exceeded"
            except OSError:
                break
    connected = 0
    for session in sessions:
        key = hashlib.sha256(session["id"].encode()).hexdigest()
        witness = root / ("client/private/diagnostics/activation-" + key + ".json")
        if witness.exists():
            observed = json.loads(witness.read_text())
            if observed == {"stage": "service-connected", "pid": native_pid}:
                connected += 1
    assert new_key and len(sessions) >= args.expected_sessions and connected == len(sessions)
    assert (work / ".git").exists()
    assert all(histories.get(s["id"], -1) == 0 for s in sessions)
    assert all({"edit", "shell", "subagent", "execute", "external_directory"} <=
               {r["action"] for r in s.get("permissions", []) if r.get("effect") == "deny"} for s in sessions)
    passed = True
finally:
    if child.poll() is None:
        os.killpg(child.pid, signal.SIGTERM)  # Only this test's own new process group.
        try:
            child.wait(timeout=20)
        except subprocess.TimeoutExpired:
            os.killpg(child.pid, signal.SIGKILL)
            child.wait(timeout=5)
    os.close(master)
    service_survived_view = (registration.exists() and (root / "client/lifecycle/service-owner.json").exists()
                            and (root / "client/lifecycle/service-ready.json").exists())
    service_stop_exit = None
    if passed and child.returncode == 0 and not args.attached and registration.exists():
        # Closing a view no longer owns service lifetime. This acceptance helper explicitly stops
        # only its disposable instance after successful UI checks; attached tests leave it alive.
        stopped = subprocess.run([str(wrapper), "--stop-service"], env=environment,
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=40)
        service_stop_exit = stopped.returncode
    result = {"passed": (passed and child.returncode == 0 and service_survived_view
                         and registration.exists() == args.attached and (args.attached or service_stop_exit == 0)),
              "attached": args.attached,
              "service_survived_view_exit": service_survived_view,
              "new_session_key_sent": new_key, "expected_sessions": args.expected_sessions,
              "store_existed_before_ui": store_existed, "terminal_bytes": len(screen),
              "native_sessions": len(sessions), "service_connect_witnesses": connected,
              "history_message_counts": sorted(histories.values()), "git_initialized": (work / ".git").exists(),
              "ui_exit": child.returncode, "native_registration_remains": registration.exists(), "submitted_prompts": 0}
    result["explicit_service_stop_exit"] = service_stop_exit
    run = str(time.time_ns())
    for name, content in [("ui-terminal-" + run + ".log", bytes(screen)),
                          ("ui-acceptance-" + run + ".json", json.dumps(result, indent=2).encode())]:
        with os.fdopen(os.open(root / name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "wb") as output:
            output.write(content)
    print(json.dumps(result, sort_keys=True))
    if not result["passed"]:
        raise SystemExit(1)
