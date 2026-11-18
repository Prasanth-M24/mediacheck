from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def test_liveness() -> None:
    response = client.get("/health/live")
    assert response.status_code == 200
    assert response.json() == {"status": "UP"}

def test_readiness_and_info() -> None:
    for path in ("/health/ready", "/api/v1/info"):
        response = client.get(path)
        assert response.status_code == 200
        assert response.json()["service"] == "mediacheck-ai-service"
        assert response.json()["ready"] is True
