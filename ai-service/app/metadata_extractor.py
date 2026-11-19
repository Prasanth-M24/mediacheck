"""Image metadata extraction using Pillow.

Extracts format, dimensions, color mode, bits per channel, EXIF, and embedded metadata.
"""

from __future__ import annotations

import io
from typing import Any
from PIL import Image, ExifTags


def extract_metadata(image_bytes: bytes) -> dict[str, Any]:
    """Extract technical and embedded metadata from image bytes."""
    try:
        with Image.open(io.BytesIO(image_bytes)) as img:
            format_name = img.format or "UNKNOWN"
            width, height = img.size
            mode = img.mode
            info = img.info or {}

            # Extract EXIF tags if present
            exif_dict: dict[str, Any] = {}
            raw_exif = img.getexif()
            if raw_exif:
                for tag_id, value in raw_exif.items():
                    tag_name = ExifTags.TAGS.get(tag_id, str(tag_id))
                    # Avoid binary blobs in json output
                    if isinstance(value, (bytes, bytearray)):
                        exif_dict[tag_name] = f"<binary {len(value)} bytes>"
                    elif isinstance(value, (str, int, float, bool)):
                        exif_dict[tag_name] = value
                    else:
                        exif_dict[tag_name] = str(value)

                # Check for Exif IFD (sub-IFD containing DateTimeOriginal, exposure, etc.)
                try:
                    ifd_exif = raw_exif.get_ifd(ExifTags.IFD.Exif)
                    if ifd_exif:
                        for tag_id, value in ifd_exif.items():
                            tag_name = ExifTags.TAGS.get(tag_id, str(tag_id))
                            if isinstance(value, (bytes, bytearray)):
                                exif_dict[tag_name] = f"<binary {len(value)} bytes>"
                            elif isinstance(value, (str, int, float, bool)):
                                exif_dict[tag_name] = value
                            else:
                                exif_dict[tag_name] = str(value)
                except Exception:
                    pass

            # Channel / bit depth
            bits_per_channel = 8
            if mode in ("I", "F"):
                bits_per_channel = 32
            elif mode == "I;16":
                bits_per_channel = 16

            return {
                "format": format_name,
                "width": width,
                "height": height,
                "color_mode": mode,
                "bits_per_channel": bits_per_channel,
                "has_exif": bool(exif_dict),
                "exif": exif_dict,
                "icc_profile_present": "icc_profile" in info,
                "dpi": info.get("dpi"),
                "is_animated": getattr(img, "is_animated", False),
                "status": "PERFORMED",
            }
    except Exception as exc:
        return {
            "format": "UNKNOWN",
            "width": None,
            "height": None,
            "color_mode": None,
            "bits_per_channel": None,
            "has_exif": False,
            "exif": {},
            "icc_profile_present": False,
            "dpi": None,
            "is_animated": False,
            "status": "FAILED",
            "error": str(exc),
        }
