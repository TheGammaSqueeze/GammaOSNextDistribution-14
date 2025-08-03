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

package com.android.internal.net.ipsec.test.ike;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockito.Mockito.mock;

import com.android.internal.net.ipsec.test.ike.net.IkeConnectionController;

import org.junit.Before;
import org.junit.Test;

public final class IkeSocketConfigTest {
    private static final int DUMMY_DSCP = 8;

    private IkeConnectionController mMockConnectionController;

    @Before
    public void setup() throws Exception {
        mMockConnectionController = mock(IkeConnectionController.class);
    }

    private IkeSocketConfig buildTestConfig() {
        return new IkeSocketConfig(mMockConnectionController, DUMMY_DSCP);
    }

    @Test
    public void testBuild() {
        final IkeSocketConfig config = buildTestConfig();

        assertEquals(mMockConnectionController, config.getConnectionController());
        assertEquals(DUMMY_DSCP, config.getDscp());
    }

    @Test
    public void testEquals() {
        final IkeSocketConfig config = buildTestConfig();
        final IkeSocketConfig otherConfig = buildTestConfig();

        assertEquals(config, otherConfig);
        assertNotSame(config, otherConfig);
    }

    @Test
    public void testNotEqualsIfConnectionControllerIsDifferent() {
        assertNotEquals(
                buildTestConfig(),
                new IkeSocketConfig(mock(IkeConnectionController.class), DUMMY_DSCP));
    }

    @Test
    public void testNotEqualsIfDscpIsDifferent() {
        final int dscp = 48;
        assertNotEquals(buildTestConfig(), new IkeSocketConfig(mMockConnectionController, dscp));
    }
}
