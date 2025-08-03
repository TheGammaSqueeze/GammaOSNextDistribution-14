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
 *
 */

package com.android.customization.picker.clock.ui.binder

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.android.themepicker.R

class CarouselAccessibilityDelegate(
    private val context: Context,
    private val scrollForwardCallback: () -> Unit,
    private val scrollBackwardCallback: () -> Unit
) : View.AccessibilityDelegate() {

    var contentDescriptionOfSelectedClock = ""

    private val ACTION_SCROLL_BACKWARD = R.id.action_scroll_backward
    private val ACTION_SCROLL_FORWARD = R.id.action_scroll_forward

    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(host, info)
        info.isScrollable = true
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(
                ACTION_SCROLL_FORWARD,
                context.getString(R.string.scroll_forward_and_select)
            )
        )
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(
                ACTION_SCROLL_BACKWARD,
                context.getString(R.string.scroll_backward_and_select)
            )
        )
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS)
        // We need to specifically set the content description since for some reason the talkback
        // service does not go to children of the clock carousel in the view hierarchy
        info.contentDescription = contentDescriptionOfSelectedClock
    }

    override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
        when (action) {
            ACTION_SCROLL_BACKWARD -> {
                scrollBackwardCallback.invoke()
                return true
            }
            ACTION_SCROLL_FORWARD -> {
                scrollForwardCallback.invoke()
                return true
            }
        }
        return super.performAccessibilityAction(host, action, args)
    }
}
