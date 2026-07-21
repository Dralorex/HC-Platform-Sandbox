#!/usr/bin/env python3
"""Export Google Sheets–ready CSV from docs/IN_GAME_FULL_TEST.md."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "docs" / "IN_GAME_FULL_TEST.md"
OUT_CSV = ROOT / "docs" / "IN_GAME_QA_GoogleSheets.csv"
OUT_HOW = ROOT / "docs" / "IN_GAME_QA_GoogleSheets_IMPORT.txt"

SKIP_FIRST_COL = {
    "#", "Step", "Old", "Abbrev", "Segment", "Symptom", "Result", "Phase",
}


def parse_tables(text: str):
    current_phase = ""
    current_sub = ""
    rows = []
    order = 0

    for line in text.splitlines():
        if line.startswith("## Phase"):
            current_phase = line.replace("## ", "").strip()
        elif line.startswith("### "):
            current_sub = line.replace("### ", "").strip()
        elif line.startswith("|") and "---" not in line:
            parts = [p.strip() for p in line.split("|")]
            if len(parts) < 6:
                continue
            num, action, expected, code = parts[1], parts[2], parts[3], parts[4]
            if num in SKIP_FIRST_COL or not num:
                continue
            try:
                float(num)
            except ValueError:
                continue
            order += 1
            rows.append((order, current_phase, current_sub, num, action, expected, code))
    return rows


def split_code_path(code: str):
    """Pull optional segments out of the long Code path cell for sheet filters."""
    persist = perm = api = ilhub = ""
    main = code
    for key, attr in (
        (" · PERSIST:", "persist"),
        (" · PERM:", "perm"),
        (" · API:", "api"),
        (" · IL/HUB:", "ilhub"),
    ):
        if key in main:
            before, after = main.split(key, 1)
            # further split after on next segment markers
            rest = after
            for nxt in (" · PERSIST:", " · PERM:", " · API:", " · IL/HUB:"):
                if nxt in rest and not rest.startswith(nxt.strip()):
                    # only split if there's another segment later
                    pass
            main = before
            # peel nested tags from after
            chunks = [rest]
            bag = {"persist": "", "perm": "", "api": "", "ilhub": ""}
            working = rest
            # re-parse from original code for cleanliness
            break

    bag = {"persist": "", "perm": "", "api": "", "ilhub": ""}
    working = code
    markers = [
        (" · PERSIST:", "persist"),
        (" · PERM:", "perm"),
        (" · API:", "api"),
        (" · IL/HUB:", "ilhub"),
    ]
    # find first marker positions
    positions = []
    for m, name in markers:
        i = working.find(m)
        if i >= 0:
            positions.append((i, m, name))
    positions.sort()
    if not positions:
        return working, "", "", "", ""
    chain = working[: positions[0][0]].strip()
    for idx, (i, m, name) in enumerate(positions):
        start = i + len(m)
        end = positions[idx + 1][0] if idx + 1 < len(positions) else len(working)
        bag[name] = working[start:end].strip()
    return chain, bag["persist"], bag["perm"], bag["api"], bag["ilhub"]


def esc(s: str) -> str:
    return '"' + s.replace('"', '""') + '"'


def main():
    rows = parse_tables(SRC.read_text(encoding="utf-8"))

    # Google Sheets friendly headers (Status first for checkbox / data validation)
    headers = [
        "Order",
        "Status",  # leave blank; set Data validation: Pass / Fail / Skip / Blocked
        "Phase",
        "Subsection",
        "Test #",
        "Action",
        "Expected",
        "Code path (entry → calls)",
        "PERSIST (files/DB)",
        "PERM",
        "API contract",
        "IL/HUB link",
        "Notes",
        "Tester",
        "Date",
    ]

    lines = [",".join(esc(h) for h in headers)]
    for order, phase, sub, num, action, expected, code in rows:
        chain, persist, perm, api, ilhub = split_code_path(code)
        lines.append(
            ",".join(
                esc(x)
                for x in (
                    str(order),
                    "",  # Status blank for Sheets
                    phase,
                    sub,
                    num,
                    action,
                    expected,
                    chain,
                    persist,
                    perm,
                    api,
                    ilhub,
                    "",
                    "",
                    "",
                )
            )
        )

    # UTF-8 with BOM so Google Drive / Sheets detects encoding correctly
    body = "\ufeff" + "\n".join(lines) + "\n"
    OUT_CSV.write_text(body, encoding="utf-8")

    OUT_HOW.write_text(
        """Google Sheets — import the Hollow Crown in-game QA checklist
================================================================================

FILE TO UPLOAD
  docs/IN_GAME_QA_GoogleSheets.csv

IMPORT (recommended)
  1. Open https://sheets.google.com → Blank spreadsheet
  2. File → Import → Upload → select IN_GAME_QA_GoogleSheets.csv
  3. Import location: Replace spreadsheet
  4. Separator type: Comma
  5. Convert text to numbers/dates: No
  6. Click Import data

OPTIONAL CLEANUP IN SHEETS
  • Freeze header: View → Freeze → 1 row
  • Freeze Status + Phase: freeze first 3 columns
  • Filter: Data → Create a filter
  • Status dropdown:
      Select column B (Status) → Data → Data validation
      Criteria: Dropdown
      Options: Pass, Fail, Skip, Blocked
  • Conditional formatting (optional):
      Status = Pass  → green
      Status = Fail  → red
      Status = Skip  → gray
      Status = Blocked → orange

COLUMNS
  Order          Sort order (1…N)
  Status         Fill with Pass/Fail/Skip/Blocked
  Phase          Phase 0–10
  Subsection     e.g. 1.2 Template registration
  Test #         Number within subsection
  Action         What to do in-game
  Expected       What should happen
  Code path      Java call chain (→ order)
  PERSIST        YAML / SQL / world folders
  PERM           Bukkit permission
  API contract   api/ SPI if involved
  IL/HUB link    Cross-plugin note
  Notes          Free text for bugs
  Tester         Initials
  Date           Test date

REGENERATE AFTER DOC EDITS
  python3 scripts/export_google_sheets_qa.py

SOURCE OF TRUTH
  docs/IN_GAME_FULL_TEST.md
================================================================================
""",
        encoding="utf-8",
    )

    print(f"Wrote {OUT_CSV} ({len(rows)} tests)")
    print(f"Wrote {OUT_HOW}")


if __name__ == "__main__":
    main()
