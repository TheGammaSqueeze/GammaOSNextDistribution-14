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

package com.google.android.mobly.snippet.bundled;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoGeneralUIHelper;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

/** Snippet class for general access to UI elements on Android devices */
public class UISnippet implements Snippet {

    private final HelperAccessor<IAutoGeneralUIHelper> mUIHelper;

    public UISnippet() {
        mUIHelper = new HelperAccessor<IAutoGeneralUIHelper>(IAutoGeneralUIHelper.class);
    }

    /**
     * @param text - The text field on the element to click.
     */
    @Rpc(description = "Attempt to click a UI Element that contains a particular text field.")
    public void clickUIElementWithText(String text) {
        mUIHelper.get().clickElementWithText(text);
    }

    /**
     * @param text - The text field on the element to search for.
     * @return - True if a UIElement with a text field holding the given text is on screen. False
     *     otherwise.
     */
    @Rpc(description = "Attempt to find a UI Element that contains a particular text field.")
    public boolean hasUIElementWithText(String text) {
        return mUIHelper.get().hasElementWithText(text);
    }



}
