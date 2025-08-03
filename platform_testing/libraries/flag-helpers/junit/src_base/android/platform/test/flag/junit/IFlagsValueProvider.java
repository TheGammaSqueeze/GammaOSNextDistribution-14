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

package android.platform.test.flag.junit;

import android.platform.test.flag.util.FlagReadException;

/** Common interface to get flag values. */
public interface IFlagsValueProvider {
    static boolean isBooleanValue(String value) {
        return "true".equals(value) || "false".equals(value);
    }
    /**
     * Sets up this provider before providing the flag values. Such as acquiring some permissions on
     * the device.
     */
    default void setUp() throws FlagReadException {}
    ;

    /** Gets the boolean value of a flag. */
    boolean getBoolean(String flag) throws FlagReadException;

    /** Tears down operations before calling the actual test run. */
    default void tearDownBeforeTest() {}
    ;
}
