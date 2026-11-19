"""FastAPI application for MEDIACHECK AI Service.

Provides provenance analysis, C2PA credential inspection, image manipulation detection,
and spectral AI-generation detection.
"""

from __future__ import annotations

import base64
import binascii
import hashlib
from typing import Any, Literal
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from app.metadata_extractor import extract_metadata
from app.c2pa_service import inspect_c2pa
from app.manipulation_detector import analyze_manipulation
from app.ai_detector import detector

app = FastAPI(title="MEDIACHECK AI Service", version="0.2.0", docs_url=None, redoc_url=None)


class ServiceInfo(BaseModel):
    service: str
    version: str
    ready: bool


class ProvenanceRequest(BaseModel):
    content_base64: str = Field(min_length=1, max_length=70_000_000)
    mime_type: Literal["image/jpeg", "image/png", "image/webp"]


class ProvenanceResponse(BaseModel):
    sha256_checksum: str
    signature_valid: bool
    metadata_status: Literal["PERFORMED", "UNAVAILABLE", "FAILED"]
    metadata: dict[str, Any]
    c2pa_status: Literal["DETECTED", "NOT_DETECTED", "INVALID", "UNAVAILABLE", "UNSUPPORTED"]
    c2pa_details: dict[str, Any]
    manipulation_status: Literal["PERFORMED", "UNAVAILABLE", "FAILED"]
    manipulation_details: dict[str, Any]
    ai_generation_status: Literal["DETECTED", "LIKELY_AI_GENERATED", "INCONCLUSIVE", "NOT_DETECTED", "UNAVAILABLE"]
    ai_generation_details: dict[str, Any]
    limitations: list[str]


@app.get("/health/live", include_in_schema=False)
def live() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/health/ready", response_model=ServiceInfo, include_in_schema=False)
def ready() -> ServiceInfo:
    return ServiceInfo(service="mediacheck-ai-service", version=app.version, ready=True)


@app.get("/api/v1/info", response_model=ServiceInfo)
def info() -> ServiceInfo:
    return ServiceInfo(service="mediacheck-ai-service", version=app.version, ready=True)


@app.post("/api/v1/analyze/provenance", response_model=ProvenanceResponse)
def analyze_provenance(request: ProvenanceRequest) -> ProvenanceResponse:
    """Perform real provenance, C2PA, manipulation, and AI generation analysis."""
    try:
        data = base64.b64decode(request.content_base64, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise HTTPException(status_code=400, detail="invalid_base64") from exc

    if not data:
        raise HTTPException(status_code=400, detail="empty_content")

    # Validate signature
    sig_valid = False
    if request.mime_type == "image/jpeg" and data.startswith(b"\xff\xd8\xff"):
        sig_valid = True
    elif request.mime_type == "image/png" and data.startswith(b"\x89PNG\r\n\x1a\n"):
        sig_valid = True
    elif request.mime_type == "image/webp" and data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        sig_valid = True

    sha256_hash = hashlib.sha256(data).hexdigest()

    # 1. Metadata extraction
    meta = extract_metadata(data)
    meta_status = meta.get("status", "PERFORMED")

    # 2. C2PA extraction
    c2pa_res = inspect_c2pa(data, request.mime_type)
    c2pa_status = c2pa_res["status"]

    # 3. Manipulation indicators (ELA)
    manip_res = analyze_manipulation(data)
    manip_status = "PERFORMED" if manip_res.get("indicator") != "UNAVAILABLE" else "UNAVAILABLE"

    # 4. AI-generation detector (2D-FFT and residual features)
    ai_res = detector.detect(data)
    ai_status = ai_res["status"]

    all_limitations: list[str] = []
    if "limitations" in c2pa_res:
        all_limitations.extend(c2pa_res["limitations"])
    if "limitations" in manip_res:
        all_limitations.extend(manip_res["limitations"])
    if "limitations" in ai_res:
        all_limitations.extend(ai_res["limitations"])

    return ProvenanceResponse(
        sha256_checksum=sha256_hash,
        signature_valid=sig_valid,
        metadata_status=meta_status,
        metadata=meta,
        c2pa_status=c2pa_status,
        c2pa_details=c2pa_res,
        manipulation_status=manip_status,
        manipulation_details=manip_res,
        ai_generation_status=ai_status,
        ai_generation_details=ai_res,
        limitations=all_limitations,
    )


@app.get("/api/v1/analyze/ai-detector/evaluate")
def evaluate_ai_detector(samples: int = 50) -> dict[str, Any]:
    """Return precision, recall, F1, and confusion matrix of the AI generation detector on holdout dataset."""
    return detector.evaluate(n_test=max(10, min(samples, 100)))
