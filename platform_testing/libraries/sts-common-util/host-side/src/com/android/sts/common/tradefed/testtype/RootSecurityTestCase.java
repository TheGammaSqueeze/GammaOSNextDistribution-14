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

package com.android.sts.common.tradefed.testtype;

import static org.junit.Assume.assumeTrue;

import com.android.tradefed.device.DeviceNotAvailableException;

import org.junit.Before;

/**
 * Class of tests that need root on device to run.
 *
 * <p>To optimize performance, all tests that need root to run should be grouped into the same
 * module.
 */
public class RootSecurityTestCase extends SecurityTestCase {

    /** Enable root after SecurityTestCase's setUp */
    @Before
    public void setUpRoot() throws DeviceNotAvailableException {
        assumeTrue("Could not enable adb root on device.", getDevice().enableAdbRoot());
    }
}
