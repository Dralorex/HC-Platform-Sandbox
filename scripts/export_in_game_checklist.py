#!/usr/bin/env python3
"""Export copy-paste friendly checklist from docs/IN_GAME_FULL_TEST.md."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "docs" / "IN_GAME_FULL_TEST.md"
OUT_TXT = ROOT / "docs" / "IN_GAME_FULL_TEST_CHECKLIST.txt"
OUT_CSV = ROOT / "docs" / "IN_GAME_FULL_TEST_CHECKLIST.csv"

SKIP_FIRST_COL = {
    "#", "Step", "Old", "Abbrev", "Segment", "Symptom", "Result", "Phase",
    "---", "--------", "-----",
}


def parse_tables(text: str):
    current_phase = ""
    current_sub = ""
    rows = []

    for line in text.splitlines():
        if line.startswith("## Phase"):
            current_phase = line.replace("## ", "").strip()
        elif line.startswith("### "):
            current_sub = line.replace("### ", "").strip()
        elif line.startswith("|") and "---" not in line:
            parts = [p.strip() for p in line.split("|")]
            if len(parts) < 6:  # empty, col1, col2, col3, col4, empty
                continue
            num, action, expected, code = parts[1], parts[2], parts[3], parts[4]
            if num in SKIP_FIRST_COL or not num:
                continue
            if num.replace(".", "").isdigit() is False and not num.isdigit():
                # skip header rows like "Deploy JARs" without numeric first col - actually num is digit
                try:
                    float(num)  # allow "1" only
                except ValueError:
                    if not num.isdigit():
                        continue
            rows.append((current_phase, current_sub, num, action, expected, code))
    return rows


def esc_csv(s: str) -> str:
    return '"' + s.replace('"', '""') + '"'


def main():
    text = SRC.read_text(encoding="utf-8")
    rows = parse_tables(text)

    header = """HOLLOW CROWN PLATFORM — IN-GAME QA CHECKLIST
================================================================================
Paper 1.21.4 · two accounts (OP + normal player)
Mark each line: ✅ pass · ❌ fail · ⚠️ skipped · ⬜ not tested

JARs (order): foundation/target/HC-Foundation-*.jar
              hub/target/HollowCrownHub-*.jar
              minigames/iron-legacy/target/HCIronLegacy-*.jar
Do NOT deploy hollowcrown-api.

KNOWN GAP: /hch queue join enqueues only — no auto limbo/match.

HOW TO USE WHEN SOMETHING FAILS
1. Find the failing line below.
2. Read "Code path" left to right (→ = call order).
3. Check PERSIST / API / IL-HUB segments for cross-plugin issues.

================================================================================

"""

    txt_lines = [header]
    for phase, sub, num, action, expected, code in rows:
        txt_lines.append(
            f"⬜  Phase: {phase}\n"
            f"    Subsection: {sub}\n"
            f"    Test #{num}: {action}\n"
            f"    Expected: {expected}\n"
            f"    Code path: {code}\n"
        )

    txt_lines.append("""
================================================================================
15-MINUTE SMOKE (minimum)
⬜ Phase 0 console checks
⬜ /hch sethub → /hub → /hch join → join lobby
⬜ /hch party create + invite + accept
⬜ Compass → Game Lobby → limbo → /il start
⬜ /il stop → /hub

BUG REPORT TEMPLATE
What you did:
Expected:
Actual:
Console (Foundation / Hub / IL):
/plugins:
Template key / world:
Checklist test #:
Code path:
================================================================================
""")

    OUT_TXT.write_text("\n".join(txt_lines), encoding="utf-8")

    csv_lines = ["Phase,Subsection,TestNum,Status,Action,Expected,Code path"]
    for phase, sub, num, action, expected, code in rows:
        csv_lines.append(
            ",".join([
                esc_csv(phase), esc_csv(sub), esc_csv(num), esc_csv("⬜"),
                esc_csv(action), esc_csv(expected), esc_csv(code),
            ])
        )
    OUT_CSV.write_text("\n".join(csv_lines) + "\n", encoding="utf-8")

    print(f"Parsed {len(rows)} tests")
    print(f"Wrote {OUT_TXT}")
    print(f"Wrote {OUT_CSV}")


if __name__ == "__main__":
    main()
