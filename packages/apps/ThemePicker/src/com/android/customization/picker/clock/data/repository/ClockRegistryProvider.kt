/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.customization.picker.clock.data.repository

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.view.LayoutInflater
import com.android.systemui.plugins.Plugin
import com.android.systemui.plugins.PluginManager
import com.android.systemui.shared.clocks.ClockRegistry
import com.android.systemui.shared.clocks.DefaultClockProvider
import com.android.systemui.shared.plugins.PluginActionManager
import com.android.systemui.shared.plugins.PluginEnabler
import com.android.systemui.shared.plugins.PluginInstance
import com.android.systemui.shared.plugins.PluginManagerImpl
import com.android.systemui.shared.plugins.PluginPrefs
import com.android.systemui.shared.system.UncaughtExceptionPreHandlerManager_Factory
import com.android.wallpaper.module.InjectorProvider
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Provide the [ClockRegistry] singleton. Note that we need to make sure that the [PluginManager]
 * needs to be connected before [ClockRegistry] is ready to use.
 */
class ClockRegistryProvider(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
    private val backgroundDispatcher: CoroutineDispatcher,
) {
    private val clockRegistry: ClockRegistry by lazy {
        ClockRegistry(
            context,
            createPluginManager(context),
            coroutineScope,
            mainDispatcher,
            backgroundDispatcher,
            isEnabled = true,
            handleAllUsers = false,
            DefaultClockProvider(context, LayoutInflater.from(context), context.resources),
            keepAllLoaded = true,
            subTag = "Picker",
            isTransitClockEnabled =
                InjectorProvider.getInjector().getFlags().isTransitClockEnabled(context)
        )
    }

    init {
        // Listeners in ClockRegistry get cleaned up when app ended
        clockRegistry.registerListeners()
    }

    fun get() = clockRegistry

    private fun createPluginManager(context: Context): PluginManager {
        val privilegedPlugins = listOf<String>()
        val isDebugDevice = true

        val instanceFactory =
            PluginInstance.Factory(
                this::class.java.classLoader,
                PluginInstance.InstanceFactory<Plugin>(),
                PluginInstance.VersionCheckerImpl(),
                privilegedPlugins,
                isDebugDevice,
            )

        /*
         * let SystemUI handle plugin, in this class assume plugins are enabled
         */
        val pluginEnabler =
            object : PluginEnabler {
                override fun setEnabled(component: ComponentName) = Unit

                override fun setDisabled(
                    component: ComponentName,
                    @PluginEnabler.DisableReason reason: Int
                ) = Unit

                override fun isEnabled(component: ComponentName): Boolean {
                    return true
                }

                @PluginEnabler.DisableReason
                override fun getDisableReason(componentName: ComponentName): Int {
                    return PluginEnabler.ENABLED
                }
            }

        val pluginActionManager =
            PluginActionManager.Factory(
                context,
                context.packageManager,
                context.mainExecutor,
                Executors.newSingleThreadExecutor(),
                context.getSystemService(NotificationManager::class.java),
                pluginEnabler,
                privilegedPlugins,
                instanceFactory,
            )
        return PluginManagerImpl(
            context,
            pluginActionManager,
            isDebugDevice,
            UncaughtExceptionPreHandlerManager_Factory.create().get(),
            pluginEnabler,
            PluginPrefs(context),
            listOf(),
        )
    }
}
