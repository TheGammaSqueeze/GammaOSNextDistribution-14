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

package com.android.systemui.car.displayarea;

import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_REGISTER_CLIENT;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.MSG_SYSUI_STARTED;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import com.android.car.caruiportrait.common.service.CarUiPortraitService;
import com.android.systemui.CoreStartable;
import com.android.systemui.dagger.SysUISingleton;

import javax.inject.Inject;

/**
 * Dagger Subcomponent for DisplayAreas within SysUI.
 */
@SysUISingleton
public class DisplayAreaComponent implements CoreStartable {
    public static final String TAG = "DisplayAreaComponent";
    // action name for the intent when to update the foreground DA visibility
    public static final String DISPLAY_AREA_VISIBILITY_CHANGED =
            "DISPLAY_AREA_VISIBILITY_CHANGED";

    private final CarDisplayAreaController mCarDisplayAreaController;
    private final Context mContext;

    /** Messenger for communicating with {@link CarUiPortraitService}. */
    Messenger mService = null;
    /** Flag indicating whether or not {@link CarUiPortraitService} is bounded. */
    boolean mIsBound;

    /**
     * All messages from {@link CarUiPortraitService} are received in this handler.
     */
    final Messenger mMessenger = new Messenger(new Handler());

    /**
     * Class for interacting with the main interface of the {@link CarUiPortraitService}.
     */
    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // Communicating with our service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = new Messenger(service);

            // Register to the service.
            try {
                Message msg = Message.obtain(null, MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
                Message msg1 = Message.obtain(null, MSG_SYSUI_STARTED);
                msg1.replyTo = mMessenger;
                mService.send(msg1);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
                Log.w(TAG, "can't connect to CarUiPortraitService: ", e);
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
        }
    };

    void doBindService() {
        // Establish a connection with {@link CarUiPortraitService}. We use an explicit class
        // name because there is no reason to be able to let other applications replace our
        // component.
        // com.android.car.portraitlauncher/com.android.car.caruiportrait.common.service
        // .CarUiPortraitService
        Intent intent = new Intent();
        String pkg = "com.android.car.portraitlauncher";
        String cls = "com.android.car.caruiportrait.common.service.CarUiPortraitService";
        intent.setComponent(new ComponentName(pkg, cls));
        UserHandle user = new UserHandle(ActivityManager.getCurrentUser());
        mContext.bindServiceAsUser(intent, mConnection,
                Context.BIND_AUTO_CREATE, user);
        mIsBound = true;
    }

    @Inject
    public DisplayAreaComponent(Context context,
            CarDisplayAreaController carDisplayAreaController) {
        mContext = context;
        mCarDisplayAreaController = carDisplayAreaController;
    }

    private static void logIfDebuggable(String message) {
        if (Build.IS_DEBUGGABLE) {
            Log.d(TAG, message);
        }
    }

    @Override
    public void start() {
        if (mContext.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            logIfDebuggable("early exit due to landscape orientation");
            return;
        }

        logIfDebuggable("start:");
        mCarDisplayAreaController.register();
        doBindService();
    }
}
