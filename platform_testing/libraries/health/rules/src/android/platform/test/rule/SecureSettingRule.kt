package android.platform.test.rule

import android.platform.uiautomator_helpers.DeviceHelpers.context
import android.provider.Settings

/** Base rule to set values in [Settings.Secure]. The value is then reset at the end of the test. */
open class SecureSettingRule<T : Any>(
    private val settingName: String,
    initialValue: T? = null,
) : SettingRule<T>(initialValue) {

    override fun getSettingValueAsString(): String? =
        Settings.Secure.getString(context.contentResolver, settingName)

    override fun setSettingValueAsString(value: String?) {
        Settings.Secure.putString(context.contentResolver, settingName, value)
    }
}
