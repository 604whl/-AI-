"""Wire the 7 stage-3 pages into the Vue router and the sidebar navigation.

Idempotent: re-running does not duplicate entries.
"""
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")

FRONTEND = r"F:\projectAI\XhsAgent\Xhs-Frontend\src"
ROUTER = os.path.join(FRONTEND, "router", "index.ts")
LAYOUT = os.path.join(FRONTEND, "layouts", "DefaultLayout.vue")

ROUTES = [
    ("publish/schedules", "publish-schedules", "publish/PublishCalendarView.vue", "nav.publishSchedule"),
    ("metrics", "metrics-dashboard", "metrics/MetricDashboardView.vue", "nav.metrics"),
    ("retrospectives", "retrospective-list", "retrospective/RetrospectiveListView.vue", "nav.retrospective"),
    ("hot-topics", "hot-topic-list", "hotTopic/HotTopicListView.vue", "nav.hotTopic"),
    ("competitors", "competitor-library", "competitor/CompetitorLibraryView.vue", "nav.competitor"),
    ("admin/kb", "kb-admin", "kb/KbDocumentAdminView.vue", "nav.kbAdmin"),
    ("admin/connectors", "connector-diagnostics", "connector/ConnectorDiagnosticView.vue", "nav.connector"),
]

ROUTER_ANCHOR = "        { path: 'review/:id', name: 'review-detail', component: () => import('@/views/review/ReviewDetailView.vue') },"

LAYOUT_ANCHOR = "        <el-menu-item index=\"/history\">{{ t('nav.history') }}</el-menu-item>"

NAV_ITEMS = "\n".join(
    "        <el-menu-item index=\"/%s\">{{ t('%s') }}</el-menu-item>" % (path, key)
    for path, _, _, key in ROUTES
)


def patch_router():
    text = open(ROUTER, encoding="utf-8").read()
    if "publish-schedules" in text:
        print("router: already patched")
        return
    if ROUTER_ANCHOR not in text:
        print("router: ANCHOR NOT FOUND")
        return
    lines = ["", "        // --- stage 3 (S3-12) ---"]
    for path, name, view, _ in ROUTES:
        lines.append(
            "        { path: '%s', name: '%s', component: () => import('@/views/%s') },"
            % (path, name, view)
        )
    text = text.replace(ROUTER_ANCHOR, ROUTER_ANCHOR + "\n" + "\n".join(lines))
    open(ROUTER, "wb").write(text.encode("utf-8"))
    print("router: %d routes added" % len(ROUTES))


def patch_layout():
    text = open(LAYOUT, encoding="utf-8").read()
    if "nav.publishSchedule" in text:
        print("layout: already patched")
        return
    if LAYOUT_ANCHOR not in text:
        print("layout: ANCHOR NOT FOUND")
        return
    block = (
        "\n"
        "        <el-sub-menu index=\"stage3\">\n"
        "          <template #title>{{ t('nav.stage3') }}</template>\n"
        + NAV_ITEMS
        + "\n"
        "        </el-sub-menu>"
    )
    text = text.replace(LAYOUT_ANCHOR, LAYOUT_ANCHOR + block)
    open(LAYOUT, "wb").write(text.encode("utf-8"))
    print("layout: %d nav items added" % len(ROUTES))


if __name__ == "__main__":
    patch_router()
    patch_layout()
