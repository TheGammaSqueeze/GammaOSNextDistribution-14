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

package com.android.systemui.car;

import android.annotation.NonNull;
import android.car.settings.CarSettings;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.DeviceProvisionedControllerImpl;
import com.android.systemui.util.settings.GlobalSettings;
import com.android.systemui.util.settings.SecureSettings;
import com.android.systemui.util.wrapper.BuildInfo;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import kotlin.Unit;

/**
 * A controller that monitors the status of SUW progress for each user in addition to the
 * functionality provided by {@link DeviceProvisionedControllerImpl}.
 */
@SysUISingleton
public class CarDeviceProvisionedControllerImpl extends DeviceProvisionedControllerImpl implements
        CarDeviceProvisionedController {
    private final UserTracker mUserTracker;
    private final Uri mUserSetupInProgressUri;
    private final ContentObserver mCarSettingsObserver;
    private final Handler mMainHandler;
    private final SecureSettings mSecureSettings;
    private final AtomicBoolean mInitted = new AtomicBoolean(false);

    @Inject
    public CarDeviceProvisionedControllerImpl(
            SecureSettings secureSettings,
            GlobalSettings globalSettings,
            UserTracker userTracker,
            DumpManager dumpManager,
            BuildInfo buildInfo,
            @Background Handler backgroundHandler,
            @Main Handler mainHandler,
            @Main Executor mainExecutor) {
        super(secureSettings, globalSettings, userTracker, dumpManager, buildInfo,
                backgroundHandler, mainExecutor);

        mUserTracker = userTracker;
        mMainHandler = mainHandler;
        mSecureSettings = secureSettings;
        mUserSetupInProgressUri = mSecureSettings.getUriFor(
                CarSettings.Secure.KEY_SETUP_WIZARD_IN_PROGRESS);
        mCarSettingsObserver = new ContentObserver(mMainHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri, int flags) {
                if (mUserSetupInProgressUri.equals(uri)) {
                    notifyUserSetupInProgressChanged();
                }
            }
        };
    }

    @Override
    public void init() {
        if (!mInitted.compareAndSet(false, true)) {
            return;
        }
        mSecureSettings.registerContentObserverForUser(
                mUserSetupInProgressUri, /* notifyForDescendants= */ true,
                mCarSettingsObserver, UserHandle.USER_ALL);
        super.init();
    }

    @Override
    public boolean isUserSetupInProgress(int user) {
        return mSecureSettings.getIntForUser(
                CarSettings.Secure.KEY_SETUP_WIZARD_IN_PROGRESS, /* def= */ 0, user) != 0;
    }

    @Override
    public boolean isCurrentUserSetupInProgress() {
        return isUserSetupInProgress(mUserTracker.getUserId());
    }

    @Override
    public void addCallback(@NonNull DeviceProvisionedListener listener) {
        super.addCallback(listener);
        if (listener instanceof CarDeviceProvisionedListener) {
            ((CarDeviceProvisionedListener) listener).onUserSetupInProgressChanged();
        }
    }

    private void notifyUserSetupInProgressChanged() {
        dispatchChange(listener -> {
            if (listener instanceof CarDeviceProvisionedListener) {
                ((CarDeviceProvisionedListener) listener).onUserSetupInProgressChanged();
            }
            return Unit.INSTANCE;
        });
    }
}
