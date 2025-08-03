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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.net.Uri;
import android.os.Parcel;
import android.view.KeyEvent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SourceRegistrationRequestTest {
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://bar.test");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo.test");
    private static final Uri INVALID_REGISTRATION_URI = Uri.parse("http://bar.test");
    private static final List<Uri> SOURCE_REGISTRATIONS =
            Arrays.asList(REGISTRATION_URI_1, REGISTRATION_URI_2);
    private static final KeyEvent INPUT_KEY_EVENT =
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1);

    private static final SourceRegistrationRequest SOURCE_REGISTRATION_REQUEST =
            createExampleRegistrationRequest();

    @Test
    public void testDefaults() throws Exception {
        SourceRegistrationRequest request =
                new SourceRegistrationRequest.Builder(SOURCE_REGISTRATIONS).build();

        assertEquals(SOURCE_REGISTRATIONS, request.getRegistrationUris());
        assertNull(request.getInputEvent());
    }

    @Test
    public void build_withAllFieldsPopulated_successfullyRetrieved() {
        SourceRegistrationRequest request = createExampleRegistrationRequest();
        assertEquals(SOURCE_REGISTRATIONS, request.getRegistrationUris());
        assertEquals(INPUT_KEY_EVENT, request.getInputEvent());
    }

    @Test
    public void writeToParcel_withInputEvent_success() {
        Parcel p = Parcel.obtain();
        SOURCE_REGISTRATION_REQUEST.writeToParcel(p, 0);
        p.setDataPosition(0);
        SourceRegistrationRequest fromParcel =
                SourceRegistrationRequest.CREATOR.createFromParcel(p);
        assertEquals(SOURCE_REGISTRATIONS, fromParcel.getRegistrationUris());
        assertEquals(
                INPUT_KEY_EVENT.getAction(), ((KeyEvent) fromParcel.getInputEvent()).getAction());
        assertEquals(
                INPUT_KEY_EVENT.getKeyCode(), ((KeyEvent) fromParcel.getInputEvent()).getKeyCode());
        p.recycle();
    }

    @Test
    public void writeToParcel_withoutInputEvent_success() {
        Parcel p = Parcel.obtain();
        new SourceRegistrationRequest.Builder(SOURCE_REGISTRATIONS).build().writeToParcel(p, 0);
        p.setDataPosition(0);
        SourceRegistrationRequest fromParcel =
                SourceRegistrationRequest.CREATOR.createFromParcel(p);
        assertEquals(SOURCE_REGISTRATIONS, fromParcel.getRegistrationUris());
        assertNull(fromParcel.getInputEvent());
        p.recycle();
    }

    @Test
    public void build_withInvalidParams_fail() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SourceRegistrationRequest.Builder(null)
                                .setInputEvent(INPUT_KEY_EVENT)
                                .build());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SourceRegistrationRequest.Builder(Collections.emptyList())
                                .setInputEvent(INPUT_KEY_EVENT)
                                .build());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SourceRegistrationRequest.Builder(generateAppRegistrationUrisList(21))
                                .setInputEvent(INPUT_KEY_EVENT)
                                .build());

        List<Uri> listWithInvalidRegistrationUri = new ArrayList<>();
        listWithInvalidRegistrationUri.addAll(SOURCE_REGISTRATIONS);
        listWithInvalidRegistrationUri.add(INVALID_REGISTRATION_URI);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SourceRegistrationRequest.Builder(listWithInvalidRegistrationUri)
                                .setInputEvent(INPUT_KEY_EVENT)
                                .build());
    }

    @Test
    public void testDescribeContents() {
        assertEquals(0, SOURCE_REGISTRATION_REQUEST.describeContents());
    }

    @Test
    public void testHashCode_equals() throws Exception {
        final SourceRegistrationRequest request1 = createExampleRegistrationRequest();
        final SourceRegistrationRequest request2 = createExampleRegistrationRequest();
        final Set<SourceRegistrationRequest> requestSet1 = Set.of(request1);
        final Set<SourceRegistrationRequest> requestSet2 = Set.of(request2);
        assertEquals(request1.hashCode(), request2.hashCode());
        assertEquals(request1, request2);
        assertEquals(requestSet1, requestSet2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        final SourceRegistrationRequest request1 = createExampleRegistrationRequest();
        final SourceRegistrationRequest request2 =
                new SourceRegistrationRequest.Builder(SOURCE_REGISTRATIONS)
                        .setInputEvent(null)
                        .build();
        final Set<SourceRegistrationRequest> requestData1 = Set.of(request1);
        final Set<SourceRegistrationRequest> requestData2 = Set.of(request2);
        assertNotEquals(request1.hashCode(), request2.hashCode());
        assertNotEquals(request1, request2);
        assertNotEquals(requestData1, requestData2);
    }

    private static List<Uri> generateAppRegistrationUrisList(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> Uri.parse(REGISTRATION_URI_1.toString()))
                .collect(Collectors.toList());
    }

    private static SourceRegistrationRequest createExampleRegistrationRequest() {
        return new SourceRegistrationRequest.Builder(SOURCE_REGISTRATIONS)
                .setInputEvent(INPUT_KEY_EVENT)
                .build();
    }
}
