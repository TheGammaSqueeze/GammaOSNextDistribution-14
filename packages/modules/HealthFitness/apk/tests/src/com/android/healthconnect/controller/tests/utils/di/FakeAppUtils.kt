package com.android.healthconnect.controller.tests.utils.di

import android.content.Context
import com.android.healthconnect.controller.shared.app.AppUtils

class FakeAppUtils : AppUtils {

    private var defaultApp = ""

    fun setDefaultApp(packageName: String) {
        this.defaultApp = packageName
    }

    override fun isDefaultApp(context: Context, packageName: String): Boolean {
        return this.defaultApp == packageName
    }

    fun reset() {
        this.defaultApp = ""
    }
}
