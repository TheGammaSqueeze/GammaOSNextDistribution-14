package com.android.intentresolver.v2.validation.types

import com.android.intentresolver.v2.validation.Importance.CRITICAL
import com.android.intentresolver.v2.validation.Importance.WARNING
import com.android.intentresolver.v2.validation.Invalid
import com.android.intentresolver.v2.validation.NoValue
import com.android.intentresolver.v2.validation.Valid
import com.android.intentresolver.v2.validation.ValueIsWrongType
import org.junit.Test
import com.google.common.truth.Truth.assertThat

class SimpleValueTest {

    /** Test for validation success when the value is present and the correct type. */
    @Test
    fun present() {
        val keyValidator = SimpleValue("key", expected = Double::class)
        val values = mapOf("key" to Math.PI)

        val result = keyValidator.validate(values::get, CRITICAL)


        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<Double>
        assertThat(result.value).isEqualTo(Math.PI)
    }

    /** Test for validation success when the value is present and the correct type. */
    @Test
    fun wrongType() {
        val keyValidator = SimpleValue("key", expected = Double::class)
        val values = mapOf("key" to "Apple Pie")

        val result = keyValidator.validate(values::get, CRITICAL)

        assertThat(result).isInstanceOf(Invalid::class.java)
        result as Invalid<Double>
        assertThat(result.errors).containsExactly(
            ValueIsWrongType(
                "key",
                importance = CRITICAL,
                actualType = String::class,
                allowedTypes = listOf(Double::class)
            )
        )
    }

    /** Test the failure result when the value is missing. */
    @Test
    fun missing() {
        val keyValidator = SimpleValue("key", expected = Double::class)

        val result = keyValidator.validate(source = { null }, CRITICAL)

        assertThat(result).isInstanceOf(Invalid::class.java)
        result as Invalid<Double>

        assertThat(result.errors).containsExactly(NoValue("key", CRITICAL, Double::class))
    }


    /** Test the failure result when the value is missing. */
    @Test
    fun optional() {
        val keyValidator = SimpleValue("key", expected = Double::class)

        val result = keyValidator.validate(source = { null }, WARNING)

        assertThat(result).isInstanceOf(Invalid::class.java)
        result as Invalid<Double>

        // Note: As single optional validation result, the return must be Invalid
        // when there is no value to return, but no errors will be reported because
        // an optional value cannot be "missing".
        assertThat(result.errors).isEmpty()
    }
}
