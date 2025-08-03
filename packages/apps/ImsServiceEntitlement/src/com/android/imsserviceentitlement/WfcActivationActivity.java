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

package com.android.imsserviceentitlement;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemProperties;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.setupdesign.util.ThemeHelper;
import com.google.android.setupdesign.util.ThemeResolver;

/** The UI for WFC activation. */
public class WfcActivationActivity extends FragmentActivity implements WfcActivationUi {
    private static final String TAG = "IMSSE-WfcActivationActivity";

    // Dependencies
    private WfcActivationController mWfcActivationController;
    private WfcWebPortalFragment mWfcWebPortalFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        createDependeny();
        setSuwTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wfc_activation);

        int subId = ActivityConstants.getSubId(getIntent());
        mWfcActivationController.startFlow();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mWfcActivationController.finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mWfcWebPortalFragment != null && mWfcWebPortalFragment.onKeyDown(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean showActivationUi(
            @StringRes int title,
            @StringRes int text,
            boolean isInProgress,
            @StringRes int primaryButtonText,
            int primaryButtonResult,
            @StringRes int secondaryButtonText) {
        runOnUiThreadIfAlive(
                () -> {
                    FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                    SuwUiFragment frag =
                            SuwUiFragment.newInstance(
                                    title,
                                    text,
                                    isInProgress,
                                    primaryButtonText,
                                    primaryButtonResult,
                                    secondaryButtonText);
                    ft.replace(R.id.wfc_activation_container, frag);
                    // commit may be executed after activity's state is saved.
                    ft.commitAllowingStateLoss();
                });
        return true;
    }

    @Override
    public boolean showWebview(String url, String postData) {
        runOnUiThreadIfAlive(
                () -> {
                    FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                    mWfcWebPortalFragment = WfcWebPortalFragment.newInstance(url, postData);
                    ft.replace(R.id.wfc_activation_container, mWfcWebPortalFragment);
                    // commit may be executed after activity's state is saved.
                    ft.commitAllowingStateLoss();
                });
        return true;
    }

    private void runOnUiThreadIfAlive(Runnable r) {
        if (!isFinishing() && !isDestroyed()) {
            runOnUiThread(r);
        }
    }

    @Override
    public void setResultAndFinish(int resultCode) {
        Log.d(TAG, "setResultAndFinish: result=" + resultCode);
        if (!isFinishing() && !isDestroyed()) {
            setResult(resultCode);
            finish();
        }
    }

    @Override
    public WfcActivationController getController() {
        return mWfcActivationController;
    }

    private void setSuwTheme() {
        int defaultTheme =
                ThemeHelper.isSetupWizardDayNightEnabled(this)
                        ? com.google.android.setupdesign.R.style.SudThemeGlifV3_DayNight
                        : com.google.android.setupdesign.R.style.SudThemeGlifV3_Light;
        ThemeResolver themeResolver =
                new ThemeResolver.Builder(ThemeResolver.getDefault())
                        .setDefaultTheme(defaultTheme)
                        .setUseDayNight(true)
                        .build();
        setTheme(themeResolver.resolve(
                SystemProperties.get("setupwizard.theme", "SudThemeGlifV3_DayNight"),
                /* suppressDayNight= */ !ThemeHelper.isSetupWizardDayNightEnabled(this)));
    }

    private void createDependeny() {
        Log.d(TAG, "Loading dependencies...");
        // TODO(b/177495634) Use DependencyInjector
        if (mWfcActivationController == null) {
            // Default initialization
            Log.d(TAG, "Default WfcActivationController initialization");
            Intent startIntent = this.getIntent();
            int subId = ActivityConstants.getSubId(startIntent);
            mWfcActivationController =
                    new WfcActivationController(
                            /* context = */ this,
                            /* wfcActivationUi = */ this,
                            new ImsEntitlementApi(this, subId),
                            this.getIntent());
        }
    }
}
