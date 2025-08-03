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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.app.AlertDialog;
import android.car.drivingstate.CarUxRestrictions;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

@RunWith(AndroidJUnit4.class)
public class MobileDataTogglePreferenceControllerTest {

    private static final int SUB_ID = 1;

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private TwoStatePreference mPreference;
    private MobileDataTogglePreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private TelephonyManager mMockTelephonyManager;
    @Mock
    private ContentResolver mMockContentResolver;
    @Mock
    private SubscriptionManager mMockSubscriptionManager;
    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(SubscriptionManager.class, withSettings().lenient())
                .startMocking();

        when(mContext.getSystemService(eq(Context.TELEPHONY_SERVICE)))
                .thenReturn(mMockTelephonyManager);
        when(mContext.getSystemService(TelephonyManager.class)).thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.createForSubscriptionId(SUB_ID))
                .thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.getSimCount()).thenReturn(1);
        when(mMockTelephonyManager.isDataEnabled()).thenReturn(false);
        when(mContext.getContentResolver()).thenReturn(mMockContentResolver);
        ExtendedMockito.when(SubscriptionManager.getDefaultDataSubscriptionId())
                .thenReturn(SUB_ID);
        when(mMockSubscriptionManager.isActiveSubscriptionId(SUB_ID)).thenReturn(true);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreference = new SwitchPreference(mContext);
        mPreferenceController = new MobileDataTogglePreferenceController(mContext,
                "key", mMockFragmentController, mCarUxRestrictions,
                mMockSubscriptionManager);
        mPreferenceController.setFields(SUB_ID);
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
    public void onStart_singleSim_registersObserver() {
        mPreferenceController.onStart(mLifecycleOwner);

        Uri uri = Settings.Global.getUriFor(Settings.Global.MOBILE_DATA);
        verify(mMockContentResolver).registerContentObserver(eq(uri), eq(false), any());
    }

    @Test
    public void onStart_multiSim_registersObserver() {
        when(mMockTelephonyManager.getSimCount()).thenReturn(2);
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
    public void refreshUi_isOpportunistic_isDisabled() {
        when(mMockSubscriptionManager.getActiveSubscriptionInfo(SUB_ID))
                .thenReturn(createSubscriptionInfo(SUB_ID, /* isOpportunistic= */ true));
        mPreferenceController.refreshUi();

        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_isNotOpportunistic_isEnabled() {
        when(mMockSubscriptionManager.getActiveSubscriptionInfo(SUB_ID))
                .thenReturn(createSubscriptionInfo(SUB_ID, /* isOpportunistic= */ false));
        mPreferenceController.refreshUi();

        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void refreshUi_dataEnabled_setChecked() {
        when(mMockTelephonyManager.isDataEnabled()).thenReturn(true);
        mPreferenceController.refreshUi();

        assertThat(mPreference.isChecked()).isTrue();
    }

    @Test
    public void refreshUi_dataDisabled_setUnchecked() {
        mPreferenceController.refreshUi();

        assertThat(mPreference.isChecked()).isFalse();
    }

    @Test
    public void handlePreferenceChanged_setFalse_isSingleSim_opensDialog() {
        mPreference.callChangeListener(false);

        verify(mMockFragmentController).showDialog(any(ConfirmationDialogFragment.class),
                eq(MobileDataTogglePreferenceController.DISABLE_DIALOG_TAG));
    }

    @Test
    public void handlePreferenceChanged_setFalse_isMultiSim_disablesData() {
        mPreference.setChecked(true);
        when(mMockTelephonyManager.isDataEnabled()).thenReturn(true);
        when(mMockTelephonyManager.getSimCount()).thenReturn(2);
        mPreference.callChangeListener(false);

        verify(mMockTelephonyManager).setDataEnabled(false);
    }

    @Test
    public void handlePreferenceChanged_setFalse_isMultiSim_setsUnchecked() {
        mPreference.setChecked(true);
        when(mMockTelephonyManager.isDataEnabled()).thenReturn(true);
        when(mMockTelephonyManager.getSimCount()).thenReturn(2);
        mPreference.callChangeListener(false);

        verify(mMockTelephonyManager).setDataEnabled(false);
        when(mMockTelephonyManager.isDataEnabled()).thenReturn(false);
        mPreferenceController.updateState(mPreference);

        assertThat(mPreference.isChecked()).isFalse();
    }

    @Test
    public void handlePreferenceChanged_setTrue_isSingleSim_enablesData() {
        mPreference.setChecked(false);
        mPreference.callChangeListener(true);

        verify(mMockTelephonyManager).setDataEnabled(true);
    }

    @Test
    public void handlePreferenceChanged_setTrue_isSingleSim_setsChecked() {
        mPreference.setChecked(false);
        mPreference.callChangeListener(true);

        verify(mMockTelephonyManager).setDataEnabled(true);
        when(mMockTelephonyManager.isDataEnabled()).thenReturn(true);
        mPreferenceController.updateState(mPreference);

        assertThat(mPreference.isChecked()).isTrue();
    }

    @Test
    public void handlePreferenceChanged_setTrue_isMultiSim_noOtherSimActive_enablesData() {
        mPreference.setChecked(false);
        when(mMockTelephonyManager.getSimCount()).thenReturn(2);
        mPreference.callChangeListener(true);

        verify(mMockTelephonyManager).setDataEnabled(true);
    }

    @Test
    public void handlePreferenceChanged_setTrue_isMultiSim_noOtherSimActive_setsChecked() {
        mPreference.setChecked(false);
        when(mMockTelephonyManager.getSimCount()).thenReturn(2);
        mPreference.callChangeListener(true);

        verify(mMockTelephonyManager).setDataEnabled(true);
        when(mMockTelephonyManager.isDataEnabled()).thenReturn(true);
        mPreferenceController.updateState(mPreference);

        assertThat(mPreference.isChecked()).isTrue();
    }

    @Test
    public void handlePreferenceChanged_setTrue_isMultiSim_otherSimActive_opensDialog() {
        int otherSubId = SUB_ID + 1;
        mPreference.setChecked(false);
        when(mMockTelephonyManager.getSimCount()).thenReturn(2);
        ExtendedMockito.when(SubscriptionManager.getDefaultDataSubscriptionId())
                .thenReturn(otherSubId);
        when(mMockSubscriptionManager.isActiveSubscriptionId(otherSubId)).thenReturn(true);
        mPreference.callChangeListener(true);

        verify(mMockFragmentController).showDialog(any(ConfirmationDialogFragment.class),
                eq(MobileDataTogglePreferenceController.ENABLE_MULTISIM_DIALOG_TAG));
    }

    @Test
    public void disableSingleSimDialog_onConfirm_disablesData() throws Throwable {
        mPreference.setChecked(true);
        when(mMockTelephonyManager.isDataEnabled()).thenReturn(true);
        mPreference.callChangeListener(false);

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(dialogCaptor.capture(),
                eq(MobileDataTogglePreferenceController.DISABLE_DIALOG_TAG));

        ConfirmationDialogFragment dialog = dialogCaptor.getValue();
        assertThat(dialogCaptor).isNotNull();
        AlertDialog alertDialog = showDialog(dialog);
        mActivityTestRule.runOnUiThread(() -> {
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mMockTelephonyManager).setDataEnabled(false);
    }

    @Test
    public void disableSingleSimDialog_onConfirm_setsUnchecked() throws Throwable {
        mPreference.setChecked(true);
        when(mMockTelephonyManager.isDataEnabled()).thenReturn(true);
        mPreference.callChangeListener(false);

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(dialogCaptor.capture(),
                eq(MobileDataTogglePreferenceController.DISABLE_DIALOG_TAG));

        ConfirmationDialogFragment dialog = dialogCaptor.getValue();
        assertThat(dialogCaptor).isNotNull();
        AlertDialog alertDialog = showDialog(dialog);
        mActivityTestRule.runOnUiThread(() -> {
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mMockTelephonyManager).setDataEnabled(false);
        when(mMockTelephonyManager.isDataEnabled()).thenReturn(false);
        mPreferenceController.updateState(mPreference);

        assertThat(mPreference.isChecked()).isFalse();
    }

    @Test
    public void enableMutliSimDialog_onConfirm_enablesData() throws Throwable {
        int otherSubId = SUB_ID + 1;
        mPreference.setChecked(false);
        when(mMockTelephonyManager.getSimCount()).thenReturn(2);
        when(mMockTelephonyManager.createForSubscriptionId(otherSubId))
                .thenReturn(mMockTelephonyManager);
        ExtendedMockito.when(SubscriptionManager.getDefaultDataSubscriptionId())
                .thenReturn(otherSubId);
        when(mMockSubscriptionManager.isActiveSubscriptionId(otherSubId)).thenReturn(true);
        mPreference.callChangeListener(true);

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(dialogCaptor.capture(),
                eq(MobileDataTogglePreferenceController.ENABLE_MULTISIM_DIALOG_TAG));

        ConfirmationDialogFragment dialog = dialogCaptor.getValue();
        assertThat(dialogCaptor).isNotNull();
        AlertDialog alertDialog = showDialog(dialog);
        mActivityTestRule.runOnUiThread(() -> {
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mMockTelephonyManager).setDataEnabled(true);
    }

    @Test
    public void enableMutliSimDialog_onConfirm_setsChecked() throws Throwable {
        int otherSubId = SUB_ID + 1;
        mPreference.setChecked(false);
        when(mMockTelephonyManager.getSimCount()).thenReturn(2);
        when(mMockTelephonyManager.createForSubscriptionId(otherSubId))
                .thenReturn(mMockTelephonyManager);
        ExtendedMockito.when(SubscriptionManager.getDefaultDataSubscriptionId())
                .thenReturn(otherSubId);
        when(mMockSubscriptionManager.isActiveSubscriptionId(otherSubId)).thenReturn(true);
        mPreference.callChangeListener(true);

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(dialogCaptor.capture(),
                eq(MobileDataTogglePreferenceController.ENABLE_MULTISIM_DIALOG_TAG));

        ConfirmationDialogFragment dialog = dialogCaptor.getValue();
        assertThat(dialogCaptor).isNotNull();
        AlertDialog alertDialog = showDialog(dialog);
        mActivityTestRule.runOnUiThread(() -> {
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mMockTelephonyManager).setDataEnabled(true);
        when(mMockTelephonyManager.isDataEnabled()).thenReturn(true);
        mPreferenceController.updateState(mPreference);

        assertThat(mPreference.isChecked()).isTrue();
    }

    private SubscriptionInfo createSubscriptionInfo(int subId, boolean isOpportunistic) {
        SubscriptionInfo subInfo = new SubscriptionInfo(subId, /* iccId= */ "",
                /* simSlotIndex= */ 0, /* displayName= */ "", /* carrierName= */ "",
                /* nameSource= */ 0, /* iconTint= */ 0, /* number= */ "",
                /* roaming= */ 0, /* icon= */ null, /* mcc= */ "", /* mnc= */ "",
                /* countryIso= */ "", /* isEmbedded= */ false,
                /* accessRules= */ null, /* cardString= */ "", isOpportunistic,
                /* groupUUID= */ null, /* carrierId= */ 0, /* profileClass= */ 0);
        return subInfo;
    }

    private AlertDialog showDialog(ConfirmationDialogFragment dialog) throws Throwable {
        mActivityTestRule.runOnUiThread(() -> {
            dialog.show(mActivityTestRule.getActivity().getSupportFragmentManager(),
                    /* tag= */ null);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        return (AlertDialog) dialog.getDialog();
    }
}
