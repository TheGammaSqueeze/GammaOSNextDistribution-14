/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.car.carlaunchercommon.shortcuts

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Application.ActivityLifecycleCallbacks
import android.app.admin.DevicePolicyManager
import android.car.media.CarMediaManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import com.android.car.carlaunchercommon.R
import com.android.car.hidden.apis.HiddenApiAccess.hasBaseUserRestriction
import com.android.car.hidden.apis.HiddenApiAccess.isDebuggable
import com.android.car.ui.AlertDialogBuilder
import com.android.car.ui.shortcutspopup.CarUiShortcutsPopup

/**
 * @property context the [Context] for the user that the app is running in.
 * @property mediaServiceComponents list of [ComponentName] of the services the adhere to the media
 * service interface
 */
open class ForceStopShortcutItem(
    private val context: Context,
    private val packageName: String,
    private val displayName: CharSequence,
    private val carMediaManager: CarMediaManager?,
    private val mediaServiceComponents: Set<ComponentName>
) : CarUiShortcutsPopup.ShortcutItem {
    // todo(b/312718542): hidden class(CarMediaManager) usage

    companion object {
        private const val TAG = "ForceStopShortcutItem"
        private val DEBUG = isDebuggable()
    }

    private var forceStopDialog: AlertDialog? = null

    init {
        // todo(b/323021079): Close alertdialog on Fragment's onPause
        if (context is Activity) {
            context.registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(p0: Activity, p1: Bundle?) {
                    // no-op
                }

                override fun onActivityStarted(p0: Activity) {
                    // no-op
                }

                override fun onActivityResumed(p0: Activity) {
                    // no-op
                }

                override fun onActivityPaused(p0: Activity) {
                    forceStopDialog?.dismiss()
                    forceStopDialog = null
                }

                override fun onActivityStopped(p0: Activity) {
                    // no-op
                }

                override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
                    // no-op
                }

                override fun onActivityDestroyed(p0: Activity) {
                    // no-op
                }
            })
        }
    }

    override fun data(): CarUiShortcutsPopup.ItemData {
        return CarUiShortcutsPopup.ItemData(
            R.drawable.ic_force_stop_caution_icon,
            context.resources.getString(
                R.string.stop_app_shortcut_label
            )
        )
    }

    override fun onClick(): Boolean {
        val builder = getAlertDialogBuilder(context)
            .setTitle(R.string.stop_app_dialog_title)
            .setMessage(R.string.stop_app_dialog_text)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                forceStop(packageName, displayName)
            }
            .setNegativeButton(
                android.R.string.cancel,
                null // listener
            )
        builder.create().let {
            it.window?.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
            forceStopDialog = it
            it.show()
        }
        return true
    }

    override fun isEnabled(): Boolean {
        return shouldAllowStopApp(packageName)
    }

    /**
     * @param packageName name of the package to stop the app
     * @return true if an app should show the Stop app action
     */
    private fun shouldAllowStopApp(packageName: String): Boolean {
        val dm = context.getSystemService(DevicePolicyManager::class.java)
        if (dm == null || dm.packageHasActiveAdmins(packageName)) {
            return false
        }
        try {
            val appInfo = context.packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
            // Show only if the User has no restrictions to force stop this app
            if (hasUserRestriction(appInfo)) {
                return false
            }
            // Show only if the app is running
            if (appInfo.flags and ApplicationInfo.FLAG_STOPPED == 0) {
                return true
            }
        } catch (e: PackageManager.NameNotFoundException) {
            if (DEBUG) Log.d(TAG, "shouldAllowStopApp() package $packageName was not found")
        }
        return false
    }

    /**
     * @return true if the user has restrictions to force stop an app with `appInfo`
     */
    private fun hasUserRestriction(appInfo: ApplicationInfo): Boolean {
        val restriction = UserManager.DISALLOW_APPS_CONTROL
        val userManager = context.getSystemService(UserManager::class.java)
        if (userManager == null) {
            if (DEBUG) Log.e(TAG, " Disabled because UserManager is null")
            return true
        }
        if (!userManager.hasUserRestriction(restriction)) {
            return false
        }
        val user = UserHandle.getUserHandleForUid(appInfo.uid)
        if (hasBaseUserRestriction(userManager, restriction, user)) {
            if (DEBUG) Log.d(TAG, " Disabled because $user has $restriction restriction")
            return true
        }
        // Not disabled for this User
        return false
    }

    /**
     * Force stops an app
     */
    @VisibleForTesting
    fun forceStop(packageName: String, displayName: CharSequence) {
        // Both MEDIA_SOURCE_MODE_BROWSE and MEDIA_SOURCE_MODE_PLAYBACK should be replaced to their
        // previous available values
        maybeReplaceMediaSource(packageName, CarMediaManager.MEDIA_SOURCE_MODE_BROWSE)
        maybeReplaceMediaSource(packageName, CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK)

        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return
        // todo(b/312718542): hidden api(ActivityManager.forceStopPackage) usage
        activityManager.forceStopPackage(packageName)
        val message = context.resources.getString(R.string.stop_app_success_toast_text, displayName)
        createToast(context, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Updates the MediaSource to second most recent if [packageName] is current media source.
     * @param mode media source mode (ex. [CarMediaManager.MEDIA_SOURCE_MODE_BROWSE])
     */
    private fun maybeReplaceMediaSource(packageName: String, mode: Int) {
        if (!isCurrentMediaSource(packageName, mode)) {
            if (DEBUG) Log.e(TAG, "Not current media source")
            return
        }
        // find the most recent source from history not equal to force-stopping package
        val mediaSources = carMediaManager?.getLastMediaSources(mode)
        var componentName = mediaSources?.firstOrNull { it?.packageName != packageName }
        if (componentName == null) {
            // no recent package found, find from all available media services.
            componentName = mediaServiceComponents.firstOrNull { it.packageName != packageName }
        }
        if (componentName == null) {
            if (DEBUG) Log.e(TAG, "Stop-app, no alternative media service found")
            return
        }
        carMediaManager?.setMediaSource(componentName, mode)
    }

    private fun isCurrentMediaSource(packageName: String, mode: Int): Boolean {
        val componentName = carMediaManager?.getMediaSource(mode)
            ?: return false // There is no current media source.
        if (DEBUG) Log.e(TAG, "isCurrentMediaSource: $packageName, $componentName")
        return componentName.packageName == packageName
    }

    /**
     * Should be overridden in the test to provide a mock [AlertDialogBuilder]
     */
    @VisibleForTesting
    open fun getAlertDialogBuilder(context: Context) = AlertDialogBuilder(context)

    /**
     * Should be overridden in the test to provide a mock [Toast]
     */
    @VisibleForTesting
    open fun createToast(context: Context, text: CharSequence, duration: Int): Toast =
        Toast.makeText(context, text, duration)
}
