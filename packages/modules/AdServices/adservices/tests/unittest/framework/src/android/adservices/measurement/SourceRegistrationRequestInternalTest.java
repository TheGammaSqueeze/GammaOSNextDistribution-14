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

package android.adservices.measurement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.view.KeyEvent;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class SourceRegistrationRequestInternalTest {

    private static final Context CONTEXT =
            InstrumentationRegistry.getInstrumentation().getContext();
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://bar.test");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo.test");
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";
    private static final KeyEvent INPUT_KEY_EVENT =
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1);
    private static final long BOOT_RELATIVE_REQUEST_TIME = 10000L;
    private static final String AD_ID_VALUE = "ad_id_value";

    private static final List<Uri> EXAMPLE_REGISTRATION_URIS =
            Arrays.asList(REGISTRATION_URI_1, REGISTRATION_URI_2);
    private static final SourceRegistrationRequest EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST =
            new SourceRegistrationRequest.Builder(EXAMPLE_REGISTRATION_URIS)
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
                SourceRegistrationRequestInternal.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void build_nullSourceRegistrationRequest_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SourceRegistrationRequestInternal.Builder(
                                        null,
                                        CONTEXT.getPackageName(),
                                        SDK_PACKAGE_NAME,
                                        BOOT_RELATIVE_REQUEST_TIME)
                                .build());
    }

    @Test
    public void build_nullAppPackageName_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SourceRegistrationRequestInternal.Builder(
                                        EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                                        /* appPackageName = */ null,
                                        SDK_PACKAGE_NAME,
                                        BOOT_RELATIVE_REQUEST_TIME)
                                .build());
    }

    @Test
    public void build_nullSdkPackageName_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SourceRegistrationRequestInternal.Builder(
                                        EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                                        CONTEXT.getPackageName(),
                                        /* sdkPackageName = */ null,
                                        BOOT_RELATIVE_REQUEST_TIME)
                                .build());
    }

    @Test
    public void testDescribeContents() {
        assertEquals(0, createExampleRegistrationRequest().describeContents());
    }

    @Test
    public void testHashCode_equals() {
        final SourceRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        final SourceRegistrationRequestInternal request2 = createExampleRegistrationRequest();
        final Set<SourceRegistrationRequestInternal> requestSet1 = Set.of(request1);
        final Set<SourceRegistrationRequestInternal> requestSet2 = Set.of(request2);
        assertEquals(request1.hashCode(), request2.hashCode());
        assertEquals(request1, request2);
        assertEquals(requestSet1, requestSet2);
    }

    @Test
    public void testHashCode_appPackageNameMismatch_notEquals() {
        final SourceRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        final SourceRegistrationRequestInternal request2 =
                new SourceRegistrationRequestInternal.Builder(
                                EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                                "com.foo",
                                SDK_PACKAGE_NAME,
                                BOOT_RELATIVE_REQUEST_TIME)
                        .setAdIdValue(AD_ID_VALUE)
                        .build();

        assertHashCodeNotEqual(request1, request2);
    }

    @Test
    public void testHashCode_sdkNameMismatch_notEquals() {
        final SourceRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        final SourceRegistrationRequestInternal request2 =
                new SourceRegistrationRequestInternal.Builder(
                                EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                                CONTEXT.getPackageName(),
                                "com.foo",
                                BOOT_RELATIVE_REQUEST_TIME)
                        .setAdIdValue(AD_ID_VALUE)
                        .build();

        assertHashCodeNotEqual(request1, request2);
    }

    @Test
    public void testHashCode_registrationUrisMismatch_notEquals() {
        final SourceRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        SourceRegistrationRequest diffSourceRegistrationRequest =
                new SourceRegistrationRequest.Builder(EXAMPLE_REGISTRATION_URIS)
                        .setInputEvent(null) // this is the difference
                        .build();
        final SourceRegistrationRequestInternal request2 =
                new SourceRegistrationRequestInternal.Builder(
                                diffSourceRegistrationRequest,
                                CONTEXT.getPackageName(),
                                SDK_PACKAGE_NAME,
                                BOOT_RELATIVE_REQUEST_TIME)
                        .setAdIdValue(AD_ID_VALUE)
                        .build();

        assertHashCodeNotEqual(request1, request2);
    }

    @Test
    public void testHashCode_requestTimeMismatch_notEquals() {
        final SourceRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        final SourceRegistrationRequestInternal request2 =
                new SourceRegistrationRequestInternal.Builder(
                                EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                                CONTEXT.getPackageName(),
                                SDK_PACKAGE_NAME,
                                43534534653L)
                        .setAdIdValue(AD_ID_VALUE)
                        .build();

        assertHashCodeNotEqual(request1, request2);
    }

    @Test
    public void testHashCode_adIdMismatch_notEquals() {
        final SourceRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        final SourceRegistrationRequestInternal request2 =
                new SourceRegistrationRequestInternal.Builder(
                                EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                                CONTEXT.getPackageName(),
                                SDK_PACKAGE_NAME,
                                BOOT_RELATIVE_REQUEST_TIME)
                        .setAdIdValue("different_ad_id")
                        .build();

        assertHashCodeNotEqual(request1, request2);
    }

    private static void assertHashCodeNotEqual(
            SourceRegistrationRequestInternal request1,
            SourceRegistrationRequestInternal request2) {
        final Set<SourceRegistrationRequestInternal> requestSet1 = Set.of(request1);
        final Set<SourceRegistrationRequestInternal> requestSet2 = Set.of(request2);
        assertNotEquals(request1.hashCode(), request2.hashCode());
        assertNotEquals(request1, request2);
        assertNotEquals(requestSet1, requestSet2);
    }

    private SourceRegistrationRequestInternal createExampleRegistrationRequest() {
        return new SourceRegistrationRequestInternal.Builder(
                        EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                        CONTEXT.getPackageName(),
                        SDK_PACKAGE_NAME,
                        BOOT_RELATIVE_REQUEST_TIME)
                .setAdIdValue(AD_ID_VALUE)
                .build();
    }

    private void verifyExampleRegistrationInternal(SourceRegistrationRequestInternal request) {
        verifyExampleRegistration(request.getSourceRegistrationRequest());
        assertEquals(CONTEXT.getPackageName(), request.getAppPackageName());
        assertEquals(SDK_PACKAGE_NAME, request.getSdkPackageName());
        assertEquals(AD_ID_VALUE, request.getAdIdValue());
    }

    private void verifyExampleRegistration(SourceRegistrationRequest request) {
        assertEquals(EXAMPLE_REGISTRATION_URIS, request.getRegistrationUris());
        assertEquals(INPUT_KEY_EVENT.getAction(), ((KeyEvent) request.getInputEvent()).getAction());
        assertEquals(
                INPUT_KEY_EVENT.getKeyCode(), ((KeyEvent) request.getInputEvent()).getKeyCode());
    }
}
