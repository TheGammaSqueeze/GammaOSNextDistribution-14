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

package com.android.tv.settings.library.about;

import static com.android.tv.settings.library.ManagerUtil.STATE_LEGAL;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.State;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;
import com.android.tv.settings.library.overlay.FlavorUtils;
import com.android.tv.settings.library.util.PreferenceCompatUtils;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.ArrayList;
import java.util.List;

public class LegalState implements State {
    private static final String KEY_TERMS = "terms";
    private static final String KEY_LICENSE = "license";
    private static final String KEY_COPYRIGHT = "copyright";
    private static final String KEY_WEBVIEW_LICENSE = "webview_license";
    private static final String KEY_ADS = "ads";
    private static final String KEY_CONSUMER_INFORMATION = "consumer_information";

    private final Context mContext;
    private final UIUpdateCallback mUIUpdateCallback;

    private PreferenceCompatManager mPreferenceCompatManager;

    public LegalState(Context context, UIUpdateCallback uiUpdateCallback) {
        this.mContext = context;
        this.mUIUpdateCallback = uiUpdateCallback;
    }

    @Override
    public void onAttach() {

    }

    @Override
    public void onCreate(Bundle extras) {
        mPreferenceCompatManager = new PreferenceCompatManager();

        final List<PreferenceCompat> preferenceList = new ArrayList<>();
        final PreferenceCompat termsPreference = mPreferenceCompatManager
                .getOrCreatePrefCompat(KEY_TERMS);
        termsPreference.setIntent(new Intent().setAction("android.settings.TERMS"));
        preferenceList.add(termsPreference);

        final PreferenceCompat licensePreference = mPreferenceCompatManager
                .getOrCreatePrefCompat(KEY_LICENSE);
        licensePreference.setIntent(new Intent().setAction("android.settings.LICENSE"));
        preferenceList.add(licensePreference);

        final PreferenceCompat copyrightPreference = mPreferenceCompatManager
                .getOrCreatePrefCompat(KEY_COPYRIGHT);
        copyrightPreference.setIntent(new Intent().setAction("android.settings.COPYRIGHT"));
        preferenceList.add(copyrightPreference);

        final PreferenceCompat webViewLicensePreference = mPreferenceCompatManager
                .getOrCreatePrefCompat(KEY_WEBVIEW_LICENSE);
        webViewLicensePreference.setIntent(new Intent()
                .setAction("android.settings.WEBVIEW_LICENSE"));
        preferenceList.add(webViewLicensePreference);

        final PreferenceCompat adsPreference = mPreferenceCompatManager
                .getOrCreatePrefCompat(KEY_ADS);
        adsPreference.setIntent(new Intent()
                .setAction("com.google.android.gms.settings.ADS_PRIVACY"));
        preferenceList.add(adsPreference);

        final PreferenceCompat consumerInformationDialogPreference =
                mPreferenceCompatManager.getOrCreatePrefCompat(KEY_CONSUMER_INFORMATION);
        consumerInformationDialogPreference.setMessage(
                ResourcesUtil.getString(mContext, "consumer_information_message"));
        consumerInformationDialogPreference.setPositiveButtonText(
                ResourcesUtil.getString(mContext, "consumer_information_button_ok"));
        consumerInformationDialogPreference.setType(PreferenceCompat.TYPE_DIALOG);
        preferenceList.add(consumerInformationDialogPreference);

        PreferenceCompatUtils.resolveSystemActivityOrRemove(
                mContext,
                preferenceList,
                termsPreference,
                0);
        PreferenceCompatUtils.resolveSystemActivityOrRemove(
                mContext,
                preferenceList,
                licensePreference,
                0);
        PreferenceCompatUtils.resolveSystemActivityOrRemove(
                mContext,
                preferenceList,
                copyrightPreference,
                0);
        PreferenceCompatUtils.resolveSystemActivityOrRemove(
                mContext,
                preferenceList,
                webViewLicensePreference,
                0);
        if (FlavorUtils.isTwoPanel(mContext)) {
            preferenceList.remove(adsPreference);
        } else {
            PreferenceCompatUtils.resolveSystemActivityOrRemove(
                    mContext,
                    preferenceList,
                    adsPreference,
                    0);
        }

        if (mUIUpdateCallback != null) {
            mUIUpdateCallback.notifyUpdateAll(STATE_LEGAL, preferenceList);
        }
    }

    @Override
    public void onStart() {

    }

    @Override
    public void onResume() {

    }

    @Override
    public void onPause() {

    }

    @Override
    public void onStop() {

    }

    @Override
    public void onDestroy() {

    }

    @Override
    public void onDetach() {

    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

    }

    @Override
    public boolean onPreferenceChange(String[] key, Object newValue) {
        return false;
    }

    @Override
    public int getStateIdentifier() {
        return STATE_LEGAL;
    }

    @Override
    public void onDisplayDialogPreference(String[] key) {
        final PreferenceCompat consumerInformationDialogPreference =
                mPreferenceCompatManager.getPrefCompat(key);
        if (consumerInformationDialogPreference != null) {
            new AlertDialog.Builder(mContext)
                    .setMessage(consumerInformationDialogPreference.getMessage())
                    .setPositiveButton(
                            consumerInformationDialogPreference.getPositiveButtonText(),
                            (dialog, which) -> {})
                    .show();
        }
    }
}
