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

package com.android.adservices.service.measurement.registration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.WebUtil;
import com.android.adservices.service.measurement.AsyncRegistrationFixture;
import com.android.adservices.service.measurement.Source;

import org.junit.Test;

import java.util.UUID;

public class AsyncRegistrationTest {
    private static final Context sDefaultContext = ApplicationProvider.getApplicationContext();
    private static final Uri DEFAULT_REGISTRANT = Uri.parse("android-app://com.registrant");
    private static final Uri DEFAULT_VERIFIED_DESTINATION = Uri.parse("android-app://com.example");
    private static final Uri APP_TOP_ORIGIN =
            Uri.parse("android-app://" + sDefaultContext.getPackageName());
    private static final Uri WEB_TOP_ORIGIN = WebUtil.validUri("https://example.test");
    private static final Uri REGISTRATION_URI = WebUtil.validUri("https://foo.test/bar?ad=134");
    private static final Uri WEB_DESTINATION = WebUtil.validUri("https://web-destination.test");
    private static final Uri APP_DESTINATION = Uri.parse("android-app://com.app_destination");

    @Test
    public void shouldProcessRedirects() {
        assertTrue(createAsyncRegistrationForAppSource().shouldProcessRedirects());
        assertFalse(createAsyncRegistrationForAppSources().shouldProcessRedirects());
        assertTrue(createAsyncRegistrationForAppTrigger().shouldProcessRedirects());
        assertFalse(createAsyncRegistrationForWebSource().shouldProcessRedirects());
        assertFalse(createAsyncRegistrationForWebTrigger().shouldProcessRedirects());
    }

    @Test
    public void isSourceRequest() {
        assertTrue(createAsyncRegistrationForAppSource().isSourceRequest());
        assertTrue(createAsyncRegistrationForAppSources().isSourceRequest());
        assertFalse(createAsyncRegistrationForAppTrigger().isSourceRequest());
        assertTrue(createAsyncRegistrationForWebSource().isSourceRequest());
        assertFalse(createAsyncRegistrationForWebTrigger().isSourceRequest());
    }

    private static AsyncRegistration createAsyncRegistrationForAppSource() {
        return new AsyncRegistration.Builder()
                .setId(UUID.randomUUID().toString())
                .setRegistrationUri(REGISTRATION_URI)
                .setRegistrant(DEFAULT_REGISTRANT)
                .setTopOrigin(APP_TOP_ORIGIN)
                .setType(AsyncRegistration.RegistrationType.APP_SOURCE)
                .setSourceType(Source.SourceType.EVENT)
                .setRequestTime(System.currentTimeMillis())
                .setRetryCount(0)
                .setDebugKeyAllowed(true)
                .setRegistrationId(
                        AsyncRegistrationFixture.ValidAsyncRegistrationParams.REGISTRATION_ID)
                .build();
    }

    private static AsyncRegistration createAsyncRegistrationForAppTrigger() {
        return new AsyncRegistration.Builder()
                .setId(UUID.randomUUID().toString())
                .setRegistrationUri(REGISTRATION_URI)
                .setRegistrant(DEFAULT_REGISTRANT)
                .setTopOrigin(APP_TOP_ORIGIN)
                .setType(AsyncRegistration.RegistrationType.APP_TRIGGER)
                .setRequestTime(System.currentTimeMillis())
                .setRetryCount(0)
                .setDebugKeyAllowed(true)
                .setRegistrationId(
                        AsyncRegistrationFixture.ValidAsyncRegistrationParams.REGISTRATION_ID)
                .build();
    }

    private static AsyncRegistration createAsyncRegistrationForWebSource() {
        return new AsyncRegistration.Builder()
                .setId(UUID.randomUUID().toString())
                .setRegistrationUri(REGISTRATION_URI)
                .setWebDestination(WEB_DESTINATION)
                .setOsDestination(APP_DESTINATION)
                .setRegistrant(DEFAULT_REGISTRANT)
                .setVerifiedDestination(DEFAULT_VERIFIED_DESTINATION)
                .setTopOrigin(WEB_TOP_ORIGIN)
                .setType(AsyncRegistration.RegistrationType.WEB_SOURCE)
                .setSourceType(Source.SourceType.EVENT)
                .setRequestTime(System.currentTimeMillis())
                .setRetryCount(0)
                .setDebugKeyAllowed(true)
                .setRegistrationId(
                        AsyncRegistrationFixture.ValidAsyncRegistrationParams.REGISTRATION_ID)
                .build();
    }

    private static AsyncRegistration createAsyncRegistrationForWebTrigger() {
        return new AsyncRegistration.Builder()
                .setId(UUID.randomUUID().toString())
                .setRegistrationUri(REGISTRATION_URI)
                .setRegistrant(DEFAULT_REGISTRANT)
                .setTopOrigin(WEB_TOP_ORIGIN)
                .setType(AsyncRegistration.RegistrationType.WEB_TRIGGER)
                .setRequestTime(System.currentTimeMillis())
                .setRetryCount(0)
                .setDebugKeyAllowed(true)
                .setRegistrationId(
                        AsyncRegistrationFixture.ValidAsyncRegistrationParams.REGISTRATION_ID)
                .build();
    }

    private static AsyncRegistration createAsyncRegistrationForAppSources() {
        return new AsyncRegistration.Builder()
                .setId(UUID.randomUUID().toString())
                .setRegistrationUri(REGISTRATION_URI)
                .setRegistrant(DEFAULT_REGISTRANT)
                .setTopOrigin(DEFAULT_REGISTRANT)
                .setType(AsyncRegistration.RegistrationType.APP_SOURCES)
                .setRequestTime(System.currentTimeMillis())
                .setRetryCount(0)
                .setDebugKeyAllowed(true)
                .setRegistrationId(
                        AsyncRegistrationFixture.ValidAsyncRegistrationParams.REGISTRATION_ID)
                .build();
    }
}
