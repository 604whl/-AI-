"""Find MyBatis queries using the `#{param} IS NULL OR col = #{param}` pattern.

PostgreSQL cannot infer the type of an untyped bind parameter used only in a
`? IS NULL` comparison, so these queries fail at runtime with
"could not determine data type of parameter $N" (HTTP 500).

The project already has the correct pattern in CompetitorAccountMapper: a
`<script>` block with `<if test="param != null and param != ''">` so the
condition is omitted entirely when the filter is absent.

This script lists every mapper method that still uses the broken pattern.
"""
import io
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")

ROOT = (r"Xhs-Backend\src\main\java\com\shortvideoscripagent"
        r"\xhsagentyunying\domain\mapper")

# A parameter used as `#{x} IS NULL` -- the broken form.
BROKEN = re.compile(r"#\{(\w+)\}\s+IS\s+NULL\s+OR\s+", re.I)

hits = []
for fn in sorted(os.listdir(ROOT)):
    if not fn.endswith(".java"):
        continue
    path = os.path.join(ROOT, fn)
    text = io.open(path, encoding="utf-8", errors="replace").read()
    for m in BROKEN.finditer(text):
        line = text[:m.start()].count("\n") + 1
        hits.append((fn, line, m.group(1)))

print("mapper methods using the broken `? IS NULL OR` pattern: %d" % len(hits))
print("-" * 74)
for fn, line, param in hits:
    print("  %-34s line %-5s param=%s" % (fn, line, param))
