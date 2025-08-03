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

package com.android.car.settings.wifi;

import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_ENABLED;
import static android.os.UserManager.DISALLOW_CONFIG_WIFI;

import static com.android.car.settings.testutils.EnterpriseTestUtils.mockUserRestrictionSetByDpm;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.UserManager;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
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
import com.android.car.settings.wifi.details.WifiDetailsFragment;
import com.android.car.ui.preference.CarUiTwoActionIconPreference;
import com.android.wifitrackerlib.WifiEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class WifiEntryListPreferenceControllerTest {
    private static final int SIGNAL_LEVEL = 1;

    // Use a spied context to be able to mock UserManager.
    private Context mSpiedContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private PreferenceGroup mPreferenceGroup;
    private WifiEntryListPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private Lifecycle mMockLifecycle;
    @Mock
    private CarWifiManager mMockCarWifiManager;
    @Mock
    private UserManager mMockUserManager;
    @Mock
    private WifiManager mMockWifiManager;
    @Mock
    private WifiEntry mMockWifiEntry1;
    @Mock
    private WifiEntry mMockWifiEntry2;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        PreferenceManager preferenceManager = new PreferenceManager(mSpiedContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mSpiedContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mSpiedContext);
        screen.addPreference(mPreferenceGroup);
        when(mFragmentController.getSettingsLifecycle()).thenReturn(mMockLifecycle);
        mPreferenceController = new TestWifiEntryListPreferenceController(mSpiedContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);

        when(mSpiedContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(mMockWifiEntry1.getSecurity()).thenReturn(WifiEntry.SECURITY_NONE);
        when(mMockWifiEntry1.getLevel()).thenReturn(SIGNAL_LEVEL);
        when(mMockWifiEntry2.getSecurity()).thenReturn(WifiEntry.SECURITY_NONE);
        when(mMockWifiEntry2.getLevel()).thenReturn(SIGNAL_LEVEL);
    }

    @Test
    public void refreshUi_emptyList_notVisible() {
        when(mMockCarWifiManager.getAllWifiEntries()).thenReturn(new ArrayList<>());
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceGroup.isVisible()).isEqualTo(false);
    }

    @Test
    public void refreshUi_notEmpty_visible() {
        List<WifiEntry> wifiEntryList = Arrays.asList(mMockWifiEntry1);
        when(mMockCarWifiManager.getAllWifiEntries()).thenReturn(wifiEntryList);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceGroup.isVisible()).isEqualTo(true);
    }

    @Test
    public void refreshUi_notEmpty_listCount() {
        List<WifiEntry> wifiEntryList = Arrays.asList(mMockWifiEntry1);
        when(mMockCarWifiManager.getAllWifiEntries()).thenReturn(wifiEntryList);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(wifiEntryList.size());
    }

    @Test
    public void refreshUi_notSavedWifiEntry_noForgetButton() {
        when(mMockWifiEntry1.isSaved()).thenReturn(false);
        List<WifiEntry> wifiEntryList = Arrays.asList(mMockWifiEntry1);
        when(mMockCarWifiManager.getAllWifiEntries()).thenReturn(wifiEntryList);
        mPreferenceController.onCreate(mLifecycleOwner);

        CarUiTwoActionIconPreference preference =
                (CarUiTwoActionIconPreference) mPreferenceGroup.getPreference(0);
        assertThat(preference.isSecondaryActionVisible()).isFalse();
    }

    @Test
    public void onUxRestrictionsChanged_switchToSavedWifiEntriesOnly() {
        List<WifiEntry> wifiEntryList = Arrays.asList(mMockWifiEntry1, mMockWifiEntry2);
        when(mMockCarWifiManager.getAllWifiEntries()).thenReturn(wifiEntryList);
        List<WifiEntry> savedWifiEntryList = Arrays.asList(mMockWifiEntry1);
        when(mMockCarWifiManager.getSavedWifiEntries()).thenReturn(savedWifiEntryList);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(wifiEntryList.size());

        CarUxRestrictions noSetupRestrictions = new CarUxRestrictions.Builder(
                true, CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP, 0).build();
        mPreferenceController.onUxRestrictionsChanged(noSetupRestrictions);
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(savedWifiEntryList.size());
    }

    @Test
    public void performClick_noSecurityNotConnectedWifiEntry_connect() {
        when(mMockWifiEntry1.isPrimaryNetwork()).thenReturn(true);
        when(mMockWifiEntry1.getSecurity()).thenReturn(WifiEntry.SECURITY_NONE);
        when(mMockWifiEntry1.isSaved()).thenReturn(false);
        List<WifiEntry> wifiEntryList = Arrays.asList(mMockWifiEntry1);
        when(mMockCarWifiManager.getAllWifiEntries()).thenReturn(wifiEntryList);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceGroup.getPreference(0).performClick();
        verify(mMockWifiEntry1).connect(any());
    }

    @Test
    public void performClick_shouldEditBeforeConnect_launchDialog() {
        when(mMockWifiEntry1.isPrimaryNetwork()).thenReturn(true);
        when(mMockWifiEntry1.getSecurity()).thenReturn(WifiEntry.SECURITY_PSK);
        when(mMockWifiEntry1.isSaved()).thenReturn(false);
        when(mMockWifiEntry1.shouldEditBeforeConnect()).thenReturn(true);
        List<WifiEntry> wifiEntryList = Arrays.asList(mMockWifiEntry1);
        when(mMockCarWifiManager.getAllWifiEntries()).thenReturn(wifiEntryList);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceGroup.getPreference(0).performClick();
        verify(mFragmentController).showDialog(any(WifiPasswordDialog.class), any());
    }

    @Test
    @UiThreadTest
    public void performClick_activeWifiEntry_showDetailsFragment() {
        when(mMockWifiEntry1.getConnectedState()).thenReturn(WifiEntry.CONNECTED_STATE_CONNECTED);
        when(mMockWifiEntry1.isPrimaryNetwork()).thenReturn(true);
        when(mMockCarWifiManager.getConnectedWifiEntries())
                .thenReturn(Collections.singletonList(mMockWifiEntry1));
        when(mMockCarWifiManager.getAllWifiEntries()).thenReturn(new ArrayList<>());
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceGroup.getPreference(0).performClick();
        verify(mFragmentController).launchFragment(any(WifiDetailsFragment.class));
    }

    @Test
    @UiThreadTest
    public void performClick_secondaryActiveWifiEntry_doesNotShowDetailsFragment() {
        when(mMockWifiEntry1.getConnectedState()).thenReturn(WifiEntry.CONNECTED_STATE_CONNECTED);
        when(mMockWifiEntry1.isPrimaryNetwork()).thenReturn(false);
        when(mMockCarWifiManager.getConnectedWifiEntries())
                .thenReturn(Collections.singletonList(mMockWifiEntry1));
        when(mMockCarWifiManager.getAllWifiEntries()).thenReturn(new ArrayList<>());
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceGroup.getPreference(0).performClick();
        verify(mFragmentController, times(0)).launchFragment(any(WifiDetailsFragment.class));
    }

    @Test
    public void performClick_savedWifiEntry_connect() {
        when(mMockWifiEntry1.isSaved()).thenReturn(true);
        when(mMockWifiEntry1.isPrimaryNetwork()).thenReturn(true);
        List<WifiEntry> wifiEntryList = Arrays.asList(mMockWifiEntry1);
        when(mMockCarWifiManager.getAllWifiEntries()).thenReturn(wifiEntryList);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceGroup.getPreference(0).performClick();
        verify(mMockWifiEntry1).connect(any());
    }

    @Test
    public void performButtonClick_savedWifiEntry_forgetsNetwork() {
        int netId = 1;

        WifiConfiguration config = mock(WifiConfiguration.class);
        WifiConfiguration.NetworkSelectionStatus status = mock(
                WifiConfiguration.NetworkSelectionStatus.class);
        config.networkId = netId;
        when(mMockWifiEntry1.getSecurity()).thenReturn(WifiEntry.SECURITY_PSK);
        when(mMockWifiEntry1.isSaved()).thenReturn(true);
        when(mMockWifiEntry1.getWifiConfiguration()).thenReturn(config);
        when(config.getNetworkSelectionStatus()).thenReturn(status);
        when(status.getNetworkSelectionStatus()).thenReturn(NETWORK_SELECTION_ENABLED);

        List<WifiEntry> wifiEntryList = Arrays.asList(mMockWifiEntry1);
        when(mMockCarWifiManager.getAllWifiEntries()).thenReturn(wifiEntryList);
        mPreferenceController.onCreate(mLifecycleOwner);

        CarUiTwoActionIconPreference preference =
                (CarUiTwoActionIconPreference) mPreferenceGroup.getPreference(0);
        preference.performSecondaryActionClick();
        verify(mMockWifiEntry1).forget(any());
    }

    @Test
    public void callChangeListener_newSecureWifiEntry_wifiConnected() {
        String title = "test_title";
        String password = "test_password";
        when(mMockWifiEntry1.getTitle()).thenReturn(title);
        when(mMockWifiEntry1.getSecurity()).thenReturn(WifiEntry.SECURITY_PSK);
        when(mMockWifiEntry1.isSaved()).thenReturn(false);
        List<WifiEntry> wifiEntryList = Arrays.asList(mMockWifiEntry1);
        when(mMockCarWifiManager.getAllWifiEntries()).thenReturn(wifiEntryList);
        mPreferenceController.onCreate(mLifecycleOwner);

        WifiPasswordDialog dialog = mock(WifiPasswordDialog.class);
        WifiConfiguration config = WifiUtil.getWifiConfig(mMockWifiEntry1, password);
        when(dialog.getConfig()).thenReturn(config);
        when(dialog.getWifiEntry()).thenReturn(mMockWifiEntry1);
        mPreferenceController.mDialogListener.onSubmit(dialog);

        verify(mMockWifiManager).connect(eq(config), any());
    }

    @Test
    public void fetchWifiEntries_getSavedWifiEntries() {
        String title = "test_title";
        when(mMockWifiEntry1.getTitle()).thenReturn(title);
        when(mMockWifiEntry1.getSecurity()).thenReturn(WifiEntry.SECURITY_PSK);
        when(mMockWifiEntry1.isSaved()).thenReturn(false);
        List<WifiEntry> wifiEntryList = Arrays.asList(mMockWifiEntry1);
        List<WifiEntry> noWifiEntries = new ArrayList<>();

        when(mMockCarWifiManager.getSavedWifiEntries()).thenReturn(wifiEntryList);
        when(mMockCarWifiManager.getAllWifiEntries()).thenReturn(noWifiEntries);

        TestWifiEntryListPreferenceController prefController =
                (TestWifiEntryListPreferenceController) mPreferenceController;
        Set<String> prefsThatIgnore = new HashSet<>();
        prefsThatIgnore.add("unknown key");

        prefController.setUxRestrictionsIgnoredConfig(/* allIgnore= */ false, prefsThatIgnore);
        CarUxRestrictions noSetupRestrictions = new CarUxRestrictions.Builder(
                true, CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP, 0).build();
        mPreferenceController.onUxRestrictionsChanged(noSetupRestrictions);

        List<WifiEntry> result = prefController.fetchWifiEntries();

        assertThat(result).containsExactlyElementsIn(wifiEntryList);
    }

    @Test
    public void fetchWifiEntries_getAllWifiEntries() {
        String title = "test_title";
        when(mMockWifiEntry1.getTitle()).thenReturn(title);
        when(mMockWifiEntry1.getSecurity()).thenReturn(WifiEntry.SECURITY_PSK);
        when(mMockWifiEntry1.isSaved()).thenReturn(false);
        List<WifiEntry> wifiEntryList = Arrays.asList(mMockWifiEntry1);
        List<WifiEntry> noWifiEntries = new ArrayList<>();

        when(mMockCarWifiManager.getSavedWifiEntries()).thenReturn(wifiEntryList);
        when(mMockCarWifiManager.getAllWifiEntries()).thenReturn(noWifiEntries);

        TestWifiEntryListPreferenceController prefController =
                (TestWifiEntryListPreferenceController) mPreferenceController;

        prefController.setUxRestrictionsIgnoredConfig(/* allIgnore= */ false, new HashSet<>());

        List<WifiEntry> result = prefController.fetchWifiEntries();

        assertThat(result).containsExactlyElementsIn(noWifiEntries);
    }

    @Test
    public void fetchWifiEntries_restrictionByDpm_returnsNoEntries() {
        String title = "test_title";
        mockUserRestrictionSetByDpm(mMockUserManager, DISALLOW_CONFIG_WIFI, true);
        when(mMockWifiEntry1.getTitle()).thenReturn(title);
        when(mMockWifiEntry1.getSecurity()).thenReturn(WifiEntry.SECURITY_PSK);
        when(mMockWifiEntry1.isSaved()).thenReturn(false);
        List<WifiEntry> wifiEntryList = Arrays.asList(mMockWifiEntry1);
        when(mMockCarWifiManager.getSavedWifiEntries()).thenReturn(wifiEntryList);
        when(mMockCarWifiManager.getAllWifiEntries()).thenReturn(wifiEntryList);

        TestWifiEntryListPreferenceController prefController =
                (TestWifiEntryListPreferenceController) mPreferenceController;

        prefController.setUxRestrictionsIgnoredConfig(/* allIgnore= */ false, new HashSet<>());

        List<WifiEntry> result = prefController.fetchWifiEntries();

        assertThat(result).isEmpty();
    }

    private class TestWifiEntryListPreferenceController extends WifiEntryListPreferenceController {

        private boolean mAllIgnoresUxRestrictions = false;
        private Set<String> mPreferencesIgnoringUxRestrictions = new HashSet<>();

        TestWifiEntryListPreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController,
                CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected CarWifiManager getCarWifiManager() {
            return mMockCarWifiManager;
        }

        @Override
        WifiManager getWifiManager() {
            return mMockWifiManager;
        }

        public void setUxRestrictionsIgnoredConfig(boolean allIgnore, Set preferencesThatIgnore) {
            mAllIgnoresUxRestrictions = allIgnore;
            mPreferencesIgnoringUxRestrictions = preferencesThatIgnore;
        }

        @Override
        protected boolean isUxRestrictionsIgnored(boolean allIgnores, Set prefsThatIgnore) {
            return super.isUxRestrictionsIgnored(mAllIgnoresUxRestrictions,
                    mPreferencesIgnoringUxRestrictions);
        }
    }
}
