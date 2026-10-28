"""Append a whole new i18n namespace to the three locale modules + types.ts.

Usage: python scripts/i18n_namespace.py <namespace> <<'EOF'
key: zh-CN | en | zh-TW
...
EOF
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
TYPES = r"F:\projectAI\XhsAgent\Xhs-Frontend\src\locales\types.ts"
ORDER = ["zh-CN", "en", "zh-TW"]


def escape(s):
    return s.replace("\\", "\\\\").replace("'", "\\'")


def read_pairs(text):
    pairs = []
    for raw in text.split("\n"):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        m = re.match(r"^([A-Za-z_][A-Za-z0-9_]*):\s*(.*)$", line)
        if not m:
            continue
        key, rest = m.group(1), m.group(2)
        parts = [p.strip() for p in rest.split("|")]
        pairs.append((key, parts))
    return pairs


def insert_namespace(path, namespace, pairs):
    lines = open(path, encoding="utf-8").read().split("\n")
    # find closing "} as const" or "}" at end of default export object
    insert_at = None
    for i, l in enumerate(lines):
        if re.match(r"^\}\s*(as const)?\s*$", l.strip()) and i > 20:
            insert_at = i
            break
    if insert_at is None:
        return False
    block = ["  %s: {" % namespace]
    for key, parts in pairs:
        idx = ORDER.index_of_value if False else None
        block.append("    %s: '%s'," % (key, escape(parts[0])))
    block.append("  },")
    lines[insert_at:insert_at] = block
    open(path, "wb").write("\n".join(lines).encode("utf-8"))
    return True


def main():
    namespace = sys.argv[1]
    body = sys.stdin.read()
    for loc in ORDER:
        pairs = read_pairs(body)
        localized = []
        for key, parts in pairs:
            i = ORDER.index(loc)
            localized.append((key, parts[i] if i < len(parts) else parts[-1]))
        ok = insert_namespace(LOCALES[loc], namespace, localized)
        print("%-6s %s" % (loc, "OK" if ok else "FAILED"))


if __name__ == "__main__":
    main()
