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

package com.android.systemui.car.systembar;

import static android.hardware.SensorPrivacyManager.Sources.QS_TILE;
import static android.hardware.SensorPrivacyManager.TOGGLE_TYPE_SOFTWARE;

import android.content.Context;
import android.hardware.SensorPrivacyManager;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import com.android.systemui.car.privacy.PrivacyChip;
import com.android.systemui.car.privacy.SensorInfoUpdateListener;
import com.android.systemui.car.privacy.SensorQcPanel;
import com.android.systemui.privacy.OngoingPrivacyChip;
import com.android.systemui.privacy.PrivacyItem;
import com.android.systemui.privacy.PrivacyItemController;
import com.android.systemui.privacy.PrivacyType;
import com.android.systemui.settings.UserTracker;

import java.util.List;
import java.util.Optional;

/** Controls a Privacy Chip view in system icons. */
public abstract class PrivacyChipViewController implements SensorQcPanel.SensorInfoProvider {

    private final PrivacyItemController mPrivacyItemController;
    private final SensorPrivacyManager mSensorPrivacyManager;
    private final UserTracker mUserTracker;

    private Context mContext;
    private PrivacyChip mPrivacyChip;
    private Runnable mQsTileNotifyUpdateRunnable;
    private SensorInfoUpdateListener mSensorInfoUpdateListener;
    private final SensorPrivacyManager.OnSensorPrivacyChangedListener
            mOnSensorPrivacyChangedListener = (sensor, sensorPrivacyEnabled) -> {
        if (mContext == null) {
            return;
        }
        // Since this is launched using a callback thread, its UI based elements need
        // to execute on main executor.
        mContext.getMainExecutor().execute(() -> {
            // We need to negate enabled since when it is {@code true} it means
            // the sensor (such as microphone or camera) has been toggled off.
            mPrivacyChip.setSensorEnabled(/* enabled= */ !sensorPrivacyEnabled);
            mQsTileNotifyUpdateRunnable.run();
            if (mSensorInfoUpdateListener != null) {
                mSensorInfoUpdateListener.onSensorPrivacyChanged();
            }
        });
    };

    private final UserTracker.Callback mUserSwitchCallback = new UserTracker.Callback() {
        @Override
        public void onUserChanged(int newUser, Context userContext) {
            mPrivacyChip.setSensorEnabled(isSensorEnabled());
        }
    };

    private boolean mAllIndicatorsEnabled;
    private boolean mMicCameraIndicatorsEnabled;
    private boolean mIsPrivacyChipVisible;
    private final PrivacyItemController.Callback mPicCallback =
            new PrivacyItemController.Callback() {
                @Override
                public void onPrivacyItemsChanged(@NonNull List<PrivacyItem> privacyItems) {
                    if (mPrivacyChip == null) {
                        return;
                    }

                    // Call QS Tile notify update runnable here so that QS tile can update when app
                    // usage is added/removed/updated
                    mQsTileNotifyUpdateRunnable.run();

                    boolean shouldShowPrivacyChip = isSensorPartOfPrivacyItems(privacyItems);
                    if (mIsPrivacyChipVisible == shouldShowPrivacyChip) {
                        return;
                    }

                    mIsPrivacyChipVisible = shouldShowPrivacyChip;
                    setChipVisibility(shouldShowPrivacyChip);

                    if (mSensorInfoUpdateListener != null) {
                        mSensorInfoUpdateListener.onPrivacyItemsChanged();
                    }
                }

                @Override
                public void onFlagAllChanged(boolean enabled) {
                    onAllIndicatorsToggled(enabled);
                }

                @Override
                public void onFlagMicCameraChanged(boolean enabled) {
                    onMicCameraToggled(enabled);
                }

                private void onMicCameraToggled(boolean enabled) {
                    if (mMicCameraIndicatorsEnabled != enabled) {
                        mMicCameraIndicatorsEnabled = enabled;
                    }
                }

                private void onAllIndicatorsToggled(boolean enabled) {
                    if (mAllIndicatorsEnabled != enabled) {
                        mAllIndicatorsEnabled = enabled;
                    }
                }
            };

    public PrivacyChipViewController(Context context, PrivacyItemController privacyItemController,
            SensorPrivacyManager sensorPrivacyManager, UserTracker userTracker) {
        mContext = context;
        mPrivacyItemController = privacyItemController;
        mSensorPrivacyManager = sensorPrivacyManager;
        mUserTracker = userTracker;

        mQsTileNotifyUpdateRunnable = () -> {
        };
        mIsPrivacyChipVisible = false;
    }

    @Override
    public boolean isSensorEnabled() {
        // We need to negate return of isSensorPrivacyEnabled since when it is {@code true} it
        // means the sensor (microphone/camera) has been toggled off
        return !mSensorPrivacyManager.isSensorPrivacyEnabled(/* toggleType= */ TOGGLE_TYPE_SOFTWARE,
                /* sensor= */ getChipSensor());
    }

    @Override
    public void toggleSensor() {
        mSensorPrivacyManager.setSensorPrivacy(/* source= */ QS_TILE, /* sensor= */ getChipSensor(),
                /* enable= */ isSensorEnabled());
    }

    @Override
    public void setNotifyUpdateRunnable(Runnable runnable) {
        mQsTileNotifyUpdateRunnable = runnable;
    }

    @Override
    public void setSensorInfoUpdateListener(SensorInfoUpdateListener listener) {
        mSensorInfoUpdateListener = listener;
    }

    protected abstract @SensorPrivacyManager.Sensors.Sensor int getChipSensor();

    protected abstract PrivacyType getChipPrivacyType();

    protected abstract @IdRes int getChipResourceId();

    private boolean isSensorPartOfPrivacyItems(@NonNull List<PrivacyItem> privacyItems) {
        Optional<PrivacyItem> optionalSensorPrivacyItem = privacyItems.stream()
                .filter(privacyItem -> privacyItem.getPrivacyType()
                        .equals(getChipPrivacyType()))
                .findAny();
        return optionalSensorPrivacyItem.isPresent();
    }

    /**
     * Finds the {@link OngoingPrivacyChip} and sets relevant callbacks.
     */
    public void addPrivacyChipView(View view) {
        if (mPrivacyChip != null) {
            return;
        }

        mPrivacyChip = view.findViewById(getChipResourceId());
        if (mPrivacyChip == null) return;

        mAllIndicatorsEnabled = mPrivacyItemController.getAllIndicatorsAvailable();
        mMicCameraIndicatorsEnabled = mPrivacyItemController.getMicCameraAvailable();
        mPrivacyItemController.addCallback(mPicCallback);

        mSensorPrivacyManager.removeSensorPrivacyListener(getChipSensor(),
                mOnSensorPrivacyChangedListener);
        mSensorPrivacyManager.addSensorPrivacyListener(getChipSensor(),
                mOnSensorPrivacyChangedListener);

        // Since this can be launched using a callback thread, its UI based elements need
        // to execute on main executor.
        mContext.getMainExecutor().execute(() -> {
            mPrivacyChip.setSensorEnabled(isSensorEnabled());
        });
        mUserTracker.removeCallback(mUserSwitchCallback);
        mUserTracker.addCallback(mUserSwitchCallback, mContext.getMainExecutor());
    }

    /**
     * Cleans up the controller and removes callbacks.
     */
    public void removeAll() {
        if (mPrivacyChip != null) {
            mPrivacyChip.setOnClickListener(null);
        }

        mIsPrivacyChipVisible = false;
        mPrivacyItemController.removeCallback(mPicCallback);
        mSensorPrivacyManager.removeSensorPrivacyListener(getChipSensor(),
                mOnSensorPrivacyChangedListener);
        mUserTracker.removeCallback(mUserSwitchCallback);
        mPrivacyChip = null;
        mSensorInfoUpdateListener = null;
    }

    private void setChipVisibility(boolean chipVisible) {
        if (mPrivacyChip == null) {
            return;
        }

        // Since this is launched using a callback thread, its UI based elements need
        // to execute on main executor.
        mContext.getMainExecutor().execute(() -> {
            if (chipVisible && getChipEnabled()) {
                mPrivacyChip.animateIn();
            } else {
                mPrivacyChip.animateOut();
            }
        });
    }

    private boolean getChipEnabled() {
        return mMicCameraIndicatorsEnabled || mAllIndicatorsEnabled;
    }
}
