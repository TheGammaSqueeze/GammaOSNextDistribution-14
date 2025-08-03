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

package android.telephony.cts;

import static android.telephony.data.RouteSelectionDescriptor.ROUTE_SSC_MODE_1;
import static android.telephony.data.RouteSelectionDescriptor.SESSION_TYPE_IPV4;

import static com.google.common.truth.Truth.assertThat;

import android.telephony.data.NetworkSliceInfo;
import android.telephony.data.RouteSelectionDescriptor;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RouteSelectionDescriptorTest {
    private static final int TEST_PRECEDENCE = 1;
    private static final int TEST_SESSION_TYPE = SESSION_TYPE_IPV4;
    private static final int TEST_SSC_MODE = ROUTE_SSC_MODE_1;

    @Test
    public void testConstructorAndGetters() {
        List<NetworkSliceInfo> si = new ArrayList<>();
        List<String> dnn = new ArrayList<>();
        RouteSelectionDescriptor rsd = new RouteSelectionDescriptor(
                TEST_PRECEDENCE, TEST_SESSION_TYPE, TEST_SSC_MODE, si, dnn);
        assertThat(rsd.getPrecedence()).isEqualTo(TEST_PRECEDENCE);
        assertThat(rsd.getSessionType()).isEqualTo(TEST_SESSION_TYPE);
        assertThat(rsd.getSscMode()).isEqualTo(TEST_SSC_MODE);
        assertThat(rsd.getSliceInfo()).isNotEqualTo(null);
        assertThat(rsd.getDataNetworkName()).isNotEqualTo(null);
    }
}
