#!/usr/bin/env python3
"""Export Android vector drawables (res/drawable/*.xml) to SVG under icon/."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

MODE_DRAWABLES = {
    "keyboard_mouse": "modes/keyboard_mouse.svg",
    "keyboard_mouse_pro": "modes/keyboard_mouse_pro.svg",
    "ic_presentation": "modes/presentation.svg",
    "gamepad": "modes/gamepad.svg",
    "three_dots": "modes/shortcuts_hub.svg",
    "macros": "modes/macros.svg",
    "voice": "modes/voice.svg",
}

KM_PRO_DRAWABLES = {
    "ic_km_pro_submode_keyboard": "km-pro/submode_keyboard.svg",
    "ic_km_pro_submode_numpad": "km-pro/submode_numpad.svg",
    "ic_km_pro_submode_compose": "km-pro/submode_compose.svg",
}

BRAND_DRAWABLES = {
    "ic_keycmd_wordmark": "brand/keycmd_wordmark.svg",
    "ic_openterface_wordmark": "brand/openterface_wordmark.svg",
}

CATEGORIZED = {**MODE_DRAWABLES, **KM_PRO_DRAWABLES, **BRAND_DRAWABLES}

COLOR_MAP = {
    "@android:color/black": "#000000",
    "@android:color/white": "#FFFFFF",
    "#FF000000": "#000000",
    "#FFFFFFFF": "#FFFFFF",
}


def android_attr(element: ET.Element, name: str) -> str | None:
    return element.get(f"{ANDROID_NS}{name}")


def normalize_color(value: str | None) -> str:
    if not value:
        return "currentColor"
    value = value.strip()
    if value in COLOR_MAP:
        return COLOR_MAP[value]
    if re.fullmatch(r"#[0-9A-Fa-f]{8}", value):
        return "#" + value[3:]
    if re.fullmatch(r"#[0-9A-Fa-f]{6}", value):
        return value
    if value.startswith("?"):
        return "currentColor"
    if value.startswith("@"):
        return "currentColor"
    return value


def parse_number(value: str | None, default: float = 0.0) -> float:
    if value is None:
        return default
    value = value.replace("dp", "").strip()
    try:
        return float(value)
    except ValueError:
        return default


def group_transform(element: ET.Element) -> str | None:
    tx = parse_number(android_attr(element, "translateX"), 0.0)
    ty = parse_number(android_attr(element, "translateY"), 0.0)
    sx = parse_number(android_attr(element, "scaleX"), 1.0)
    sy = parse_number(android_attr(element, "scaleY"), 1.0)
    rot = parse_number(android_attr(element, "rotation"), 0.0)
    px = parse_number(android_attr(element, "pivotX"), 0.0)
    py = parse_number(android_attr(element, "pivotY"), 0.0)

    parts: list[str] = []
    if tx or ty:
        parts.append(f"translate({tx:g},{ty:g})")
    if rot:
        parts.append(f"rotate({rot:g},{px:g},{py:g})")
    if sx != 1.0 or sy != 1.0:
        parts.append(f"scale({sx:g},{sy:g})")
    return " ".join(parts) if parts else None


def path_to_svg(element: ET.Element) -> str:
    d = android_attr(element, "pathData")
    if not d:
        return ""

    fill = normalize_color(android_attr(element, "fillColor"))
    stroke = normalize_color(android_attr(element, "strokeColor"))
    stroke_width = android_attr(element, "strokeWidth")
    line_cap = android_attr(element, "strokeLineCap")
    fill_type = android_attr(element, "fillType") or "nonZero"

    attrs: list[str] = [f'd="{d}"']
    if stroke and stroke != "currentColor":
        attrs.append('fill="none"')
        attrs.append(f'stroke="{stroke}"')
        if stroke_width:
            attrs.append(f'stroke-width="{stroke_width}"')
        if line_cap:
            attrs.append(f'stroke-linecap="{line_cap}"')
    elif fill_type == "evenOdd":
        attrs.append(f'fill="{fill}"')
        attrs.append('fill-rule="evenodd"')
    else:
        attrs.append(f'fill="{fill}"')

    return f"<path {' '.join(attrs)}/>"


def element_to_svg(element: ET.Element) -> str:
    tag = element.tag.split("}")[-1] if "}" in element.tag else element.tag
    if tag == "path":
        return path_to_svg(element)
    if tag == "group":
        transform = group_transform(element)
        inner = "".join(element_to_svg(child) for child in element)
        if transform:
            return f'<g transform="{transform}">{inner}</g>'
        return f"<g>{inner}</g>"
    return ""


def vector_xml_to_svg(xml_path: Path) -> str | None:
    try:
        tree = ET.parse(xml_path)
    except ET.ParseError as exc:
        print(f"  skip (parse error): {xml_path.name}: {exc}", file=sys.stderr)
        return None

    root = tree.getroot()
    if root.tag.split("}")[-1] != "vector":
        return None

    vw = android_attr(root, "viewportWidth") or "24"
    vh = android_attr(root, "viewportHeight") or "24"
    width = android_attr(root, "width") or f"{vw}dp"
    height = android_attr(root, "height") or f"{vh}dp"

    body = "".join(element_to_svg(child) for child in root)
    if not body.strip():
        print(f"  skip (no paths): {xml_path.name}", file=sys.stderr)
        return None

    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        f'<svg xmlns="http://www.w3.org/2000/svg" '
        f'viewBox="0 0 {vw} {vh}" width="{width}" height="{height}">\n'
        f"{body}\n"
        "</svg>\n"
    )


def export_drawable(drawable_dir: Path, icon_dir: Path, stem: str, rel_out: str) -> bool:
    xml_path = drawable_dir / f"{stem}.xml"
    if not xml_path.is_file():
        print(f"  missing: {xml_path}", file=sys.stderr)
        return False

    svg = vector_xml_to_svg(xml_path)
    if svg is None:
        return False

    out_path = icon_dir / rel_out
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(svg, encoding="utf-8")
    return True


def main() -> int:
    script_dir = Path(__file__).resolve().parent
    project_root = script_dir.parent
    drawable_dir = project_root / "app/src/main/res/drawable"
    icon_dir = project_root / "icon"

    if not drawable_dir.is_dir():
        print(f"drawable dir not found: {drawable_dir}", file=sys.stderr)
        return 1

    icon_dir.mkdir(parents=True, exist_ok=True)
    for sub in ("modes", "km-pro", "brand", "ui"):
        (icon_dir / sub).mkdir(parents=True, exist_ok=True)

    ok = 0
    skipped = 0

    print("Categorized icons:")
    for stem, rel_out in sorted(CATEGORIZED.items()):
        if export_drawable(drawable_dir, icon_dir, stem, rel_out):
            print(f"  {rel_out}")
            ok += 1
        else:
            skipped += 1

    print("\nUI icons (all other vectors):")
    categorized_stems = set(CATEGORIZED.keys())
    for xml_path in sorted(drawable_dir.glob("*.xml")):
        stem = xml_path.stem
        if stem in categorized_stems:
            continue
        rel_out = f"ui/{stem}.svg"
        if export_drawable(drawable_dir, icon_dir, stem, rel_out):
            ok += 1
        else:
            skipped += 1

    print(f"\nDone: {ok} SVG files written under {icon_dir}")
    if skipped:
        print(f"Skipped: {skipped} (non-vector or empty)", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
