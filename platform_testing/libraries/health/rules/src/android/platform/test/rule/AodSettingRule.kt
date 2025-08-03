package android.platform.test.rule

import android.provider.Settings

/**
 * Rule that allows to control whether the AOD setting is enabled. Also restores the setting to its
 * original value at the end of the test.
 */
class AodSettingRule(enabled: Boolean) :
    SecureSettingRule<Boolean>(
        settingName = Settings.Secure.DOZE_ALWAYS_ON,
        initialValue = enabled,
    )
