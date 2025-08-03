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

package com.android.permissioncontroller.safetycenter.ui;

import static android.Manifest.permission_group.CAMERA;
import static android.Manifest.permission_group.LOCATION;
import static android.Manifest.permission_group.MICROPHONE;
import static android.os.Build.VERSION_CODES.TIRAMISU;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.permission.PermissionGroupUsage;
import android.permission.PermissionManager;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.ArrayMap;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.permissioncontroller.safetycenter.ui.model.LiveSafetyCenterViewModelFactory;
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterQsViewModel;
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterQsViewModel.SensorState;
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterQsViewModelFactory;
import com.android.permissioncontroller.safetycenter.ui.model.SafetyCenterViewModel;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The Quick Settings fragment for the safety center. Displays information to the user about the
 * current safety and privacy status of their device, including showing mic/camera usage, and having
 * mic/camera/location toggles.
 */
@RequiresApi(TIRAMISU)
public class SafetyCenterQsFragment extends Fragment {
    private static final List<String> TOGGLE_BUTTONS = List.of(CAMERA, MICROPHONE, LOCATION);
    private static final String SETTINGS_TOGGLE_TAG = "settings_toggle";
    private static final int MAX_TOGGLES_PER_ROW = 2;

    private Context mContext;
    private long mSessionId;
    private List<PermissionGroupUsage> mPermGroupUsages;
    private SafetyCenterQsViewModel mViewModel;
    private boolean mIsPermissionUsageReady;
    private boolean mAreSensorTogglesReady;

    private SafetyCenterViewModel mSafetyCenterViewModel;

    /**
     * Create instance of SafetyCenterDashboardFragment with the arguments set
     *
     * @param sessionId The current session Id
     * @param usages ArrayList of PermissionGroupUsage
     * @return SafetyCenterQsFragment with the arguments set
     */
    public static SafetyCenterQsFragment newInstance(
            long sessionId, ArrayList<PermissionGroupUsage> usages) {
        Bundle args = new Bundle();
        args.putLong(EXTRA_SESSION_ID, sessionId);
        args.putParcelableArrayList(PermissionManager.EXTRA_PERMISSION_USAGES, usages);
        SafetyCenterQsFragment frag = new SafetyCenterQsFragment();
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSessionId = INVALID_SESSION_ID;
        if (getArguments() != null) {
            mSessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);
        }
        mContext = getContext();

        mPermGroupUsages =
                getArguments().getParcelableArrayList(PermissionManager.EXTRA_PERMISSION_USAGES);
        if (mPermGroupUsages == null) {
            mPermGroupUsages = new ArrayList<>();
        }

        getActivity().setTheme(R.style.Theme_SafetyCenterQs);

        SafetyCenterQsViewModelFactory factory =
                new SafetyCenterQsViewModelFactory(
                        getActivity().getApplication(), mSessionId, mPermGroupUsages);
        mViewModel =
                new ViewModelProvider(requireActivity(), factory)
                        .get(SafetyCenterQsViewModel.class);
        mViewModel.getSensorPrivacyLiveData().observe(this, this::setSensorToggleState);
        // LightAppPermGroupLiveDatas are kept track of in the view model,
        // we need to start observing them here
        if (!mPermGroupUsages.isEmpty()) {
            mViewModel.getPermDataLoadedLiveData().observe(this, this::onPermissionGroupsLoaded);
        } else {
            mIsPermissionUsageReady = true;
        }
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.safety_center_qs, container, false);
        root.setVisibility(View.GONE);
        root.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        View closeButton = root.findViewById(R.id.close_button);
        closeButton.setOnClickListener((v) -> requireActivity().finish());
        SafetyCenterTouchTarget.configureSize(
                closeButton, R.dimen.sc_icon_button_touch_target_size);

        mSafetyCenterViewModel =
                new ViewModelProvider(
                                requireActivity(),
                                new LiveSafetyCenterViewModelFactory(
                                        requireActivity().getApplication()))
                        .get(SafetyCenterViewModel.class);

        getChildFragmentManager()
                .beginTransaction()
                .add(
                        R.id.safety_center_prefs,
                        SafetyCenterDashboardFragment.newInstance(
                                mSessionId, /* isQuickSettingsFragment= */ true))
                .commitNow();
        return root;
    }

    private void maybeEnableView(@Nullable View rootView) {
        if (rootView == null) {
            return;
        }
        if (mIsPermissionUsageReady && mAreSensorTogglesReady) {
            rootView.setVisibility(View.VISIBLE);
        }
    }

    private void onPermissionGroupsLoaded(boolean initialized) {
        if (initialized) {
            if (!mIsPermissionUsageReady) {
                mIsPermissionUsageReady = true;
                maybeEnableView(getView());
            }
            addPermissionUsageInformation(getView());
        }
    }

    private void addPermissionUsageInformation(@Nullable View rootView) {
        if (rootView == null) {
            return;
        }
        View permissionSectionTitleView = rootView.findViewById(R.id.permission_section_title);
        View statusSectionTitleView = rootView.findViewById(R.id.status_section_title);
        if (mPermGroupUsages == null || mPermGroupUsages.isEmpty()) {
            permissionSectionTitleView.setVisibility(View.GONE);
            statusSectionTitleView.setVisibility(View.GONE);
            return;
        }
        permissionSectionTitleView.setVisibility(View.VISIBLE);
        statusSectionTitleView.setVisibility(View.VISIBLE);
        LinearLayout usageLayout = rootView.findViewById(R.id.permission_usage);
        Collections.sort(
                mPermGroupUsages,
                (pguA, pguB) ->
                        getAppLabel(pguA).toString().compareTo(getAppLabel(pguB).toString()));

        for (PermissionGroupUsage usage : mPermGroupUsages) {
            View cardView = View.inflate(mContext, R.layout.indicator_card, usageLayout);
            cardView.setId(View.generateViewId());
            ConstraintLayout parentIndicatorLayout = cardView.findViewById(R.id.indicator_layout);
            parentIndicatorLayout.setId(View.generateViewId());
            ImageView expandView = parentIndicatorLayout.findViewById(R.id.expand_view);

            // Update UI for the parent indicator card
            updateIndicatorParentUi(
                    parentIndicatorLayout,
                    usage.getPermissionGroupName(),
                    generateUsageLabel(usage),
                    usage.isActive());

            // If sensor usage is due to an active phone call, don't allow any actions
            if (usage.isPhoneCall()) {
                expandView.setVisibility(View.GONE);
                continue;
            }

            ConstraintLayout expandedLayout = cardView.findViewById(R.id.expanded_layout);
            expandedLayout.setId(View.generateViewId());

            // Handle redraw on orientation changes if permission has been revoked
            if (mViewModel.getRevokedUsages().contains(usage)) {
                disableIndicatorCardUi(parentIndicatorLayout, expandView);
                continue;
            }

            setIndicatorExpansionBehavior(parentIndicatorLayout, expandedLayout, expandView);
            ViewCompat.replaceAccessibilityAction(
                    parentIndicatorLayout,
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                    mContext.getString(R.string.safety_center_qs_expand_action),
                    null);

            // Configure the indicator action buttons
            configureIndicatorActionButtons(
                    usage, parentIndicatorLayout, expandedLayout, expandView);
        }
    }

    private void configureIndicatorActionButtons(
            PermissionGroupUsage usage,
            ConstraintLayout parentIndicatorLayout,
            ConstraintLayout expandedLayout,
            ImageView expandView) {
        configurePrimaryActionButton(usage, parentIndicatorLayout, expandedLayout, expandView);
        configureSeeUsageButton(usage, expandedLayout);
    }

    private void configurePrimaryActionButton(
            PermissionGroupUsage usage,
            ConstraintLayout parentIndicatorLayout,
            ConstraintLayout expandedLayout,
            ImageView expandView) {
        boolean shouldAllowRevoke = mViewModel.shouldAllowRevoke(usage);
        Intent manageServiceIntent = null;

        if (isSubAttributionUsage(usage.getAttributionLabel())) {
            manageServiceIntent = mViewModel.getStartViewPermissionUsageIntent(mContext, usage);
        }

        int primaryActionButtonLabel =
                getPrimaryActionButtonLabel(
                        manageServiceIntent != null,
                        shouldAllowRevoke,
                        usage.getPermissionGroupName());
        MaterialButton primaryActionButton = expandedLayout.findViewById(R.id.primary_button);
        primaryActionButton.setText(primaryActionButtonLabel);
        primaryActionButton.setStrokeColorResource(
                Utils.getColorResId(mContext, android.R.attr.colorAccent));

        if (shouldAllowRevoke && manageServiceIntent == null) {
            primaryActionButton.setOnClickListener(
                    l -> {
                        parentIndicatorLayout.callOnClick();
                        disableIndicatorCardUi(parentIndicatorLayout, expandView);
                        revokePermission(usage);
                        mSafetyCenterViewModel
                                .getInteractionLogger()
                                .recordForSensor(
                                        Action.SENSOR_PERMISSION_REVOKE_CLICKED,
                                        Sensor.fromPermissionGroupUsage(usage));
                    });
        } else {
            setPrimaryActionClickListener(primaryActionButton, usage, manageServiceIntent);
        }
    }

    private void configureSeeUsageButton(
            PermissionGroupUsage usage, ConstraintLayout expandedLayout) {
        MaterialButton seeUsageButton = expandedLayout.findViewById(R.id.secondary_button);
        seeUsageButton.setText(getSeeUsageText(usage.getPermissionGroupName()));

        seeUsageButton.setStrokeColorResource(
                Utils.getColorResId(mContext, android.R.attr.colorAccent));
        seeUsageButton.setOnClickListener(
                l -> {
                    mViewModel.navigateToSeeUsage(this, usage.getPermissionGroupName());
                    mSafetyCenterViewModel
                            .getInteractionLogger()
                            .recordForSensor(
                                    Action.SENSOR_PERMISSION_SEE_USAGES_CLICKED,
                                    Sensor.fromPermissionGroupUsage(usage));
                });
    }

    private void setPrimaryActionClickListener(
            Button primaryActionButton, PermissionGroupUsage usage, Intent manageServiceIntent) {
        if (manageServiceIntent != null) {
            primaryActionButton.setOnClickListener(
                    l -> {
                        mViewModel.navigateToManageService(this, manageServiceIntent);
                        mSafetyCenterViewModel
                                .getInteractionLogger()
                                .recordForSensor(
                                        // Unfortunate name, but this is used for all primary
                                        // CTAs on the permission usage cards.
                                        Action.SENSOR_PERMISSION_REVOKE_CLICKED,
                                        Sensor.fromPermissionGroupUsage(usage));
                    });
        } else {
            primaryActionButton.setOnClickListener(
                    l -> {
                        mViewModel.navigateToManageAppPermissions(this, usage);
                        mSafetyCenterViewModel
                                .getInteractionLogger()
                                .recordForSensor(
                                        Action.SENSOR_PERMISSION_REVOKE_CLICKED,
                                        Sensor.fromPermissionGroupUsage(usage));
                    });
        }
    }

    private int getPrimaryActionButtonLabel(
            boolean canHandleIntent, boolean shouldAllowRevoke, String permissionGroupName) {
        if (canHandleIntent) {
            return R.string.manage_service_qs;
        }
        if (!shouldAllowRevoke) {
            return R.string.manage_permissions_qs;
        }
        return getRemovePermissionText(permissionGroupName);
    }

    private boolean isSubAttributionUsage(@Nullable CharSequence attributionLabel) {
        if (attributionLabel == null || attributionLabel.length() == 0) {
            return false;
        }
        return true;
    }

    private void revokePermission(PermissionGroupUsage usage) {
        mViewModel.revokePermission(usage);
    }

    private void disableIndicatorCardUi(
            ConstraintLayout parentIndicatorLayout, ImageView expandView) {
        // Disable the parent indicator and the expand view
        parentIndicatorLayout.setEnabled(false);
        expandView.setEnabled(false);
        expandView.setVisibility(View.GONE);

        // Construct new icon for revoked permission
        ImageView iconView = parentIndicatorLayout.findViewById(R.id.indicator_icon);
        Drawable background = mContext.getDrawable(R.drawable.indicator_background_circle).mutate();
        background.setTint(mContext.getColor(R.color.sc_surface_variant_dark));
        Drawable icon = mContext.getDrawable(R.drawable.ic_check);
        Utils.applyTint(mContext, icon, android.R.attr.textColorPrimary);
        int bgSize = (int) getResources().getDimension(R.dimen.ongoing_appops_dialog_circle_size);
        int iconSize = (int) getResources().getDimension(R.dimen.ongoing_appops_dialog_icon_size);
        iconView.setImageDrawable(constructIcon(icon, background, bgSize, iconSize));

        // Set label to show on permission revoke
        TextView labelView = parentIndicatorLayout.findViewById(R.id.indicator_label);
        labelView.setText(R.string.permissions_removed_qs);
        labelView.setContentDescription(mContext.getString(R.string.permissions_removed_qs));
    }

    private void setIndicatorExpansionBehavior(
            ConstraintLayout parentIndicatorLayout,
            ConstraintLayout expandedLayout,
            ImageView expandView) {
        View rootView = getView();
        if (rootView == null) {
            return;
        }
        parentIndicatorLayout.setOnClickListener(
                createExpansionListener(expandedLayout, expandView, rootView));
    }

    private View.OnClickListener createExpansionListener(
            ConstraintLayout expandedLayout, ImageView expandView, View rootView) {
        AutoTransition transition = new AutoTransition();
        // Get the entire fragment as a viewgroup in order to animate it nicely in case of
        // expand/collapse
        ViewGroup indicatorCardViewGroup = (ViewGroup) rootView;
        return v -> {
            if (expandedLayout.getVisibility() == View.VISIBLE) {
                // Enable -> Press -> Hide the expanded card for a continuous ripple effect
                expandedLayout.setEnabled(true);
                pressButton(expandedLayout);
                expandedLayout.setVisibility(View.GONE);
                TransitionManager.beginDelayedTransition(indicatorCardViewGroup, transition);
                expandView.setImageDrawable(
                        mContext.getDrawable(R.drawable.ic_safety_group_expand));
                ViewCompat.replaceAccessibilityAction(
                        v,
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                        mContext.getString(R.string.safety_center_qs_expand_action),
                        null);
            } else {
                // Show -> Press -> Disable the expanded card for a continuous ripple effect
                expandedLayout.setVisibility(View.VISIBLE);
                pressButton(expandedLayout);
                expandedLayout.setEnabled(false);
                TransitionManager.beginDelayedTransition(indicatorCardViewGroup, transition);
                expandView.setImageDrawable(
                        mContext.getDrawable(R.drawable.ic_safety_group_collapse));
                ViewCompat.replaceAccessibilityAction(
                        v,
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                        mContext.getString(R.string.safety_center_qs_collapse_action),
                        null);
            }
        };
    }

    /**
     * To get the expanded card to ripple at the same time as the parent card we must simulate a
     * user press on the expanded card
     */
    private void pressButton(View buttonToBePressed) {
        buttonToBePressed.setPressed(true);
        buttonToBePressed.setPressed(false);
        buttonToBePressed.performClick();
    }

    private String generateUsageLabel(PermissionGroupUsage usage) {
        if (usage.isPhoneCall() && usage.isActive()) {
            return mContext.getString(R.string.active_call_usage_qs);
        } else if (usage.isPhoneCall()) {
            return mContext.getString(R.string.recent_call_usage_qs);
        }
        return generateAttributionUsageLabel(usage);
    }

    private String generateAttributionUsageLabel(PermissionGroupUsage usage) {
        CharSequence appLabel = getAppLabel(usage);

        final int usageResId =
                usage.isActive() ? R.string.active_app_usage_qs : R.string.recent_app_usage_qs;
        final int singleUsageResId =
                usage.isActive() ? R.string.active_app_usage_1_qs : R.string.recent_app_usage_1_qs;
        final int doubleUsageResId =
                usage.isActive() ? R.string.active_app_usage_2_qs : R.string.recent_app_usage_2_qs;

        CharSequence attributionLabel = usage.getAttributionLabel();
        CharSequence proxyLabel = usage.getProxyLabel();

        if (attributionLabel == null && proxyLabel == null) {
            return mContext.getString(usageResId, appLabel);
        } else if (attributionLabel != null && proxyLabel != null) {
            return mContext.getString(doubleUsageResId, appLabel, attributionLabel, proxyLabel);
        } else {
            return mContext.getString(
                    singleUsageResId,
                    appLabel,
                    attributionLabel == null ? proxyLabel : attributionLabel);
        }
    }

    private CharSequence getAppLabel(PermissionGroupUsage usage) {
        return KotlinUtils.INSTANCE.getPackageLabel(
                getActivity().getApplication(),
                usage.getPackageName(),
                UserHandle.getUserHandleForUid(usage.getUid()));
    }

    private void updateIndicatorParentUi(
            ConstraintLayout indicatorParentLayout,
            String permGroupName,
            String usageText,
            boolean isActiveUsage) {
        CharSequence permGroupLabel = getPermGroupLabel(permGroupName);
        ImageView iconView = indicatorParentLayout.findViewById(R.id.indicator_icon);

        Drawable background = mContext.getDrawable(R.drawable.indicator_background_circle);
        int indicatorColor =
                Utils.getColorResId(
                        mContext,
                        isActiveUsage
                                ? android.R.attr.textColorPrimaryInverse
                                : android.R.attr.textColorPrimary);
        Drawable indicatorIcon =
                KotlinUtils.INSTANCE.getPermGroupIcon(
                        mContext, permGroupName, mContext.getColor(indicatorColor));
        if (isActiveUsage) {
            Utils.applyTint(mContext, background, android.R.attr.colorAccent);
        } else {
            background.setTint(mContext.getColor(R.color.sc_surface_variant_dark));
        }
        int bgSize = (int) getResources().getDimension(R.dimen.ongoing_appops_dialog_circle_size);
        int iconSize = (int) getResources().getDimension(R.dimen.ongoing_appops_dialog_icon_size);
        iconView.setImageDrawable(constructIcon(indicatorIcon, background, bgSize, iconSize));
        iconView.setContentDescription(permGroupLabel);

        TextView titleText = indicatorParentLayout.findViewById(R.id.indicator_title);
        titleText.setText(permGroupLabel);
        titleText.setTextColor(
                mContext.getColor(Utils.getColorResId(mContext, android.R.attr.textColorPrimary)));
        titleText.setContentDescription(permGroupLabel);

        TextView labelText = indicatorParentLayout.findViewById(R.id.indicator_label);
        labelText.setText(usageText);
        labelText.setContentDescription(usageText);

        ImageView expandView = indicatorParentLayout.findViewById(R.id.expand_view);
        expandView.setImageDrawable(mContext.getDrawable(R.drawable.ic_safety_group_expand));
    }

    private Drawable constructIcon(Drawable icon, Drawable background, int bgSize, int iconSize) {
        LayerDrawable layered = new LayerDrawable(new Drawable[] {background, icon});
        final int bgLayerIndex = 0;
        final int iconLayerIndex = 1;
        layered.setLayerSize(bgLayerIndex, bgSize, bgSize);
        layered.setLayerSize(iconLayerIndex, iconSize, iconSize);
        layered.setLayerGravity(iconLayerIndex, Gravity.CENTER);
        return layered;
    }

    private void setSensorToggleState(@Nullable Map<String, SensorState> sensorStates) {
        if (!mAreSensorTogglesReady) {
            mAreSensorTogglesReady = true;
            maybeEnableView(getView());
            setupSensorToggles(sensorStates, getView());
        }
        updateSensorToggleState(sensorStates, getView());
    }

    private void setupSensorToggles(
            @Nullable Map<String, SensorState> sensorStates, @Nullable View rootView) {
        if (rootView == null) {
            return;
        }

        if (sensorStates == null) {
            sensorStates = new ArrayMap<>();
        }

        LinearLayout toggleContainer = rootView.findViewById(R.id.toggle_container);

        LinearLayout row = addRow(toggleContainer);

        for (String groupName : TOGGLE_BUTTONS) {
            boolean sensorVisible =
                    !sensorStates.containsKey(groupName)
                            || sensorStates.get(groupName).getVisible();
            if (!sensorVisible) {
                continue;
            }

            addToggle(groupName, row);

            if (row.getChildCount() >= MAX_TOGGLES_PER_ROW) {
                row = addRow(toggleContainer);
            }
        }
        addSettingsToggle(row);
    }

    private LinearLayout addRow(ViewGroup parent) {
        LinearLayout row =
                new LinearLayout(parent.getContext(), null, 0, R.style.SafetyCenterQsToggleRow);
        parent.addView(row);
        return row;
    }

    private View addToggle(String tag, ViewGroup parent) {
        View toggle =
                getLayoutInflater().inflate(R.layout.safety_center_toggle_button, parent, false);
        toggle.setTag(tag);
        parent.addView(toggle);
        return toggle;
    }

    private View addSettingsToggle(ViewGroup parent) {
        View securitySettings = addToggle(SETTINGS_TOGGLE_TAG, parent);
        securitySettings.setOnClickListener(
                (v) ->
                        mSafetyCenterViewModel.navigateToSafetyCenter(
                                mContext, NavigationSource.QUICK_SETTINGS_TILE));
        TextView securitySettingsText = securitySettings.findViewById(R.id.toggle_sensor_name);
        securitySettingsText.setText(R.string.settings);
        securitySettingsText.setSelected(true);
        securitySettings.findViewById(R.id.toggle_sensor_status).setVisibility(View.GONE);
        ImageView securitySettingsIcon = securitySettings.findViewById(R.id.toggle_sensor_icon);
        securitySettingsIcon.setImageDrawable(
                Utils.applyTint(
                        mContext,
                        mContext.getDrawable(R.drawable.ic_safety_center_shield),
                        android.R.attr.textColorPrimaryInverse));
        securitySettings.findViewById(R.id.arrow_icon).setVisibility(View.VISIBLE);
        ((ImageView) securitySettings.findViewById(R.id.arrow_icon))
                .setImageDrawable(
                        Utils.applyTint(
                                mContext,
                                mContext.getDrawable(R.drawable.ic_chevron_right),
                                android.R.attr.textColorSecondaryInverse));
        ViewCompat.replaceAccessibilityAction(
                securitySettings,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                mContext.getString(R.string.safety_center_qs_open_action),
                null);
        return securitySettings;
    }

    private void updateSensorToggleState(
            @Nullable Map<String, SensorState> sensorStates, @Nullable View rootView) {
        if (rootView == null) {
            return;
        }

        if (sensorStates == null) {
            sensorStates = new ArrayMap<>();
        }

        for (String groupName : TOGGLE_BUTTONS) {
            View toggle = rootView.findViewWithTag(groupName);
            if (toggle == null) {
                continue;
            }
            EnforcedAdmin admin =
                    sensorStates.containsKey(groupName)
                            ? sensorStates.get(groupName).getAdmin()
                            : null;
            boolean sensorBlockedByAdmin = admin != null;

            if (sensorBlockedByAdmin) {
                toggle.setOnClickListener(
                        (v) ->
                                startActivity(
                                        RestrictedLockUtils.getShowAdminSupportDetailsIntent(
                                                mContext, admin)));
            } else {
                toggle.setOnClickListener(
                        (v) -> {
                            mViewModel.toggleSensor(groupName);
                            mSafetyCenterViewModel
                                    .getInteractionLogger()
                                    .recordForSensor(
                                            Action.PRIVACY_CONTROL_TOGGLE_CLICKED,
                                            Sensor.fromPermissionGroupName(groupName));
                        });
            }

            TextView groupLabel = toggle.findViewById(R.id.toggle_sensor_name);
            groupLabel.setText(getPermGroupLabel(groupName));
            // Set the text as selected to get marquee to work
            groupLabel.setSelected(true);
            TextView blockedStatus = toggle.findViewById(R.id.toggle_sensor_status);
            // Set the text as selected to get marquee to work
            blockedStatus.setSelected(true);
            ImageView iconView = toggle.findViewById(R.id.toggle_sensor_icon);
            boolean sensorEnabled =
                    !sensorStates.containsKey(groupName)
                            || sensorStates.get(groupName).getEnabled();

            Drawable icon;
            boolean useEnabledBackground = sensorEnabled && !sensorBlockedByAdmin;
            int colorPrimary = getTextColor(true, useEnabledBackground, sensorBlockedByAdmin);
            int colorSecondary = getTextColor(false, useEnabledBackground, sensorBlockedByAdmin);
            if (useEnabledBackground) {
                toggle.setBackgroundResource(R.drawable.safety_center_sensor_toggle_enabled);
            } else {
                toggle.setBackgroundResource(R.drawable.safety_center_sensor_toggle_disabled);
            }
            if (sensorEnabled) {
                icon = KotlinUtils.INSTANCE.getPermGroupIcon(mContext, groupName, colorPrimary);
            } else {
                icon = mContext.getDrawable(getBlockedIconResId(groupName));
                icon.setTint(colorPrimary);
            }
            blockedStatus.setText(getSensorStatusTextResId(groupName, sensorEnabled));
            blockedStatus.setTextColor(colorSecondary);
            groupLabel.setTextColor(colorPrimary);
            iconView.setImageDrawable(icon);

            int contentDescriptionResId = R.string.safety_center_qs_privacy_control;
            toggle.setContentDescription(
                    mContext.getString(
                            contentDescriptionResId,
                            groupLabel.getText(),
                            blockedStatus.getText()));
            ViewCompat.replaceAccessibilityAction(
                    toggle,
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                    mContext.getString(R.string.safety_center_qs_toggle_action),
                    null);
        }
    }

    @ColorInt
    private int getTextColor(boolean primary, boolean inverse, boolean useLowerOpacity) {
        int primaryAttribute =
                inverse ? android.R.attr.textColorPrimaryInverse : android.R.attr.textColorPrimary;
        int secondaryAttribute =
                inverse
                        ? android.R.attr.textColorSecondaryInverse
                        : android.R.attr.textColorSecondary;
        int attribute = primary ? primaryAttribute : secondaryAttribute;
        TypedValue value = new TypedValue();
        mContext.getTheme().resolveAttribute(attribute, value, true);
        int colorRes = value.resourceId != 0 ? value.resourceId : value.data;
        int color = mContext.getColor(colorRes);
        if (useLowerOpacity) {
            color = colorWithAdjustedAlpha(color, 0.5f);
        }
        return color;
    }

    @ColorInt
    private int colorWithAdjustedAlpha(@ColorInt int color, float factor) {
        return Color.argb(
                Math.round(Color.alpha(color) * factor),
                Color.red(color),
                Color.green(color),
                Color.blue(color));
    }

    private CharSequence getPermGroupLabel(String permissionGroup) {
        switch (permissionGroup) {
            case MICROPHONE:
                return mContext.getString(R.string.microphone_toggle_label_qs);
            case CAMERA:
                return mContext.getString(R.string.camera_toggle_label_qs);
        }
        return KotlinUtils.INSTANCE.getPermGroupLabel(mContext, permissionGroup);
    }

    private static int getRemovePermissionText(String permissionGroup) {
        return CAMERA.equals(permissionGroup)
                ? R.string.remove_camera_qs
                : R.string.remove_microphone_qs;
    }

    private static int getSeeUsageText(String permissionGroup) {
        return CAMERA.equals(permissionGroup)
                ? R.string.camera_usage_qs
                : R.string.microphone_usage_qs;
    }

    private static int getBlockedIconResId(String permissionGroup) {
        switch (permissionGroup) {
            case MICROPHONE:
                return R.drawable.ic_mic_blocked;
            case CAMERA:
                return R.drawable.ic_camera_blocked;
            case LOCATION:
                return R.drawable.ic_location_blocked;
        }
        return -1;
    }

    private static int getSensorStatusTextResId(String permissionGroup, boolean enabled) {
        switch (permissionGroup) {
            case LOCATION:
                return enabled ? R.string.on : R.string.off;
        }
        return enabled ? R.string.available : R.string.blocked;
    }
}
