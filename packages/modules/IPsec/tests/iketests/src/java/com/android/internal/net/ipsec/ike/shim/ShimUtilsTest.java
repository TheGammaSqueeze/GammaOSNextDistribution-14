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

package com.android.internal.net.ipsec.test.ike.shim;

import static org.junit.Assert.assertTrue;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Test;

public class ShimUtilsTest {
    @Test
    public void testGetInstance() {
        final ShimUtils shim = ShimUtils.getInstance();
        assertTrue(shim instanceof ShimUtils);
        if (SdkLevel.isAtLeastV()) {
            assertTrue(shim instanceof ShimUtilsMinV);
        } else if (SdkLevel.isAtLeastU()) {
            assertTrue(shim instanceof ShimUtilsU);
        } else if (SdkLevel.isAtLeastT()) {
            assertTrue(shim instanceof ShimUtilsT);
        } else {
            assertTrue(shim instanceof ShimUtilsRAndS);
        }
    }
}
