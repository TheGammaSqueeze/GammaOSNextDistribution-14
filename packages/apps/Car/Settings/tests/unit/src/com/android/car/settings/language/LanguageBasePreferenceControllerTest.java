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

package com.android.car.settings.language;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.internal.app.LocalePicker;
import com.android.internal.app.LocaleStore;
import com.android.internal.app.SuggestedLocaleAdapter;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class LanguageBasePreferenceControllerTest {

    private static final LocaleStore.LocaleInfo TEST_LOCALE_INFO = LocaleStore.getLocaleInfo(
            Locale.FRENCH);

    private LanguageBasePreferenceController mController;
    private Context mContext;
    private PreferenceGroup mPreferenceGroup;
    private MockitoSession mSession;
    @Mock
    private FragmentController mFragmentController;
    @Mock
    private SuggestedLocaleAdapter mSuggestedLocaleAdapter;
    @Mock
    private LocaleStore.LocaleInfo mLocaleInfo;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        LifecycleOwner lifecycleOwner = new TestLifecycleOwner();

        mContext = ApplicationProvider.getApplicationContext();
        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
        initMocks();
        CarUxRestrictions carUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        LocalePreferenceProvider localePreferenceProvider =
                new LocalePreferenceProvider(mContext, mSuggestedLocaleAdapter);
        mController = new LanguageBasePreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, carUxRestrictions) {
            @Override
            protected LocalePreferenceProvider defineLocaleProvider() {
                return localePreferenceProvider;
            }
        };
        PreferenceControllerTestUtil.assignPreference(mController, mPreferenceGroup);

        mController.onCreate(lifecycleOwner);
    }

    @After
    @UiThreadTest
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void testRefreshUi_groupConstructed() {
        when(mSuggestedLocaleAdapter.getCount()).thenReturn(1);
        when(mSuggestedLocaleAdapter.getItemViewType(0)).thenReturn(
                LocalePreferenceProvider.TYPE_LOCALE);
        when(mSuggestedLocaleAdapter.getItem(0)).thenReturn(TEST_LOCALE_INFO);
        mController.refreshUi();
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void testOnPreferenceClick_noLocale_returnsFalse() {
        assertThat(mController.onPreferenceClick(new Preference(mContext))).isFalse();
    }

    @Test
    public void testOnPreferenceClick_hasMultipleChildLocales_returnsTrue() {
        when(LocaleUtil.getLocaleArgument(any())).thenReturn(mLocaleInfo);
        ExtendedMockito.when(LocaleStore.getLevelLocales(mContext,
                mController.getExclusionSet(), mLocaleInfo, true))
                .thenReturn(new HashSet<>(Arrays.asList(
                        mock(LocaleStore.LocaleInfo.class),
                        mock(LocaleStore.LocaleInfo.class))));
        Preference preference = new Preference(mContext);

        assertThat(mController.onPreferenceClick(preference)).isTrue();
    }

    @Test
    public void testOnPreferenceClick_hasMultipleChildLocales_localeNotUpdated() {
        when(LocaleUtil.getLocaleArgument(any())).thenReturn(mLocaleInfo);
        ExtendedMockito.when(LocaleStore.getLevelLocales(mContext,
                mController.getExclusionSet(), mLocaleInfo, true))
                .thenReturn(new HashSet<>(Arrays.asList(
                        mock(LocaleStore.LocaleInfo.class),
                        mock(LocaleStore.LocaleInfo.class))));
        Preference preference = new Preference(mContext);
        mController.onPreferenceClick(preference);

        ExtendedMockito.verify(() -> LocalePicker.updateLocale(any()), never());
    }

    @Test
    public void testOnPreferenceClick_hasMultipleChildLocales_neverCallsGoBack() {
        when(LocaleUtil.getLocaleArgument(any())).thenReturn(mLocaleInfo);
        ExtendedMockito.when(LocaleStore.getLevelLocales(mContext,
                mController.getExclusionSet(), mLocaleInfo, true))
                .thenReturn(new HashSet<>(Arrays.asList(
                        mock(LocaleStore.LocaleInfo.class),
                        mock(LocaleStore.LocaleInfo.class))));
        Preference preference = new Preference(mContext);
        mController.onPreferenceClick(preference);

        verify(mFragmentController, never()).goBack();
    }

    @Test
    public void testOnPreferenceClick_hasSingleChildLocale_returnsTrue() {
        when(LocaleUtil.getLocaleArgument(any())).thenReturn(mLocaleInfo);
        ExtendedMockito.when(LocaleStore.getLevelLocales(mContext,
                mController.getExclusionSet(), mLocaleInfo, true))
                .thenReturn(new HashSet<>(Arrays.asList(
                        mock(LocaleStore.LocaleInfo.class))));
        Preference preference = new Preference(mContext);
        assertThat(mController.onPreferenceClick(preference)).isTrue();
    }

    @Test
    public void testOnPreferenceClick_hasSingleChildLocale_localeUpdated() {
        when(LocaleUtil.getLocaleArgument(any())).thenReturn(mLocaleInfo);
        ExtendedMockito.when(LocaleStore.getLevelLocales(mContext,
                mController.getExclusionSet(), mLocaleInfo, true))
                .thenReturn(new HashSet<>(Arrays.asList(
                        mock(LocaleStore.LocaleInfo.class))));
        Preference preference = new Preference(mContext);
        mController.onPreferenceClick(preference);

        ExtendedMockito.verify(() -> LocalePicker.updateLocale(any()), times(1));
    }

    @Test
    public void testOnPreferenceClick_hasSingleChildLocale_callsGoBack() {
        when(LocaleUtil.getLocaleArgument(any())).thenReturn(mLocaleInfo);
        ExtendedMockito.when(LocaleStore.getLevelLocales(mContext,
                mController.getExclusionSet(), mLocaleInfo, true))
                .thenReturn(new HashSet<>(Arrays.asList(
                        mock(LocaleStore.LocaleInfo.class))));
        Preference preference = new Preference(mContext);
        mController.onPreferenceClick(preference);

        verify(mFragmentController).goBack();
    }

    @Test
    public void testOnPreferenceClick_noChildLocale_returnsFalse() {
        when(LocaleUtil.getLocaleArgument(any())).thenReturn(mLocaleInfo);
        Preference preference = new Preference(mContext);
        assertThat(mController.onPreferenceClick(preference)).isFalse();
    }

    @Test
    public void testOnPreferenceClick_noChildLocale_localeNotUpdated() {
        when(LocaleUtil.getLocaleArgument(any())).thenReturn(mLocaleInfo);
        Preference preference = new Preference(mContext);
        mController.onPreferenceClick(preference);
        ExtendedMockito.verify(() -> LocalePicker.updateLocale(any()), never());
    }

    @Test
    public void testOnPreferenceClick_noChildLocale_neverCallsGoBack() {
        when(LocaleUtil.getLocaleArgument(any())).thenReturn(mLocaleInfo);
        Preference preference = new Preference(mContext);
        mController.onPreferenceClick(preference);
        verify(mFragmentController, never()).goBack();
    }

    @Test
    public void testOnPreferenceClick_hasParentLocale_returnsTrue() {
        when(mLocaleInfo.getParent()).thenReturn(Locale.ENGLISH);
        when(LocaleUtil.getLocaleArgument(any())).thenReturn(mLocaleInfo);
        Preference preference = new Preference(mContext);
        assertThat(mController.onPreferenceClick(preference)).isTrue();
    }

    @Test
    public void testOnPreferenceClick_hasParentLocale_localeUpdated() {
        when(mLocaleInfo.getParent()).thenReturn(Locale.ENGLISH);
        when(LocaleUtil.getLocaleArgument(any())).thenReturn(mLocaleInfo);
        Preference preference = new Preference(mContext);
        mController.onPreferenceClick(preference);
        ExtendedMockito.verify(() -> LocalePicker.updateLocale(any()), times(1));
    }

    @Test
    public void testOnPreferenceClick_hasParentLocale_callsGoBack() {
        when(mLocaleInfo.getParent()).thenReturn(Locale.ENGLISH);
        when(LocaleUtil.getLocaleArgument(any())).thenReturn(mLocaleInfo);
        Preference preference = new Preference(mContext);
        mController.onPreferenceClick(preference);
        verify(mFragmentController).goBack();
    }

    private void initMocks() {
        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(LocalePicker.class, withSettings().lenient())
                .mockStatic(LocaleStore.class, withSettings().lenient())
                .mockStatic(LocaleUtil.class, withSettings().lenient())
                .startMocking();
        }
}
