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
import android.platform.helpers.IAutoPrivacySettingsHelper;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

/** Snippet class for exposing Privacy Settings App APIs. */
public class PrivacySettingsSnippet implements Snippet {

    private final HelperAccessor<IAutoPrivacySettingsHelper> mPrivacySettingsHelper;

    public PrivacySettingsSnippet() {

        mPrivacySettingsHelper = new HelperAccessor<>(IAutoPrivacySettingsHelper.class);
    }

    @Rpc(description = "Microphone Chip.")
    public boolean isMicChipPresentOnStatusBar() {
        return mPrivacySettingsHelper.get().isMicChipPresentOnStatusBar();
    }

    @Rpc(description = "Start Android Auto pop-up displayed")
    public boolean isStartAndroidAutoPopUpPresent() {
        return mPrivacySettingsHelper.get().isStartAndroidAutoPopUpPresent();
    }

    @Rpc(description = "Skip Start Android Auto pop-up")
    public void skipStartAndroidAutoPopUp() {
        mPrivacySettingsHelper.get().skipStartAndroidAutoPopUp();
    }

    @Rpc(description = "Assistant improvement pop-up displayed")
    public boolean isAssistantImprovementPopUpPresent() {
        return mPrivacySettingsHelper.get().isAssistantImprovementPopUpPresent();
    }

    @Rpc(description = "Skip Improvement calling and texting pop-up")
    public void skipImprovementCallingAndTextingPopUp() {
        mPrivacySettingsHelper.get().skipImprovementCallingAndTextingPopUp();
    }

    @Override
    public void shutdown() {}
}
