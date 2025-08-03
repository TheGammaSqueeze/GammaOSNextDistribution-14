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

package com.chassis.car.ui.plugin.preference;

import static com.android.car.ui.preference.CarUiPreferenceViewStub.CATEGORY;
import static com.android.car.ui.preference.CarUiPreferenceViewStub.DROPDOWN;
import static com.android.car.ui.preference.CarUiPreferenceViewStub.EDIT_TEXT;
import static com.android.car.ui.preference.CarUiPreferenceViewStub.FOOTER;
import static com.android.car.ui.preference.CarUiPreferenceViewStub.PREFERENCE;
import static com.android.car.ui.preference.CarUiPreferenceViewStub.SEEKBAR_DIALOG;
import static com.android.car.ui.preference.CarUiPreferenceViewStub.SWITCH;
import static com.android.car.ui.preference.CarUiPreferenceViewStub.TWO_ACTION;
import static com.android.car.ui.preference.CarUiPreferenceViewStub.TWO_ACTION_ICON;
import static com.android.car.ui.preference.CarUiPreferenceViewStub.TWO_ACTION_SWITCH;
import static com.android.car.ui.preference.CarUiPreferenceViewStub.TWO_ACTION_TEXT;
import static com.android.car.ui.preference.CarUiPreferenceViewStub.TWO_ACTION_TEXT_BORDERLESS;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.car.ui.plugin.oemapis.preference.PreferenceOEMV1;
import com.android.car.ui.plugin.oemapis.preference.PreferenceViewAttributesOEMV1;
import com.android.car.ui.preference.CarUiPreferenceViewStub.PreferenceType;

import com.chassis.car.ui.plugin.R;

/**
 * Adapter to load preference from plugin.
 */
public class PreferenceAdapterProxy implements PreferenceOEMV1 {

    private final Context mPluginContext;
    private final Context mSourceContext;

    public PreferenceAdapterProxy(Context pluginContext, Context sourceContext) {
        mPluginContext = pluginContext;
        mSourceContext = sourceContext;
    }

    @Override
    public View createCarUiPreferenceView(PreferenceViewAttributesOEMV1 attrs) {
        LayoutInflater inflater = LayoutInflater.from(mPluginContext);
        int preferenceType = attrs.getPreferenceType();
        View view = inflater.inflate(getPreferenceViewResLayoutId(preferenceType), null, false);

        // swap Ids
        updateViewIdToUseAppResId(preferenceType, view);

        return view;
    }

    private int getPreferenceViewResLayoutId(@PreferenceType int preferenceType) {
        switch (preferenceType) {
            case SWITCH:
                return R.layout.car_ui_preference_primary_switch;
            case EDIT_TEXT:
                return R.layout.car_ui_preference_dialog_edittext;
            case CATEGORY:
                return R.layout.car_ui_preference_category;
            case DROPDOWN:
                return R.layout.car_ui_preference_dropdown;
            case TWO_ACTION:
                return R.layout.car_ui_two_action_preference;
            case TWO_ACTION_TEXT:
                return R.layout.car_ui_preference_two_action_text;
            case TWO_ACTION_TEXT_BORDERLESS:
                return R.layout.car_ui_preference_two_action_text_borderless;
            case TWO_ACTION_ICON:
                return R.layout.car_ui_preference_two_action_icon;
            case TWO_ACTION_SWITCH:
                return R.layout.car_ui_preference_two_action_switch;
            case SEEKBAR_DIALOG:
                return R.layout.car_ui_seekbar_dialog;
            case FOOTER:
                return R.layout.car_ui_preference_footer;
            default:
                return R.layout.car_ui_preference;
        }
    }

    private void updateViewIdToUseAppResId(@PreferenceType int preferenceType, View view) {
        switch (preferenceType) {
            case PREFERENCE:
            case SWITCH:
            case EDIT_TEXT:
            case CATEGORY:
                // no swap needed
                break;
            case DROPDOWN:
                swapPluginViewIdWithAppsViewId("spinner", view);
                break;
            case TWO_ACTION:
                swapPluginViewIdWithAppsViewId("car_ui_preference_container_without_widget", view);
                swapPluginViewIdWithAppsViewId("action_widget_container", view);
                break;
            case TWO_ACTION_TEXT:
            case TWO_ACTION_TEXT_BORDERLESS:
                swapPluginViewIdWithAppsViewId("car_ui_first_action_container", view);
                swapPluginViewIdWithAppsViewId("car_ui_divider", view);
                swapPluginViewIdWithAppsViewId("car_ui_second_action_container", view);
                swapPluginViewIdWithAppsViewId("car_ui_secondary_action", view);
                break;
            case TWO_ACTION_ICON:
            case TWO_ACTION_SWITCH:
                swapPluginViewIdWithAppsViewId("car_ui_first_action_container", view);
                swapPluginViewIdWithAppsViewId("car_ui_divider", view);
                swapPluginViewIdWithAppsViewId("car_ui_second_action_container", view);
                swapPluginViewIdWithAppsViewId("car_ui_secondary_action", view);
                swapPluginViewIdWithAppsViewId("car_ui_secondary_action_concrete", view);
                break;
            case SEEKBAR_DIALOG:
                swapPluginViewIdWithAppsViewId("seek_bar_text_top", view);
                swapPluginViewIdWithAppsViewId("seek_bar_text_left", view);
                swapPluginViewIdWithAppsViewId("seek_bar", view);
                swapPluginViewIdWithAppsViewId("seek_bar_text_right", view);
                break;
            case FOOTER:
                swapPluginViewIdWithAppsViewId("car_ui_link", view);
                break;
            default:
                break;
        }
    }

    private void swapPluginViewIdWithAppsViewId(String name, View carUiPreferenceView) {
        int sharedLibId = getSharedLibViewId(name);
        int appViewId = getAppViewId(name);
        View view = carUiPreferenceView.findViewById(sharedLibId);
        ViewGroup.LayoutParams currentViewLayoutParam =
                (ViewGroup.LayoutParams) view.getLayoutParams();
        ViewGroup parent = (ViewGroup) view.getParent();
        int index = parent.indexOfChild(view);
        parent.removeView(view);
        FrameLayout wrapper = new FrameLayout(mPluginContext);
        ViewGroup.LayoutParams wrapperLayoutparams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        wrapper.addView(view, wrapperLayoutparams);
        parent.addView(wrapper, index, currentViewLayoutParam);
        view.setId(appViewId);
        wrapper.setId(sharedLibId);
    }

    private int getSharedLibViewId(String resName) {
        Resources res = mPluginContext.getResources();
        return res.getIdentifier(resName, "id",
                ((ContextWrapper) mPluginContext).getBaseContext().getPackageName());
    }

    private int getAppViewId(String resName) {
        Resources res = mSourceContext.getResources();
        return res.getIdentifier(resName, "id",
                mSourceContext.getPackageName());
    }
}
