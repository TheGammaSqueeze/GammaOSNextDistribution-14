/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.preprovisioning.terms;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_TERMS_ACTIVITY_TIME_MS;
import static com.android.internal.util.Preconditions.checkNotNull;

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.android.managedprovisioning.analytics.MetricsWriterFactory;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.SetupGlifLayoutActivity;
import com.android.managedprovisioning.common.StylerHelper;
import com.android.managedprovisioning.common.ThemeHelper;
import com.android.managedprovisioning.common.ThemeHelper.DefaultNightModeChecker;
import com.android.managedprovisioning.common.ThemeHelper.DefaultSetupWizardBridge;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.preprovisioning.terms.TermsViewModel.TermsViewModelFactory;
import com.android.managedprovisioning.preprovisioning.terms.adapters.TermsListAdapter;

import java.util.function.BiFunction;

/**
 * Activity responsible for displaying the Terms screen
 */
public class TermsActivity extends SetupGlifLayoutActivity implements
        TermsListAdapter.TermsBridge {
    private final AccessibilityContextMenuMaker mContextMenuMaker;
    private final SettingsFacade mSettingsFacade;
    private ProvisioningAnalyticsTracker mProvisioningAnalyticsTracker;
    private final BiFunction<AppCompatActivity, ProvisioningParams, TermsViewModel>
            mViewModelFetcher;
    private TermsViewModel mViewModel;
    private final StylerHelper mStylerHelper;

    private TermsActivityBridge mBridge;

    @SuppressWarnings("unused")
    public TermsActivity() {
        this(
                /* contextMenuMaker= */ null,
                new SettingsFacade(),
                new StylerHelper(),
                (activity, params) -> {
                    final TermsViewModelFactory factory =
                            new TermsViewModelFactory(activity.getApplication(), params);
                    return new ViewModelProvider(activity, factory).get(TermsViewModel.class);
                });
    }

    @VisibleForTesting
    TermsActivity(AccessibilityContextMenuMaker contextMenuMaker, SettingsFacade settingsFacade,
            StylerHelper stylerHelper,
            BiFunction<AppCompatActivity, ProvisioningParams, TermsViewModel> viewModelFetcher) {
        super(new Utils(), settingsFacade,
                new ThemeHelper(new DefaultNightModeChecker(), new DefaultSetupWizardBridge()));

        mContextMenuMaker =
                contextMenuMaker != null ? contextMenuMaker : new AccessibilityContextMenuMaker(
                        this);
        mSettingsFacade = requireNonNull(settingsFacade);
        mViewModelFetcher = requireNonNull(viewModelFetcher);
        mStylerHelper = requireNonNull(stylerHelper);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ProvisioningParams params = checkNotNull(
                getIntent().getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS));
        mViewModel = mViewModelFetcher.apply(this, params);

        mBridge = createBridge();
        mBridge.initiateUi(this, mViewModel.getTerms(), mViewModel.getGeneralDisclaimer());

        initAnalyticsTracker();
    }

    private void initAnalyticsTracker() {
        mProvisioningAnalyticsTracker = new ProvisioningAnalyticsTracker(
                MetricsWriterFactory.getMetricsWriter(this, mSettingsFacade),
                new ManagedProvisioningSharedPreferences(getApplicationContext()));
        mProvisioningAnalyticsTracker.logNumberOfTermsDisplayed(this, mViewModel.getTerms().size());
    }

    protected TermsActivityBridge createBridge() {
        return TermsActivityBridgeImpl.builder()
                .setUtils(mUtils)
                .setStylerHelper(mStylerHelper)
                .setTransitionHelper(getTransitionHelper())
                .build();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v instanceof TextView) {
            mContextMenuMaker.populateMenuContent(menu, (TextView) v);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        mProvisioningAnalyticsTracker.logNumberOfTermsRead(
                this, mViewModel.getNumberOfReadTerms());
        super.onDestroy();
    }

    @Override
    protected int getMetricsCategory() {
        return PROVISIONING_TERMS_ACTIVITY_TIME_MS;
    }

    @Override
    public boolean isTermExpanded(int groupPosition) {
        return mViewModel.isTermExpanded(groupPosition);
    }

    @Override
    public void onTermExpanded(int groupPosition, boolean expanded) {
        mViewModel.setTermExpanded(groupPosition, expanded);
    }

    @Override
    public void onLinkClicked(Intent intent) {
        getTransitionHelper().startActivityWithTransition(this, intent);
    }

    @Override
    protected boolean shouldSetupDynamicColors() {
        Context context = getApplicationContext();
        return !mSettingsFacade.isDeferredSetup(context)
                && !mSettingsFacade.isDuringSetupWizard(context);
    }
}
