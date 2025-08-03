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

import java.util.ArrayList;
import java.util.List;

/**
 * Data class that stores HID  battery test data.
  */
public class HidBatteryTestData {
    // Name of the test
    public String name;

    // HID reports that are used as input to /dev/uhid
    public List<String> reports = new ArrayList<String>();

    // Expected battery capacities.
    // Some input device drivers change battery capacity interpretations so we have to add
    // alternative capacity levels for different version of drivers.
    public float[] capacities;

    // Expected battery status
    public int status;
}
