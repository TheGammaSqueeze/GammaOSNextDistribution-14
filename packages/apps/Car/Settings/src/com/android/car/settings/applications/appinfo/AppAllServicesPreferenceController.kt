/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.settings.applications.appinfo

import android.car.drivingstate.CarUxRestrictions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.location.LocationManager
import androidx.preference.Preference
import com.android.car.settings.common.FragmentController
import com.android.car.settings.common.Logger
import com.android.car.settings.common.PreferenceController

/** Preference Controller for the "All Services" preference in the "App Info" page. */
class AppAllServicesPreferenceController(
    context: Context,
    preferenceKey: String,
    fragmentController: FragmentController,
    uxRestrictions: CarUxRestrictions
) : PreferenceController<Preference>(context, preferenceKey, fragmentController, uxRestrictions) {
    private val packageManager = context.packageManager
    private var packageName: String? = null

    override fun onStartInternal() {
        getStorageSummary()?.let { preference.summary = it }
    }

    override fun getPreferenceType(): Class<Preference> = Preference::class.java

    override fun getDefaultAvailabilityStatus(): Int {
        return if (canPackageHandleIntent() && isLocationProvider()) {
            AVAILABLE
        } else {
            CONDITIONALLY_UNAVAILABLE
        }
    }

    override fun handlePreferenceClicked(preference: Preference): Boolean {
        startAllServicesActivity()
        return true
    }

    /**
     * Set the package name of the package for which the "All Services" activity needs to be shown.
     *
     * @param packageName Name of the package for which the services need to be shown.
     */
    fun setPackageName(packageName: String?) {
        this.packageName = packageName
    }

    private fun getStorageSummary(): CharSequence? {
        val resolveInfo = getResolveInfo(PackageManager.GET_META_DATA)
        if (resolveInfo == null) {
            LOG.d("mResolveInfo is null.")
            return null
        }
        val metaData = resolveInfo.activityInfo.metaData
        try {
            val pkgRes = packageManager.getResourcesForActivity(
                ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name))
            return pkgRes.getString(metaData.getInt(SUMMARY_METADATA_KEY))
        } catch (exception: Resources.NotFoundException) {
            LOG.d("Resource not found for summary string.")
        } catch (exception: PackageManager.NameNotFoundException) {
            LOG.d("Name of resource not found for summary string.")
        }
        return null
    }

    private fun isLocationProvider(): Boolean {
        val locationManagerService = context.getSystemService(LocationManager::class.java)
        return packageName?.let {
            locationManagerService?.isProviderPackage(
                /* provider = */ null,
                /* packageName = */ it,
                /* attributionTag = */ null)
        } ?: false
    }

    private fun canPackageHandleIntent(): Boolean = getResolveInfo(/* flags = */ 0) != null

    private fun startAllServicesActivity() {
        val featuresIntent = Intent(Intent.ACTION_VIEW_APP_FEATURES)
        // Resolve info won't be null as it is only shown for packages that can
        // handle the intent
        val resolveInfo = getResolveInfo(/* flags = */ 0)
        if (resolveInfo == null) {
            LOG.e("Resolve info is null, package unable to handle all services intent")
            return
        }
        featuresIntent.component =
                ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name)
        LOG.v("Starting the All Services activity with intent:" +
            featuresIntent.toUri(/* flags = */ 0))
        try {
            context.startActivity(featuresIntent)
        } catch (e: ActivityNotFoundException) {
            LOG.e("The app cannot handle android.intent.action.VIEW_APP_FEATURES")
        }
    }

    private fun getResolveInfo(flags: Int): ResolveInfo? {
        if (packageName == null) {
            return null
        }
        val featuresIntent = Intent(Intent.ACTION_VIEW_APP_FEATURES).apply {
            `package` = packageName
        }
        return packageManager.resolveActivity(featuresIntent, flags)
    }

    private companion object {
        val LOG = Logger(AppAllServicesPreferenceController::class.java)
        const val SUMMARY_METADATA_KEY = "app_features_preference_summary"
    }
}
