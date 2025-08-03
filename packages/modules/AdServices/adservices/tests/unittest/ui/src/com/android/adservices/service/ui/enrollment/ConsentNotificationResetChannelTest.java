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

package com.android.adservices.service.ui.enrollment;

import static com.android.adservices.service.ui.enrollment.impl.ConsentNotificationResetChannel.CONSENT_NOTIFICATION_RESET_TOKEN;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.content.SharedPreferences;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.ConsentNotificationJobService;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.ui.data.UxStatesManager;
import com.android.adservices.service.ui.enrollment.impl.ConsentNotificationResetChannel;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.UUID;

public class ConsentNotificationResetChannelTest {
    private final ConsentNotificationResetChannel mConsentNotificationResetChannel =
            new ConsentNotificationResetChannel();

    @Mock
    private UxStatesManager mUxStatesManager;
    @Mock
    private ConsentManager mConsentManager;
    @Mock
    private Flags mFlags;
    @Mock
    private SharedPreferences mSharedPreferences;
    @Mock
    private SharedPreferences.Editor mEditor;
    private MockitoSession mStaticMockSession;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(UxStatesManager.class)
                        .spyStatic(ConsentManager.class)
                        .spyStatic(ConsentNotificationJobService.class)
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        ExtendedMockito.doReturn(mFlags).when(FlagsFactory::getFlags);
    }

    @After
    public void teardown() throws IOException {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void isEligibleTest_emptyToken() {
        doReturn("").when(mFlags).getConsentNotificationResetToken();

        assertThat(
                mConsentNotificationResetChannel.isEligible(
                        null,
                        mConsentManager,
                        mUxStatesManager))
                .isFalse();
    }

    @Test
    public void isEligibleTest_newTokenWriteSucceeded() {
        doReturn(UUID.randomUUID().toString()).when(mFlags).getConsentNotificationResetToken();
        doReturn(mSharedPreferences).when(mUxStatesManager).getUxSharedPreferences();
        doReturn(mEditor).when(mSharedPreferences).edit();
        doReturn(mEditor).when(mEditor).putString(anyString(), anyString());
        doReturn(true).when(mEditor).commit();

        assertThat(
                mConsentNotificationResetChannel.isEligible(
                        null,
                        mConsentManager,
                        mUxStatesManager))
                .isTrue();
    }

    @Test
    public void isEligibleTest_newTokenWriteFailed() {
        doReturn(UUID.randomUUID().toString()).when(mFlags).getConsentNotificationResetToken();
        doReturn(mSharedPreferences).when(mUxStatesManager).getUxSharedPreferences();
        doReturn(mEditor).when(mSharedPreferences).edit();
        doReturn(mEditor).when(mEditor).putString(anyString(), anyString());
        doReturn(false).when(mEditor).commit();

        assertThat(
                mConsentNotificationResetChannel.isEligible(
                        null,
                        mConsentManager,
                        mUxStatesManager))
                .isFalse();
    }

    @Test
    public void isEligibleTest_sameToken() {
        String currentToken = UUID.randomUUID().toString();
        doReturn(currentToken).when(mFlags).getConsentNotificationResetToken();
        doReturn(mSharedPreferences).when(mUxStatesManager).getUxSharedPreferences();
        doReturn(mEditor).when(mSharedPreferences).edit();
        doReturn(mEditor).when(mEditor).putString(anyString(), anyString());
        doReturn(false).when(mEditor).commit();
        doReturn(currentToken).when(mSharedPreferences).getString(
                CONSENT_NOTIFICATION_RESET_TOKEN, /* defValue= */ currentToken);

        assertThat(
                mConsentNotificationResetChannel.isEligible(
                        null,
                        mConsentManager,
                        mUxStatesManager))
                .isFalse();
    }


    @Test
    public void enrollTest() {
        mConsentNotificationResetChannel.enroll(null, mConsentManager);

        if (SdkLevel.isAtLeastS()) {
            verify(mConsentManager).recordNotificationDisplayed(false);
            verify(mConsentManager).recordGaUxNotificationDisplayed(false);
        }
        if (SdkLevel.isAtLeastT()) {
            verify(mConsentManager).recordPasNotificationDisplayed(false);
        }

        verify(mConsentManager).setU18NotificationDisplayed(false);
        verify(mConsentManager).setU18Account(false);
    }
}
