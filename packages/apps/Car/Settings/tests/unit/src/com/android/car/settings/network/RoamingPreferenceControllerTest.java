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
import android.os.PersistableBundle;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
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
public class RoamingPreferenceControllerTest {

    private static final int SUB_ID = 1;

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private TwoStatePreference mPreference;
    private RoamingPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private CarrierConfigManager mMockCarrierConfigManager;
    @Mock
    private TelephonyManager mMockTelephonyManager;
    @Mock
    private ContentResolver mMockContentResolver;
    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(TelephonyManager.class, withSettings().lenient())
                .mockStatic(SubscriptionManager.class, withSettings().lenient())
                .startMocking();

        ExtendedMockito.when(TelephonyManager.from(mContext)).thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.createForSubscriptionId(SUB_ID))
                .thenReturn(mMockTelephonyManager);
        when(mMockTelephonyManager.getSimCount()).thenReturn(1);
        when(mMockTelephonyManager.isDataRoamingEnabled()).thenReturn(true);
        when(mContext.getContentResolver()).thenReturn(mMockContentResolver);
        when(mContext.getSystemService(CarrierConfigManager.class))
                .thenReturn(mMockCarrierConfigManager);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreference = new SwitchPreference(mContext);
        mPreferenceController = new RoamingPreferenceController(mContext,
                "key", mMockFragmentController, mCarUxRestrictions);
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
    public void onStart_registerObserver() {
        mPreferenceController.onStart(mLifecycleOwner);

        verify(mMockContentResolver).registerContentObserver(
                eq(Settings.Global.getUriFor(Settings.Global.DATA_ROAMING)), eq(false),
                any());
    }

    @Test
    public void onStart_registerObserver_additionalSubId() {
        when(mMockTelephonyManager.getSimCount()).thenReturn(2);
        mPreferenceController.onStart(mLifecycleOwner);

        verify(mMockContentResolver).registerContentObserver(
                eq(Settings.Global.getUriFor(Settings.Global.DATA_ROAMING + SUB_ID)),
                eq(false), any());
    }

    @Test
    public void onStop_unregisterObserver() {
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.onStop(mLifecycleOwner);

        verify(mMockContentResolver).unregisterContentObserver(any());
    }

    @Test
    public void refreshUi_invalidSubId_isDisabled() {
        when(mMockTelephonyManager.createForSubscriptionId(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID)).thenReturn(mMockTelephonyManager);
        mPreferenceController.setFields(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.refreshUi();

        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_validSubId_isEnabled() {
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.refreshUi();

        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void refreshUi_roamingEnabled_setChecked() {
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.refreshUi();

        assertThat(mPreference.isChecked()).isTrue();
    }

    @Test
    public void refreshUi_roamingDisabled_setUnchecked() {
        when(mMockTelephonyManager.isDataRoamingEnabled()).thenReturn(false);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.refreshUi();

        assertThat(mPreference.isChecked()).isFalse();
    }

    @Test
    public void callChangeListener_toggleFalse_turnOffRoaming() {
        mPreference.setChecked(true);

        mPreference.callChangeListener(false);

        verify(mMockTelephonyManager).setDataRoamingEnabled(false);
    }

    @Test
    public void callChangeListener_toggleTrue_needsDialog_showDialog() {
        mPreference.setChecked(false);
        when(mMockTelephonyManager.isDataRoamingEnabled()).thenReturn(false);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_DISABLE_CHARGE_INDICATION_BOOL, false);
        when(mMockCarrierConfigManager.getConfigForSubId(SUB_ID)).thenReturn(bundle);

        mPreference.callChangeListener(true);

        verify(mMockFragmentController).showDialog(any(ConfirmationDialogFragment.class),
                eq(ConfirmationDialogFragment.TAG));
    }

    @Test
    public void confirmDialog_turnOnRoaming() throws Throwable {
        mPreference.setChecked(false);
        when(mMockTelephonyManager.isDataRoamingEnabled()).thenReturn(false);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_DISABLE_CHARGE_INDICATION_BOOL, false);
        when(mMockCarrierConfigManager.getConfigForSubId(SUB_ID)).thenReturn(bundle);

        mPreference.callChangeListener(true);

        // Capture the dialog that is shown on toggle.
        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(dialogCaptor.capture(),
                eq(ConfirmationDialogFragment.TAG));

        // Show the captured dialog on press the confirmation button.
        ConfirmationDialogFragment dialog = dialogCaptor.getValue();
        assertThat(dialogCaptor).isNotNull();
        AlertDialog alertDialog = showDialog(dialog);
        mActivityTestRule.runOnUiThread(() -> {
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mMockTelephonyManager).setDataRoamingEnabled(true);
    }

    @Test
    public void callChangeListener_toggleTrue_doesntNeedDialog_turnOnRoaming() {
        mPreference.setChecked(false);
        when(mMockTelephonyManager.isDataRoamingEnabled()).thenReturn(false);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_DISABLE_CHARGE_INDICATION_BOOL, true);
        when(mMockCarrierConfigManager.getConfigForSubId(SUB_ID)).thenReturn(bundle);

        mPreference.callChangeListener(true);

        verify(mMockTelephonyManager).setDataRoamingEnabled(true);
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
