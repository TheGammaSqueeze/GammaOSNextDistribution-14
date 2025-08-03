/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.car.carlaunchercommon.shortcuts

import android.content.res.Resources
import com.android.car.carlaunchercommon.R
import com.android.car.ui.shortcutspopup.CarUiShortcutsPopup

/**
 * {@link CarUiShortcutsPopup.ShortcutItem} to pin or unpin an app to the dock.
 * @param isItemPinned if the app is pinned to the dock
 * @param pinItemClickDelegate {@link Runnable} to pin the app to the dock
 * @param unpinItemClickDelegate {@link Runnable} to unpin the app to the dock
 */
class PinShortcutItem(
    private val resources: Resources,
    private val isItemPinned: Boolean,
    private val pinItemClickDelegate: Runnable,
    private val unpinItemClickDelegate: Runnable
) : CarUiShortcutsPopup.ShortcutItem {

    override fun data(): CarUiShortcutsPopup.ItemData {
        return if (isItemPinned) {
            CarUiShortcutsPopup.ItemData(
                R.drawable.ic_dock_unpin, // leftDrawable
                resources.getString(R.string.dock_unpin_shortcut_label) // shortcutName
            )
        } else {
            CarUiShortcutsPopup.ItemData(
                R.drawable.ic_dock_pin, // leftDrawable
                resources.getString(R.string.dock_pin_shortcut_label) // shortcutName
            )
        }
    }

    override fun onClick(): Boolean {
        // todo(b/314835197): fix pinning/opening media apps
        if (isItemPinned) {
            unpinItemClickDelegate.run()
        } else {
            pinItemClickDelegate.run()
        }
        return true
    }

    override fun isEnabled(): Boolean {
        return true
    }
}
