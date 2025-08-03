/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.car.userswitcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.car.Car;
import android.car.user.CarUserManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.GridLayoutManager;

import com.android.settingslib.applications.InterestingConfigChanges;
import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.window.OverlayViewController;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.ConfigurationController;

import javax.inject.Inject;

/**
 * Controller for {@link R.layout#car_fullscreen_user_switcher}.
 */
@SysUISingleton
public class FullScreenUserSwitcherViewController extends OverlayViewController
        implements ConfigurationController.ConfigurationListener {
    private final Context mContext;
    private final UserTracker mUserTracker;
    private final Resources mResources;
    private final CarServiceProvider mCarServiceProvider;
    private final int mShortAnimationDuration;
    private CarUserManager mCarUserManager;
    private UserGridRecyclerView mUserGridView;
    private UserGridRecyclerView.UserSelectionListener mUserSelectionListener;
    private final InterestingConfigChanges mConfigChanges = new InterestingConfigChanges(
            ActivityInfo.CONFIG_UI_MODE);


    @Inject
    public FullScreenUserSwitcherViewController(
            Context context,
            UserTracker userTracker,
            @Main Resources resources,
            ConfigurationController configurationController,
            CarServiceProvider carServiceProvider,
            OverlayViewGlobalStateController overlayViewGlobalStateController) {
        super(R.id.fullscreen_user_switcher_stub, overlayViewGlobalStateController);
        mContext = context;
        mUserTracker = userTracker;
        mResources = resources;
        mCarServiceProvider = carServiceProvider;
        mCarServiceProvider.addListener(car -> {
            mCarUserManager = (CarUserManager) car.getCarManager(Car.CAR_USER_SERVICE);
            registerCarUserManagerIfPossible();
        });
        mShortAnimationDuration = mResources.getInteger(android.R.integer.config_shortAnimTime);
        mConfigChanges.applyNewConfig(mContext.getResources());
        configurationController.addCallback(this);
    }

    @Override
    protected void onFinishInflate() {
        initializeViews();
    }

    @Override
    public void onConfigChanged(Configuration configuration) {
        if (mConfigChanges.applyNewConfig(mContext.getResources())) {
            // prerequisites
            if (getLayout() == null) return;
            UserSwitcherContainer container = getLayout().findViewById(
                    R.id.user_switcher_container);
            if (container == null) return;
            ViewGroup viewGroupParent = (ViewGroup) container.getParent();

            // store index
            int viewIndex = viewGroupParent.indexOfChild(container);

            // reinflate
            viewGroupParent.removeView(container);
            UserSwitcherContainer newContainer = (UserSwitcherContainer) LayoutInflater.from(
                    mContext).inflate(R.layout.car_fullscreen_user_switcher,
                    viewGroupParent, /* attachToRoot= */ false);
            viewGroupParent.addView(newContainer, viewIndex);
            initializeViews();
        }
    }

    @Override
    protected int getFocusAreaViewId() {
        return R.id.user_switcher_focus_area;
    }

    @Override
    protected boolean shouldFocusWindow() {
        return true;
    }

    @Override
    protected void showInternal() {
        getLayout().setVisibility(View.VISIBLE);
    }

    @Override
    protected void hideInternal() {
        // Switching is about to happen, since it takes time, fade out the switcher gradually.
        fadeOut();
    }

    private void initializeViews() {
        // Intercept back button.
        UserSwitcherContainer container = getLayout().findViewById(R.id.user_switcher_container);
        container.setKeyEventHandler(event -> {
            if (event.getKeyCode() != KeyEvent.KEYCODE_BACK) {
                return false;
            }

            if (event.getAction() == KeyEvent.ACTION_UP && getLayout().isVisibleToUser()) {
                stop();
            }
            return true;
        });

        mUserGridView = getLayout().findViewById(R.id.user_grid);
        GridLayoutManager layoutManager = new GridLayoutManager(mContext,
                mResources.getInteger(R.integer.user_fullscreen_switcher_num_col));
        mUserGridView.setLayoutManager(layoutManager);
        mUserGridView.setUserTracker(mUserTracker);
        mUserGridView.buildAdapter();
        mUserGridView.setUserSelectionListener(mUserSelectionListener);
        registerCarUserManagerIfPossible();
    }

    private void fadeOut() {
        mUserGridView.animate()
                .alpha(0.0f)
                .setDuration(mShortAnimationDuration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        getLayout().setVisibility(View.GONE);
                        mUserGridView.setAlpha(1.0f);
                    }
                });

    }

    /**
     * Set {@link UserGridRecyclerView.UserSelectionListener}.
     */
    void setUserGridSelectionListener(
            UserGridRecyclerView.UserSelectionListener userGridSelectionListener) {
        mUserSelectionListener = userGridSelectionListener;
    }

    private void registerCarUserManagerIfPossible() {
        if (mUserGridView != null && mCarUserManager != null) {
            mUserGridView.setCarUserManager(mCarUserManager);
        }
    }
}
