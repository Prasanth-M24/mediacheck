"""C2PA manifest extraction and validation service using c2pa-python.

Distinguishes between:
- DETECTED: Valid or invalid C2PA credentials present
- NOT_DETECTED: No C2PA credentials found in the media
- INVALID: C2PA manifest found but corrupted or tampered
- UNAVAILABLE: Library or binding not available
- UNSUPPORTED: File format not supported by C2PA
"""

from __future__ import annotations

import io
import json
from typing import Any

C2PA_AVAILABLE = False
try:
    import c2pa
    C2PA_AVAILABLE = True
except ImportError:
    c2pa = None  # type: ignore


def inspect_c2pa(image_bytes: bytes, mime_type: str) -> dict[str, Any]:
    """Inspect image bytes for C2PA provenance credentials."""
    if not C2PA_AVAILABLE:
        return {
            "status": "UNAVAILABLE",
            "has_credentials": False,
            "is_valid": False,
            "manifest": None,
            "signer_info": None,
            "details": "c2pa-python library is not installed or unavailable.",
            "limitations": ["C2PA validation requires native c2pa-python runtime."],
        }

    # Supported MIME types for C2PA reader
    supported_mimes = {"image/jpeg", "image/png", "image/webp", "image/avif", "image/tiff"}
    if mime_type not in supported_mimes:
        return {
            "status": "UNSUPPORTED",
            "has_credentials": False,
            "is_valid": False,
            "manifest": None,
            "signer_info": None,
            "details": f"MIME type '{mime_type}' is not supported by C2PA specification.",
            "limitations": [f"C2PA inspection currently supports: {', '.join(sorted(supported_mimes))}"],
        }

    stream = io.BytesIO(image_bytes)
    try:
        with c2pa.Reader(mime_type, stream) as reader:
            manifest_raw = reader.json()
            is_valid = reader.is_valid() if hasattr(reader, "is_valid") else True

            manifest_data: dict[str, Any] = {}
            if manifest_raw:
                try:
                    manifest_data = json.loads(manifest_raw)
                except Exception:
                    manifest_data = {"raw": str(manifest_raw)}

            active_manifest = manifest_data.get("active_manifest") or {}
            claim_generator = active_manifest.get("claim_generator")
            title = active_manifest.get("title")
            signature_info = active_manifest.get("signature_info") or {}

            signer_info = {
                "issuer": signature_info.get("issuer"),
                "time": signature_info.get("time"),
                "cert_serial_number": signature_info.get("cert_serial_number"),
                "claim_generator": claim_generator,
            }

            validation_results = None
            if hasattr(reader, "get_validation_results"):
                try:
                    validation_results = reader.get_validation_results()
                except Exception:
                    pass

            return {
                "status": "DETECTED",
                "has_credentials": True,
                "is_valid": bool(is_valid),
                "manifest_title": title,
                "claim_generator": claim_generator,
                "signer_info": signer_info,
                "validation_results": validation_results,
                "manifest": manifest_data,
                "details": "C2PA manifest found and parsed successfully.",
                "limitations": ["Valid manifest confirms content credentials, not author identity beyond signing certificate."],
            }

    except Exception as exc:
        err_msg = str(exc)
        # Check if error is simply "no manifest found"
        if "ManifestNotFound" in err_msg or "no JUMBF" in err_msg or "no manifest" in err_msg.lower():
            return {
                "status": "NOT_DETECTED",
                "has_credentials": False,
                "is_valid": False,
                "manifest": None,
                "signer_info": None,
                "details": "No C2PA manifest found in image. This is typical for ordinary camera and web images.",
                "limitations": ["Missing C2PA credentials do NOT indicate AI generation or malicious tampering."],
            }
        elif "Could not parse" in err_msg or "corrupt" in err_msg.lower() or "invalid" in err_msg.lower():
            return {
                "status": "INVALID",
                "has_credentials": True,
                "is_valid": False,
                "manifest": None,
                "signer_info": None,
                "details": f"C2PA manifest parsing error: {err_msg}",
                "limitations": ["Corrupted or incomplete manifest structure detected."],
            }
        else:
            return {
                "status": "NOT_DETECTED",
                "has_credentials": False,
                "is_valid": False,
                "manifest": None,
                "signer_info": None,
                "details": f"C2PA reader returned: {err_msg}",
                "limitations": ["Absence of C2PA credentials is non-evaluative."],
            }
