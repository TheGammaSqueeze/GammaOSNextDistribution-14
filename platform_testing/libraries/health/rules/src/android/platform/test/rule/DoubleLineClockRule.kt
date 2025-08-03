package android.platform.test.rule

import android.provider.Settings

/**
 * Rule that allows to set the double line clock on the lockscreen to be enabled/disabled. Also
 * resets the setting to its original value at the end of the test.
 */
class DoubleLineClockRule :
    SecureSettingRule<Boolean>(
        settingName = Settings.Secure.LOCKSCREEN_USE_DOUBLE_LINE_CLOCK,
    )
