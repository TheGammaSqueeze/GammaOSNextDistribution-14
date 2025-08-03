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

package com.android.car.oemcarservice.testapp;

import android.car.CarVersion;
import android.car.oem.OemCarAudioDuckingService;
import android.car.oem.OemCarAudioFocusService;
import android.car.oem.OemCarAudioVolumeService;
import android.car.oem.OemCarService;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;

import java.io.FileDescriptor;
import java.io.PrintWriter;

public final class OemCarServiceImpl extends OemCarService {

    private static final String TAG = OemCarServiceImpl.class.getSimpleName();
    private static final boolean DEBUG = true;
    private static final CarVersion SUPPORTED_CAR_VERSION =
            CarVersion.VERSION_CODES.UPSIDE_DOWN_CAKE_0;

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private OemCarAudioVolumeServiceImp mOemCarAudioVolumeService;
    @GuardedBy("mLock")
    private OemCarAudioFocusServiceImpl mOemCarAudioFocusServiceImpl;
    @GuardedBy("mLock")
    private OemCarAudioDuckingServiceImpl mOemCarAudioDuckingService;

    @Override
    public void onCreate() {
        if (DEBUG) {
            Slog.d(TAG, "onCreate");
        }
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Slog.d(TAG, "onDestroy");
        }
        // Releases resource from subcomponents.
        super.onDestroy();
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (DEBUG) {
            Slog.d(TAG, "dump");
        }
        writer.println("Dump OemCarServiceImpl");
        writer.printf("\tSUPPORTED_CAR_VERSION: %s\n", SUPPORTED_CAR_VERSION);
        super.dump(fd, writer, args);
    }

    @Override
    public OemCarAudioFocusService getOemAudioFocusService() {
        if (DEBUG) {
            Slog.d(TAG, "getOemAudioFocusService returning car audio focus service");
        }
        synchronized (mLock) {
            if (mOemCarAudioFocusServiceImpl == null) {
                mOemCarAudioFocusServiceImpl = new OemCarAudioFocusServiceImpl(
                    getApplicationContext());
            }
            return mOemCarAudioFocusServiceImpl;
        }
    }

    @Override
    public OemCarAudioDuckingService getOemAudioDuckingService() {
        if (DEBUG) {
            Slog.d(TAG, "getOemAudioDuckingService returning car ducking service");
        }
        synchronized (mLock) {
            if (mOemCarAudioDuckingService == null) {
                mOemCarAudioDuckingService = new OemCarAudioDuckingServiceImpl(
                    getApplicationContext());
            }
            return mOemCarAudioDuckingService;
        }
    }

    @Override
    public OemCarAudioVolumeService getOemAudioVolumeService() {
        if (DEBUG) {
            Slog.d(TAG, "getOemAudioVolumeService returning car ducking service");
        }

        synchronized (mLock) {
            if (mOemCarAudioVolumeService == null) {
                mOemCarAudioVolumeService = new OemCarAudioVolumeServiceImp(
                    getApplicationContext());
            }
            return mOemCarAudioVolumeService;
        }
    }

    @Override
    public void onCarServiceReady() {
        if (DEBUG) {
            Slog.d(TAG, "onCarServiceReady");
        }
    }

    @Override
    public CarVersion getSupportedCarVersion() {
        if (DEBUG) {
            Slog.d(TAG, "OemCarServiceImpl getSupportedCarVersion called");
        }
        return SUPPORTED_CAR_VERSION;
    }
}
