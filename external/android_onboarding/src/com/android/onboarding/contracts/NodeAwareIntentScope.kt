package com.android.onboarding.contracts

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.annotation.RequiresApi
import com.android.onboarding.contracts.NodeAwareIntentScope.IntentExtra.Invalid
import com.android.onboarding.contracts.NodeAwareIntentScope.IntentExtra.Present
import com.android.onboarding.nodes.AndroidOnboardingGraphLog
import com.android.onboarding.nodes.OnboardingGraphLog
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * @property androidIntent the intent this scope is wrapping for data manipulation
 * @property strict by default, closing the scope will only fail the node on the graph in case any
 *   invalid extras are detected without throwing an exception, however in strict mode it will throw
 *   as well
 */
@IntentManipulationDsl
class NodeAwareIntentScope(
  @OnboardingNodeId override val nodeId: NodeId,
  private val androidIntent: Intent,
  private val strict: Boolean = false,
) : NodeAware, AutoCloseable {
  internal sealed interface IntentExtra<V : Any> {
    data class Present<V : Any>(val value: V) : IntentExtra<V>

    data class Invalid<V : Any>(val reason: String) : IntentExtra<V>

    companion object {
      operator fun <T : Any> invoke(name: String, kClass: KClass<T>, value: T?): IntentExtra<T> =
        if (value == null) {
          Invalid("Intent extra [$name: ${kClass.simpleName}] is missing")
        } else {
          Present(value)
        }

      inline operator fun <reified T : Any> invoke(name: String, value: T?): IntentExtra<T> =
        invoke(name, T::class, value)
    }
  }

  abstract class IntentExtraDelegate<V> internal constructor() : ReadOnlyProperty<Any?, V> {
    abstract val value: V

    final override fun getValue(thisRef: Any?, property: KProperty<*>): V = value

    companion object {
      operator fun <V> invoke(provider: () -> V) =
        object : IntentExtraDelegate<V>() {
          override val value: V by lazy(provider)
        }
    }
  }

  inner class OptionalIntentExtraDelegate<V : Any>
  internal constructor(internal val extra: IntentExtra<out V>) : IntentExtraDelegate<V?>() {
    init {
      if (extra is Invalid<*>) errors.add(extra.reason)
    }

    override val value: V?
      get() =
        when (extra) {
          is Present -> extra.value
          is Invalid -> null
        }

    @IntentManipulationDsl
    val required: RequiredIntentExtraDelegate<V>
      get() = RequiredIntentExtraDelegate(extra)
  }

  inner class RequiredIntentExtraDelegate<V : Any>
  internal constructor(internal val extra: IntentExtra<out V>) : IntentExtraDelegate<V>() {
    init {
      if (extra is Invalid<*>) errors.add(extra.reason)
    }

    override val value: V
      get() =
        when (extra) {
          is Present -> extra.value
          is Invalid -> error("Intent extra cannot be resolved: ${extra.reason}")
        }

    @IntentManipulationDsl
    val optional: OptionalIntentExtraDelegate<V>
      get() = OptionalIntentExtraDelegate(extra)
  }

  @IntentManipulationDsl
  inline fun <T> IntentExtraDelegate<T>.validate(
    crossinline validator: (T) -> Unit
  ): IntentExtraDelegate<T> = IntentExtraDelegate { value.also(validator) }

  @IntentManipulationDsl
  inline fun <T, R> IntentExtraDelegate<T>.map(
    crossinline transform: (T) -> R
  ): IntentExtraDelegate<R> = IntentExtraDelegate { value.let(transform) }

  /** Similar to [map], but only calls [transform] on non-null value from the receiver */
  @IntentManipulationDsl
  inline fun <T, R> IntentExtraDelegate<T?>.mapOrNull(
    crossinline transform: (T) -> R
  ): IntentExtraDelegate<R?> = IntentExtraDelegate { value?.let(transform) }

  @IntentManipulationDsl
  inline fun <T1, T2, R> IntentExtraDelegate<T1>.zip(
    other: IntentExtraDelegate<T2>,
    crossinline zip: (T1, T2) -> R,
  ): IntentExtraDelegate<R> = IntentExtraDelegate { zip(value, other.value) }

  @IntentManipulationDsl
  infix fun <T, E : IntentExtraDelegate<T>> IntentExtraDelegate<T?>.or(
    other: E
  ): IntentExtraDelegate<T> = IntentExtraDelegate { value ?: other.value }

  @IntentManipulationDsl
  infix fun <T> IntentExtraDelegate<T?>.or(provider: () -> T): IntentExtraDelegate<T> =
    IntentExtraDelegate {
      value ?: provider()
    }

  @IntentManipulationDsl
  infix fun <T> IntentExtraDelegate<T?>.or(default: T): IntentExtraDelegate<T> =
    IntentExtraDelegate {
      value ?: default
    }

  @IntentManipulationDsl
  inline fun <T, R> (() -> T).map(crossinline transform: (T) -> R): () -> R = {
    invoke().let(transform)
  }

  @IntentManipulationDsl
  inline fun <T, R> (() -> T?).mapOrNull(crossinline transform: (T) -> R): () -> R? = {
    invoke()?.let(transform)
  }

  private val errors = mutableSetOf<String>()

  override fun close() {
    if (errors.isNotEmpty()) {
      val reason =
        errors.joinToString(prefix = "Detected invalid extras:\n\t", separator = "\n\t - ")
      AndroidOnboardingGraphLog.log(
        OnboardingGraphLog.OnboardingEvent.ActivityNodeFail(nodeId, reason)
      )
      if (strict) error(reason)
    }
  }

  // region DSL
  /**
   * Self-reference for more fluid write access
   *
   * ```
   * with(IntentScope) {
   *   intent[KEY] = {"value"}
   * }
   * ```
   */
  @IntentManipulationDsl val intent: NodeAwareIntentScope = this

  /** Provides observable access to [Intent.getAction] */
  @IntentManipulationDsl
  var action: String?
    get() = androidIntent.action
    set(value) {
      value?.let(androidIntent::setAction)
    }

  /** Provides observable access to [Intent.getType] */
  @IntentManipulationDsl
  var type: String?
    get() = androidIntent.type
    set(value) {
      value?.let(androidIntent::setType)
    }

  /** Provides observable access to [Intent.getData] */
  @IntentManipulationDsl
  var data: Uri?
    get() = androidIntent.data
    set(value) {
      value?.let(androidIntent::setData)
    }

  /** Copy over all [extras] to this [NodeAwareIntentScope] */
  @IntentManipulationDsl
  operator fun plusAssign(extras: Bundle) {
    androidIntent.putExtras(extras)
  }

  /** Copy over all extras from [other] to this [NodeAwareIntentScope] */
  @IntentManipulationDsl
  operator fun plusAssign(other: NodeAwareIntentScope) {
    androidIntent.putExtras(other.androidIntent)
  }

  @IntentManipulationDsl operator fun contains(key: String): Boolean = androidIntent.hasExtra(key)

  // getters

  @IntentManipulationDsl
  fun <T : Any> read(serializer: NodeAwareIntentSerializer<T>): IntentExtraDelegate<T> =
    RequiredIntentExtraDelegate(with(serializer) { read().let(::Present) })

  @IntentManipulationDsl
  fun string(name: String): OptionalIntentExtraDelegate<String> =
    OptionalIntentExtraDelegate(IntentExtra(name, androidIntent.getStringExtra(name)))

  @IntentManipulationDsl
  fun int(name: String): OptionalIntentExtraDelegate<Int> =
    OptionalIntentExtraDelegate(
      IntentExtra(name, name.takeIf(::contains)?.let { androidIntent.getIntExtra(it, 0) })
    )

  @IntentManipulationDsl
  fun boolean(name: String): OptionalIntentExtraDelegate<Boolean> =
    OptionalIntentExtraDelegate(
      IntentExtra(name, name.takeIf(::contains)?.let { androidIntent.getBooleanExtra(it, false) })
    )

  @IntentManipulationDsl
  fun bundle(name: String): OptionalIntentExtraDelegate<Bundle> =
    OptionalIntentExtraDelegate(IntentExtra(name, androidIntent.getBundleExtra(name)))

  @PublishedApi
  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  @IntentManipulationDsl
  internal fun <T : Any> parcelable(
    name: String,
    kClass: KClass<T>,
  ): OptionalIntentExtraDelegate<T> =
    OptionalIntentExtraDelegate(
      IntentExtra(name, kClass, androidIntent.getParcelableExtra(name, kClass.java))
    )

  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  @IntentManipulationDsl
  inline fun <reified T : Any> parcelable(name: String): OptionalIntentExtraDelegate<T> =
    parcelable(name, T::class)

  @PublishedApi
  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  @IntentManipulationDsl
  internal fun <T : Any> parcelableArray(
    name: String,
    kClass: KClass<T>,
    kClassArray: KClass<Array<T>>,
  ): OptionalIntentExtraDelegate<Array<T>> =
    OptionalIntentExtraDelegate(
      IntentExtra(name, kClassArray, androidIntent.getParcelableArrayExtra(name, kClass.java))
    )

  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  @IntentManipulationDsl
  inline fun <reified T : Any> parcelableArray(
    name: String
  ): OptionalIntentExtraDelegate<Array<T>> = parcelableArray(name, T::class, Array<T>::class)

  // setters

  /** Extracts a given value logging error on failure */
  @PublishedApi
  @IntentManipulationDsl
  internal fun <T : Any> (() -> T).extract(key: String, kClass: KClass<T>): Result<T> =
    runCatching(::invoke).onFailure {
      errors.add("Argument value for intent extra [$key: ${kClass.simpleName}] is missing")
    }

  private inline fun <reified T : Any> (() -> T).extract(key: String): Result<T> =
    extract(key, T::class)

  /** Extracts a given nullable value ensuring successful [Result] always contains non-null value */
  @PublishedApi
  @IntentManipulationDsl
  internal fun <T : Any> (() -> T?).extractOptional(): Result<T> =
    runCatching(::invoke).mapCatching(::requireNotNull)

  @JvmName("setSerializer")
  @IntentManipulationDsl
  inline operator fun <reified T : Any> set(
    serializer: NodeAwareIntentSerializer<T>,
    noinline value: () -> T,
  ) {
    value.extract(serializer::class.simpleName ?: "NESTED", T::class).onSuccess {
      with(serializer) { write(it) }
    }
  }

  @JvmName("setSerializerOrNull")
  @IntentManipulationDsl
  inline operator fun <reified T : Any> set(
    serializer: NodeAwareIntentSerializer<T>,
    noinline value: () -> T?,
  ) {
    value.extractOptional().onSuccess { with(serializer) { write(it) } }
  }

  @JvmName("setString")
  @IntentManipulationDsl
  operator fun set(key: String, value: () -> String) {
    value.extract(key).onSuccess { androidIntent.putExtra(key, it) }
  }

  @JvmName("setStringOrNull")
  @IntentManipulationDsl
  operator fun set(key: String, value: () -> String?) {
    value.extractOptional().onSuccess { androidIntent.putExtra(key, it) }
  }

  @JvmName("setInt")
  @IntentManipulationDsl
  operator fun set(key: String, value: () -> Int) {
    value.extract(key).onSuccess { androidIntent.putExtra(key, it) }
  }

  @JvmName("setIntOrNull")
  @IntentManipulationDsl
  operator fun set(key: String, value: () -> Int?) {
    value.extractOptional().onSuccess { androidIntent.putExtra(key, it) }
  }

  @JvmName("setBoolean")
  @IntentManipulationDsl
  operator fun set(key: String, value: () -> Boolean) {
    value.extract(key).onSuccess { androidIntent.putExtra(key, it) }
  }

  @JvmName("setBooleanOrNull")
  @IntentManipulationDsl
  operator fun set(key: String, value: () -> Boolean?) {
    value.extractOptional().onSuccess { androidIntent.putExtra(key, it) }
  }

  @JvmName("setBundle")
  @IntentManipulationDsl
  operator fun set(key: String, value: () -> Bundle) {
    value.extract(key).onSuccess { androidIntent.putExtra(key, it) }
  }

  @JvmName("setBundleOrNull")
  @IntentManipulationDsl
  operator fun set(key: String, value: () -> Bundle?) {
    value.extractOptional().onSuccess { androidIntent.putExtra(key, it) }
  }

  @JvmName("setParcelable")
  @IntentManipulationDsl
  operator fun set(key: String, value: () -> Parcelable) {
    value.extract(key).onSuccess { androidIntent.putExtra(key, it) }
  }

  @JvmName("setParcelableOrNull")
  @IntentManipulationDsl
  operator fun set(key: String, value: () -> Parcelable?) {
    value.extractOptional().onSuccess { androidIntent.putExtra(key, it) }
  }

  @JvmName("setParcelableArray")
  @IntentManipulationDsl
  operator fun set(key: String, value: () -> Array<out Parcelable>) {
    value.extract(key).onSuccess { androidIntent.putExtra(key, it) }
  }

  @JvmName("setParcelableArrayOrNull")
  @IntentManipulationDsl
  operator fun set(key: String, value: () -> Array<out Parcelable>?) {
    value.extractOptional().onSuccess { androidIntent.putExtra(key, it) }
  }

  // endregion
}
