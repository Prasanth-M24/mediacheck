from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="MEDIACHECK AI Service", version="0.1.0", docs_url=None, redoc_url=None)

class ServiceInfo(BaseModel):
    service: str
    version: str
    ready: bool

@app.get("/health/live", include_in_schema=False)
def live() -> dict[str, str]:
    return {"status": "UP"}

@app.get("/health/ready", response_model=ServiceInfo, include_in_schema=False)
def ready() -> ServiceInfo:
    return ServiceInfo(service="mediacheck-ai-service", version=app.version, ready=True)

@app.get("/api/v1/info", response_model=ServiceInfo)
def info() -> ServiceInfo:
    return ServiceInfo(service="mediacheck-ai-service", version=app.version, ready=True)
