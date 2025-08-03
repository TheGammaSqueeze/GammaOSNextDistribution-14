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

package com.android.managedprovisioning.provisioning;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.StringRes;

import com.android.car.setupwizardlib.CarSetupWizardCompatLayout;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.CarSetupWizardLayoutHelper;
import com.android.managedprovisioning.common.InitializeLayoutConsumerHandler;
import com.android.managedprovisioning.common.StylerHelper;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.provisioning.ProvisioningActivity.ProvisioningMode;
import com.android.managedprovisioning.provisioning.ProvisioningModeWrapperProvider.ProvisioningModeWrapper;
import com.android.managedprovisioning.provisioning.TransitionAnimationHelper.AnimationComponents;
import com.android.managedprovisioning.provisioning.TransitionAnimationHelper.TransitionAnimationCallback;
import com.android.managedprovisioning.provisioning.TransitionAnimationHelper.TransitionAnimationStateManager;
import com.android.server.utils.Slogf;

import com.airbnb.lottie.LottieAnimationView;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import java.util.function.Supplier;

/**
 * A {@link ProvisioningActivityBridge} implementation meant for the base {@code
 * ManagedProvisioning}. Note: there is no progress label in AAOS's layout.
 */
@AutoValue
abstract class CarProvisioningActivityBridgeImpl implements ProvisioningActivityBridge {
    private static final String TAG = CarProvisioningActivityBridgeImpl.class.getSimpleName();

    private TransitionAnimationHelper mTransitionAnimationHelper;
    private View mPrimaryButtonView;
    private View mSecondaryButtonView;
    private View mProgressBar;

    abstract Utils getUtils();
    abstract ProvisioningParams getParams();
    abstract @ProvisioningMode int getProvisioningMode();
    abstract ProvisioningManager getProvisioningManager();
    abstract TransitionAnimationCallback getTransitionAnimationCallback();
    abstract InitializeLayoutConsumerHandler getInitializeLayoutParamsConsumer();
    abstract boolean getShouldSkipEducationScreens();
    abstract @StringRes int getProgressLabelResId();
    abstract ProvisioningActivityBridgeCallbacks getBridgeCallbacks();
    abstract TransitionAnimationStateManager getStateManager();

    @Override
    public void initiateUi(Activity activity) {
        boolean isPoProvisioning = getUtils().isProfileOwnerAction(getParams().provisioningAction);
        int titleResId =
                isPoProvisioning ? R.string.setup_profile_progress : R.string.setup_device_progress;
        int mainLayoutId = CarSetupWizardLayoutHelper.MAIN_LAYOUT_RES_ID;
        int subLayoutId = getShouldSkipEducationScreens()
                ? R.layout.empty_layout
                : R.layout.provision_layout;

        getInitializeLayoutParamsConsumer().initializeLayoutParams(
                mainLayoutId, /* headerResId= */ null);
        activity.setTitle(titleResId);

        setupBasicLayout(activity, subLayoutId);

        setupEducationViews(getShouldSkipEducationScreens(), getProgressLabelResId());
    }

    @Override
    public void onStart(Activity activity) {
        if (!getShouldSkipEducationScreens()) {
            startTransitionAnimation(activity, getTransitionAnimationCallback());
        }
        boolean show = activity.getResources().getBoolean(R.bool.show_progress_label_on_left_side);
        Slogf.d(TAG, "setupEducationViews(): onStart: show_progress_label_on_left_side: " + show);
    }

    @Override
    public void onStop() {
        if (!getShouldSkipEducationScreens()) {
            endTransitionAnimation();
        }
    }

    @Override
    public void onProvisioningFinalized(Activity activity) {
        if (!getShouldSkipEducationScreens()) {
            mPrimaryButtonView.setVisibility(View.VISIBLE);
            mSecondaryButtonView.setVisibility(View.VISIBLE);
            mProgressBar.setVisibility(View.GONE);
        }

        if (getShouldSkipEducationScreens()) {
            getBridgeCallbacks().onNextButtonClicked();
        }
    }

    private CarSetupWizardLayoutHelper setupBasicLayout(Activity activity, int subLayoutId) {
        CarSetupWizardLayoutHelper layoutHelper = new CarSetupWizardLayoutHelper(activity);
        CarSetupWizardCompatLayout layout = layoutHelper.setBaseLayout(
                subLayoutId, /* isDoubleColumnAllowed= */ false);
        setupButtons(layout, activity);
        setupProgressBar(layout);

        return layoutHelper;
    }

    private void startTransitionAnimation(Activity activity, TransitionAnimationCallback callback) {
        int subLayoutId = R.layout.provision_layout;
        CarSetupWizardLayoutHelper layoutHelper = setupBasicLayout(activity, subLayoutId);
        setupTransitionAnimationHelper(layoutHelper.getBaseLayout(), activity, callback);
        mTransitionAnimationHelper.start();
    }

    private void endTransitionAnimation() {
        mTransitionAnimationHelper.stop();
        mTransitionAnimationHelper = null;
    }

    private void setupTransitionAnimationHelper(
            CarSetupWizardCompatLayout layout,
            Activity activity,
            TransitionAnimationCallback callback) {
        TextView header = activity.findViewById(R.id.description_title);
        TextView description = activity.findViewById(R.id.description);

        ImageView logo = activity.findViewById(R.id.title_logo);
        logo.setImageResource(com.android.internal.R.drawable.ic_corp_badge_case);

        ViewGroup item1 = activity.findViewById(R.id.item1);
        ViewGroup item2 = activity.findViewById(R.id.item2);
        LottieAnimationView drawable = activity.findViewById(R.id.illustration);
        ViewGroup drawableContainer = activity.findViewById(R.id.illustration_container);
        Space space1 = layout.findViewById(R.id.space1);
        Space space2 = layout.findViewById(R.id.space2);
        AnimationComponents animationComponents =
                new AnimationComponents(
                        header, description, item1, item2, drawable, drawableContainer,
                        space1, space2);

        ProvisioningModeWrapper provisioningModeWrapper =
                getProvisioningModeWrapperForFullyManaged(activity.getApplicationContext());

        mTransitionAnimationHelper = new TransitionAnimationHelper(
                animationComponents,
                callback,
                getStateManager(),
                new StylerHelper(),
                provisioningModeWrapper);

        Slogf.d(TAG, "setupEducationViews(): shouldSkipEducationScreens: "
                + R.bool.show_edu_animations);
    }

    private ProvisioningModeWrapper getProvisioningModeWrapperForFullyManaged(Context context) {
        int provisioningSummaryId;
        TransitionScreenWrapper.Builder secondScreenBuilder =
                new TransitionScreenWrapper.Builder()
                        .setHeader(R.string.fully_managed_device_provisioning_step_2_header);

        if (!getParams().deviceOwnerPermissionGrantOptOut) {
            secondScreenBuilder
                    .setSubHeaderTitle(
                            R.string.fully_managed_device_provisioning_permissions_header)
                    .setSubHeader(R.string.fully_managed_device_provisioning_permissions_subheader)
                    .setSubHeaderIcon(R.drawable.ic_history)
                    .setSecondarySubHeaderTitle(
                            R.string.fully_managed_device_provisioning_permissions_secondary_header)
                    .setSecondarySubHeader(R.string
                            .fully_managed_device_provisioning_permissions_secondary_subheader)
                    .setSecondarySubHeaderIcon(R.drawable.ic_perm_device_information)
                    .setShouldLoop(false);
            provisioningSummaryId =
                    R.string.fully_managed_device_with_permission_control_provisioning_summary;
        } else {
            provisioningSummaryId = R.string.fully_managed_device_provisioning_summary;
            secondScreenBuilder
                    .setDescription(R.string.fully_managed_device_provisioning_step_2_subheader)
                    .setAnimation(/* drawable= */ 0);
        }

        TransitionScreenWrapper firstScreen = new TransitionScreenWrapper(
                R.string.fully_managed_device_provisioning_step_1_header,
                R.string.fully_managed_device_provisioning_step_1_description,
                /* drawable= */ 0,
                /* shouldLoop= */ false);
        return new ProvisioningModeWrapper(
                ImmutableList.of(firstScreen, secondScreenBuilder.build()),
                provisioningSummaryId);
    }

    @Override
    public boolean shouldShowButtonsWhenPreProvisioningCompletes() {
        return mTransitionAnimationHelper.areAllTransitionsShown();
    }

    static Builder builder() {
        return new AutoValue_CarProvisioningActivityBridgeImpl.Builder();
    }

    private void setupEducationViews(
            boolean shouldSkipEducationScreens,
            @StringRes int progressLabelResId) {

        Slogf.d(TAG, "setupEducationViews(): shouldSkipEducationScreens: "
                + shouldSkipEducationScreens + " progressLabelResId: " + progressLabelResId);
    }

    private void setupButtons(CarSetupWizardCompatLayout layout, Activity activity) {
        layout.setSecondaryToolbarButtonEnabled(true);

        // Button will be bisible only when the provision finished
        layout.setPrimaryToolbarButtonVisible(false);
        layout.setSecondaryToolbarButtonVisible(false);

        layout.setPrimaryToolbarButtonText(activity.getString(R.string.next));
        layout.setPrimaryToolbarButtonListener(v -> getBridgeCallbacks().onNextButtonClicked());

        layout.setSecondaryToolbarButtonText(activity.getString(
                R.string.fully_managed_device_cancel_setup_button));
        layout.setSecondaryToolbarButtonListener(v -> getBridgeCallbacks().onAbortButtonClicked());

        //TODO(b/181323689): Add tests to ProvisioningActivityTest that the button is not
        // shown for non-DO provisioning flows.
        mPrimaryButtonView = (View) layout.getPrimaryToolbarButton();
        mSecondaryButtonView = (View) layout.getSecondaryToolbarButton();
        setOnButtonViewMeasured(mPrimaryButtonView);
        setOnButtonViewMeasured(mSecondaryButtonView);
    }

    private void setOnButtonViewMeasured(View view) {
        getUtils().onViewMeasured(view, v ->
                onButtonViewMeasured(v, getBridgeCallbacks()::isProvisioningFinalized));
    }

    /**
     * Calls {@code callback} when {@code view} has been measured.
     */
    private void onButtonViewMeasured(View view,
            Supplier<Boolean> checkIsProvisioningFinalised) {
        if (checkIsProvisioningFinalised.get()) {
            view.setVisibility(View.VISIBLE);
            return;
        }
        view.setVisibility(View.GONE);
    }

    private void setupProgressBar(CarSetupWizardCompatLayout layout) {
        mProgressBar = (View) layout.getProgressBar();
        getUtils().onViewMeasured(mProgressBar, v ->
                onProgressBarMeasured(v, getBridgeCallbacks()::isProvisioningFinalized));
    }

    /**
     * Calls {@code callback} when {@code ProgessBar} has been measured.
     */
    private void onProgressBarMeasured(View view,
            Supplier<Boolean> checkIsProvisioningFinalised) {
        if (checkIsProvisioningFinalised.get()) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setVisibility(View.VISIBLE);
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setUtils(Utils utils);
        abstract Builder setParams(ProvisioningParams params);
        abstract Builder setProvisioningMode(@ProvisioningMode int provisioningMode);
        abstract Builder setProvisioningManager(ProvisioningManager provisioningManager);
        abstract Builder setTransitionAnimationCallback(TransitionAnimationCallback callback);
        abstract Builder setInitializeLayoutParamsConsumer(
                InitializeLayoutConsumerHandler initializeLayoutParamsConsumer);
        abstract Builder setShouldSkipEducationScreens(boolean shouldSkipEducationScreens);
        abstract Builder setProgressLabelResId(@StringRes int progressLabelResId);
        abstract Builder setBridgeCallbacks(
                ProvisioningActivityBridgeCallbacks callbacks);
        abstract Builder setStateManager(TransitionAnimationStateManager stateManager);
        abstract CarProvisioningActivityBridgeImpl build();
    }
}
