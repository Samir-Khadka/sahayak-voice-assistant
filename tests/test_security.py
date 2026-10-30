"""Security + robustness tests for Sahayak. Run: pytest -q (from voice-assistant/)."""
import os
import sys
import tempfile

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from sahayak import actions_windows
from sahayak.brain import Brain
from sahayak.config import Config


def _brain():
    cfg = Config(path=os.path.join(tempfile.mkdtemp(), "cfg.json"))
    return Brain(cfg, dry_run=True)


# --- open_app sanitization (the old code passed names to os.system) ---
def test_sanitize_rejects_shell_metachars():
    assert actions_windows._sanitize_app_name('chrome" & calc.exe') == ""
    assert actions_windows._sanitize_app_name("a; rm -rf /") == ""
    assert actions_windows._sanitize_app_name("$(whoami)") == ""
    assert actions_windows._sanitize_app_name("../../etc/passwd") == ""


def test_sanitize_accepts_normal_names():
    assert actions_windows._sanitize_app_name("Chrome") == "chrome"
    assert actions_windows._sanitize_app_name("vs code") == "vs code"
    assert actions_windows._sanitize_app_name("notepad++") == ""  # + not allowed -> safe reject


def test_open_app_hostile_name_never_executes():
    # Would previously hit os.system; now rejected before any OS call.
    reply = actions_windows._open_app('x" & echo pwned', "en")
    assert "can't open" in reply


# --- confirm flow for dangerous actions ---
def test_shutdown_asks_for_confirmation_first():
    b = _brain()
    reply, intent, slots = b.handle_text("shut down")
    assert intent == "shutdown"
    assert slots.get("awaiting_confirm") is True
    assert "sure" in reply.lower() or "पक्का" in reply


def test_shutdown_cancel():
    b = _brain()
    b.handle_text("shut down")
    reply, intent, _ = b.handle_text("no")
    assert intent == "cancel"
    assert b.pending is None


def test_shutdown_confirm_executes():
    b = _brain()
    b.handle_text("restart")
    assert b.pending is not None  # gated
    reply, intent, _ = b.handle_text("yes")
    assert intent == "restart"
    assert reply  # executed (on Windows: real restart; elsewhere: honest notice)
    assert b.pending is None


def test_new_command_supersedes_pending():
    b = _brain()
    b.handle_text("shut down")
    reply, intent, _ = b.handle_text("what time is it")
    assert intent == "time"
    assert b.pending is None


def test_confirm_nepali():
    b = _brain()
    b.handle_text("शटडाउन गर")
    reply, intent, _ = b.handle_text("हो")
    assert intent == "shutdown"
    assert b.pending is None
