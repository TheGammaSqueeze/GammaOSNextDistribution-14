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

package com.android.car.obd2.test;

/** Test constants for Obd2FrameGenerator tests */
public final class Obd2FrameGeneratorTestConstants {

    /* package private */
    static final String[] EXPECTED_INIT_COMMANDS =
            new String[]{
                    "ATD\r", "ATZ\r", "AT E0\r", "AT L0\r", "AT S0\r", "AT H0\r", "AT SP 0\r"
            };

    /* package private */
    static final String OBD2_PROMPT = ">";

    /* package private */
    static final String[] EXPECTED_INIT_RESPONSES =
            new String[]{
                    OBD2_PROMPT,
                    OBD2_PROMPT,
                    OBD2_PROMPT,
                    OBD2_PROMPT,
                    OBD2_PROMPT,
                    OBD2_PROMPT,
                    OBD2_PROMPT
            };

    /* package private */
    static final String[] EXPECTED_DISCOVERY_COMMANDS =
            new String[]{"0100\r", "0120\r", "0140\r", "0160\r"};

    /* package private */
    static final String[] EXPECTED_DISCOVERY_RESPONSES =
            new String[]{"00 00 00 18 00 00", OBD2_PROMPT, OBD2_PROMPT, OBD2_PROMPT, OBD2_PROMPT};

    private Obd2FrameGeneratorTestConstants() {
    }
}
