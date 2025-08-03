/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.wear;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_ALWAYS_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_FOREGROUND_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.ALLOW_ONE_TIME_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DENY_AND_DONT_ASK_AGAIN_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DENY_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DIALOG_WITH_BOTH_LOCATIONS;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DIALOG_WITH_COARSE_LOCATION_ONLY;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.DIALOG_WITH_FINE_LOCATION_ONLY;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.FINE_RADIO_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NEXT_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NEXT_LOCATION_DIALOG;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.NO_UPGRADE_OT_BUTTON;

import android.app.Activity;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.View;
import android.view.WindowManager.LayoutParams;

import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler;
import com.android.permissioncontroller.permission.ui.wear.model.WearGrantPermissionsViewModel;
import com.android.permissioncontroller.permission.ui.wear.model.WearGrantPermissionsViewModelFactory;

import kotlin.Unit;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Wear-specific view handler for the grant permissions activity.
 */
public class GrantPermissionsWearViewHandler implements GrantPermissionsViewHandler {

    public static final String ARG_GROUP_NAME = "ARG_GROUP_NAME";
    public static final String ARG_GROUP_COUNT = "ARG_GROUP_COUNT";
    public static final String ARG_GROUP_INDEX = "ARG_GROUP_INDEX";
    public static final String ARG_GROUP_ICON = "ARG_GROUP_ICON";
    public static final String ARG_GROUP_MESSAGE = "ARG_GROUP_MESSAGE";
    private static final String ARG_GROUP_DETAIL_MESSAGE = "ARG_GROUP_DETAIL_MESSAGE";
    private static final String ARG_DIALOG_BUTTON_VISIBILITIES = "ARG_DIALOG_BUTTON_VISIBILITIES";
    private static final String ARG_DIALOG_LOCATION_VISIBILITIES =
            "ARG_DIALOG_LOCATION_VISIBILITIES";

    public static final SparseIntArray BUTTON_RES_ID_TO_NUM = new SparseIntArray();
    static {
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_allow_button, ALLOW_BUTTON);
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_allow_always_button,
                ALLOW_ALWAYS_BUTTON);
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_allow_foreground_only_button,
                ALLOW_FOREGROUND_BUTTON);
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_deny_button, DENY_BUTTON);
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_deny_and_dont_ask_again_button,
                DENY_AND_DONT_ASK_AGAIN_BUTTON);
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_allow_one_time_button, ALLOW_ONE_TIME_BUTTON);
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_no_upgrade_button, NO_UPGRADE_BUTTON);
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_no_upgrade_and_dont_ask_again_button,
                NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON);
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_no_upgrade_one_time_button, NO_UPGRADE_OT_BUTTON);
        BUTTON_RES_ID_TO_NUM.put(R.id.permission_no_upgrade_one_time_and_dont_ask_again_button,
                NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON);
    }

    private final Activity mActivity;

    private ResultListener mResultListener;

    // Configuration of the current dialog
    private String mGroupName;
    private int mGroupCount;
    private int mGroupIndex;
    private Icon mGroupIcon;
    private CharSequence mGroupMessage;
    private CharSequence mDetailMessage;
    private final boolean[] mButtonVisibilities = new boolean[NEXT_BUTTON];
    private final boolean[] mLocationVisibilities = new boolean[NEXT_LOCATION_DIALOG];
    // View model to update WearGrantPermissionsScreen elements
    private WearGrantPermissionsViewModel mViewModel;

    public GrantPermissionsWearViewHandler(Activity activity) {
        mActivity = activity;
    }

    @Override
    public GrantPermissionsWearViewHandler setResultListener(ResultListener listener) {
        mResultListener = listener;
        return this;
    }

    @Override
    public void saveInstanceState(Bundle arguments) {
        arguments.putString(ARG_GROUP_NAME, mGroupName);
        arguments.putInt(ARG_GROUP_COUNT, mGroupCount);
        arguments.putInt(ARG_GROUP_INDEX, mGroupIndex);
        arguments.putParcelable(ARG_GROUP_ICON, mGroupIcon);
        arguments.putCharSequence(ARG_GROUP_MESSAGE, mGroupMessage);
        arguments.putCharSequence(ARG_GROUP_DETAIL_MESSAGE, mDetailMessage);
        arguments.putBooleanArray(ARG_DIALOG_BUTTON_VISIBILITIES, mButtonVisibilities);
        arguments.putBooleanArray(ARG_DIALOG_LOCATION_VISIBILITIES, mLocationVisibilities);
    }

    @Override
    public void loadInstanceState(Bundle savedInstanceState) {
        mGroupName = savedInstanceState.getString(ARG_GROUP_NAME);
        mGroupMessage = savedInstanceState.getCharSequence(ARG_GROUP_MESSAGE);
        mGroupIcon = savedInstanceState.getParcelable(ARG_GROUP_ICON);
        mGroupCount = savedInstanceState.getInt(ARG_GROUP_COUNT);
        mGroupIndex = savedInstanceState.getInt(ARG_GROUP_INDEX);
        mDetailMessage = savedInstanceState.getCharSequence(ARG_GROUP_DETAIL_MESSAGE);
        setButtonVisibilities(savedInstanceState.getBooleanArray(ARG_DIALOG_BUTTON_VISIBILITIES));
        setLocationVisibilities(
                savedInstanceState.getBooleanArray(ARG_DIALOG_LOCATION_VISIBILITIES));

        updateScreen();
    }

    @Override
    public void updateUi(String groupName, int groupCount, int groupIndex, Icon icon,
            CharSequence message, CharSequence detailMessage,
            CharSequence permissionRationaleMessage, boolean[] buttonVisibilities,
            boolean[] locationVisibilities) {
        // permissionRationaleMessage ignored by wear
        if (mActivity.isFinishing()) {
            return;
        }
        mGroupName = groupName;
        mGroupCount = groupCount;
        mGroupIndex = groupIndex;
        mGroupIcon = icon;
        mGroupMessage = message;
        mDetailMessage = detailMessage;
        setButtonVisibilities(buttonVisibilities);
        setLocationVisibilities(locationVisibilities);

        updateScreen();
    }

    private void updateScreen() {
        mViewModel.getIconLiveData().setValue(
                mGroupIcon == null ? null : mGroupIcon.loadDrawable(mActivity));
        mViewModel.getGroupMessageLiveData().setValue(
                mGroupMessage == null ? "" : mGroupMessage.toString());
        mViewModel.getDetailMessageLiveData().setValue(mDetailMessage);
        int numButtons = BUTTON_RES_ID_TO_NUM.size();
        List<Boolean> buttonVisibilityList = Arrays.asList(new Boolean[NEXT_BUTTON]);
        for (int i = 0; i < numButtons; i++) {
            int pos = BUTTON_RES_ID_TO_NUM.valueAt(i);
            buttonVisibilityList.set(pos, mButtonVisibilities[pos] ? Boolean.TRUE : Boolean.FALSE);
        }
        mViewModel.getButtonVisibilitiesLiveData().setValue(buttonVisibilityList);
        List<Boolean> locationVisibilityList = Arrays.asList(new Boolean[NEXT_LOCATION_DIALOG]);
        for (int i = 0; i < locationVisibilityList.size(); i++) {
            locationVisibilityList.set(i, mLocationVisibilities[i] ? Boolean.TRUE : Boolean.FALSE);
        }
        mViewModel.getLocationVisibilitiesLiveData().setValue(locationVisibilityList);
        if (mLocationVisibilities[DIALOG_WITH_BOTH_LOCATIONS]) {
            mViewModel.getPreciseLocationCheckedLiveData()
                    .setValue(mLocationVisibilities[FINE_RADIO_BUTTON]);
        } else if (mLocationVisibilities[DIALOG_WITH_FINE_LOCATION_ONLY]
                || mLocationVisibilities[DIALOG_WITH_COARSE_LOCATION_ONLY]) {
            mViewModel.getPreciseLocationCheckedLiveData().setValue(false);
        }
    }

    private void onLocationSwitchChanged(boolean checked) {
        mViewModel.getPreciseLocationCheckedLiveData().setValue(checked);
    }

    private void onButtonClicked(int id) {
        List<String> affectedForegroundPermissions = null;
        if (mLocationVisibilities[DIALOG_WITH_BOTH_LOCATIONS]) {
            if (Boolean.TRUE.equals(mViewModel.getPreciseLocationCheckedLiveData().getValue())) {
                affectedForegroundPermissions = Arrays.asList(
                        ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION);
            } else {
                affectedForegroundPermissions = Collections.singletonList(ACCESS_COARSE_LOCATION);
            }
        } else if (mLocationVisibilities[DIALOG_WITH_FINE_LOCATION_ONLY]) {
            affectedForegroundPermissions = Collections.singletonList(ACCESS_FINE_LOCATION);
        } else if (mLocationVisibilities[DIALOG_WITH_COARSE_LOCATION_ONLY]) {
            affectedForegroundPermissions = Collections.singletonList(ACCESS_COARSE_LOCATION);
        }

        int button = -1;
        try {
            button = BUTTON_RES_ID_TO_NUM.get(id);
        } catch (NullPointerException e) {
            // Clicked a view which is not one of the defined buttons
            return;
        }
        // TODO (b/297534305): Research on performAccessibilityAction().
        switch (button) {
            case ALLOW_BUTTON:
                if (mResultListener != null) {
                    mResultListener.onPermissionGrantResult(mGroupName,
                            affectedForegroundPermissions, GRANTED_ALWAYS);
                }
                break;
            case ALLOW_FOREGROUND_BUTTON:
                if (mResultListener != null) {
                    mResultListener.onPermissionGrantResult(mGroupName,
                            affectedForegroundPermissions, GRANTED_FOREGROUND_ONLY);
                }
                break;
            case ALLOW_ALWAYS_BUTTON:
                if (mResultListener != null) {
                    mResultListener.onPermissionGrantResult(mGroupName,
                            affectedForegroundPermissions, GRANTED_ALWAYS);
                }
                break;
            case ALLOW_ONE_TIME_BUTTON:
                if (mResultListener != null) {
                    mResultListener.onPermissionGrantResult(mGroupName,
                            affectedForegroundPermissions, GRANTED_ONE_TIME);
                }
                break;
            case DENY_BUTTON:
            case NO_UPGRADE_BUTTON:
            case NO_UPGRADE_OT_BUTTON:
                if (mResultListener != null) {
                    mResultListener.onPermissionGrantResult(mGroupName,
                            affectedForegroundPermissions, DENIED);
                }
                break;
            case DENY_AND_DONT_ASK_AGAIN_BUTTON:
            case NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON:
            case NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON:
                if (mResultListener != null) {
                    mResultListener.onPermissionGrantResult(mGroupName,
                            affectedForegroundPermissions, DENIED_DO_NOT_ASK_AGAIN);
                }
                break;
        }
    }

    @Override
    public View createView() {
        WearGrantPermissionsViewModelFactory factory = new WearGrantPermissionsViewModelFactory();
        mViewModel = factory.create(WearGrantPermissionsViewModel.class);
        ComposeView root = new ComposeView(mActivity);

        WearGrantPermissionsScreenKt.setContent(root,
                mViewModel,
                id -> {
                    onButtonClicked(id);
                    return Unit.INSTANCE;
                },
                checked -> {
                    onLocationSwitchChanged(checked);
                    return Unit.INSTANCE;
                });
        if (mGroupName != null) {
            updateScreen();
        }

        return root;
    }

    @Override
    public void updateWindowAttributes(LayoutParams outLayoutParams) {
        // No-op
    }

    private void setButtonVisibilities(@Nullable boolean[] visibilities) {
        for (int i = 0; i < mButtonVisibilities.length; i++) {
            mButtonVisibilities[i] =
                    visibilities != null && i < visibilities.length && visibilities[i];
        }
    }

    private void setLocationVisibilities(@Nullable boolean[] visibilities) {
        for (int i = 0; i < mLocationVisibilities.length; i++) {
            mLocationVisibilities[i] =
                    visibilities != null && i < visibilities.length && visibilities[i];
        }
    }

    @Override
    public void onBackPressed() {
        if (mResultListener != null) {
            mResultListener.onPermissionGrantResult(mGroupName, CANCELED);
        } else {
            mActivity.finish();
        }
    }
}
