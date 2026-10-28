"""Insert i18n keys into a specific namespace of the vue-i18n locale modules.

Line-based and indentation-aware (no regex over the whole file), so it cannot
insert into the wrong namespace.

Usage:
  python scripts/i18n_put.py <namespace> key=zh|en|tw [key=zh|en|tw ...]
  python scripts/i18n_put.py --ns <namespace> key=zh|en|tw ...
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


def indent_of(line):
    return len(line) - len(line.lstrip(" "))


def find_namespace(lines, name):
    """Return (open_index, close_index) of the `name: {` block at indent 2."""
    for i, l in enumerate(lines):
        stripped = l.strip()
        if indent_of(l) != 2:
            continue
        if not stripped.startswith(name + ":"):
            continue
        if not stripped.endswith("{"):
            return None
        # find matching close: first line at indent 2 that is exactly '},' or '}'
        for j in range(i + 1, len(lines)):
            if indent_of(lines[j]) == 2 and lines[j].strip() in ("},", "}"):
                return (i, j)
        return None
    return None


def find_top_level_close(lines):
    for i, l in enumerate(lines):
        if indent_of(l) == 0 and l.strip() in ("}", "} as const"):
            return i
    return None


def namespace_exists(lines, name):
    return find_namespace(lines, name) is not None


def has_key(lines, ns_range, key):
    open_i, close_i = ns_range
    for l in lines[open_i + 1:close_i]:
        s = l.strip()
        if s.startswith(key + ":"):
            return True
    return False


def escape(s):
    return s.replace("\\", "\\\\").replace("'", "\\'")


def put(path, loc, namespace, pairs):
    lines = open(path, encoding="utf-8").read().split("\n")
    i = ORDER.index(loc)

    ns_range = find_namespace(lines, namespace)
    if ns_range is None:
        # create the namespace just before the top-level closing brace
        close_i = find_top_level_close(lines)
        if close_i is None:
            return False, "no top-level close"
        block = ["  %s: {" % namespace]
        for key, vals in pairs.items():
            block.append("    %s: '%s'," % (key, escape(vals[i])))
        block.append("  },")
        lines[close_i:close_i] = block
        open(path, "wb").write("\n".join(lines).encode("utf-8"))
        return True, "namespace created"

    added = []
    for key, vals in pairs.items():
        if has_key(lines, ns_range, key):
            continue
        insert_at = ns_range[0] + 1
        lines.insert(insert_at, "    %s: '%s'," % (key, escape(vals[i])))
        added.append(key)
        # recompute range after each insert
        ns_range = find_namespace(lines, namespace)
    if added:
        open(path, "wb").write("\n".join(lines).encode("utf-8"))
    return True, ("added " + ",".join(added)) if added else "already present"


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        sys.exit(1)
    namespace = args[0]
    pairs = {}
    for arg in args[1:]:
        key, value = arg.split("=", 1)
        pairs[key] = value.split("|")
    for loc in ORDER:
        ok, msg = put(LOCALES[loc], loc, namespace, pairs)
        print("%-6s %-8s %s" % (loc, "OK" if ok else "FAIL", msg))


if __name__ == "__main__":
    main()
