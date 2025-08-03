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
package com.android.managedprovisioning.common;

import static java.util.Objects.requireNonNull;

import android.annotation.Nullable;
import android.annotation.StringRes;
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.car.setupwizardlib.CarSetupWizardCompatLayout;
import com.android.car.setupwizardlib.util.CarOrientationHelper;
import com.android.managedprovisioning.R;

/**
 * Sets the layout for screens that's integrated with CarSetupWizard layout. It will have a
 * hierarchical strucutre with {@code ViewStub}:
 *
 * <p> {@code MAIN_LAYOUT_RES_Id} will be the main layout for all provisioning screens. The main
 * view will have a {@code MAIN_VIEW_RES_ID} which contains a {@code ViewStub} and its resource id
 * is {@code MAIN_VIEW_STUB_RES_ID}.
 * <p> Either a double column or a single column layout will be passed into
 * {@code ViewStub}. This layout should have another {@code ViewStub} with resource id
 * {@code MAIN_VIEW_STUB_RES_ID}.
 * <p> Provisioning screen specific layout will be passed into the {@code ViewStub} from the
 * previous level.
 */
public final class CarSetupWizardLayoutHelper {
    private static final String TAG = CarSetupWizardLayoutHelper.class.getSimpleName();

    // Main layout for all provisioning screens.
    public static final int MAIN_LAYOUT_RES_ID = R.layout.car_layout;
    // Main layout's main view.
    private static final int MAIN_VIEW_RES_ID = R.id.car_layout;
    // Main layout's view stub that will take either single or double column layout.
    private static final int MAIN_VIEW_STUB_RES_ID = R.id.layout_content_stub;
    // Main layout's logo view.
    private static final int TITLE_LOGO_VIEW_RES_ID = R.id.title_logo;
    // Sub-screen's view stub that will take specific layout for different provisioning screens.
    private static final int SUB_VIEW_STUB_RES_ID = R.id.sub_content_stub;
    // Default icon for the provisioning screen header.
    private static final int ICON_RES_ID =
            com.android.internal.R.drawable.ic_corp_badge_case;

    private final Activity mActivity;
    private CarSetupWizardCompatLayout mLayout;

    public CarSetupWizardLayoutHelper(Activity activity) {
        mActivity = requireNonNull(activity);
    }

    public CarSetupWizardCompatLayout getBaseLayout() {
        return mLayout;
    }

    /**
     * Sets the layout for the current Activity with a default main layout.
     *
     * @param subLayoutId {@code LayoutRes} for the {@code ViewStub} on the right column of
     * double column layout or at the bottom of single column layout
     * @param isDoubleColumnAllowed whether to use double column layout for the sub layout
     */
    public CarSetupWizardCompatLayout setBaseLayout(int subLayoutId,
            boolean isDoubleColumnAllowed) {
        return setBaseLayout(MAIN_LAYOUT_RES_ID, subLayoutId, isDoubleColumnAllowed);
    }

    /**
     * Sets base layout for the current Activity.
     *
     * @param mainLayoutId main {@code LayoutRes} to be set for the current Activity
     * @param subLayoutId {@code LayoutRes} to be passed to {@code ViewStub} in main layout
     * @param isDoubleColumnAllowed whether there should be single or double column
     */
    public CarSetupWizardCompatLayout setBaseLayout(int mainLayoutId, int subLayoutId,
            boolean isDoubleColumnAllowed) {
        // TODO(b/203732347) enable split-nav support after split-nav cls are merged to sc-dev
        // boolean isSplitNavLayoutUsed = mLayout.isSplitNavLayoutUsed();
        return setBaseLayout(MAIN_LAYOUT_RES_ID,
                getColumnLayoutResourceId(/* isSplitNavLayoutUsed= */ false,
                        isDoubleColumnAllowed), subLayoutId);
    }

    /**
     * Sets base layout for the current Activity.
     *
     * @param mainLayoutId main {@code LayoutRes} to be set for the current Activity
     * @param columnLayoutId {@code LayoutRes} to be set withint the main layout
     * @param subLayoutId {@code LayoutRes} to be passed to {@code ViewStub} in main layout
     */
    public CarSetupWizardCompatLayout setBaseLayout(int mainLayoutId, int columnLayoutId,
            int subLayoutId) {
        mActivity.setContentView(mainLayoutId);

        mLayout = mActivity.findViewById(MAIN_VIEW_RES_ID);
        mLayout.setBackgroundColor(mActivity.getColor(R.color.background_grey));

        ViewStub contentLayoutRes = (ViewStub) mLayout.findViewById(MAIN_VIEW_STUB_RES_ID);

        requireNonNull(contentLayoutRes);

        contentLayoutRes.setLayoutResource(columnLayoutId);
        contentLayoutRes.inflate();

        ViewStub subContentStub = (ViewStub) mLayout.findViewById(SUB_VIEW_STUB_RES_ID);
        subContentStub.setLayoutResource(subLayoutId);
        subContentStub.inflate();

        mLayout.setBackButtonListener(v -> mActivity.onBackPressed());
        setupDefaultLogo();

        return mLayout;
    }

    /**
     * Gets the layout resource for the current Activity.
     *
     * @return either single column or double column resource id
     */
    public int getColumnLayoutResourceId(boolean isSplitNavLayoutUsed,
            boolean isDoubleColumnAllowed) {

        if (isDoubleColumnAllowed && !CarOrientationHelper.isNarrowOrientation(mActivity)) {
            return isSplitNavLayoutUsed ? R.layout.double_column_gmodel_layout
                    : R.layout.double_column_layout;
        }
        return isSplitNavLayoutUsed ? R.layout.single_column_gmodel_layout
                : R.layout.single_column_layout;
    }

    /**
     * Sets the header title and description in {@code CarSetupWizardCompatLayout} for current
     * activity.
     */
    public void setHeaderText(int headerResId, int descriptionResId) {
        setHeaderTitle(headerResId);

        TextView description = mActivity.findViewById(R.id.description);
        description.setText(descriptionResId);
    }

    /**
     * Sets the header title in {@code CarSetupWizardCompatLayout} for current
     * activity and removes empty description view.
     */
    public void setHeaderText(int headerResId) {
        setHeaderTitle(headerResId);

        TextView description = mActivity.findViewById(R.id.description);
        ViewGroup parent = (ViewGroup) description.getParent();
        parent.removeView(description);
    }

    private void setHeaderTitle(int headerResId) {
        TextView descriptionTitle = mActivity.findViewById(R.id.description_title);
        descriptionTitle.setText(headerResId);
    }

    /**
     * Sets up primary tool bar button.
     *
     * @param resId {@code StringRes} for the primary toolbar button's text
     * @param listener  {@code View.OnClickListener} for the primary toolbar button
     */
    public void setupPrimaryToolbarButton(@StringRes int resId,
            @Nullable View.OnClickListener listener) {
        mLayout.setPrimaryToolbarButtonText(mActivity.getString(resId));
        mLayout.setPrimaryToolbarButtonListener(listener);
    }

    /**
     * Sets the icon for the header.
     */
    public void setupLogo(int logoResId) {
        ImageView logo = mLayout.findViewById(TITLE_LOGO_VIEW_RES_ID);
        logo.setImageResource(logoResId);
    }

    /**
     * Sets the default icon for the header.
     */
    public void setupDefaultLogo() {
        setupLogo(ICON_RES_ID);
    }
}
