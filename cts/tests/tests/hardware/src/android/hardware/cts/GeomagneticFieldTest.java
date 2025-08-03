/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.hardware.cts;

import android.hardware.GeomagneticField;
import android.platform.test.annotations.Presubmit;
import android.test.AndroidTestCase;

import java.util.Calendar;
import java.util.TimeZone;

public class GeomagneticFieldTest extends AndroidTestCase {
    private static final float DECLINATION_THRESHOLD = 0.01f;
    private static final float INCLINATION_THRESHOLD = 0.01f;
    private static final float FIELD_STRENGTH_THRESHOLD = 1;

    @Presubmit
    public void testGeomagneticField() {
        // Reference values calculated from NOAA online calculator for WMM 2020
        // https://www.ngdc.noaa.gov/geomag-web/#igrfwmm
        // Mountain View, CA, USA on 2022/1/1
        assertMagneticField(37.386f, -122.083f, 32f, 2022, Calendar.JANUARY, 1,
                13.0859f, 60.9203f, 47672.9f);
        // Chengdu, China on 2022/8/8
        assertMagneticField(30.658f, 103.935f, 500f, 2022, Calendar.AUGUST, 8,
                -2.4528f, 48.3264f, 51119.6f);
        // Sao Paulo, Brazil on 2023/12/25
        assertMagneticField(-23.682f, -46.875f, 760f, 2023, Calendar.DECEMBER, 25,
                -21.7186f, -40.0321f, 22830.8f);
        // Boston, MA, USA on 2024/2/10
        assertMagneticField(42.313f, -71.127f, 43f, 2024, Calendar.FEBRUARY, 10,
                -14.0899f, 66.3987f, 51413.4f);
        // Cape Town, South Africa on 2024/5/1
        assertMagneticField(-33.913f, 18.095f, 100f, 2024, Calendar.MAY, 1,
                -25.7008f, -65.1060f, 25053.5f);
        // Sydney, Australia on 2024/12/31
        assertMagneticField(-33.847f, 150.791f, 19f, 2024, Calendar.DECEMBER, 31,
                12.6845f, -64.4769f, 57114.9f);
    }

    private void assertMagneticField(float lat, float lon, float altitude, int year, int month,
                                     int day, float dec, float inc, float strength) {
        long time = new Calendar.Builder()
                .setDate(year, month, day)
                .setTimeZone(TimeZone.getTimeZone("UTC"))
                .build()
                .getTimeInMillis();

        GeomagneticField field = new GeomagneticField(lat, lon, altitude, time);
        assertEquals(dec, field.getDeclination(), DECLINATION_THRESHOLD);
        assertEquals(inc, field.getInclination(), INCLINATION_THRESHOLD);
        assertEquals(strength, field.getFieldStrength(), FIELD_STRENGTH_THRESHOLD);
        float horizontalFieldStrengthNanoTesla = (float) (
                Math.cos(Math.toRadians(inc)) * strength);
        assertEquals(horizontalFieldStrengthNanoTesla, field.getHorizontalStrength(),
                FIELD_STRENGTH_THRESHOLD);
        float verticalFieldStrengthNanoTesla = (float) (Math.sin(Math.toRadians(inc)) * strength);
        assertEquals(verticalFieldStrengthNanoTesla, field.getZ(), FIELD_STRENGTH_THRESHOLD);
        float declinationDegree = (float) (
                Math.toDegrees(Math.atan2(field.getY(), field.getX())));
        assertEquals(dec, declinationDegree, DECLINATION_THRESHOLD);
        assertEquals(horizontalFieldStrengthNanoTesla,
                Math.sqrt(field.getX() * field.getX() + field.getY() * field.getY()),
                FIELD_STRENGTH_THRESHOLD);
    }
}
