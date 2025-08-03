/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.car.notification;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.android.internal.statusbar.IStatusBarService;

/**
 * Displays all undismissed notifications.
 */
public class CarNotificationCenterActivity extends Activity {

    private static final String TAG = "CarNotificationActivity";

    private CarNotificationListener mNotificationListener;
    private PreprocessingManager mPreprocessingManager;
    private CarNotificationView mCarNotificationView;
    private NotificationViewController mNotificationViewController;
    private IStatusBarService mStatusBarService;
    private NotificationDataManager mNotificationDataManager;
    private CarNotificationVisibilityLogger mNotificationVisibilityLogger;

    private ServiceConnection mNotificationListenerConnectionListener = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            Log.d(TAG, "onServiceConnected");
            mNotificationListener = ((CarNotificationListener.LocalBinder) binder).getService();
            NotificationApplication app = (NotificationApplication) getApplication();
            mNotificationViewController =
                    new NotificationViewController(mCarNotificationView,
                            mPreprocessingManager,
                            mNotificationListener,
                            app.getCarUxRestrictionWrapper());
            mNotificationViewController.enable();
            getApplicationContext().getMainExecutor().execute(() -> {
                if (isResumed()) {
                    notifyVisibilityChanged(/* isVisible= */ true);
                    mCarNotificationView.setVisibleNotificationsAsSeen();
                }
            });
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "onServiceDisconnected");
            mNotificationViewController.disable();
            mNotificationViewController = null;
            mNotificationListener = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPreprocessingManager = PreprocessingManager.getInstance(getApplicationContext());
        NotificationApplication app = (NotificationApplication) getApplication();
        setContentView(R.layout.notification_center_activity);
        mCarNotificationView = findViewById(R.id.notification_view);
        mCarNotificationView.setClickHandlerFactory(app.getClickHandlerFactory());
        findViewById(R.id.exit_button_container).setOnClickListener(v -> finish());
        mNotificationDataManager = NotificationDataManager.getInstance();
        mStatusBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
        mNotificationVisibilityLogger = new CarNotificationVisibilityLogger(mStatusBarService,
                mNotificationDataManager);

        mNotificationDataManager.setOnUnseenCountUpdateListener(() -> {
            mNotificationListener.setNotificationsShown(
                    NotificationDataManager.getInstance().getSeenNotifications());
            mNotificationVisibilityLogger.notifyVisibilityChanged(isResumed());
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind notification listener
        Intent intent = new Intent(this, CarNotificationListener.class);
        intent.setAction(CarNotificationListener.ACTION_LOCAL_BINDING);
        bindService(intent, mNotificationListenerConnectionListener, Context.BIND_AUTO_CREATE);
        if (mNotificationViewController != null) {
            mNotificationViewController.onVisibilityChanged(/* isVisible= */ true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        notifyVisibilityChanged(/* isVisible= */ false);

        // Unbind notification listener
        if (mNotificationViewController != null) {
            mNotificationViewController.disable();
            mNotificationViewController = null;
        }

        if (mNotificationListenerConnectionListener != null) {
            unbindService(mNotificationListenerConnectionListener);
            mNotificationListener = null;
        }
    }

    private void notifyVisibilityChanged(boolean isVisible) {
        if (mNotificationViewController != null) {
            mNotificationViewController.onVisibilityChanged(isVisible);
        }
        try {
            if (isVisible) {
                mStatusBarService.onPanelRevealed(/* clearNotificationEffects= */ true,
                        mNotificationDataManager.getVisibleNotifications().size());
            } else {
                mStatusBarService.onPanelHidden();
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "Unable to report notification visibility changes", ex);
        }
    }
}
