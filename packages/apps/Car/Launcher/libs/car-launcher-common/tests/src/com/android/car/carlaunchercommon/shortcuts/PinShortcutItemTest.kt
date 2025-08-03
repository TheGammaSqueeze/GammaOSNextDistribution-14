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
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class PinShortcutItemTest {
    private val resourcesMock = mock<Resources> {}
    private val pinItemClickDelegateMock = mock<Runnable> {}
    private val unpinItemClickDelegateMock = mock<Runnable> {}

    @Test
    fun onClick_pinnedItem_runUnpinDelegate() {
        val pinShortcutItem = PinShortcutItem(
            resourcesMock,
            isItemPinned = true,
            pinItemClickDelegateMock,
            unpinItemClickDelegateMock
        )

        pinShortcutItem.onClick()

        verify(unpinItemClickDelegateMock).run()
    }

    @Test
    fun onClick_unpinnedItem_runPinDelegate() {
        val pinShortcutItem = PinShortcutItem(
            resourcesMock,
            isItemPinned = false,
            pinItemClickDelegateMock,
            unpinItemClickDelegateMock
        )

        pinShortcutItem.onClick()

        verify(pinItemClickDelegateMock).run()
    }
}
