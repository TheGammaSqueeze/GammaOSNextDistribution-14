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

package android.app.time.cts;

import static android.app.time.cts.ParcelableTestSupport.assertRoundTripParcelable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.app.time.UnixEpochTime;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UnixEpochTimeTest {

    @Test
    public void testEqualsAndHashcode() {
        UnixEpochTime one1000one = new UnixEpochTime(1000, 1);
        assertEqualsAndHashCode(one1000one, one1000one);

        UnixEpochTime one1000two = new UnixEpochTime(1000, 1);
        assertEqualsAndHashCode(one1000one, one1000two);

        UnixEpochTime two1000 = new UnixEpochTime(1000, 2);
        assertNotEquals(one1000one, two1000);

        UnixEpochTime one2000 = new UnixEpochTime(2000, 1);
        assertNotEquals(one1000one, one2000);
    }

    private static void assertEqualsAndHashCode(Object one, Object two) {
        assertEquals(one, two);
        assertEquals(one.hashCode(), two.hashCode());
    }

    @Test
    public void testParceling() {
        assertRoundTripParcelable(new UnixEpochTime(1000, 1));
    }

    @Test
    public void testAt() {
        long timeMillis = 1000L;
        int elapsedRealtimeMillis = 100;
        UnixEpochTime unixEpochTime = new UnixEpochTime(elapsedRealtimeMillis, timeMillis);
        // Reference time is after the timestamp.
        UnixEpochTime at125 = unixEpochTime.at(125);
        assertEquals(timeMillis + (125 - elapsedRealtimeMillis), at125.getUnixEpochTimeMillis());
        assertEquals(125, at125.getElapsedRealtimeMillis());

        // Reference time is before the timestamp.
        UnixEpochTime at75 = unixEpochTime.at(75);
        assertEquals(timeMillis + (75 - elapsedRealtimeMillis), at75.getUnixEpochTimeMillis());
        assertEquals(75, at75.getElapsedRealtimeMillis());
    }
}
