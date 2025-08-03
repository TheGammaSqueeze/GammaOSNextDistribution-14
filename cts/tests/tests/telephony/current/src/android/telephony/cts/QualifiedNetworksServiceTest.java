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

import static org.junit.Assert.assertEquals;

import android.net.NetworkCapabilities;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.AccessNetworkConstants;
import android.telephony.data.DataServiceCallback;
import android.telephony.data.QualifiedNetworksService;

import com.android.internal.telephony.flags.Flags;

import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class QualifiedNetworksServiceTest {
    private static final int SLOT_INDEX = 0;
    private static final int APN_TYPES = 0;
    private static final int EMERGENCY_PREFERRED_TRANPORT =
            AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
    private static final int REQUEST_TIMEOUT_MS = 500;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private class TestQns extends QualifiedNetworksService {
        TestQns() {
            super();
        }

        public TestNap onCreateNetworkAvailabilityProvider(int slotIndex) {
            return new TestNap(slotIndex);
        }

        public class TestNap extends QualifiedNetworksService.NetworkAvailabilityProvider {
            private int mPreferredTransportForEmergencyDataNetwork;

            TestNap(int slotIndex) {
                super(slotIndex);
            }

            public void close() {
                // Do nothing
            }

            @Override
            public void reportEmergencyDataNetworkPreferredTransportChanged(int transportType) {
                mPreferredTransportForEmergencyDataNetwork = transportType;
            }

            public int getPreferredTransportForEmergencyDataNetwork() {
                return mPreferredTransportForEmergencyDataNetwork;
            }


            @Override
            public void requestNetworkValidation(int networkCapability, Executor executor,
                                                 Consumer<Integer> resultCodeCallback) {
                resultCodeCallback.accept(DataServiceCallback.RESULT_ERROR_UNSUPPORTED);
            }
        }
    }

    @Test
    public void testConstructorsAndGetters() {
        QualifiedNetworksService qns = new TestQns();
        QualifiedNetworksService.NetworkAvailabilityProvider nap =
                qns.onCreateNetworkAvailabilityProvider(SLOT_INDEX);
        assertEquals(SLOT_INDEX, nap.getSlotIndex());
    }

    @Test
    public void testNetworkAvailabilityProvider() {
        QualifiedNetworksService qns = new TestQns();
        QualifiedNetworksService.NetworkAvailabilityProvider nap =
                qns.onCreateNetworkAvailabilityProvider(SLOT_INDEX);
        nap.updateQualifiedNetworkTypes(APN_TYPES, Collections.emptyList());
        nap.reportThrottleStatusChanged(Collections.emptyList());
    }

    @Test
    public void testReportEmergencyDataNetworkPreferredTransportChanged() {
        QualifiedNetworksService qns = new TestQns();
        QualifiedNetworksService.NetworkAvailabilityProvider nap =
                qns.onCreateNetworkAvailabilityProvider(SLOT_INDEX);
        nap.reportEmergencyDataNetworkPreferredTransportChanged(EMERGENCY_PREFERRED_TRANPORT);
        assertEquals(((TestQns.TestNap) nap).getPreferredTransportForEmergencyDataNetwork(),
                EMERGENCY_PREFERRED_TRANPORT);
    }


    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NETWORK_VALIDATION)
    public void testRequestNetworkValidation() throws InterruptedException {
        QualifiedNetworksService qns = new TestQns();
        QualifiedNetworksService.NetworkAvailabilityProvider nap =
                qns.onCreateNetworkAvailabilityProvider(SLOT_INDEX);

        LinkedBlockingQueue<Integer> resultCodeCallback = new LinkedBlockingQueue<>(1);
        nap.requestNetworkValidation(NetworkCapabilities.NET_CAPABILITY_IMS,
                Runnable::run, resultCodeCallback::offer);
        Integer result = resultCodeCallback.poll(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(result.intValue(), DataServiceCallback.RESULT_ERROR_UNSUPPORTED);
    }
}
