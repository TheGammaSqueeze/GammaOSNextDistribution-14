package android.platform.test.rule

import android.platform.uiautomator_helpers.DeviceHelpers.context
import android.platform.uiautomator_helpers.DeviceHelpers.shell
import android.provider.Settings

/** Base rule to set values in [Settings.System]. The value is then reset at the end of the test. */
open class SystemSettingRule<T : Any>(
    private val settingName: String,
    initialValue: T? = null,
) : SettingRule<T>(initialValue) {

    override fun getSettingValueAsString(): String? =
        Settings.System.getString(context.contentResolver, settingName)

    override fun setSettingValueAsString(value: String?) {
        // We don't have permission from the test to write System Settings. Using shell command
        // instead.
        if (value == null) {
            shell("settings delete system $settingName")
        } else {
            shell("settings put system $settingName $value")
        }
    }
}
