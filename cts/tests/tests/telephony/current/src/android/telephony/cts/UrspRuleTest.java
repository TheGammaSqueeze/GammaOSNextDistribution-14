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

import static com.google.common.truth.Truth.assertThat;

import android.telephony.data.RouteSelectionDescriptor;
import android.telephony.data.TrafficDescriptor;
import android.telephony.data.UrspRule;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class UrspRuleTest {
    private static final int TEST_PRECEDENCE = 1;

    @Test
    public void testConstructorAndGetters() {
        List<TrafficDescriptor> tds = new ArrayList<>();
        List<RouteSelectionDescriptor> rsds = new ArrayList<>();
        UrspRule ur = new UrspRule(TEST_PRECEDENCE, tds, rsds);
        assertThat(ur.getPrecedence()).isEqualTo(TEST_PRECEDENCE);
        assertThat(ur.getTrafficDescriptors()).isNotEqualTo(null);
        assertThat(ur.getRouteSelectionDescriptor()).isNotEqualTo(null);
    }
}
