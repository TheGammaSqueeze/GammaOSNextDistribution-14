package com.android.onboarding.testing

import com.google.common.truth.Fact.fact
import com.google.common.truth.Fact.simpleFact
import com.google.common.truth.FailureMetadata
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Subject
import com.google.common.truth.ThrowableSubject
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertThat
import com.google.errorprone.annotations.CanIgnoreReturnValue
import kotlin.reflect.KClass

/** A [Truth] [Subject] to assert expected [Throwable]s being thrown. */
class ErrorSubject private constructor(metadata: FailureMetadata, private val actual: () -> Any?) :
  Subject(metadata, actual) {

  @CanIgnoreReturnValue
  inline fun <reified T : Throwable> failsWith(): ThrowableSubject = failsWith(T::class)

  @CanIgnoreReturnValue
  fun <T : Throwable> failsWith(expected: KClass<T>): ThrowableSubject {
    val error = runCatching(actual).exceptionOrNull()
    if (error == null) {
      failWithoutActual(
        fact("expected to fail with", expected.qualifiedName),
        simpleFact("but did not fail"),
      )
    } else if (!expected.java.isInstance(error)) {
      failWithoutActual(
        fact("expected to fail with", expected.qualifiedName),
        fact("but failed with", error::class.qualifiedName),
      )
    }
    return assertThat(error)
  }

  companion object : Factory<ErrorSubject, () -> Any?> {
    override fun createSubject(metadata: FailureMetadata, actual: (() -> Any?)?): ErrorSubject =
      ErrorSubject(metadata, actual ?: error("Unable to assert empty lambda"))
  }
}

/** @see assertFailsWith */
inline fun <reified T : Throwable> StandardSubjectBuilder.failsWith(noinline action: () -> Any?) {
  about(ErrorSubject).that(action).failsWith<T>()
}

/**
 * Unfortunately @[CanIgnoreReturnValue] is not supported on inline library functions and as such
 * this utility function is not returning [ThrowableSubject] for further assertions. If you need
 * further assertions consider using one of the more verbose forms:
 * ```
 * @get:Rule val expect: Expect = Expect.create()
 * expect.about(ErrorSubject).that { functionUnderTest() }.failsWith<Throwable>().isNotNull()
 *
 * assertAbout(ErrorSubject).that { functionUnderTest() }.failsWith<Throwable>().isNotNull()
 * ```
 */
inline fun <reified T : Throwable> assertFailsWith(noinline action: () -> Any?) {
  assertAbout(ErrorSubject).that(action).failsWith<T>()
}
