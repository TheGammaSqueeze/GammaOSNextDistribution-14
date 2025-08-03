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

/** Workflow Task Configuration For Workflows in Spectatio Config JSON Config */
public class WorkflowTaskConfig {
    // TEXT for supported task types - COMMAND, PRESS, LONG_PRESS, etc.
    @SerializedName("TEXT")
    private String mText;

    // UI Element for supported task types - CLICK, LONG_CLICK, SCROLL_AND_FIND, etc.
    @SerializedName("UI_ELEMENT")
    private UiElement mUiElement;

    public WorkflowTaskConfig(String text, UiElement uiElement) {
        mText = text;
        mUiElement = uiElement;
    }

    public String getText() {
        return mText;
    }

    public UiElement getUiElement() {
        return mUiElement;
    }
}
