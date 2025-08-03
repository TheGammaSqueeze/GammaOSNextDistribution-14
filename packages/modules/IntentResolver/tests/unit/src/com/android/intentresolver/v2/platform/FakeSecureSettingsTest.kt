package com.android.intentresolver.v2.platform

import com.google.common.truth.Truth.assertThat

class FakeSecureSettingsTest {

    private val secureSettings = fakeSecureSettings {
        putInt(intKey, intVal)
        putString(stringKey, stringVal)
        putFloat(floatKey, floatVal)
        putLong(longKey, longVal)
    }

    fun testExpectedValues_returned() {
        assertThat(secureSettings.getInt(intKey)).isEqualTo(intVal)
        assertThat(secureSettings.getString(stringKey)).isEqualTo(stringVal)
        assertThat(secureSettings.getFloat(floatKey)).isEqualTo(floatVal)
        assertThat(secureSettings.getLong(longKey)).isEqualTo(longVal)
    }

    fun testUndefinedValues_returnNull() {
        assertThat(secureSettings.getInt("unknown")).isNull()
        assertThat(secureSettings.getString("unknown")).isNull()
        assertThat(secureSettings.getFloat("unknown")).isNull()
        assertThat(secureSettings.getLong("unknown")).isNull()
    }

    /**
     * FakeSecureSettings models the real secure settings by storing values in String form. The
     * value is returned if/when it can be parsed from the string value, otherwise null.
     */
    fun testMismatchedTypes() {
        assertThat(secureSettings.getString(intKey)).isEqualTo(intVal.toString())
        assertThat(secureSettings.getString(floatKey)).isEqualTo(floatVal.toString())
        assertThat(secureSettings.getString(longKey)).isEqualTo(longVal.toString())

        assertThat(secureSettings.getInt(stringKey)).isNull()
        assertThat(secureSettings.getLong(stringKey)).isNull()
        assertThat(secureSettings.getFloat(stringKey)).isNull()

        assertThat(secureSettings.getInt(longKey)).isNull()
        assertThat(secureSettings.getFloat(longKey)).isNull() // TODO: verify Long.MAX > Float.MAX ?

        assertThat(secureSettings.getLong(floatKey)).isNull() // TODO: or is Float.MAX > Long.MAX?
        assertThat(secureSettings.getInt(floatKey)).isNull()
    }

    companion object Data {
        const val intKey = "int"
        const val intVal = Int.MAX_VALUE

        const val stringKey = "string"
        const val stringVal = "String"

        const val floatKey = "float"
        const val floatVal = Float.MAX_VALUE

        const val longKey = "long"
        const val longVal = Long.MAX_VALUE
    }
}
