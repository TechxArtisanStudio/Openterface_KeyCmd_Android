#!/usr/bin/env python3
"""
One-off generator: reads app/src/main/res/values/strings.xml and writes
values-{es,fr,de,ja,ko,it,ru,zh-rCN}/strings.xml using translate.googleapis.com (client=gtx).

Skips CDATA / raw-HTML strings (help blocks) — copies English verbatim.
Skips empty bodies.
"""
from __future__ import annotations

import json
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/res/values/strings.xml"
LOCALES = {
    "es": "es",
    "fr": "fr",
    "de": "de",
    "ja": "ja",
    "ko": "ko",
    "it": "it",
    "ru": "ru",
    "zh-rCN": "zh-CN",
}
# Rare separator (Unicode record separator); must not appear in source strings.
BATCH_SEP = "\u241e"
BATCH_SIZE = 18
CACHE: dict[tuple[str, str], str] = {}
SLEEP_SEC = 0.06


def gtx_translate(text: str, target: str) -> str:
    text = text.strip()
    if not text:
        return text
    key = (target, text)
    if key in CACHE:
        return CACHE[key]
    q = urllib.parse.quote(text, safe="")
    url = (
        "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl="
        + target
        + "&dt=t&q="
        + q
    )
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=45) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    out = "".join(part[0] for part in data[0])
    CACHE[key] = out
    time.sleep(SLEEP_SEC)
    return out


def gtx_translate_batch(parts: list[str], target: str) -> list[str]:
    """Translate multiple strings in one HTTP request; returns same length."""
    if not parts:
        return []
    joined = BATCH_SEP.join(parts)
    translated = gtx_translate(joined, target)
    out = translated.split(BATCH_SEP)
    if len(out) != len(parts):
        # Fallback per-item if batch boundary failed
        return [gtx_translate(p, target) for p in parts]
    return out


def split_resources(xml: str) -> tuple[str, list[str]]:
    """Return prolog + list of full resource element strings (string or string-array)."""
    m = re.search(r"<resources>\s*", xml)
    if not m:
        raise SystemExit("no <resources>")
    end = xml.rfind("</resources>")
    inner = xml[m.end() : end]
    parts: list[str] = []
    pos = 0
    while pos < len(inner):
        # string-array MUST be matched before string — otherwise "<string-array"
        # is parsed as "<string" and the chunk runs until the next "</string>".
        m2 = re.search(
            r"<(string-array|string)\b", inner[pos:]
        )
        if not m2:
            break
        start = pos + m2.start()
        tag = m2.group(1)
        open_end = inner.find(">", start)
        if open_end < 0:
            break
        if tag == "string-array":
            close = inner.find("</string-array>", open_end)
            if close < 0:
                break
            chunk = inner[start : close + len("</string-array>")]
            pos = close + len("</string-array>")
        elif tag == "string":
            close = inner.find("</string>", open_end)
            if close < 0:
                break
            chunk = inner[start : close + len("</string>")]
            pos = close + len("</string>")
        else:
            raise SystemExit(f"unknown tag {tag!r}")
        parts.append(chunk.strip())
    prolog = xml[: m.end()]
    return prolog, parts


def escape_apostrophes_for_android_string_xml(s: str) -> str:
    """AAPT2 requires \' for apostrophes in plain (non-CDATA) string bodies."""
    out: list[str] = []
    for i, ch in enumerate(s):
        if ch == "'" and (i == 0 or s[i - 1] != "\\"):
            out.append("\\'")
        else:
            out.append(ch)
    return "".join(out)


def apply_translated_string(chunk: str, translated: str) -> str:
    m = re.match(
        r'(<string\s+name="([^"]+)"([^>]*)>)([\s\S]*?)(</string>)',
        chunk,
    )
    if not m:
        return chunk
    open_tag, name, attrs, _body, close = m.groups()
    out = (
        translated.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "\\\"")
    )
    # Machine translation sometimes doubles backslashes before quotes.
    out = re.sub(r'\\{2}"', r'\\"', out)
    # gtx / some locales corrupt newline escape \n -> \N (invalid in AAPT2).
    out = out.replace("\\N", "\\n")
    # Two consecutive \" breaks AAPT2 for keys like Quotation_Button; default is \"" (escaped + literal ").
    out = re.sub(r'\\"\\"', r'\\""', out)
    out = escape_apostrophes_for_android_string_xml(out)
    return f"{open_tag}{out}{close}"


def translate_string_array(chunk: str, tl: str) -> str:
    m = re.match(
        r'(<string-array\s+name="([^"]+)"([^>]*)>)([\s\S]*?)(</string-array>)',
        chunk,
    )
    if not m:
        return chunk
    open_tag, name, attrs, inner, close = m.groups()
    items = re.findall(r"<item>([\s\S]*?)</item>", inner)
    if not items:
        return chunk
    new_items = []
    for it in items:
        t = it.strip()
        if not t:
            new_items.append("<item></item>")
            continue
        try:
            nt = gtx_translate(t, tl)
        except Exception:
            nt = t
        nt = (
            nt.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace('"', "\\\"")
        )
        nt = nt.replace("\\N", "\\n")
        nt = re.sub(r'\\"\\"', r'\\""', nt)
        nt = escape_apostrophes_for_android_string_xml(nt)
        new_items.append(f"<item>{nt}</item>")
    body = "\n        ".join(new_items)
    return f"{open_tag}\n        {body}\n    {close}"


def build_locale(tl_code: str, out_dir: Path, parts: list[str]) -> None:
    out: list[str] = []
    batch: list[str] = []
    batch_chunks: list[str] = []

    def flush_batch() -> None:
        nonlocal batch, batch_chunks
        if not batch:
            return
        try:
            trs = gtx_translate_batch(batch, tl_code)
        except Exception as e:
            print("batch fail", e, "-> per string")
            trs = [gtx_translate(t, tl_code) for t in batch]
        for ch, tr in zip(batch_chunks, trs):
            out.append("    " + apply_translated_string(ch, tr))
        batch = []
        batch_chunks = []

    for chunk in parts:
        if chunk.lstrip().startswith("<string-array"):
            flush_batch()
            out.append("    " + translate_string_array(chunk, tl_code))
            continue
        m = re.match(
            r'(<string\s+name="([^"]+)"([^>]*)>)([\s\S]*?)(</string>)',
            chunk,
        )
        if not m:
            flush_batch()
            out.append("    " + chunk)
            continue
        open_tag, name, attrs, body, close = m.groups()
        raw = body.strip()
        if not raw or raw.startswith("<![CDATA[") or ("<" in raw and ">" in raw and not raw.startswith("&")):
            flush_batch()
            out.append("    " + chunk)
            continue
        src = (
            raw.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
        )
        if not src.strip():
            flush_batch()
            out.append("    " + chunk)
            continue
        batch.append(src)
        batch_chunks.append(chunk)
        if len(batch) >= BATCH_SIZE:
            flush_batch()
    flush_batch()
    body = "\n".join(out)
    final = '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + body + "\n</resources>\n"
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "strings.xml").write_text(final, encoding="utf-8")
    print("Wrote", out_dir / "strings.xml")


def main() -> None:
    xml = SRC.read_text(encoding="utf-8")
    prolog, parts = split_resources(xml)
    print("elements", len(parts))
    for folder, tl in LOCALES.items():
        out = ROOT / "app/src/main/res" / f"values-{folder}"
        print("===", folder, tl, "===")
        build_locale(tl, out, parts)


if __name__ == "__main__":
    main()
