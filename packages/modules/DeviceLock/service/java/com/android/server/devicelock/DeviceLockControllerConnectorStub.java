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

package com.android.server.devicelock;

import android.os.OutcomeReceiver;

/**
 * Stub implementation of the connector that is used when the device has had its restrictions
 * cleared so that we don't try to bind to a disabled package.
 */
public class DeviceLockControllerConnectorStub implements DeviceLockControllerConnector {

    @Override
    public void unbind() {}

    @Override
    public void lockDevice(OutcomeReceiver<Void, Exception> callback) {
        setException(callback);
    }

    @Override
    public void unlockDevice(OutcomeReceiver<Void, Exception> callback) {
        setException(callback);
    }

    @Override
    public void isDeviceLocked(OutcomeReceiver<Boolean, Exception> callback) {
        setException(callback);
    }

    @Override
    public void getDeviceId(OutcomeReceiver<String, Exception> callback) {
        setException(callback);
    }

    @Override
    public void clearDeviceRestrictions(OutcomeReceiver<Void, Exception> callback) {
        setException(callback);
    }

    @Override
    public void onUserSwitching(OutcomeReceiver<Void, Exception> callback) {
        // Do not throw exception as we expect this to be called
        callback.onResult(null);
    }

    @Override
    public void onUserUnlocked(OutcomeReceiver<Void, Exception> callback) {
        // Do not throw exception as we expect this to be called
        callback.onResult(null);
    }

    @Override
    public void onUserSetupCompleted(OutcomeReceiver<Void, Exception> callback) {
        // Do not throw exception as we expect this to be called
        callback.onResult(null);
    }

    @Override
    public void onAppCrashed(boolean isKiosk, OutcomeReceiver<Void, Exception> callback) {
        setException(callback);
    }

    private static void setException(OutcomeReceiver<?, Exception> callback) {
        callback.onError(new IllegalStateException("Device lock controller package is disabled."));
    }
}
