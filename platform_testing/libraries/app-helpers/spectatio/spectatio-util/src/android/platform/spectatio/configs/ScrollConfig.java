/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.platform.spectatio.configs;

import com.google.gson.annotations.SerializedName;

/** Scroll Config for Workflow Task in Spectatio Config JSON Config */
public class ScrollConfig {
    // If task needs scrolling, provide Scroll Action e.g. USE_BUTTON or USE_GESTURE
    @SerializedName("SCROLL_ACTION")
    private String mScrollAction;

    // If task needs scrolling and Scroll Action = USE_GESTURE, provide Scroll Direction
    // e.g. Horizontal or VERTICAL
    @SerializedName("SCROLL_DIRECTION")
    private String mScrollDirection;

    // If task needs scrolling and Scroll Action = USE_BUTTON, provide Scroll Forward Button
    @SerializedName("SCROLL_FORWARD")
    private UiElement mScrollForward;

    // If task needs scrolling and Scroll Action = USE_BUTTON, provide Scroll Backward Button
    @SerializedName("SCROLL_BACKWARD")
    private UiElement mScrollBackward;

    // If task needs scrolling and Scroll Action = USE_GESTURE, provide Scroll Element
    @SerializedName("SCROLL_ELEMENT")
    private UiElement mScrollElement;

    // If task needs scrolling and Scroll Action = USE_GESTURE, provide Scroll Margin
    @SerializedName("SCROLL_MARGIN")
    private String mScrollMargin = "10";

    // If task needs scrolling and Scroll Action = USE_GESTURE, provide Scroll wait time
    @SerializedName("SCROLL_WAIT_TIME")
    private String mScrollWaitTime = "1";

    public ScrollConfig(
            String scrollAction,
            String scrollDirection,
            UiElement scrollForward,
            UiElement scrollBackward,
            UiElement scrollElement) {
        mScrollAction = scrollAction;
        mScrollDirection = scrollDirection;
        mScrollForward = scrollForward;
        mScrollBackward = scrollBackward;
        mScrollElement = scrollElement;
    }

    public ScrollConfig(String scrollAction, UiElement scrollForward, UiElement scrollBackward) {
        mScrollAction = scrollAction;
        mScrollForward = scrollForward;
        mScrollBackward = scrollBackward;
    }

    public ScrollConfig(
            String scrollAction,
            String scrollDirection,
            UiElement scrollElement,
            String scrollMargin,
            String scrollWaitTime) {
        mScrollAction = scrollAction;
        mScrollDirection = scrollDirection;
        mScrollElement = scrollElement;

        if (scrollMargin != null) {
            mScrollMargin = scrollMargin;
        }
        if (scrollWaitTime != null) {
            mScrollWaitTime = scrollWaitTime;
        }
    }

    public String getScrollAction() {
        return mScrollAction;
    }

    public String getScrollDirection() {
        return mScrollDirection;
    }

    public UiElement getScrollForwardButton() {
        return mScrollForward;
    }

    public UiElement getScrollBackwardButton() {
        return mScrollBackward;
    }

    public UiElement getScrollElement() {
        return mScrollElement;
    }

    /** Getter Function for ScrollMargin */
    public String getScrollMargin() {
        return mScrollMargin;
    }

    /** Getter Function for ScrollWaitTime */
    public String getScrollWaitTime() {
        return mScrollWaitTime;
    }
}
