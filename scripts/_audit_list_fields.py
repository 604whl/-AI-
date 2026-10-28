"""Find every `List<String>` entity field sitting on a `jsonb` column.

`List<String>` needs an explicit type handler. If the column is a real
PostgreSQL `TEXT[]` then `TextArrayTypeHandler` is correct; if the column is
`jsonb` (a JSON array stored as text) then `JsonbListTypeHandler` is needed.

Without a handler MyBatis throws at runtime:
    IllegalStateException: Type handler was null on parameter mapping for
    property 'riskTags' ... for the javaType (java.util.List)

`ReviewTask.riskTags` is one of these (jsonb column, no handler), which broke
`POST /v1/assets/{id}/submit-review` with a 500.
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
for schema, tbl, col, ctype in query(
        "select table_schema, table_name, column_name, data_type "
        "from information_schema.columns "
        "where table_schema in ('app','kb');"):
    col_types[(schema.lower(), col.lower())] = ctype

LIST_FIELD = re.compile(
    r"(?:@TableField\((?P<ann>[^)]*)\)\s*)?private\s+"
    r"(?P<jtype>List<\s*String\s*>|List<String>)\s+(?P<name>\w+)\s*;")

problems = []
for fn in sorted(os.listdir(ROOT)):
    if not fn.endswith(".java"):
        continue
    text = io.open(os.path.join(ROOT, fn), encoding="utf-8", errors="replace").read()
    tm = re.search(r'@TableName\(\s*(?:value\s*=\s*)?["{]?([^",}\s]+)', text)
    if not tm:
        continue
    table = tm.group(1)
    schema = "kb" if table.startswith("kb.") else "app"
    if table.startswith("kb."):
        table = table[3:]

    for m in LIST_FIELD.finditer(text):
        name = m.group("name")
        ann = m.group("ann") or ""
        ctype = col_types.get((schema, name.lower()))
        if not ctype:
            continue
        if "typeHandler" in ann:
            continue
        problems.append((fn, table, name, ctype))

print("List<String> fields without a type handler: %d" % len(problems))
print("-" * 74)
for fn, table, name, ctype in problems:
    handler = ("TextArrayTypeHandler" if ctype == "ARRAY"
               else "JsonbListTypeHandler" if ctype in ("jsonb", "json")
               else "???")
    print("  %-32s %-24s %-16s col=%-10s -> %s"
          % (fn, table, name, ctype, handler))
