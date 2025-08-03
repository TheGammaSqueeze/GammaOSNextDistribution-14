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

package com.android.car.settings.location;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.app.AlertDialog;
import android.car.drivingstate.CarUxRestrictions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.widget.Toast;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiSwitchPreference;
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

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AdasLocationSwitchPreferenceControllerTest {
    private LifecycleOwner mLifecycleOwner;
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private CarUiSwitchPreference mSwitchPreference;
    private AdasLocationSwitchPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;

    @Mock
    private FragmentController mFragmentController;

    @Mock
    private LocationManager mLocationManager;

    @Mock
    private Toast mToast;

    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Before
    public void setUp() {
        mLifecycleOwner = new TestLifecycleOwner();
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(LocationManager.class)).thenReturn(mLocationManager);

        mSession = ExtendedMockito.mockitoSession().mockStatic(Toast.class,
                withSettings().lenient()).startMocking();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mSwitchPreference = new CarUiSwitchPreference(mContext);
    }

    @After
    public void tearDown() {
        mSession.finishMocking();
    }

    @Test
    public void unclickable_switchDisabled() throws Throwable {
        initializePreference(/* isAdasLocationEnabled= */true, /* isMainLocationEnabled= */false,
                /* isClickable= */false);

        assertThat(mSwitchPreference.isEnabled()).isFalse();
        assertThat(mSwitchPreference.isChecked()).isTrue();
    }

    @Test
    public void unclickable_onPreferenceClicked_noChange_showToggleDisabledDialog() {
        initializePreference(/* isAdasLocationEnabled= */true, /* isMainLocationEnabled= */false,
                /* isClickable= */false);

        mSwitchPreference.performClick();

        assertThat(mSwitchPreference.isEnabled()).isFalse();
        assertThat(mSwitchPreference.isChecked()).isTrue();
        verify(mLocationManager, never()).setAdasGnssLocationEnabled(anyBoolean());
        verify(mFragmentController)
                .showDialog(any(ConfirmationDialogFragment.class),
                        eq(ConfirmationDialogFragment.TAG));
    }

    @Test
    public void unclickable_powerPolicyOff_onPreferenceClicked_showToggleDisabledDialog() {
        initializePreference(/* isAdasLocationEnabled= */true, /* isMainLocationEnabled= */false,
                /* isClickable= */false);
        mPreferenceController.mPowerPolicyListener.getPolicyChangeHandler()
                .handlePolicyChange(/* isOn= */ false);

        mSwitchPreference.performClick();

        verify(mLocationManager, never()).setAdasGnssLocationEnabled(anyBoolean());
        verify(mFragmentController)
                .showDialog(any(ConfirmationDialogFragment.class),
                        eq(ConfirmationDialogFragment.TAG));
    }

    @Test
    public void powerPolicyOff_onPreferenceClicked_showCorrectToast() throws Throwable {
        int correctToastId = R.string.power_component_disabled;
        mActivityTestRule.runOnUiThread(() -> {
            ExtendedMockito.when(Toast.makeText(any(), eq(correctToastId), anyInt()))
                    .thenReturn(mToast);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        initializePreference(/* isAdasLocationEnabled= */true, /* isMainLocationEnabled= */false,
                /* isClickable= */true);
        mPreferenceController.mPowerPolicyListener.getPolicyChangeHandler()
                .handlePolicyChange(/* isOn= */ false);

        mSwitchPreference.performClick();

        verify(mToast).show();
    }

    @Test
    public void onAdasIntentReceived_updateUi() {
        initializePreference(/* isAdasLocationEnabled= */false, /* isMainLocationEnabled= */false,
                /* isClickable= */true);

        ArgumentCaptor<BroadcastReceiver> broadcastReceiverArgumentCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        ArgumentCaptor<IntentFilter> intentFilterCaptor = ArgumentCaptor.forClass(
                IntentFilter.class);
        verify(mContext, times(2))
                .registerReceiver(broadcastReceiverArgumentCaptor.capture(),
                        intentFilterCaptor.capture(), eq(Context.RECEIVER_NOT_EXPORTED));
        List<IntentFilter> actions = intentFilterCaptor.getAllValues();
        assertTrue(actions.get(0).hasAction(LocationManager.ACTION_ADAS_GNSS_ENABLED_CHANGED));

        when(mLocationManager.isAdasGnssLocationEnabled()).thenReturn(true);
        broadcastReceiverArgumentCaptor.getValue().onReceive(mContext,
                new Intent(LocationManager.ACTION_ADAS_GNSS_ENABLED_CHANGED));

        assertThat(mSwitchPreference.isEnabled()).isTrue();
    }

    @Test
    public void onLocationIntentReceived_updateUi() {
        initializePreference(/* isAdasLocationEnabled= */false, /* isMainLocationEnabled= */false,
                /* isClickable= */true);

        ArgumentCaptor<BroadcastReceiver> broadcastReceiverArgumentCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        ArgumentCaptor<IntentFilter> intentFilterCaptor = ArgumentCaptor.forClass(
                IntentFilter.class);

        verify(mContext, times(2))
                .registerReceiver(broadcastReceiverArgumentCaptor.capture(),
                        intentFilterCaptor.capture(), eq(Context.RECEIVER_NOT_EXPORTED));
        List<IntentFilter> actions = intentFilterCaptor.getAllValues();
        assertTrue(actions.get(1).hasAction(LocationManager.MODE_CHANGED_ACTION));

        when(mLocationManager.isLocationEnabled()).thenReturn(true);
        broadcastReceiverArgumentCaptor.getValue().onReceive(mContext,
                new Intent(LocationManager.MODE_CHANGED_ACTION));

        assertThat(mSwitchPreference.isEnabled()).isFalse();
    }

    @Test
    public void onPreferenceClicked_adasDisabled_shouldEnable_notShowDialog() {
        initializePreference(/* isAdasLocationEnabled= */false, /* isMainLocationEnabled= */false,
                /* isClickable= */true);

        mSwitchPreference.performClick();

        assertThat(mSwitchPreference.isEnabled()).isTrue();
        assertThat(mSwitchPreference.isChecked()).isTrue();
        verify(mLocationManager).setAdasGnssLocationEnabled(true);
        verify(mFragmentController, never())
                .showDialog(any(ConfirmationDialogFragment.class), any());
    }

    @Test
    public void onPreferenceClicked_adasEnabled_shouldStayEnable_showDialog() {
        initializePreference(/* isAdasLocationEnabled= */true, /* isMainLocationEnabled= */false,
                /* isClickable= */true);

        mSwitchPreference.performClick();

        assertThat(mSwitchPreference.isEnabled()).isTrue();
        assertThat(mSwitchPreference.isChecked()).isTrue();
        verify(mLocationManager, never()).setLocationEnabledForUser(anyBoolean(), any());
        verify(mFragmentController)
                .showDialog(any(ConfirmationDialogFragment.class),
                        eq(ConfirmationDialogFragment.TAG));
    }

    @Test
    public void confirmDialog_turnOffDriverAssistance() throws Throwable {
        initializePreference(/* isAdasLocationEnabled= */true, /* isMainLocationEnabled= */false,
                /* isClickable= */true);

        mSwitchPreference.performClick();

        // Capture the dialog that is shown on toggle.
        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mFragmentController).showDialog(dialogCaptor.capture(),
                eq(ConfirmationDialogFragment.TAG));

        // Show the captured dialog on press the confirmation button.
        ConfirmationDialogFragment dialog = dialogCaptor.getValue();
        assertThat(dialogCaptor).isNotNull();
        AlertDialog alertDialog = showDialog(dialog);

        // Confirm action is the listener of negative button.
        mActivityTestRule.runOnUiThread(() -> {
            alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mLocationManager).setAdasGnssLocationEnabled(false);
    }

    @Test
    public void onPowerPolicyChange_isEnabledChanges() {
        initializePreference(/* isAdasLocationEnabled= */ true, /* isMainLocationEnabled= */ false,
                /* isClickable= */true);

        mPreferenceController.mPowerPolicyListener.getPolicyChangeHandler()
                .handlePolicyChange(/* isOn= */ false);

        assertThat(mSwitchPreference.isEnabled()).isFalse();

        mPreferenceController.mPowerPolicyListener.getPolicyChangeHandler()
                .handlePolicyChange(/* isOn= */ true);

        assertThat(mSwitchPreference.isEnabled()).isTrue();
    }

    @Test
    public void onPowerPolicyChange_enabled_locationEnabled_switchStaysDisabled() {
        initializePreference(/* isAdasLocationEnabled= */ true, /* isMainLocationEnabled= */ true,
                /* isClickable= */true);

        assertThat(mSwitchPreference.isEnabled()).isFalse();

        mPreferenceController.mPowerPolicyListener.getPolicyChangeHandler()
                .handlePolicyChange(/* isOn= */ true);

        assertThat(mSwitchPreference.isEnabled()).isFalse();
    }

    @Test
    public void cancelDialog_DriverAssistanceStaysOn() throws Throwable {
        initializePreference(/* isAdasLocationEnabled= */true, /* isMainLocationEnabled= */false,
                /* isClickable= */true);

        mSwitchPreference.performClick();

        // Capture the dialog that is shown on toggle.
        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mFragmentController).showDialog(dialogCaptor.capture(),
                eq(ConfirmationDialogFragment.TAG));

        // Show the captured dialog on press the confirmation button.
        ConfirmationDialogFragment dialog = dialogCaptor.getValue();
        assertThat(dialogCaptor).isNotNull();
        AlertDialog alertDialog = showDialog(dialog);

        // Cancel action is the listener of positive button.
        mActivityTestRule.runOnUiThread(() -> {
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mLocationManager, never()).setAdasGnssLocationEnabled(false);
    }

    private void initializePreference(boolean isAdasLocationEnabled, boolean isMainLocationEnabled,
            boolean isClickable) {
        when(mLocationManager.isAdasGnssLocationEnabled()).thenReturn(isAdasLocationEnabled);
        when(mLocationManager.isLocationEnabled()).thenReturn(isMainLocationEnabled);
        mPreferenceController = new AdasLocationSwitchPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mSwitchPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.mIsClickable = isClickable;
        mPreferenceController.onStart(mLifecycleOwner);
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
