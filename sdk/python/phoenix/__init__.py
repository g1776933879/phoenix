import json, urllib.request, urllib.error
from dataclasses import dataclass
from typing import Optional, List, Dict

@dataclass
class ChatResponse:
    content: str=""; iterations: int=0; duration_ms: int=0; truncated: bool=False; session_id: Optional[str]=None

@dataclass
class SessionInfo:
    id: str=""; title: str=""; created_at: str=""; message_count: int=0; current: bool=False

class PhoenixError(Exception): pass

class Phoenix:
    def __init__(self, base_url="http://localhost:8080", api_key: Optional[str]=None):
        self.base_url = base_url.rstrip("/"); self.api_key = api_key; self._sid: Optional[str] = None
    def _req(self, m: str, p: str, d: Optional[Dict]=None) -> Dict:
        url = f"{self.base_url}{p}"; h = {"Content-Type": "application/json"}
        if self.api_key: h["Authorization"] = f"Bearer {self.api_key}"
        b = json.dumps(d).encode() if d else None
        try:
            with urllib.request.urlopen(urllib.request.Request(url, data=b, headers=h, method=m), timeout=60) as r:
                return json.loads(r.read().decode())
        except urllib.error.HTTPError as e:
            raise PhoenixError(f"HTTP {e.code}: {e.read().decode()}")
        except urllib.error.URLError as e:
            raise PhoenixError(f"Connection: {e.reason}")
    def chat(self, content: str) -> ChatResponse:
        d = self._req("POST", "/api/agent/chat", {"content": content})
        r = ChatResponse(content=d.get("content",""), iterations=d.get("iterations",0), duration_ms=d.get("durationMs",0), truncated=d.get("truncated",False), session_id=d.get("sessionId"))
        if r.session_id: self._sid = r.session_id
        return r
    def list_sessions(self) -> List[SessionInfo]:
        return [SessionInfo(**s) for s in self._req("GET", "/api/sessions")]
    def new_session(self) -> str:
        self._sid = self._req("POST", "/api/sessions").get("sessionId"); return self._sid
    def health(self) -> Dict: return self._req("GET", "/api/agent/health")
    def evolve(self) -> Dict: return self._req("POST", "/api/evolution/evolve")
    def __repr__(self): return f"Phoenix(base_url='{self.base_url}')"
