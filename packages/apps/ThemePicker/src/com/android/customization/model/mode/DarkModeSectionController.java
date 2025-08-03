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
package com.android.customization.model.mode;


import static android.Manifest.permission.MODIFY_DAY_NIGHT_MODE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED;

import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.annotation.MainThread;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.android.customization.module.logging.ThemesUserEventLogger;
import com.android.customization.picker.mode.DarkModeSectionView;
import com.android.themepicker.R;
import com.android.wallpaper.model.CustomizationSectionController;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Section for dark theme toggle that controls if this section will be shown visually. */
public class DarkModeSectionController implements
        CustomizationSectionController<DarkModeSectionView>, LifecycleObserver {

    private static final ExecutorService sExecutorService = Executors.newSingleThreadExecutor();

    private final Lifecycle mLifecycle;
    private final PowerManager mPowerManager;
    private final BatterySaverStateReceiver mBatterySaverStateReceiver =
            new BatterySaverStateReceiver();

    private Context mContext;
    private DarkModeSectionView mDarkModeSectionView;
    private final DarkModeSnapshotRestorer mSnapshotRestorer;
    private final ThemesUserEventLogger mThemesUserEventLogger;

    public DarkModeSectionController(
            Context context,
            Lifecycle lifecycle,
            DarkModeSnapshotRestorer snapshotRestorer,
            ThemesUserEventLogger themesUserEventLogger) {
        mContext = context;
        mLifecycle = lifecycle;
        mPowerManager = context.getSystemService(PowerManager.class);
        mLifecycle.addObserver(this);
        mSnapshotRestorer = snapshotRestorer;
        mThemesUserEventLogger = themesUserEventLogger;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    @MainThread
    public void onStart() {
        sExecutorService.submit(() -> {
            if (mContext != null && mLifecycle.getCurrentState().isAtLeast(
                    Lifecycle.State.STARTED)) {
                mContext.registerReceiver(mBatterySaverStateReceiver,
                        new IntentFilter(ACTION_POWER_SAVE_MODE_CHANGED));
            }
        });
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    @MainThread
    public void onStop() {
        sExecutorService.submit(() -> {
            if (mContext != null && mBatterySaverStateReceiver != null) {
                mContext.unregisterReceiver(mBatterySaverStateReceiver);
            }
        });
    }

    @Override
    public void release() {
        mLifecycle.removeObserver(this);
        mContext = null;
    }

    @Override
    public boolean isAvailable(Context context) {
        if (context == null) {
            return false;
        }
        return ContextCompat.checkSelfPermission(context, MODIFY_DAY_NIGHT_MODE)
                == PERMISSION_GRANTED;
    }

    @Override
    public DarkModeSectionView createView(Context context) {
        mDarkModeSectionView = (DarkModeSectionView) LayoutInflater.from(
                context).inflate(R.layout.dark_mode_section_view, /* root= */ null);
        mDarkModeSectionView.setViewListener(this::onViewActivated);
        PowerManager pm = context.getSystemService(PowerManager.class);
        mDarkModeSectionView.setEnabled(!pm.isPowerSaveMode());
        return mDarkModeSectionView;
    }

    private void onViewActivated(Context context, boolean viewActivated) {
        if (context == null) {
            return;
        }
        MaterialSwitch switchView = mDarkModeSectionView.findViewById(R.id.dark_mode_toggle);
        if (!switchView.isEnabled()) {
            Toast disableToast = Toast.makeText(mContext,
                    mContext.getString(R.string.mode_disabled_msg), Toast.LENGTH_SHORT);
            disableToast.show();
            return;
        }
        UiModeManager uiModeManager = context.getSystemService(UiModeManager.class);
        int shortDelay = context.getResources().getInteger(android.R.integer.config_shortAnimTime);
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> {
                    mDarkModeSectionView.announceForAccessibility(
                            context.getString(R.string.mode_changed));
                    uiModeManager.setNightModeActivated(viewActivated);
                    mThemesUserEventLogger.logDarkThemeApplied(viewActivated);
                    mSnapshotRestorer.store(viewActivated);
                },
                /* delayMillis= */ shortDelay);
    }

    private class BatterySaverStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(intent.getAction(), ACTION_POWER_SAVE_MODE_CHANGED)
                    && mDarkModeSectionView != null) {
                mDarkModeSectionView.setEnabled(!mPowerManager.isPowerSaveMode());
            }
        }
    }
}
