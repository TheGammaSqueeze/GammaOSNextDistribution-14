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

package android.platform.helpers;

/** Car Sms Helper Interface */
public interface IAutoCarSmsMessengerHelper extends IAppHelper, Scrollable {

    /**
     * Setup expectations: bluetooth off
     *
     * <p>This method is used checking if the error is displayed when bluetooth is off
     */
    boolean isSmsBluetoothErrorDisplayed();

    /**
     * Setup expectations: bluetooth on and phone is paired, SMS app is open
     *
     * <p>This method is used checking unread text badge is displayed
     */
    boolean isUnreadSmsDisplayed();

    /**
     * Setup expectations: bluetooth on and phone is paired, SMS app is open
     *
     * <p>This method is used checking if sms text is displaye
     */
    boolean isSmsPreviewDisplayed(String text);

    /**
     * Setup expectations: bluetooth on and phone is paired, SMS app is open
     *
     * <p>This method is used checking if sms timestamp is displayed
     */
    boolean isSmsTimeStampDisplayed();

    /**
     * Setup expectations: bluetooth on and phone is paired, SMS app is open
     *
     * <p>This method is used checking if sms timestamp is displayed
     */
    boolean isNoMessagesDisplayed();

    /**
     * Setup expectations: bluetooth on and phone is paired, SMS app is open,HU is in driving mode
     *
     * <p>This method is used to tap on sms text received to play it aloud
     */
    void tapToReadAloud();

    /**
     * Setup expectations: None
     *
     * <p>This method is used to verify microphone Transcription plate in status bar.
     */
    boolean isAssistantSMSTranscriptionPlateDisplayed();
}
