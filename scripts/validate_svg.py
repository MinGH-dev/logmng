#!/usr/bin/env python3
"""
Validate SVG files for Chromium-friendly authoring:
  - File is valid UTF-8 (no broken bytes).
  - Content is well-formed XML (same baseline as the XML parser inside browsers).

Usage:
  python3 scripts/validate_svg.py
  python3 scripts/validate_svg.py path/to/file.svg
"""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def validate_file(path: Path) -> None:
    raw = path.read_bytes()
    try:
        raw.decode("utf-8")
    except UnicodeDecodeError as e:
        raise ValueError(f"not valid UTF-8: {e}") from e
    try:
        ET.parse(path)
    except ET.ParseError as e:
        raise ValueError(f"not well-formed XML: {e}") from e


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    if len(sys.argv) > 1:
        paths = [Path(p) for p in sys.argv[1:]]
    else:
        paths = sorted((root / "assets" / "svg").rglob("*.svg"))
        if not paths:
            print("No SVG files under assets/svg", file=sys.stderr)
            return 0
    errors: list[tuple[Path, str]] = []
    for p in paths:
        if not p.is_file():
            errors.append((p, "not a file"))
            continue
        try:
            validate_file(p)
        except ValueError as e:
            errors.append((p, str(e)))
    if errors:
        for p, msg in errors:
            print(f"FAIL {p}: {msg}", file=sys.stderr)
        return 1
    for p in paths:
        print(f"OK {p.relative_to(root)}")
    print(f"All {len(paths)} SVG file(s) passed (UTF-8 + XML). Chromium-safe baseline.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
