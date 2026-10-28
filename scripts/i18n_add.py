"""Insert i18n keys into the vue-i18n locale modules.

Each locale file is a TS object literal; we locate a namespace block by its
`name: {` header and insert new `key: 'value',` lines right after it.

Usage: python scripts/i18n_add.py <namespace> <key>=<zh> <key>=<en> <key>=<zhTW> ...
"""
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")

LOCALES = {
    "zh-CN": r"F:\projectAI\XhsAgent\Xhs-Frontend\src\locales\zh-CN.ts",
    "en": r"F:\projectAI\XhsAgent\Xhs-Frontend\src\locales\en.ts",
    "zh-TW": r"F:\projectAI\XhsAgent\Xhs-Frontend\src\locales\zh-TW.ts",
}
ORDER = ["zh-CN", "en", "zh-TW"]


def escape(s):
    return s.replace("\\", "\\\\").replace("'", "\\'")


def insert(path, namespace, pairs):
    lines = open(path, encoding="utf-8").read().split("\n")
    header = "  %s: {" % namespace
    idx = None
    for i, l in enumerate(lines):
        if l.rstrip() == header:
            idx = i
            break
    if idx is None:
        return False
    new = []
    for k, v in pairs.items():
        new.append("    %s: '%s'," % (k, escape(v)))
    lines[idx + 1:idx + 1] = new
    open(path, "wb").write("\n".join(lines).encode("utf-8"))
    return True


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    namespace = sys.argv[1]
    by_locale = {loc: {} for loc in ORDER}
    for arg in sys.argv[2:]:
        key, value = arg.split("=", 1)
        for i, loc in enumerate(ORDER):
            parts = value.split("|")
            by_locale[loc][key] = parts[i] if i < len(parts) else parts[-1]
    for loc in ORDER:
        ok = insert(LOCALES[loc], namespace, by_locale[loc])
        print("%-6s %s (%d keys)" % (loc, "OK" if ok else "NOT FOUND", len(by_locale[loc])))


if __name__ == "__main__":
    main()
