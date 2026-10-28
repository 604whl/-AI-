"""Regenerate `src/locales/types.ts` from the zh-CN locale module.

The locale file is the source of truth; this mirrors its object-literal
structure into a TypeScript interface so `useI18n` stays type-safe.

Usage: python scripts/gen_locale_types.py [--apply]
"""
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")

SRC = r"F:\projectAI\XhsAgent\Xhs-Frontend\src\locales\zh-CN.ts"
DST = r"F:\projectAI\XhsAgent\Xhs-Frontend\src\locales\types.ts"

HEADER = """export type AppLocale = 'zh-CN' | 'zh-TW' | 'en'

export const LOCALE_OPTIONS: Array<{ value: AppLocale; labelKey: string }> = [
  { value: 'zh-CN', labelKey: 'language.zhCN' },
  { value: 'zh-TW', labelKey: 'language.zhTW' },
  { value: 'en', labelKey: 'language.en' },
]
"""


def parse_tree(path):
    """Parse the locale object literal into a nested dict of key -> value/children."""
    lines = open(path, encoding="utf-8").read().split("\n")
    start = None
    for i, l in enumerate(lines):
        s = l.strip()
        if re.match(r"^const zhCN\s*(:\s*\w+)?\s*=\s*\{$", s):
            start = i
            break
    if start is None:
        for i, l in enumerate(lines):
            if l.strip() == "{":
                start = i
                break
    end = None
    for j in range(start + 1, len(lines)):
        if lines[j].strip() in ("}", "} as const"):
            end = j
            break
    if end is None:
        raise SystemExit("could not find object literal bounds")

    root = {}
    stack = [(0, root)]
    i = start + 1
    while i < end:
        raw = lines[i]
        stripped = raw.strip()
        if not stripped or stripped.startswith("//"):
            i += 1
            continue
        indent = len(raw) - len(raw.lstrip(" "))
        m = re.match(r"^([A-Za-z_][A-Za-z0-9_]*):\s*(.*)$", stripped)
        if not m:
            i += 1
            continue
        name, rest = m.group(1), m.group(2)
        while len(stack) > 1 and stack[-1][0] >= indent:
            stack.pop()
        parent = stack[-1][1]
        if rest.startswith("{"):
            child = {}
            parent[name] = child
            stack.append((indent, child))
        elif rest.startswith("["):
            # array literal: collect until the closing bracket
            value = rest
            j = i
            while value.count("[") > value.count("]") and j + 1 < end:
                j += 1
                value += "\n" + lines[j].strip()
            i = j
            parent[name] = "string[]"
        else:
            value = rest
            if value.count("'") % 2 == 1 or value.count('"') % 2 == 1:
                j = i + 1
                while j < end and lines[j].strip():
                    value += "\n" + lines[j].strip()
                    j += 1
                i = j
            parent[name] = "string"
        i += 1
    return root


def emit(tree, indent=0):
    pad = "  " * indent
    out = []
    for key, val in tree.items():
        if isinstance(val, dict):
            out.append("%s%s: {" % (pad, key))
            out.extend(emit(val, indent + 1))
            out.append("%s}" % pad)
        else:
            out.append("%s%s: %s" % (pad, key, val))
    return out


def main():
    apply = "--apply" in sys.argv
    tree = parse_tree(SRC)
    body = "\n".join(emit(tree))
    content = HEADER + "\nexport interface LocaleMessages {\n" + body + "\n}\n"
    if apply:
        open(DST, "wb").write(content.encode("utf-8"))
        print("types.ts regenerated (%d bytes)" % len(content))
    else:
        print(content[:1500])
        print("...")
        print("DRY RUN. Re-run with --apply.")


if __name__ == "__main__":
    main()
