/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.adservices.customaudience;

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.net.Uri;
import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

public final class ScheduleCustomAudienceUpdateInputTest extends AdServicesUnitTestCase {
    public static Uri VALID_UPDATE_URI_1 =
            CustomAudienceFixture.getValidFetchUriByBuyer(CommonFixture.VALID_BUYER_1, "1");
    public static Duration VALID_DELAY = Duration.ofMinutes(100);
    public static PartialCustomAudience VALID_PARTIAL_CA =
            new PartialCustomAudience.Builder("fake_ca").build();
    public static List<PartialCustomAudience> VALID_PARTIAL_CA_LIST = List.of(VALID_PARTIAL_CA);

    @Test
    public void testBuildValidRequest_All_Success() {
        ScheduleCustomAudienceUpdateInput request =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                VALID_UPDATE_URI_1,
                                CustomAudienceFixture.VALID_OWNER,
                                VALID_DELAY,
                                VALID_PARTIAL_CA_LIST)
                        .build();

        expect.withMessage("Update Uri").that(request.getUpdateUri()).isEqualTo(VALID_UPDATE_URI_1);
        expect.withMessage("Min Delay Time").that(request.getMinDelay()).isEqualTo(VALID_DELAY);
        expect.withMessage("Caller package name")
                .that(request.getCallerPackageName().equals((CustomAudienceFixture.VALID_OWNER)));
        expect.withMessage("Partial Custom Audience List")
                .that(request.getPartialCustomAudienceList())
                .containsExactly(VALID_PARTIAL_CA);
    }

    @Test
    public void testBuildValidRequest_AllSetters_Success() {
        Uri uri2 = CustomAudienceFixture.getValidFetchUriByBuyer(CommonFixture.VALID_BUYER_2, "2");
        Duration delay2 = Duration.ofMinutes(200);
        List<PartialCustomAudience> emptyCaList = Collections.emptyList();

        ScheduleCustomAudienceUpdateInput request =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                VALID_UPDATE_URI_1,
                                CustomAudienceFixture.VALID_OWNER,
                                VALID_DELAY,
                                VALID_PARTIAL_CA_LIST)
                        .setUpdateUri(uri2)
                        .setMinDelay(delay2)
                        .setPartialCustomAudienceList(emptyCaList)
                        .build();

        expect.withMessage("Update Uri").that(request.getUpdateUri()).isEqualTo(uri2);
        expect.withMessage("Min Delay time").that(request.getMinDelay()).isEqualTo(delay2);
        expect.withMessage("Caller Package name")
                .that(request.getCallerPackageName().equals((CustomAudienceFixture.VALID_OWNER)));
        expect.withMessage("Partial Custom Audience List")
                .that(request.getPartialCustomAudienceList())
                .isEmpty();
    }

    @Test
    public void testBuildValidRequestParcel_Success() {
        ScheduleCustomAudienceUpdateInput request =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                VALID_UPDATE_URI_1,
                                CustomAudienceFixture.VALID_OWNER,
                                VALID_DELAY,
                                VALID_PARTIAL_CA_LIST)
                        .build();

        Parcel p = Parcel.obtain();
        request.writeToParcel(p, 0);
        p.setDataPosition(0);
        ScheduleCustomAudienceUpdateInput fromParcel =
                ScheduleCustomAudienceUpdateInput.CREATOR.createFromParcel(p);

        expect.that(request).isEqualTo(fromParcel);
    }

    @Test
    public void testBuild_NullUpdateUri_Throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ScheduleCustomAudienceUpdateInput.Builder(
                                        null,
                                        CustomAudienceFixture.VALID_OWNER,
                                        VALID_DELAY,
                                        VALID_PARTIAL_CA_LIST)
                                .build());
    }

    @Test
    public void testBuild_NullPackage_Throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ScheduleCustomAudienceUpdateInput.Builder(
                                        VALID_UPDATE_URI_1,
                                        null,
                                        VALID_DELAY,
                                        VALID_PARTIAL_CA_LIST)
                                .build());
    }

    @Test
    public void testBuild_NullDelay_Throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ScheduleCustomAudienceUpdateInput.Builder(
                                        VALID_UPDATE_URI_1,
                                        CustomAudienceFixture.VALID_OWNER,
                                        null,
                                        VALID_PARTIAL_CA_LIST)
                                .build());
    }

    @Test
    public void testBuild_NullPartialCa_Throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ScheduleCustomAudienceUpdateInput.Builder(
                                        VALID_UPDATE_URI_1,
                                        CustomAudienceFixture.VALID_OWNER,
                                        VALID_DELAY,
                                        null)
                                .build());
    }

    @Test
    public void testEquals_Same() {
        ScheduleCustomAudienceUpdateInput request1 =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                VALID_UPDATE_URI_1,
                                CustomAudienceFixture.VALID_OWNER,
                                VALID_DELAY,
                                VALID_PARTIAL_CA_LIST)
                        .build();

        ScheduleCustomAudienceUpdateInput request2 =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                VALID_UPDATE_URI_1,
                                CustomAudienceFixture.VALID_OWNER,
                                VALID_DELAY,
                                VALID_PARTIAL_CA_LIST)
                        .build();
        expectObjectsAreEqual(request2, request1);
    }

    @Test
    public void testEquals_Different() {
        ScheduleCustomAudienceUpdateInput request1 =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                VALID_UPDATE_URI_1,
                                CustomAudienceFixture.VALID_OWNER,
                                VALID_DELAY,
                                VALID_PARTIAL_CA_LIST)
                        .build();

        ScheduleCustomAudienceUpdateInput request2 =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                VALID_UPDATE_URI_1,
                                CustomAudienceFixture.VALID_OWNER,
                                VALID_DELAY,
                                Collections.emptyList())
                        .build();

        expectObjectsAreNotEqual(request1, request2);
    }

    @Test
    public void testHashCode_Same() {
        int request1Hash =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                VALID_UPDATE_URI_1,
                                CustomAudienceFixture.VALID_OWNER,
                                VALID_DELAY,
                                VALID_PARTIAL_CA_LIST)
                        .build()
                        .hashCode();

        int request2Hash =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                VALID_UPDATE_URI_1,
                                CustomAudienceFixture.VALID_OWNER,
                                VALID_DELAY,
                                VALID_PARTIAL_CA_LIST)
                        .build()
                        .hashCode();

        expect.withMessage("Object hash").that(request1Hash == request2Hash).isTrue();
    }

    @Test
    public void testHashCode_Different() {
        int request1Hash =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                VALID_UPDATE_URI_1,
                                CustomAudienceFixture.VALID_OWNER,
                                VALID_DELAY,
                                VALID_PARTIAL_CA_LIST)
                        .build()
                        .hashCode();

        int request2Hash =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                VALID_UPDATE_URI_1,
                                CustomAudienceFixture.VALID_OWNER,
                                VALID_DELAY,
                                Collections.emptyList())
                        .build()
                        .hashCode();

        expect.withMessage("Object hash").that(request1Hash == request2Hash).isFalse();
    }

    @Test
    public void testToString() {
        String request =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                VALID_UPDATE_URI_1,
                                CustomAudienceFixture.VALID_OWNER,
                                VALID_DELAY,
                                VALID_PARTIAL_CA_LIST)
                        .build()
                        .toString();

        String expected =
                String.format(
                        "ScheduleCustomAudienceUpdateInput {updateUri=%s, "
                                + "callerPackageName=%s, "
                                + "delayTimeMinutes=%s, "
                                + "partialCustomAudienceList=%s}",
                        VALID_UPDATE_URI_1,
                        CustomAudienceFixture.VALID_OWNER,
                        VALID_DELAY.toMinutes(),
                        VALID_PARTIAL_CA_LIST);

        expect.withMessage("Object to string").that(request.toString()).isEqualTo(expected);
    }
}
