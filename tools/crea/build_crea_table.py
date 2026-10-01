#!/usr/bin/env python3
"""
Generates app/src/main/assets/crea_foods.tsv from the CREA food composition tables
(https://www.alimentinutrizione.it/tabelle-nutrizionali).

The portal offers no bulk download: the script reads the food list and then each food's
page, one request per second, with a local cache (tools/crea/.cache) so later runs don't
download anything again.

Data source: CREA - Centro di ricerca Alimenti e Nutrizione, Tabelle di composizione
degli alimenti (2019 update). The site's terms of use ask to cite the source.

Usage:  python3 tools/crea/build_crea_table.py [--limit N]
"""
import argparse
import html
import pathlib
import re
import sys
import time
import urllib.request

BASE = "https://www.alimentinutrizione.it"
INDEX_URL = f"{BASE}/tabelle-nutrizionali/ricerca-per-alimento"
USER_AGENT = "PhotoCal/0.1 (personal Android app; CREA table import)"
DELAY_SECONDS = 1.0

ROOT = pathlib.Path(__file__).resolve().parents[2]
CACHE_DIR = pathlib.Path(__file__).resolve().parent / ".cache"
OUTPUT = ROOT / "app/src/main/assets/crea_foods.tsv"

# Label on the page -> TSV column
NUTRIENTS = {
    "Energia (kcal)": "kcal",
    "Proteine (g)": "protein_g",
    "Lipidi (g)": "fat_g",
    "Carboidrati disponibili (g)": "carbs_g",
    "Fibra totale (g)": "fiber_g",
    "Zuccheri solubili (g)": "sugars_g",
    "Sodio (mg)": "sodium_mg",
}
# Salt (g) = sodium (mg) x 2.5 / 1000, as on food labels
COLUMNS = ["code", "name", "category", "edible_pct", "portion_g", "kcal", "protein_g", "fat_g", "carbs_g", "fiber_g",
           "sugars_g", "salt_g"]


def fetch(url: str, cache_name: str) -> str:
    cached = CACHE_DIR / cache_name
    if cached.exists():
        return cached.read_text(encoding="utf-8")
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        text = response.read().decode("utf-8", errors="replace")
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    cached.write_text(text, encoding="utf-8")
    time.sleep(DELAY_SECONDS)
    return text


def text_lines(page: str) -> list[str]:
    page = re.sub(r"(?s)<(script|style)[^>]*>.*?</\1>", "", page)
    text = html.unescape(re.sub(r"<[^>]+>", "\n", page))
    return [line.strip() for line in text.split("\n") if line.strip()]


def number(value: str) -> float | None:
    """'62.8' -> 62.8, 'tr' (traces) -> 0, anything else -> None."""
    value = value.replace(",", ".").strip()
    if value.lower() == "tr":
        return 0.0
    try:
        return float(value)
    except ValueError:
        return None


def after(lines: list[str], label: str, offset: int = 1) -> str | None:
    try:
        return lines[lines.index(label) + offset]
    except (ValueError, IndexError):
        return None


def parse_food(code: str, page: str) -> dict | None:
    title = re.search(r"<title>\s*AlimentiNUTrizione\s*-\s*(.*?)\s*</title>", page, re.S)
    if not title:
        return None
    lines = text_lines(page)
    row = {
        "code": code,
        "name": html.unescape(title.group(1)).strip(),
        "category": after(lines, "Categoria") or "",
        "edible_pct": number((after(lines, "Parte Edibile") or "").replace("%", "")),
        "portion_g": number((after(lines, "Porzione") or "").replace("g", "")),
    }
    for label, column in NUTRIENTS.items():
        # Nutrient row: label, unit of measure, value per 100 g, ...
        row[column] = number(after(lines, label, offset=2) or "")
    sodium = row.pop("sodium_mg")
    row["salt_g"] = round(sodium * 2.5 / 1000, 2) if sodium is not None else None
    return row if row["kcal"] is not None else None


def format_value(value) -> str:
    if value is None:
        return ""
    if isinstance(value, float):
        return f"{value:g}"
    return str(value).replace("\t", " ")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, help="only the first N foods (for testing)")
    args = parser.parse_args()

    index = fetch(INDEX_URL, "index.html")
    codes = sorted(set(re.findall(r'href="/tabelle-nutrizionali/(\d+)"', index)))
    if args.limit:
        codes = codes[: args.limit]
    print(f"{len(codes)} foods to read", file=sys.stderr)

    rows, skipped = [], []
    for position, code in enumerate(codes, start=1):
        page = fetch(f"{BASE}/tabelle-nutrizionali/{code}", f"{code}.html")
        row = parse_food(code, page)
        if row:
            rows.append(row)
        else:
            skipped.append(code)
        if position % 50 == 0:
            print(f"  {position}/{len(codes)}", file=sys.stderr)

    rows.sort(key=lambda r: r["name"].lower())
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT.open("w", encoding="utf-8") as out:
        out.write("# Source: CREA - Centro di ricerca Alimenti e Nutrizione, Tabelle di composizione degli alimenti "
                  "(https://www.alimentinutrizione.it). Values per 100 g of edible portion.\n")
        out.write("\t".join(COLUMNS) + "\n")
        for row in rows:
            out.write("\t".join(format_value(row[c]) for c in COLUMNS) + "\n")
    print(f"Wrote {len(rows)} foods to {OUTPUT.relative_to(ROOT)}; skipped: {skipped or 'none'}",
          file=sys.stderr)


if __name__ == "__main__":
    main()
