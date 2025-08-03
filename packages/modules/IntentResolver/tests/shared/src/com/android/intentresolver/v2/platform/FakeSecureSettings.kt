package com.android.intentresolver.v2.platform

/**
 * Creates a SecureSettings instance with predefined values:
 *
 *     val settings = fakeSecureSettings {
 *         putString("stringValue", "example")
 *         putInt("intValue", 42)
 *     }
 */
fun fakeSecureSettings(block: FakeSecureSettings.Builder.() -> Unit): SecureSettings {
    return FakeSecureSettings.Builder().apply(block).build()
}

/** An in memory implementation of [SecureSettings]. */
class FakeSecureSettings private constructor(private val map: Map<String, String>) :
    SecureSettings {

    override fun getString(name: String): String? = map[name]
    override fun getInt(name: String): Int? = getString(name)?.toIntOrNull()
    override fun getLong(name: String): Long? = getString(name)?.toLongOrNull()
    override fun getFloat(name: String): Float? = getString(name)?.toFloatOrNull()

    class Builder {
        private val map = mutableMapOf<String, String>()

        fun putString(name: String, value: String) {
            map[name] = value
        }
        fun putInt(name: String, value: Int) {
            map[name] = value.toString()
        }
        fun putLong(name: String, value: Long) {
            map[name] = value.toString()
        }
        fun putFloat(name: String, value: Float) {
            map[name] = value.toString()
        }

        fun build(): SecureSettings {
            return FakeSecureSettings(map.toMap())
        }
    }
}
