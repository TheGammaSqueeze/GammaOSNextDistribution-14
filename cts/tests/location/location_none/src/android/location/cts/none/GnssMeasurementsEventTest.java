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

import static org.junit.Assert.assertEquals;

import android.location.GnssAutomaticGainControl;
import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class GnssMeasurementsEventTest {

    private static final  GnssAutomaticGainControl AGC_1 =
            new GnssAutomaticGainControl.Builder().setCarrierFrequencyHz(
                    1575420000).setConstellationType(
                    GnssStatus.CONSTELLATION_GPS).setLevelDb(3.5).build();
    private static final GnssAutomaticGainControl AGC_2 =
            new GnssAutomaticGainControl.Builder().setCarrierFrequencyHz(
                    1602000000).setConstellationType(
                    GnssStatus.CONSTELLATION_GLONASS).setLevelDb(-2.8).build();

    @Test
    public void testDescribeContents() {
        GnssClock clock = new GnssClock();
        GnssMeasurement m1 = new GnssMeasurement();
        GnssMeasurement m2 = new GnssMeasurement();
        GnssAutomaticGainControl agc = new GnssAutomaticGainControl.Builder().build();
        GnssMeasurementsEvent event = new GnssMeasurementsEvent.Builder().setClock(clock)
                .setMeasurements(List.of(m1, m2))
                .setGnssAutomaticGainControls(List.of(agc)).build();
        assertEquals(0, event.describeContents());
    }

    @Test
    public void testWriteToParcel() {
        GnssMeasurementsEvent event = getTestEvent();
        Parcel parcel = Parcel.obtain();
        event.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        GnssMeasurementsEvent newEvent = GnssMeasurementsEvent.CREATOR.createFromParcel(parcel);

        assertEquals(100, newEvent.getClock().getLeapSecond());
        Collection<GnssMeasurement> measurements = newEvent.getMeasurements();
        assertEquals(2, measurements.size());
        Iterator<GnssMeasurement> iterator = measurements.iterator();
        GnssMeasurement newM1 = iterator.next();
        assertEquals(GnssStatus.CONSTELLATION_GLONASS, newM1.getConstellationType());
        GnssMeasurement newM2 = iterator.next();
        assertEquals(43999, newM2.getReceivedSvTimeNanos());

        Collection<GnssAutomaticGainControl> agcs = newEvent.getGnssAutomaticGainControls();
        assertEquals(2, agcs.size());
        Iterator<GnssAutomaticGainControl> gnssAgcIterator = agcs.iterator();
        GnssAutomaticGainControl newAgc1 = gnssAgcIterator.next();
        assertEquals(newAgc1, AGC_1);
        GnssAutomaticGainControl newAgc2 = gnssAgcIterator.next();
        assertEquals(newAgc2, AGC_2);

        assertEquals(true, newEvent.hasIsFullTracking());
        assertEquals(true, newEvent.isFullTracking());
    }

    @Test
    public void testBuilder() {
        GnssMeasurementsEvent event1 = getTestEvent();
        GnssMeasurementsEvent event2 = new GnssMeasurementsEvent.Builder(event1).build();
        assertEquals(event1.toString(), event2.toString());
    }

    @Test
    public void testClear() {
        GnssMeasurementsEvent event = new GnssMeasurementsEvent.Builder(
                getTestEvent()).clearIsFullTracking().build();
        assertEquals(false, event.hasIsFullTracking());
    }

    private GnssMeasurementsEvent getTestEvent() {
        GnssClock clock = new GnssClock();
        clock.setLeapSecond(100);
        GnssMeasurement m1 = new GnssMeasurement();
        m1.setConstellationType(GnssStatus.CONSTELLATION_GLONASS);
        GnssMeasurement m2 = new GnssMeasurement();
        m2.setReceivedSvTimeNanos(43999);
        return new GnssMeasurementsEvent.Builder().setClock(clock)
                .setMeasurements(List.of(m1, m2))
                .setGnssAutomaticGainControls(List.of(AGC_1, AGC_2))
                .setIsFullTracking(true)
                .build();
    }
}
