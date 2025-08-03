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

import static android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE;

import static com.google.android.setupdesign.util.ThemeHelper.shouldApplyMaterialYouStyle;

import android.app.Activity;
import android.content.Context;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.StringRes;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.InitializeLayoutConsumerHandler;
import com.android.managedprovisioning.common.StylerHelper;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.provisioning.ProvisioningActivity.ProvisioningMode;
import com.android.managedprovisioning.provisioning.ProvisioningModeWrapperProvider.ProvisioningModeWrapper;
import com.android.managedprovisioning.provisioning.TransitionAnimationHelper.AnimationComponents;
import com.android.managedprovisioning.provisioning.TransitionAnimationHelper.TransitionAnimationCallback;
import com.android.managedprovisioning.provisioning.TransitionAnimationHelper.TransitionAnimationStateManager;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.setupdesign.GlifLayout;
import com.google.android.setupdesign.util.ContentStyler;
import com.google.android.setupdesign.util.DescriptionStyler;
import com.google.android.setupdesign.util.DeviceHelper;
import com.google.auto.value.AutoValue;

import java.util.function.Supplier;

@AutoValue
abstract class ProvisioningActivityBridgeImpl implements ProvisioningActivityBridge {

    private TransitionAnimationHelper mTransitionAnimationHelper;
    private ViewGroup mButtonFooterContainer;
    private Context mContext;

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
        Context context = activity.getApplicationContext();
        CharSequence deviceName = DeviceHelper.getDeviceName(context);

        boolean isPoProvisioning = getUtils().isProfileOwnerAction(getParams().provisioningAction);
        String title =
                isPoProvisioning
                        ? activity.getString(R.string.setup_profile_progress)
                        : activity.getString(R.string.setup_device_progress, deviceName);
        int layoutResId =
                getShouldSkipEducationScreens()
                        ? R.layout.empty_loading_layout
                        : R.layout.provisioning_progress;

        getInitializeLayoutParamsConsumer()
                .initializeLayoutParams(layoutResId, /* headerResId */ null);
        activity.setTitle(title);

        GlifLayout layout = activity.findViewById(R.id.setup_wizard_layout);
        setupEducationViews(layout, activity, getShouldSkipEducationScreens(),
                getProgressLabelResId());
        if (getUtils().isFinancedDeviceAction(getParams().provisioningAction)) {
            // make the icon invisible
            layout.findViewById(com.google.android.setupdesign.R.id.sud_layout_icon)
                    .setVisibility(View.INVISIBLE);
        }

        Utils.addNextButton(layout, v -> getBridgeCallbacks().onNextButtonClicked());
        // TODO(b/181323689): Add tests to ProvisioningActivityTest that the button is not
        // shown for non-DO provisioning flows.
        if (getUtils().isDeviceOwnerAction(getParams().provisioningAction)) {
            Utils.addAbortAndResetButton(layout, v -> getBridgeCallbacks().onAbortButtonClicked());
        }
        ViewGroup root = activity.findViewById(
                com.google.android.setupdesign.R.id.sud_layout_template_content);
        mButtonFooterContainer = getButtonFooterContainer(root);
        mButtonFooterContainer.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);

        getUtils()
                .onViewMeasured(
                        mButtonFooterContainer,
                        view ->
                                onContainerMeasured(view, activity,
                                        getBridgeCallbacks()::isProvisioningFinalized));
    }

    private void onContainerMeasured(
            View view,
            Activity activity,
            Supplier<Boolean> checkIsProvisioningFinalised) {
        if (checkIsProvisioningFinalised.get()) {
            view.setVisibility(View.VISIBLE);
            return;
        }
        getProvisioningProgressLabelContainer(activity).setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        view.getHeight()));
        view.setVisibility(View.GONE);
    }

    @Override
    public void onStart(Activity activity) {
        if (!getShouldSkipEducationScreens()) {
            startTransitionAnimation(activity, getTransitionAnimationCallback());
        }
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
            getProvisioningProgressLabelContainer(activity).setVisibility(View.GONE);
            mButtonFooterContainer.setVisibility(View.VISIBLE);
        }

        if (getShouldSkipEducationScreens()) {
            getBridgeCallbacks().onNextButtonClicked();
        }
    }

    @Override
    public boolean shouldShowButtonsWhenPreProvisioningCompletes() {
        return mTransitionAnimationHelper.areAllTransitionsShown();
    }

    private void startTransitionAnimation(
            Activity activity,
            TransitionAnimationCallback callback) {
        final GlifLayout layout = activity.findViewById(R.id.setup_wizard_layout);
        setupTransitionAnimationHelper(layout, callback);
        mTransitionAnimationHelper.start();
    }

    private void endTransitionAnimation() {
        mTransitionAnimationHelper.stop();
        mTransitionAnimationHelper = null;
    }

    private void setupTransitionAnimationHelper(
            GlifLayout layout, TransitionAnimationCallback callback) {
        TextView header = layout.findViewById(com.google.android.setupdesign.R.id.suc_layout_title);
        TextView description =
                layout.findViewById(com.google.android.setupdesign.R.id.sud_layout_subtitle);
        // No hyphenation split word in header
        header.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
        ViewGroup item1 = layout.findViewById(R.id.item1);
        ViewGroup item2 = layout.findViewById(R.id.item2);
        LottieAnimationView drawable = layout.findViewById(R.id.animation);
        ViewGroup drawableContainer = layout.findViewById(R.id.animation_container);
        Space space1 = layout.findViewById(R.id.space1);
        Space space2 = layout.findViewById(R.id.space2);
        AnimationComponents animationComponents =
                new AnimationComponents(
                        header, description, item1, item2, drawable, drawableContainer, space1,
                        space2);

        ProvisioningModeWrapperProvider provider =
                new ProvisioningModeWrapperProvider(getParams());
        ProvisioningModeWrapper provisioningModeWrapper =
                provider.getProvisioningModeWrapper(getProvisioningMode(),
                        DeviceHelper.getDeviceName(layout.getContext()));
        mTransitionAnimationHelper =
                new TransitionAnimationHelper(
                        animationComponents,
                        callback,
                        getStateManager(),
                        new StylerHelper(),
                        provisioningModeWrapper);
    }

    private void setupEducationViews(
            GlifLayout layout,
            Activity activity,
            boolean shouldSkipEducationScreens,
            @StringRes int progressLabelResId) {
        addProvisioningProgressLabel(activity);
        if (shouldSkipEducationScreens) {
            TextView header =
                    layout.findViewById(com.google.android.setupdesign.R.id.suc_layout_title);
            header.setText(progressLabelResId);
            getProvisioningProgressLabelContainer(activity).setVisibility(View.GONE);
        } else {
            setupProgressLabel(progressLabelResId, activity);
        }
    }

    private void addProvisioningProgressLabel(Activity activity) {
        ViewGroup parent = activity.findViewById(
                com.google.android.setupdesign.R.id.sud_layout_template_content);
        activity.getLayoutInflater().inflate(R.layout.label_provisioning_progress, parent, true);
    }

    private ViewGroup getProvisioningProgressLabelContainer(Activity activity) {
        ViewGroup parent = activity.findViewById(
                com.google.android.setupdesign.R.id.sud_layout_template_content);
        return parent.findViewById(R.id.provisioning_progress_container);
    }

    /**
     * Returns the relevant progress label and takes care of visibilities to show the correct one.
     */
    private void setupProgressLabel(@StringRes int progressLabelResId, Activity activity) {
        TextView progressLabel = getRelevantProgressLabel(activity);
        DescriptionStyler.applyPartnerCustomizationHeavyStyle(progressLabel);
        progressLabel.setTextColor(
                shouldApplyMaterialYouStyle(activity)
                        ? getUtils().getTextSecondaryColor(activity)
                        : getUtils().getAccentColor(activity));
        progressLabel.setText(progressLabelResId);
        int sidePadding = (int) ContentStyler.getPartnerContentMarginStart(activity);
        progressLabel.setPadding(sidePadding, /* top= */ 0, sidePadding, /* bottom= */ 0);
        getProvisioningProgressLabelContainer(activity).setVisibility(View.VISIBLE);
    }

    private TextView getRelevantProgressLabel(Activity activity) {
        ViewGroup parent = (ViewGroup) activity.findViewById(
                com.google.android.setupdesign.R.id.suc_layout_footer).getParent();
        TextView provisioningProgressLabel = parent.findViewById(R.id.provisioning_progress);
        if (provisioningProgressLabel != null) {
            return provisioningProgressLabel;
        }
        TextView leftProgress = parent.findViewById(R.id.provisioning_progress_left);
        TextView rightProgress = parent.findViewById(R.id.provisioning_progress_right);
        if (activity.getResources().getBoolean(R.bool.show_progress_label_on_left_side)) {
            leftProgress.setVisibility(View.VISIBLE);
            rightProgress.setVisibility(View.INVISIBLE);
            return leftProgress;
        }
        leftProgress.setVisibility(View.INVISIBLE);
        rightProgress.setVisibility(View.VISIBLE);
        return rightProgress;
    }

    private ViewGroup getButtonFooterContainer(ViewGroup root) {
        return (ViewGroup) root.getChildAt(root.getChildCount() - 2);
    }

    static Builder builder() {
        return new AutoValue_ProvisioningActivityBridgeImpl.Builder();
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
        abstract ProvisioningActivityBridgeImpl build();
    }
}
