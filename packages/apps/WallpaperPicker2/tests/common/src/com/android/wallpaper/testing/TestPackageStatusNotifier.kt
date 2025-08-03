package com.android.wallpaper.testing

import com.android.wallpaper.module.PackageStatusNotifier

/** Test implementation of [PackageStatusNotifier] */
class TestPackageStatusNotifier : PackageStatusNotifier {
    override fun addListener(listener: PackageStatusNotifier.Listener?, action: String?) {
        // Do nothing intended
    }

    override fun removeListener(listener: PackageStatusNotifier.Listener?) {
        // Do nothing intended
    }
}
