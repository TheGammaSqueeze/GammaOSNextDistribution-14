package com.android.onboarding.versions

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.provider.Settings
import android.util.Log
import com.android.onboarding.versions.annotations.ChangeId
import com.android.onboarding.versions.annotations.ChangeRadius
import com.android.onboarding.versions.changes.ALL_CHANGE_IDS
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.UnsupportedOperationException

private const val LOG_TAG = "DefaultOnboardingChanges"

/**
 * Default implementation of [OnboardingChanges].
 *
 * For local testing, use `adb shell settings put global package.name.changeid 1` to enable and `adb
 * shell settings put global package.name.changeid 0` to disable. For example to enable change ID
 * 1234 on package `com.android.setup` we would use `adb shell settings put global
 * com.android.setup.1234 1`. You can also use `adb shell settings delete global
 * package.name.changeid` to use the default configured value.
 */
class DefaultOnboardingChanges(val context: Context) : OnboardingChanges {

  private val version = Versions(context)

  /**
   * Map of package to the compliance date for that package.
   *
   * If a package has failed to be loaded, the value will be null. If it has not been attempted the
   * key will be non-present.
   */
  private val packageComplianceDates =
    mutableMapOf<String, ComplianceDate?>().apply {
      put(context.packageName, version.myComplianceDate)
    }

  override fun currentProcessSupportsChange(changeId: Long): Boolean {
    return componentSupportsChange(context.packageName, changeId)
  }

  override fun requireCurrentProcessSupportsChange(changeId: Long) {
    if (currentProcessSupportsChange(changeId)) {
      return
    }

    // currentProcessSupportsChange will throw if this is null
    val changeData = ALL_CHANGE_IDS[changeId]!!

    if (changeData.available == ChangeId.NOT_AVAILABLE) {
      throw UnsupportedOperationException(
        "This API is not available. The change ($changeId) is set to not available. See OnboardingChanges for options for enabling this change during test and development."
      )
    }

    throw UnsupportedOperationException(
      "This API is not available. The change ($changeId) is available as of ${changeData.available} but the compliance date of this package is ${version.myComplianceDate}"
    )
  }

  override fun loadSupportedChanges(component: String) {
    val packageName = extractPackageFromComponent(component)

    if (packageName == context.packageName) {
      return
    }

    try {
      val packageInfo =
        context.packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)

      val complianceDateString =
        packageInfo.applicationInfo?.metaData?.getString("onboarding-compliance-date")
          ?: ComplianceDate.EARLIEST_COMPLIANCE_DATE_STRING
      packageComplianceDates[packageName] = ComplianceDate(complianceDateString)
    } catch (e: NameNotFoundException) {
      Log.w(LOG_TAG, "Could not find package info for $packageName", e)
      packageComplianceDates[packageName] = null
    }
  }

  override fun componentSupportsChange(component: String, changeId: Long): Boolean {
    val packageName = extractPackageFromComponent(component)

    val changeData =
      ALL_CHANGE_IDS[changeId]
        ?: throw IllegalStateException(
          "Invalid change ID $changeId - this should have been blocked by the conformance checker"
        )

    if (
      changeData.changeRadius == ChangeRadius.SINGLE_COMPONENT && packageName != context.packageName
    ) {
      // This should be blocked by conformance
      throw IllegalArgumentException(
        "You cannot query changes which are SINGLE_COMPONENT across process boundaries."
      )
    }

    val changeOverride = getChangeOverride(packageName, changeId)
    if (changeOverride != null) {
      return changeOverride
    }

    // If it's released, we assume it's available everywhere
    // (this call should be blocked by conformance)
    if (changeData.released != ChangeId.NOT_RELEASED) {
      return true
    }

    if (!packageComplianceDates.containsKey(packageName)) {
      loadSupportedChanges(component)
    }

    if (packageComplianceDates[packageName] == null) {
      // If we can't fetch the supported features we assume no non-released features
      return false
    }

    return packageComplianceDates[packageName]!!.isAvailable(changeData)
  }

  private fun getChangeOverride(packageName: String, changeId: Long): Boolean? {
    val settingKey = "$packageName.$changeId"

    return try {
      Settings.Global.getInt(context.contentResolver, settingKey) == 1
    } catch (e: Settings.SettingNotFoundException) {
      null
    }
  }

  private fun extractPackageFromComponent(component: String) = component.split("/", limit = 2)[0]
}
