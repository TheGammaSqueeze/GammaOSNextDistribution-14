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

package com.android.adservices.service.signals.updateprocessors;

import static com.android.adservices.service.signals.SignalsFixture.DEV_CONTEXT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.signals.DBEncoderEndpoint;
import com.android.adservices.data.signals.EncoderEndpointsDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.service.devapi.DevContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Instant;

public class UpdateEncoderEventHandlerTest {

    @Rule public MockitoRule mRule = MockitoJUnit.rule();
    @Mock private EncoderEndpointsDao mEncoderEndpointsDaoMock;

    @Mock private EncoderLogicHandler mEncoderLogicHandlerMock;

    @Captor private ArgumentCaptor<DBEncoderEndpoint> mEndpointCaptor;

    private UpdateEncoderEventHandler mHandler;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Before
    public void setup() {
        mHandler =
                new UpdateEncoderEventHandler(mEncoderEndpointsDaoMock, mEncoderLogicHandlerMock);
    }

    @Test
    public void testNullEventUpdate() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        assertThrows(
                NullPointerException.class,
                () -> {
                    mHandler.handle(buyer, null, DEV_CONTEXT);
                });
        verifyZeroInteractions(mEncoderEndpointsDaoMock, mEncoderLogicHandlerMock);
    }

    @Test
    public void testUpdateEventRegister() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        Uri uri = CommonFixture.getUri(buyer, "/encoder");
        when(mEncoderEndpointsDaoMock.getEndpoint(buyer)).thenReturn(null);
        mHandler.handle(
                buyer,
                UpdateEncoderEvent.builder()
                        .setUpdateType(UpdateEncoderEvent.UpdateType.REGISTER)
                        .setEncoderEndpointUri(uri)
                        .build(),
                DevContext.createForDevOptionsDisabled());
        verify(mEncoderEndpointsDaoMock).registerEndpoint(mEndpointCaptor.capture());
        verify(mEncoderLogicHandlerMock)
                .downloadAndUpdate(buyer, DevContext.createForDevOptionsDisabled());
        assertEquals(uri, mEndpointCaptor.getValue().getDownloadUri());
        assertEquals(buyer, mEndpointCaptor.getValue().getBuyer());
    }

    @Test
    public void testUpdateEventRegisterSkipsNonFirstUpdate() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        Uri uri = CommonFixture.getUri(buyer, "/encoder");
        when(mEncoderEndpointsDaoMock.getEndpoint(buyer))
                .thenReturn(
                        DBEncoderEndpoint.builder()
                                .setDownloadUri(uri)
                                .setCreationTime(Instant.now())
                                .setBuyer(buyer)
                                .build());
        mHandler.handle(
                buyer,
                UpdateEncoderEvent.builder()
                        .setUpdateType(UpdateEncoderEvent.UpdateType.REGISTER)
                        .setEncoderEndpointUri(uri)
                        .build(),
                DEV_CONTEXT);
        verify(mEncoderEndpointsDaoMock).registerEndpoint(mEndpointCaptor.capture());
        assertEquals(uri, mEndpointCaptor.getValue().getDownloadUri());
        assertEquals(buyer, mEndpointCaptor.getValue().getBuyer());
        verifyZeroInteractions(mEncoderLogicHandlerMock);
    }

    @Test
    public void testUpdateEventRegisterNullUri() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mHandler.handle(
                            buyer,
                            UpdateEncoderEvent.builder()
                                    .setUpdateType(UpdateEncoderEvent.UpdateType.REGISTER)
                                    .build(),
                            DEV_CONTEXT);
                });
        verifyZeroInteractions(mEncoderEndpointsDaoMock, mEncoderLogicHandlerMock);
    }
}
