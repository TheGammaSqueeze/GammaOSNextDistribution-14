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

package com.google.android.car.kitchensink.hotword;

interface HotwordServiceUpdatedCallback {

    /**
     * Called when service has started
     * @param message update message
     */
    void onServiceStarted(String message);

    /**
     * Called when service has stopped
     * @param message update message
     */
    void onServiceStopped(String message);

    /**
     * Called when service recording has started
     * @param message update message
     */
    void onRecordingStarted(String message);

    /**
     * Call when servicing recording has stopped
     * @param message update message
     */
    void onRecordingStopped(String message);
}
