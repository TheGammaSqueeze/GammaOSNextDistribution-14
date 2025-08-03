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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.view.KeyEvent;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class WebSourceRegistrationRequestInternalTest {
    private static final Context CONTEXT =
            InstrumentationRegistry.getInstrumentation().getContext();
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://foo1.com");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo2.com");
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";
    private static final Uri TOP_ORIGIN_URI = Uri.parse("https://top-origin.com");
    private static final Uri OS_DESTINATION_URI = Uri.parse("android-app://com.os-destination");
    private static final Uri WEB_DESTINATION_URI = Uri.parse("https://web-destination.com");
    private static final Uri VERIFIED_DESTINATION = Uri.parse("https://verified-dest.com");
    private static final KeyEvent INPUT_KEY_EVENT =
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1);
    private static final long REQUEST_TIME = 10000L;

    private static final WebSourceParams SOURCE_REGISTRATION_1 =
            new WebSourceParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();

    private static final WebSourceParams SOURCE_REGISTRATION_2 =
            new WebSourceParams.Builder(REGISTRATION_URI_2).setDebugKeyAllowed(false).build();

    private static final List<WebSourceParams> SOURCE_REGISTRATIONS =
            Arrays.asList(SOURCE_REGISTRATION_1, SOURCE_REGISTRATION_2);

    private static final WebSourceRegistrationRequest EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST =
            new WebSourceRegistrationRequest.Builder(SOURCE_REGISTRATIONS, TOP_ORIGIN_URI)
                    .setAppDestination(OS_DESTINATION_URI)
                    .setWebDestination(WEB_DESTINATION_URI)
                    .setVerifiedDestination(VERIFIED_DESTINATION)
                    .setInputEvent(INPUT_KEY_EVENT)
                    .build();

    @Test
    public void build_exampleRequest_success() {
        verifyExampleRegistrationInternal(createExampleRegistrationRequest());
    }

    @Test
    public void createFromParcel_basic_success() {
        Parcel p = Parcel.obtain();
        createExampleRegistrationRequest().writeToParcel(p, 0);
        p.setDataPosition(0);
        verifyExampleRegistrationInternal(
                WebSourceRegistrationRequestInternal.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void build_nullSourceRegistrationRequest_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new WebSourceRegistrationRequestInternal.Builder(
                                        null,
                                        CONTEXT.getPackageName(),
                                        SDK_PACKAGE_NAME,
                                        REQUEST_TIME)
                                .build());
    }

    @Test
    public void build_nullAppPackageName_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new WebSourceRegistrationRequestInternal.Builder(
                                        EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                                        /* appPackageName = */ null,
                                        SDK_PACKAGE_NAME,
                                        REQUEST_TIME)
                                .build());
    }

    @Test
    public void build_nullSdkPackageName_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new WebSourceRegistrationRequestInternal.Builder(
                                        EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                                        CONTEXT.getPackageName(),
                                        /* sdkPackageName = */ null,
                                        REQUEST_TIME)
                                .build());
    }

    @Test
    public void testDescribeContents() {
        assertEquals(0, createExampleRegistrationRequest().describeContents());
    }

    @Test
    public void testHashCode_equals() {
        final WebSourceRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        final WebSourceRegistrationRequestInternal request2 = createExampleRegistrationRequest();
        final Set<WebSourceRegistrationRequestInternal> requestSet1 = Set.of(request1);
        final Set<WebSourceRegistrationRequestInternal> requestSet2 = Set.of(request2);
        assertEquals(request1.hashCode(), request2.hashCode());
        assertEquals(request1, request2);
        assertEquals(requestSet1, requestSet2);
    }

    @Test
    public void testHashCode_notEquals() {
        final WebSourceRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        final WebSourceRegistrationRequestInternal request2 =
                new WebSourceRegistrationRequestInternal.Builder(
                                EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                                "com.foo",
                                SDK_PACKAGE_NAME,
                                REQUEST_TIME)
                        .build();

        final Set<WebSourceRegistrationRequestInternal> requestSet1 = Set.of(request1);
        final Set<WebSourceRegistrationRequestInternal> requestSet2 = Set.of(request2);
        assertNotEquals(request1.hashCode(), request2.hashCode());
        assertNotEquals(request1, request2);
        assertNotEquals(requestSet1, requestSet2);
    }

    private WebSourceRegistrationRequestInternal createExampleRegistrationRequest() {
        return new WebSourceRegistrationRequestInternal.Builder(
                        EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                        CONTEXT.getPackageName(),
                        SDK_PACKAGE_NAME,
                        REQUEST_TIME)
                .setAdIdPermissionGranted(true)
                .build();
    }

    private void verifyExampleRegistrationInternal(WebSourceRegistrationRequestInternal request) {
        verifyExampleRegistration(request.getSourceRegistrationRequest());
        assertEquals(CONTEXT.getPackageName(), request.getAppPackageName());
        assertEquals(SDK_PACKAGE_NAME, request.getSdkPackageName());
        assertTrue(request.isAdIdPermissionGranted());
    }

    private void verifyExampleRegistration(WebSourceRegistrationRequest request) {
        assertEquals(SOURCE_REGISTRATIONS, request.getSourceParams());
        assertEquals(TOP_ORIGIN_URI, request.getTopOriginUri());
        assertEquals(OS_DESTINATION_URI, request.getAppDestination());
        assertEquals(WEB_DESTINATION_URI, request.getWebDestination());
        assertEquals(INPUT_KEY_EVENT.getAction(), ((KeyEvent) request.getInputEvent()).getAction());
        assertEquals(
                INPUT_KEY_EVENT.getKeyCode(), ((KeyEvent) request.getInputEvent()).getKeyCode());
        assertEquals(VERIFIED_DESTINATION, request.getVerifiedDestination());
    }
}
