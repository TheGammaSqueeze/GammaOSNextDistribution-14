/*
 * Copyright (C) 2016 The Android Open Source Project
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Flags;
import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.service.notification.Condition;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ConditionTest {
    Context mContext;
    private final Uri mConditionId = new Uri.Builder().scheme("scheme")
            .authority("authority")
            .appendPath("path")
            .appendPath("test")
            .build();
    private final String mSummary = "summary";
    private final int mState = Condition.STATE_FALSE;
    private final int mSource = Flags.modesApi() ? Condition.SOURCE_USER_ACTION : 0;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Test
    public void testDescribeContents() {
        final int expected = 0;
        Condition condition = new Condition(mConditionId, mSummary, mState);
        assertEquals(expected, condition.describeContents());
    }

    @Test
    public void testConstructor() {
        Condition condition = new Condition(mConditionId, mSummary, mState);
        if (Flags.modesApi()) {
            condition = new Condition(mConditionId, mSummary, mState, mSource);
        }
        assertEquals(mConditionId, condition.id);
        assertEquals(mSummary, condition.summary);
        assertEquals("", condition.line1);
        assertEquals("", condition.line2);
        assertEquals(mState, condition.state);
        assertEquals(-1, condition.icon);
        assertEquals(Condition.FLAG_RELEVANT_ALWAYS, condition.flags);
        if (Flags.modesApi()) {
            assertEquals(mSource, condition.source);
        }
    }

    @Test
    public void testWriteToParcel() {
        Condition original = new Condition(mConditionId, mSummary, mState);
        if (Flags.modesApi()) {
            original = new Condition(mConditionId, mSummary, mState, mSource);
        }
        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Condition copy = new Condition(parcel);
        assertEquals(mConditionId, copy.id);
        assertEquals(mSummary, copy.summary);
        assertEquals("", copy.line1);
        assertEquals("", copy.line2);
        assertEquals(mState, copy.state);
        assertEquals(-1, copy.icon);
        assertEquals(Condition.FLAG_RELEVANT_ALWAYS, copy.flags);
        if (Flags.modesApi()) {
            assertEquals(mSource, copy.source);
        }
    }

    @Test
    public void testCopy() {
        Condition original = new Condition(mConditionId, mSummary, mState);
        if (Flags.modesApi()) {
            original = new Condition(mConditionId, mSummary, mState, mSource);
        }
        Condition copy = original.copy();
        assertEquals(mConditionId, copy.id);
        assertEquals(mSummary, copy.summary);
        assertEquals("", copy.line1);
        assertEquals("", copy.line2);
        assertEquals(mState, copy.state);
        assertEquals(-1, copy.icon);
        assertEquals(Condition.FLAG_RELEVANT_ALWAYS, copy.flags);
        if (Flags.modesApi()) {
            assertEquals(mSource, copy.source);
        }
    }

    @Test
    public void testIsValidId_null() {
        assertFalse(Condition.isValidId(null, null));
    }

    @Test
    public void testIsValidId_noScheme() {
        String pkg = this.getClass().getPackage().toString();
        Uri uri = new Uri.Builder().authority(pkg).build();
        assertFalse(Condition.isValidId(uri, pkg));
    }

    @Test
    public void testIsValidId_wrongAuthority() {
        String pkg = this.getClass().getPackage().toString();
        Uri uri = new Uri.Builder().authority(pkg).scheme(Condition.SCHEME).build();
        assertFalse(Condition.isValidId(uri, "different"));
    }

    @Test
    public void testIsValidId() {
        String pkg = this.getClass().getPackage().toString();
        Uri uri = new Uri.Builder().authority(pkg).scheme(Condition.SCHEME).build();
        assertTrue(Condition.isValidId(uri, pkg));
    }

    @Test
    public void testNewId() {
        assertTrue(Condition.isValidId(
                Condition.newId(mContext).build(), mContext.getPackageName()));
    }

    @Test
    public void testRelevanceToString() {
        assertNotNull(Condition.relevanceToString(Condition.FLAG_RELEVANT_ALWAYS));
    }

    @Test
    public void testSourceDefault() {
        if (!Flags.modesApi()) {
            return;
        }
        Condition condition = new Condition(mConditionId, mSummary, mState);
        assertEquals(Condition.SOURCE_UNKNOWN, condition.source);
    }
}
