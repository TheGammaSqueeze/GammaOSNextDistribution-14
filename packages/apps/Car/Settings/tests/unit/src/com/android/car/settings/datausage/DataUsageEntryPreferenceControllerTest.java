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

package com.android.car.settings.datausage;

import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionPlan;
import android.util.Range;
import android.util.RecurrenceRule;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiPreference;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.android.collect.Lists;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.time.ZonedDateTime;
import java.util.Iterator;

@RunWith(AndroidJUnit4.class)
public class DataUsageEntryPreferenceControllerTest {

    private static final int SUBSCRIPTION_ID = 1;

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private Preference mPreference;
    private DataUsageEntryPreferenceController mPreferenceController;
    private MockitoSession mSession;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private NetworkCapabilities mMockNetworkCapabilities;
    @Mock
    private SubscriptionManager mMockSubscriptionManager;
    @Mock
    private SubscriptionPlan mMockSubscriptionPlan;
    @Mock
    private RecurrenceRule mMockRecurrenceRule;
    @Mock
    private ConnectivityManager mMockConnectivityManager;
    @Mock
    private Network mMockNetwork;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new DataUsageEntryPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions);

        mPreference = new CarUiPreference(mContext);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);

        mSession = ExtendedMockito.mockitoSession().mockStatic(
                SubscriptionManager.class, withSettings().lenient()).startMocking();

        // Setup to always make preference available.
        when(mContext.getSystemService(ConnectivityManager.class))
                .thenReturn(mMockConnectivityManager);
        Network[] networks = {mMockNetwork};
        when(mMockConnectivityManager.getAllNetworks()).thenReturn(networks);
        when(mMockConnectivityManager.getNetworkCapabilities(mMockNetwork))
                .thenReturn(mMockNetworkCapabilities);
        when(mMockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                .thenReturn(true);

        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void getAvailabilityStatus_noMobileNetwork_isUnsupported() {
        when(mMockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                .thenReturn(false);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_noMobileNetwork_isUnsupported_zoneWrite() {
        when(mMockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                .thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_noMobileNetwork_isUnsupported_zoneRead() {
        when(mMockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                .thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_noMobileNetwork_isUnsupported_zoneHidden() {
        when(mMockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                .thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_hasMobileNetwork_isAvailable() {
        when(mMockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                .thenReturn(true);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_hasMobileNetwork_isAvailable_zoneWrite() {
        when(mMockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                .thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_hasMobileNetwork_isAvailable_zoneRead() {
        when(mMockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                .thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_hasMobileNetwork_isAvailable_zoneHidden() {
        when(mMockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                .thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void refreshUi_noDefaultSubscriptionId_noSummarySet() {
        when(mContext.getSystemService(SubscriptionManager.class))
                .thenReturn(mMockSubscriptionManager);
        ExtendedMockito.when(SubscriptionManager.getDefaultSubscriptionId())
                .thenReturn(INVALID_SUBSCRIPTION_ID);

        mPreferenceController.refreshUi();
        assertThat(mPreference.getSummary()).isNull();
    }

    @Test
    public void refreshUi_noPrimaryPlan_noSummarySet() {
        when(mContext.getSystemService(SubscriptionManager.class))
                .thenReturn(mMockSubscriptionManager);
        ExtendedMockito.when(SubscriptionManager.getDefaultSubscriptionId())
                .thenReturn(SUBSCRIPTION_ID);
        when(mMockSubscriptionManager.getSubscriptionPlans(SUBSCRIPTION_ID))
                .thenReturn(Lists.newArrayList());

        mPreferenceController.refreshUi();
        assertThat(mPreference.getSummary()).isNull();
    }

    @Test
    public void refreshUi_hasPrimaryPlan_setsSummary() {
        when(mContext.getSystemService(SubscriptionManager.class))
                .thenReturn(mMockSubscriptionManager);
        ExtendedMockito.when(SubscriptionManager.getDefaultSubscriptionId())
                .thenReturn(SUBSCRIPTION_ID);
        when(mMockSubscriptionManager.getSubscriptionPlans(SUBSCRIPTION_ID))
                .thenReturn(Lists.newArrayList(mMockSubscriptionPlan));
        when(mMockSubscriptionPlan.getDataLimitBytes()).thenReturn(100L);
        when(mMockSubscriptionPlan.getDataUsageBytes()).thenReturn(10L);
        when(mMockSubscriptionPlan.getCycleRule()).thenReturn(mMockRecurrenceRule);

        // Provide arbitrary cycle start time so summary is displayed
        Iterator<Range<ZonedDateTime>> cycle = new Iterator<Range<ZonedDateTime>>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Range<ZonedDateTime> next() {
                return new Range<>(ZonedDateTime.now(), ZonedDateTime.now());
            }
        };
        when(mMockSubscriptionPlan.cycleIterator()).thenReturn(cycle);

        mPreferenceController.refreshUi();
        assertThat(mPreference.getSummary().length()).isGreaterThan(0);
    }
}
