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
import android.platform.helpers.IAutoCarSmsMessengerHelper;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

/** Snippet class for exposing Messenger App APIs. */
public class CarSmsMessengerSnippet implements Snippet {

    private final HelperAccessor<IAutoCarSmsMessengerHelper> mCarSmsMessengerHelper;

    public CarSmsMessengerSnippet() {

        mCarSmsMessengerHelper = new HelperAccessor<>(IAutoCarSmsMessengerHelper.class);
    }

    @Rpc(description = "Open SMS Application.")
    public void openSmsApp() {
        mCarSmsMessengerHelper.get().open();
    }

    @Rpc(description = "Bluetooth SMS Error")
    public boolean isSmsBluetoothErrorDisplayed() {
        return mCarSmsMessengerHelper.get().isSmsBluetoothErrorDisplayed();
    }

    @Rpc(description = "Checks Unread Sms Badge")
    public boolean isUnreadSmsDisplayed() {
        return mCarSmsMessengerHelper.get().isUnreadSmsDisplayed();
    }

    /** for checking the sms preview */
    @Rpc(description = "Checks Sms Preview Text")
    public boolean isSmsPreviewTextDisplayed(String text) {
        return mCarSmsMessengerHelper.get().isSmsPreviewDisplayed(text);
    }

    @Rpc(description = "Checks Sms Preview TimeStamp")
    public boolean isSmsTimeStampDisplayed() {
        return mCarSmsMessengerHelper.get().isSmsTimeStampDisplayed();
    }

    @Rpc(description = "Checks Sms No Messages")
    public boolean isNoMessagesDisplayed() {
        return mCarSmsMessengerHelper.get().isNoMessagesDisplayed();
    }

    /** for tapping the sms received in order to read it aloud */
    @Rpc(description = "Clicks on tap to read aloud text to tead out the message")
    public void tapToReadAloud() {
        mCarSmsMessengerHelper.get().tapToReadAloud();
    }

    /** checks whether the assistant transctption plate is displayed upon tapping received sms */
    @Rpc(description = "Checks if assistant sms plate is displayed")
    public boolean isAssistantSMSTranscriptionPlateDisplayed() {
        return mCarSmsMessengerHelper.get().isAssistantSMSTranscriptionPlateDisplayed();
    }

    @Override
    public void shutdown() {}
}
