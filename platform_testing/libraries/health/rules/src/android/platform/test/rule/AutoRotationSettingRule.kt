package android.platform.test.rule

import android.provider.Settings

/**
 * Rule that allows to control whether the auto-rotation setting is enabled. Also restores the
 * setting to its original value at the end of the test.
 */
class AutoRotationSettingRule(enabled: Boolean? = null) :
    SystemSettingRule<Boolean>(
        settingName = Settings.System.ACCELEROMETER_ROTATION,
        initialValue = enabled,
    ) {

    fun isEnabled() = getSettingValue<Boolean>()
}
