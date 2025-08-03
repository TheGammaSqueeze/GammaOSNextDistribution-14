package com.android.onboarding.versions

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/** The meta-data key used to indicate compliance date. */
private const val COMPLIANCE_DATE_METADATA_KEY = "onboarding-compliance-date"

/**
 * Entry point to onboarding versions check.
 *
 * <p>Versioning works by each onboarding component including a meta data of the following form in
 * their manifest:
 * <pre>{@code
 *   <meta-data android:name="onboarding-compliance-date" android:value="2023-07-10" />
 * }</pre>
 *
 * <p>This indicates that this APK is compatible with onboarding APIs as of 2023-07-10. Breaking API
 * changes should not be introduced from the point of view of this APK without that compliance date
 * being increased.
 *
 * <p>Note that any single onboarding session will be made up of several APKs working to different
 * compliance dates, so APKs should not assume that everyone is using the same APIs.
 */
class Versions internal constructor(private val context: Context) {
  val myComplianceDate: ComplianceDate by lazy {
    val app: ApplicationInfo =
      context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)

    val complianceDateString = app.metaData?.getString(COMPLIANCE_DATE_METADATA_KEY)

    complianceDateString?.let(::ComplianceDate) ?: ComplianceDate()
  }
}
