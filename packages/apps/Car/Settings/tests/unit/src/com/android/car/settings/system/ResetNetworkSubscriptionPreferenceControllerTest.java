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

package com.android.car.settings.system;

import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;
import static android.telephony.SubscriptionManager.MIN_SUBSCRIPTION_ID_VALUE;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.ListPreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Unit test for {@link ResetNetworkSubscriptionPreferenceController}. */
@RunWith(AndroidJUnit4.class)
public class ResetNetworkSubscriptionPreferenceControllerTest {
    private static final int SUBID_1 = MIN_SUBSCRIPTION_ID_VALUE;
    private static final int SUBID_2 = SUBID_1 + 1;
    private static final int SUBID_3 = SUBID_2 + 1;
    private static final int SUBID_4 = SUBID_3 + 1;

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private ListPreference mPreference;
    private ResetNetworkSubscriptionPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private PackageManager mMockPm;
    @Mock
    private SubscriptionManager mMockSubscriptionManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        when(mContext.getPackageManager()).thenReturn(mMockPm);
        // Default to AVAILABLE status. Tests for this behavior will do their own setup.
        when(mMockPm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)).thenReturn(true);
        when(mContext.getSystemService(SubscriptionManager.class))
                .thenReturn(mMockSubscriptionManager);
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList()).thenReturn(null);
        mSession = ExtendedMockito.mockitoSession().mockStatic(
                SubscriptionManager.class, withSettings().lenient()).startMocking();
        // Default to having no defaults. Tests for this behavior will do their own setup.
        when(SubscriptionManager.isUsableSubIdValue(anyInt())).thenCallRealMethod();
        when(SubscriptionManager.getDefaultSubscriptionId()).thenReturn(INVALID_SUBSCRIPTION_ID);
        when(SubscriptionManager.getDefaultSmsSubscriptionId()).thenReturn(INVALID_SUBSCRIPTION_ID);
        when(SubscriptionManager.getDefaultVoiceSubscriptionId()).thenReturn(
                INVALID_SUBSCRIPTION_ID);
        when(SubscriptionManager.getDefaultDataSubscriptionId()).thenReturn(
                INVALID_SUBSCRIPTION_ID);

        mPreference = new ListPreference(mContext);
        mPreferenceController = new ResetNetworkSubscriptionPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void getAvailabilityStatus_telephonyAvailable_available() {
        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_telephonyAvailable_available_zoneWrite() {
        mPreferenceController.setAvailabilityStatusForZone("write");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_telephonyAvailable_available_zoneRead() {
        mPreferenceController.setAvailabilityStatusForZone("read");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_telephonyAvailable_available_zoneHidden() {
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_telephonyNotAvailable_unsupportedOnDevice() {
        when(mMockPm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)).thenReturn(false);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_telephonyNotAvailable_unsupportedOnDevice_zoneWrite() {
        when(mMockPm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_telephonyNotAvailable_unsupportedOnDevice_zoneRead() {
        when(mMockPm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_telephonyNotAvailable_unsupportedOnDevice_zoneHidden() {
        when(mMockPm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)).thenReturn(false);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void refreshUi_nullSubscriptions_hidesPreference() {
        mPreferenceController.refreshUi();

        assertThat(mPreference.isVisible()).isFalse();
    }

    @Test
    public void refreshUi_nullSubscriptions_setsValue() {
        mPreferenceController.refreshUi();

        assertThat(mPreference.getValue()).isEqualTo(
                String.valueOf(INVALID_SUBSCRIPTION_ID));
    }

    @Test
    public void refreshUi_noSubscriptions_hidesPreference() {
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(Collections.emptyList());

        mPreferenceController.refreshUi();

        assertThat(mPreference.isVisible()).isFalse();
    }

    @Test
    public void refreshUi_noSubscriptions_setsValue() {
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(Collections.emptyList());

        mPreferenceController.refreshUi();

        assertThat(mPreference.getValue()).isEqualTo(
                String.valueOf(INVALID_SUBSCRIPTION_ID));
    }

    @Test
    public void refreshUi_oneSubscription_hidesPreference() {
        List<SubscriptionInfo> subscriptionInfoList = Collections.singletonList(
                createSubInfo(SUBID_1, "sub1"));
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(subscriptionInfoList);

        mPreferenceController.refreshUi();

        assertThat(mPreference.isVisible()).isFalse();
    }

    @Test
    public void refreshUi_oneSubscription_setsValue() {
        List<SubscriptionInfo> subscriptionInfoList = Collections.singletonList(
                createSubInfo(SUBID_1, "sub1"));
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(subscriptionInfoList);

        mPreferenceController.refreshUi();

        assertThat(mPreference.getValue()).isEqualTo(String.valueOf(SUBID_1));
    }

    @Test
    public void refreshUi_multipleSubscriptions_showsPreference() {
        List<SubscriptionInfo> subscriptionInfoList = Arrays.asList(createSubInfo(SUBID_1, "sub1"),
                createSubInfo(SUBID_2, "sub2"));
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(subscriptionInfoList);

        mPreferenceController.refreshUi();

        assertThat(mPreference.isVisible()).isTrue();
    }

    @Test
    public void refreshUi_multipleSubscriptions_populatesEntries() {
        String displayName1 = "sub1";
        String displayName2 = "sub2";
        List<SubscriptionInfo> subscriptionInfoList = Arrays.asList(
                createSubInfo(SUBID_1, displayName1), createSubInfo(SUBID_2, displayName2));
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(subscriptionInfoList);

        mPreferenceController.refreshUi();

        assertThat(Arrays.asList(mPreference.getEntries())).containsExactly(displayName1,
                displayName2);
        assertThat(Arrays.asList(mPreference.getEntryValues())).containsExactly(
                String.valueOf(SUBID_1),
                String.valueOf(SUBID_2));
    }

    @Test
    public void refreshUi_defaultSelection_fourthPriority_system() {
        List<SubscriptionInfo> subscriptionInfoList = Arrays.asList(
                createSubInfo(SUBID_1, "sub1"), createSubInfo(SUBID_2, "sub2"),
                createSubInfo(SUBID_3, "sub3"), createSubInfo(SUBID_4, "sub4"));
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(subscriptionInfoList);

        setDefaultSubscriptionId(SUBID_4);
        mPreferenceController.refreshUi();

        assertThat(mPreference.getValue()).isEqualTo(String.valueOf(SUBID_4));
    }

    @Test
    public void refreshUi_defaultSelection_thirdPriority_sms() {
        List<SubscriptionInfo> subscriptionInfoList = Arrays.asList(createSubInfo(SUBID_1, "sub1"),
                createSubInfo(SUBID_2, "sub2"), createSubInfo(SUBID_3, "sub3"),
                createSubInfo(SUBID_4, "sub4"));
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(subscriptionInfoList);

        setDefaultSubscriptionId(SUBID_4);
        setDefaultSmsSubscriptionId(SUBID_3);
        mPreferenceController.refreshUi();

        assertThat(mPreference.getValue()).isEqualTo(String.valueOf(SUBID_3));
    }

    @Test
    public void refreshUi_defaultSelection_secondPriority_voice() {
        List<SubscriptionInfo> subscriptionInfoList = Arrays.asList(createSubInfo(SUBID_1, "sub1"),
                createSubInfo(SUBID_2, "sub2"), createSubInfo(SUBID_3, "sub3"),
                createSubInfo(SUBID_4, "sub4"));
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(subscriptionInfoList);

        setDefaultSubscriptionId(SUBID_4);
        setDefaultSmsSubscriptionId(SUBID_3);
        setDefaultVoiceSubscriptionId(SUBID_2);
        mPreferenceController.refreshUi();

        assertThat(mPreference.getValue()).isEqualTo(String.valueOf(SUBID_2));
    }

    @Test
    public void refreshUi_defaultSelection_firstPriority_data() {
        List<SubscriptionInfo> subscriptionInfoList = Arrays.asList(createSubInfo(SUBID_1, "sub1"),
                createSubInfo(SUBID_2, "sub2"), createSubInfo(SUBID_3, "sub3"),
                createSubInfo(SUBID_4, "sub4"));
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(subscriptionInfoList);

        setDefaultSubscriptionId(SUBID_4);
        setDefaultSmsSubscriptionId(SUBID_3);
        setDefaultVoiceSubscriptionId(SUBID_2);
        setDefaultDataSubscriptionId(SUBID_1);
        mPreferenceController.refreshUi();

        assertThat(mPreference.getValue()).isEqualTo(String.valueOf(SUBID_1));
    }

    @Test
    public void refreshUi_title_fourthPriority_subscriptionNetworkIds() {
        SubscriptionInfo subInfo = createSubInfo(
                SUBID_1,
                /* displayName= */ "",
                /* carrierName= */ "",
                /* number= */ "");
        // Multiple subscriptions so that preference is shown / title is set.
        List<SubscriptionInfo> subscriptionInfoList = Arrays.asList(subInfo,
                createSubInfo(SUBID_2, "sub2"));
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(subscriptionInfoList);
        setDefaultDataSubscriptionId(SUBID_1);

        mPreferenceController.refreshUi();

        String title = mPreference.getTitle().toString();
        assertThat(title).contains(String.valueOf(subInfo.getMcc()));
        assertThat(title).contains(String.valueOf(subInfo.getMnc()));
        assertThat(title).contains(String.valueOf(subInfo.getSimSlotIndex()));
        assertThat(title).contains(String.valueOf(subInfo.getSubscriptionId()));
    }

    @Test
    public void refreshUi_title_thirdPriority_subscriptionCarrierName() {
        SubscriptionInfo subInfo = createSubInfo(
                SUBID_1,
                /* displayName= */ "",
                "carrierName",
                /* number= */ "");
        // Multiple subscriptions so that preference is shown / title is set.
        // Multiple subscriptions so that preference is shown / title is set.
        List<SubscriptionInfo> subscriptionInfoList = Arrays.asList(subInfo,
                createSubInfo(SUBID_2, "sub2"));
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(subscriptionInfoList);
        setDefaultDataSubscriptionId(SUBID_1);

        mPreferenceController.refreshUi();

        assertThat(mPreference.getTitle()).isEqualTo(subInfo.getCarrierName());
    }

    @Test
    public void refreshUi_title_secondPriority_subscriptionNumber() {
        SubscriptionInfo subInfo = createSubInfo(
                SUBID_1,
                /* displayName= */ "",
                "carrierName",
                "number");
        // Multiple subscriptions so that preference is shown / title is set.
        List<SubscriptionInfo> subscriptionInfoList = Arrays.asList(subInfo,
                createSubInfo(SUBID_2, "sub2"));
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(subscriptionInfoList);
        setDefaultDataSubscriptionId(SUBID_1);

        mPreferenceController.refreshUi();

        assertThat(mPreference.getTitle()).isEqualTo(subInfo.getNumber());
    }

    @Test
    public void refreshUi_title_firstPriority_subscriptionDisplayName() {
        SubscriptionInfo subInfo = createSubInfo(
                SUBID_1,
                "displayName",
                "carrierName",
                "number");
        // Multiple subscriptions so that preference is shown / title is set.
        List<SubscriptionInfo> subscriptionInfoList = Arrays.asList(subInfo,
                createSubInfo(SUBID_2, "sub2"));
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(subscriptionInfoList);
        setDefaultDataSubscriptionId(SUBID_1);

        mPreferenceController.refreshUi();

        assertThat(mPreference.getTitle()).isEqualTo(subInfo.getDisplayName());
    }

    @Test
    public void handlePreferenceChanged_updatesTitle() {
        List<SubscriptionInfo> subscriptionInfoList = Arrays.asList(createSubInfo(SUBID_1, "sub1"),
                createSubInfo(SUBID_2, "sub2"));
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(subscriptionInfoList);
        setDefaultDataSubscriptionId(SUBID_1);
        mPreferenceController.refreshUi();

        mPreference.callChangeListener(String.valueOf(SUBID_2));

        assertThat(mPreference.getTitle()).isEqualTo("sub2");
    }

    @Test
    public void handlePreferenceChanged_returnsTrue() {
        List<SubscriptionInfo> subscriptionInfoList = Arrays.asList(createSubInfo(SUBID_1, "sub1"),
                createSubInfo(SUBID_2, "sub2"));
        when(mMockSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(subscriptionInfoList);
        setDefaultDataSubscriptionId(SUBID_1);
        mPreferenceController.refreshUi();

        assertThat(mPreferenceController.handlePreferenceChanged(mPreference,
                String.valueOf(SUBID_2))).isTrue();
    }

    /** Reduce SubscriptionInfo constructor args to the ones we care about here. */
    private SubscriptionInfo createSubInfo(int subId, String displayName) {
        return createSubInfo(subId, displayName, "carrierName", "number");
    }

    /** Reduce SubscriptionInfo constructor args to the ones we care about here. */
    private SubscriptionInfo createSubInfo(int subId, String displayName, String carrierName,
            String number) {
        // Hidden constructor so resort to mocking.
        SubscriptionInfo subscriptionInfo = mock(SubscriptionInfo.class);
        when(subscriptionInfo.getSubscriptionId()).thenReturn(subId);
        when(subscriptionInfo.getDisplayName()).thenReturn(displayName);
        when(subscriptionInfo.getCarrierName()).thenReturn(carrierName);
        when(subscriptionInfo.getNumber()).thenReturn(number);
        when(subscriptionInfo.getSimSlotIndex()).thenReturn(111);
        when(subscriptionInfo.getMcc()).thenReturn(222);
        when(subscriptionInfo.getMnc()).thenReturn(333);
        return subscriptionInfo;
    }

    private void setDefaultSubscriptionId(int id) {
        when(SubscriptionManager.getDefaultSubscriptionId()).thenReturn(id);
    }

    private void setDefaultSmsSubscriptionId(int id) {
        when(SubscriptionManager.getDefaultSmsSubscriptionId()).thenReturn(id);
    }

    private void setDefaultVoiceSubscriptionId(int id) {
        when(SubscriptionManager.getDefaultVoiceSubscriptionId()).thenReturn(id);
    }

    private void setDefaultDataSubscriptionId(int id) {
        when(SubscriptionManager.getDefaultDataSubscriptionId()).thenReturn(id);
    }
}
