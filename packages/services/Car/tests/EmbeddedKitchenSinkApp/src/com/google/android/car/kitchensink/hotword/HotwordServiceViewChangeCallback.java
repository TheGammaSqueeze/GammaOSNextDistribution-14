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

interface HotwordServiceViewChangeCallback {

    /**
     * Call to start service
     * @param absoluteAdapterPosition position of the view holder for the service
     */
    void onStartService(int absoluteAdapterPosition);

    /**
     * Call to stop service
     * @param absoluteAdapterPosition position of the view holder for the service
     */
    void onsStopService(int absoluteAdapterPosition);

    /**
     * Call to start recording
     * @param absoluteAdapterPosition position of the view holder for the service
     */
    void onStartRecording(int absoluteAdapterPosition);

    /**
     * Call to stop recording
     * @param absoluteAdapterPosition position of the view holder for the service
     */
    void onStopRecording(int absoluteAdapterPosition);
}
