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

package com.android.systemui.tv.media;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.MediaRouter2;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerExemptionManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.android.internal.widget.LinearLayoutManager;
import com.android.internal.widget.RecyclerView;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.media.MediaDevice;
import com.android.settingslib.media.flags.Flags;
import com.android.systemui.animation.DialogTransitionAnimator;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.media.dialog.MediaOutputController;
import com.android.systemui.media.nearby.NearbyMediaDevicesManager;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection;
import com.android.systemui.tv.res.R;

import java.util.Collections;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * A TV specific variation of the {@link com.android.systemui.media.dialog.MediaOutputDialog}.
 * This activity allows the user to select a default audio output, which is not based on the
 * currently playing media.
 * There are two entry points for the dialog, either by sending a broadcast via the
 * {@link com.android.systemui.media.dialog.MediaOutputDialogReceiver} or by calling
 * {@link MediaRouter2#showSystemOutputSwitcher()}
 */
public class TvMediaOutputDialogActivity extends Activity
        implements MediaOutputController.Callback {
    private static final String TAG = TvMediaOutputDialogActivity.class.getSimpleName();
    private static final boolean DEBUG = false;

    private TvMediaOutputController mMediaOutputController;
    private TvMediaOutputAdapter mAdapter;

    private final MediaSessionManager mMediaSessionManager;
    private final LocalBluetoothManager mLocalBluetoothManager;
    private final ActivityStarter mActivityStarter;
    private final CommonNotifCollection mCommonNotifCollection;
    private final DialogTransitionAnimator mDialogTransitionAnimator;
    private final NearbyMediaDevicesManager mNearbyMediaDevicesManager;
    private final AudioManager mAudioManager;
    private final PowerExemptionManager mPowerExemptionManager;
    private final KeyguardManager mKeyguardManager;
    private final FeatureFlags mFeatureFlags;
    private final UserTracker mUserTracker;

    protected final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
    private String mActiveDeviceId;

    @Inject
    public TvMediaOutputDialogActivity(
            MediaSessionManager mediaSessionManager,
            @Nullable LocalBluetoothManager localBluetoothManager,
            ActivityStarter activityStarter,
            CommonNotifCollection commonNotifCollection,
            DialogTransitionAnimator dialogTransitionAnimator,
            NearbyMediaDevicesManager nearbyMediaDevicesManager,
            AudioManager audioManager,
            PowerExemptionManager powerExemptionManager,
            KeyguardManager keyguardManager,
            FeatureFlags featureFlags,
            UserTracker userTracker) {
        mMediaSessionManager = mediaSessionManager;
        mLocalBluetoothManager = localBluetoothManager;
        mActivityStarter = activityStarter;
        mCommonNotifCollection = commonNotifCollection;
        mDialogTransitionAnimator = dialogTransitionAnimator;
        mNearbyMediaDevicesManager = nearbyMediaDevicesManager;
        mAudioManager = audioManager;
        mPowerExemptionManager = powerExemptionManager;
        mKeyguardManager = keyguardManager;
        mFeatureFlags = featureFlags;
        mUserTracker = userTracker;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "package name: " + getPackageName());

        if (!Flags.enableTvMediaOutputDialog()) {
            finish();
            return;
        }

        setContentView(R.layout.media_output_dialog);

        mMediaOutputController = new TvMediaOutputController(this, getPackageName(),
                mMediaSessionManager, mLocalBluetoothManager, mActivityStarter,
                mCommonNotifCollection, mDialogTransitionAnimator, mNearbyMediaDevicesManager,
                mAudioManager, mPowerExemptionManager, mKeyguardManager, mFeatureFlags,
                mUserTracker);
        mAdapter = new TvMediaOutputAdapter(this, mMediaOutputController, this);

        Resources res = getResources();
        DisplayMetrics metrics = res.getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        int marginVerticalPx = res.getDimensionPixelSize(R.dimen.media_dialog_margin_vertical);
        int marginEndPx = res.getDimensionPixelSize(R.dimen.media_dialog_margin_end);

        final Window window = getWindow();
        final WindowManager.LayoutParams lp = window.getAttributes();
        lp.gravity = Gravity.getAbsoluteGravity(Gravity.TOP | Gravity.END,
                res.getConfiguration().getLayoutDirection());
        lp.width = res.getDimensionPixelSize(R.dimen.media_dialog_width);
        lp.height = screenHeight - 2 * marginVerticalPx;
        lp.horizontalMargin = ((float) marginEndPx) / screenWidth;
        lp.verticalMargin = ((float) marginVerticalPx) / screenHeight;
        window.setBackgroundDrawableResource(R.drawable.media_dialog_background);
        window.setAttributes(lp);
        window.setElevation(getWindow().getElevation() + 5);
        window.setTitle(getString(
                com.android.systemui.R.string.media_output_dialog_accessibility_title));

        window.getDecorView().addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom)
                        -> findViewById(android.R.id.content).setUnrestrictedPreferKeepClearRects(
                        Collections.singletonList(new Rect(left, top, right, bottom))));

        RecyclerView devicesRecyclerView = requireViewById(R.id.device_list);
        devicesRecyclerView.setLayoutManager(new LayoutManagerWrapper(this));
        devicesRecyclerView.setAdapter(mAdapter);

        int itemSpacingPx = getResources().getDimensionPixelSize(R.dimen.media_dialog_item_spacing);
        devicesRecyclerView.addItemDecoration(new SpacingDecoration(itemSpacingPx));
    }

    @Override
    public void onStart() {
        super.onStart();
        mMediaOutputController.start(this);
    }

    @Override
    public void onStop() {
        mMediaOutputController.stop();
        super.onStop();
    }

    private void refresh(boolean deviceSetChanged) {
        if (DEBUG) Log.d(TAG, "refresh: deviceSetChanged " + deviceSetChanged);
        // If the dialog is going away or is already refreshing, do nothing.
        if (mMediaOutputController.isRefreshing()) {
            return;
        }
        mMediaOutputController.setRefreshing(true);
        mAdapter.updateItems();
    }

    @Override
    public void onMediaChanged() {
        // NOOP
    }

    @Override
    public void onMediaStoppedOrPaused() {
        // NOOP
    }

    @Override
    public void onRouteChanged() {
        mMainThreadHandler.post(() -> refresh(/* deviceSetChanged= */ false));
        MediaDevice activeDevice = mMediaOutputController.getCurrentConnectedMediaDevice();
        if (mActiveDeviceId != null && !mActiveDeviceId.equals(activeDevice.getId())) {
            mMediaOutputController.showVolumeDialog();
        }
        mActiveDeviceId = activeDevice.getId();
    }

    @Override
    public void onDeviceListChanged() {
        mMainThreadHandler.post(() -> refresh(/* deviceSetChanged= */ true));
        if (mActiveDeviceId == null
                && mMediaOutputController.getCurrentConnectedMediaDevice() != null) {
            mActiveDeviceId = mMediaOutputController.getCurrentConnectedMediaDevice().getId();
        }
    }

    @Override
    public void dismissDialog() {
        if (DEBUG) Log.d(TAG, "dismissDialog");
        finish();
    }

    private class LayoutManagerWrapper extends LinearLayoutManager {
        LayoutManagerWrapper(Context context) {
            super(context);
        }

        @Override
        public void onLayoutCompleted(RecyclerView.State state) {
            super.onLayoutCompleted(state);
            mMediaOutputController.setRefreshing(false);
            mMediaOutputController.refreshDataSetIfNeeded();
        }
    }

    private static class SpacingDecoration extends RecyclerView.ItemDecoration {
        private final int mMarginPx;

        SpacingDecoration(int marginPx) {
            mMarginPx = marginPx;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            if (parent.getChildAdapterPosition(view) == 0) {
                outRect.top = mMarginPx;
            }
            outRect.bottom = mMarginPx;
        }
    }
}
