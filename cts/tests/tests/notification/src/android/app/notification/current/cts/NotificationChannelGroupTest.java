/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.app.notification.current.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.NotificationChannelGroup;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NotificationChannelGroupTest {

    @Test
    public void testDescribeContents() {
        final int expected = 0;
        NotificationChannelGroup group = new NotificationChannelGroup("1", "1");
        assertEquals(expected, group.describeContents());
    }

    @Test
    public void testConstructor() {
        NotificationChannelGroup group =  new NotificationChannelGroup("1", "one");
        assertEquals("1", group.getId());
        assertEquals("one", group.getName().toString());
        assertFalse(group.isBlocked());
        assertNull(group.getDescription());
        assertEquals(0, group.getChannels().size());
        assertEquals(0, group.getUserLockedFields());
    }

    @Test
    public void testIsBlocked() {
        NotificationChannelGroup group =  new NotificationChannelGroup("1", "one");
        group.setBlocked(true);
        assertTrue(group.isBlocked());
    }

    @Test
    public void testWriteToParcel() {
        NotificationChannelGroup group = new NotificationChannelGroup("1", "one");
        group.setBlocked(true);
        group.setDescription("bananas!");
        Parcel parcel = Parcel.obtain();
        group.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        NotificationChannelGroup fromParcel =
                NotificationChannelGroup.CREATOR.createFromParcel(parcel);
        assertEquals(group, fromParcel);
    }

    @Test
    public void testClone() {
        NotificationChannelGroup group =  new NotificationChannelGroup("1", "one");
        group.setBlocked(true);
        group.setDescription("bananas");
        NotificationChannelGroup cloned = group.clone();
        assertEquals("1", cloned.getId());
        assertEquals("one", cloned.getName().toString());
        assertTrue(cloned.isBlocked());
        assertEquals("bananas", cloned.getDescription());
    }
}
