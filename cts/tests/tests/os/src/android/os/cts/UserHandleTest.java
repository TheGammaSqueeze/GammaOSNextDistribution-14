/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.os.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.os.Parcel;
import android.os.Process;
import android.os.UserHandle;

import org.junit.Test;

public class UserHandleTest {
    private static final int TEST_APP_ID = 1234;

    private static void assertSameUserHandle(int userId) {
        assertSame(UserHandle.of(userId), UserHandle.of(userId));
    }

    @Test
    public void testOf() {
        // We test them separately since technically it's possible for these constants to have
        // different values than the AOSP contains and are out of the [-1000, 1000] range.
        assertSameUserHandle(UserHandle.USER_SYSTEM);
        assertSameUserHandle(UserHandle.USER_ALL);
        assertSameUserHandle(UserHandle.USER_NULL);

        for (int userId = -1000; userId <= 1000; userId++) {
            assertEquals(userId, UserHandle.of(userId).getIdentifier());

            // Because of the cache, this should always be true.
            assertSameUserHandle(userId);
        }
    }

    private static void assertParcel(int userId) {
        Parcel p = Parcel.obtain();
        p.writeParcelable(UserHandle.of(userId), 0);
        p.setDataPosition(0);

        UserHandle read = p.readParcelable(UserHandleTest.class.getClassLoader());

        assertEquals(userId, read.getIdentifier());

        p.recycle();
    }

    @Test
    public void testParcel() {
        for (int i = -1000; i < 100; i++) {
            assertParcel(i);
        }
    }

    @Test
    public void testGetUid() {
        assertEquals(
                UserHandle.getUid(UserHandle.USER_ALL, TEST_APP_ID),
                UserHandle.ALL.getUid(TEST_APP_ID));
        assertEquals(
                UserHandle.getUid(UserHandle.USER_SYSTEM, TEST_APP_ID),
                UserHandle.SYSTEM.getUid(TEST_APP_ID));
        assertEquals(
                UserHandle.ALL.getUid(TEST_APP_ID),
                UserHandle.getUid(UserHandle.ALL.getIdentifier(), TEST_APP_ID));
        assertEquals(
                UserHandle.SYSTEM.getUid(TEST_APP_ID),
                UserHandle.getUid(UserHandle.SYSTEM.getIdentifier(), TEST_APP_ID));
    }

    @Test
    public void testGetSharedAppGid() {
        assertEquals(UserHandle.getSharedAppGid(Process.ROOT_UID), Process.ROOT_UID);
        assertEquals(UserHandle.getSharedAppGid(Process.SYSTEM_UID), Process.SYSTEM_UID);
        assertEquals(UserHandle.getSharedAppGid(Process.FIRST_APPLICATION_UID), 50000);
        assertEquals(UserHandle.getSharedAppGid(Process.FIRST_APPLICATION_UID + 1000), 51000);
        assertEquals(UserHandle.getSharedAppGid(android.os.Process.LAST_APPLICATION_UID + 1), -1);
    }

    private void assertConsistent(int userId, int appId) {
        final int uid = UserHandle.getUid(userId, appId);
        assertEquals(userId, UserHandle.getUserId(uid));
        assertEquals(appId, UserHandle.getAppId(uid));
    }

    @Test
    public void testConsistent() throws Exception {
        assertConsistent(UserHandle.SYSTEM.getIdentifier(), Process.SYSTEM_UID);
        assertConsistent(UserHandle.SYSTEM.getIdentifier(), Process.FIRST_APPLICATION_UID);
        assertConsistent(UserHandle.SYSTEM.getIdentifier(), Process.LAST_APPLICATION_UID);
    }
}
