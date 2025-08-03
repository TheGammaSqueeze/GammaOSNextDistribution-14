/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.location.cts.none;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.location.Location;
import android.os.Bundle;
import android.os.Parcel;
import android.util.StringBuilderPrinter;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.DecimalFormat;

@RunWith(AndroidJUnit4.class)
public class LocationTest {

    private static final float DELTA = 0.01f;

    @Test
    public void testConstructor_Defaults() {
        Location l = new Location("provider");

        assertThat(l.getProvider()).isEqualTo("provider");
        assertThat(l.getTime()).isEqualTo(0);
        assertThat(l.getElapsedRealtimeNanos()).isEqualTo(0);
        assertThat(l.hasElapsedRealtimeUncertaintyNanos()).isFalse();
        assertThat(l.getLatitude()).isEqualTo(0);
        assertThat(l.getLongitude()).isEqualTo(0);
        assertThat(l.hasAltitude()).isFalse();
        assertThat(l.hasSpeed()).isFalse();
        assertThat(l.hasBearing()).isFalse();
        assertThat(l.hasVerticalAccuracy()).isFalse();
        assertThat(l.hasSpeedAccuracy()).isFalse();
        assertThat(l.hasBearingAccuracy()).isFalse();
        assertThat(l.hasMslAltitude()).isFalse();
        assertThat(l.hasMslAltitudeAccuracy()).isFalse();
        assertThat(l.isMock()).isFalse();
        assertThat(l.getExtras()).isNull();
    }

    @Test
    public void testSet() {
        Location location = new Location("");

        Location l = new Location("test");
        l.setTime(1);
        l.setElapsedRealtimeNanos(2);
        l.setElapsedRealtimeUncertaintyNanos(3);
        l.setLatitude(-90);
        l.setLongitude(90);
        l.setAltitude(100);
        l.setVerticalAccuracyMeters(90);
        l.setSpeed(1000);
        l.setSpeedAccuracyMetersPerSecond(9);
        l.setBearing(7);
        l.setBearingAccuracyDegrees(11);
        l.setMslAltitudeMeters(-12);
        l.setMslAltitudeAccuracyMeters(13);
        l.setMock(true);
        Bundle b = new Bundle();
        b.putString("key", "value");
        l.setExtras(b);

        location.set(l);
        assertThat(location).isEqualTo(l);
    }

    @Test
    public void testValues() {
        Location l = new Location("provider");

        l.setProvider("test");
        assertThat(l.getProvider()).isEqualTo("test");

        l.setTime(1);
        assertThat(l.getTime()).isEqualTo(1);
        l.setTime(Long.MAX_VALUE);
        assertThat(l.getTime()).isEqualTo(Long.MAX_VALUE);

        l.setElapsedRealtimeNanos(1);
        assertThat(l.getElapsedRealtimeNanos()).isEqualTo(1);
        l.setElapsedRealtimeNanos(Long.MAX_VALUE);
        assertThat(l.getElapsedRealtimeNanos()).isEqualTo(Long.MAX_VALUE);

        l.setElapsedRealtimeUncertaintyNanos(1);
        assertThat(l.hasElapsedRealtimeUncertaintyNanos()).isTrue();
        assertThat(l.getElapsedRealtimeUncertaintyNanos()).isEqualTo(1);
        l.removeElapsedRealtimeUncertaintyNanos();
        assertThat(l.hasElapsedRealtimeUncertaintyNanos()).isFalse();

        l.setLatitude(-90);
        assertThat(l.getLatitude()).isEqualTo(-90);

        l.setLongitude(90);
        assertThat(l.getLongitude()).isEqualTo(90);

        l.setAltitude(100);
        assertThat(l.hasAltitude()).isTrue();
        assertThat(l.getAltitude()).isEqualTo(100);
        l.removeAltitude();
        assertThat(l.hasAltitude()).isFalse();

        l.setVerticalAccuracyMeters(90);
        assertThat(l.hasVerticalAccuracy()).isTrue();
        assertThat(l.getVerticalAccuracyMeters()).isEqualTo(90);
        l.removeVerticalAccuracy();
        assertThat(l.hasVerticalAccuracy()).isFalse();

        l.setSpeed(1000);
        assertThat(l.hasSpeed()).isTrue();
        assertThat(l.getSpeed()).isEqualTo(1000);
        l.removeSpeed();
        assertThat(l.hasSpeed()).isFalse();

        l.setSpeedAccuracyMetersPerSecond(9);
        assertThat(l.hasSpeedAccuracy()).isTrue();
        assertThat(l.getSpeedAccuracyMetersPerSecond()).isEqualTo(9);
        l.removeSpeedAccuracy();
        assertThat(l.hasSpeedAccuracy()).isFalse();

        l.setBearing(7);
        assertThat(l.hasBearing()).isTrue();
        assertThat(l.getBearing()).isEqualTo(7);
        l.setBearing(Float.MAX_VALUE);
        assertThat(l.getBearing()).isEqualTo(0);
        l.setBearing((Float.MAX_VALUE - 1) * -1);
        assertThat(l.getBearing()).isEqualTo(0);
        l.setBearing(371);
        assertThat(l.getBearing()).isEqualTo(11f);
        l.setBearing(-371);
        assertThat(l.getBearing()).isEqualTo(349f);
        l.removeBearing();
        assertThat(l.hasBearing()).isFalse();

        l.setBearingAccuracyDegrees(11);
        assertThat(l.hasBearingAccuracy()).isTrue();
        assertThat(l.getBearingAccuracyDegrees()).isEqualTo(11);
        l.removeBearingAccuracy();
        assertThat(l.hasBearingAccuracy()).isFalse();

        l.setMslAltitudeMeters(100);
        assertThat(l.hasMslAltitude()).isTrue();
        assertThat(l.getMslAltitudeMeters()).isEqualTo(100);
        l.removeMslAltitude();
        assertThat(l.hasMslAltitude()).isFalse();

        l.setMslAltitudeAccuracyMeters(90);
        assertThat(l.hasMslAltitudeAccuracy()).isTrue();
        assertThat(l.getMslAltitudeAccuracyMeters()).isEqualTo(90);
        l.removeMslAltitudeAccuracy();
        assertThat(l.hasMslAltitudeAccuracy()).isFalse();

        l.setMock(true);
        assertThat(l.isMock()).isTrue();
        l.setMock(false);
        assertThat(l.isMock()).isFalse();

        l.setExtras(new Bundle());
        assertThat(l.getExtras()).isNotNull();
    }

    @Test
    public void testDump() {
        StringBuilder sb = new StringBuilder();
        new Location("").dump(new StringBuilderPrinter(sb), "");
        assertNotNull(sb.toString());
    }

    @Test
    public void testConvert_CoordinateToRepresentation() {
        DecimalFormat df = new DecimalFormat("###.#####");
        String result;

        result = Location.convert(-80.0, Location.FORMAT_DEGREES);
        assertEquals("-" + df.format(80.0), result);

        result = Location.convert(-80.085, Location.FORMAT_MINUTES);
        assertEquals("-80:" + df.format(5.1), result);

        result = Location.convert(-80, Location.FORMAT_MINUTES);
        assertEquals("-80:" + df.format(0), result);

        result = Location.convert(-80.075, Location.FORMAT_MINUTES);
        assertEquals("-80:" + df.format(4.5), result);

        result = Location.convert(-80.075, Location.FORMAT_DEGREES);
        assertEquals("-" + df.format(80.075), result);

        result = Location.convert(-80.075, Location.FORMAT_SECONDS);
        assertEquals("-80:4:30", result);

        try {
            Location.convert(-181, Location.FORMAT_SECONDS);
            fail("should throw IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            // expected.
        }

        try {
            Location.convert(181, Location.FORMAT_SECONDS);
            fail("should throw IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            // expected.
        }

        try {
            Location.convert(-80.075, -1);
            fail("should throw IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            // expected.
        }
    }

    @Test
    public void testConvert_RepresentationToCoordinate() {
        double result;

        result = Location.convert("-80.075");
        assertEquals(-80.075, result, DELTA);

        result = Location.convert("-80:05.10000");
        assertEquals(-80.085, result, DELTA);

        result = Location.convert("-80:04:03.00000");
        assertEquals(-80.0675, result, DELTA);

        result = Location.convert("-80:4:3");
        assertEquals(-80.0675, result, DELTA);

        try {
            Location.convert(null);
            fail("should throw NullPointerException.");
        } catch (NullPointerException e){
            // expected.
        }

        try {
            Location.convert(":");
            fail("should throw IllegalArgumentException.");
        } catch (IllegalArgumentException e){
            // expected.
        }

        try {
            Location.convert("190:4:3");
            fail("should throw IllegalArgumentException.");
        } catch (IllegalArgumentException e){
            // expected.
        }

        try {
            Location.convert("-80:60:3");
            fail("should throw IllegalArgumentException.");
        } catch (IllegalArgumentException e){
            // expected.
        }

        try {
            Location.convert("-80:4:60");
            fail("should throw IllegalArgumentException.");
        } catch (IllegalArgumentException e){
            // expected.
        }
    }

    @Test
    public void testDistanceBetween() {
        float[] result = new float[3];
        Location.distanceBetween(0, 0, 0, 0, result);
        assertEquals(0.0, result[0], DELTA);
        assertEquals(0.0, result[1], DELTA);
        assertEquals(0.0, result[2], DELTA);

        Location.distanceBetween(20, 30, -40, 140, result);
        assertEquals(1.3094936E7, result[0], 1);
        assertEquals(125.4538, result[1], DELTA);
        assertEquals(93.3971, result[2], DELTA);

        try {
            Location.distanceBetween(20, 30, -40, 140, null);
            fail("should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected.
        }

        try {
            Location.distanceBetween(20, 30, -40, 140, new float[0]);
            fail("should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected.
        }
    }

    @Test
    public void testDistanceTo() {
        float distance;
        Location zeroLocation = new Location("");
        zeroLocation.setLatitude(0);
        zeroLocation.setLongitude(0);

        Location testLocation = new Location("");
        testLocation.setLatitude(30);
        testLocation.setLongitude(50);

        distance = zeroLocation.distanceTo(zeroLocation);
        assertEquals(0, distance, DELTA);

        distance = zeroLocation.distanceTo(testLocation);
        assertEquals(6244139.0, distance, 1);
    }

    @Test
    public void testBearingTo() {
        Location location = new Location("");
        Location dest = new Location("");

        // set the location to Beijing
        location.setLatitude(39.9);
        location.setLongitude(116.4);
        // set the destination to Chengdu
        dest.setLatitude(30.7);
        dest.setLongitude(104.1);
        assertEquals(-128.66, location.bearingTo(dest), DELTA);

        float bearing;
        Location zeroLocation = new Location("");
        zeroLocation.setLatitude(0);
        zeroLocation.setLongitude(0);

        Location testLocation = new Location("");
        testLocation.setLatitude(0);
        testLocation.setLongitude(150);

        bearing = zeroLocation.bearingTo(zeroLocation);
        assertEquals(0.0f, bearing, DELTA);

        bearing = zeroLocation.bearingTo(testLocation);
        assertEquals(90.0f, bearing, DELTA);

        testLocation.setLatitude(90);
        testLocation.setLongitude(0);
        bearing = zeroLocation.bearingTo(testLocation);
        assertEquals(0.0f, bearing, DELTA);

        try {
            location.bearingTo(null);
            fail("should throw NullPointerException");
        } catch (NullPointerException e) {
            // expected.
        }
    }

    @Test
    public void testParcelRoundtrip() {
        Location l = new Location("test");
        l.setTime(1);
        l.setElapsedRealtimeNanos(2);
        l.setElapsedRealtimeUncertaintyNanos(3);
        l.setLatitude(-90);
        l.setLongitude(90);
        l.setAltitude(100);
        l.setVerticalAccuracyMeters(90);
        l.setSpeed(1000);
        l.setSpeedAccuracyMetersPerSecond(9);
        l.setBearing(7);
        l.setBearingAccuracyDegrees(11);
        l.setMslAltitudeMeters(-12);
        l.setMslAltitudeAccuracyMeters(13);
        l.setMock(true);
        Bundle b = new Bundle();
        b.putString("key", "value");
        l.setExtras(b);

        Parcel parcel = Parcel.obtain();
        try {
            l.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            assertThat(Location.CREATOR.createFromParcel(parcel)).isEqualTo(l);
        } finally {
            parcel.recycle();
        }
    }
}
