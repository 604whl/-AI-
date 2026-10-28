"""Add missing i18n namespaces / keys to zh-CN, en, zh-TW and types.ts.

Run with no args for a dry run; pass --apply to write.
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

# key -> (zh-CN, en, zh-TW)
NEW_KEYS = {
    "common": {
        "back": ("返回", "Back", "返回"),
        "loadFailed": ("加载失败", "Load failed", "載入失敗"),
        "reset": ("重置", "Reset", "重設"),
    },
    "asset": {
        "accountCol": ("账号", "Account", "賬號"),
        "viewVersions": ("查看版本", "View versions", "查看版本"),
    },
    "analysis": {
        "cover": ("封面", "Cover", "封面"),
        "newDesc": ("描述分析任务的新建参数", "Description of new analysis task", "描述分析任務的新建參數"),
    },
    "auth": {
        "brandDesc": ("面向留学生的内容运营助手", "Content ops assistant for international students", "面向留學生的內容運營助手"),
    },
    "dashboard": {
        "sampleBody": ("示例正文内容", "Sample body content", "範例正文內容"),
    },
    "template": {
        "basicInfo": ("基本信息", "Basic info", "基本資訊"),
    },
    "admin": {
        "retryFromDlq": ("从死信队列重试", "Retry from DLQ", "從死信佇列重試"),
        "retryFromDlqSuccess": ("重试任务已提交", "Retry task submitted", "重試任務已提交"),
        "retryFromDlqFailed": ("重试任务提交失败", "Retry task failed", "重試任務提交失敗"),
    },
}

# Whole new namespaces: name -> {key: (zh, en, tw)}
NEW_NAMESPACES = {
    "tone": {
        "friendly": ("友好亲切", "Friendly", "友善親切"),
        "professional": ("专业严谨", "Professional", "專業嚴謹"),
        "casual": ("轻松口语", "Casual", "輕鬆口語"),
        "motivational": ("激励鼓舞", "Motivational", "激勵鼓舞"),
    },
    "goal": {
        "high_conversion": ("高转化", "High conversion", "高轉化"),
        "engagement": ("高互动", "Engagement", "高互動"),
        "brand_awareness": ("品牌认知", "Brand awareness", "品牌認知"),
    },
}

TYPE_ALIASES = {
    "tone": "ToneKey",
    "goal": "GoalKey",
}


def escape(s):
    return s.replace("\\", "\\\\").replace("'", "\\'")


def has_namespace(text, name):
    return re.search(r"^\s{2}%s:\s*\{" % re.escape(name), text, re.M) is not None


def has_key(text, ns, key):
    return re.search(r"^\s+%s:\s*['\"]" % re.escape(key), text, re.M) is not None


def find_ns_insert_point(lines):
    """Return index of the line that closes the top-level object."""
    for i, l in enumerate(lines):
        if re.match(r"^\}\s*(as const)?\s*$", l.strip()) and i > 20:
            return i
    return None


def apply_new_namespace(path, name, entries):
    text = open(path, encoding="utf-8").read()
    if has_namespace(text, name):
        return False
    lines = text.split("\n")
    idx = find_ns_insert_point(lines)
    if idx is None:
        return False
    block = ["  %s: {" % name]
    for key, vals in entries.items():
        i = ORDER.index(_LOC_OF[os.path.normcase(path)])
    block.append("    %s: '%s'," % (key, escape(vals[i])))
    block.append("  },")
    lines[idx:idx] = block
    open(path, "wb").write("\n".join(lines).encode("utf-8"))
    return True


def apply_new_key(path, ns, key, vals):
    text = open(path, encoding="utf-8").read()
    if not has_namespace(text, ns):
        return False
    if has_key(text, ns, key):
        return False
    lines = text.split("\n")
    m = re.search(r"^(\s{2})%s:\s*\{$" % re.escape(ns), text, re.M)
    if not m:
        return False
    ns_start = text[:m.start()].count("\n")
    ns_indent = len(m.group(1))
    insert_at = ns_start + 1
    i = ORDER.index(_LOC_OF[os.path.normcase(path)])
    new_line = "%s%s: '%s'," % (" " * (ns_indent + 2), key, escape(vals[i]))
    lines.insert(insert_at, new_line)
    open(path, "wb").write("\n".join(lines).encode("utf-8"))
    return True


_LOC_OF = {}
for _loc, _p in LOCALES.items():
    _LOC_OF[os.path.normcase(_p)] = _loc


def main():
    apply = "--apply" in sys.argv
    changed = 0
    for loc, path in LOCALES.items():
        for ns, keys in NEW_NAMESPACES.items():
            if apply_namespace(loc, path, ns, keys, apply):
                changed += 1
        for ns, keys in NEW_KEYS.items():
            for key, vals in keys.items():
                if apply_key(loc, path, ns, key, vals, apply):
                    changed += 1
    print("changes:", changed, "(apply)" if apply else "(dry run)")


def apply_namespace(loc, path, ns, keys, apply):
    i = ORDER.index(loc)
    entries = {k: (v[0], v[1], v[2]) for k, v in keys.items()}
    if not apply:
        text = open(path, encoding="utf-8").read()
        return not has_namespace(text, ns)
    return apply_new_namespace(path, ns, entries)


def apply_key(loc, path, ns, key, vals, apply):
    if not apply:
        text = open(path, encoding="utf-8").read()
        return has_namespace(text, ns) and not has_key(text, ns, key)
    return apply_new_key(path, ns, key, vals)


if __name__ == "__main__":
    main()
