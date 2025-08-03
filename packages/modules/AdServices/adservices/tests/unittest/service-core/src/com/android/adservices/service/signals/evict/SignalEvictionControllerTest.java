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

package com.android.adservices.service.signals.evict;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.CommonFixture;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.List;

public class SignalEvictionControllerTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();
    @Mock private SignalEvictor mSignalEvictorMock1;
    @Mock private SignalEvictor mSignalEvictorMock2;
    @Mock private SignalEvictor mSignalEvictorMock3;
    private SignalEvictionController mController;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Before
    public void setup() {
        mController =
                new SignalEvictionController(
                        List.of(mSignalEvictorMock1, mSignalEvictorMock2, mSignalEvictorMock3),
                        100,
                        200);
    }

    @Test
    public void evict_3Evictor_firstReturnFalse() {
        when(mSignalEvictorMock1.evict(any(), any(), any(), anyInt(), anyInt())).thenReturn(false);

        mController.evict(CommonFixture.VALID_BUYER_1, List.of(), new UpdateOutput());

        verify(mSignalEvictorMock1).evict(any(), any(), any(), anyInt(), anyInt());
        verifyZeroInteractions(mSignalEvictorMock2, mSignalEvictorMock3);
    }

    @Test
    public void evict_3Evictor_firstReturnTrueAndSecondReturnFalse() {
        when(mSignalEvictorMock1.evict(any(), any(), any(), anyInt(), anyInt())).thenReturn(true);
        when(mSignalEvictorMock2.evict(any(), any(), any(), anyInt(), anyInt())).thenReturn(false);

        mController.evict(CommonFixture.VALID_BUYER_1, List.of(), new UpdateOutput());

        verify(mSignalEvictorMock1).evict(any(), any(), any(), anyInt(), anyInt());
        verify(mSignalEvictorMock2).evict(any(), any(), any(), anyInt(), anyInt());
        verifyZeroInteractions(mSignalEvictorMock3);
    }
}
