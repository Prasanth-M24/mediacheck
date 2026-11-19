"""Image manipulation indicator analysis.

Implements Error Level Analysis (ELA) and noise discrepancy analysis.
Explicitly documents limitations: ordinary compression, resizing, or text overlays
produce error signals; this check is an advisory signal, not proof of forgery.
"""

from __future__ import annotations

import io
from typing import Any
import numpy as np
from PIL import Image, ImageChops


def analyze_manipulation(image_bytes: bytes, quality: int = 90) -> dict[str, Any]:
    """Perform Error Level Analysis (ELA) and noise variance discrepancy check."""
    try:
        with Image.open(io.BytesIO(image_bytes)) as original:
            # Convert to RGB for consistent analysis
            if original.mode not in ("RGB", "L"):
                orig_rgb = original.convert("RGB")
            else:
                orig_rgb = original

            # Resave to JPEG buffer at specified quality
            buf = io.BytesIO()
            orig_rgb.save(buf, format="JPEG", quality=quality)
            buf.seek(0)

            with Image.open(buf) as recompressed:
                # Difference between original and recompressed
                diff = ImageChops.difference(orig_rgb, recompressed)
                diff_arr = np.array(diff, dtype=np.float32)

                # Statistical metrics
                mean_error = float(np.mean(diff_arr))
                max_error = int(np.max(diff_arr))
                std_error = float(np.std(diff_arr))

                # Ratio of pixels with high error discrepancy (> 25 on 0-255 scale)
                high_discrepancy_pixels = int(np.sum(diff_arr > 25))
                total_elements = diff_arr.size
                high_discrepancy_ratio = float(high_discrepancy_pixels / max(total_elements, 1))

                # Localized block variance: check if variance across 16x16 tiles is anomalous
                h, w = diff_arr.shape[:2]
                tile_h, tile_w = max(1, h // 8), max(1, w // 8)
                tile_means = []
                for i in range(8):
                    for j in range(8):
                        tile = diff_arr[i * tile_h : (i + 1) * tile_h, j * tile_w : (j + 1) * tile_w]
                        if tile.size > 0:
                            tile_means.append(float(np.mean(tile)))

                tile_discrepancy_std = float(np.std(tile_means)) if tile_means else 0.0

                # Determine indicator level
                if high_discrepancy_ratio > 0.15 or tile_discrepancy_std > 8.0:
                    indicator = "HIGH_DISCREPANCY"
                    summary = "Elevated compression discrepancy detected across image regions."
                elif high_discrepancy_ratio > 0.05 or tile_discrepancy_std > 4.0:
                    indicator = "MODERATE_DISCREPANCY"
                    summary = "Moderate localized compression variance detected."
                else:
                    indicator = "LOW_DISCREPANCY"
                    summary = "Compression error level is uniform and consistent across regions."

                return {
                    "method": f"Error Level Analysis (ELA quality {quality}) and localized variance",
                    "indicator": indicator,
                    "mean_error": round(mean_error, 3),
                    "max_error": max_error,
                    "std_error": round(std_error, 3),
                    "tile_variance_std": round(tile_discrepancy_std, 3),
                    "high_discrepancy_ratio": round(high_discrepancy_ratio, 4),
                    "summary": summary,
                    "limitations": [
                        "Compression levels naturally vary with fine detail, edges, and high-frequency textures.",
                        "Recompressing previously compressed media produces non-zero ELA residuals.",
                        "This indicator is advisory and does NOT constitute forensic proof of malicious manipulation.",
                    ],
                }

    except Exception as exc:
        return {
            "method": "Error Level Analysis",
            "indicator": "UNAVAILABLE",
            "mean_error": None,
            "max_error": None,
            "std_error": None,
            "tile_variance_std": None,
            "high_discrepancy_ratio": None,
            "summary": f"Manipulation analysis encountered an error: {exc}",
            "limitations": ["Could not process image stream for ELA."],
        }
