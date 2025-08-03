package com.android.bedstead.nene.bugreports

import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.utils.UndoableContext

/** Test APIs related to bug reports. */
object BugReports {

    /**
     * When true, bug reports will skip calling the underlying component.
     *
     * This means the bug reports are generally not useful but are much quicker to take - which
     * can be useful during tests.
     */
    fun setTakeQuickBugReports(isQuick: Boolean): UndoableContext =
        TestApis.properties().set("dumpstate.dry_run", if (isQuick) "true" else "false")

    /** True if quick bug reports are enabled. */
    fun willTakeQuickBugReports(): Boolean =
        TestApis.properties().get("dumpstate.dry_run").contains("true")

}