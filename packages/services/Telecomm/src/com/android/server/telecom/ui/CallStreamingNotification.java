/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.telecom.ui;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.UserHandle;
import android.telecom.Log;
import android.telecom.PhoneAccount;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.android.internal.annotations.GuardedBy;
import com.android.server.telecom.AppLabelProxy;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallsManagerListenerBase;
import com.android.server.telecom.R;
import com.android.server.telecom.TelecomBroadcastIntentProcessor;
import com.android.server.telecom.components.TelecomBroadcastReceiver;

import java.util.concurrent.Executor;

/**
 * Class responsible for tracking if there is a call which is being streamed and posting a
 * notification which informs the user that a call is streaming.  The user has two possible actions:
 * disconnect the call, bring the call back to the current device (stop streaming).
 */
public class CallStreamingNotification extends CallsManagerListenerBase implements Call.Listener {
    // URI scheme used for data related to the notification actions.
    public static final String CALL_ID_SCHEME = "callid";
    // The default streaming notification ID.
    private static final int STREAMING_NOTIFICATION_ID = 90210;
    // Tag for streaming notification.
    private static final String NOTIFICATION_TAG =
            CallStreamingNotification.class.getSimpleName();

    private final Context mContext;
    private final NotificationManager mNotificationManager;
    // Used to get the app name for the notification.
    private final AppLabelProxy mAppLabelProxy;
    // An executor that can be used to fire off async tasks that do not block Telecom in any manner.
    private final Executor mAsyncTaskExecutor;
    // The call which is streaming.
    private Call mStreamingCall;
    // Lock for notification post/remove -- these happen outside the Telecom sync lock.
    private final Object mNotificationLock = new Object();

    // Whether the notification is showing.
    @GuardedBy("mNotificationLock")
    private boolean mIsNotificationShowing = false;
    @GuardedBy("mNotificationLock")
    private UserHandle mNotificationUserHandle;

    public CallStreamingNotification(@NonNull Context context,
            @NonNull AppLabelProxy appLabelProxy,
            @NonNull Executor asyncTaskExecutor) {
        mContext = context;
        mNotificationManager = context.getSystemService(NotificationManager.class);
        mAppLabelProxy = appLabelProxy;
        mAsyncTaskExecutor = asyncTaskExecutor;
    }

    @Override
    public void onCallAdded(Call call) {
        if (call.isStreaming()) {
            trackStreamingCall(call);
            enqueueStreamingNotification(call);
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        if (call == mStreamingCall) {
            trackStreamingCall(null);
            dequeueStreamingNotification();
        }
    }

    /**
     * Handles streaming state changes for a call.
     * @param call the call
     * @param isStreaming whether it is streaming or not
     */
    @Override
    public void onCallStreamingStateChanged(Call call, boolean isStreaming) {
        Log.i(this, "onCallStreamingStateChanged: call=%s, isStreaming=%b", call.getId(),
                isStreaming);

        if (isStreaming) {
            trackStreamingCall(call);
            enqueueStreamingNotification(call);
        } else {
            trackStreamingCall(null);
            dequeueStreamingNotification();
        }
    }

    /**
     * Change the streaming call we are tracking.
     * @param call the call.
     */
    private void trackStreamingCall(Call call) {
        if (mStreamingCall != null) {
            mStreamingCall.removeListener(this);
        }
        mStreamingCall = call;
        if (mStreamingCall != null) {
            mStreamingCall.addListener(this);
        }
    }

    /**
     * Enqueue an async task to post/repost the streaming notification.
     * Note: This happens INSIDE the telecom lock.
     * @param call the call to post notification for.
     */
    private void enqueueStreamingNotification(Call call) {
        final Bitmap contactPhotoBitmap = call.getPhotoIcon();
        mAsyncTaskExecutor.execute(() -> {
            Icon contactPhotoIcon = null;
            try {
                contactPhotoIcon = Icon.createWithResource(mContext.getResources(),
                        R.drawable.person_circle);
            } catch (Exception e) {
                // All loads of things can do wrong when working with bitmaps and images, so to
                // ensure Telecom doesn't crash, lets try/catch to be sure.
                Log.e(this, e, "enqueueStreamingNotification: Couldn't build avatar icon");
            }
            showStreamingNotification(call.getId(),
                    call.getAssociatedUser(), call.getCallerDisplayName(),
                    call.getHandle(), contactPhotoIcon,
                    call.getTargetPhoneAccount().getComponentName().getPackageName(),
                    call.getConnectTimeMillis());
        });
    }

    /**
     * Dequeues the call streaming notification.
     * Note: This is yo be called within the Telecom sync lock to launch the task to remove the call
     * streaming notification.
     */
    private void dequeueStreamingNotification() {
        mAsyncTaskExecutor.execute(() -> hideStreamingNotification());
    }

    /**
     * Show the call streaming notification.  This is intended to run outside the Telecom sync lock.
     *
     * @param callId the call ID we're streaming.
     * @param userHandle the userhandle for the call.
     * @param callerName the name of the caller/callee associated with the call
     * @param callerAddress the address associated with the caller/callee
     * @param photoIcon the contact photo icon if available
     * @param appPackageName the package name for the app to post the notification for
     * @param connectTimeMillis when the call connected (for chronometer in the notification)
     */
    private void showStreamingNotification(final String callId, final UserHandle userHandle,
            String callerName, Uri callerAddress, Icon photoIcon, String appPackageName,
            long connectTimeMillis) {
        Log.i(this, "showStreamingNotification; callid=%s, hasPhoto=%b", callId, photoIcon != null);

        // Use the caller name for the label if available, default to app name if none.
        if (TextUtils.isEmpty(callerName)) {
            // App did not provide a caller name, so default to app's name.
            callerName = mAppLabelProxy.getAppLabel(appPackageName).toString();
        }

        // Action to hangup; this can use the default hangup action from the call style
        // notification.
        Intent hangupIntent = new Intent(TelecomBroadcastIntentProcessor.ACTION_HANGUP_CALL,
                Uri.fromParts(CALL_ID_SCHEME, callId, null),
                mContext, TelecomBroadcastReceiver.class);
        PendingIntent hangupPendingIntent = PendingIntent.getBroadcast(mContext, 0, hangupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Action to switch here.
        Intent switchHereIntent = new Intent(TelecomBroadcastIntentProcessor.ACTION_STOP_STREAMING,
                Uri.fromParts(CALL_ID_SCHEME, callId, null),
                mContext, TelecomBroadcastReceiver.class);
        PendingIntent switchHerePendingIntent = PendingIntent.getBroadcast(mContext, 0,
                switchHereIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Apply a span to the string to colorize it using the "answer" color.
        Spannable spannable = new SpannableString(
                mContext.getString(R.string.call_streaming_notification_action_switch_here));
        spannable.setSpan(new ForegroundColorSpan(
                com.android.internal.R.color.call_notification_answer_color), 0, spannable.length(),
                Spannable.SPAN_INCLUSIVE_EXCLUSIVE);

        // Use the "phone link" icon per mock.
        Icon switchHereIcon = Icon.createWithResource(mContext, R.drawable.gm_phonelink);
        Notification.Action.Builder switchHereBuilder = new Notification.Action.Builder(
                switchHereIcon,
                spannable,
                switchHerePendingIntent);
        Notification.Action switchHereAction = switchHereBuilder.build();

        // Notifications use a "person" entity to identify caller/callee.
        Person.Builder personBuilder = new Person.Builder()
                .setName(callerName);

        // Some apps use phone numbers to identify; these are something the notification framework
        // can lookup in contacts to provide more data
        if (callerAddress != null && PhoneAccount.SCHEME_TEL.equals(callerAddress)) {
            personBuilder.setUri(callerAddress.toString());
        }
        if (photoIcon != null) {
            personBuilder.setIcon(photoIcon);
        }
        Person person = personBuilder.build();

        // Call Style notification requires a full screen intent, so we'll just link in a null
        // pending intent
        Intent nullIntent = new Intent();
        PendingIntent nullPendingIntent = PendingIntent.getBroadcast(mContext, 0, nullIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(mContext,
                NotificationChannelManager.CHANNEL_ID_CALL_STREAMING)
                // Use call style to get the general look and feel for the notification; it provides
                // a hangup action with the right action already so we can leverage that.  The
                // "switch here" action will be a custom action defined later.
                .setStyle(Notification.CallStyle.forOngoingCall(person, hangupPendingIntent))
                .setSmallIcon(R.drawable.ic_phone)
                .setContentText(mContext.getString(
                        R.string.call_streaming_notification_body))
                // Report call time
                .setWhen(connectTimeMillis)
                .setShowWhen(true)
                .setUsesChronometer(true)
                // Set the full screen intent; this is just tricking notification manager into
                // letting us use this style.  Sssh.
                .setFullScreenIntent(nullPendingIntent, true)
                .setColorized(true)
                .addAction(switchHereAction);
        Notification notification = builder.build();

        synchronized(mNotificationLock) {
            mIsNotificationShowing = true;
            mNotificationUserHandle = userHandle;
            try {
                mNotificationManager.notifyAsUser(NOTIFICATION_TAG, STREAMING_NOTIFICATION_ID,
                        notification, userHandle);
            } catch (Exception e) {
                // We don't want to crash Telecom if something changes with the requirements for the
                // notification.
                Log.e(this, e, "Notification post failed.");
            }
        }
    }

    /**
     * Removes the posted streaming notification.  Intended to run outside the telecom lock.
     */
    private void hideStreamingNotification() {
        Log.i(this, "hideStreamingNotification");
        synchronized(mNotificationLock) {
            if (mIsNotificationShowing) {
                mIsNotificationShowing = false;
                mNotificationManager.cancelAsUser(NOTIFICATION_TAG,
                        STREAMING_NOTIFICATION_ID, mNotificationUserHandle);
            }
        }
    }

    public static Bitmap drawableToBitmap(@Nullable Drawable drawable, int width, int height) {
        if (drawable == null) {
            return null;
        }

        Bitmap bitmap;
        if (drawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) drawable).getBitmap();
        } else {
            if (width > 0 || height > 0) {
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            } else if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
                // Needed for drawables that are just a colour.
                bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            } else {
                bitmap =
                        Bitmap.createBitmap(
                                drawable.getIntrinsicWidth(),
                                drawable.getIntrinsicHeight(),
                                Bitmap.Config.ARGB_8888);
            }

            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }
        return bitmap;
    }
}
