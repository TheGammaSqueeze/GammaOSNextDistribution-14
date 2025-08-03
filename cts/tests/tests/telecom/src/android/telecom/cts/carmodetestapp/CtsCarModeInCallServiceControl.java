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

package android.telecom.cts.carmodetestapp;

import android.app.Service;
import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Control class for the car mode app; allows CTS tests to perform operations using the car mode
 * test app.
 */
public class CtsCarModeInCallServiceControl extends Service {
    private static final String TAG = CtsCarModeInCallServiceControl.class.getSimpleName();
    public static final String CONTROL_INTERFACE_ACTION =
            "android.telecom.cts.carmodetestapp.ACTION_CAR_MODE_CONTROL";
    public static final ComponentName CONTROL_INTERFACE_COMPONENT =
            new ComponentName(CtsCarModeInCallServiceControl.class.getPackage().getName(),
                    CtsCarModeInCallServiceControl.class.getName());

    private final IBinder mCtsControl = new ICtsCarModeInCallServiceControl.Stub() {
        @Override
        public boolean isBound() {
            return CtsCarModeInCallService.isBound();
        }

        @Override
        public boolean isUnbound() {
            return CtsCarModeInCallService.isUnbound();
        }

        @Override
        public void reset() {
            CtsCarModeInCallService.reset();
            UiModeManager uiModeManager = getSystemService(UiModeManager.class);
            uiModeManager.disableCarMode(0);
        }

        @Override
        public void disconnectCalls() {
            if (CtsCarModeInCallService.getInstance() != null) {
                CtsCarModeInCallService.getInstance().disconnectCalls();
            }
        }

        @Override
        public int getCallCount() {
            if (CtsCarModeInCallService.getInstance() != null) {
                return CtsCarModeInCallService.getInstance().getCallCount();
            }
            // if there's no instance, there's no calls
            return 0;
        }

        @Override
        public void enableCarMode(int priority) {
            UiModeManager uiModeManager = getSystemService(UiModeManager.class);
            uiModeManager.enableCarMode(priority, 0);
        }

        @Override
        public void disableCarMode() {
            UiModeManager uiModeManager = getSystemService(UiModeManager.class);
            uiModeManager.disableCarMode(0);
        }

        @Override
        public boolean requestAutomotiveProjection() {
            UiModeManager uiModeManager = getSystemService(UiModeManager.class);
            return uiModeManager.requestProjection(UiModeManager.PROJECTION_TYPE_AUTOMOTIVE);
        }

        @Override
        public void releaseAutomotiveProjection() {
            UiModeManager uiModeManager = getSystemService(UiModeManager.class);
            uiModeManager.releaseProjection(UiModeManager.PROJECTION_TYPE_AUTOMOTIVE);
        }

        @Override
        public boolean checkBindStatus(boolean bind) {
            return CtsCarModeInCallService.checkBindStatus(bind);
        }

        @Override
        public List<PhoneAccountHandle> getSelfManagedPhoneAccounts() {
            TelecomManager telecomManager = getSystemService(TelecomManager.class);
            return (telecomManager != null) ? telecomManager.getSelfManagedPhoneAccounts()
                    : new ArrayList<>();
        }

        @Override
        public List<PhoneAccountHandle> getOwnSelfManagedPhoneAccounts() {
            TelecomManager telecomManager = getSystemService(TelecomManager.class);
            return (telecomManager != null) ? telecomManager.getOwnSelfManagedPhoneAccounts()
                    : new ArrayList<>();
        }

        @Override
        public void registerPhoneAccount(PhoneAccount phoneAccount) {
            TelecomManager telecomManager = getSystemService(TelecomManager.class);
            if (telecomManager != null) {
                telecomManager.registerPhoneAccount(phoneAccount);
            }
        }

        @Override
        public void unregisterPhoneAccount(PhoneAccountHandle phoneAccountHandle) {
            TelecomManager telecomManager = getSystemService(TelecomManager.class);
            if (telecomManager != null) {
                telecomManager.unregisterPhoneAccount(phoneAccountHandle);
            }
        }

        @Override
        public boolean checkCallAddedStatus() {
            return CtsCarModeInCallService.getInstance().checkCallAddedStatus();
        }

        @Override
        public int getCallVideoState() {
            return CtsCarModeInCallService.getInstance().getLastCall().getDetails().getVideoState();
        }

        @Override
        public int getCallState() {
            return CtsCarModeInCallService.getInstance().getLastCall().getState();
        }

        @Override
        public void hold() {
            CtsCarModeInCallService.getInstance().getLastCall().hold();
        }

        @Override
        public void unhold() {
            CtsCarModeInCallService.getInstance().getLastCall().unhold();
        }

        @Override
        public void disconnect() {
            CtsCarModeInCallService.getInstance().getLastCall().disconnect();
        }

        @Override
        public void answerCall(int videoState) {
            CtsCarModeInCallService.getInstance().getLastCall().answer(videoState);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        if (CONTROL_INTERFACE_ACTION.equals(intent.getAction())) {
            Log.d(TAG, "onBind: return control interface.");
            return mCtsControl;
        }
        Log.d(TAG, "onBind: invalid intent.");
        return null;
    }
}
