package com.android.healthconnect.controller.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager.NameNotFoundException
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Functions that help dealing with app stores. */
@Singleton
class AppStoreUtils @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        private const val TAG = "HCAppStoreUtil"
    }

    private val packageManager = context.packageManager

    /**
     * Returns the app store intent for a package name, returns null if the package is not installed
     */
    fun getAppStoreLink(packageName: String): Intent? {
        val installerPackageName = getInstallerPackageName(packageName)
        return getAppStoreLink(installerPackageName, packageName)
    }

    private fun getInstallerPackageName(packageName: String): String? {
        var installerPackageName: String? = null

        try {
            val source = packageManager.getInstallSourceInfo(packageName)

            // By default use the installing package name
            installerPackageName = source.installingPackageName

            // Use the recorded originating package name only if the initiating package is a system
            // app (eg. Package Installer). The originating package is not verified by the platform,
            // so we choose to ignore this when supplied by a non-system app.
            val originatingPackageName = source.originatingPackageName
            val initiatingPackageName = source.initiatingPackageName
            if (originatingPackageName != null && initiatingPackageName != null) {
                val ai = packageManager.getApplicationInfo(initiatingPackageName, 0)
                if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
                    installerPackageName = originatingPackageName
                }
            }
        } catch (exception: NameNotFoundException) {
            Log.e(
                TAG, "Exception while retrieving the package installer of $packageName", exception)
        }

        return installerPackageName
    }

    private fun getAppStoreLink(installerPackageName: String?, packageName: String): Intent? {
        val intent = Intent(Intent.ACTION_SHOW_APP_INFO)
        if (installerPackageName != null) {
            // if we cannot find the installer package name we can still
            // send the intent which should be handled by one app
            intent.setPackage(installerPackageName)
        }

        val result = resolveIntent(intent)
        if (result != null) {
            result.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
            return result
        }

        return null
    }

    private fun resolveIntent(intent: Intent): Intent? {
        val resolveInfoResult = packageManager.resolveActivity(intent, 0)
        return if (resolveInfoResult != null) {
            Intent(intent.action)
                .setClassName(
                    resolveInfoResult.activityInfo.packageName, resolveInfoResult.activityInfo.name)
        } else null
    }
}
