package com.android.onboarding.contracts.testing

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.android.onboarding.contracts.IntentSerializer
import com.android.onboarding.contracts.NodeId
import com.android.onboarding.contracts.OnboardingActivityApiContract
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import org.apache.commons.lang3.ClassUtils.isPrimitiveOrWrapper
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

const val TEST_NODE_ID: NodeId = -666

private fun isPrimitiveArray(obj: Any): Boolean =
  obj is ByteArray ||
    obj is CharArray ||
    obj is ShortArray ||
    obj is IntArray ||
    obj is LongArray ||
    obj is DoubleArray ||
    obj is FloatArray ||
    obj is BooleanArray

/**
 * Compares that [lhs] is equal to [rhs] via reflection by recursively checking that all kotlin
 * properties are equal. The usage of this function should only be reserved for cases where the
 * objects being checked cannot guarantee a reliable [Any.equals] implementation.
 *
 * Behaviour for Java objects is undefined.
 */
fun recursiveReflectionEquals(lhs: Any?, rhs: Any?): Boolean {
  if (lhs == null || rhs == null) return lhs == rhs
  if (isPrimitiveOrWrapper(lhs::class.java) || isPrimitiveOrWrapper(rhs::class.java)) {
    return lhs == rhs
  }
  if (isPrimitiveArray(lhs) && isPrimitiveArray(rhs)) {
    return arrayOf(lhs).contentDeepEquals(arrayOf(rhs))
  }
  if (lhs is Array<*> && rhs is Array<*>) {
    return lhs.zip(rhs).none { (l, r) -> !recursiveReflectionEquals(l, r) }
  }
  if (lhs is Iterable<*> && rhs is Iterable<*>) {
    return lhs.zip(rhs).none { (l, r) -> !recursiveReflectionEquals(l, r) }
  }
  val leftProps = lhs::class.memberProperties.associateBy(KProperty1<*, *>::name)
  val rightProps = rhs::class.memberProperties.associateBy(KProperty1<*, *>::name)
  @Suppress("UNCHECKED_CAST")
  return leftProps.none search@{ (lName, lProp) ->
    val rProp = rightProps[lName] ?: return@search false
    val lValue =
      (lProp as KProperty1<Any, *>).run {
        val accessible = isAccessible
        isAccessible = true
        val value = get(lhs)
        isAccessible = accessible
        value
      }
    val rValue =
      (rProp as KProperty1<Any, *>).run {
        val accessible = isAccessible
        isAccessible = true
        val value = get(rhs)
        isAccessible = accessible
        value
      }
    !recursiveReflectionEquals(lValue, rValue)
  }
}

/** Assert that a contract's arguments encode correctly. */
fun <I> assertArgumentEncodesCorrectly(contract: OnboardingActivityApiContract<I, *>, argument: I) {
  val context = ApplicationProvider.getApplicationContext<Context>()
  val intent = contract.createIntent(context, argument)
  val out = contract.extractArgument(intent)

  assertThat(recursiveReflectionEquals(out, argument)).isTrue()
}

/**
 * Assert that a contract's result encodes correctly.
 *
 * <p>Due to an implementation detail, tests using this must be using RobolectricTestRunner
 */
fun <O> assertReturnValueEncodesCorrectly(contract: OnboardingActivityApiContract<*, O>, value: O) {
  val controller = Robolectric.buildActivity(Activity::class.java)
  /*
   * Cannot use [AutoCloseable::use] since [ActivityController]
   * does not implement [AutoCloseable] on AOSP
   */
  try {
    val activity = controller.get()
    contract.setResult(activity, value)
    val shadowActivity = shadowOf(activity)
    val result = contract.parseResult(shadowActivity.resultCode, shadowActivity.resultIntent)

    assertThat(recursiveReflectionEquals(result, value)).isTrue()
  } finally {
    controller.pause()
    controller.stop()
    controller.destroy()
  }
}

/** Assert that an object can be correctly written to and parsed from an Intent. */
fun <I> assertIntentEncodesCorrectly(parser: IntentSerializer<I>, obj: I) {
  val intent = Intent()
  parser.write(intent, obj)
  val out = parser.read(intent)

  assertThat(recursiveReflectionEquals(out, obj)).isTrue()
}

/**
 * Assert that a given [serializer] decodes an empty [Intent] without throwing an exception (fails
 * lazily on property access).
 */
fun <I : Any> assertEmptyIntentDecodingFailsLazily(serializer: IntentSerializer<I>) {
  val intent = Intent()
  val arg = serializer.read(intent)
  val failures =
    arg::class
      .memberProperties
      .map { @Suppress("UNCHECKED_CAST") (it as KProperty1<I, *>) }
      .map { runCatching { it.get(arg) } }
      .filter(Result<*>::isFailure)
  assertWithMessage("Accessing parsed properties throws lazy errors").that(failures).isNotEmpty()
}
