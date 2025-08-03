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

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionPlan;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.Formatter;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.settingslib.net.DataUsageController;

import com.google.android.collect.Lists;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.time.Period;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class DataUsageSummaryPreferenceControllerTest {

    private static final CharSequence TEST_CARRIER_NAME = "TEST_CARRIER_NAME";
    private static final int SUB_ID = 1;

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private DataUsageSummaryPreference mDataUsageSummaryPreference;
    private DataUsageSummaryPreferenceController mPreferenceController;
    private MockitoSession mSession;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private DataUsageController mMockDataUsageController;
    @Mock
    private SubscriptionManager mMockSubscriptionManager;
    @Mock
    private TelephonyManager mMockTelephonyManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mSession = ExtendedMockito.mockitoSession()
                .spyStatic(TelephonyManager.class)
                .startMocking();

        ExtendedMockito.when(TelephonyManager.from(mContext)).thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.createForSubscriptionId(SUB_ID))
                .thenReturn(mMockTelephonyManager);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new DataUsageSummaryPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions, mMockSubscriptionManager, mMockDataUsageController);
        mDataUsageSummaryPreference = new DataUsageSummaryPreference(mContext);
        mPreferenceController.setFields(SUB_ID);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController,
                mDataUsageSummaryPreference);

        SubscriptionInfo info = mock(SubscriptionInfo.class);
        when(mMockSubscriptionManager.getActiveSubscriptionInfo(SUB_ID)).thenReturn(info);

        when(mMockTelephonyManager.getSimState()).thenReturn(TelephonyManager.SIM_STATE_LOADED);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void getAvailabilityStatus_hasSim_isAvailable() {
        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_hasSim_isAvailable_zoneWrite() {
        mPreferenceController.setAvailabilityStatusForZone("write");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_hasSim_isAvailable_zoneRead() {
        mPreferenceController.setAvailabilityStatusForZone("read");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_hasSim_isAvailable_zoneHidden() {
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_noSim_isConditionallyUnavailable() {
        when(mMockTelephonyManager.getSimState()).thenReturn(TelephonyManager.SIM_STATE_UNKNOWN);
        assertThat(mPreferenceController.getAvailabilityStatus())
                .isEqualTo(CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_noSim_isConditionallyUnavailable_zoneWrite() {
        when(mMockTelephonyManager.getSimState()).thenReturn(TelephonyManager.SIM_STATE_UNKNOWN);
        mPreferenceController.setAvailabilityStatusForZone("write");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_noSim_isConditionallyUnavailable_zoneRead() {
        when(mMockTelephonyManager.getSimState()).thenReturn(TelephonyManager.SIM_STATE_UNKNOWN);
        mPreferenceController.setAvailabilityStatusForZone("read");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_noSim_isConditionallyUnavailable_zoneHidden() {
        when(mMockTelephonyManager.getSimState()).thenReturn(TelephonyManager.SIM_STATE_UNKNOWN);
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void refreshUi_hasUsage_titleSet() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.usageLevel = 10000;
        when(mMockDataUsageController.getDataUsageInfo(any())).thenReturn(info);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();

        String usedValueString = Formatter.formatBytes(mContext.getResources(), info.usageLevel,
                Formatter.FLAG_CALCULATE_ROUNDED | Formatter.FLAG_IEC_UNITS).value;
        assertThat(mDataUsageSummaryPreference.getTitle().toString()).contains(usedValueString);
    }

    @Test
    public void refreshUi_hasCarrierName_hasRecentUpdate_setsCarrierInfoText() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.usageLevel = 10000;
        when(mMockDataUsageController.getDataUsageInfo(any())).thenReturn(info);

        setCarrierName(TEST_CARRIER_NAME);
        setSubscriptionPlan(/* usageBytes= */ 1000L, System.currentTimeMillis());

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();

        assertThat(mDataUsageSummaryPreference.getCarrierInfoText()).isEqualTo(
                TextUtils.expandTemplate(mContext.getText(R.string.carrier_and_update_now_text),
                        TEST_CARRIER_NAME));
    }

    @Test
    public void refreshUi_hasCarrierName_hasOldUpdate_setsCarrierInfoText() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.usageLevel = 10000;
        when(mMockDataUsageController.getDataUsageInfo(any())).thenReturn(info);

        int numDays = 15;
        setCarrierName(TEST_CARRIER_NAME);
        setSubscriptionPlan(/* usageBytes= */ 1000L,
                System.currentTimeMillis() - TimeUnit.DAYS.toMillis(numDays));

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();

        assertThat(mDataUsageSummaryPreference.getCarrierInfoText()).isEqualTo(
                TextUtils.expandTemplate(mContext.getText(R.string.carrier_and_update_text),
                        TEST_CARRIER_NAME, numDays + " days"));
    }

    @Test
    public void refreshUi_noCarrierName_hasRecentUpdate_setsCarrierInfoText() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.usageLevel = 10000;
        when(mMockDataUsageController.getDataUsageInfo(any())).thenReturn(info);

        setSubscriptionPlan(/* usageBytes= */ 1000L, System.currentTimeMillis());

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();

        assertThat(mDataUsageSummaryPreference.getCarrierInfoText().toString()).isEqualTo(
                mContext.getString(R.string.no_carrier_update_now_text));
    }

    @Test
    public void refreshUi_noCarrierName_hasOldUpdate_setsCarrierInfoText() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.usageLevel = 10000;
        when(mMockDataUsageController.getDataUsageInfo(any())).thenReturn(info);

        int numDays = 15;
        setSubscriptionPlan(/* usageBytes= */ 1000L,
                System.currentTimeMillis() - TimeUnit.DAYS.toMillis(numDays));

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();

        assertThat(mDataUsageSummaryPreference.getCarrierInfoText()).isEqualTo(
                TextUtils.expandTemplate(mContext.getText(R.string.no_carrier_update_text),
                        null, numDays + " days"));
    }

    @Test
    public void refreshUi_hasUpdateTimeOlderThanWarning_setsCarrierInfoStyle() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.usageLevel = 10000;
        when(mMockDataUsageController.getDataUsageInfo(any())).thenReturn(info);


        // Subtract an extra hour to account fo the difference in calls to
        // System.currentTimeMillis().
        setSubscriptionPlan(/* usageBytes= */ 1000L,
                System.currentTimeMillis() - DataUsageSummaryPreferenceController.WARNING_AGE
                        - TimeUnit.HOURS.toMillis(1));

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();

        assertThat(mDataUsageSummaryPreference.getCarrierInfoTextStyle()).isEqualTo(
                R.style.DataUsageSummaryCarrierInfoWarningTextAppearance);
    }

    @Test
    public void refreshUi_hasUpdateTimeYoungerThanWarning_setsCarrierInfoStyle() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.usageLevel = 10000;
        when(mMockDataUsageController.getDataUsageInfo(any())).thenReturn(info);

        // Subtract an extra hour to account fo the difference in calls to
        // System.currentTimeMillis().
        setSubscriptionPlan(/* usageBytes= */ 1000L,
                System.currentTimeMillis() - DataUsageSummaryPreferenceController.WARNING_AGE
                        + TimeUnit.HOURS.toMillis(1));

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.refreshUi();

        assertThat(mDataUsageSummaryPreference.getCarrierInfoTextStyle()).isEqualTo(
                R.style.DataUsageSummaryCarrierInfoTextAppearance);
    }

    private void setCarrierName(CharSequence name) {
        SubscriptionInfo subInfo = new SubscriptionInfo(/* id= */ 0, /* iccId= */ "",
                /* simSlotIndex= */ 0, /* displayName= */ "", name,
                /* nameSource= */ 0, /* iconTint= */ 0, /* number= */ "",
                /* roaming= */ 0, /* icon= */ null, /* mcc= */ "", /* mnc= */ "",
                /* countryIso= */ "", /* isEmbedded= */ false,
                /* accessRules= */ null, /* cardString= */ "");
        when(mMockSubscriptionManager.getActiveSubscriptionInfo(SUB_ID)).thenReturn(subInfo);
    }

    private void setSubscriptionPlan(long usageBytes, long snapshotMillis) {
        ZonedDateTime start = ZonedDateTime.now();
        ZonedDateTime end = ZonedDateTime.now().plusDays(30);
        SubscriptionPlan plan = new SubscriptionPlan.Builder(start, end, Period.ofMonths(1))
                .setDataLimit(/* dataLimitBytes= */ 5000000000L,
                        SubscriptionPlan.LIMIT_BEHAVIOR_DISABLED)
                .setDataUsage(usageBytes, snapshotMillis)
                .build();
        when(mMockSubscriptionManager.getSubscriptionPlans(anyInt()))
                .thenReturn(Lists.newArrayList(plan));
    }
}
