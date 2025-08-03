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

package android.app.stubs;

import android.app.Activity;
import android.app.Notification;
import android.app.Notification.BubbleMetadata;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.content.LocusId;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.SystemClock;

/**
 * Used by NotificationManagerBubbleTest for testing policy around bubbles, this activity is able to
 * send a bubble.
 */
public class SendBubbleActivity extends Activity {

    // Should be same as what NotificationManagerBubbleTest is using
    private static final String NOTIFICATION_CHANNEL_ID = "NotificationManagerTest";
    private static final String SHARE_SHORTCUT_ID = "shareShortcut";

    public static final String BUBBLE_ACTIVITY_OPENED =
            "android.app.stubs.BUBBLE_ACTIVITY_OPENED";
    public static final int BUBBLE_NOTIF_ID = 1;

    private volatile boolean mIsStopped;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Intent i = new Intent(BUBBLE_ACTIVITY_OPENED);
        sendBroadcast(i);
    }

    /**
     * Sends a notification that has bubble metadata but the rest of the notification isn't
     * configured correctly so the system won't allow it to bubble.
     */
    public void sendInvalidBubble(int notifId, boolean autoExpand) {
        Context context = getApplicationContext();

        PendingIntent pendingIntent = PendingIntent.getActivity(context, notifId,
                new Intent().setPackage(context.getPackageName()),
                PendingIntent.FLAG_MUTABLE);
        Notification n = new Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.black)
                .setWhen(System.currentTimeMillis())
                .setContentTitle("notify#" + notifId)
                .setContentText("This is #" + notifId + "notification  ")
                .setContentIntent(pendingIntent)
                .setBubbleMetadata(getBubbleMetadata(notifId, autoExpand,
                        false /* suppressNotification */,
                        false /* suppressBubble */,
                        false /* useShortcut */))
                .build();

        NotificationManager noMan = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        noMan.notify(notifId, n);
    }

    /** Sends a notification that is properly configured to bubble. */
    public void sendBubble(int notifId, boolean autoExpand, boolean suppressNotification) {
        sendBubble(notifId, autoExpand, suppressNotification, false /* suppressBubble */,
                false /* useShortcut */, true /* setLocusId */);
    }

    /** Sends a notification that is properly configured to bubble. */
    public void sendBubble(int notifId, boolean autoExpand, boolean suppressNotification,
            boolean suppressBubble) {
        sendBubble(notifId, autoExpand, suppressNotification, suppressBubble,
                false /* useShortcut */, true /* setLocusId */);
    }

    /** Sends a notification that is properly configured to bubble. */
    public void sendBubble(int notifId, boolean autoExpand, boolean suppressNotification,
            boolean suppressBubble, boolean useShortcut, boolean setLocusId) {
        Context context = getApplicationContext();
        // Give it a person
        Person person = new Person.Builder()
                .setName("bubblebot" + notifId)
                .build();
        // Make it messaging style
        Notification.Builder nb = new Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.black)
                .setContentTitle("Bubble Chat")
                .setShortcutId(SHARE_SHORTCUT_ID)
                .setStyle(new Notification.MessagingStyle(person)
                        .setConversationTitle("Bubble Chat")
                        .addMessage("Hello?",
                                SystemClock.currentThreadTimeMillis() - 300000, person)
                        .addMessage("Is it me you're looking for?",
                                SystemClock.currentThreadTimeMillis(), person)
                )
                .setBubbleMetadata(getBubbleMetadata(notifId,
                        autoExpand,
                        suppressNotification,
                        suppressBubble,
                        useShortcut));

        if (setLocusId) {
            nb.setLocusId(new LocusId(String.valueOf(notifId)));
        }

        NotificationManager noMan = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        noMan.notify(notifId, nb.build());
    }

    private BubbleMetadata getBubbleMetadata(int notifId, boolean autoExpand,
            boolean suppressNotification,
            boolean suppressBubble,
            boolean useShortcut) {
        if (useShortcut) {
            return new Notification.BubbleMetadata.Builder(SHARE_SHORTCUT_ID)
                    .setAutoExpandBubble(autoExpand)
                    .setSuppressableBubble(suppressBubble)
                    .setSuppressNotification(suppressNotification)
                    .build();
        } else {
            Context context = getApplicationContext();
            final Intent intent = new Intent(context, BubbledActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(Intent.ACTION_MAIN);
            final PendingIntent pendingIntent =
                    PendingIntent.getActivity(context, notifId, intent,
                            PendingIntent.FLAG_MUTABLE);

            return new Notification.BubbleMetadata.Builder(pendingIntent,
                    Icon.createWithResource(context, R.drawable.black))
                    .setAutoExpandBubble(autoExpand)
                    .setSuppressNotification(suppressNotification)
                    .setSuppressableBubble(suppressBubble)
                    .build();
        }
    }

    /** Waits for the activity to be stopped. Do not call this method on main thread. */
    public void waitForStopped() {
        synchronized (this) {
            while (!mIsStopped) {
                try {
                    wait(5000 /* timeout */);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mIsStopped = false;
    }

    @Override
    protected void onStop() {
        super.onStop();
        synchronized (this) {
            mIsStopped = true;
            notifyAll();
        }
    }
}
