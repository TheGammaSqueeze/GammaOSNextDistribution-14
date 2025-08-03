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

package com.android.car.settings.inputmethod;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class KeyboardManagementPreferenceControllerTest {
    private static final String PLACEHOLDER_LABEL = "placeholder label";
    private static final String PLACEHOLDER_SETTINGS_ACTIVITY = "placeholder settings activity";
    private static final String PLACEHOLDER_PACKAGE_NAME = "placeholder package name";
    private static final String PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE =
            "placeholder id defaultable direct boot aware";
    private static final String PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE =
            "placeholder id defaultable not direct boot aware";
    private static final String PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE =
            "placeholder id not defaultable direct boot aware";
    private static final String PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE =
            "placeholder id not defaultable not direct boot aware";
    private static final String DISALLOWED_PACKAGE_NAME = "disallowed package name";
    private static final String ALLOWED_PACKAGE_NAME = "allowed package name";

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private KeyboardManagementPreferenceController mPreferenceController;
    private LogicalPreferenceGroup mPreferenceGroup;
    private CarUxRestrictions mCarUxRestrictions;
    private List<String> mPermittedList;
    private Map<String, InputMethodInfo> mInputMethodMap;
    private String mInitialInputMethods;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private DevicePolicyManager mDevicePolicyManager;
    @Mock
    private InputMethodManager mInputMethodManager;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mInputMethodMap = new HashMap<>();
        mPermittedList = new ArrayList<>();
        mPermittedList.add(PLACEHOLDER_PACKAGE_NAME);
        mPermittedList.add(ALLOWED_PACKAGE_NAME);
        mInitialInputMethods = Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS);

        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(mDevicePolicyManager);
        when(mContext.getSystemService(InputMethodManager.class)).thenReturn(mInputMethodManager);
        when(mInputMethodManager.getInputMethodList()).thenReturn(new ArrayList<>());

        mPreferenceController = new KeyboardManagementPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions);
        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @After
    public void tearDown() {
        Settings.Secure.putString(mContext.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS, mInitialInputMethods);
    }

    @Test
    @UiThreadTest
    public void refreshUi_permitAllInputMethods_preferenceCountIs4() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(infos);

        mPreferenceController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(4);
    }

    @Test
    @UiThreadTest
    public void refreshUi_multiplteAllowedImeByOrganization_allPreferencesVisible() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(
                mPermittedList);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(infos);

        mPreferenceController.refreshUi();

        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            assertThat(mPreferenceGroup.getPreference(i).isVisible()).isTrue();
        }
    }

    @Test
    @UiThreadTest
    public void refreshUi_multipleEnabledInputMethods_allPreferencesEnabled() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(
                mPermittedList);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(infos);

        mPreferenceController.refreshUi();

        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            assertThat(mPreferenceGroup.getPreference(i).isEnabled()).isTrue();
        }
    }

    @Test
    @UiThreadTest
    public void refreshUi_multipleEnabledInputMethods_allPreferencesChecked() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(
                mPermittedList);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(infos);

        mPreferenceController.refreshUi();

        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            assertThat(((SwitchPreference) mPreferenceGroup.getPreference(i)).isChecked())
                    .isTrue();
        }
    }

    @Test
    @UiThreadTest
    public void refreshUi_disallowedByOrganization_preferenceShownDisabled() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(
                mPermittedList);
        List<InputMethodInfo> infos = createInputMethodInfoList(DISALLOWED_PACKAGE_NAME,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(infos);

        mPreferenceController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(infos.size());

        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            SwitchPreference pref = (SwitchPreference) mPreferenceGroup.getPreference(i);
            assertThat(pref.isVisible()).isTrue();
            assertThat(pref.isEnabled()).isTrue();
        }
    }

    @Test
    @UiThreadTest
    public void refreshUi_skipVoiceTyping() {
        // TODO: b/236143731 Add Sufficient Testing to Prevent "Google Voice Typing" Regression
        for (String gvtPackageName : InputMethodUtil.GVT_PACKAGE_NAMES) {
            List<InputMethodInfo> infos =
                    createInputMethodInfoList(gvtPackageName);
            setInputMethodList(infos);

            mPreferenceController.refreshUi();

            assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
        }
    }

    @Test
    @UiThreadTest
    public void refreshUi_verifyPreferenceIcon() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(infos);

        mPreferenceController.refreshUi();

        Preference preference = mPreferenceGroup.getPreference(0);
        assertThat(preference.getIcon()).isEqualTo(
                InputMethodUtil.getPackageIcon(mContext.getPackageManager(), infos.get(0)));
    }

    @Test
    @UiThreadTest
    public void refreshUi_verifyPreferenceTitle() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(infos);

        mPreferenceController.refreshUi();

        Preference preference = mPreferenceGroup.getPreference(0);
        assertThat(preference.getTitle()).isEqualTo(
                InputMethodUtil.getPackageLabel(mContext.getPackageManager(), infos.get(0)));
    }

    @Test
    @UiThreadTest
    public void refreshUi_verifyPreferenceSummary() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(infos);

        mPreferenceController.refreshUi();

        Preference preference = mPreferenceGroup.getPreference(0);
        assertThat(preference.getSummary()).isEqualTo(
                InputMethodUtil.getSummaryString(mContext, mInputMethodManager, infos.get(0)));
    }

    @Test
    @UiThreadTest
    public void refreshUi_oneInputMethod_noneEnabled_oneInputMethodPreferenceInView() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    @UiThreadTest
    public void refreshUi_oneInputMethod_noneEnabled_preferenceEnabled() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        assertThat(mPreferenceGroup.getPreference(0).isEnabled()).isTrue();
    }

    @Test
    @UiThreadTest
    public void refreshUi_oneInputMethod_noneEnabled_preferenceNotChecked() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        assertThat(((SwitchPreference) mPreferenceGroup.getPreference(0)).isChecked())
                .isFalse();
    }

    @Test
    @UiThreadTest
    public void refreshUi_oneInputMethod_defaultable_notChecked_preferenceEnabled() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        assertThat(mPreferenceGroup.getPreference(0).isEnabled()).isTrue();
    }

    @Test
    @UiThreadTest
    public void performClick_toggleTrue_securityDialogShown() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        verify(mMockFragmentController).showDialog(
                any(ConfirmationDialogFragment.class),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
    }

    @Test
    @UiThreadTest
    public void performClick_toggleTrue_showSecurityDialog_positive_noOtherPreferenceAdded() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(dialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreferenceGroup.getPreference(0).getKey()).isEqualTo(
                PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE);
    }

    @Test
    @UiThreadTest
    public void performClick_toggleTrue_showSecurityDialog_positive_preferenceChecked() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(dialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        assertThat(((SwitchPreference) mPreferenceGroup.getPreference(0)).isChecked())
                .isTrue();
    }

    @Test
    @UiThreadTest
    public void performClick_toggleTrue_showSecurityDialog_positive_preferenceEnabled() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(dialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        assertThat(mPreferenceGroup.getPreference(0).isEnabled()).isTrue();
    }

    @Test
    @UiThreadTest
    public void performClick_toggleTrue_showSecurityDialog_positive_inputMethodEnabled() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(dialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        assertThat(getEnabledInputMethodList().get(0).getId())
                .isEqualTo(PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE);
    }

    @Test
    @UiThreadTest
    public void performClick_toggleTrue_showSecurityDialog_negative_noOtherPreferenceAdded() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(dialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_NEGATIVE);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreferenceGroup.getPreference(0).getKey()).isEqualTo(
                PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE);
    }

    @Test
    @UiThreadTest
    public void performClick_toggleTrue_showSecurityDialog_negative_preferenceEnabled() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(dialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_NEGATIVE);

        assertThat(mPreferenceGroup.getPreference(0).isEnabled()).isTrue();
    }

    @Test
    @UiThreadTest
    public void performClick_toggleTrue_showSecurityDialog_negative_inputMethodDisabled() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(dialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_NEGATIVE);

        assertThat(((SwitchPreference) mPreferenceGroup.getPreference(0)).isChecked())
                .isFalse();
    }

    @Test
    @UiThreadTest
    public void performClick_toggleTrue_directBootWarningShown() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> securityDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(
                securityDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = securityDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);


        verify(mMockFragmentController).showDialog(
                any(ConfirmationDialogFragment.class),
                eq(KeyboardManagementPreferenceController.DIRECT_BOOT_WARN_DIALOG_TAG));
    }

    @Test
    @UiThreadTest
    public void performClick_toggleTrue_showDirectBootDialog_positive_noOtherPreferenceAdded() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> securityDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(
                securityDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = securityDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        ArgumentCaptor<ConfirmationDialogFragment> bootDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(bootDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.DIRECT_BOOT_WARN_DIALOG_TAG));
        dialogFragment = bootDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreferenceGroup.getPreference(0).getKey()).isEqualTo(
                PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
    }

    @Test
    @UiThreadTest
    public void performClick_toggleTrue_showDirectBootDialog_positive_preferenceChecked() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> securityDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(
                securityDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = securityDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        ArgumentCaptor<ConfirmationDialogFragment> bootDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(bootDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.DIRECT_BOOT_WARN_DIALOG_TAG));
        dialogFragment = bootDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        assertThat(((SwitchPreference) mPreferenceGroup.getPreference(0)).isChecked())
                .isTrue();
    }

    @Test
    @UiThreadTest
    public void performClick_toggleTrue_showDirectBootDialog_positive_preferenceEnabled() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> securityDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(
                securityDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = securityDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        ArgumentCaptor<ConfirmationDialogFragment> bootDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(bootDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.DIRECT_BOOT_WARN_DIALOG_TAG));
        dialogFragment = bootDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        assertThat(mPreferenceGroup.getPreference(0).isEnabled()).isTrue();
    }

    @Test
    @UiThreadTest
    public void performClick_toggleTrue_showDirectBootDialog_positive_inputMethodEnabled() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> securityDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(
                securityDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = securityDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        ArgumentCaptor<ConfirmationDialogFragment> bootDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(bootDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.DIRECT_BOOT_WARN_DIALOG_TAG));
        dialogFragment = bootDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        assertThat(getEnabledInputMethodList().get(0).getId())
                .isEqualTo(PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
    }

    @Test
    @UiThreadTest
    public void performClick_toggleTrue_showDirectBootDialog_negative_noOtherPreferenceAdded() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> securityDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(
                securityDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = securityDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        ArgumentCaptor<ConfirmationDialogFragment> bootDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(bootDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.DIRECT_BOOT_WARN_DIALOG_TAG));
        dialogFragment = bootDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_NEGATIVE);


        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreferenceGroup.getPreference(0).getKey()).isEqualTo(
                PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
    }

    @Test
    @UiThreadTest
    public void performClick_toggleTrue_showDirectBootDialog_negative_preferenceNotChecked() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> securityDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(
                securityDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = securityDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        ArgumentCaptor<ConfirmationDialogFragment> bootDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(bootDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.DIRECT_BOOT_WARN_DIALOG_TAG));
        dialogFragment = bootDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_NEGATIVE);


        assertThat(((SwitchPreference) mPreferenceGroup.getPreference(0)).isChecked())
                .isFalse();
    }

    @Test
    @UiThreadTest
    public void performClick_toggleTrue_showDirectBootDialog_negative_preferenceEnabled() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> securityDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(
                securityDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = securityDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        ArgumentCaptor<ConfirmationDialogFragment> bootDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(bootDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.DIRECT_BOOT_WARN_DIALOG_TAG));
        dialogFragment = bootDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_NEGATIVE);

        assertThat(mPreferenceGroup.getPreference(0).isEnabled()).isTrue();
    }

    @Test
    @UiThreadTest
    public void performClick_toggleTrue_showDirectBootDialog_negative_inputMethodDisabled() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(new ArrayList<>());

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> securityDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(
                securityDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = securityDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        ArgumentCaptor<ConfirmationDialogFragment> bootDialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(bootDialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.DIRECT_BOOT_WARN_DIALOG_TAG));
        dialogFragment = bootDialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_NEGATIVE);

        assertThat(mInputMethodManager.getEnabledInputMethodList().size())
                .isEqualTo(0);
    }

    @Test
    @UiThreadTest
    public void performClick_toggleFalse_noOtherPreferenceAdded() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(infos);

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(mPreferenceGroup.getPreference(0).getKey()).isEqualTo(
                PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
    }

    @Test
    @UiThreadTest
    public void performClick_toggleFalse_preferenceNotChecked() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(infos);

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        assertThat(((SwitchPreference) mPreferenceGroup.getPreference(0)).isChecked())
                .isFalse();
    }

    @Test
    @UiThreadTest
    public void performClick_toggleFalse_preferenceEnabled() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(infos);

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        assertThat((mPreferenceGroup.getPreference(0)).isEnabled())
                .isTrue();
    }

    @Test
    @UiThreadTest
    public void performClick_toggleFalse_inputMethodDisabled() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(infos);

        mPreferenceController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();

        assertThat(mInputMethodManager.getEnabledInputMethodList().size())
                .isEqualTo(0);
    }

    @Test
    @UiThreadTest
    public void performClick_toggleFalse_twoDefaultable_notClickDefaultablePreferenceDisabled() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(infos);

        mPreferenceController.refreshUi();

        getPreferenceFromGroupByKey(mPreferenceGroup, PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE)
                .performClick();

        assertThat(getPreferenceFromGroupByKey(mPreferenceGroup,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE).isEnabled()).isFalse();
    }

    @Test
    @UiThreadTest
    public void performClick_toggleFalse_twoDefaultable_clickedDefaultablePreferenceEnabled() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(infos);

        mPreferenceController.refreshUi();

        getPreferenceFromGroupByKey(mPreferenceGroup,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE).performClick();

        assertThat(getPreferenceFromGroupByKey(mPreferenceGroup,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE).isEnabled()).isTrue();
    }

    @Test
    @UiThreadTest
    public void performClick_toggleFalse_twoDefaultable_nonDefaultablePreferenceEnabled() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(infos);

        mPreferenceController.refreshUi();

        getPreferenceFromGroupByKey(mPreferenceGroup,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE).performClick();

        assertThat(getPreferenceFromGroupByKey(mPreferenceGroup,
                PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE).isEnabled()).isTrue();
    }

    @Test
    @UiThreadTest
    public void performClick_toggleFalse_twoDefaultable_clickedDefaultablePreferenceNotChecked() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(infos);

        mPreferenceController.refreshUi();

        getPreferenceFromGroupByKey(mPreferenceGroup,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE).performClick();

        assertThat(((SwitchPreference) getPreferenceFromGroupByKey(mPreferenceGroup,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE)).isChecked()).isFalse();
    }

    @Test
    @UiThreadTest
    public void performClick_toggleFalse_twoDefaultable_notClickedDefaultablePreferenceChecked() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(infos);

        mPreferenceController.refreshUi();

        getPreferenceFromGroupByKey(mPreferenceGroup,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE).performClick();

        assertThat(((SwitchPreference) getPreferenceFromGroupByKey(mPreferenceGroup,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE)).isChecked()).isTrue();
    }

    @Test
    @UiThreadTest
    public void performClick_toggleFalse_twoDefaultable_nonDefaultablePreferenceChecked() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        setEnabledInputMethodList(infos);

        mPreferenceController.refreshUi();

        getPreferenceFromGroupByKey(mPreferenceGroup,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE).performClick();

        assertThat(((SwitchPreference) getPreferenceFromGroupByKey(mPreferenceGroup,
                PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE)).isChecked()).isTrue();
    }

    @Test
    @UiThreadTest
    public void performClick_toggleTrue_twoDefaultable_allPreferencesEnabled() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        List<InputMethodInfo> infos2 = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setEnabledInputMethodList(infos2);

        mPreferenceController.refreshUi();

        getPreferenceFromGroupByKey(mPreferenceGroup,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(dialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            assertThat(mPreferenceGroup.getPreference(i).isEnabled()).isTrue();
        }
    }

    @Test
    @UiThreadTest
    public void performClick_toggleTrue_twoDefaultable_allPreferencesChecked() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setInputMethodList(infos);
        List<InputMethodInfo> infos2 = createInputMethodInfoList(
                ALLOWED_PACKAGE_NAME, PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE,
                PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE);
        setEnabledInputMethodList(infos2);

        mPreferenceController.refreshUi();

        getPreferenceFromGroupByKey(mPreferenceGroup,
                PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE).performClick();

        ArgumentCaptor<ConfirmationDialogFragment> dialogCaptor = ArgumentCaptor.forClass(
                ConfirmationDialogFragment.class);
        verify(mMockFragmentController).showDialog(dialogCaptor.capture(),
                eq(KeyboardManagementPreferenceController.SECURITY_WARN_DIALOG_TAG));
        ConfirmationDialogFragment dialogFragment = dialogCaptor.getValue();

        dialogFragment.onClick(null, DialogInterface.BUTTON_POSITIVE);

        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            assertThat(((SwitchPreference) mPreferenceGroup.getPreference(i)).isChecked())
                    .isTrue();
        }
    }

    private InputMethodInfo createMockInputMethodInfo(
            Context context, PackageManager packageManager,
            String packageName, String id, boolean isDefaultable, boolean directBootAware) {
        ServiceInfo mockServiceInfo = mock(ServiceInfo.class);
        mockServiceInfo.directBootAware = directBootAware;

        InputMethodInfo mockInfo = mock(InputMethodInfo.class);
        when(mockInfo.getPackageName()).thenReturn(packageName);
        when(mockInfo.loadLabel(packageManager)).thenReturn(PLACEHOLDER_LABEL);
        when(mockInfo.getServiceInfo()).thenReturn(mockServiceInfo);
        when(mockInfo.getSettingsActivity()).thenReturn(PLACEHOLDER_SETTINGS_ACTIVITY);
        when(mockInfo.getId()).thenReturn(id);
        when(mockInfo.isDefault(context)).thenReturn(isDefaultable);
        List<InputMethodSubtype> subtypes = createSubtypes();
        when(mInputMethodManager.getEnabledInputMethodSubtypeList(any(), anyBoolean()))
                .thenReturn(subtypes);
        return mockInfo;
    }

    private static Preference getPreferenceFromGroupByKey(PreferenceGroup prefGroup, String key) {
        for (int i = 0; i < prefGroup.getPreferenceCount(); i++) {
            Preference pref = prefGroup.getPreference(i);
            if (pref.getKey().equals(key)) {
                return pref;
            }
        }
        return null;
    }

    private static List<InputMethodSubtype> createSubtypes() {
        List<InputMethodSubtype> subtypes = new ArrayList<>();
        subtypes.add(createSubtype(1, "en_US"));
        subtypes.add(createSubtype(2, "de_BE"));
        subtypes.add(createSubtype(3, "oc-FR"));
        return subtypes;
    }

    private static InputMethodSubtype createSubtype(int id, String locale) {
        return new InputMethodSubtype.InputMethodSubtypeBuilder().setSubtypeId(id)
                .setSubtypeLocale(locale).setIsAuxiliary(false).setIsAsciiCapable(true).build();
    }

    private List<InputMethodInfo> createInputMethodInfoList(String packageName, String... ids) {
        List<InputMethodInfo> infos = new ArrayList<>();
        PackageManager packageManager = mContext.getPackageManager();
        List<String> idsList = Arrays.asList(ids);
        idsList.forEach(id -> {
            boolean defaultable;
            boolean directBootAware;
            switch (id) {
                case PLACEHOLDER_ID_DEFAULTABLE_DIRECT_BOOT_AWARE:
                    defaultable = true;
                    directBootAware = true;
                    break;
                case PLACEHOLDER_ID_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE:
                    defaultable = true;
                    directBootAware = false;
                    break;
                case PLACEHOLDER_ID_NOT_DEFAULTABLE_DIRECT_BOOT_AWARE:
                    defaultable = false;
                    directBootAware = true;
                    break;
                default: //case PLACEHOLDER_ID_NOT_DEFAULTABLE_NOT_DIRECT_BOOT_AWARE:
                    defaultable = false;
                    directBootAware = false;
                    break;
            }
            infos.add(createMockInputMethodInfo(mContext, packageManager, packageName, id,
                    defaultable, directBootAware));
        });
        return infos;
    }

    private void setInputMethodList(List<InputMethodInfo> list) {
        when(mInputMethodManager.getInputMethodList()).thenReturn(list);
        if (list != null && list.size() > 0) {
            addInputMethodInfosToMap(list);
        }
    }

    private void setEnabledInputMethodList(List<InputMethodInfo> list) {
        if (list == null || list.size() == 0) {
            Settings.Secure.putString(mContext.getContentResolver(),
                    Settings.Secure.ENABLED_INPUT_METHODS, "");
            return;
        }

        String concatenatedInputMethodIds = createInputMethodIdString(list.stream().map(
                imi -> imi.getId()).collect(Collectors.toList()).toArray(new String[list.size()]));
        Settings.Secure.putString(mContext.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS, concatenatedInputMethodIds);
        addInputMethodInfosToMap(list);
        when(mInputMethodManager.getEnabledInputMethodList()).thenAnswer(
                (Answer<List<InputMethodInfo>>) invocation -> getEnabledInputMethodList());
    }

    private List<InputMethodInfo> getEnabledInputMethodList() {
        List<InputMethodInfo> enabledInputMethodList = new ArrayList<>();

        String inputMethodIdString = Settings.Secure.getString(
                mContext.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS);
        if (inputMethodIdString == null || inputMethodIdString.isEmpty()) {
            return enabledInputMethodList;
        }

        InputMethodUtil.sInputMethodSplitter.setString(inputMethodIdString);
        while (InputMethodUtil.sInputMethodSplitter.hasNext()) {
            enabledInputMethodList.add(mInputMethodMap.get(InputMethodUtil.sInputMethodSplitter
                    .next()));
        }
        return enabledInputMethodList;
    }

    private String createInputMethodIdString(String... ids) {
        int size = ids == null ? 0 : ids.length;

        if (size == 1) {
            return ids[0];
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < size; i++) {
            builder.append(ids[i]);
            if (i != size - 1) {
                builder.append(InputMethodUtil.INPUT_METHOD_DELIMITER);
            }
        }
        return builder.toString();
    }

    private void addInputMethodInfosToMap(List<InputMethodInfo> inputMethodInfos) {
        if (mInputMethodMap == null || mInputMethodMap.size() == 0) {
            mInputMethodMap = inputMethodInfos.stream().collect(Collectors.toMap(
                    InputMethodInfo::getId, imi -> imi));
            return;
        }

        inputMethodInfos.forEach(imi -> {
            mInputMethodMap.put(imi.getId(), imi);
        });
    }
}
