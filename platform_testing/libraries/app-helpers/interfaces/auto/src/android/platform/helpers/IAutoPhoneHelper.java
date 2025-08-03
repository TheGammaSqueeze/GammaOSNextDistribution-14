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

/**
 * Interface Helper class for manipulating a mobile phone device. Unlike other helpers in this
 * directory, this helper is meant to run on mobile phone devices, rather than auto devices
 */
public interface IAutoPhoneHelper extends IAppHelper {

    /**
     * Assumes mobile device has dialer pad open.
     *
     * <p>Press the call button on screen
     */
    void pressCallButton();

    /**
     * Assumes mobile device has dialer app open.
     *
     * <p>Press the dial pad icon
     */
    void pressDialpadIcon();

    /**
     * Assumes mobile device is on home screen, and phone icon is present.
     *
     * <p>Press the phone icon
     */
    void pressPhoneIcon();

    /**
     * Assumes dial pad is open.
     *
     * <p>Enter a number into the dial pad
     */
    void enterNumberOnDialpad(String numberToEnter);

    /**
     * Assumes dial pad is open. Dials a number one digit at a time using the number buttons on the
     * dial pad
     */
    void dialNumberOnDialpad(String phoneNumber);
}
