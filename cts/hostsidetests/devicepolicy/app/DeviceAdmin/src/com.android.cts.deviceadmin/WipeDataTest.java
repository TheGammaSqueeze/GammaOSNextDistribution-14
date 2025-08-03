/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.cts.deviceadmin;

import android.util.Log;

public final class WipeDataTest extends BaseDeviceAdminTest {

    private static final String TAG = WipeDataTest.class.getSimpleName();

    // Caution: this test will wipe the device's data if it fails
    public void testWipeDataThrowsSecurityException() {
        try {
            Log.i(TAG, "Calling wipeData() on " + dpm);
            dpm.wipeData(/* flags= */ 0);
            fail("wipeData didn't throw expected SecurityException. Managed to kick off factory"
                    + " reset process");
        } catch (SecurityException expected) {
            Log.v(TAG, "Got exception as expected: " + expected);
        }
    }
}
