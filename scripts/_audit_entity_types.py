"""Audit every entity field against the real PostgreSQL column type.

A field declared as String / Map / List on a `jsonb` column needs an explicit
`@TableField(typeHandler = ...)`. Without it MyBatis binds the value as
`character varying` and PostgreSQL rejects it:

    ERROR: column "variables" is of type jsonb but expression is of type
    character varying

This has bitten three entities so far (ContentAsset.tags, AuditEvent.metadata,
Template.variables). Rather than fixing them one at a time, audit the whole
entity layer against information_schema and list every mismatch.
"""
import io
import os
import re
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8")

ROOT = (r"Xhs-Backend\src\main\java\com\shortvideoscripagent"
        r"\xhsagentyunying\domain\entity")

DOCKER = ["docker", "exec", "xhsagent-postgres-dev", "psql", "-U", "xhsagent",
          "-d", "xhsagent", "-t", "-A", "-F", "\x1f", "-c"]


def query(sql):
    out = subprocess.run(DOCKER + [sql], capture_output=True, text=True,
                         encoding="utf-8", errors="replace")
    rows = []
    for line in out.stdout.splitlines():
        line = line.strip()
        if line and "\x1f" in line:
            rows.append(line.split("\x1f"))
    return rows


col_types = {}
for tbl, col, ctype in query(
        "select table_name, column_name, data_type "
        "from information_schema.columns where table_schema='app';"):
    col_types[(tbl, col.lower())] = ctype

# ── collect entity fields ───────────────────────────────────────────────
FIELD = re.compile(
    r"(?:@TableField\([^)]*\)\s*)?private\s+([\w<>,\s\.\[\]]+?)\s+(\w+)\s*;")

problems = []
for fn in sorted(os.listdir(ROOT)):
    if not fn.endswith(".java"):
        continue
    text = io.open(os.path.join(ROOT, fn), encoding="utf-8", errors="replace").read()
    tm = re.search(r'@TableName\(\s*(?:value\s*=\s*)?"([^"]+)"', text)
    if not tm:
        continue
    table = tm.group(1)
    if (table, "__none__") not in col_types and table not in {
            t for (t, _c) in col_types}:
        continue

    # find the field block so we can see whether a typeHandler is attached
    for m in FIELD.finditer(text):
        jtype, name = m.group(1).strip(), m.group(2)
        ctype = col_types.get((table, name.lower()))
        if not ctype:
            continue
        # look back a few lines for @TableField(typeHandler = ...)
        start = max(0, m.start() - 300)
        prefix = text[start:m.start()]
        has_handler = "typeHandler" in prefix.split("private")[-1]

        if ctype in ("jsonb", "json"):
            if not has_handler:
                problems.append((fn, table, name, jtype, ctype, "missing typeHandler"))
        elif ctype in ("character varying", "text", "varchar"):
            if jtype in ("Map", "List") and not has_handler:
                problems.append((fn, table, name, jtype, ctype, "collection on text col"))
        elif ctype == "uuid":
            if jtype == "String" and not has_handler:
                problems.append((fn, table, name, jtype, ctype, "String on uuid col"))

print("entity / column type mismatches: %d" % len(problems))
print("-" * 92)
for fn, table, name, jtype, ctype, why in problems:
    print("  %-34s %-26s %-22s %-10s %-10s %s"
          % (fn, table, name, jtype, ctype, why))
