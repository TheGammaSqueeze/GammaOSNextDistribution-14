/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetAdServicesFlag;

import android.provider.DeviceConfig;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;

@SpyStatic(DeviceConfig.class)
public final class PhFlagsHelpersTest extends AdServicesExtendedMockitoTestCase {

    private static final String NAME = "Flag, James Flag";

    @Test
    public void testGetBooleanDeviceConfigFlag() {
        mockGetAdServicesFlag(NAME, true);

        expect.that(PhFlags.getDeviceConfigFlag(NAME, /* defaultValue= */ false)).isTrue();
    }

    @Test
    public void testGetStringDeviceConfigFlag() {
        mockGetAdServicesFlag(NAME, "of the rose");

        expect.that(PhFlags.getDeviceConfigFlag(NAME, /* defaultValue= */ "D'OH"))
                .isEqualTo("of the rose");
    }

    @Test
    public void testGetIntDeviceConfigFlag() {
        mockGetAdServicesFlag(NAME, 42);

        expect.that(PhFlags.getDeviceConfigFlag(NAME, /* defaultValue= */ 108)).isEqualTo(42);
    }

    @Test
    public void testGetLongDeviceConfigFlag() {
        mockGetAdServicesFlag(NAME, 42L);

        expect.that(PhFlags.getDeviceConfigFlag(NAME, /* defaultValue= */ 108L)).isEqualTo(42L);
    }

    @Test
    public void testGetFloatDeviceConfigFlag() {
        mockGetAdServicesFlag(NAME, 42f);

        expect.that(PhFlags.getDeviceConfigFlag(NAME, /* defaultValue= */ 108f)).isEqualTo(42f);
    }
}
