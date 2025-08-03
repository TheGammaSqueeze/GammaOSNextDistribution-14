package com.android.intentresolver.v2.validation.types

import android.content.Intent
import android.graphics.Point
import com.android.intentresolver.v2.validation.Importance.CRITICAL
import com.android.intentresolver.v2.validation.Importance.WARNING
import com.android.intentresolver.v2.validation.Invalid
import com.android.intentresolver.v2.validation.NoValue
import com.android.intentresolver.v2.validation.Valid
import com.android.intentresolver.v2.validation.ValueIsWrongType
import com.android.intentresolver.v2.validation.WrongElementType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ParceledArrayTest {

    /** Check that a array is handled correctly when valid. */
    @Test
    fun valid() {
        val keyValidator = ParceledArray("key", elementType = String::class)
        val values = mapOf("key" to arrayOf("String"))

        val result = keyValidator.validate(values::get, CRITICAL)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<List<String>>
        assertThat(result.value).containsExactly("String")
    }

    /** Check correct failure result when an array has the wrong element type. */
    @Test
    fun wrongElementType() {
        val keyValidator = ParceledArray("key", elementType = Intent::class)
        val values = mapOf("key" to arrayOf(Point()))

        val result = keyValidator.validate(values::get, CRITICAL)

        assertThat(result).isInstanceOf(Invalid::class.java)
        result as Invalid<List<Intent>>

        assertThat(result.errors)
            .containsExactly(
                // TODO: report with a new class `WrongElementType` to improve clarity
                WrongElementType(
                    "key",
                    importance = CRITICAL,
                    container = Array::class,
                    actualType = Point::class,
                    expectedType = Intent::class
                )
            )
    }

    /** Check correct failure result when an array value is missing. */
    @Test
    fun missing() {
        val keyValidator = ParceledArray("key", Intent::class)

        val result = keyValidator.validate(source = { null }, CRITICAL)

        assertThat(result).isInstanceOf(Invalid::class.java)
        result as Invalid<List<Intent>>

        assertThat(result.errors).containsExactly(NoValue("key", CRITICAL, Intent::class))
    }

    /** Check validation passes when value is null and importance is [WARNING] (optional). */
    @Test
    fun optional() {
        val keyValidator = ParceledArray("key", Intent::class)

        val result = keyValidator.validate(source = { null }, WARNING)

        assertThat(result).isInstanceOf(Invalid::class.java)
        result as Invalid<List<Intent>>

        assertThat(result.errors).isEmpty()
    }

    /** Check correct failure result when the array value itself is the wrong type. */
    @Test
    fun wrongType() {
        val keyValidator = ParceledArray("key", Intent::class)
        val values = mapOf("key" to 1)

        val result = keyValidator.validate(values::get, CRITICAL)

        assertThat(result).isInstanceOf(Invalid::class.java)
        result as Invalid<List<Intent>>

        assertThat(result.errors)
            .containsExactly(
                ValueIsWrongType(
                    "key",
                    importance = CRITICAL,
                    actualType = Int::class,
                    allowedTypes = listOf(Intent::class)
                )
            )
    }
}
