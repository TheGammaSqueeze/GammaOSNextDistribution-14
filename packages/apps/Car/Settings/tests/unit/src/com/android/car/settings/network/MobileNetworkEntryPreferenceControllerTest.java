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

package com.android.car.settings.network;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.drivingstate.CarUxRestrictions;
import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.UserManager;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiTwoActionSwitchPreference;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.settingslib.utils.StringUtil;

import com.google.android.collect.Lists;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class MobileNetworkEntryPreferenceControllerTest {

    private static final String TEST_NETWORK_NAME = "test network name";
    private static final int SUB_ID = 1;

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private MobileNetworkEntryPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private UserManager mUserManager;
    @Mock
    private SubscriptionManager mSubscriptionManager;
    @Mock
    private ConnectivityManager mConnectivityManager;
    @Mock
    private TelephonyManager mTelephonyManager;
    @Mock
    private NetworkCapabilities mCapabilities;
    @Mock
    private ContentResolver mMockContentResolver;
    @Mock
    private CarUiTwoActionSwitchPreference mMockPreference;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(SubscriptionManager.class, withSettings().lenient())
                .startMocking();

        // Setup to always make preference available.
        when(mContext.getSystemService(Context.USER_SERVICE)).thenReturn(mUserManager);
        when(mContext.getSystemService(SubscriptionManager.class)).thenReturn(mSubscriptionManager);
        // Because of the static mock of SubscriptionManager, Mockito thinks .from() is supposed to
        // return SubscriptionManager instead of TelephonyManager
        doReturn(mTelephonyManager).when(mContext).getSystemService(TelephonyManager.class);
        doReturn(mConnectivityManager).when(mContext).getSystemService(ConnectivityManager.class);
        when(mContext.getContentResolver()).thenReturn(mMockContentResolver);
        ExtendedMockito.when(SubscriptionManager.getDefaultDataSubscriptionId())
                .thenReturn(SUB_ID);

        when(mTelephonyManager.getSimState()).thenReturn(TelephonyManager.SIM_STATE_PRESENT);
        when(mTelephonyManager.getSimCount()).thenReturn(1);
        when(mTelephonyManager.isDataEnabled()).thenReturn(true);

        Network network = mock(Network.class);
        when(mCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(true);
        when(mConnectivityManager.getNetworkCapabilities(network)).thenReturn(mCapabilities);
        when(mConnectivityManager.getAllNetworks()).thenReturn(new Network[]{network});

        when(mUserManager.isAdminUser()).thenReturn(true);
        when(mUserManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS))
                .thenReturn(false);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreferenceController = new MobileNetworkEntryPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mMockPreference);
    }

    @After
    @UiThreadTest
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void getAvailabilityStatus_noSim_noMobileNetwork_unsupported() {
        when(mTelephonyManager.getSimState()).thenReturn(TelephonyManager.SIM_STATE_ABSENT);
        when(mCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(false);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_noSim_noMobileNetwork_unsupported_zoneWrite() {
        when(mTelephonyManager.getSimState()).thenReturn(TelephonyManager.SIM_STATE_ABSENT);
        when(mCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_noSim_noMobileNetwork_unsupported_zoneRead() {
        when(mTelephonyManager.getSimState()).thenReturn(TelephonyManager.SIM_STATE_ABSENT);
        when(mCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_noSim_noMobileNetwork_unsupported_zoneHidden() {
        when(mTelephonyManager.getSimState()).thenReturn(TelephonyManager.SIM_STATE_ABSENT);
        when(mCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_notAdmin_disabledForUser() {
        when(mUserManager.isAdminUser()).thenReturn(false);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_notAdmin_disabledForUser_zoneWrite() {
        when(mUserManager.isAdminUser()).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_notAdmin_disabledForUser_zoneRead() {
        when(mUserManager.isAdminUser()).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_notAdmin_disabledForUser_zoneHidden() {
        when(mUserManager.isAdminUser()).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_hasRestriction_disabledForUser() {
        when(mUserManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS))
                .thenReturn(true);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_hasRestriction_disabledForUser_zoneWrite() {
        when(mUserManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS))
                .thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_hasRestriction_disabledForUser_zoneRead() {
        when(mUserManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS))
                .thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_hasRestriction_disabledForUser_zoneHidden() {
        when(mUserManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS))
                .thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_hasSim_hasMobileNetwork_isAdmin_noRestriction_available() {
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_noSim_hasMobileNetwork_isAdmin_noRestriction_available() {
        when(mTelephonyManager.getSimState()).thenReturn(TelephonyManager.SIM_STATE_ABSENT);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_hasSim_noMobileNetwork_isAdmin_noRestriction_available() {
        when(mCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(false);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_hasSim_hasMobileNetwork_noRestriction_available_zoneWrite() {
        mPreferenceController.setAvailabilityStatusForZone("write");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_hasSim_hasMobileNetwork_noRestriction_available_zoneRead() {
        mPreferenceController.setAvailabilityStatusForZone("read");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_hasSim_hasMobileNetwork_noRestriction_available_zoneHidden() {
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void onStart_singleSim_registersObserver() {
        mPreferenceController.onStart(mLifecycleOwner);

        Uri uri = Settings.Global.getUriFor(Settings.Global.MOBILE_DATA);
        verify(mMockContentResolver).registerContentObserver(eq(uri), eq(false), any());
    }

    @Test
    public void onStart_multiSim_registersObserver() {
        when(mTelephonyManager.getSimCount()).thenReturn(2);
        mPreferenceController.onStart(mLifecycleOwner);

        Uri uri = Settings.Global.getUriFor(Settings.Global.MOBILE_DATA + SUB_ID);
        verify(mMockContentResolver).registerContentObserver(eq(uri), eq(false), any());
    }

    @Test
    public void onStop_singleSim_unregistersObserver() {
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.onStop(mLifecycleOwner);

        verify(mMockContentResolver).unregisterContentObserver(any());
    }

    @Test
    public void onCreate_noSubscriptions_disabled() {
        mPreferenceController.onCreate(mLifecycleOwner);

        verify(mMockPreference).setEnabled(false);
    }

    @Test
    public void onCreate_oneSim_enabled() {
        SubscriptionInfo info = createSubscriptionInfo(/* subId= */ 1,
                /* simSlotIndex= */ 1, TEST_NETWORK_NAME);
        List<SubscriptionInfo> selectable = Lists.newArrayList(info);
        when(mSubscriptionManager.getSelectableSubscriptionInfoList()).thenReturn(selectable);

        mPreferenceController.onCreate(mLifecycleOwner);

        verify(mMockPreference, times(2)).setEnabled(true);
    }

    @Test
    public void onCreate_dataEnabled_oneSim_summaryIsDisplayName() {
        SubscriptionInfo info = createSubscriptionInfo(/* subId= */ 1,
                /* simSlotIndex= */ 1, TEST_NETWORK_NAME);
        List<SubscriptionInfo> selectable = Lists.newArrayList(info);
        when(mSubscriptionManager.getSelectableSubscriptionInfoList()).thenReturn(selectable);

        mPreferenceController.onCreate(mLifecycleOwner);

        verify(mMockPreference).setSummary(TEST_NETWORK_NAME);
    }

    @Test
    public void onCreate_dataDisabled_summaryIsOff() {
        SubscriptionInfo info = createSubscriptionInfo(/* subId= */ 1,
                /* simSlotIndex= */ 1, TEST_NETWORK_NAME);
        List<SubscriptionInfo> selectable = Lists.newArrayList(info);
        when(mSubscriptionManager.getSelectableSubscriptionInfoList()).thenReturn(selectable);
        when(mTelephonyManager.isDataEnabled()).thenReturn(false);

        mPreferenceController.onCreate(mLifecycleOwner);

        verify(mMockPreference).setSummary(mContext.getString(R.string.mobile_network_state_off));
    }

    @Test
    public void onCreate_multiSim_enabled() {
        SubscriptionInfo info1 = createSubscriptionInfo(/* subId= */ 1,
                /* simSlotIndex= */ 1, TEST_NETWORK_NAME);
        SubscriptionInfo info2 = createSubscriptionInfo(/* subId= */ 2,
                /* simSlotIndex= */ 2, TEST_NETWORK_NAME);
        List<SubscriptionInfo> selectable = Lists.newArrayList(info1, info2);
        when(mSubscriptionManager.getSelectableSubscriptionInfoList()).thenReturn(selectable);

        mPreferenceController.onCreate(mLifecycleOwner);

        verify(mMockPreference, times(2)).setEnabled(true);
    }

    @Test
    public void onCreate_multiSim_summaryShowsCount() {
        SubscriptionInfo info1 = createSubscriptionInfo(/* subId= */ 1,
                /* simSlotIndex= */ 1, TEST_NETWORK_NAME);
        SubscriptionInfo info2 = createSubscriptionInfo(/* subId= */ 2,
                /* simSlotIndex= */ 2, TEST_NETWORK_NAME);
        List<SubscriptionInfo> selectable = Lists.newArrayList(info1, info2);
        when(mSubscriptionManager.getSelectableSubscriptionInfoList()).thenReturn(selectable);

        mPreferenceController.onCreate(mLifecycleOwner);

        verify(mMockPreference).setSummary(StringUtil.getIcuPluralsString(mContext, 2,
                R.string.mobile_network_summary_count));
    }

    @Test
    @UiThreadTest
    public void performClick_noSim_noFragmentStarted() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.handlePreferenceClicked(mMockPreference);

        verify(mFragmentController, never()).launchFragment(
                any(Fragment.class));
    }

    @Test
    @UiThreadTest
    public void performClick_oneSim_startsMobileNetworkFragment() {
        int subId = 1;
        SubscriptionInfo info = createSubscriptionInfo(subId, /* simSlotIndex= */ 1,
                TEST_NETWORK_NAME);
        List<SubscriptionInfo> selectable = Lists.newArrayList(info);
        when(mSubscriptionManager.getSelectableSubscriptionInfoList()).thenReturn(selectable);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.handlePreferenceClicked(mMockPreference);

        ArgumentCaptor<MobileNetworkFragment> captor = ArgumentCaptor.forClass(
                MobileNetworkFragment.class);
        verify(mFragmentController).launchFragment(captor.capture());

        assertThat(captor.getValue().getArguments().getInt(MobileNetworkFragment.ARG_NETWORK_SUB_ID,
                -1)).isEqualTo(subId);
    }

    @Test
    @UiThreadTest
    public void performClick_multiSim_startsMobileNetworkListFragment() {
        SubscriptionInfo info1 = createSubscriptionInfo(/* subId= */ 1,
                /* simSlotIndex= */ 1, TEST_NETWORK_NAME);
        SubscriptionInfo info2 = createSubscriptionInfo(/* subId= */ 2,
                /* simSlotIndex= */ 2, TEST_NETWORK_NAME);
        List<SubscriptionInfo> selectable = Lists.newArrayList(info1, info2);
        when(mSubscriptionManager.getSelectableSubscriptionInfoList()).thenReturn(selectable);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.handlePreferenceClicked(mMockPreference);

        verify(mFragmentController).launchFragment(
                any(MobileNetworkListFragment.class));
    }

    @Test
    public void performToggle_disabled_setsDataEnabled() {
        SubscriptionInfo info = createSubscriptionInfo(/* subId= */ 1,
                /* simSlotIndex= */ 1, TEST_NETWORK_NAME);
        List<SubscriptionInfo> selectable = Lists.newArrayList(info);
        when(mSubscriptionManager.getSelectableSubscriptionInfoList()).thenReturn(selectable);

        when(mTelephonyManager.isDataEnabled()).thenReturn(false);
        mPreferenceController.onCreate(mLifecycleOwner);

        verify(mMockPreference).setOnSecondaryActionClickListener(any());
        verify(mMockPreference).setSecondaryActionChecked(false);
        mPreferenceController.onSecondaryActionClick(true);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mTelephonyManager).setDataEnabled(true);
    }

    @Test
    public void performToggle_enabled_setsDataDisabled() {
        SubscriptionInfo info = createSubscriptionInfo(/* subId= */ 1,
                /* simSlotIndex= */ 1, TEST_NETWORK_NAME);
        List<SubscriptionInfo> selectable = Lists.newArrayList(info);
        when(mSubscriptionManager.getSelectableSubscriptionInfoList()).thenReturn(selectable);

        when(mTelephonyManager.isDataEnabled()).thenReturn(true);
        mPreferenceController.onCreate(mLifecycleOwner);

        verify(mMockPreference).setOnSecondaryActionClickListener(any());
        verify(mMockPreference).setSecondaryActionChecked(true);
        mPreferenceController.onSecondaryActionClick(false);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mTelephonyManager).setDataEnabled(false);
    }

    private SubscriptionInfo createSubscriptionInfo(int subId, int simSlotIndex,
            String displayName) {
        SubscriptionInfo subInfo = new SubscriptionInfo(subId, /* iccId= */ "",
                simSlotIndex, displayName, /* carrierName= */ "",
                /* nameSource= */ 0, /* iconTint= */ 0, /* number= */ "",
                /* roaming= */ 0, /* icon= */ null, /* mcc= */ "", "mncString",
                /* countryIso= */ "", /* isEmbedded= */ false,
                /* accessRules= */ null, /* cardString= */ "");
        return subInfo;
    }
}
