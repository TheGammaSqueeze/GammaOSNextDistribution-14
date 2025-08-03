package com.android.tv.settings.testutils

import android.app.ActivityManager
import android.app.IActivityManager
import android.content.res.Configuration
import android.os.LocaleList
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowActivityManager
import java.lang.reflect.Proxy
import java.util.Locale


@Implements(ActivityManager::class)
@Suppress("ACCIDENTAL_OVERRIDE") // override doesn't work with JvmStatic
open class SettingsShadowActivityManager : ShadowActivityManager() {
    companion object {
        val configuration: Configuration = Configuration().apply {
            setLocales(LocaleList(Locale.US))
        }

        @JvmStatic
        protected fun getService(): IActivityManager {
            val clazz = IActivityManager::class.java
            return Proxy.newProxyInstance(clazz.classLoader, arrayOf<Class<*>>(clazz)) {
                _, method, _ ->
                    if (method.name == "getConfiguration") configuration else null
            } as IActivityManager
        }
    }
}
