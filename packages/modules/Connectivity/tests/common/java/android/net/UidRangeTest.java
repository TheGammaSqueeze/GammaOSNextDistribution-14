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

package android.net;

import static android.os.UserHandle.MIN_SECONDARY_USER_ID;
import static android.os.UserHandle.SYSTEM;
import static android.os.UserHandle.USER_SYSTEM;
import static android.os.UserHandle.getUid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Build;
import android.os.UserHandle;
import android.util.ArraySet;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.testutils.ConnectivityModuleTest;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
@SmallTest
@ConnectivityModuleTest
public class UidRangeTest {

    /*
     * UidRange is no longer passed to netd. UID ranges between the framework and netd are passed as
     * UidRangeParcel objects.
     */

    @Rule
    public final DevSdkIgnoreRule mIgnoreRule = new DevSdkIgnoreRule();

    @Test
    public void testSingleItemUidRangeAllowed() {
        new UidRange(123, 123);
        new UidRange(0, 0);
        new UidRange(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Test
    public void testNegativeUidsDisallowed() {
        try {
            new UidRange(-2, 100);
            fail("Exception not thrown for negative start UID");
        } catch (IllegalArgumentException expected) {
        }

        try {
            new UidRange(-200, -100);
            fail("Exception not thrown for negative stop UID");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testStopLessThanStartDisallowed() {
        final int x = 4195000;
        try {
            new UidRange(x, x - 1);
            fail("Exception not thrown for negative-length UID range");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testGetStartAndEndUser() throws Exception {
        final UidRange uidRangeOfPrimaryUser = new UidRange(
                getUid(USER_SYSTEM, 10000), getUid(USER_SYSTEM, 10100));
        final UidRange uidRangeOfSecondaryUser = new UidRange(
                getUid(MIN_SECONDARY_USER_ID, 10000), getUid(MIN_SECONDARY_USER_ID, 10100));
        assertEquals(USER_SYSTEM, uidRangeOfPrimaryUser.getStartUser());
        assertEquals(USER_SYSTEM, uidRangeOfPrimaryUser.getEndUser());
        assertEquals(MIN_SECONDARY_USER_ID, uidRangeOfSecondaryUser.getStartUser());
        assertEquals(MIN_SECONDARY_USER_ID, uidRangeOfSecondaryUser.getEndUser());

        final UidRange uidRangeForDifferentUsers = new UidRange(
                getUid(USER_SYSTEM, 10000), getUid(MIN_SECONDARY_USER_ID, 10100));
        assertEquals(USER_SYSTEM, uidRangeOfPrimaryUser.getStartUser());
        assertEquals(MIN_SECONDARY_USER_ID, uidRangeOfSecondaryUser.getEndUser());
    }

    @Test @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testCreateForUser() throws Exception {
        final UidRange uidRangeOfPrimaryUser = UidRange.createForUser(SYSTEM);
        final UidRange uidRangeOfSecondaryUser = UidRange.createForUser(
                UserHandle.of(USER_SYSTEM + 1));
        assertTrue(uidRangeOfPrimaryUser.stop < uidRangeOfSecondaryUser.start);
        assertEquals(USER_SYSTEM, uidRangeOfPrimaryUser.getStartUser());
        assertEquals(USER_SYSTEM, uidRangeOfPrimaryUser.getEndUser());
        assertEquals(USER_SYSTEM + 1, uidRangeOfSecondaryUser.getStartUser());
        assertEquals(USER_SYSTEM + 1, uidRangeOfSecondaryUser.getEndUser());
    }

    private static void assertSameUids(@NonNull final String msg, @Nullable final Set<UidRange> s1,
            @Nullable final Set<UidRange> s2) {
        assertTrue(msg + " : " + s1 + " unexpectedly different from " + s2,
                UidRange.hasSameUids(s1, s2));
    }

    private static void assertDifferentUids(@NonNull final String msg,
            @Nullable final Set<UidRange> s1, @Nullable final Set<UidRange> s2) {
        assertFalse(msg + " : " + s1 + " unexpectedly equal to " + s2,
                UidRange.hasSameUids(s1, s2));
    }

    // R doesn't have UidRange.hasSameUids, but since S has the module, it does have hasSameUids.
    @Test @IgnoreUpTo(Build.VERSION_CODES.R)
    public void testHasSameUids() {
        final UidRange uids1 = new UidRange(1, 100);
        final UidRange uids2 = new UidRange(3, 300);
        final UidRange uids3 = new UidRange(1, 1000);
        final UidRange uids4 = new UidRange(800, 1000);

        assertSameUids("null <=> null", null, null);
        final Set<UidRange> set1 = new ArraySet<>();
        assertDifferentUids("empty <=> null", set1, null);
        final Set<UidRange> set2 = new ArraySet<>();
        set1.add(uids1);
        assertDifferentUids("uids1 <=> null", set1, null);
        assertDifferentUids("null <=> uids1", null, set1);
        assertDifferentUids("uids1 <=> empty", set1, set2);
        set2.add(uids1);
        assertSameUids("uids1 <=> uids1", set1, set2);
        set1.add(uids2);
        assertDifferentUids("uids1,2 <=> uids1", set1, set2);
        set1.add(uids3);
        assertDifferentUids("uids1,2,3 <=> uids1", set1, set2);
        set2.add(uids3);
        assertDifferentUids("uids1,2,3 <=> uids1,3", set1, set2);
        set2.add(uids2);
        assertSameUids("uids1,2,3 <=> uids1,2,3", set1, set2);
        set1.remove(uids2);
        assertDifferentUids("uids1,3 <=> uids1,2,3", set1, set2);
        set1.add(uids4);
        assertDifferentUids("uids1,3,4 <=> uids1,2,3", set1, set2);
        set2.add(uids4);
        assertDifferentUids("uids1,3,4 <=> uids1,2,3,4", set1, set2);
        assertDifferentUids("uids1,3,4 <=> null", set1, null);
        set2.remove(uids2);
        assertSameUids("uids1,3,4 <=> uids1,3,4", set1, set2);
        set2.remove(uids1);
        assertDifferentUids("uids1,3,4 <=> uids3,4", set1, set2);
        set2.remove(uids3);
        assertDifferentUids("uids1,3,4 <=> uids4", set1, set2);
        set2.remove(uids4);
        assertDifferentUids("uids1,3,4 <=> empty", set1, set2);
        assertDifferentUids("null <=> empty", null, set2);
        assertSameUids("empty <=> empty", set2, new ArraySet<>());
    }
}
