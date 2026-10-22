#!/usr/bin/env python3
"""Static guardrails: Login palette is canonical; Activity/Controller UI is XML-bound.

Run from any directory:
    python LibraryOnline/tools/check_ui_boundaries.py
"""
from __future__ import annotations

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
UI = ROOT / "app" / "src" / "main" / "java" / "com" / "example" / "libraryonline" / "ui"

CANONICAL = {
    "library_background": "#F5F7FC",
    "library_surface": "#FFFFFFFF",
    "library_border": "#DCE3EF",
    "library_text": "#182230",
    "library_muted": "#68758A",
    "library_blue": "#315EFB",
}
ALIASES = {
    "background": "@color/library_background",
    "surface": "@color/library_surface",
    "input_background": "@color/library_surface",
    "border": "@color/library_border",
    "text_primary": "@color/library_text",
    "text_secondary": "@color/library_muted",
    "admin_screen_primary": "@color/library_blue",
    "online_forest": "@color/library_blue",
}
NEW_WIDGET = re.compile(
    r"\bnew\s+(?:(?:android\.widget|android\.view)\.)?"
    r"(?:TextView|Button|EditText|LinearLayout|FrameLayout|ScrollView|HorizontalScrollView)\s*\("
)
HARDCODED_COLOR = re.compile(r"#[A-Fa-f0-9]{6}(?:[A-Fa-f0-9]{2})?\b")

errors = []
root = ET.parse(RES / "values" / "colors.xml").getroot()
color_map = {node.attrib.get("name"): (node.text or "").strip() for node in root.findall("color")}
for name, expected in {**CANONICAL, **ALIASES}.items():
    if color_map.get(name, "").upper() != expected.upper():
        errors.append(f"colors.xml: {name} should be {expected}, got {color_map.get(name)}")

for file in sorted((RES / "layout").glob("*.xml")):
    try:
        ET.parse(file)
    except ET.ParseError as exc:
        errors.append(f"Invalid XML {file.relative_to(ROOT)}: {exc}")
    if HARDCODED_COLOR.search(file.read_text(encoding="utf-8")):
        errors.append(f"Hardcoded hex color in {file.relative_to(ROOT)}")

for file in sorted(UI.rglob("*.java")):
    code = file.read_text(encoding="utf-8")
    if NEW_WIDGET.search(code):
        errors.append(f"Imperative widget constructor in {file.relative_to(ROOT)}")

if errors:
    print("UI boundaries FAILED:")
    for error in errors:
        print(" -", error)
    sys.exit(1)
print("UI boundaries OK: canonical Login colors, layout XML, no widgets constructed in ui/")
