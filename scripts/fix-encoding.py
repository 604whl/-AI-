# -*- coding: utf-8 -*-
"""Repair UTF-16LE / mixed-encoding source files in-place -> UTF-8 (no BOM).

The Cursor `Write` tool emits UTF-16LE on this Windows host. Applying
`StrReplace` to such a file splices raw UTF-8 bytes into a UTF-16 body,
producing a file that is neither encoding. javac then fails with
"illegal character" errors.

Repair strategy: walk the byte stream.
  - byte 0x00       -> UTF-16LE high byte, drop it
  - byte >= 0xC0    -> try to decode a UTF-8 sequence, keep it intact
  - otherwise       -> ASCII, keep as-is
"""
import os
import sys

EXTS = (".java", ".sql", ".md", ".ps1", ".ts", ".vue", ".yml", ".yaml",
        ".json", ".xml", ".properties", ".txt", ".sh")
SKIP_DIRS = {".git", "node_modules", "target", "dist", ".idea", "tmp_compile",
             ".mvn", "build", "out"}


def looks_corrupt(data: bytes) -> bool:
    if len(data) < 20:
        return False
    nulls = data.count(0)
    if nulls / len(data) > 0.02:
        return True
    try:
        data.decode("utf-8")
        return False
    except UnicodeDecodeError:
        return True


def repair(data: bytes) -> bytes:
    out = bytearray()
    i = 0
    n = len(data)
    while i < n:
        b = data[i]
        if b == 0:
            i += 1
            continue
        if b >= 0xC0:
            ln = 4 if b >= 0xF0 else (3 if b >= 0xE0 else 2)
            if i + ln <= n:
                seq = data[i:i + ln]
                try:
                    seq.decode("utf-8")
                    out += seq
                    i += ln
                    continue
                except UnicodeDecodeError:
                    pass
        out.append(b)
        i += 1
    return bytes(out)


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else r"F:\projectAI\XhsAgent"
    apply = "--apply" in sys.argv
    targets = []
    for dp, dns, fns in os.walk(root):
        dns[:] = [d for d in dns if d not in SKIP_DIRS]
        for fn in fns:
            if not fn.lower().endswith(EXTS):
                continue
            targets.append(os.path.join(dp, fn))

    fixed = []
    for p in targets:
        try:
            data = open(p, "rb").read()
        except Exception:
            continue
        if not looks_corrupt(data):
            continue
        new = repair(data)
        try:
            new.decode("utf-8")
        except UnicodeDecodeError as e:
            print("SKIP (undecodable): %s -> %s" % (os.path.relpath(p, root), e))
            continue
        rel = os.path.relpath(p, root)
        if new != data:
            fixed.append((rel, len(data), len(new)))
            if apply:
                with open(p, "wb") as f:
                    f.write(new)

    print("corrupted files found: %d" % len(fixed))
    for rel, old, new in fixed:
        print("  %-90s %d -> %d bytes" % (rel, old, new))
    if fixed and not apply:
        print("\nDRY RUN. Re-run with --apply to write.")


if __name__ == "__main__":
    main()