/*
 * Copyright 2020 The Android Open Source Project
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

package android.app.time.cts;

import static android.app.time.cts.ParcelableTestSupport.assertRoundTripParcelable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.app.time.TimeZoneConfiguration;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TimeZoneConfigurationTest {

    @Test
    public void testBuilder_copyConstructor() {
        TimeZoneConfiguration.Builder builder1 =
                new TimeZoneConfiguration.Builder()
                        .setAutoDetectionEnabled(true)
                        .setGeoDetectionEnabled(true);
        TimeZoneConfiguration configuration1 = builder1.build();

        TimeZoneConfiguration configuration2 =
                new TimeZoneConfiguration.Builder(configuration1).build();

        assertEquals(configuration1, configuration2);
    }

    @Test
    public void testEquals() {
        TimeZoneConfiguration.Builder builder1 =
                new TimeZoneConfiguration.Builder();
        {
            TimeZoneConfiguration one = builder1.build();
            assertEquals(one, one);
        }

        TimeZoneConfiguration.Builder builder2 =
                new TimeZoneConfiguration.Builder();
        {
            TimeZoneConfiguration one = builder1.build();
            TimeZoneConfiguration two = builder2.build();
            assertEquals(one, two);
            assertEquals(two, one);
        }

        builder1.setAutoDetectionEnabled(true);
        {
            TimeZoneConfiguration one = builder1.build();
            TimeZoneConfiguration two = builder2.build();
            assertNotEquals(one, two);
        }

        builder2.setAutoDetectionEnabled(false);
        {
            TimeZoneConfiguration one = builder1.build();
            TimeZoneConfiguration two = builder2.build();
            assertNotEquals(one, two);
        }

        builder1.setAutoDetectionEnabled(false);
        {
            TimeZoneConfiguration one = builder1.build();
            TimeZoneConfiguration two = builder2.build();
            assertEquals(one, two);
        }

        builder1.setGeoDetectionEnabled(true);
        {
            TimeZoneConfiguration one = builder1.build();
            TimeZoneConfiguration two = builder2.build();
            assertNotEquals(one, two);
        }

        builder2.setGeoDetectionEnabled(false);
        {
            TimeZoneConfiguration one = builder1.build();
            TimeZoneConfiguration two = builder2.build();
            assertNotEquals(one, two);
        }

        builder1.setGeoDetectionEnabled(false);
        {
            TimeZoneConfiguration one = builder1.build();
            TimeZoneConfiguration two = builder2.build();
            assertEquals(one, two);
        }
    }

    @Test
    public void testParcelable() {
        TimeZoneConfiguration.Builder builder =
                new TimeZoneConfiguration.Builder();
        assertRoundTripParcelable(builder.build());

        builder.setAutoDetectionEnabled(true);
        assertRoundTripParcelable(builder.build());

        builder.setAutoDetectionEnabled(false);
        assertRoundTripParcelable(builder.build());

        builder.setGeoDetectionEnabled(false);
        assertRoundTripParcelable(builder.build());

        builder.setGeoDetectionEnabled(true);
        assertRoundTripParcelable(builder.build());
    }
}
