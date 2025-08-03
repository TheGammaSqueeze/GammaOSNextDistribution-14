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

package com.android.tv.settings.system.locale;

import android.app.ActivityManager;
import android.app.tvsettings.TvSettingsEnums;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.LocaleList;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Keep;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.app.LocaleHelper;
import com.android.internal.app.LocaleStore;
import com.android.tv.settings.R;
import com.android.tv.settings.RadioPreference;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.settings.library.util.ThreadUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/** Language picker settings screen for locale selection. */
@Keep
public class LanguagePickerFragment extends SettingsPreferenceFragment {
    private static final String TAG = "LanguagePickerFragment";
    private static final String LANGUAGE_PICKER_RADIO_GROUP = "language_picker_group";
    private ArrayList<LocaleStore.LocaleInfo> mLocaleInfos;
    private LocaleDataViewModel mLocaleDataViewModel;
    private static final boolean DEBUG = Build.isDebuggable();
    static final String KEY_LOCALE_INFO = "locale_info";
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mProgressBarHidden;

    public static LanguagePickerFragment newInstance() {
        return new LanguagePickerFragment();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        mLocaleDataViewModel = new ViewModelProvider(getActivity()).get(LocaleDataViewModel.class);
        final Context themedContext = getPreferenceManager().getContext();
        final PreferenceScreen screen =
                getPreferenceManager().createPreferenceScreen(themedContext);
        screen.setTitle(R.string.system_language);
        Context applicationContext = getContext().getApplicationContext();
        Runnable mainThreadRunnable = () -> createPreferences(screen);
        Future localeLoadedFuture = ThreadUtils.postOnBackgroundThread(() -> {
            loadLocales(applicationContext, mainThreadRunnable);
        });
        setPreferenceScreen(screen);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final ViewGroup view =
                (ViewGroup) super.onCreateView(inflater, container, savedInstanceState);
        LayoutInflater themedInflater = LayoutInflater.from(view.getContext());
        final View progressContainer =
                themedInflater.inflate(R.layout.settings_progress_bar, container, false);
        ((ViewGroup) progressContainer).addView(view);
        progressContainer.setVisibility(View.VISIBLE);
        return progressContainer;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        showProgressBar();
    }

    private void showProgressBar() {
        if (mProgressBarHidden) {
            return;
        }
        View progressBar = requireView().requireViewById(R.id.progress_bar);
        progressBar.bringToFront();
        progressBar.setAlpha(0f);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.animate().alpha(1f).setStartDelay(1000).setDuration(250).start();
    }

    private void hideProgressBar() {
        mProgressBarHidden = true;
        if (getView() != null) {
            View progressBar = requireView().requireViewById(R.id.progress_bar);
            progressBar.setVisibility(View.GONE);
        }
    }

    private void loadLocales(Context applicationContext, Runnable mainThreadRunnable) {
        mLocaleInfos = new ArrayList<>(mLocaleDataViewModel.getLocaleInfos(applicationContext));

        final Locale sortingLocale = Locale.getDefault();
        final LocaleHelper.LocaleInfoComparator comp =
                new LocaleHelper.LocaleInfoComparator(sortingLocale, false);
        mLocaleInfos.sort(comp);
        for (LocaleStore.LocaleInfo localeInfo : mLocaleInfos) {
            mLocaleDataViewModel.addLocaleInfoList(localeInfo, applicationContext);
        }
        mHandler.post(mainThreadRunnable);
    }

    private void createPreferences(PreferenceScreen screen) {
        hideProgressBar();
        final Set<LocaleStore.LocaleInfo> notSuggestedLocales =
                mLocaleInfos.stream().filter(
                        localeInfo -> !localeInfo.isSuggested()).collect(Collectors.toSet());
        for (LocaleStore.LocaleInfo localeInfo : mLocaleInfos) {
            if (localeInfo.isSuggested()
                    && containsSuggestedLocale(notSuggestedLocales, localeInfo)) {
                continue;
            }
            Locale currentLocale = LocaleDataViewModel.getCurrentLocale();
            List<LocaleStore.LocaleInfo> localeInfoWithCountryList = mLocaleDataViewModel
                    .getLocaleInfoList(localeInfo);
            if (localeInfoWithCountryList != null && localeInfoWithCountryList.size() <= 1) {
                RadioPreference preference = new RadioPreference(getContext());
                preference.setTitle(localeInfo.getFullNameNative());
                if (localeInfoWithCountryList.size() == 1) {
                    localeInfo = localeInfoWithCountryList.get(0);
                }
                preference.setChecked(localeInfo.getLocale().equals(currentLocale));
                preference.setRadioGroup(LANGUAGE_PICKER_RADIO_GROUP);
                preference.getExtras().putSerializable(KEY_LOCALE_INFO, localeInfo);
                screen.addPreference(preference);
            } else {
                Preference preference = new Preference(getContext());
                preference.setFragment(CountryPickerFragment.class.getSimpleName());
                preference.setTitle(localeInfo.getFullNameNative());
                preference.getExtras().putAll(CountryPickerFragment.prepareArgs(localeInfo));
                preference.setFragment(CountryPickerFragment.class.getName());
                screen.addPreference(preference);
                preference.getExtras().putSerializable(KEY_LOCALE_INFO, localeInfo);
            }
        }
    }

    private static boolean containsSuggestedLocale(Set<LocaleStore.LocaleInfo> localeInfos,
            LocaleStore.LocaleInfo suggestedLocaleInfo) {
        return localeInfos.stream().anyMatch(localeInfo -> localeInfo.getLocale().getLanguage()
                        .equals(suggestedLocaleInfo.getLocale().getLanguage())
                && localeInfo.getLocale().getScript().equals(
                        suggestedLocaleInfo.getLocale().getScript()));
    }

    @Override
    public void onResume() {
        super.onResume();
        Locale currentLocale = LocaleDataViewModel.getCurrentLocale();
        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            Preference pref = getPreferenceScreen().getPreference(i);
            LocaleStore.LocaleInfo localeInfo = (LocaleStore.LocaleInfo)
                    pref.getExtras().getSerializable(KEY_LOCALE_INFO);
                final Locale locale = localeInfo.getLocale();
                if (locale != null
                        && locale.getLanguage().equals(currentLocale.getLanguage())
                        && locale.getScript().equals(currentLocale.getScript())) {
                    if (DEBUG) {
                        Log.d(TAG, "Scroll to active locale: " + locale);
                    }
                    mHandler.post(() -> scrollToPreference(pref));
                }
            }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (DEBUG) {
            Log.d(TAG, "Preference clicked: " + preference.getTitle());
        }
        if (preference instanceof RadioPreference localePref) {
            if (!localePref.isChecked()) {
                localePref.setChecked(true);
                return true;
            }
            LocaleStore.LocaleInfo localeInfo = localePref.getExtras()
                    .getSerializable(KEY_LOCALE_INFO, LocaleStore.LocaleInfo.class);
            if (localeInfo != null) {
                getContext().getSystemService(ActivityManager.class)
                        .setDeviceLocales(new LocaleList(localeInfo.getLocale()));
            }
            localePref.clearOtherRadioPreferences(getPreferenceScreen());
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.SYSTEM_LANGUAGE;
    }
}
