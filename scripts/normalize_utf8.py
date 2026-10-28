"""Normalize a file to UTF-8.

The Cursor Write/StrReplace tools emit UTF-16LE on this Windows host. Applying
them to an existing UTF-8 file splices raw UTF-8 bytes into a UTF-16 body,
leaving a file that is neither encoding. javac then reports
"illegal character" for every non-ASCII byte.

Usage: python scripts/normalize_utf8.py <path> [<path> ...]
       python scripts/normalize_utf8.py --all   (whole repo)
"""
import os
import sys

SKIP_DIRS = {".git", "node_modules", "target", "dist", ".idea", "build", "out"}
EXTS = (".java", ".sql", ".ts", ".vue", ".yml", ".yaml", ".md", ".ps1",
        ".json", ".xml", ".properties", ".txt", ".sh")


def convert(path):
    data = open(path, "rb").read()
    if len(data) < 20:
        return None
    if data.count(0) / len(data) <= 0.02:
        # Not UTF-16 by density; check for stray NUL bytes (mixed encoding).
        if b"\x00" in data:
            fixed = data.replace(b"\x00", b"")
            try:
                fixed.decode("utf-8")
            except UnicodeDecodeError:
                return None
            open(path, "wb").write(fixed)
            return "stripped-nul"
        return None
    bom = data[:2] == b"\xff\xfe"
    try:
        text = data.decode("utf-16" if bom else "utf-16-le")
    except UnicodeDecodeError:
        # Mixed encoding: UTF-16 body with UTF-8 bytes spliced in.
        # Repair by dropping NUL bytes and decoding the remainder as UTF-8.
        try:
            text = data.replace(b"\x00", b"").decode("utf-8")
        except UnicodeDecodeError:
            return None
        open(path, "wb").write(text.encode("utf-8"))
        return "mixed->utf8"
    open(path, "wb").write(text.lstrip("\ufeff").encode("utf-8"))
    return "utf16->utf8"


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        sys.exit(1)
    if args[0] == "--all":
        root = r"F:\projectAI\XhsAgent"
        targets = []
        for dp, dns, fns in os.walk(root):
            dns[:] = [d for d in dns if d not in SKIP_DIRS]
            for fn in fns:
                if fn.lower().endswith(EXTS):
                    targets.append(os.path.join(dp, fn))
    else:
        targets = args

    n = 0
    for p in targets:
        if not os.path.isfile(p):
            continue
        r = convert(p)
        if r:
            print("%-14s %s" % (r, p))
            n += 1
    print("normalized files:", n)


if __name__ == "__main__":
    main()
