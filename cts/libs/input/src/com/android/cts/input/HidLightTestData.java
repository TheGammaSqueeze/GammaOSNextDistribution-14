/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.input;

import android.util.ArrayMap;

/**
 * Data class that stores HID light test data.
 */
public class HidLightTestData {
    // Light type
    public int lightType;

    // Light capabilities
    public int lightCapabilities;

    // Light color
    public int lightColor;

    // Light player ID
    public int lightPlayerId;

    // Light name
    public String lightName;

    // HID event type
    public Byte hidEventType;

    // HID report
    public String report;

    // Array of index and expected data in HID output packet to verify LED states.
    public ArrayMap<Integer, Integer> expectedHidData;

}
