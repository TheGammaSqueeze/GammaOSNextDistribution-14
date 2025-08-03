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

package android.healthconnect.cts.datatypes;

import static android.healthconnect.cts.utils.DataFactory.SESSION_START_TIME;
import static android.healthconnect.cts.utils.DataFactory.buildLocationTimePoint;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.health.connect.datatypes.ExerciseRoute;
import android.health.connect.datatypes.units.Length;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ExerciseRouteTest {
    private static final Instant DEFAULT_TIME = Instant.ofEpochSecond((long) 1e9);
    private static final double DEFAULT_LATITUDE = 23.5;
    private static final double DEFAULT_LONGITUDE = 12.3;

    @Test
    public void testExerciseRouteLocation_buildViaBuilder_buildCorrectObject() {
        ExerciseRoute.Location point =
                new ExerciseRoute.Location.Builder(
                                DEFAULT_TIME, DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
                        .build();
        assertThat(point.getTime()).isEqualTo(DEFAULT_TIME);
        assertThat(point.getLatitude()).isEqualTo(DEFAULT_LATITUDE);
        assertThat(point.getLongitude()).isEqualTo(DEFAULT_LONGITUDE);
        assertThat(point.getAltitude()).isNull();
        assertThat(point.getHorizontalAccuracy()).isNull();
        assertThat(point.getVerticalAccuracy()).isNull();
    }

    @Test
    public void testExerciseRouteLocation_setExtraFieldsViaBuilder_buildCorrectObject() {
        double horizontalAcc = 2.3F;
        double verticalAcc = 0.5F;
        double altitude = -1F;
        ExerciseRoute.Location point =
                new ExerciseRoute.Location.Builder(
                                DEFAULT_TIME, DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
                        .setHorizontalAccuracy(Length.fromMeters(horizontalAcc))
                        .setVerticalAccuracy(Length.fromMeters(verticalAcc))
                        .setAltitude(Length.fromMeters(altitude))
                        .build();

        assertThat(point.getTime()).isEqualTo(DEFAULT_TIME);
        assertThat(point.getLatitude()).isEqualTo(DEFAULT_LATITUDE);
        assertThat(point.getLongitude()).isEqualTo(DEFAULT_LONGITUDE);
        assertThat(point.getAltitude()).isEqualTo(Length.fromMeters(altitude));
        assertThat(point.getHorizontalAccuracy()).isEqualTo(Length.fromMeters(horizontalAcc));
        assertThat(point.getVerticalAccuracy()).isEqualTo(Length.fromMeters(verticalAcc));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExerciseRouteLocation_latitudeIsInvalid_throwsException() {
        new ExerciseRoute.Location.Builder(DEFAULT_TIME, /* latitude= */ -120, DEFAULT_LONGITUDE)
                .build();
        fail("Must return error if latitude is illegal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExerciseRouteLocation_longitudeIsInvalid_throwsException() {
        new ExerciseRoute.Location.Builder(DEFAULT_TIME, DEFAULT_LATITUDE, /* longitude= */ 400)
                .build();
        fail("Must return error if longitude is illegal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExerciseRouteLocation_horizontalAccuracyIsInvalid_throwsException() {
        new ExerciseRoute.Location.Builder(DEFAULT_TIME, DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
                .setHorizontalAccuracy(Length.fromMeters(-5))
                .build();
        fail("Must return error if horizontal accuracy is illegal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExerciseRouteLocation_verticalAccuracyIsInvalid_throwsException() {
        new ExerciseRoute.Location.Builder(DEFAULT_TIME, DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
                .setHorizontalAccuracy(Length.fromMeters(-5))
                .build();
        fail("Must return error if vertical accuracy is illegal");
    }

    @Test
    public void testExerciseRouteLocation_buildRoute_success() {
        ExerciseRoute route =
                new ExerciseRoute(List.of(buildLocationTimePoint(SESSION_START_TIME)));
        assertThat(route.getRouteLocations()).hasSize(1);
    }

    @Test
    public void testExerciseRouteLocation_parcelable() {
        Parcel parcel = Parcel.obtain();
        double horizontalAcc = 2.3F;
        double verticalAcc = 0.5F;
        double altitude = -1F;
        ExerciseRoute.Location point =
                new ExerciseRoute.Location.Builder(
                                DEFAULT_TIME, DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
                        .setHorizontalAccuracy(Length.fromMeters(horizontalAcc))
                        .setVerticalAccuracy(Length.fromMeters(verticalAcc))
                        .setAltitude(Length.fromMeters(altitude))
                        .build();

        point.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        assertThat(ExerciseRoute.Location.CREATOR.createFromParcel(parcel)).isEqualTo(point);
    }

    @Test
    public void testExerciseRouteLocation_parcelable_missingField() {
        Parcel parcel = Parcel.obtain();
        double altitude = -1F;
        ExerciseRoute.Location point =
                new ExerciseRoute.Location.Builder(
                                DEFAULT_TIME, DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
                        .setAltitude(Length.fromMeters(altitude))
                        .build();

        point.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        assertThat(ExerciseRoute.Location.CREATOR.createFromParcel(parcel)).isEqualTo(point);
    }

    @Test
    public void testExerciseRoute_parcelable() {
        Parcel parcel = Parcel.obtain();
        ExerciseRoute.Location point1 =
                new ExerciseRoute.Location.Builder(
                                DEFAULT_TIME, DEFAULT_LATITUDE, DEFAULT_LONGITUDE)
                        .setHorizontalAccuracy(Length.fromMeters(2.3F))
                        .setVerticalAccuracy(Length.fromMeters(1.2F))
                        .setAltitude(Length.fromMeters(-1F))
                        .build();

        ExerciseRoute.Location point2 =
                new ExerciseRoute.Location.Builder(DEFAULT_TIME.plusSeconds(13), 33.5, -12.9)
                        .setHorizontalAccuracy(Length.fromMeters(0.9F))
                        .setVerticalAccuracy(Length.fromMeters(1.2F))
                        .setAltitude(Length.fromMeters(2.3F))
                        .build();

        ExerciseRoute route = new ExerciseRoute(ImmutableList.of(point1, point2));
        route.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        assertThat(ExerciseRoute.CREATOR.createFromParcel(parcel)).isEqualTo(route);
    }
}
