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

package com.android.adservices.service.signals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.content.pm.PackageManager;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.service.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public class SignalsMaintenanceTasksWorkerTest {

    @Rule public final MockitoRule rule = MockitoJUnit.rule();
    @Mock private ProtectedSignalsDao mProtectedSignalsDaoMock;
    @Mock private EnrollmentDao mEnrollmentDaoMock;
    @Mock private EncoderLogicHandler mEncoderLogicHandlerMock;
    @Mock private EncodedPayloadDao mEncodedPayloadDaoMock;
    @Mock private Flags mFlagsMock;
    @Mock private Clock mClockMock;
    @Mock private PackageManager mPackageManagerMock;

    SignalsMaintenanceTasksWorker mSignalsMaintenanceTasksWorker;
    Instant mNow;
    Instant mExpirationTime;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Before
    public void setup() {
        mSignalsMaintenanceTasksWorker =
                new SignalsMaintenanceTasksWorker(
                        mFlagsMock,
                        mProtectedSignalsDaoMock,
                        mEncoderLogicHandlerMock,
                        mEncodedPayloadDaoMock,
                        mEnrollmentDaoMock,
                        mClockMock,
                        mPackageManagerMock);
        when(mClockMock.instant()).thenReturn(CommonFixture.FIXED_NOW);
        mNow = CommonFixture.FIXED_NOW;
        mExpirationTime = CommonFixture.FIXED_NOW.minusSeconds(ProtectedSignal.EXPIRATION_SECONDS);
    }

    @Test
    public void testClearInvalidSignalsEnrollmentEnabled() throws Exception {
        when(mFlagsMock.getDisableFledgeEnrollmentCheck()).thenReturn(false);

        mSignalsMaintenanceTasksWorker.clearInvalidSignals(mExpirationTime, mNow);

        verify(mProtectedSignalsDaoMock)
                .deleteExpiredSignalsAndUpdateSignalsUpdateMetadata(mExpirationTime, mNow);
        verify(mFlagsMock).getDisableFledgeEnrollmentCheck();
        verify(mProtectedSignalsDaoMock).deleteDisallowedBuyerSignals(any());
        verify(mProtectedSignalsDaoMock)
                .deleteAllDisallowedPackageSignalsAndUpdateSignalUpdateMetadata(
                        any(), any(), eq(mNow));
    }

    @Test
    public void testClearInvalidSignalsEnrollmentDisabled() throws Exception {
        when(mFlagsMock.getDisableFledgeEnrollmentCheck()).thenReturn(true);

        mSignalsMaintenanceTasksWorker.clearInvalidSignals(mExpirationTime, mNow);

        verify(mProtectedSignalsDaoMock)
                .deleteExpiredSignalsAndUpdateSignalsUpdateMetadata(mExpirationTime, mNow);
        verify(mFlagsMock).getDisableFledgeEnrollmentCheck();
    }

    @Test
    public void testClearInvalidEncoderLogic() {
        when(mFlagsMock.getDisableFledgeEnrollmentCheck()).thenReturn(false);
        AdTechIdentifier buyer1 = AdTechIdentifier.fromString("buyer1");
        AdTechIdentifier buyer2 = AdTechIdentifier.fromString("buyer2");
        AdTechIdentifier buyer3 = AdTechIdentifier.fromString("buyer3");

        when(mEncoderLogicHandlerMock.getBuyersWithEncoders())
                .thenReturn(List.of(buyer1, buyer2, buyer3));

        // Marking buyer2 stale
        when(mEncoderLogicHandlerMock.getBuyersWithStaleEncoders(mExpirationTime))
                .thenReturn(List.of(buyer2));

        // Excluding buyer3 from enrollment
        when(mEnrollmentDaoMock.getAllFledgeEnrolledAdTechs()).thenReturn(Set.of(buyer1, buyer2));

        mSignalsMaintenanceTasksWorker.clearInvalidEncoders(mExpirationTime);
        verify(mEncoderLogicHandlerMock).getBuyersWithStaleEncoders(mExpirationTime);
        verify(mEncoderLogicHandlerMock).deleteEncodersForBuyers(Set.of(buyer2, buyer3));
    }

    @Test
    public void testClearInvalidEncoderLogicEnrollmentDisabled() {
        when(mFlagsMock.getDisableFledgeEnrollmentCheck()).thenReturn(true);
        AdTechIdentifier buyer1 = AdTechIdentifier.fromString("buyer1");

        // Marking buyer2 stale
        when(mEncoderLogicHandlerMock.getBuyersWithStaleEncoders(mExpirationTime))
                .thenReturn(List.of(buyer1));

        mSignalsMaintenanceTasksWorker.clearInvalidEncoders(mExpirationTime);
        verifyZeroInteractions(mEnrollmentDaoMock);
        verify(mEncoderLogicHandlerMock).getBuyersWithStaleEncoders(mExpirationTime);
        verify(mEncoderLogicHandlerMock).deleteEncodersForBuyers(Set.of(buyer1));
    }

    @Test
    public void testClearInvalidEncodedPayloads() {
        when(mFlagsMock.getDisableFledgeEnrollmentCheck()).thenReturn(false);
        AdTechIdentifier buyer1 = AdTechIdentifier.fromString("buyer1");
        AdTechIdentifier buyer2 = AdTechIdentifier.fromString("buyer2");

        when(mEncodedPayloadDaoMock.getAllBuyersWithEncodedPayloads())
                .thenReturn(List.of(buyer1, buyer2));

        // Excluding buyer 2 from enrollment
        when(mEnrollmentDaoMock.getAllFledgeEnrolledAdTechs()).thenReturn(Set.of(buyer1));

        mSignalsMaintenanceTasksWorker.clearInvalidEncodedPayloads(mExpirationTime);
        verify(mEncodedPayloadDaoMock).deleteEncodedPayload(buyer2);
        verify(mEncodedPayloadDaoMock).deleteEncodedPayloadsBeforeTime(mExpirationTime);
    }

    @Test
    public void testClearInvalidEncodedPayloadsEnrollmentDisabled() {
        when(mFlagsMock.getDisableFledgeEnrollmentCheck()).thenReturn(true);

        mSignalsMaintenanceTasksWorker.clearInvalidEncodedPayloads(mExpirationTime);
        verifyZeroInteractions(mEnrollmentDaoMock);
        verify(mEncodedPayloadDaoMock).deleteEncodedPayloadsBeforeTime(mExpirationTime);
    }
}
