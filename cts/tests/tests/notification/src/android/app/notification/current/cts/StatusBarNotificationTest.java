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
package android.app.notification.current.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.content.Context;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.test.AndroidTestCase;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StatusBarNotificationTest {
    private static final int UID = 123;
    private static final int ID = 146;
    private static final String TAG = "tag";
    private static final long POST_TIME = 316;
    private static final String PKG = "foo.bar";
    private static final String OP_PKG = "optional.abc";

    private StatusBarNotification mSbn;
    private UserHandle mUserHandle;
    private Notification mNotification;
    Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mUserHandle = UserHandle.getUserHandleForUid(UID);
        mNotification = new Notification.Builder(mContext)
                .setContentTitle("foo")
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .build();
        mSbn = new StatusBarNotification(PKG, OP_PKG, ID, TAG, UID, 0, 0, mNotification,
                mUserHandle, POST_TIME);
    }

    @Test
    public void testGetUid() {
        assertEquals(UID, mSbn.getUid());
    }

    @Test
    public void testGetUserId() {
        assertEquals(0, mSbn.getUserId());
    }

    @Test
    public void testGetId() {
        assertEquals(ID, mSbn.getId());
    }

    @Test
    public void testGetUser() {
        assertEquals(mUserHandle, mSbn.getUser());
    }

    @Test
    public void testGetTag() {
        assertEquals(TAG, mSbn.getTag());
    }

    @Test
    public void testGetPostTime() {
        assertEquals(POST_TIME, mSbn.getPostTime());
    }

    @Test
    public void testGetPackageName() {
        assertEquals(PKG, mSbn.getPackageName());
    }

    @Test
    public void testGetOpPkg() {
        assertEquals(OP_PKG, mSbn.getOpPkg());
    }

    @Test
    public void testGetOverride_noOverride() {
        assertNull(mSbn.getOverrideGroupKey());
    }

    @Test
    public void testOverrideGroupKey() {
        assertNull(mSbn.getOverrideGroupKey());
        mSbn.setOverrideGroupKey("override");
        assertEquals("override", mSbn.getOverrideGroupKey());
    }
    
    @Test
    public void testIsClearable_clearable() {
        assertTrue(mSbn.isClearable());
    }

    @Test
    public void testIsClearable_notClearableOngoingEvent() {
        Notification notification = new Notification.Builder(mContext)
                .setContentTitle("foo")
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setOngoing(true)
                .build();
        StatusBarNotification sbn =
                new StatusBarNotification(PKG, OP_PKG, ID, TAG, UID, 0, 0, notification,
                        mUserHandle, POST_TIME);
        assertFalse(sbn.isClearable());
    }

    @Test
    public void testIsGroup_notGroup() {
        assertFalse(mSbn.isGroup());
    }

    @Test
    public void testIsGroup_overrideGroupKey() {
        mSbn.setOverrideGroupKey("foo");
        assertTrue(mSbn.isGroup());
    }

    @Test
    public void testIsGroup_notifGroup() {
        Notification notification = new Notification.Builder(mContext)
                .setContentTitle("foo")
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setGroup("bar")
                .build();
        StatusBarNotification sbn =
                new StatusBarNotification(PKG, OP_PKG, ID, TAG, UID, 0, 0, notification,
                        mUserHandle, POST_TIME);
        assertTrue(sbn.isGroup());
    }

    @Test
    public void testIsGroup_sortKey() {
        Notification notification = new Notification.Builder(mContext)
                .setContentTitle("foo")
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setSortKey("bar")
                .build();
        StatusBarNotification sbn =
                new StatusBarNotification(PKG, OP_PKG, ID, TAG, UID, 0, 0, notification,
                        mUserHandle, POST_TIME);
        assertTrue(sbn.isGroup());
    }

    @Test
    public void testIsOngoing_notOngoing() {
        assertFalse(mSbn.isOngoing());
    }

    @Test
    public void testIsOngoing_ongoingEvent() {
        Notification notification = new Notification.Builder(mContext)
                .setContentTitle("foo")
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setOngoing(true)
                .build();
        StatusBarNotification sbn =
                new StatusBarNotification(PKG, OP_PKG, ID, TAG, UID, 0, 0, notification,
                        mUserHandle, POST_TIME);
        assertTrue(sbn.isOngoing());
    }

    @Test
    public void testGetNotification() {
        assertEquals(mNotification, mSbn.getNotification());
    }

    @Test
    public void testClone() {
        mSbn.setOverrideGroupKey("bar");

        StatusBarNotification clone = mSbn.clone();

        assertEquals(PKG, clone.getPackageName());
        assertEquals(OP_PKG, clone.getOpPkg());
        assertEquals(ID, clone.getId());
        assertEquals(UID, clone.getUid());
        assertEquals(TAG, clone.getTag());
        assertEquals(POST_TIME, clone.getPostTime());
        assertEquals(mUserHandle, clone.getUser());
        assertEquals("bar", clone.getOverrideGroupKey());
        Notification notification = clone.getNotification();
        assertEquals("foo", notification.extras.get(Notification.EXTRA_TITLE));
    }
}
