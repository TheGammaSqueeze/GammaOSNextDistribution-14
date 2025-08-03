package android.platform.test.rule

import android.provider.Settings

/**
 * Rule that allows to control whether the device-state based auto-rotation setting. Also restores
 * the setting to its original value at the end of the test.
 */
class DeviceStateRotationSettingRule(initialValue: String? = null) :
    SecureSettingRule<String>(
        settingName = Settings.Secure.DEVICE_STATE_ROTATION_LOCK,
        initialValue = initialValue,
    )
