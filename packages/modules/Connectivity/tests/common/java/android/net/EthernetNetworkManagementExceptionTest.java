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

package android.net;

import static com.android.testutils.DevSdkIgnoreRuleKt.SC_V2;
import static com.android.testutils.ParcelUtils.assertParcelingIsLossless;

import static org.junit.Assert.assertEquals;

import androidx.test.filters.SmallTest;

import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@DevSdkIgnoreRule.IgnoreUpTo(SC_V2) // TODO: Use to Build.VERSION_CODES.SC_V2 when available
@RunWith(DevSdkIgnoreRunner.class)
@SmallTest
public class EthernetNetworkManagementExceptionTest {
    private static final String ERROR_MESSAGE = "Test error message";

    @Test
    public void testEthernetNetworkManagementExceptionParcelable() {
        final EthernetNetworkManagementException e =
                new EthernetNetworkManagementException(ERROR_MESSAGE);

        assertParcelingIsLossless(e);
    }

    @Test
    public void testEthernetNetworkManagementExceptionHasExpectedErrorMessage() {
        final EthernetNetworkManagementException e =
                new EthernetNetworkManagementException(ERROR_MESSAGE);

        assertEquals(ERROR_MESSAGE, e.getMessage());
    }
}
