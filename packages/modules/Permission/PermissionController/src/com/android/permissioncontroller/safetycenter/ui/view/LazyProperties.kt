package com.android.permissioncontroller.safetycenter.ui.view

import android.view.View

/** Returns a lazy property wrapping a view with a given ID. */
fun <T : View> View.lazyView(childViewId: Int): Lazy<T> = lazyView { requireViewById(childViewId) }

/** Returns a lazy property wrapping a view produced by the given function. */
fun <T> lazyView(viewProducer: () -> T): Lazy<T> =
    // Lazy by default uses synchronization to ensure a variable is only initialized once. This
    // is unnecessary and expensive for view properties, so we don't use synchronization here.
    lazy(LazyThreadSafetyMode.NONE) { viewProducer() }
