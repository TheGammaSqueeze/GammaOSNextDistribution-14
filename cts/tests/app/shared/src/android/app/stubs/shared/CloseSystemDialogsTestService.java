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

package android.app.stubs.shared;

import static android.app.PendingIntent.FLAG_IMMUTABLE;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.app.IActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelableException;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.view.IWindowManager;

import com.android.compatibility.common.util.PollingCheck;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * This is a bound service used in conjunction with CloseSystemDialogsTest.
 */
public class CloseSystemDialogsTestService extends Service {
    private static final String TAG = "CloseSystemDialogsTestService";
    private static final String NOTIFICATION_ACTION = TAG;
    private static final String NOTIFICATION_CHANNEL_ID = "cts/" + TAG;
    private static final Intent INTENT_ACSD = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);

    private final ICloseSystemDialogsTestsService mBinder = new Binder();
    private NotificationManager mNotificationManager;
    private IWindowManager mWindowManager;
    private IActivityManager mActivityManager;
    private BroadcastReceiver mNotificationReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = getSystemService(NotificationManager.class);
        mWindowManager = IWindowManager.Stub.asInterface(
                ServiceManager.getService(Context.WINDOW_SERVICE));
        mActivityManager = IActivityManager.Stub.asInterface(
                ServiceManager.getService(Context.ACTIVITY_SERVICE));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder.asBinder();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mNotificationReceiver != null) {
            unregisterReceiver(mNotificationReceiver);
        }
    }

    private class Binder extends ICloseSystemDialogsTestsService.Stub {
        private final Context mContext = CloseSystemDialogsTestService.this;

        /** Checks that it can call @hide methods. */
        @Override
        public boolean waitUntilReady(long timeoutMs) {
            try {
                PollingCheck.check("Can't call @hide methods", timeoutMs, () -> {
                    try {
                        // Any method suffices since IWindowManager is @hide
                        mWindowManager.isRotationFrozen();
                        return true;
                    } catch (NoSuchMethodError e) {
                        return false;
                    }
                });
                return true;
            } catch (AssertionError e) {
                return false;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void sendCloseSystemDialogsBroadcast() {
            mContext.sendBroadcast(INTENT_ACSD);
        }

        @Override
        public void postNotification(int notificationId, ResultReceiver receiver,
                boolean usePendingIntent) {
            mNotificationReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        if (usePendingIntent) {
                            PendingIntent.getBroadcast(mContext, /* requestCode */ 0, INTENT_ACSD,
                                    FLAG_IMMUTABLE).send();
                        } else {
                            mContext.sendBroadcast(INTENT_ACSD);
                        }
                        receiver.send(RESULT_OK, null);
                    } catch (SecurityException e) {
                        receiver.send(RESULT_SECURITY_EXCEPTION, null);
                    } catch (PendingIntent.CanceledException e) {
                        receiver.send(RESULT_ERROR, null);
                    }
                }
            };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                mContext.registerReceiver(mNotificationReceiver,
                        new IntentFilter(NOTIFICATION_ACTION), Context.RECEIVER_EXPORTED_UNAUDITED);
            } else {
                mContext.registerReceiver(mNotificationReceiver,
                        new IntentFilter(NOTIFICATION_ACTION));
            }
            Intent intent = new Intent(NOTIFICATION_ACTION);
            intent.setPackage(mContext.getPackageName());
            CloseSystemDialogsTestService.this.notify(
                    notificationId,
                    PendingIntent.getBroadcast(mContext, 0, intent, FLAG_IMMUTABLE));
        }

        @Override
        public void closeSystemDialogsViaWindowManager(String reason) throws RemoteException {
            mWindowManager.closeSystemDialogs(reason);
        }

        @Override
        public void closeSystemDialogsViaActivityManager(String reason) throws RemoteException {
            mActivityManager.closeSystemDialogs(reason);
        }

        @Override
        public boolean waitForAccessibilityServiceWindow(long timeoutMs) throws RemoteException {
            final AppAccessibilityService service;
            try {
                service = AppAccessibilityService.getConnected().get(timeoutMs, MILLISECONDS);
            } catch (TimeoutException e) {
                return false;
            } catch (ExecutionException | InterruptedException e) {
                throw new ParcelableException(e);
            }
            return service.waitWindowAdded(timeoutMs);
        }
    }

    private void notify(int notificationId, PendingIntent intent) {
        Notification notification =
                new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_info)
                        .setContentIntent(intent)
                        .build();
        NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(notificationChannel);
        mNotificationManager.notify(notificationId, notification);
    }
}
