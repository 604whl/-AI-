"""Repair the mis-targeted i18n insertions.

The earlier bulk-insert used a regex that matched nested namespaces, so a few
keys landed in the wrong namespace. This script moves them to the right place
and drops the accidental top-level `tone` / `goal` namespaces (the locale
already carries them nested under `template`).

Usage: python scripts/i18n_repair.py [--apply]
"""
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")

LOCALES = {
    "zh-CN": r"F:\projectAI\XhsAgent\Xhs-Frontend\src\locales\zh-CN.ts",
    "en": r"F:\projectAI\XhsAgent\Xhs-Frontend\src\locales\en.ts",
    "zh-TW": r"F:\projectAI\XhsAgent\Xhs-Frontend\src\locales\zh-TW.ts",
}
ORDER = ["zh-CN", "en", "zh-TW"]

# namespace that should own each key
WANT_NS = {
    "back": "common",
    "reset": "common",
    "loadFailed": "common",
    "refresh": "common",
    "required": "common",
    "viewVersions": "asset",
    "accountCol": "asset",
    "basicInfo": "template",
    "brandDesc": "auth",
    "sampleBody": "dashboard",
    "newDesc": "analysis",
    "cover": "analysis",
    "retryFromDlq": "admin",
    "retryFromDlqSuccess": "admin",
    "retryFromDlqFailed": "admin",
}

# top-level namespaces to delete (they shadow the nested template.* ones)
DROP_TOP_LEVEL = ("tone", "goal")


def indent_of(line):
    return len(line) - len(line.lstrip(" "))


def ns_blocks(lines):
    """Map namespace name -> (open_idx, close_idx) for indent-2 blocks."""
    out = {}
    for i, l in enumerate(lines):
        if indent_of(l) != 2:
            continue
        s = l.strip()
        m = re.match(r"^([A-Za-z_][A-Za-z0-9_]*):\s*\{$", s)
        if not m:
            continue
        name = m.group(1)
        for j in range(i + 1, len(lines)):
            if indent_of(lines[j]) == 2 and lines[j].strip() in ("},", "}"):
                out.setdefault(name, (i, j))
                break
    return out


def find_nested(lines, parent, child):
    """Find a `child: {` block nested inside the `parent: {` block."""
    p = ns_blocks(lines)
    if parent not in p:
        return None
    o, c = p[parent]
    for i in range(o + 1, c):
        if indent_of(lines[i]) == 4:
            s = lines[i].strip()
            m = re.match(r"^([A-Za-z_][A-Za-z0-9_]*):\s*\{$", s)
            if m and m.group(1) == child:
                for j in range(i + 1, c):
                    if indent_of(lines[j]) == 4 and lines[j].strip() in ("},", "}"):
                        return (i, j)
                return (i, None)
    return None


def key_lines(lines, rng):
    """Return list of (idx, key) for direct children of the given block."""
    o, c = rng
    out = []
    for i in range(o + 1, c):
        s = lines[i].strip()
        m = re.match(r"^([A-Za-z_][A-Za-z0-9_]*):", s)
        if m:
            out.append((i, m.group(1)))
    return out


def process(path, loc, apply):
    lines = open(path, encoding="utf-8").read().split("\n")
    changes = []

    # 1. drop accidental top-level tone/goal
    blocks = ns_blocks(lines)
    for name in DROP_TOP_LEVEL:
        if name in blocks:
            o, c = blocks[name]
            # only drop if it is a thin block (1-2 keys) we created
            nk = len(key_lines(lines, (o, c)))
            if nk <= 2:
                changes.append("drop top-level %s" % name)
                del lines[o:c + 1]
                if apply:
                    open(path, "wb").write("\n".join(lines).encode("utf-8"))
                    return True
    return False


def main():
    apply = "--apply" in sys.argv
    for loc in ORDER:
        path = LOCALES[loc]
        lines = open(path, encoding="utf-8").read().split("\n")
        blocks = ns_blocks(lines)

        # 1. collect misplaced keys (present in a namespace other than WANT_NS)
        wanted = {}
        for key, ns in WANT_NS.items():
            if ns in blocks:
                for idx, k in key_lines(lines, blocks[ns]):
                    if k == key:
                        wanted[key] = lines[idx]
                        break

        remove = []
        for name, (o, c) in blocks.items():
            if name in WANT_NS.values():
                continue
            for idx, k in key_lines(lines, (o, c)):
                if k in WANT_NS and WANT_NS[k] != name:
                    remove.append((idx, k))

        for idx, k in sorted(remove, reverse=True):
            if k in wanted:
                changes_line = "move %s out of %s" % (k, name)
                del lines[idx]
                print("  %s: %s" % (loc, changes_line))
                if apply:
                    open(path, "wb").write("\n".join(lines).encode("utf-8"))
                    lines = open(path, encoding="utf-8").read().split("\n")

        # 2. re-insert missing wanted keys into their proper namespace
        lines = open(path, encoding="utf-8").read().split("\n")
        blocks = ns_blocks(lines)
        for key, ns in WANT_NS.items():
            if ns not in blocks:
                continue
            present = any(k == key for _, k in key_lines(lines, blocks[ns]))
            if not present and key in wanted:
                o, c = blocks[ns]
                lines.insert(o + 1, "    " + wanted[key].strip())
                print("  %s: add %s to %s" % (loc, key, ns))
                if apply:
                    open(path, "wb").write("\n".join(lines).encode("utf-8"))
                    lines = open(path, encoding="utf-8").read().split("\n")
                blocks = ns_blocks(lines)


if __name__ == "__main__":
    main()
