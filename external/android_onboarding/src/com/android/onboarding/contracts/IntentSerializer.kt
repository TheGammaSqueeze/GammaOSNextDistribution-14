package com.android.onboarding.contracts

import android.content.Intent

/** Marks functions that read or write intent data */
@DslMarker internal annotation class IntentManipulationDsl

/** Interface for writing an object to an [Intent] and reading the same object from an [Intent]. */
interface IntentSerializer<V> {
  /**
   * Utility intent builder that allows writing the [arg] and customising the intent settings
   *
   * @param arg to serialize into intent extras
   * @param builder to configure the intent
   */
  @IntentManipulationDsl
  fun Intent(arg: V, builder: Intent.() -> Unit = {}): Intent =
    Intent().also {
      write(it, arg)
      it.builder()
    }

  /**
   * Utility intent builder that allows writing the [arg] and customising the intent settings
   *
   * @param action the intent should send
   * @param arg to serialize into intent extras
   * @param builder to configure the intent
   */
  @IntentManipulationDsl
  fun Intent(action: String, arg: V, builder: Intent.() -> Unit = {}): Intent =
    Intent(arg) {
      this.action = action
      builder()
    }

  fun write(intent: Intent, value: V)

  fun read(intent: Intent): V

  /**
   * Calls [read] to get the value, but instead of throwing an error during read failures it instead
   * catches it, prints stack trace and returns null
   */
  fun readOrNull(intent: Intent): V? =
    runCatching { read(intent) }.onFailure(Throwable::printStackTrace).getOrNull()
}

/**
 * A serializer that does not expose [Intent] directly and instead works via [NodeAwareIntentScope]
 * abstraction that can be observed
 *
 * TODO This is living as a separate opt-in interface for now, but we should look into incorporating
 * it inside the contract class to make methods enforceable and protected
 */
interface NodeAwareIntentSerializer<V> : IntentSerializer<V>, NodeAware {
  fun NodeAwareIntentScope.write(value: V)

  fun NodeAwareIntentScope.read(): V

  override fun write(intent: Intent, value: V): Unit =
    NodeAwareIntentScope(nodeId, intent).use { it.write(value) }

  override fun read(intent: Intent): V = NodeAwareIntentScope(nodeId, intent).use { it.read() }
}
