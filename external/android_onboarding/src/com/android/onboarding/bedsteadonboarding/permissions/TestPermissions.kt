package com.android.onboarding.bedsteadonboarding.permissions

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Security Mechanism implemented is as follows:
 *
 * Only instrumented apps running on a debuggable device should be able to set Test configs using
 * [TestContentProvider]. Any app including production apps can query [TestContentProvider] to fetch
 * Test Configs. In production scenarios, no configuration will be set, and so no test logic should
 * be executed.
 *
 * However since [TestContentProvider] is exported so we want to restrict who can call it to set
 * test configs. So using [TestPermissions#canCallerExecuteTestFunctionality()] we will check if the
 * device has debuggable android build and if so then whether the caller uid is that of an
 * instrumented app. Only if both are true will we allow the caller to manipulate test configs.
 */
object TestPermissions {

  private const val TAG = "TestPermissions"

  /**
   * Returns true if the calling uid is permitted to utilise test-only functionality in Onboarding.
   *
   * This will only return true in situations where it is safe for the caller to use test-only
   * functionality. Additional security checks (such as for rooted devices) should not be performed
   * if this returns true.
   *
   * It is not possible to query in-general if we are running within a test. The standard pattern is
   * to expose some test-only capability (guarded with this method) which sets state that can be
   * queried later. For example, exposing a test-only API to override an allow-list - rather than
   * having the production code skip the allowlist when running in a test.
   *
   * @param context context of the current application from which the function is called.
   * @param uid userid of the caller against which to check if they have permissions to execute
   *   test-only functionality in Onboarding.
   */
  fun canCallerExecuteTestFunctionality(context: Context, uid: Int): Boolean =
    isRunningOnDebuggableDevice() && uidIsInstrumented(context, uid)

  internal fun isRunningOnDebuggableDevice(): Boolean {
    return try {
      Build::class.java.getDeclaredField("IS_DEBUGGABLE").get(null) as Boolean
    } catch (t: Throwable) {
      Log.e(
        TAG,
        "Test Permission not granted since if the build is debuggable could not be determined",
      )
      false
    }
  }

  /**
   * Returns if the caller with userid [uid] is associated with an instrumented app.
   *
   * @param context context of the current application from which the function is called.
   * @param uid userid of the caller for which to check if the associated process is instrumented.
   */
  fun uidIsInstrumented(context: Context, uid: Int): Boolean {
    try {
      // Get all the app packages associated with [uid].
      val packagesForUid = context.packageManager.getPackagesForUid(uid) ?: arrayOf()
      for (packageForUid in packagesForUid) {
        // Check if for the [packageForUid], there exists any [InstrumentationInfo]. This will
        // only be the case when the app with userId [packageForUid] is instrumented.
        val instrumentationInfo =
          context.packageManager.queryInstrumentation(
            packageForUid,
            /** flags= */
            0,
          )
        // Returns true if atleast one package with [uid] is instrumented.
        if (instrumentationInfo.isNotEmpty()) return true
      }
    } catch (t: Throwable) {
      Log.e(TAG, "Got error while determining if the caller is an instrumented app", t)
    }
    return false
  }
}
