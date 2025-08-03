/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.app.people.ConversationStatus.ACTIVITY_GAME;
import static android.app.people.ConversationStatus.AVAILABILITY_BUSY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.people.ConversationStatus;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Parcel;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ConversationStatusTest {

    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testCreation()  {
        final ConversationStatus cs =
                new ConversationStatus.Builder("id", ACTIVITY_GAME)
                        .setIcon(Icon.createWithResource(mContext, android.R.drawable.btn_default))
                        .setDescription("playing chess")
                        .setAvailability(AVAILABILITY_BUSY)
                        .setEndTimeMillis(1000)
                        .setStartTimeMillis(100)
                        .build();

        assertEquals("id", cs.getId());
        assertEquals(ACTIVITY_GAME, cs.getActivity());
        assertEquals(AVAILABILITY_BUSY, cs.getAvailability());
        assertEquals(100, cs.getStartTimeMillis());
        assertEquals(1000, cs.getEndTimeMillis());
        assertEquals(android.R.drawable.btn_default, cs.getIcon().getResId());
        assertEquals("playing chess", cs.getDescription().toString());
    }

    @Test
    public void testParcelEmpty()  {
        final ConversationStatus orig = new ConversationStatus.Builder("id", 100).build();

        Parcel parcel = Parcel.obtain();
        orig.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);

        ConversationStatus cs = ConversationStatus.CREATOR.createFromParcel(parcel);

        assertEquals("id", cs.getId());
        assertEquals(100, cs.getActivity());
        assertEquals(ConversationStatus.AVAILABILITY_UNKNOWN, cs.getAvailability());
        assertEquals(-1, cs.getStartTimeMillis());
        assertEquals(-1, cs.getEndTimeMillis());
        assertNull(cs.getIcon());
        assertNull(cs.getDescription());
    }

    @Test
    public void testParcel()  {
        final ConversationStatus orig =
                new ConversationStatus.Builder("id", ACTIVITY_GAME)
                        .setIcon(Icon.createWithResource(mContext, android.R.drawable.btn_default))
                        .setDescription("playing chess")
                        .setAvailability(AVAILABILITY_BUSY)
                        .setEndTimeMillis(1000)
                        .setStartTimeMillis(100)
                .build();

        Parcel parcel = Parcel.obtain();
        orig.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);

        ConversationStatus cs = ConversationStatus.CREATOR.createFromParcel(parcel);

        assertEquals("id", cs.getId());
        assertEquals(ACTIVITY_GAME, cs.getActivity());
        assertEquals(AVAILABILITY_BUSY, cs.getAvailability());
        assertEquals(100, cs.getStartTimeMillis());
        assertEquals(1000, cs.getEndTimeMillis());
        assertEquals(android.R.drawable.btn_default, cs.getIcon().getResId());
        assertEquals("playing chess", cs.getDescription().toString());
    }
}

