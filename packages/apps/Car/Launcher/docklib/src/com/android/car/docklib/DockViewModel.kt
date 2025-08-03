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

import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.car.content.pm.CarPackageManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.Display
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.android.car.docklib.data.DockAppItem
import com.android.car.docklib.data.DockItemId
import com.android.car.docklib.media.MediaUtils
import com.android.car.docklib.task.TaskUtils
import com.android.launcher3.icons.BaseIconFactory
import com.android.launcher3.icons.ColorExtractor
import com.android.launcher3.icons.IconFactory
import java.util.Collections
import java.util.UUID

/**
 * This class contains a live list of dock app items. All changes to dock items will go through it
 * and will be observed by the view layer.
 */
open class DockViewModel(
        private val maxItemsInDock: Int,
        private val context: Context,
        private val packageManager: PackageManager,
        private var carPackageManager: CarPackageManager? = null,
        private val userId: Int = context.userId,
        private var launcherActivities: MutableSet<ComponentName>,
        defaultPinnedItems: List<ComponentName>,
        private val excludedComponents: Set<ComponentName>,
        private val excludedPackages: Set<String>,
        private val iconFactory: IconFactory = IconFactory.obtain(context),
        private val observer: Observer<List<DockAppItem>>,
) {

    private companion object {
        private const val TAG = "DockViewModel"
        private val DEBUG = Build.isDebuggable()
        private const val MAX_UNIQUE_ID_TRIES = 20
        private const val MAX_TASKS_TO_FETCH = 20
    }

    private val noSpotAvailableToPinToastMsg = context.getString(R.string.pin_failed_no_spots)
    private val colorExtractor = ColorExtractor()
    private val defaultIconColor = context.resources.getColor(
            R.color.icon_default_color,
            null // theme
    )
    private val currentItems = MutableLiveData<List<DockAppItem>>()
    private val mediaServiceComponents = MediaUtils.fetchMediaServiceComponents(packageManager)

    /*
     * Maintain a mapping of dock index to dock item, with the order of addition,
     * so it's easier to find least recently updated position.
     * The order goes from least recently updated item to most recently updated item.
     * The key in each mapping is the index/position of the item being shown in Dock.
     */
    @VisibleForTesting
    val internalItems: MutableMap<Int, DockAppItem> =
            Collections.synchronizedMap(LinkedHashMap<Int, DockAppItem>())

    init {
        defaultPinnedItems.forEachIndexed { index, component ->
            if (index < maxItemsInDock) {
                createDockItem(
                    component,
                    DockAppItem.Type.STATIC,
                    isMediaApp(component)
                )?.let { dockItem ->
                    internalItems[index] = dockItem
                }
            }
        }
        currentItems.value = createDockList()
        currentItems.observeForever(observer)
    }

    /** Pin an existing dock item with given [id]. It is assumed the item is not pinned/static. */
    fun pinItem(@DockItemId id: UUID) {
        if (DEBUG) Log.d(TAG, "Pin Item, id: $id")
        internalItems
                .filter { mapEntry -> mapEntry.value.id == id }
                .firstNotNullOfOrNull { it }
                ?.let { mapEntry ->
                    if (DEBUG) {
                        Log.d(TAG, "Pinning ${mapEntry.value.component} at ${mapEntry.key}")
                    }
                    internalItems[mapEntry.key] =
                            mapEntry.value.copy(type = DockAppItem.Type.STATIC)
                }
        // update list regardless to update the listeners
        currentItems.value = createDockList()
    }

    /**
     * Pin a new item that is not previously present in the dock. It is assumed the item is not
     * pinned/static.
     *
     * @param component [ComponentName] of the pinned item.
     * @param indexToPin the index to pin the item at. For null value, a suitable index is searched
     * to pin to. If no index is suitable the user is notified.
     */
    fun pinItem(component: ComponentName, indexToPin: Int? = null) {
        if (DEBUG) Log.d(TAG, "Pin Item, component: $component, indexToPin: $indexToPin")
        createDockItem(
            component,
            DockAppItem.Type.STATIC,
            isMediaApp(component)
        )?.let { dockItem ->
            if (indexToPin != null) {
                if (indexToPin in 0..<maxItemsInDock) {
                    if (DEBUG) Log.d(TAG, "Pinning $component at $indexToPin")
                    internalItems[indexToPin] = dockItem
                } else {
                    if (DEBUG) Log.d(TAG, "Invalid index provided")
                }
            } else {
                val index = findIndexToPin()
                if (index == null) {
                    if (DEBUG) Log.d(TAG, "No dynamic or empty spots available to pin")
                    // if no dynamic or empty spots available, notify the user
                    showToast(noSpotAvailableToPinToastMsg)
                    return@pinItem
                }
                if (DEBUG) Log.d(TAG, "Pinning $component at $index")
                internalItems[index] = dockItem
            }
        }
        // update list regardless to update the listeners
        currentItems.value = createDockList()
    }

    /** Removes item with the given [id] from the dock. */
    fun removeItem(id: UUID) {
        if (DEBUG) Log.d(TAG, "Unpin Item, id: $id")
        internalItems
                .filter { mapEntry -> mapEntry.value.id == id }
                .firstNotNullOfOrNull { it }
                ?.let { mapEntry ->
                    if (DEBUG) {
                        Log.d(TAG, "Unpinning ${mapEntry.value.component} at ${mapEntry.key}")
                    }
                    internalItems.remove(mapEntry.key)
                }
        // update list regardless to update the listeners
        currentItems.value = createDockList()
    }

    /** Removes all items of the given [packageName] from the dock. */
    fun removeItems(packageName: String) {
        internalItems.entries.removeAll { it.value.component.packageName == packageName }
        val areMediaComponentsRemoved =
            mediaServiceComponents.removeIf { it.packageName == packageName }
        if (areMediaComponentsRemoved && DEBUG) {
            Log.d(TAG, "Media components were removed for $packageName")
        }
        launcherActivities.removeAll { it.packageName == packageName }
        currentItems.value = createDockList()
    }

    /** Adds all media service components for the given [packageName]. */
    fun addMediaComponents(packageName: String) {
        val components = MediaUtils.fetchMediaServiceComponents(packageManager, packageName)
        if (DEBUG) Log.d(TAG, "Added media components: $components")
        mediaServiceComponents.addAll(components)
    }

    /** Adds all launcher components. */
    fun addLauncherComponents(components: List<ComponentName>) {
        launcherActivities.addAll(components)
    }

    fun getMediaServiceComponents(): Set<ComponentName> = mediaServiceComponents

    /**
     * Add a new app to the dock. If the app is already in the dock, the recency of the app is
     * refreshed. If not, and the dock has dynamic item(s) to update, then it will replace the least
     * recent dynamic item.
     */
    fun addDynamicItem(component: ComponentName) {
        if (DEBUG) Log.d(TAG, "Add dynamic item, component: $component")
        if (isItemExcluded(component)) {
            if (DEBUG) Log.d(TAG, "Dynamic item is excluded")
            return
        }
        if (isItemInDock(component, DockAppItem.Type.STATIC)) {
            if (DEBUG) Log.d(TAG, "Dynamic item is already present in the dock as static item")
            return
        }
        val indexToUpdate =
                indexOfItemWithPackageName(component.packageName)
                        ?: indexOfLeastRecentDynamicItemInDock()
        if (indexToUpdate == null || indexToUpdate >= maxItemsInDock) return

        createDockItem(
            component,
            DockAppItem.Type.DYNAMIC,
            isMediaApp(component)
        )?.let { newDockItem ->
            if (DEBUG) Log.d(TAG, "Updating $component at $indexToUpdate")
            internalItems.remove(indexToUpdate)
            internalItems[indexToUpdate] = newDockItem
            currentItems.value = createDockList()
        }
    }

    fun getIconColorWithScrim(componentName: ComponentName): Int {
        return DockAppItem.getIconColorWithScrim(getIconColor(componentName))
    }

    fun destroy() {
        currentItems.removeObserver(observer)
    }

    fun setCarPackageManager(carPackageManager: CarPackageManager) {
        this.carPackageManager = carPackageManager
        internalItems.forEach { mapEntry ->
            internalItems[mapEntry.key] = mapEntry.value.copy(
                    isDistractionOptimized = carPackageManager.isActivityDistractionOptimized(
                            mapEntry.value.component.packageName,
                            mapEntry.value.component.className
                    )
            )
        }
    }

    @VisibleForTesting
    fun createDockList(): List<DockAppItem> {
        if (DEBUG) Log.d(TAG, "createDockList called")
        // todo(b/312718542): hidden api(ActivityTaskManager.getTasks) usage
        val runningTaskList = getRunningTasks().filter { it.userId == userId }

        for (index in 0..<maxItemsInDock) {
            if (internalItems.contains(index)) continue

            var isItemFound = false
            for (component in runningTaskList.mapNotNull { TaskUtils.getComponentName(it) }) {
                if (!isItemExcluded(component) && !isItemInDock(component)) {
                    createDockItem(
                        component,
                        DockAppItem.Type.DYNAMIC,
                        isMediaApp(component)
                    )?.let { dockItem ->
                        if (DEBUG) {
                            Log.d(TAG, "Adding recent item(${dockItem.component}) at $index")
                        }
                        internalItems[index] = dockItem
                        isItemFound = true
                    }
                }
                if (isItemFound) break
            }

            if (isItemFound) continue

            for (component in launcherActivities.shuffled()) {
                if (!isItemExcluded(component) && !isItemInDock(component)) {
                    createDockItem(
                        componentName = component,
                        DockAppItem.Type.DYNAMIC,
                        isMediaApp(component)
                    )?.let { dockItem ->
                        if (DEBUG) {
                            Log.d(TAG, "Adding recommended item(${dockItem.component}) at $index")
                        }
                        internalItems[index] = dockItem
                        isItemFound = true
                    }
                }
                if (isItemFound) break
            }

            if (!isItemFound) {
                throw IllegalStateException("Cannot find enough apps to place in the dock")
            }
        }
        return convertMapToList(internalItems)
    }

    /** Use the mapping index->item to create the ordered list of Dock items */
    private fun convertMapToList(map: Map<Int, DockAppItem>): List<DockAppItem> =
            List(maxItemsInDock) { index -> map[index] }.filterNotNull()
    // TODO b/314409899: use a default DockItem when a position is empty

    private fun findIndexToPin(): Int? {
        var index: Int? = null
        for (i in 0..<maxItemsInDock) {
            if (!internalItems.contains(i)) {
                index = i
                break
            }
            if (internalItems[i]?.type == DockAppItem.Type.DYNAMIC) {
                index = i
                break
            }
        }
        return index
    }

    private fun indexOfLeastRecentDynamicItemInDock(): Int? {
        if (DEBUG) {
            Log.d(
                    TAG,
                    "internalItems.size = ${internalItems.size}, maxItemsInDock= $maxItemsInDock"
            )
        }
        if (internalItems.size < maxItemsInDock) return internalItems.size
        // since map is ordered from least recent to most recent, return first dynamic entry found
        internalItems.forEach { appItemEntry ->
            if (appItemEntry.value.type == DockAppItem.Type.DYNAMIC) return appItemEntry.key
        }
        // there is no dynamic item in dock to be replaced
        return null
    }

    private fun indexOfItemWithPackageName(packageName: String): Int? {
        internalItems.forEach { appItemEntry ->
            if (appItemEntry.value.component.packageName == packageName) {
                return appItemEntry.key
            }
        }
        return null
    }

    private fun isItemExcluded(component: ComponentName): Boolean =
            (excludedPackages.contains(component.packageName) ||
                    excludedComponents.contains(component))

    private fun isItemInDock(component: ComponentName, ofType: DockAppItem.Type? = null): Boolean {
        return internalItems.values
                .filter { (ofType == null) || (it.type == ofType) }
                .map { it.component.packageName }
                .contains(component.packageName)
    }

    /* Creates Dock item from a ComponentName. */
    private fun createDockItem(
            componentName: ComponentName,
            itemType: DockAppItem.Type,
            isMediaApp: Boolean,
    ): DockAppItem? {
        // TODO: Compare the component against LauncherApps to make sure the component
        // is launchable, similar to what app grid has

        val ai = getPackageItemInfo(componentName) ?: return null
        // todo(b/315210225): handle getting icon lazily
        val icon = ai.loadIcon(packageManager)
        val iconColor = getIconColor(icon)
        return DockAppItem(
                id = getUniqueDockItemId(),
                type = itemType,
                component = componentName,
                name = ai.loadLabel(packageManager).toString(),
                icon = icon,
                iconColor = iconColor,
                isDistractionOptimized =
                isMediaApp || (carPackageManager?.isActivityDistractionOptimized(
                    componentName.packageName,
                    componentName.className
                ) ?: false),
            isMediaApp = isMediaApp
        )
    }

    private fun getPackageItemInfo(componentName: ComponentName): PackageItemInfo? {
        try {
            val isMediaApp = isMediaApp(componentName)
            val pkgInfo = packageManager.getPackageInfo(
                componentName.packageName,
                PackageManager.PackageInfoFlags.of(
                    (if (isMediaApp) PackageManager.GET_SERVICES else PackageManager.GET_ACTIVITIES)
                        .toLong()
                )
            )
            return if (isMediaApp) {
                pkgInfo.services?.find { it.componentName == componentName }
            } else {
                pkgInfo.activities?.find { it.componentName == componentName }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            if (DEBUG) {
                // don't need to crash for this failure, log error instead
                Log.e(TAG, "Component $componentName not found", e)
            }
        }
        return null
    }

    private fun getIconColor(componentName: ComponentName): Int {
        val ai = getPackageItemInfo(componentName) ?: return defaultIconColor
        return getIconColor(ai.loadIcon(packageManager))
    }

    private fun getIconColor(icon: Drawable) = colorExtractor.findDominantColorByHue(
            iconFactory.createScaledBitmap(icon, BaseIconFactory.MODE_DEFAULT)
    )

    private fun getUniqueDockItemId(): @DockItemId UUID {
        val existingKeys = internalItems.values.map { it.id }.toSet()
        for (i in 0..MAX_UNIQUE_ID_TRIES) {
            val id = UUID.randomUUID()
            if (!existingKeys.contains(id)) return id
        }
        return UUID.randomUUID()
    }

    private fun isMediaApp(component: ComponentName) = mediaServiceComponents.contains(component)

    /** To be disabled for tests since [Toast] cannot be shown on that process */
    @VisibleForTesting
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /** To be overridden in tests to pass mock values for RunningTasks */
    @VisibleForTesting
    fun getRunningTasks(): List<ActivityManager.RunningTaskInfo> {
        return ActivityTaskManager.getInstance().getTasks(
                MAX_TASKS_TO_FETCH,
                false, // filterOnlyVisibleRecents
                false, // keepIntentExtra
                Display.DEFAULT_DISPLAY // displayId
        )
    }
}
