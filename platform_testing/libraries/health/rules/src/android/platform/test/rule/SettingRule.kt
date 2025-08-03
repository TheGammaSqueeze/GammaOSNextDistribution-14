package android.platform.test.rule

import android.platform.uiautomator_helpers.WaitUtils.ensureThat
import android.provider.Settings
import org.junit.rules.ExternalResource

private val supportedTypes =
    listOf(Boolean::class, Int::class, Long::class, Float::class, String::class)

/** Base rule to set values in [Settings]. The value is then reset at the end of the test. */
abstract class SettingRule<T>(
    private val initialValue: T? = null,
) : ExternalResource() {

    private var originalValueAsString: String? = null

    override fun before() {
        originalValueAsString = getSettingValueAsString()
        if (initialValue != null) {
            setSettingValue(initialValue)
        }
    }

    override fun after() {
        setSettingValueAsString(originalValueAsString)
    }

    fun clearValue() {
        setSettingValueAsString(null)
    }

    inline fun <reified T> getSettingValue(): T? {
        val valueAsString = getSettingValueAsString() ?: return null
        val actualValue =
            when (T::class) {
                Boolean::class -> valueAsString.toInt() == 1
                Int::class -> valueAsString.toInt()
                Long::class -> valueAsString.toLong()
                Float::class -> valueAsString.toFloat()
                String::class -> valueAsString
                else -> throw IllegalArgumentException("Type not supported: ${T::class}")
            }
        return actualValue as T
    }

    fun setSettingValue(value: T?) {
        val valueAsString =
            when {
                value == null -> null
                value is Boolean -> if (value) "1" else "0"
                value!!::class in supportedTypes -> value.toString()
                else -> throw IllegalArgumentException("Unsupported type: ${value!!::class}")
            }
        setSettingValueAsString(valueAsString)
        ensureThat("New setting value set") { getSettingValueAsString() == valueAsString }
    }

    abstract fun getSettingValueAsString(): String?

    abstract fun setSettingValueAsString(value: String?)
}
