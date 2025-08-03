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
package android.adservices.measurement;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;


/**
 * Unit tests for {@link android.adservices.measurement.RegistrationRequest}
 */
@SmallTest
public final class RegistrationRequestTest {
    private static final Context sContext = InstrumentationRegistry.getTargetContext();
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";

    private RegistrationRequest createExampleAttribution() {
        return new RegistrationRequest.Builder(
                        RegistrationRequest.REGISTER_SOURCE,
                        Uri.parse("https://baz.test"),
                        sContext.getPackageName(),
                        SDK_PACKAGE_NAME)
                .setRequestTime(1000L)
                .setAdIdPermissionGranted(true)
                .build();
    }

    void verifyExampleAttribution(RegistrationRequest request) {
        assertEquals("https://baz.test", request.getRegistrationUri().toString());
        assertEquals(RegistrationRequest.REGISTER_SOURCE,
                request.getRegistrationType());
        assertNull(request.getInputEvent());
        assertNotNull(request.getAppPackageName());
        assertEquals(SDK_PACKAGE_NAME, request.getSdkPackageName());
        assertEquals(1000L, request.getRequestTime());
        assertTrue(request.isAdIdPermissionGranted());
    }

    @Test
    public void testNoRegistrationType_throwException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RegistrationRequest.Builder(
                                        RegistrationRequest.INVALID,
                                        Uri.parse("https://foo.test"),
                                        sContext.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testRegistrationUriWithoutScheme_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RegistrationRequest.Builder(
                                        RegistrationRequest.REGISTER_SOURCE,
                                        Uri.parse("foo.test"),
                                        sContext.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testRegistrationUriWithNonHttpsScheme_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RegistrationRequest.Builder(
                                        RegistrationRequest.REGISTER_SOURCE,
                                        Uri.parse("http://foo.test"),
                                        sContext.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testNoAttributionSource_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new RegistrationRequest.Builder(
                                        RegistrationRequest.REGISTER_TRIGGER,
                                        /* registrationUri = */ null,
                                        sContext.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testNoAppPackageName_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new RegistrationRequest.Builder(
                                        RegistrationRequest.REGISTER_TRIGGER,
                                        Uri.parse("https://foo.test"),
                                        /* appPackageName = */ null,
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testNoSdkPackageName_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new RegistrationRequest.Builder(
                                        RegistrationRequest.REGISTER_TRIGGER,
                                        /* registrationUri = */ Uri.parse("https://foo.test"),
                                        sContext.getPackageName(),
                                        /* sdkPackageName = */ null)
                                .build());
    }

    @Test
    public void testDefaults() throws Exception {
        RegistrationRequest request =
                new RegistrationRequest.Builder(
                                RegistrationRequest.REGISTER_TRIGGER,
                                Uri.parse("https://foo.test"),
                                sContext.getPackageName(),
                                SDK_PACKAGE_NAME)
                        .build();
        assertEquals("https://foo.test", request.getRegistrationUri().toString());
        assertEquals(RegistrationRequest.REGISTER_TRIGGER, request.getRegistrationType());
        assertNull(request.getInputEvent());
        assertNotNull(request.getAppPackageName());
        assertEquals(SDK_PACKAGE_NAME, request.getSdkPackageName());
        assertEquals(0, request.getRequestTime());
    }

    @Test
    public void testCreationAttribution() {
        verifyExampleAttribution(createExampleAttribution());
    }

    @Test
    public void testParcelingAttribution() {
        Parcel p = Parcel.obtain();
        createExampleAttribution().writeToParcel(p, 0);
        p.setDataPosition(0);
        verifyExampleAttribution(
                RegistrationRequest.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void testDescribeContents() {
        assertEquals(0, createExampleAttribution().describeContents());
    }
}
