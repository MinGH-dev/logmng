#!/usr/bin/env bash
# List requirement docs that are NOT yet in TOPIC-INDEX.md, or auto-add one doc.
# Usage:
#   ./scripts/generate-requirements-index.sh
#   ./scripts/generate-requirements-index.sh --doc docs/requirements/yyyyMMdd-name.md

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEFAULT_REQ_DIR="$SCRIPT_DIR/../docs/requirements"
REQ_DIR="${REQUIREMENTS_DIR_OVERRIDE:-$DEFAULT_REQ_DIR}"
INDEX="${TOPIC_INDEX_PATH_OVERRIDE:-$REQ_DIR/TOPIC-INDEX.md}"

python3 - "$REQ_DIR" "$INDEX" "$@" <<'PY'
from pathlib import Path
import re
import sys

req_dir = Path(sys.argv[1]).resolve()
index_path = Path(sys.argv[2]).resolve()
args = sys.argv[3:]

REQ_DOC_RE = re.compile(r"^\d{8}-.+\.md$")
SECTION_RE = re.compile(r"^##\s+(.*)$")
DOC_LINE_RE = re.compile(r"^-\s+([0-9]{8}-.+?)\s+\|\s+(.+?)\s*$")


def die(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def normalize_doc_path(input_path: str) -> Path:
    candidate = Path(input_path)
    if not candidate.is_absolute():
        candidate = (Path.cwd() / candidate).resolve()
    if not candidate.exists():
        die(f"Error: requirement doc not found: {input_path}")
    return candidate


def is_requirement_doc(path: Path) -> bool:
    return path.name != "TOPIC-INDEX.md" and bool(REQ_DOC_RE.match(path.name))


def parse_args(raw_args):
    target_doc = None
    idx = 0
    while idx < len(raw_args):
        arg = raw_args[idx]
        if arg == "--doc":
            if idx + 1 >= len(raw_args):
                die("Error: --doc requires a file path.")
            target_doc = raw_args[idx + 1]
            idx += 2
            continue
        die(f"Error: unsupported option: {arg}")
    return target_doc


def read_lines(path: Path):
    return path.read_text(encoding="utf-8").splitlines()


def title_for_doc(path: Path) -> str:
    for line in read_lines(path):
        if line.startswith("# "):
            return line[2:].strip()
    return "(no title)"


def summary_for_doc(path: Path) -> str:
    lines = read_lines(path)
    title = title_for_doc(path)
    summary = ""

    for idx, line in enumerate(lines):
        if line.strip() == "### Requirement description":
            for candidate in lines[idx + 1:]:
                stripped = candidate.strip()
                if not stripped:
                    continue
                if stripped.startswith("### ") or stripped.startswith("## "):
                    break
                summary = stripped
                break
            break

    if not summary:
        summary = re.sub(r"^\d{8}\s*-\s*", "", title).strip() or title

    return re.sub(r"\s+", " ", summary).strip()


def parse_sections(lines):
    sections = []
    for idx, line in enumerate(lines):
        match = SECTION_RE.match(line)
        if not match:
            continue
        tokens = [token.strip() for token in match.group(1).split("|") if token.strip()]
        sections.append({"start": idx, "heading": line, "tokens": tokens})
    for idx, section in enumerate(sections):
        section["end"] = sections[idx + 1]["start"] if idx + 1 < len(sections) else len(lines)
    return sections


def doc_ids_in_index(lines):
    ids = set()
    for line in lines:
        match = DOC_LINE_RE.match(line)
        if match:
            ids.add(match.group(1))
    return ids


def pick_section(sections, doc_id: str, summary: str, title: str):
    searchable = " ".join(
        [
            doc_id.casefold(),
            doc_id[9:].replace("-", " ").casefold(),
            summary.casefold(),
            re.sub(r"^\d{8}\s*-\s*", "", title).casefold(),
        ]
    )
    fallback = None
    best = None
    best_score = -1

    for section in sections:
        score = 0
        for token in section["tokens"]:
            normalized = token.casefold()
            if normalized == "misc":
                fallback = section
            if len(normalized) < 2:
                continue
            if normalized in searchable:
                score += len(normalized)
        if score > best_score:
            best_score = score
            best = section

    if best is not None and best_score > 0:
        return best
    if fallback is not None:
        return fallback
    return sections[-1] if sections else None


def insert_doc_line(index_lines, section, doc_line):
    insert_at = section["end"]
    while insert_at > section["start"] + 1 and index_lines[insert_at - 1].strip() == "":
        insert_at -= 1
    index_lines.insert(insert_at, doc_line)


def ensure_doc_in_index(doc_path: Path) -> None:
    if not is_requirement_doc(doc_path):
        return

    index_lines = read_lines(index_path)
    sections = parse_sections(index_lines)
    if not sections:
        die("Error: TOPIC-INDEX.md does not contain any topic sections.")

    doc_id = doc_path.stem
    if doc_id in doc_ids_in_index(index_lines):
        print(f"Requirement doc already indexed: {doc_id}")
        return

    title = title_for_doc(doc_path)
    summary = summary_for_doc(doc_path)
    section = pick_section(sections, doc_id, summary, title)
    if section is None:
        die("Error: could not determine a target section in TOPIC-INDEX.md.")

    doc_line = f"- {doc_id} | {summary}"
    insert_doc_line(index_lines, section, doc_line)
    index_path.write_text("\n".join(index_lines) + "\n", encoding="utf-8")
    print(f"Added requirement doc to TOPIC-INDEX.md under: {section['heading'][3:].strip()}")
    print(f"  - {doc_id} | {summary}")


def report_missing_docs() -> None:
    print("Requirements not in TOPIC-INDEX.md:")
    print("")

    index_lines = read_lines(index_path)
    indexed_ids = doc_ids_in_index(index_lines)

    missing = []
    for doc_path in sorted(req_dir.glob("*.md")):
        if not is_requirement_doc(doc_path):
            continue
        if doc_path.stem not in indexed_ids:
            missing.append((doc_path.stem, title_for_doc(doc_path)))

    if not missing:
        print("  (all docs are in TOPIC-INDEX)")
    else:
        for doc_id, title in missing:
            print(f"  - {doc_id} | {title}")

    print("")
    print("Add the above to docs/requirements/TOPIC-INDEX.md under the appropriate ## topic section.")


target_doc = parse_args(args)
if target_doc:
    ensure_doc_in_index(normalize_doc_path(target_doc))
else:
    report_missing_docs()
PY
