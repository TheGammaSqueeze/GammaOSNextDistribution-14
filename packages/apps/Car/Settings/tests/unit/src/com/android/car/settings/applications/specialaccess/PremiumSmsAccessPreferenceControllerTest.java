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

package com.android.car.settings.applications.specialaccess;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Looper;
import android.telephony.SmsManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.ListPreference;
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
import com.android.settingslib.applications.ApplicationsState;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class PremiumSmsAccessPreferenceControllerTest {

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private PreferenceGroup mPreferenceGroup;
    private PremiumSmsAccessPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private AppEntryListManager mMockAppEntryListManager;
    @Mock
    private ApplicationsState mMockApplicationsState;
    @Mock
    private SmsManager mMockSmsManager;
    @Captor
    private ArgumentCaptor<AppEntryListManager.Callback> mCallbackCaptor;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();
        when(mMockApplicationsState.getBackgroundLooper()).thenReturn(Looper.getMainLooper());

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new PremiumSmsAccessPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions, mMockSmsManager, mMockAppEntryListManager);

        PreferenceControllerTestUtil
                .assignPreference(mPreferenceController, mPreferenceGroup);

        mPreferenceController.onCreate(mLifecycleOwner);

        verify(mMockAppEntryListManager).init(any(AppStatePremiumSmsBridge.class), any(),
                mCallbackCaptor.capture());
    }

    @Test
    public void onStart_startsListManager() {
        mPreferenceController.onStart(mLifecycleOwner);

        verify(mMockAppEntryListManager).start();
    }

    @Test
    public void onStop_stopsListManager() {
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.onStop(mLifecycleOwner);

        verify(mMockAppEntryListManager).stop();
    }

    @Test
    public void onDestroy_destroysListManager() {
        mPreferenceController.onDestroy(mLifecycleOwner);

        verify(mMockAppEntryListManager).destroy();
    }

    @Test
    public void onAppEntryListChanged_addsPreferencesForEntries() {
        mPreferenceController.onStart(mLifecycleOwner);
        List<ApplicationsState.AppEntry> entries = Arrays.asList(
                createAppEntry("test.package", /* uid= */ 1,
                        SmsManager.PREMIUM_SMS_CONSENT_ALWAYS_ALLOW),
                createAppEntry("another.test.package", /* uid= */ 2,
                        SmsManager.PREMIUM_SMS_CONSENT_NEVER_ALLOW));

        mCallbackCaptor.getValue().onAppEntryListChanged(entries);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(2);
        assertThat(((ListPreference) mPreferenceGroup.getPreference(0)).getValue()).isEqualTo(
                String.valueOf(SmsManager.PREMIUM_SMS_CONSENT_ALWAYS_ALLOW));
        assertThat(((ListPreference) mPreferenceGroup.getPreference(1)).getValue()).isEqualTo(
                String.valueOf(SmsManager.PREMIUM_SMS_CONSENT_NEVER_ALLOW));
    }

    @Test
    public void onPreferenceChange_setsPremiumSmsPermission() {
        mPreferenceController.onStart(mLifecycleOwner);
        String packageName = "test.package";
        List<ApplicationsState.AppEntry> entries = Collections.singletonList(
                createAppEntry(packageName, /* uid= */ 1,
                        SmsManager.PREMIUM_SMS_CONSENT_NEVER_ALLOW));
        mCallbackCaptor.getValue().onAppEntryListChanged(entries);
        Preference appPref = mPreferenceGroup.getPreference(0);
        int updatedValue = SmsManager.PREMIUM_SMS_CONSENT_ASK_USER;

        appPref.getOnPreferenceChangeListener().onPreferenceChange(appPref,
                String.valueOf(updatedValue));

        verify(mMockSmsManager).setPremiumSmsConsent(packageName, updatedValue);
    }

    @Test
    public void onPreferenceChange_updatesEntry() {
        mPreferenceController.onStart(mLifecycleOwner);
        List<ApplicationsState.AppEntry> entries = Collections.singletonList(
                createAppEntry("test.package", /* uid= */ 1,
                        SmsManager.PREMIUM_SMS_CONSENT_NEVER_ALLOW));
        mCallbackCaptor.getValue().onAppEntryListChanged(entries);
        Preference appPref = mPreferenceGroup.getPreference(0);

        appPref.getOnPreferenceChangeListener().onPreferenceChange(appPref,
                String.valueOf(SmsManager.PREMIUM_SMS_CONSENT_ASK_USER));

        verify(mMockAppEntryListManager).forceUpdate(entries.get(0));
    }

    @Test
    public void appFilter_removesUnknownStates() {
        mPreferenceController.onStart(mLifecycleOwner);
        ArgumentCaptor<AppEntryListManager.AppFilterProvider> filterCaptor =
                ArgumentCaptor.forClass(AppEntryListManager.AppFilterProvider.class);
        verify(mMockAppEntryListManager).init(any(), filterCaptor.capture(), any());
        ApplicationsState.AppFilter filter = filterCaptor.getValue().getAppFilter();
        ApplicationsState.AppEntry unknownStateApp = createAppEntry("test.package", /* uid= */ 1,
                SmsManager.PREMIUM_SMS_CONSENT_UNKNOWN);

        assertThat(filter.filterApp(unknownStateApp)).isFalse();
    }

    private ApplicationsState.AppEntry createAppEntry(String packageName, int uid, int smsState) {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = packageName;
        info.uid = uid;

        ApplicationsState.AppEntry appEntry = mock(ApplicationsState.AppEntry.class);
        appEntry.info = info;
        appEntry.label = packageName;
        appEntry.extraInfo = smsState;

        return appEntry;
    }
}
