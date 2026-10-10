#!/usr/bin/env python3
"""Prints the GitHub release notes of a version: the changelog shown in the app.

Usage: release_notes.py 1.3.0

The notes come from the "changelog_1_3_0" string arrays of the app, the same text it shows after
an update, so the two never drift apart. GitHub shows the English notes (values-en); the Italian
ones (values) follow in an HTML comment, hidden on GitHub but read by the app to show the notes
of a new version in its own language (GitHubReleasesClient.parseNotes). Keep the two in sync.
Fails when an array is missing or empty.
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path(__file__).resolve().parents[2] / "app/src/main/res"
ENGLISH = RES / "values-en/strings.xml"
# Language tag -> strings file of the other languages, hidden in the notes
HIDDEN = {"it": RES / "values/strings.xml"}
HIDDEN_START = "<!-- photocal-notes:"
HIDDEN_END = "-->"


def android_unescape(text: str) -> str:
    """Undoes the escapes of Android string resources (\\' \\" \\n \\t \\\\)."""
    out, i = [], 0
    while i < len(text):
        c = text[i]
        if c == "\\" and i + 1 < len(text):
            nxt = text[i + 1]
            out.append({"n": "\n", "t": "\t"}.get(nxt, nxt))
            i += 2
        else:
            out.append(c)
            i += 1
    return " ".join("".join(out).split())


def notes(strings: Path, version: str) -> list[str]:
    name = "changelog_" + version.replace(".", "_")
    array = ET.parse(strings).getroot().find(f"string-array[@name='{name}']")
    items = [android_unescape("".join(item.itertext())) for item in array.findall("item")] if array is not None else []
    if not items:
        sys.exit(f"No '{name}' string array in {strings}: add the changelog of {version}")
    if any(HIDDEN_END in item for item in items):
        sys.exit(f"'{HIDDEN_END}' would end the hidden notes early: remove it from '{name}' in {strings}")
    return items


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit("usage: release_notes.py <version>")
    version = sys.argv[1].removeprefix("v")
    print(f"## What's new in PhotoKCal {version}\n")
    for item in notes(ENGLISH, version):
        print(f"- {item}")
    for tag, strings in HIDDEN.items():
        print(f"\n{HIDDEN_START}{tag}")
        for item in notes(strings, version):
            print(f"- {item}")
        print(HIDDEN_END)


if __name__ == "__main__":
    main()
