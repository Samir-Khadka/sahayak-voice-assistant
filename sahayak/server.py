"""HTTP server so the Android app (and anything else) can share the brain.

  GET  /v1/health
  POST /v1/command  {"text": ..., "device_id": ..., "lang": "en"|"ne"|"auto"}
      -> {"reply_text": ..., "action": ..., "data": {...}}
Auth: header X-Token must equal the server_token in sahayak_config.json
(compared in constant time). Per-IP rate limits apply; repeated bad tokens
get the IP temporarily blocked. Binds to server_host — set it to 127.0.0.1
if you only use the laptop itself, or your Tailscale IP for phone access.
"""
import hmac
import time

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from pydantic import BaseModel, Field

from .brain import Brain
from .config import Config
from .intents import detect_lang

cfg = Config()
brain = Brain(cfg)
app = FastAPI(title="Sahayak")

# ---- tiny in-memory rate limiter (per IP) ----
_hits: dict[str, list[float]] = {}    # successful-token call timestamps
_auth_fails: dict[str, list[float]] = {}  # failed-token timestamps


def _prune(store: dict[str, list[float]], ip: str, window: float) -> list[float]:
    now = time.time()
    arr = [t for t in store.get(ip, []) if now - t < window]
    store[ip] = arr
    return arr


def _client_ip(request: Request) -> str:
    return request.client.host if request.client else "unknown"


class Command(BaseModel):
    text: str = Field(max_length=500)
    device_id: str = Field(default="unknown", max_length=100)
    lang: str = Field(default="auto", max_length=10)


def _auth(request: Request, x_token: str = Header(default="")):
    ip = _client_ip(request)
    fails = _prune(_auth_fails, ip, 300)
    if len(fails) >= int(cfg["max_auth_failures"]):
        raise HTTPException(status_code=429, detail="too many bad tokens, try later")
    if not hmac.compare_digest(x_token or "", cfg["server_token"] or ""):
        fails.append(time.time())
        print(f"[server] bad token from {ip}")
        raise HTTPException(status_code=401, detail="bad token")
    calls = _prune(_hits, ip, 60)
    if len(calls) >= int(cfg["rate_limit_per_minute"]):
        raise HTTPException(status_code=429, detail="rate limited")
    calls.append(time.time())


@app.get("/v1/health")
def health():
    return {"ok": True, "assistant": cfg["assistant_name"]}


@app.post("/v1/command", dependencies=[Depends(_auth)])
def command(cmd: Command, request: Request):
    lang = cmd.lang if cmd.lang in ("en", "ne") else detect_lang(cmd.text)
    reply, intent, slots = brain.handle_text(cmd.text)
    return {"reply_text": reply, "action": intent, "data": slots or {}, "lang": lang}


def run():
    import uvicorn
    uvicorn.run(app, host=cfg["server_host"], port=int(cfg["server_port"]))
