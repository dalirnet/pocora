#!/usr/bin/env python3
#
# Turn the presets in ../preset/, the design source, into the one JSON file both apps ship with.
#
# Usage:  python3 presets.py <preset folder> <output file>
#
# The markdown stays the only place a preset is edited. Run `make presets` after changing it.

import json
import re
import sys
from pathlib import Path

MONTHS = [
    "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
    "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand",
]
DAYS = ["Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri"]
MARKS_PER_HOUR = 2

errors = []


def fail(message):
    errors.append(message)


def table_rows(text, heading):
    """Rows of the first table under a heading, as lists of cells, header and divider left out."""
    match = re.search(r"^#+ " + re.escape(heading) + r"\s*$(.*?)(?=^#+ |\Z)", text, re.M | re.S)
    if not match:
        return []
    rows = [line for line in match.group(1).splitlines() if line.startswith("|")]
    return [[cell.strip() for cell in row.strip("|").split("|")] for row in rows[2:]]


def all_table_rows(text):
    rows = []
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if line.startswith("|") and index + 1 < len(lines) and lines[index + 1].startswith("| ---"):
            body = lines[index + 2 :]
            for row in body:
                if not row.startswith("|"):
                    break
                rows.append([cell.strip() for cell in row.strip("|").split("|")])
    return rows


def link_name(cell):
    """'[School, morning shift](./school-morning.md)' becomes ('School, morning shift', 'school-morning')."""
    match = re.match(r"\[(.+?)\]\(\./(?:groups/)?(.+?)\.md\)", cell)
    return (match.group(1), match.group(2)) if match else (None, None)


def mark(text):
    """'17:30' becomes the index of its 30-minute mark. '24:00' is the end of the day, 48."""
    hours, minutes = text.split(":")
    if minutes not in ("00", "30"):
        fail(f"time not on a mark: {text}")
    return int(hours) * MARKS_PER_HOUR + int(minutes) // 30


def days_of(cell):
    """'Sat to Wed, and Fri', 'Thu and Fri', 'Every day' become weekday indexes, Saturday first."""
    cell = cell.replace(", and ", ", ").replace(" and ", ", ")
    if cell == "Every day":
        return list(range(7))
    result = []
    for part in cell.split(","):
        part = part.strip()
        if " to " in part:
            first, last = part.split(" to ")
            result += list(range(DAYS.index(first), DAYS.index(last) + 1))
        else:
            result.append(DAYS.index(part))
    return result


def blocks_of(cell):
    """'10:00 to 12:00, 17:00 to 22:00' becomes [[20, 24], [34, 44]]. 'None' is no block."""
    if cell.lower() == "none":
        return []
    blocks = []
    for part in cell.split(","):
        start, end = part.strip().split(" to ")
        blocks.append([mark(start), mark(end)])
    return blocks


def iranian(cell, year=None):
    """'Bahman 19' becomes '11-19', or '1405-11-19' with a year."""
    month, day = cell.split()
    text = f"{MONTHS.index(month) + 1:02d}-{int(day):02d}"
    return f"{year}-{text}" if year else text


def sections(readme):
    """Each linked preset in an index README, with its section and Persian name."""
    found = {}
    section = None
    for line in readme.splitlines():
        if line.startswith("### "):
            section = line[4:].strip().lower().replace(" ", "_")
        elif line.startswith("| [") and "](./groups/" not in line:
            cells = [cell.strip() for cell in line.strip("|").split("|")]
            english, identifier = link_name(cells[0])
            found[identifier] = {"section": section, "en": english, "fa": cells[1], "cells": cells}
    return found


# --- Schedules ---


def schedules(folder):
    index = sections((folder / "README.md").read_text())
    result = []
    for identifier, entry in index.items():
        text = (folder / f"{identifier}.md").read_text()
        week = [[] for _ in DAYS]
        for days, allowed in table_rows(text, "Allowed blocks"):
            for day in days_of(days):
                week[day] = blocks_of(allowed)
        seasons = []
        for row in table_rows(text, "Season"):
            first, last = row[2].split(" to ")
            seasons.append({"from": first, "to": last})
        if not any(week):
            fail(f"schedule {identifier} has no Allowed blocks")
        result.append(
            {
                "id": identifier,
                "name": {"en": entry["en"], "fa": entry["fa"]},
                "section": entry["section"],
                "week": week,
                "seasons": seasons,
            }
        )
    return result


# --- Apps lists and groups ---


def groups(folder):
    index = sections((folder / "README.md").read_text())
    result = []
    for identifier, entry in index.items():
        text = (folder / f"{identifier}.md").read_text()
        apps = []
        for row in all_table_rows(text):
            if len(row) < 3 or not row[2].startswith("`"):
                continue
            apps.append({"package": row[2].strip("`"), "name": {"en": row[0], "fa": row[1] if row[1] != "-" else row[0]}})
        result.append({"id": identifier, "name": {"en": entry["en"], "fa": entry["fa"]}, "apps": apps})
    return result


def apps_lists(folder, group_names):
    index = sections((folder / "README.md").read_text())
    result = []
    for identifier, entry in index.items():
        text = (folder / f"{identifier}.md").read_text()
        opened = []
        for line in text.splitlines():
            match = re.match(r"\[(x| )\] (\S+(?: \S+)?)\s{2,}", line)
            if match and match.group(1) == "x":
                name = match.group(2).strip()
                if name not in group_names:
                    fail(f"apps list {identifier}: unknown group {name}")
                else:
                    opened.append(group_names[name])
        age = entry["cells"][2]
        ages = [int(number) for number in age.split(" to ")] if " to " in age else None
        result.append(
            {
                "id": identifier,
                "name": {"en": entry["en"], "fa": entry["fa"]},
                "section": entry["section"],
                "ages": ages,
                "groups": opened,
            }
        )
    return result


# --- Quota ---


def quotas(folder):
    index = sections((folder / "README.md").read_text())
    result = []
    for identifier, entry in index.items():
        text = (folder / f"{identifier}.md").read_text()
        match = re.search(r"\*\*Per mark \(30 minutes\):\*\* (\d+) MB", text)
        result.append(
            {
                "id": identifier,
                "name": {"en": entry["en"], "fa": entry["fa"]},
                "megabytesPerMark": int(match.group(1)) if match else None,
            }
        )
    return result


# --- Holidays ---


def holidays(folder):
    result = []
    for file in sorted(folder.glob("1*.md")):
        year = int(file.stem)
        for row in table_rows(file.read_text(), "Holidays"):
            result.append({"date": iranian(row[0], year), "name": {"en": row[2], "fa": row[3]}})
    return result


def ramadan(folder):
    result = []
    for row in table_rows((folder / "README.md").read_text(), "Ramadan"):
        first, last = row[1].split(" to ")
        result.append({"from": iranian(first, row[0]), "to": iranian(last, row[0])})
    return result


def main():
    source = Path(sys.argv[1])
    output = Path(sys.argv[2])
    group_list = groups(source / "apps" / "groups")
    group_names = {group["name"]["en"]: group["id"] for group in group_list}
    presets = {
        "schedules": schedules(source / "schedule"),
        "appsLists": apps_lists(source / "apps", group_names),
        "groups": group_list,
        "quotas": quotas(source / "quota"),
        "holidays": holidays(source / "holidays"),
        "ramadan": ramadan(source / "holidays"),
    }
    if errors:
        for error in errors:
            print(f"Error: {error}", file=sys.stderr)
        sys.exit(1)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(presets, ensure_ascii=False, indent=4) + "\n")
    counts = ", ".join(f"{len(value)} {key}" for key, value in presets.items())
    print(f"Done: {output} ({counts})")


main()
