package com.android.onboarding.contracts

import com.google.errorprone.annotations.CanIgnoreReturnValue
import kotlin.jvm.Throws

/** Implementing entities provide means to validate the correctness of [A] */
interface ArgumentValidator<A : Any> {
  /**
   * Validates given object or throws an exception
   *
   * @receiver the object to validate
   */
  @Throws(IllegalArgumentException::class) fun A.validate()
}

/**
 * Chaining-enabled validation condition that throws an error if it's not met
 *
 * @param message to pass when throwing an error
 * @param condition lambda describing correct state
 * @receiver the value to validate
 */
@Throws(IllegalArgumentException::class)
@CanIgnoreReturnValue
inline fun <V> V.require(message: String, condition: (V) -> Boolean): V = also {
  require(condition(this)) { message }
}
