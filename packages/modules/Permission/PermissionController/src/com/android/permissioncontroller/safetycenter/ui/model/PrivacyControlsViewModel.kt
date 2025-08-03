/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.safetycenter.ui.model

import android.app.Application
import android.content.Intent
import android.hardware.SensorPrivacyManager
import android.hardware.SensorPrivacyManager.Sensors
import android.hardware.SensorPrivacyManager.TOGGLE_TYPE_SOFTWARE
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.provider.DeviceConfig
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.data.SmartUpdateMediatorLiveData
import com.android.settingslib.RestrictedLockUtils
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin

/** Viewmodel for the privacy controls page. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class PrivacyControlsViewModel(private val app: Application) : AndroidViewModel(app) {

    private val sensorPrivacyManager: SensorPrivacyManager =
        app.getSystemService(SensorPrivacyManager::class.java)!!
    private val userManager: UserManager = app.getSystemService(UserManager::class.java)!!

    private val CONFIG_CLIPBOARD_SHOW_ACCESS_NOTIFICATIONS =
        app.getString(R.string.clipboard_show_access_notifications_config)
    private val CONFIG_SHOW_ACCESS_NOTIFICATIONS_DEFAULT =
        app.getString(R.string.show_access_notifications_default_config)
    private val CONFIG_MIC_TOGGLE_ENABLED = app.getString(R.string.mic_toggle_enable_config)
    private val CONFIG_CAMERA_TOGGLE_ENABLED = app.getString(R.string.camera_toggle_enable_config)

    enum class Pref(val key: String, @StringRes val titleResId: Int) {
        MIC("privacy_mic_toggle", R.string.mic_toggle_title),
        CAMERA("privacy_camera_toggle", R.string.camera_toggle_title),
        LOCATION("privacy_location_access", R.string.location_settings),
        CLIPBOARD("show_clip_access_notification", R.string.show_clip_access_notification_title),
        SHOW_PASSWORD("show_password", R.string.show_password_title);

        companion object {
            @JvmStatic fun findByKey(inputKey: String) = values().find { it.key == inputKey }
        }
    }

    data class PrefState(val visible: Boolean, val checked: Boolean, val admin: EnforcedAdmin?)

    val controlStateLiveData: SmartUpdateMediatorLiveData<Map<Pref, PrefState>> =
        object :
            SmartUpdateMediatorLiveData<@JvmSuppressWildcards Map<Pref, PrefState>>(),
            SensorPrivacyManager.OnSensorPrivacyChangedListener {

            override fun onUpdate() {
                val shownPrefs = mutableMapOf<Pref, PrefState>()
                shownPrefs[Pref.CAMERA] =
                    getSensorToggleState(
                        Sensors.CAMERA,
                        UserManager.DISALLOW_CAMERA_TOGGLE,
                        CONFIG_CAMERA_TOGGLE_ENABLED
                    )
                shownPrefs[Pref.MIC] =
                    getSensorToggleState(
                        Sensors.MICROPHONE,
                        UserManager.DISALLOW_MICROPHONE_TOGGLE,
                        CONFIG_MIC_TOGGLE_ENABLED
                    )
                shownPrefs[Pref.CLIPBOARD] =
                    PrefState(visible = true, checked = isClipboardEnabled(), admin = null)
                shownPrefs[Pref.SHOW_PASSWORD] =
                    PrefState(
                        visible = shouldDisplayShowPasswordToggle(),
                        checked = isShowPasswordEnabled(),
                        admin = null
                    )
                value = shownPrefs
            }

            override fun onActive() {
                sensorPrivacyManager.addSensorPrivacyListener(this)
                super.onActive()
                update()
            }

            override fun onInactive() {
                super.onInactive()
                sensorPrivacyManager.removeSensorPrivacyListener(this)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onSensorPrivacyChanged(sensor: Int, enabled: Boolean) {
                update()
            }
        }

    fun handlePrefClick(fragment: Fragment, pref: Pref, admin: EnforcedAdmin?) {
        when (pref) {
            Pref.MIC -> toggleSensorOrShowAdmin(fragment, Sensors.MICROPHONE, admin)
            Pref.CAMERA -> toggleSensorOrShowAdmin(fragment, Sensors.CAMERA, admin)
            Pref.LOCATION -> goToLocation(fragment)
            Pref.CLIPBOARD -> toggleClipboard()
            Pref.SHOW_PASSWORD -> toggleShowPassword()
        }
    }

    private fun toggleSensorOrShowAdmin(fragment: Fragment, sensor: Int, admin: EnforcedAdmin?) {
        if (admin != null) {
            RestrictedLockUtils.sendShowAdminSupportDetailsIntent(fragment.context, admin)
            return
        }
        val blocked = sensorPrivacyManager.isSensorPrivacyEnabled(TOGGLE_TYPE_SOFTWARE, sensor)
        sensorPrivacyManager.setSensorPrivacy(sensor, !blocked)
    }

    private fun goToLocation(fragment: Fragment) {
        fragment.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun getSensorToggleState(
        sensor: Int,
        restriction: String,
        enableConfig: String
    ): PrefState {
        val admin = RestrictedLockUtils.getProfileOrDeviceOwner(app, Process.myUserHandle())
        val sensorConfigEnabled =
            DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_PRIVACY, enableConfig, true)
        return PrefState(
            visible = sensorConfigEnabled && sensorPrivacyManager.supportsSensorToggle(sensor),
            checked = !sensorPrivacyManager.isSensorPrivacyEnabled(TOGGLE_TYPE_SOFTWARE, sensor),
            admin =
                if (
                    userManager
                        .getUserRestrictionSources(restriction, Process.myUserHandle())
                        .isNotEmpty()
                ) {
                    admin
                } else {
                    null
                }
        )
    }

    private fun isClipboardEnabled(): Boolean {
        val clipboardDefaultEnabled =
            DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_CLIPBOARD,
                CONFIG_SHOW_ACCESS_NOTIFICATIONS_DEFAULT,
                true
            )
        val defaultSetting = if (clipboardDefaultEnabled) 1 else 0
        return Settings.Secure.getInt(
            app.contentResolver,
            CONFIG_CLIPBOARD_SHOW_ACCESS_NOTIFICATIONS,
            defaultSetting
        ) != 0
    }

    private fun toggleClipboard() {
        val newState = if (isClipboardEnabled()) 0 else 1
        Settings.Secure.putInt(
            app.contentResolver,
            CONFIG_CLIPBOARD_SHOW_ACCESS_NOTIFICATIONS,
            newState
        )
    }

    private fun isShowPasswordEnabled(): Boolean {
        return Settings.System.getInt(app.contentResolver, Settings.System.TEXT_SHOW_PASSWORD, 1) !=
            0
    }

    private fun toggleShowPassword() {
        Settings.System.putInt(
            app.contentResolver,
            Settings.System.TEXT_SHOW_PASSWORD,
            if (isShowPasswordEnabled()) 0 else 1
        )
    }

    private fun shouldDisplayShowPasswordToggle(): Boolean {
        return app.resources.getBoolean(R.bool.config_display_show_password_toggle)
    }
}

/**
 * Factory for a PrivacyControlsViewModel
 *
 * @param app The current application
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class PrivacyControlsViewModelFactory(
    private val app: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return PrivacyControlsViewModel(app) as T
    }
}
