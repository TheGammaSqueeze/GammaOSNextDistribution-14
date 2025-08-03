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

package com.android.systemui.car.statusicon.ui;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;

import com.android.systemui.R;
import com.android.systemui.car.statusicon.StatusIconController;
import com.android.systemui.car.statusicon.StatusIconPanelViewController;
import com.android.systemui.car.systembar.DistantDisplayController;
import com.android.systemui.dagger.qualifiers.Main;

import javax.inject.Inject;
import javax.inject.Provider;

public class DistantDisplayStatusIconController extends StatusIconController implements
        DistantDisplayController.StatusChangeListener {
    public static final String TAG = DistantDisplayStatusIconController.class.getSimpleName();
    private static final int DEFAULT_DISPLAY_ID = 0;
    private final Provider<StatusIconPanelViewController.Builder> mPanelControllerBuilderProvider;
    private final DistantDisplayController mDistantDisplayController;
    private final Drawable mDistantDisplayDrawable;
    private final Drawable mDefaultDisplayDrawable;
    private ImageView mDistantDisplayButton;
    private int mCurrentDisplayId;

    private StatusIconPanelViewController mDistantDisplayPanelController;

    @Inject
    DistantDisplayStatusIconController(
            Provider<StatusIconPanelViewController.Builder> panelControllerBuilderProvider,
            DistantDisplayController distantDisplayController,
            @Main Resources resources) {
        mPanelControllerBuilderProvider = panelControllerBuilderProvider;
        mDistantDisplayController = distantDisplayController;
        mDistantDisplayDrawable = resources.getDrawable(
                R.drawable.ic_sys_ui_send_to_distant_display, /* theme= */ null);
        mDefaultDisplayDrawable = resources.getDrawable(
                R.drawable.ic_sys_ui_bring_back, /* theme= */ null);
    }

    /**
     * Find the {@link DistantDisplayButton} from a view and sets button state.
     */
    public void addDistantDisplayButtonView(View view) {
        if (mDistantDisplayButton != null) return;

        mDistantDisplayButton = view.findViewById(getId());

        if (mDistantDisplayButton == null) {
            Log.e(TAG, "Distant Display button view not found to initialize button and panel.");
            return;
        }

        mDistantDisplayPanelController = mPanelControllerBuilderProvider.get()
                .setGravity(Gravity.TOP | Gravity.END).build(mDistantDisplayButton,
                        getPanelContentLayout(), R.dimen.car_profile_quick_controls_panel_width);
        mDistantDisplayPanelController.init();
        registerIconView(mDistantDisplayButton);
        mDistantDisplayController.setDistantDisplayControlStatusInfoListener(this);
    }

    @Override
    protected void updateStatus() {
        if (mCurrentDisplayId == DEFAULT_DISPLAY_ID) {
            setIconDrawableToDisplay(mDistantDisplayDrawable);
        } else {
            setIconDrawableToDisplay(mDefaultDisplayDrawable);
        }
        onStatusUpdated();
    }

    @Override
    public void onDestroy() {
        if (mDistantDisplayButton != null) {
            unregisterIconView(mDistantDisplayButton);
        }
        mDistantDisplayController.removeDistantDisplayControlStatusInfoListener();
        mDistantDisplayButton = null;
    }

    @Override
    protected int getId() {
        return R.id.distant_display_nav;
    }

    @Override
    protected int getPanelContentLayout() {
        return R.layout.qc_distant_display_panel;
    }

    @Override
    public void onDisplayChanged(int displayId) {
        mCurrentDisplayId = displayId;
        updateStatus();
    }

    @Override
    public void onVisibilityChanged(boolean visible) {
        setIconVisibility(visible);
        onStatusUpdated();
    }
}
