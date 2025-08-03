package com.android.onboarding.bedsteadonboarding.contractutils

import android.content.Context
import android.os.Process
import android.util.Log
import com.android.onboarding.bedsteadonboarding.providers.ConfigProviderUtil
import com.android.onboarding.bedsteadonboarding.providers.ConfigProviderUtil.TEST_NODE_CLASS_COLUMN

private const val TAG = "TestFramework"

/**
 * Contains helper methods which different nodes can call to check if they are allowed to execute.
 */
object ContractExecutionEligibilityChecker {

  internal val ALLOW_ALL_NODES = null

  /**
   * Checks if the test configurations are set. If it is not set then node is allowed to execute.
   * This will always be the case when the node is being executed in production. If it is set then
   * it means that test process have set those configurations. So it would then check if the node is
   * being allowed to execute. If not then it will terminate the app process.
   *
   * @param context context of the package being executed
   * @param contractClass contract class of the node which is to be executed
   */
  fun terminateIfNodeIsTriggeredByTestAndIsNotAllowed(context: Context, contractClass: Class<*>) {
    try {
      // Fetch the list of contracts which are allowed to execute.
      val allowedNodes = getAllowedNodes(context)

      if (allowedNodes == ALLOW_ALL_NODES) {
        return
      }

      val nodeToCheck = ContractUtils.getContractIdentifier(contractClass)

      // If the node is attempted to be executed as part of test but it is not allowed.
      if (!allowedNodes.contains(nodeToCheck)) {
        Log.w(TAG, "Contract $nodeToCheck is not allowed to execute")
        // Kill the app under test.
        Process.killProcess(Process.myPid())
      }
    } catch (t: Throwable) {
      // For safety, ignore the exception since current function would run in production flow.
      Log.e(TAG, "Error while fetching list of allowed nodes", t)
    }
  }

  /**
   * Fetches the list of contractClasses of nodes which are allowed to execute. It will always
   * return [ALLOW_ALL_NODES] when the node is being executed in production.
   */
  internal fun getAllowedNodes(context: Context): Set<String>? {
    val uri = ConfigProviderUtil.getTestConfigUri(context)
    var allowedNodes: MutableSet<String>? = null
    // Fetch the list of contracts which are allowed to execute.
    context.contentResolver
      .query(
        uri,
        arrayOf(TEST_NODE_CLASS_COLUMN),
        /* selection= */ null,
        /* selectionArgs= */ null,
        /* sortOrder= */ null,
        /* cancellationSignal= */ null,
      )
      .use { cursor ->
        if ((cursor != null) && cursor.moveToFirst()) {
          do {
            val columnIndex = cursor.getColumnIndex(TEST_NODE_CLASS_COLUMN)
            require(columnIndex != -1) { "Column $TEST_NODE_CLASS_COLUMN not found." }
            val allowedNode = cursor.getString(columnIndex)
            if (allowedNodes == null) {
              allowedNodes = mutableSetOf()
            }
            allowedNodes!!.add(allowedNode)
          } while (cursor.moveToNext())
        }
      }
    return allowedNodes?.toSet() ?: ALLOW_ALL_NODES
  }
}
