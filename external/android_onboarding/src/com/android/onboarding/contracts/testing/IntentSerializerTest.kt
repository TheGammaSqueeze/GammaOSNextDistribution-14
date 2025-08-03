package com.android.onboarding.contracts.testing

import com.android.onboarding.contracts.IntentSerializer
import org.junit.Test

abstract class IntentSerializerTest<V : Any> {
  protected abstract val target: IntentSerializer<V>
  protected abstract val data: V

  @Test
  fun completeArgument_encodesCorrectly() {
    assertIntentEncodesCorrectly(target, data)
  }
}

abstract class NodeAwareIntentSerializerTest<V : Any> : IntentSerializerTest<V>() {
  @Test
  fun noExtras_decodingFailsLazily() {
    assertEmptyIntentDecodingFailsLazily(target)
  }
}
