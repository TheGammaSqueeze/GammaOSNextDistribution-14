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

package android.app.notification.current.cts;

import static android.app.Notification.CATEGORY_CALL;
import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.app.NotificationManager.INTERRUPTION_FILTER_ALL;

import static junit.framework.TestCase.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.Notification.CallStyle;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.app.role.RoleManager;
import android.app.stubs.BubbledActivity;
import android.app.stubs.R;
import android.app.stubs.shared.NotificationHelper;
import android.app.stubs.shared.NotificationHelper.SEARCH_TYPE;
import android.app.stubs.shared.TestNotificationAssistant;
import android.app.stubs.shared.TestNotificationListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Telephony;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.ThrowingRunnable;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/* Base class for NotificationManager tests. Handles some of the common set up logic for tests. */
public abstract class BaseNotificationManagerTest {

    static final String STUB_PACKAGE_NAME = "android.app.stubs";
    protected static final String NOTIFICATION_CHANNEL_ID = "NotificationManagerTest";
    protected static final String SHARE_SHORTCUT_CATEGORY =
            "android.app.stubs.SHARE_SHORTCUT_CATEGORY";
    protected static final String SHARE_SHORTCUT_ID = "shareShortcut";
    // Constants for GetResultActivity and return codes from MatchesCallFilterTestActivity
    // the permitted/not permitted values need to stay the same as in the test activity.
    protected static final int REQUEST_CODE = 42;
    protected static final String TEST_APP = "com.android.test.notificationapp";

    private static final String TAG = BaseNotificationManagerTest.class.getSimpleName();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    protected Context mContext;
    protected PackageManager mPackageManager;
    protected AudioManager mAudioManager;
    protected RoleManager mRoleManager;
    protected NotificationManager mNotificationManager;
    protected ActivityManager mActivityManager;
    protected TestNotificationAssistant mAssistant;
    protected TestNotificationListener mListener;
    protected Instrumentation mInstrumentation;
    protected NotificationHelper mNotificationHelper;

    @Before
    public void baseSetUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mNotificationManager = mContext.getSystemService(NotificationManager.class);
        mNotificationHelper = new NotificationHelper(mContext);
        // clear the deck so that our getActiveNotifications results are predictable
        mNotificationManager.cancelAll();

        assertEquals("Previous test left system in a bad state ",
                0, mNotificationManager.getActiveNotifications().length);

        mNotificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "name", IMPORTANCE_DEFAULT));
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mPackageManager = mContext.getPackageManager();
        mAudioManager = mContext.getSystemService(AudioManager.class);
        mRoleManager = mContext.getSystemService(RoleManager.class);

        // ensure listener access isn't allowed before test runs (other tests could put
        // TestListener in an unexpected state)
        mNotificationHelper.disableListener(STUB_PACKAGE_NAME);
        mNotificationHelper.disableAssistant(STUB_PACKAGE_NAME);
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        toggleNotificationPolicyAccess(mContext.getPackageName(), mInstrumentation, true);
        mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALL);
        toggleNotificationPolicyAccess(mContext.getPackageName(), mInstrumentation, false);

        // Ensure that the tests are exempt from global service-related rate limits
        setEnableServiceNotificationRateLimit(false);
    }

    @After
    public void baseTearDown() throws Exception {
        setEnableServiceNotificationRateLimit(true);

        mNotificationManager.cancelAll();

        assertExpectedDndState(INTERRUPTION_FILTER_ALL);

        List<NotificationChannel> channels = mNotificationManager.getNotificationChannels();
        // Delete all channels.
        for (NotificationChannel nc : channels) {
            if (NotificationChannel.DEFAULT_CHANNEL_ID.equals(nc.getId())) {
                continue;
            }
            mNotificationManager.deleteNotificationChannel(nc.getId());
        }

        // Unsuspend package if it was suspended in the test
        suspendPackage(mContext.getPackageName(), mInstrumentation, false);

        mNotificationHelper.disableListener(STUB_PACKAGE_NAME);
        mNotificationHelper.disableAssistant(STUB_PACKAGE_NAME);
        toggleNotificationPolicyAccess(mContext.getPackageName(), mInstrumentation, false);

        List<NotificationChannelGroup> groups = mNotificationManager.getNotificationChannelGroups();
        // Delete all groups.
        for (NotificationChannelGroup ncg : groups) {
            mNotificationManager.deleteNotificationChannelGroup(ncg.getId());
        }
    }

    /**
     * Runs a {@link ThrowingRunnable} as the Shell, while adopting SystemUI's permission (as
     * checked by {@code NotificationManagerService#isCallerSystemOrSystemUi}).
     */
    protected static void runAsSystemUi(@NonNull ThrowingRunnable runnable) {
        SystemUtil.runWithShellPermissionIdentity(runnable, Manifest.permission.STATUS_BAR_SERVICE);
    }

    /**
     * Calls a {@link Callable} as the Shell, while adopting SystemUI's permission (as checked by
     * {@code NotificationManagerService#isCallerSystemOrSystemUi}).
     */
    protected static <T> T callAsSystemUi(@NonNull Callable<T> callable) {
        try {
            return SystemUtil.callWithShellPermissionIdentity(callable,
                    Manifest.permission.STATUS_BAR_SERVICE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("InlineMeInliner")
    protected void setUpNotifListener() {
        try {
            mListener = mNotificationHelper.enableListener(STUB_PACKAGE_NAME);
            assertNotNull(mListener);
            mListener.resetData();
        } catch (Exception e) {
            Log.e(TAG, "error in setUpNotifListener", e);
        }
    }

    protected void toggleExternalListenerAccess(ComponentName listenerComponent, boolean on)
            throws IOException {
        String command = " cmd notification " + (on ? "allow_listener " : "disallow_listener ")
                + listenerComponent.flattenToString();
        mNotificationHelper.runCommand(command, InstrumentationRegistry.getInstrumentation());
    }

    protected void assertExpectedDndState(int expectedState) throws Exception {
        int tries = 3;
        for (int i = tries; i >= 0; i--) {
            if (expectedState
                    == mNotificationManager.getCurrentInterruptionFilter()) {
                break;
            }
            Thread.sleep(100);
        }

        assertEquals(expectedState, mNotificationManager.getCurrentInterruptionFilter());
    }

    /** Creates a dynamic, longlived, sharing shortcut. Call {@link #deleteShortcuts()} after. */
    protected void createDynamicShortcut() {
        Person person = new Person.Builder()
                .setBot(false)
                .setIcon(Icon.createWithResource(mContext, R.drawable.icon_black))
                .setName("BubbleBot")
                .setImportant(true)
                .build();

        Set<String> categorySet = new ArraySet<>();
        categorySet.add(SHARE_SHORTCUT_CATEGORY);
        Intent shortcutIntent = new Intent(mContext, BubbledActivity.class);
        shortcutIntent.setAction(Intent.ACTION_VIEW);

        ShortcutInfo shortcut = new ShortcutInfo.Builder(mContext, SHARE_SHORTCUT_ID)
                .setShortLabel(SHARE_SHORTCUT_ID)
                .setIcon(Icon.createWithResource(mContext, R.drawable.icon_black))
                .setIntent(shortcutIntent)
                .setPerson(person)
                .setCategories(categorySet)
                .setLongLived(true)
                .build();

        ShortcutManager scManager = mContext.getSystemService(ShortcutManager.class);
        scManager.addDynamicShortcuts(Arrays.asList(shortcut));
    }

    protected void deleteShortcuts() {
        ShortcutManager scManager = mContext.getSystemService(ShortcutManager.class);
        scManager.removeAllDynamicShortcuts();
        scManager.removeLongLivedShortcuts(Collections.singletonList(SHARE_SHORTCUT_ID));
    }

    /**
     * Notification fulfilling conversation policy; for the shortcut to be valid
     * call {@link #createDynamicShortcut()}
     */
    protected Notification.Builder getConversationNotification() {
        Person person = new Person.Builder()
                .setName("bubblebot")
                .build();
        return new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("foo")
                .setShortcutId(SHARE_SHORTCUT_ID)
                .setStyle(new Notification.MessagingStyle(person)
                        .setConversationTitle("Bubble Chat")
                        .addMessage("Hello?",
                                SystemClock.currentThreadTimeMillis() - 300000, person)
                        .addMessage("Is it me you're looking for?",
                                SystemClock.currentThreadTimeMillis(), person)
                )
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
    }

    protected Notification.Builder getCallStyleNotification(final int id) {
        Person person = new Person.Builder().setName("Test name").build();
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0,
            new Intent().setPackage(mContext.getPackageName()), PendingIntent.FLAG_MUTABLE);
        CallStyle cs = CallStyle.forIncomingCall(person, pendingIntent, pendingIntent);

        return new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.black)
                .setContentTitle("notify#" + id)
                .setContentText("This is #" + id + "notification  ")
                .setStyle(cs);
    }

    protected void cancelAndPoll(int id) {
        mNotificationManager.cancel(id);

        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            // pass
        }
        assertTrue(mNotificationHelper.isNotificationGone(id, SEARCH_TYPE.APP));
    }

    protected void sendNotification(final int id,
            final int icon) throws Exception {
        sendNotification(id, null, icon);
    }

    protected void sendNotification(final int id,
            String groupKey, final int icon) {
        sendNotification(id, groupKey, icon, false, null);
    }

    protected void sendNotification(final int id,
            String groupKey, final int icon,
            boolean isCall, Uri phoneNumber) {
        final Intent intent = new Intent(Intent.ACTION_MAIN, Telephony.Threads.CONTENT_URI);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setAction(Intent.ACTION_MAIN);
        intent.setPackage(mContext.getPackageName());

        final PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent,
                PendingIntent.FLAG_MUTABLE);
        Notification.Builder nb = new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(icon)
                .setWhen(System.currentTimeMillis())
                .setContentTitle("notify#" + id)
                .setContentText("This is #" + id + "notification  ")
                .setContentIntent(pendingIntent)
                .setGroup(groupKey);

        if (isCall) {
            nb.setCategory(CATEGORY_CALL);
            if (phoneNumber != null) {
                Bundle extras = new Bundle();
                ArrayList<Person> pList = new ArrayList<>();
                pList.add(new Person.Builder().setUri(phoneNumber.toString()).build());
                extras.putParcelableArrayList(Notification.EXTRA_PEOPLE_LIST, pList);
                nb.setExtras(extras);
            }
        }

        final Notification notification = nb.build();
        mNotificationManager.notify(id, notification);

        assertNotNull(mNotificationHelper.findPostedNotification(null, id, SEARCH_TYPE.APP));
    }

    protected void setEnableServiceNotificationRateLimit(boolean enable) throws IOException {
        String command = "cmd activity fgs-notification-rate-limit "
                + (enable ? "enable" : "disable");

        mNotificationHelper.runCommand(command, InstrumentationRegistry.getInstrumentation());
    }

    protected void suspendPackage(String packageName,
            Instrumentation instrumentation, boolean suspend) throws IOException {
        int userId = mContext.getUserId();
        String command = " cmd package " + (suspend ? "suspend " : "unsuspend ")
                + "--user " + userId + " " + packageName;

        mNotificationHelper.runCommand(command, instrumentation);
        AmUtils.waitForBroadcastBarrier();
    }

    protected void toggleNotificationPolicyAccess(String packageName,
            Instrumentation instrumentation, boolean on) throws IOException {

        String command = " cmd notification " + (on ? "allow_dnd " : "disallow_dnd ") + packageName;

        mNotificationHelper.runCommand(command, instrumentation);
        AmUtils.waitForBroadcastBarrier();

        NotificationManager nm = mContext.getSystemService(NotificationManager.class);
        assertEquals("Notification Policy Access Grant is "
                + nm.isNotificationPolicyAccessGranted() + " not " + on + " for "
                + packageName, on, nm.isNotificationPolicyAccessGranted());
    }
}
