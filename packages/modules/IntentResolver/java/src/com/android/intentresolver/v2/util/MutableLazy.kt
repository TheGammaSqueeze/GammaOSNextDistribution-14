package com.android.intentresolver.v2.util

import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KProperty

/** A lazy delegate that can be changed to a new lazy or null at any time. */
class MutableLazy<T>(initializer: () -> T?) : Lazy<T?> {

    override val value: T?
        get() = lazy.get()?.value

    private var lazy: AtomicReference<Lazy<T?>?> = AtomicReference(lazy(initializer))

    override fun isInitialized(): Boolean = lazy.get()?.isInitialized() != false

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T? =
        lazy.get()?.getValue(thisRef, property)

    /** Replace the existing lazy logic with the [newLazy] */
    fun setLazy(newLazy: Lazy<T?>?) {
        lazy.set(newLazy)
    }

    /** Replace the existing lazy logic with a [Lazy] created from the [newInitializer]. */
    fun setLazy(newInitializer: () -> T?) {
        lazy.set(lazy(newInitializer))
    }

    /** Set the lazy logic to null. */
    fun clear() {
        lazy.set(null)
    }
}

/** Constructs a [MutableLazy] using the given [initializer] */
fun <T> mutableLazy(initializer: () -> T?) = MutableLazy(initializer)
