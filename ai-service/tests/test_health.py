"""Tests for MEDIACHECK AI Service: health, metadata, C2PA, manipulation, and AI detection."""

from __future__ import annotations

import base64
import io
import numpy as np
from PIL import Image
from fastapi.testclient import TestClient

from app.main import app
from app.ai_detector import detector

client = TestClient(app)


def make_test_image(format_name: str = "PNG", color=(255, 0, 0), size=(120, 120)) -> bytes:
    buf = io.BytesIO()
    img = Image.new("RGB", size, color=color)
    img.save(buf, format=format_name)
    return buf.getvalue()


def test_live() -> None:
    response = client.get("/health/live")
    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_readiness_and_info() -> None:
    for path in ("/health/ready", "/api/v1/info"):
        response = client.get(path)
        assert response.status_code == 200
        assert response.json()["service"] == "mediacheck-ai-service"
        assert response.json()["ready"] is True


def test_provenance_png_valid() -> None:
    data = make_test_image("PNG")
    response = client.post("/api/v1/analyze/provenance", json={
        "content_base64": base64.b64encode(data).decode(),
        "mime_type": "image/png",
    })
    assert response.status_code == 200
    res = response.json()
    assert res["signature_valid"] is True
    assert res["metadata_status"] == "PERFORMED"
    assert res["metadata"]["width"] == 120
    assert res["metadata"]["height"] == 120
    assert res["metadata"]["color_mode"] == "RGB"
    assert res["c2pa_status"] in ("NOT_DETECTED", "DETECTED")
    assert res["manipulation_status"] == "PERFORMED"
    assert res["ai_generation_status"] in ("NOT_DETECTED", "INCONCLUSIVE", "LIKELY_AI_GENERATED", "DETECTED")
    assert len(res["limitations"]) > 0


def test_provenance_jpeg_valid() -> None:
    data = make_test_image("JPEG", color=(0, 255, 128), size=(80, 60))
    response = client.post("/api/v1/analyze/provenance", json={
        "content_base64": base64.b64encode(data).decode(),
        "mime_type": "image/jpeg",
    })
    assert response.status_code == 200
    res = response.json()
    assert res["signature_valid"] is True
    assert res["metadata"]["format"] == "JPEG"
    assert res["metadata"]["width"] == 80
    assert res["metadata"]["height"] == 60


def test_provenance_webp_valid() -> None:
    data = make_test_image("WEBP", color=(50, 50, 200), size=(64, 64))
    response = client.post("/api/v1/analyze/provenance", json={
        "content_base64": base64.b64encode(data).decode(),
        "mime_type": "image/webp",
    })
    assert response.status_code == 200
    res = response.json()
    assert res["signature_valid"] is True
    assert res["metadata"]["format"] == "WEBP"


def test_provenance_invalid_base64() -> None:
    response = client.post("/api/v1/analyze/provenance", json={
        "content_base64": "!!!not-valid-base64!!!",
        "mime_type": "image/png",
    })
    assert response.status_code == 400
    assert "invalid_base64" in response.json()["detail"]


def test_provenance_mismatched_mime_signature() -> None:
    # Send JPEG bytes with PNG mime_type
    jpeg_data = make_test_image("JPEG")
    response = client.post("/api/v1/analyze/provenance", json={
        "content_base64": base64.b64encode(jpeg_data).decode(),
        "mime_type": "image/png",
    })
    assert response.status_code == 200
    res = response.json()
    assert res["signature_valid"] is False  # MIME mismatch detected!


def test_ai_detector_evaluation_endpoint() -> None:
    response = client.get("/api/v1/analyze/ai-detector/evaluate?samples=20")
    assert response.status_code == 200
    metrics = response.json()
    assert metrics["evaluation_status"] == "VALIDATED"
    assert metrics["test_samples_total"] == 40  # 20 synth + 20 auth
    assert metrics["precision"] >= 0.70
    assert metrics["recall"] >= 0.70
    assert metrics["f1_score"] >= 0.70
    assert "confusion_matrix" in metrics
    assert metrics["confusion_matrix"]["true_positive"] > 0
