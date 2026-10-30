"""HTTP server so the Android app (and anything else) can share the brain.

  GET  /v1/health
  POST /v1/command  {"text": ..., "device_id": ..., "lang": "en"|"ne"|"auto"}
      -> {"reply_text": ..., "action": ..., "data": {...}}
Auth: header X-Token must equal the server_token in sahayak_config.json.
"""
from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel

from .brain import Brain
from .config import Config
from .intents import detect_lang

cfg = Config()
brain = Brain(cfg)
app = FastAPI(title="Sahayak")


class Command(BaseModel):
    text: str
    device_id: str = "unknown"
    lang: str = "auto"


def _auth(x_token: str = Header(default="")):
    if x_token != cfg["server_token"]:
        raise HTTPException(status_code=401, detail="bad token")


@app.get("/v1/health")
def health():
    return {"ok": True, "assistant": cfg["assistant_name"]}


@app.post("/v1/command", dependencies=[Depends(_auth)])
def command(cmd: Command):
    lang = cmd.lang if cmd.lang in ("en", "ne") else detect_lang(cmd.text)
    reply, intent, slots = brain.handle_text(cmd.text)
    return {"reply_text": reply, "action": intent, "data": slots or {}, "lang": lang}


def run():
    import uvicorn
    uvicorn.run(app, host=cfg["server_host"], port=int(cfg["server_port"]))
