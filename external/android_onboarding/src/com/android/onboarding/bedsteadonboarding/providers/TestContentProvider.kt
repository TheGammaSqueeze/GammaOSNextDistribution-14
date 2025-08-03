package com.android.onboarding.bedsteadonboarding.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.util.Log
import com.android.onboarding.bedsteadonboarding.data.NodeData
import com.android.onboarding.bedsteadonboarding.data.TestConfigData
import com.android.onboarding.bedsteadonboarding.permissions.TestPermissions
import com.android.onboarding.bedsteadonboarding.providers.ConfigProviderUtil.TEST_NODE_CLASS_COLUMN

/**
 * Content Provider used for storing all the test configs. Each production onboarding app will have
 * an instance of this provider to store test configuration to be used when such app is executing
 * during tests. Only the test process has permissions to insert and delete the configurations
 * whereas all onboarding apps can query even outside of tests.
 */
class TestContentProvider : ContentProvider() {
  private lateinit var baseTestConfigUri: Uri

  private val testConfigUriMatcher = UriMatcher(UriMatcher.NO_MATCH)
  private val IS_TEST_CONFIG_KEY = 1
  private val TAG = "TestContentProvider"

  private var testConfigData = TestConfigData(listOf())
  private var nodeConfigList = mutableListOf<NodeData>()

  override fun attachInfo(context: Context?, info: ProviderInfo) {
    super.attachInfo(context, info)
    val authority = info.authority
    baseTestConfigUri = ConfigProviderUtil.getBaseContentUri(authority)
    testConfigUriMatcher.addURI(authority, ConfigProviderUtil.TEST_CONFIG_PATH, IS_TEST_CONFIG_KEY)
  }

  override fun onCreate(): Boolean {
    return true
  }

  override fun query(
    uri: Uri,
    projection: Array<String>?,
    selection: String?,
    selectionArgs: Array<String>?,
    sortOrder: String?,
  ): Cursor {
    when (testConfigUriMatcher.match(uri)) {
      IS_TEST_CONFIG_KEY -> {
        val matrixCursor = MatrixCursor(arrayOf(TEST_NODE_CLASS_COLUMN))
        for (nodeData in testConfigData.testNodes) {
          matrixCursor.newRow().add(TEST_NODE_CLASS_COLUMN, nodeData.allowedContractIdentifier)
        }
        return matrixCursor
      }
      else -> throw UnsupportedOperationException("Unsupported Query Uri $uri")
    }
  }

  override fun getType(uri: Uri): String? {
    throw UnsupportedOperationException("getType")
  }

  override fun insert(uri: Uri, values: ContentValues?): Uri? {
    throw UnsupportedOperationException("insert")
  }
  override fun bulkInsert(uri: Uri, contentValuesList: Array<ContentValues>): Int {
    if (hasPermission()) {
      when (testConfigUriMatcher.match(uri)) {
        IS_TEST_CONFIG_KEY -> {
          for (contentValues in contentValuesList) {
            contentValues.getAsString(TEST_NODE_CLASS_COLUMN)?.let { testNodeClassName ->
              nodeConfigList.add(NodeData(testNodeClassName))
            }
          }
        }
        else -> throw UnsupportedOperationException("Unsupported Insertion Uri $uri")
      }
      testConfigData = TestConfigData(nodeConfigList.toList())
      return nodeConfigList.size
    }
    throw SecurityException("Does not have permission to insert")
  }

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
    if (hasPermission()) {
      when (testConfigUriMatcher.match(uri)) {
        IS_TEST_CONFIG_KEY -> {
          testConfigData = TestConfigData(listOf())
        }
        else -> throw UnsupportedOperationException("Unsupported Deletion Uri $uri")
      }
      return 1
    }
    throw SecurityException("Does not have permission to delete")
  }

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<String>?,
  ): Int {
    throw UnsupportedOperationException("update")
  }

  private fun hasPermission(): Boolean {
    val context = context
    return when {
      "robolectric" == Build.FINGERPRINT -> true
      context != null ->
        TestPermissions.canCallerExecuteTestFunctionality(context, Binder.getCallingUid())
      else -> {
        Log.e(TAG, "Can't check test permission since context is null")
        false
      }
    }
  }
}
