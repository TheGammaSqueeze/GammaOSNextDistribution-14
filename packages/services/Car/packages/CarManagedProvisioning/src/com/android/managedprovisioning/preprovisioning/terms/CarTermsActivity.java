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

package com.android.managedprovisioning.preprovisioning.terms;

import static com.android.internal.util.Preconditions.checkNotNull;

import static java.util.Objects.requireNonNull;

import android.annotation.Nullable;
import android.os.Bundle;

import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.setupwizardlib.CarSetupWizardCompatLayout;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.CarSetupWizardLayoutHelper;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.StylerHelper;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.preprovisioning.terms.TermsViewModel.TermsViewModelFactory;
import com.android.managedprovisioning.preprovisioning.terms.adapters.TermsListAdapter;

import java.util.List;
import java.util.function.BiFunction;

/**
 * TODO(b/183510678) A temporary implementation for AAOS to override
 * {@link TermsActivity} in ManagedProvisioning using inheritance. The long term
 * solution will be to use composition.
 */
public final class CarTermsActivity extends TermsActivity implements
        TermsListAdapter.TermsBridge {

    private static final String TAG = CarTermsActivity.class.getSimpleName();

    private final BiFunction<AppCompatActivity, ProvisioningParams, TermsViewModel>
            mViewModelFetcher;
    private final StylerHelper mStylerHelper;
    private TermsViewModel mViewModel;

    public CarTermsActivity() {
        this(new SettingsFacade(), new StylerHelper(), (activity, params) -> {
            TermsViewModelFactory factory =
                     new TermsViewModelFactory(activity.getApplication(), params);
            return new ViewModelProvider(activity, factory).get(TermsViewModel.class);
        });
    }

    @VisibleForTesting
    CarTermsActivity(SettingsFacade settingsFacade, StylerHelper stylerHelper,
            BiFunction<AppCompatActivity, ProvisioningParams, TermsViewModel> viewModelFetcher) {
        super(/* contextMenuMaker= */ null, settingsFacade, stylerHelper, viewModelFetcher);

        mStylerHelper = requireNonNull(stylerHelper);
        mViewModelFetcher = requireNonNull(viewModelFetcher);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CarSetupWizardLayoutHelper layoutHelper = new CarSetupWizardLayoutHelper(this);
        CarSetupWizardCompatLayout layout =
                layoutHelper.setBaseLayout(
                        /* mainLayoutResId= */ CarSetupWizardLayoutHelper.MAIN_LAYOUT_RES_ID,
                        /* columnLayoutId= */ R.layout.single_column_left_layout,
                        /* subLayoutId= */ R.layout.terms_layout);
        layoutHelper.setHeaderText(R.string.terms);
        // TextView titleView = layout.findViewById(R.id.description_title);
        // titleView.setText(R.string.terms);
        layout.setPrimaryToolbarButtonVisible(false);

        ProvisioningParams params = checkNotNull(
                getIntent().getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS));
        mViewModel = mViewModelFetcher.apply(this, params);
        List<TermsDocument> terms = mViewModel.getTerms();

        RecyclerView recyclerView = layout.findViewById(R.id.terms_container);
        recyclerView.setLayoutManager(new LinearLayoutManager(/* context= */ this));
        recyclerView.setAdapter(new TermsListAdapter(
                this,
                mViewModel.getGeneralDisclaimer(),
                terms,
                getLayoutInflater(),
                new AccessibilityContextMenuMaker(this),
                this,
                mUtils,
                mStylerHelper));
    }

    /**
     * Overrides {@code initializeLayoutParams} in SetupGlifLayoutActivity to set layout as
     * {@code CarSetupWizardLayout}
     */
    @Override
    protected void initializeLayoutParams(
            int layoutResourceId, @Nullable Integer headerResourceId) {
        CarSetupWizardLayoutHelper layoutHelper = new CarSetupWizardLayoutHelper(this);
        layoutHelper.setBaseLayout(
                /* mainLayoutResId= */ CarSetupWizardLayoutHelper.MAIN_LAYOUT_RES_ID,
                /* columnLayoutId= */ R.layout.single_column_left_layout,
                /* subLayoutId= */ R.layout.terms_layout);
    }
}
