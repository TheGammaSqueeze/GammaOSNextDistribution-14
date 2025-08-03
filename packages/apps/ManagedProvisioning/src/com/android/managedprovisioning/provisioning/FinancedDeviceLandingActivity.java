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
 * limitations under the License
 */

package com.android.managedprovisioning.provisioning;

import static java.util.Objects.requireNonNull;

import android.annotation.DrawableRes;
import android.annotation.StringRes;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.SetupGlifLayoutActivity;
import com.android.managedprovisioning.common.StylerHelper;
import com.android.managedprovisioning.common.ThemeHelper;
import com.android.managedprovisioning.common.ThemeHelper.DefaultNightModeChecker;
import com.android.managedprovisioning.common.ThemeHelper.DefaultSetupWizardBridge;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import com.google.android.setupcompat.template.FooterBarMixin;
import com.google.android.setupcompat.template.FooterButton;
import com.google.android.setupdesign.GlifLayout;
import com.google.android.setupdesign.util.DeviceHelper;

import java.util.Objects;

/**
 * This is the first activity the user will see during financed device provisioning.
 */
public final class FinancedDeviceLandingActivity extends SetupGlifLayoutActivity {

    private final AccessibilityContextMenuMaker mContextMenuMaker;
    private final StylerHelper mStylerHelper;

    public FinancedDeviceLandingActivity() {
        this(new Utils(), /* contextMenuMaker= */null, new SettingsFacade(),
                new ThemeHelper(new DefaultNightModeChecker(), new DefaultSetupWizardBridge()),
                new StylerHelper());
    }

    @VisibleForTesting
    FinancedDeviceLandingActivity(Utils utils, AccessibilityContextMenuMaker contextMenuMaker,
            SettingsFacade settingsFacade, ThemeHelper themeHelper, StylerHelper stylerHelper) {
        super(utils, settingsFacade, themeHelper);
        mContextMenuMaker = contextMenuMaker != null
                ? contextMenuMaker
                : new AccessibilityContextMenuMaker(this);
        mStylerHelper = requireNonNull(stylerHelper);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ProvisioningParams params = getIntent()
                .getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS);
        initializeUi(params);
    }

    private void initializeUi(ProvisioningParams params) {
        setContentView(R.layout.financed_device_landing_screen);

        GlifLayout layout = findViewById(R.id.setup_wizard_layout);
        CharSequence deviceName = DeviceHelper.getDeviceName(this);
        final String headerString = getString(R.string.financed_device_screen_header, deviceName,
                params.organizationName);
        layout.setHeaderText(headerString);
        layout.setIcon(getDrawable(R.drawable.ic_info_outline_24px));
        setupFooterBar(layout);

        final ViewGroup item1 = layout.findViewById(R.id.item1);
        final String makePaymentsSubheaderSummary = getString(
                R.string.financed_make_payments_subheader_description, params.organizationName,
                params.organizationName, deviceName);
        setupItem(item1, R.string.financed_make_payments_subheader_title,
                makePaymentsSubheaderSummary, R.drawable.ic_file_download_24px, deviceName);

        final ViewGroup item2 = layout.findViewById(R.id.item2);

        final String restrictDeviceSubheaderSummary = getString(
                R.string.financed_restrict_device_subheader_description, params.organizationName,
                deviceName);
        setupItem(item2, R.string.financed_restrict_device_subheader_title,
                restrictDeviceSubheaderSummary, R.drawable.ic_lock,
                deviceName);
    }

    private void setupFooterBar(GlifLayout layout) {
        final FooterBarMixin footerBarMixin = layout.getMixin(FooterBarMixin.class);
        footerBarMixin.setPrimaryButton(
                new FooterButton.Builder(this)
                        .setText(R.string.next)
                        .setListener(view -> onNextButtonClicked())
                        .setButtonType(FooterButton.ButtonType.NEXT)
                        .setTheme(com.google.android.setupdesign.R.style.SudGlifButton_Primary)
                        .build());
    }

    private void setupItem(ViewGroup item, @StringRes int title, String summary,
            @DrawableRes int icon, CharSequence deviceName) {
        mStylerHelper.applyListItemStyling(
                item, new LinearLayout.LayoutParams(item.getLayoutParams()));

        final ImageView makePaymentsSubHeaderImage = item.findViewById(R.id.sud_items_icon);
        makePaymentsSubHeaderImage.setImageDrawable(getDrawable(icon));

        final TextView makePaymentsSubHeaderTitle = item.findViewById(R.id.sud_items_title);
        makePaymentsSubHeaderTitle.setText(getString(title, deviceName));

        final TextView makePaymentsSubHeaderDescription = item.findViewById(R.id.sud_items_summary);
        makePaymentsSubHeaderDescription.setText(summary);
    }

    private void onNextButtonClicked() {
        setResult(RESULT_OK);
        getTransitionHelper().finishActivity(this);
    }
}
