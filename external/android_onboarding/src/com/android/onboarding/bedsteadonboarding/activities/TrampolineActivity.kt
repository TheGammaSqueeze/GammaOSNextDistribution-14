package com.android.onboarding.bedsteadonboarding.activities

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.android.onboarding.bedsteadonboarding.permissions.TestPermissions
import com.android.onboarding.bedsteadonboarding.providers.ConfigProviderUtil

/**
 * It is an activity whose exported value is always set as true. It's purpose is to launch another
 * activity of the same app whose exported value is set as false. Every onboarding app would have
 * this activity added via dependency on bedstead-onboarding.
 */
class TrampolineActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (!hasPermission()) {
      Log.e(TAG, "Unauthorized request for creating TrampolineActivity ")
      finish()
      return
    }

    val nodeIntent =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(EXTRA_NODE_START_INTENT_KEY, Intent::class.java)
      } else {
        intent.getParcelableExtra(EXTRA_NODE_START_INTENT_KEY)
      }

    if (nodeIntent != null) {
      startActivity(nodeIntent)
    } else {
      Log.e(TAG, NODE_NOT_LAUNCHED_ERROR_MSG)
    }
    finish()
  }

  /**
   * Returns true if either the code is triggered by Robolectric or else there is some test
   * configuration set by Test Process and the caller is instrumented. It will always return false
   * when it is called in production.
   */
  private fun hasPermission(): Boolean {
    return when {
      requireRobolectric() -> true
      isTestConfigSet(context = this) -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
          return TestPermissions.uidIsInstrumented(this, launchedFromUid)
        } else {
          return true
        }
      }
      else -> false
    }
  }

  private fun requireRobolectric() = "robolectric" == Build.FINGERPRINT

  /**
   * Returns if the test configuration has been set. It will always return false when it is called
   * in production.
   */
  private fun isTestConfigSet(context: Context): Boolean {
    val uri = ConfigProviderUtil.getTestConfigUri(context)
    val cursor =
      context.contentResolver.query(
        uri,
        arrayOf(ConfigProviderUtil.TEST_NODE_CLASS_COLUMN),
        /* selection= */ null,
        /* selectionArgs= */ null,
        /* sortOrder= */ null,
        /* cancellationSignal= */ null,
      ) ?: return false
    return cursor.use { it.moveToFirst() } // Returns if there is first record
  }

  companion object {
    private const val EXTRA_NODE_START_INTENT_KEY =
      "com.android.onboarding.bedsteadonboarding.activities.extra.NODE_START_INTENT"
    private const val NODE_NOT_LAUNCHED_ERROR_MSG = "Couldn't launch node as intent is null"
    private const val TAG = "TrampolineActivity"
  }
}
