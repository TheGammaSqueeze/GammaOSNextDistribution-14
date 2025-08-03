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

package com.android.cts.appcloningtestapp;

// Container for information about each image
public class Image {

    // This tells the exact location of the image along with its display name
    // e.g. /storage/emulated/<user_id>/Pictures/<imageDisplayName>
    private final String mData;

    // This shows the display name of the image in the shared storage
    private final String mDisplayName;

    public Image(String data, String displayName) {
        mData = data;
        mDisplayName = displayName;
    }

    public String getData() {
        return mData;
    }

    public String getDisplayName() {
        return mDisplayName;
    }
}
