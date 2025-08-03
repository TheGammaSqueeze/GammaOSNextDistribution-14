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

package com.android.adservices.service.measurement.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdServicesStatusUtils;
import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class KillSwitchAccessResolverTest {

    private static final String ERROR_MESSAGE = "Measurement API is disabled by kill-switch.";

    @Mock private Context mContext;

    private KillSwitchAccessResolver mResolver;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testIsAllowed_killSwitchOff_isAllowed() {
        mResolver = new KillSwitchAccessResolver(() -> /* kill switch off */ false);

        assertTrue(mResolver.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void testIsAllowed_killSwitchOn_isNotAllowed() {
        mResolver = new KillSwitchAccessResolver(() -> /* kill switch on */ true);

        assertFalse(mResolver.getAccessInfo(mContext).isAllowedAccess());
    }

    @Test
    public void testGetErrorMessage() {
        assertEquals(ERROR_MESSAGE, new KillSwitchAccessResolver(() -> false).getErrorMessage());
    }

    @Test
    public void testGetErrorStatusCode() {
        assertEquals(
                AdServicesStatusUtils.STATUS_KILLSWITCH_ENABLED,
                new KillSwitchAccessResolver(() -> false).getErrorStatusCode());
    }
}
