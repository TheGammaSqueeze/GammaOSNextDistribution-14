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

import android.content.ComponentName
import com.android.car.docklib.data.DockItemId
import java.util.UUID

interface DockInterface {
    /** called when an app is pinned to the Dock */
    fun appPinned(componentName: ComponentName)

    /** called when an app is pinned to the Dock at a particular position */
    fun appPinned(componentName: ComponentName, index: Int)

    /** called when an app already in the dock is pinned */
    fun appPinned(@DockItemId id: UUID)

    /** called when an app already in the dock is unpinned */
    fun appUnpinned(componentName: ComponentName)

    /** called when an app already in the dock is unpinned */
    fun appUnpinned(@DockItemId id: UUID)

    /** called when an app is launched */
    fun appLaunched(componentName: ComponentName)

    /**
     * called when an app is uninstalled/removed from the system or is inaccessible in the dock.
     * @param packageName packageName of removed package
     */
    fun packageRemoved(packageName: String)

    /**
     * called when an app is installed in the system or is enabled in the dock.
     * @param packageName packageName of removed package
     */
    fun packageAdded(packageName: String)

    /** called to launch an app */
    fun launchApp(componentName: ComponentName, isMediaApp: Boolean)

    /** @return the dominant color to be used with the icon corresponding to [componentName] */
    fun getIconColorWithScrim(componentName: ComponentName): Int

    /** get the set of all media service components */
    fun getMediaServiceComponents(): Set<ComponentName>
}
