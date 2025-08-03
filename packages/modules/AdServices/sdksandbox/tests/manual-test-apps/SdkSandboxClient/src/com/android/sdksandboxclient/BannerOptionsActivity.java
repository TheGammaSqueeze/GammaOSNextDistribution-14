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

package com.android.sdksandboxclient;

import android.os.Bundle;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class BannerOptionsActivity extends AppCompatActivity {

    public BannerOptionsActivity() {
        super(R.layout.activity_options);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.options_container, BannerOptionsFragment.class, null)
                    .commit();
        }
    }

    public static class BannerOptionsFragment extends PreferenceFragmentCompat {

        private final Executor mExecutor = Executors.newSingleThreadExecutor();
        private EditTextPreference mVideoUrlPreference;

        private EditTextPreference mPackageToOpen;
        private ListPreference mOnClickPreference;
        private ListPreference mSizePreference;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            mExecutor.execute(
                    () -> {
                        Looper.prepare();
                        setPreferencesFromResource(R.xml.banner_preferences, rootKey);
                        configurePreferences();
                    });
        }

        @NonNull
        private Preference findPreferenceOrFail(String key) {
            final Preference preference = findPreference(key);
            if (preference == null) {
                throw new RuntimeException(String.format("Could not find preference '%s'", key));
            }
            return preference;
        }

        private void configurePreferences() {
            mVideoUrlPreference = (EditTextPreference) findPreferenceOrFail("banner_video_url");
            mOnClickPreference = (ListPreference) findPreferenceOrFail("banner_on_click");
            mPackageToOpen = (EditTextPreference) findPreferenceOrFail("package_to_open");
            mSizePreference = (ListPreference) findPreferenceOrFail("banner_ad_size");
            final ListPreference viewTypePreference =
                    (ListPreference) findPreferenceOrFail("banner_view_type");

            viewTypePreference.setOnPreferenceChangeListener(
                    (preference, object) -> {
                        final String selection = (String) object;
                        refreshVideoPreferenceVisibility(selection);
                        refreshOnClickEnabled(selection);
                        refreshAdSize((selection));
                        return true;
                    });

            final String viewTypeSelection = viewTypePreference.getValue();
            refreshVideoPreferenceVisibility(viewTypeSelection);
            refreshOnClickEnabled(viewTypeSelection);
            refreshAdSize(viewTypeSelection);
        }

        private void refreshVideoPreferenceVisibility(String viewTypeSelection) {
            BannerOptions.ViewType viewType = BannerOptions.ViewType.valueOf(viewTypeSelection);
            mVideoUrlPreference.setVisible(viewType == BannerOptions.ViewType.VIDEO);
        }

        private void refreshOnClickEnabled(String viewTypeSelection) {
            BannerOptions.ViewType viewType = BannerOptions.ViewType.valueOf(viewTypeSelection);
            switch (viewType) {
                case VIDEO:
                {
                    mOnClickPreference.setEnabled(false);
                    mOnClickPreference.setSummaryProvider(null);
                    mOnClickPreference.setSummary("Video controls");
                    break;
                }
                case WEBVIEW:
                {
                    mOnClickPreference.setEnabled(false);
                    mOnClickPreference.setSummaryProvider(null);
                    mOnClickPreference.setSummary("WebView receives clicks");
                    break;
                }
                case EDITTEXT:
                {
                    mOnClickPreference.setEnabled(false);
                    mOnClickPreference.setSummaryProvider(null);
                    mOnClickPreference.setSummary("EditText doesn't need to be clicked");
                    break;
                }
                default:
                {
                    mOnClickPreference.setEnabled(true);
                    mOnClickPreference.setSummaryProvider(
                            ListPreference.SimpleSummaryProvider.getInstance());
                    break;
                }
            }
        }

        private void refreshAdSize(String viewTypeSelection) {
            BannerOptions.ViewType viewType = BannerOptions.ViewType.valueOf(viewTypeSelection);
            switch (viewType) {
                case WEBVIEW:
                {
                    mSizePreference.setEnabled(false);
                    mSizePreference.setSummaryProvider(null);
                    mSizePreference.setSummary("WebView must be large");
                    break;
                }
                default:
                {
                    mSizePreference.setEnabled(true);
                    mSizePreference.setSummaryProvider(
                            ListPreference.SimpleSummaryProvider.getInstance());
                    break;
                }
            }
        }
    }
}
