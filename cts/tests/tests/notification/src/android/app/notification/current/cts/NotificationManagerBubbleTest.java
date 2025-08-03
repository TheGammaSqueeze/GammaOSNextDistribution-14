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

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.Manifest.permission.REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL;
import static android.Manifest.permission.REVOKE_RUNTIME_PERMISSIONS;
import static android.app.Notification.FLAG_BUBBLE;
import static android.app.NotificationManager.BUBBLE_PREFERENCE_ALL;
import static android.app.NotificationManager.BUBBLE_PREFERENCE_NONE;
import static android.app.NotificationManager.BUBBLE_PREFERENCE_SELECTED;
import static android.app.stubs.BubbledActivity.EXTRA_LOCUS_ID;
import static android.app.stubs.BubblesTestService.EXTRA_TEST_CASE;
import static android.app.stubs.BubblesTestService.TEST_CALL;
import static android.app.stubs.BubblesTestService.TEST_MESSAGING;
import static android.app.stubs.SendBubbleActivity.BUBBLE_NOTIF_ID;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_DOCUMENT;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.stubs.BubbledActivity;
import android.app.stubs.BubblesTestService;
import android.app.stubs.R;
import android.app.stubs.SendBubbleActivity;
import android.app.stubs.shared.NotificationHelper.SEARCH_TYPE;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.LocusId;
import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.permission.PermissionManager;
import android.permission.cts.PermissionUtils;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests bubbles related logic in NotificationManager.
 */
@RunWith(AndroidJUnit4.class)
public class NotificationManagerBubbleTest extends BaseNotificationManagerTest {

    private static final String TAG = NotificationManagerBubbleTest.class.getSimpleName();

    private static final String STUB_PACKAGE_NAME = "android.app.stubs";

    // use a value of 10000 for consistency with other CTS tests (see
    // android.server.wm.intentLaunchRunner#ACTIVITY_LAUNCH_TIMEOUT)
    private static final int ACTIVITY_LAUNCH_TIMEOUT = 10000;

    private BroadcastReceiver mBubbleBroadcastReceiver;
    private boolean mBubblesEnabledSettingToRestore;

    @Before
    public void setUp() throws Exception {
        PermissionUtils.grantPermission(STUB_PACKAGE_NAME, POST_NOTIFICATIONS);
        PermissionUtils.grantPermission(mContext.getPackageName(), POST_NOTIFICATIONS);

        // This setting is forced on / off for certain tests, save it & restore what's on the
        // device after tests are run
        mBubblesEnabledSettingToRestore = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.NOTIFICATION_BUBBLES) == 1;

        // ensure listener access isn't allowed before test runs (other tests could put
        // TestListener in an unexpected state)
        mNotificationHelper.disableListener(STUB_PACKAGE_NAME);

        // delay between tests so notifications aren't dropped by the rate limiter
        sleep();
    }

    @After
    public void tearDown() throws Exception {

        // Restore bubbles setting
        setBubblesGlobal(mBubblesEnabledSettingToRestore);

        // Use test API to prevent PermissionManager from killing the test process when revoking
        // permission.
        SystemUtil.runWithShellPermissionIdentity(
                () -> mContext.getSystemService(PermissionManager.class)
                        .revokePostNotificationPermissionWithoutKillForTest(
                                mContext.getPackageName(),
                                android.os.Process.myUserHandle().getIdentifier()),
                REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL,
                REVOKE_RUNTIME_PERMISSIONS);
        PermissionUtils.revokePermission(STUB_PACKAGE_NAME, POST_NOTIFICATIONS);
    }

    private boolean isBubblesFeatureSupported() {
        // These do not support bubbles.
        return (!mActivityManager.isLowRamDevice() && !FeatureUtil.isWatch()
                && !FeatureUtil.isAutomotive() && !FeatureUtil.isTV()
                && mContext.getResources().getBoolean(Resources.getSystem().getIdentifier(
                        "config_supportsBubble", "bool", "android")));
    }

    private void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
        }
    }

    private void sendAndVerifyBubble(final int id, Notification.Builder builder,
            boolean shouldBeBubble) {
        setUpNotifListener();

        Notification notif = builder.build();
        mNotificationManager.notify(id, notif);

        verifyNotificationBubbleState(id, shouldBeBubble);
    }

    private Notification.Builder getDefaultNotifBuilder(int id) {
        return new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.black)
                .setWhen(System.currentTimeMillis())
                .setContentTitle("notify#" + id)
                .setContentText("This is #" + id + "notification  ")
                .setContentIntent(getDefaultNotifPendingIntent());
    }

    private PendingIntent getDefaultNotifPendingIntent() {
        final Intent intent = new Intent(mContext, BubbledActivity.class);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setAction(Intent.ACTION_MAIN);
        return PendingIntent.getActivity(mContext, 0, intent,
                PendingIntent.FLAG_MUTABLE_UNAUDITED);
    }

    private Notification.BubbleMetadata getDefaultBubbleMetadata() {
        return new Notification.BubbleMetadata.Builder(
                getDefaultNotifPendingIntent(),
                Icon.createWithResource(mContext, R.drawable.black))
                .build();
    }

    /**
     * Make sure {@link #setUpNotifListener()} is called prior to sending the notif and verifying
     * in this method.
     */
    private void verifyNotificationBubbleState(int id, boolean shouldBeBubble) {
        StatusBarNotification sbn =
                mNotificationHelper.findPostedNotification(null, id, SEARCH_TYPE.POSTED);
        assertNotNull("Cannot find notification id=" + id, sbn);
        assertEquals("Unexpected bubble state for id=" + id + ", expected " + shouldBeBubble,
                shouldBeBubble, ((sbn.getNotification().flags & FLAG_BUBBLE) != 0));
    }

    private void setBubblesGlobal(boolean enabled) {
        SystemUtil.runWithShellPermissionIdentity(() ->
                Settings.Secure.putInt(mContext.getContentResolver(),
                        Settings.Secure.NOTIFICATION_BUBBLES, enabled ? 1 : 0));
    }

    private void setBubblesAppPref(int pref) throws Exception {
        int userId = mContext.getUser().getIdentifier();
        String pkg = mContext.getPackageName();
        String command = " cmd notification set_bubbles " + pkg
                + " " + Integer.toString(pref)
                + " " + userId;
        mNotificationHelper.runCommand(command, InstrumentationRegistry.getInstrumentation());
    }

    private void setBubblesChannelAllowed(boolean allowed) throws Exception {
        int userId = mContext.getUser().getIdentifier();
        String pkg = mContext.getPackageName();
        String command = " cmd notification set_bubbles_channel " + pkg
                + " " + NOTIFICATION_CHANNEL_ID
                + " " + allowed
                + " " + userId;
        mNotificationHelper.runCommand(command, InstrumentationRegistry.getInstrumentation());
    }

    private void allowAllNotificationsToBubble() throws Exception {
        setBubblesGlobal(true);
        setBubblesAppPref(1 /* all */);
        setBubblesChannelAllowed(true);
        sleep(); // wait for ranking update
    }

    /**
     * Starts an activity that is able to send a bubble; also handles unlocking the device.
     * Any tests that use this method should be sure to call {@link #cleanupSendBubbleActivity()}
     * to unregister the related broadcast receiver.
     *
     * @return the SendBubbleActivity that was opened.
     */
    private SendBubbleActivity startSendBubbleActivity() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        mBubbleBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                latch.countDown();
            }
        };
        IntentFilter filter = new IntentFilter(SendBubbleActivity.BUBBLE_ACTIVITY_OPENED);
        mContext.registerReceiver(mBubbleBroadcastReceiver, filter, Context.RECEIVER_EXPORTED);

        // Start & get the activity
        Class clazz = SendBubbleActivity.class;

        Instrumentation.ActivityResult result =
                new Instrumentation.ActivityResult(0, new Intent());
        Instrumentation.ActivityMonitor monitor =
                new Instrumentation.ActivityMonitor(clazz.getName(), result, false);
        InstrumentationRegistry.getInstrumentation().addMonitor(monitor);

        Intent i = new Intent(mContext, SendBubbleActivity.class);
        i.setFlags(FLAG_ACTIVITY_NEW_TASK);
        InstrumentationRegistry.getInstrumentation().startActivitySync(i);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        SendBubbleActivity sendBubbleActivity = (SendBubbleActivity) monitor.waitForActivity();

        // Make sure device is unlocked
        ensureDeviceUnlocked(sendBubbleActivity);
        latch.await(500, TimeUnit.MILLISECONDS);

        return sendBubbleActivity;
    }

    private Instrumentation.ActivityMonitor startBubbledActivityMonitor() {
        Class<BubbledActivity> clazz = BubbledActivity.class;
        Instrumentation.ActivityResult result =
                new Instrumentation.ActivityResult(0, new Intent());
        Instrumentation.ActivityMonitor monitor =
                new Instrumentation.ActivityMonitor(clazz.getName(), result, false);
        InstrumentationRegistry.getInstrumentation().addMonitor(monitor);
        return monitor;
    }

    private BubbledActivity startBubbleActivity(int id) {
        return startBubbleActivity(id, true /* addLocusId */);
    }

    /**
     * Starts the same activity that is in the bubble produced by this activity.
     */
    private BubbledActivity startBubbleActivity(int id, boolean addLocusId) {
        Instrumentation.ActivityMonitor monitor = startBubbledActivityMonitor();

        final Intent intent = new Intent(mContext, BubbledActivity.class);
        // Clear any previous instance of this activity
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        if (addLocusId) {
            intent.putExtra(EXTRA_LOCUS_ID, String.valueOf(id));
        }

        InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        BubbledActivity bubbledActivity = (BubbledActivity) monitor.waitForActivity();
        ensureDeviceUnlocked(bubbledActivity);
        return bubbledActivity;
    }

    /**
     * Make sure device is unlocked so the activity can become visible
     */
    private void ensureDeviceUnlocked(Activity activity) {
        // Make sure device is unlocked
        KeyguardManager keyguardManager = mContext.getSystemService(KeyguardManager.class);
        if (keyguardManager.isKeyguardLocked()) {
            CountDownLatch latch = new CountDownLatch(1);
            keyguardManager.requestDismissKeyguard(activity,
                    new KeyguardManager.KeyguardDismissCallback() {
                        @Override
                        public void onDismissSucceeded() {
                            latch.countDown();
                        }
                    });
            try {
                latch.await(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void cleanupSendBubbleActivity() {
        mContext.unregisterReceiver(mBubbleBroadcastReceiver);
    }

    @Test
    public void testCanBubble_ranking() throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }

        // turn on bubbles globally
        setBubblesGlobal(true);
        sleep();

        assertEquals(1, Settings.Secure.getInt(
                mContext.getContentResolver(), Settings.Secure.NOTIFICATION_BUBBLES));

        mListener = mNotificationHelper.enableListener(STUB_PACKAGE_NAME);
        assertNotNull(mListener);

        sendNotification(1, R.drawable.black);
        // wait for notification listener to receive notification
        mNotificationHelper.findPostedNotification(null, 1, SEARCH_TYPE.POSTED);
        NotificationListenerService.RankingMap rankingMap = mListener.mRankingMap;
        NotificationListenerService.Ranking outRanking =
                new NotificationListenerService.Ranking();
        for (String key : rankingMap.getOrderedKeys()) {
            if (key.contains(mListener.getPackageName())) {
                rankingMap.getRanking(key, outRanking);
                // by default nothing can bubble
                assertFalse(outRanking.canBubble());
            }
        }

        // turn off bubbles globally
        setBubblesGlobal(false);
        sleep();

        rankingMap = mListener.mRankingMap;
        outRanking = new NotificationListenerService.Ranking();
        for (String key : rankingMap.getOrderedKeys()) {
            if (key.contains(mListener.getPackageName())) {
                rankingMap.getRanking(key, outRanking);
                assertFalse(outRanking.canBubble());
            }
        }

        mListener.resetData();
    }

    @Test
    public void testAreBubblesAllowed_appNone() throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        setBubblesAppPref(BUBBLE_PREFERENCE_NONE);
        sleep();
        assertFalse(mNotificationManager.areBubblesAllowed());
    }

    @Test
    public void testAreBubblesAllowed_appSelected() throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        setBubblesAppPref(BUBBLE_PREFERENCE_SELECTED);
        sleep();
        assertFalse(mNotificationManager.areBubblesAllowed());
    }

    @Test
    public void testAreBubblesAllowed_appAll() throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        setBubblesAppPref(BUBBLE_PREFERENCE_ALL);
        sleep();
        assertTrue(mNotificationManager.areBubblesAllowed());
    }

    @Test
    public void testGetBubblePreference_appNone() throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        setBubblesAppPref(BUBBLE_PREFERENCE_NONE);
        sleep();
        assertEquals(BUBBLE_PREFERENCE_NONE, mNotificationManager.getBubblePreference());
    }

    @Test
    public void testGetBubblePreference_appSelected() throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        setBubblesAppPref(BUBBLE_PREFERENCE_SELECTED);
        sleep();
        assertEquals(BUBBLE_PREFERENCE_SELECTED, mNotificationManager.getBubblePreference());
    }

    @Test
    public void testGetBubblePreference_appAll() throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        setBubblesAppPref(BUBBLE_PREFERENCE_ALL);
        sleep();
        assertEquals(BUBBLE_PREFERENCE_ALL, mNotificationManager.getBubblePreference());
    }

    @Test
    public void testAreBubblesEnabled() {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        setBubblesGlobal(true);
        sleep();
        assertTrue(mNotificationManager.areBubblesEnabled());
    }

    @Test
    public void testAreBubblesEnabled_false() {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        setBubblesGlobal(false);
        sleep();
        assertFalse(mNotificationManager.areBubblesEnabled());
    }

    @Test
    public void testNotificationManagerBubblePolicy_flag_intentBubble()
            throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            allowAllNotificationsToBubble();
            createDynamicShortcut();

            Notification.Builder nb = getConversationNotification();
            nb.setBubbleMetadata(getDefaultBubbleMetadata());
            boolean shouldBeBubble = !mActivityManager.isLowRamDevice();
            sendAndVerifyBubble(1, nb, shouldBeBubble);
        } finally {
            deleteShortcuts();
        }
    }

    @Test
    public void testNotificationManagerBubblePolicy_noFlag_service()
            throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        Intent serviceIntent = new Intent(mContext, BubblesTestService.class);
        serviceIntent.putExtra(EXTRA_TEST_CASE, TEST_MESSAGING);
        try {
            allowAllNotificationsToBubble();

            createDynamicShortcut();
            setUpNotifListener();

            mContext.startService(serviceIntent);

            // No services in R (allowed in Q)
            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, false /* shouldBeBubble */);
        } finally {
            deleteShortcuts();
            mContext.stopService(serviceIntent);
        }
    }

    @Test
    public void testNotificationManagerBubblePolicy_noFlag_phonecall()
            throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        Intent serviceIntent = new Intent(mContext, BubblesTestService.class);
        serviceIntent.putExtra(EXTRA_TEST_CASE, TEST_CALL);

        try {
            allowAllNotificationsToBubble();

            createDynamicShortcut();
            setUpNotifListener();

            mContext.startService(serviceIntent);

            // No phonecalls in R (allowed in Q)
            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, false /* shouldBeBubble */);
        } finally {
            deleteShortcuts();
            mContext.stopService(serviceIntent);
        }
    }

    @Test
    public void testNotificationManagerBubblePolicy_noFlag_foreground() throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            allowAllNotificationsToBubble();

            createDynamicShortcut();
            setUpNotifListener();

            // Start & get the activity
            SendBubbleActivity a = startSendBubbleActivity();
            // Send a bubble that doesn't fulfill policy from foreground
            a.sendInvalidBubble(BUBBLE_NOTIF_ID, false /* autoExpand */);

            // No foreground bubbles that don't fulfill policy in R (allowed in Q)
            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, false /* shouldBeBubble */);
        } finally {
            deleteShortcuts();
            cleanupSendBubbleActivity();
        }
    }

    @Test
    public void testNotificationManagerBubble_checkActivityFlagsDocumentLaunchMode()
            throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            allowAllNotificationsToBubble();

            createDynamicShortcut();
            setUpNotifListener();

            // make ourselves foreground so we can auto-expand the bubble & check the intent flags
            SendBubbleActivity a = startSendBubbleActivity();

            // Prep to find bubbled activity
            Instrumentation.ActivityMonitor monitor = startBubbledActivityMonitor();

            a.sendBubble(BUBBLE_NOTIF_ID, true /* autoExpand */, false /* suppressNotif */);

            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, true /* shouldBeBubble */);

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            BubbledActivity activity = (BubbledActivity) monitor.waitForActivityWithTimeout(
                    ACTIVITY_LAUNCH_TIMEOUT);
            assertNotNull(String.format(
                    "Failed to detect BubbleActivity after %d ms", ACTIVITY_LAUNCH_TIMEOUT),
                    activity);
            assertTrue((activity.getIntent().getFlags() & FLAG_ACTIVITY_NEW_DOCUMENT) != 0);
            assertTrue((activity.getIntent().getFlags() & FLAG_ACTIVITY_MULTIPLE_TASK) != 0);
        } finally {
            deleteShortcuts();
            cleanupSendBubbleActivity();
        }
    }

    @Test
    public void testNotificationManagerBubblePolicy_flag_shortcutBubble()
            throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            allowAllNotificationsToBubble();

            createDynamicShortcut();

            Notification.Builder nb = getConversationNotification();
            nb.setBubbleMetadata(
                    new Notification.BubbleMetadata.Builder(SHARE_SHORTCUT_ID).build());

            boolean shouldBeBubble = !mActivityManager.isLowRamDevice();
            sendAndVerifyBubble(1, nb, shouldBeBubble);
        } finally {
            deleteShortcuts();
        }
    }

    @Test
    public void testNotificationManagerBubblePolicy_noFlag_invalidShortcut()
            throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            allowAllNotificationsToBubble();

            createDynamicShortcut();

            Notification.Builder nb = getConversationNotification();
            nb.setShortcutId("invalid");
            nb.setBubbleMetadata(new Notification.BubbleMetadata.Builder("invalid").build());

            sendAndVerifyBubble(1, nb, false);
        } finally {
            deleteShortcuts();
        }
    }

    @Test
    public void testNotificationManagerBubblePolicy_noFlag_invalidNotif()
            throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            allowAllNotificationsToBubble();

            createDynamicShortcut();

            int id = 1;
            Notification.Builder nb = getDefaultNotifBuilder(id);
            nb.setBubbleMetadata(
                    new Notification.BubbleMetadata.Builder(SHARE_SHORTCUT_ID).build());

            sendAndVerifyBubble(id, nb, false /* shouldBeBubble */);
        } finally {
            deleteShortcuts();
        }
    }

    @Test
    public void testNotificationManagerBubblePolicy_appAll_globalOn() throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            allowAllNotificationsToBubble();

            createDynamicShortcut();
            Notification.Builder nb = getConversationNotification();
            nb.setBubbleMetadata(
                    new Notification.BubbleMetadata.Builder(SHARE_SHORTCUT_ID).build());

            boolean shouldBeBubble = !mActivityManager.isLowRamDevice();
            sendAndVerifyBubble(1, nb, shouldBeBubble);
        } finally {
            deleteShortcuts();
        }
    }

    @Test
    public void testNotificationManagerBubblePolicy_appAll_globalOff() throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            setBubblesGlobal(false);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);
            sleep();

            createDynamicShortcut();
            Notification.Builder nb = getConversationNotification();
            nb.setBubbleMetadata(new Notification.BubbleMetadata.Builder(SHARE_SHORTCUT_ID)
                    .build());

            sendAndVerifyBubble(1, nb, false);
        } finally {
            deleteShortcuts();
        }
    }

    @Test
    public void testNotificationManagerBubblePolicy_appAll_channelNo() throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(false);
            sleep();

            createDynamicShortcut();
            Notification.Builder nb = getConversationNotification();
            nb.setBubbleMetadata(new Notification.BubbleMetadata.Builder(SHARE_SHORTCUT_ID)
                    .build());

            sendAndVerifyBubble(1, nb, false);
        } finally {
            deleteShortcuts();
        }
    }

    @Test
    public void testNotificationManagerBubblePolicy_appSelected_channelNo() throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(2 /* selected */);
            setBubblesChannelAllowed(false);
            sleep();

            createDynamicShortcut();
            Notification.Builder nb = getConversationNotification();
            nb.setBubbleMetadata(new Notification.BubbleMetadata.Builder(SHARE_SHORTCUT_ID)
                    .build());

            sendAndVerifyBubble(1, nb, false);
        } finally {
            deleteShortcuts();
        }
    }

    @Test
    public void testNotificationManagerBubblePolicy_appSelected_channelYes() throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(2 /* selected */);
            setBubblesChannelAllowed(true);
            sleep();

            createDynamicShortcut();
            Notification.Builder nb = getConversationNotification();
            nb.setBubbleMetadata(new Notification.BubbleMetadata.Builder(SHARE_SHORTCUT_ID)
                    .build());

            boolean shouldBeBubble = !mActivityManager.isLowRamDevice();
            sendAndVerifyBubble(1, nb, shouldBeBubble);
        } finally {
            deleteShortcuts();
        }
    }

    @Test
    public void testNotificationManagerBubblePolicy_appNone_channelNo() throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(0 /* none */);
            setBubblesChannelAllowed(false);
            sleep();

            createDynamicShortcut();
            Notification.Builder nb = getConversationNotification();
            nb.setBubbleMetadata(new Notification.BubbleMetadata.Builder(SHARE_SHORTCUT_ID)
                    .build());

            sendAndVerifyBubble(1, nb, false);
        } finally {
            deleteShortcuts();
        }
    }

    @Test
    public void testNotificationManagerBubblePolicy_noFlag_shortcutRemoved()
            throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }

        try {
            allowAllNotificationsToBubble();
            createDynamicShortcut();
            Notification.Builder nb = getConversationNotification();
            nb.setBubbleMetadata(new Notification.BubbleMetadata.Builder(SHARE_SHORTCUT_ID)
                    .build());

            sendAndVerifyBubble(42, nb, true /* shouldBeBubble */);
            mListener.resetData();

            deleteShortcuts();
            verifyNotificationBubbleState(42, false /* should be bubble */);
        } finally {
            deleteShortcuts();
        }
    }

    @Test
    public void testNotificationManagerBubbleNotificationSuppression() throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            allowAllNotificationsToBubble();

            createDynamicShortcut();
            setUpNotifListener();

            // make ourselves foreground so we can specify suppress notification flag
            SendBubbleActivity a = startSendBubbleActivity();

            // send the bubble with notification suppressed
            a.sendBubble(BUBBLE_NOTIF_ID, false /* autoExpand */, true /* suppressNotif */);
            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, true /* shouldBeBubble */);

            // check for the notification
            StatusBarNotification sbnSuppressed = mListener.mPosted.get(0);
            assertNotNull(sbnSuppressed);
            // check for suppression state
            Notification.BubbleMetadata metadata =
                    sbnSuppressed.getNotification().getBubbleMetadata();
            assertNotNull(metadata);
            assertTrue(metadata.isNotificationSuppressed());

            mListener.resetData();

            // send the bubble with notification NOT suppressed
            a.sendBubble(BUBBLE_NOTIF_ID, false /* autoExpand */, false /* suppressNotif */);
            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, true /* shouldBubble */);

            // check for the notification
            StatusBarNotification sbnNotSuppressed = mListener.mPosted.get(0);
            assertNotNull(sbnNotSuppressed);
            // check for suppression state
            metadata = sbnNotSuppressed.getNotification().getBubbleMetadata();
            assertNotNull(metadata);
            assertFalse(metadata.isNotificationSuppressed());
        } finally {
            cleanupSendBubbleActivity();
            deleteShortcuts();
        }
    }

    @Test
    public void testNotificationManagerBubble_checkIsBubbled_pendingIntent()
            throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            allowAllNotificationsToBubble();

            createDynamicShortcut();
            setUpNotifListener();

            SendBubbleActivity a = startSendBubbleActivity();

            // Prep to find bubbled activity
            Instrumentation.ActivityMonitor monitor = startBubbledActivityMonitor();

            a.sendBubble(BUBBLE_NOTIF_ID, true /* autoExpand */, false /* suppressNotif */);

            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, true /* shouldBeBubble */);

            BubbledActivity activity = (BubbledActivity) monitor.waitForActivity();
            assertTrue(activity.isLaunchedFromBubble());
        } finally {
            deleteShortcuts();
            cleanupSendBubbleActivity();
        }
    }

    @Test
    public void testNotificationManagerBubble_checkIsBubbled_shortcut()
            throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            allowAllNotificationsToBubble();

            createDynamicShortcut();
            setUpNotifListener();

            SendBubbleActivity a = startSendBubbleActivity();

            // Prep to find bubbled activity
            Instrumentation.ActivityMonitor monitor = startBubbledActivityMonitor();

            a.sendBubble(BUBBLE_NOTIF_ID, true /* autoExpand */,
                    false /* suppressNotif */,
                    false /* suppressBubble */,
                    true /* useShortcut */,
                    true /* setLocus */);

            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, true /* shouldBeBubble */);

            BubbledActivity activity = (BubbledActivity) monitor.waitForActivity();
            assertTrue(activity.isLaunchedFromBubble());
        } finally {
            deleteShortcuts();
            cleanupSendBubbleActivity();
        }
    }

    /** Verifies the bubble is suppressed when it should be. */
    @Test
    public void testNotificationManagerBubble_setSuppressBubble()
            throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            allowAllNotificationsToBubble();

            createDynamicShortcut();
            setUpNotifListener();

            final int notifId = 3;

            // Make a bubble
            SendBubbleActivity a = startSendBubbleActivity();
            a.sendBubble(notifId,
                    false /* autoExpand */,
                    false /* suppressNotif */,
                    true /* suppressBubble */);

            verifyNotificationBubbleState(notifId, true /* shouldBeBubble */);
            mListener.resetData();

            // Launch same activity as whats in the bubble
            BubbledActivity activity = startBubbleActivity(notifId);

            // It should have the locusId
            assertEquals(new LocusId(String.valueOf(notifId)),
                    activity.getLocusId());

            // notif gets posted with update, so wait
            verifyNotificationBubbleState(notifId, true /* shouldBeBubble */);

            // Bubble should have suppressed flag
            StatusBarNotification sbn = mNotificationHelper.findPostedNotification(null, notifId,
                    SEARCH_TYPE.POSTED);
            assertTrue(sbn.getNotification().getBubbleMetadata().isBubbleSuppressable());
            assertTrue(sbn.getNotification().getBubbleMetadata().isBubbleSuppressed());
        } finally {
            deleteShortcuts();
            cleanupSendBubbleActivity();
        }
    }

    /** Verifies the bubble is not suppressed if dev didn't specify suppressable */
    @Test
    public void testNotificationManagerBubble_setSuppressBubble_notSuppressable()
            throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            allowAllNotificationsToBubble();

            createDynamicShortcut();
            setUpNotifListener();

            // Make a bubble
            SendBubbleActivity a = startSendBubbleActivity();
            a.sendBubble(BUBBLE_NOTIF_ID,
                    false /* autoExpand */,
                    false /* suppressNotif */,
                    false /* suppressBubble */);

            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, true /* shouldBeBubble */);
            mListener.resetData();

            // Launch same activity as whats in the bubble
            BubbledActivity activity = startBubbleActivity(BUBBLE_NOTIF_ID);

            // It should have the locusId
            assertEquals(new LocusId(String.valueOf(BUBBLE_NOTIF_ID)),
                    activity.getLocusId());

            // Wait a little (if it wrongly updates it'd be a new post so wait for that))
            sleep();
            assertTrue(mListener.mPosted.isEmpty());

            // Bubble should not be suppressed
            StatusBarNotification sbn = mNotificationHelper.findPostedNotification(null,
                    BUBBLE_NOTIF_ID, SEARCH_TYPE.LISTENER);
            assertFalse(sbn.getNotification().getBubbleMetadata().isBubbleSuppressable());
            assertFalse(sbn.getNotification().getBubbleMetadata().isBubbleSuppressed());
        } finally {
            deleteShortcuts();
            cleanupSendBubbleActivity();
        }
    }

    /** Verifies the bubble is not suppressed if the activity doesn't have a locusId. */
    @Test
    public void testNotificationManagerBubble_setSuppressBubble_activityNoLocusId()
            throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            allowAllNotificationsToBubble();

            createDynamicShortcut();
            setUpNotifListener();

            // Make a bubble
            SendBubbleActivity a = startSendBubbleActivity();
            a.sendBubble(BUBBLE_NOTIF_ID,
                    false /* autoExpand */,
                    false /* suppressNotif */,
                    true /* suppressBubble */);

            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, true /* shouldBeBubble */);
            mListener.resetData();

            // Launch same activity as whats in the bubble
            BubbledActivity activity = startBubbleActivity(BUBBLE_NOTIF_ID, false /* addLocusId */);

            // It shouldn't have the locusId
            assertNull(activity.getLocusId());

            // Wait a little (if it wrongly updates it'd be a new post so wait for that))
            sleep();
            assertTrue(mListener.mPosted.isEmpty());

            // Bubble should not be suppressed
            StatusBarNotification sbn = mNotificationHelper.findPostedNotification(null,
                    BUBBLE_NOTIF_ID, SEARCH_TYPE.LISTENER);
            assertTrue(sbn.getNotification().getBubbleMetadata().isBubbleSuppressable());
            assertFalse(sbn.getNotification().getBubbleMetadata().isBubbleSuppressed());
        } finally {
            deleteShortcuts();
            cleanupSendBubbleActivity();
        }
    }

    /** Verifies the bubble is not suppressed if the notification doesn't have a locusId. */
    @Test
    public void testNotificationManagerBubble_setSuppressBubble_notificationNoLocusId()
            throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            allowAllNotificationsToBubble();

            createDynamicShortcut();
            setUpNotifListener();

            // Make a bubble
            SendBubbleActivity a = startSendBubbleActivity();
            a.sendBubble(BUBBLE_NOTIF_ID,
                    false /* autoExpand */,
                    false /* suppressNotif */,
                    true /* suppressBubble */,
                    false /* useShortcut */,
                    false /* setLocusId */);

            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, true /* shouldBeBubble */);
            mListener.resetData();

            // Launch same activity as whats in the bubble
            BubbledActivity activity = startBubbleActivity(BUBBLE_NOTIF_ID, true /* addLocusId */);

            // Activity has the locus
            assertNotNull(activity.getLocusId());

            // Wait a little (if it wrongly updates it'd be a new post so wait for that))
            sleep();
            assertTrue(mListener.mPosted.isEmpty());

            // Bubble should not be suppressed & not have a locusId
            StatusBarNotification sbn = mNotificationHelper.findPostedNotification(null,
                    BUBBLE_NOTIF_ID, SEARCH_TYPE.LISTENER);
            assertNull(sbn.getNotification().getLocusId());
            assertTrue(sbn.getNotification().getBubbleMetadata().isBubbleSuppressable());
            assertFalse(sbn.getNotification().getBubbleMetadata().isBubbleSuppressed());
        } finally {
            deleteShortcuts();
            cleanupSendBubbleActivity();
        }
    }

    /** Verifies the bubble is unsuppressed when the locus activity is hidden. */
    @Test
    public void testNotificationManagerBubble_setSuppressBubble_dismissLocusActivity()
            throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            allowAllNotificationsToBubble();

            createDynamicShortcut();
            setUpNotifListener();

            final int notifId = 2;

            // Make a bubble
            SendBubbleActivity a = startSendBubbleActivity();
            a.sendBubble(notifId,
                    false /* autoExpand */,
                    false /* suppressNotif */,
                    true /* suppressBubble */);

            verifyNotificationBubbleState(notifId, true);
            StatusBarNotification sbn = mNotificationHelper.findPostedNotification(null, notifId,
                    SEARCH_TYPE.POSTED);
            assertTrue(sbn.getNotification().getBubbleMetadata().isBubbleSuppressable());
            assertFalse(sbn.getNotification().getBubbleMetadata().isBubbleSuppressed());
            mListener.resetData();

            // Launch same activity as whats in the bubble
            BubbledActivity activity = startBubbleActivity(notifId);

            // It should have the locusId
            assertEquals(new LocusId(String.valueOf(notifId)), activity.getLocusId());

            // notif gets posted with update, so wait
            verifyNotificationBubbleState(notifId, true /* shouldBeBubble */);
            // Bubble should have suppressed flag
            sbn = mNotificationHelper.findPostedNotification(null, notifId, SEARCH_TYPE.LISTENER);
            assertTrue(sbn.getNotification().getBubbleMetadata().isBubbleSuppressable());
            assertTrue(sbn.getNotification().getBubbleMetadata().isBubbleSuppressed());
            mListener.resetData();

            activity.finish();

            // notif gets posted with update, so wait
            verifyNotificationBubbleState(notifId, true /* shouldBeBubble */);
            sbn = mNotificationHelper.findPostedNotification(null, notifId, SEARCH_TYPE.LISTENER);
            assertTrue(sbn.getNotification().getBubbleMetadata().isBubbleSuppressable());
            assertFalse(sbn.getNotification().getBubbleMetadata().isBubbleSuppressed());
        } finally {
            deleteShortcuts();
            cleanupSendBubbleActivity();
        }
    }

    /** Verifies that a regular activity can't specify a bubble in ActivityOptions */
    @Test
    @SuppressWarnings("AssertThrowsMultipleStatements")
    public void testNotificationManagerBubble_launchBubble_activityOptions_fails()
            throws Exception {
        if (!isBubblesFeatureSupported()) {
            return;
        }
        try {
            // Start test activity
            SendBubbleActivity activity = startSendBubbleActivity();
            assertFalse(activity.isLaunchedFromBubble());

            // Should have exception
            assertThrows(SecurityException.class, () -> {
                Intent i = new Intent(mContext, BubbledActivity.class);
                ActivityOptions options = ActivityOptions.makeBasic();
                Bundle b = options.toBundle();
                b.putBoolean("android.activity.launchTypeBubble", true);
                activity.startActivity(i, b);
            });
        } finally {
            cleanupSendBubbleActivity();
        }
    }
}
