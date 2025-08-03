/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.telephony.qns;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class QnsComponentsTest extends QnsTest {

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        super.setUp();
    }

    @Test
    public void testCreateQnsComponents() {
        int slotId = 0;
        QnsComponents qnsComponents = new QnsComponents(sMockContext);

        assertNull(qnsComponents.getQnsTelephonyListener(slotId));
        assertNull(qnsComponents.getQnsImsManager(slotId));
        assertNull(qnsComponents.getCellularNetworkStatusTracker(slotId));
        assertNull(qnsComponents.getCellularQualityMonitor(slotId));
        assertNull(qnsComponents.getQnsProvisioningListener(slotId));
        assertNull(qnsComponents.getQnsEventDispatcher(slotId));
        assertNull(qnsComponents.getQnsCarrierConfigManager(slotId));
        assertNull(qnsComponents.getQnsCallStatusTracker(slotId));
        assertNull(qnsComponents.getWifiBackhaulMonitor(slotId));
        assertNull(qnsComponents.getWifiQualityMonitor());
        assertNull(qnsComponents.getIwlanNetworkStatusTracker());
        assertNull(qnsComponents.getQnsTimer());

        qnsComponents.createQnsComponents(slotId);

        assertNotNull(qnsComponents.getQnsTelephonyListener(slotId));
        assertNotNull(qnsComponents.getQnsImsManager(slotId));
        assertNotNull(qnsComponents.getCellularNetworkStatusTracker(slotId));
        assertNotNull(qnsComponents.getCellularQualityMonitor(slotId));
        assertNotNull(qnsComponents.getQnsProvisioningListener(slotId));
        assertNotNull(qnsComponents.getQnsEventDispatcher(slotId));
        assertNotNull(qnsComponents.getQnsCarrierConfigManager(slotId));
        assertNotNull(qnsComponents.getQnsCallStatusTracker(slotId));
        assertNotNull(qnsComponents.getWifiBackhaulMonitor(slotId));
        assertNotNull(qnsComponents.getWifiQualityMonitor());
        assertNotNull(qnsComponents.getIwlanNetworkStatusTracker());
        assertNotNull(qnsComponents.getQnsTimer());
    }


    @Test
    public void testCloseQnsComponents() {
        int slotId = 0;
        QnsComponents qnsComponents = new QnsComponents(
                sMockContext,
                mMockCellNetStatusTracker,
                mMockCellularQm,
                mMockIwlanNetworkStatusTracker,
                mMockQnsImsManager,
                mMockQnsConfigManager,
                mMockQnsEventDispatcher,
                mMockQnsProvisioningListener,
                mMockQnsTelephonyListener,
                mMockQnsCallStatusTracker,
                mMockQnsTimer,
                mMockWifiBm,
                mMockWifiQm,
                mMockQnsMetrics,
                slotId);

        assertNotNull(qnsComponents.getQnsTelephonyListener(slotId));
        assertNotNull(qnsComponents.getQnsImsManager(slotId));
        assertNotNull(qnsComponents.getCellularNetworkStatusTracker(slotId));
        assertNotNull(qnsComponents.getCellularQualityMonitor(slotId));
        assertNotNull(qnsComponents.getQnsProvisioningListener(slotId));
        assertNotNull(qnsComponents.getQnsEventDispatcher(slotId));
        assertNotNull(qnsComponents.getQnsCarrierConfigManager(slotId));
        assertNotNull(qnsComponents.getQnsCallStatusTracker(slotId));
        assertNotNull(qnsComponents.getWifiBackhaulMonitor(slotId));
        assertNotNull(qnsComponents.getWifiQualityMonitor());
        assertNotNull(qnsComponents.getIwlanNetworkStatusTracker());
        assertNotNull(qnsComponents.getQnsTimer());
        assertNotNull(qnsComponents.getQnsMetrics());

        qnsComponents.closeComponents(slotId);

        assertNull(qnsComponents.getQnsTelephonyListener(slotId));
        assertNull(qnsComponents.getQnsImsManager(slotId));
        assertNull(qnsComponents.getCellularNetworkStatusTracker(slotId));
        assertNull(qnsComponents.getCellularQualityMonitor(slotId));
        assertNull(qnsComponents.getQnsProvisioningListener(slotId));
        assertNull(qnsComponents.getQnsEventDispatcher(slotId));
        assertNull(qnsComponents.getQnsCarrierConfigManager(slotId));
        assertNull(qnsComponents.getQnsCallStatusTracker(slotId));
        assertNull(qnsComponents.getWifiBackhaulMonitor(slotId));
        assertNull(qnsComponents.getWifiQualityMonitor());
        assertNull(qnsComponents.getIwlanNetworkStatusTracker());
        assertNull(qnsComponents.getQnsTimer());
        assertNull(qnsComponents.getQnsMetrics());

        verify(mMockQnsTelephonyListener).close();
        verify(mMockQnsImsManager).close();
        verify(mMockCellNetStatusTracker).close();
        verify(mMockCellularQm).close();
        verify(mMockQnsProvisioningListener).close();
        verify(mMockQnsEventDispatcher).close();
        verify(mMockQnsConfigManager).close();
        verify(mMockQnsCallStatusTracker).close();
        verify(mMockWifiBm).close();
        verify(mMockWifiQm).close();
        verify(mMockIwlanNetworkStatusTracker).close();
        verify(mMockQnsTimer).close();
        verify(mMockQnsMetrics).close();
    }
}
