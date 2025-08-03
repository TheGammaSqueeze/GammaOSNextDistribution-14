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

package com.android.car.docklib

import android.app.ActivityOptions
import android.car.Car
import android.car.content.pm.CarPackageManager
import android.car.media.CarMediaManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import com.android.car.docklib.events.DockEventsReceiver
import com.android.car.docklib.events.DockPackageChangeReceiver
import com.android.car.docklib.media.MediaUtils
import com.android.car.docklib.task.DockTaskStackChangeListener
import com.android.car.docklib.view.DockAdapter
import com.android.car.docklib.view.DockView
import com.android.launcher3.icons.IconFactory
import com.android.systemui.shared.system.TaskStackChangeListeners
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * Create a controller for DockView. It initializes the view with default and persisted icons. Upon
 * initializing, it will listen to broadcast events, and update the view.
 *
 * @param dockView the inflated dock view
 * @param userContext the foreground user context, since the view may be hosted on system context
 */
class DockViewController(
        dockView: DockView,
        private val userContext: Context = dockView.context,
) : DockInterface {
    private companion object {
        private const val TAG = "DockViewController"
        private val DEBUG = Build.isDebuggable()
    }

    private val numItems = dockView.context.resources.getInteger(R.integer.config_numDockApps)
    private val car: Car
    private val dockViewWeakReference: WeakReference<DockView>
    private val dockViewModel: DockViewModel
    private val dockEventsReceiver: DockEventsReceiver
    private val dockPackageChangeReceiver: DockPackageChangeReceiver
    private val taskStackChangeListeners: TaskStackChangeListeners
    private val dockTaskStackChangeListener: DockTaskStackChangeListener
    private val launcherApps = userContext.getSystemService<LauncherApps>()

    init {
        if (DEBUG) Log.d(TAG, "Init DockViewController for user ${userContext.userId}")
        val adapter = DockAdapter(this, userContext)
        dockView.setAdapter(adapter)
        dockViewWeakReference = WeakReference(dockView)

        val launcherActivities = launcherApps
                ?.getActivityList(null, userContext.user)
                ?.map { it.componentName }
                ?.toMutableSet() ?: mutableSetOf()

        dockViewModel = DockViewModel(
                maxItemsInDock = numItems,
                context = userContext,
                packageManager = userContext.packageManager,
                launcherActivities = launcherActivities,
                defaultPinnedItems = dockView.resources
                        .getStringArray(R.array.config_defaultDockApps)
                        .mapNotNull(ComponentName::unflattenFromString),
                excludedComponents = dockView.resources
                        .getStringArray(R.array.config_componentsExcludedFromDock)
                        .mapNotNull(ComponentName::unflattenFromString).toHashSet(),
                excludedPackages = dockView.resources
                        .getStringArray(R.array.config_packagesExcludedFromDock).toHashSet(),
                iconFactory = IconFactory.obtain(dockView.context)
        ) { updatedApps ->
            dockViewWeakReference.get()?.getAdapter()?.submitList(updatedApps)
                    ?: throw NullPointerException("the View referenced does not exist")
        }
        car = Car.createCar(
                userContext,
                null, // handler
                Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT
        ) { car, ready ->
            run {
                if (ready) {
                    car.getCarManager(CarPackageManager::class.java)?.let { carPM ->
                        dockViewModel.setCarPackageManager(carPM)
                    }
                    car.getCarManager(CarMediaManager::class.java)?.let { carMM ->
                        adapter.setCarMediaManager(carMM)
                    }
                }
            }
        }
        dockEventsReceiver = DockEventsReceiver.registerDockReceiver(userContext, this)
        dockPackageChangeReceiver = DockPackageChangeReceiver.registerReceiver(userContext, this)
        dockTaskStackChangeListener =
                DockTaskStackChangeListener(userContext.userId, this)
        taskStackChangeListeners = TaskStackChangeListeners.getInstance()
        taskStackChangeListeners.registerTaskStackListener(dockTaskStackChangeListener)
    }

    /** Method to stop the dock. Call this upon View being destroyed. */
    fun destroy() {
        if (DEBUG) Log.d(TAG, "Destroy called")
        car.disconnect()
        userContext.unregisterReceiver(dockEventsReceiver)
        userContext.unregisterReceiver(dockPackageChangeReceiver)
        taskStackChangeListeners.unregisterTaskStackListener(dockTaskStackChangeListener)
        dockViewModel.destroy()
    }

    override fun appPinned(componentName: ComponentName) = dockViewModel.pinItem(componentName)

    override fun appPinned(componentName: ComponentName, index: Int) =
            dockViewModel.pinItem(componentName, index)

    override fun appPinned(id: UUID) = dockViewModel.pinItem(id)

    override fun appUnpinned(componentName: ComponentName) {
        // TODO: Not yet implemented
    }

    override fun appUnpinned(id: UUID) = dockViewModel.removeItem(id)

    override fun appLaunched(componentName: ComponentName) =
            dockViewModel.addDynamicItem(componentName)

    override fun launchApp(componentName: ComponentName, isMediaApp: Boolean) {
        val intent = if (isMediaApp) {
            MediaUtils.createLaunchIntent(componentName)
        } else {
            Intent(Intent.ACTION_MAIN)
                .setComponent(componentName)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val options = ActivityOptions.makeBasic()
        options.setLaunchDisplayId(userContext.display.displayId)
        // todo(b/312718542): hidden api(context.startActivityAsUser) usage
        userContext.startActivityAsUser(intent, options.toBundle(), userContext.user)
    }

    override fun getIconColorWithScrim(componentName: ComponentName) =
            dockViewModel.getIconColorWithScrim(componentName)

    override fun packageRemoved(packageName: String) = dockViewModel.removeItems(packageName)

    override fun packageAdded(packageName: String) {
        dockViewModel.addMediaComponents(packageName)
        dockViewModel.addLauncherComponents(
            launcherApps?.getActivityList(packageName, userContext.user)
                ?.map { it.componentName } ?: listOf()
        )
    }

    override fun getMediaServiceComponents(): Set<ComponentName> =
        dockViewModel.getMediaServiceComponents()
}
