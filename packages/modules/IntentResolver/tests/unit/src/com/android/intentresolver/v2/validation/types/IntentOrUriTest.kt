package com.android.intentresolver.v2.validation.types

import android.content.Intent
import android.content.Intent.URI_INTENT_SCHEME
import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.truth.content.IntentSubject.assertThat
import com.android.intentresolver.v2.validation.Importance.CRITICAL
import com.android.intentresolver.v2.validation.Importance.WARNING
import com.android.intentresolver.v2.validation.Invalid
import com.android.intentresolver.v2.validation.NoValue
import com.android.intentresolver.v2.validation.Valid
import com.android.intentresolver.v2.validation.ValueIsWrongType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IntentOrUriTest {

    /** Test for validation success when the value is an Intent. */
    @Test
    fun intent() {
        val keyValidator = IntentOrUri("key")
        val values = mapOf("key" to Intent("GO"))

        val result = keyValidator.validate(values::get, CRITICAL)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<Intent>
        assertThat(result.value).hasAction("GO")
    }

    /** Test for validation success when the value is a Uri. */
    @Test
    fun uri() {
        val keyValidator = IntentOrUri("key")
        val values = mapOf("key" to Intent("GO").toUri(URI_INTENT_SCHEME).toUri())

        val result = keyValidator.validate(values::get, CRITICAL)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<Intent>
        assertThat(result.value).hasAction("GO")
    }

    /** Test the failure result when the value is missing. */
    @Test
    fun missing() {
        val keyValidator = IntentOrUri("key")

        val result = keyValidator.validate({ null }, CRITICAL)

        assertThat(result).isInstanceOf(Invalid::class.java)
        result as Invalid<Intent>

        assertThat(result.errors)
                .containsExactly(NoValue("key", CRITICAL, Intent::class))
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

    /**
     * Test for failure result when the value is neither Intent nor Uri, with importance CRITICAL.
     */
    @Test
    fun wrongType_required() {
        val keyValidator = IntentOrUri("key")
        val values = mapOf("key" to 1)

        val result = keyValidator.validate(values::get, CRITICAL)

        assertThat(result).isInstanceOf(Invalid::class.java)
        result as Invalid<Intent>

        assertThat(result.errors)
            .containsExactly(
                ValueIsWrongType(
                    "key",
                    importance = CRITICAL,
                    actualType = Int::class,
                    allowedTypes = listOf(Intent::class, Uri::class)
                )
            )
    }

    /**
     * Test for warnings when the value is neither Intent nor Uri, with importance WARNING.
     */
    @Test
    fun wrongType_optional() {
        val keyValidator = IntentOrUri("key")
        val values = mapOf("key" to 1)

        val result = keyValidator.validate(values::get, WARNING)

        assertThat(result).isInstanceOf(Invalid::class.java)
        result as Invalid<Intent>

        assertThat(result.errors)
                .containsExactly(
                    ValueIsWrongType(
                        "key",
                        importance = WARNING,
                        actualType = Int::class,
                        allowedTypes = listOf(Intent::class, Uri::class)
                    )
                )
    }
}
