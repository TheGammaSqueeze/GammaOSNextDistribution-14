/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.phone;

import static org.junit.Assert.fail;

import android.os.Build;
import android.telephony.LocationAccessPolicy;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LocationAccessPolicyBuilderTest {
    @Test
    public void testBuilderMissingMinCoarse() {
        try {
            new LocationAccessPolicy.LocationPermissionQuery.Builder()
                    .setMethod("test")
                    .setCallingPackage("com.android.test")
                    .setCallingFeatureId(null)
                    .setCallingPid(0)
                    .setCallingUid(0)
                    .setMinSdkVersionForFine(Build.VERSION_CODES.N)
                    .build();
            fail("Should have failed without specifying min version for coarse");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testBuilderMissingMinFine() {
        try {
            new LocationAccessPolicy.LocationPermissionQuery.Builder()
                    .setMethod("test")
                    .setCallingPackage("com.android.test")
                    .setCallingFeatureId(null)
                    .setCallingPid(0)
                    .setCallingUid(0)
                    .setMinSdkVersionForCoarse(Build.VERSION_CODES.N)
                    .build();
            fail("Should have failed without specifying min version for fine");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testBuilderMissingMinEnforcement() {
        try {
            new LocationAccessPolicy.LocationPermissionQuery.Builder()
                    .setMethod("test")
                    .setCallingPackage("com.android.test")
                    .setCallingFeatureId(null)
                    .setCallingPid(0)
                    .setCallingUid(0)
                    .setMinSdkVersionForFine(Build.VERSION_CODES.N)
                    .setMinSdkVersionForCoarse(Build.VERSION_CODES.N)
                    .build();
            fail("Should have failed without specifying min version for any enforcement");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
