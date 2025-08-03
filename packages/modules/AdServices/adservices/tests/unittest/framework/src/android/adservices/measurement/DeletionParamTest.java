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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.time.Instant;
import java.util.Collections;

/** Unit tests for {@link DeletionParam} */
@SmallTest
public final class DeletionParamTest {
    private static final Context sContext = InstrumentationRegistry.getTargetContext();
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";

    private DeletionParam createExample() {
        return new DeletionParam.Builder(
                        Collections.singletonList(Uri.parse("http://foo.com")),
                        Collections.emptyList(),
                        Instant.ofEpochMilli(1642060000000L),
                        Instant.ofEpochMilli(1642060538000L),
                        sContext.getPackageName(),
                        "sdk.package.name")
                .setDeletionMode(DeletionRequest.DELETION_MODE_EXCLUDE_INTERNAL_DATA)
                .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_PRESERVE)
                .build();
    }

    private DeletionParam createDefaultExample() {
        return new DeletionParam.Builder(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Instant.MIN,
                        Instant.MAX,
                        sContext.getPackageName(),
                        /* sdkPackageName = */ "")
                .build();
    }

    void verifyExample(DeletionParam request) {
        assertEquals(1, request.getOriginUris().size());
        assertEquals("http://foo.com", request.getOriginUris().get(0).toString());
        assertTrue(request.getDomainUris().isEmpty());
        assertEquals(DeletionRequest.MATCH_BEHAVIOR_PRESERVE, request.getMatchBehavior());
        assertEquals(
                DeletionRequest.DELETION_MODE_EXCLUDE_INTERNAL_DATA, request.getDeletionMode());
        assertEquals(1642060000000L, request.getStart().toEpochMilli());
        assertEquals(1642060538000L, request.getEnd().toEpochMilli());
        assertNotNull(request.getAppPackageName());
    }

    void verifyDefaultExample(DeletionParam request) {
        assertTrue(request.getOriginUris().isEmpty());
        assertTrue(request.getDomainUris().isEmpty());
        assertEquals(DeletionRequest.MATCH_BEHAVIOR_DELETE, request.getMatchBehavior());
        assertEquals(DeletionRequest.DELETION_MODE_ALL, request.getDeletionMode());
        assertEquals(Instant.MIN, request.getStart());
        assertEquals(Instant.MAX, request.getEnd());
        assertNotNull(request.getAppPackageName());
    }

    @Test
    public void testMissingOrigin_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new DeletionParam.Builder(
                                        /* originUris = */ null,
                                        Collections.emptyList(),
                                        Instant.MIN,
                                        Instant.MAX,
                                        sContext.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testMissingDomainUris_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new DeletionParam.Builder(
                                        Collections.emptyList(),
                                        /* domainUris = */ null,
                                        Instant.MIN,
                                        Instant.MAX,
                                        sContext.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testMissingStart_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new DeletionParam.Builder(
                                        Collections.emptyList(),
                                        Collections.emptyList(),
                                        /* start = */ null,
                                        Instant.MAX,
                                        sContext.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testMissingEnd_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new DeletionParam.Builder(
                                        Collections.emptyList(),
                                        Collections.emptyList(),
                                        Instant.MIN,
                                        /* end = */ null,
                                        sContext.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testMissingAppPackageName_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new DeletionParam.Builder(
                                        Collections.emptyList(),
                                        Collections.emptyList(),
                                        Instant.MIN,
                                        Instant.MAX,
                                        /* appPackageName = */ null,
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testMissingSdkPackageName_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new DeletionParam.Builder(
                                        Collections.emptyList(),
                                        Collections.emptyList(),
                                        Instant.MIN,
                                        Instant.MAX,
                                        sContext.getPackageName(),
                                        /* sdkPackageName = */ null)
                                .build());
    }

    @Test
    public void testDefaults() {
        verifyDefaultExample(createDefaultExample());
    }

    @Test
    public void testCreation() {
        verifyExample(createExample());
    }

    @Test
    public void testParcelingDelete() {
        Parcel p = Parcel.obtain();
        createExample().writeToParcel(p, 0);
        p.setDataPosition(0);
        verifyExample(DeletionParam.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void testParcelingDeleteDefaults() {
        Parcel p = Parcel.obtain();
        createDefaultExample().writeToParcel(p, 0);
        p.setDataPosition(0);
        verifyDefaultExample(DeletionParam.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void testDescribeContents() {
        assertEquals(0, createExample().describeContents());
    }
}
