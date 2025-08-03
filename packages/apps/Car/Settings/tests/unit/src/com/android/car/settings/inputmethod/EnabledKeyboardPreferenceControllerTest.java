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

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class EnabledKeyboardPreferenceControllerTest {
    private static final String PLACEHOLDER_LABEL = "placeholder label";
    private static final String PLACEHOLDER_ID = "placeholder id";
    private static final String PLACEHOLDER_SETTINGS_ACTIVITY = "placeholder settings activity";
    private static final String PLACEHOLDER_PACKAGE_NAME = "placeholder package name";
    private static final String ALLOWED_PACKAGE_NAME = "allowed package name";
    private static final String DISALLOWED_PACKAGE_NAME = "disallowed package name";
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private EnabledKeyboardPreferenceController mPreferenceController;
    private LogicalPreferenceGroup mPreference;
    private CarUxRestrictions mCarUxRestrictions;
    private List<String> mPermittedList;

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
        mPermittedList = new ArrayList<>();
        mPermittedList.add(PLACEHOLDER_PACKAGE_NAME);
        mPermittedList.add(ALLOWED_PACKAGE_NAME);

        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(mDevicePolicyManager);
        when(mContext.getSystemService(InputMethodManager.class)).thenReturn(mInputMethodManager);

        mPreferenceController = new TestEnabledKeyboardPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions);
        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreference = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreference);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @Test
    public void refreshUi_permitAllInputMethods() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(PLACEHOLDER_PACKAGE_NAME);
        when(mInputMethodManager.getEnabledInputMethodList()).thenReturn(infos);

        mPreferenceController.refreshUi();

        assertThat(mPreference.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void refreshUi_hasAllowedImeByOrganization() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser())
                .thenReturn(mPermittedList);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME);
        when(mInputMethodManager.getEnabledInputMethodList()).thenReturn(infos);

        mPreferenceController.refreshUi();

        assertThat(mPreference.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void refreshUi_disallowedByOrganization() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser())
                .thenReturn(mPermittedList);
        List<InputMethodInfo> infos = createInputMethodInfoList(DISALLOWED_PACKAGE_NAME);
        when(mInputMethodManager.getEnabledInputMethodList()).thenReturn(infos);

        mPreferenceController.refreshUi();

        assertThat(mPreference.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void refreshUi_skipVoiceTyping() {
        // TODO: b/236143731 Add Sufficient Testing to Prevent "Google Voice Typing" Regression
        for (String gvtPackageName : InputMethodUtil.GVT_PACKAGE_NAMES) {
            List<InputMethodInfo> infos =
                    createInputMethodInfoList(gvtPackageName);
            when(mInputMethodManager.getEnabledInputMethodList()).thenReturn(infos);

            mPreferenceController.refreshUi();

            assertThat(mPreference.getPreferenceCount()).isEqualTo(0);
        }
    }

    @Test
    public void refreshUi_verifyPreferenceIcon() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME);
        when(mInputMethodManager.getEnabledInputMethodList()).thenReturn(infos);

        mPreferenceController.refreshUi();

        Preference preference = mPreference.getPreference(0);
        assertThat(preference.getIcon()).isEqualTo(
                InputMethodUtil.getPackageIcon(mContext.getPackageManager(), infos.get(0)));
    }

    @Test
    public void refreshUi_verifyPreferenceTitle() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME);
        when(mInputMethodManager.getEnabledInputMethodList()).thenReturn(infos);

        mPreferenceController.refreshUi();

        Preference preference = mPreference.getPreference(0);
        assertThat(preference.getTitle()).isEqualTo(
                InputMethodUtil.getPackageLabel(mContext.getPackageManager(), infos.get(0)));
    }

    @Test
    public void refreshUi_verifyPreferenceSummary() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(ALLOWED_PACKAGE_NAME);
        when(mInputMethodManager.getEnabledInputMethodList()).thenReturn(infos);

        mPreferenceController.refreshUi();

        Preference preference = mPreference.getPreference(0);
        assertThat(preference.getSummary()).isEqualTo(
                InputMethodUtil.getSummaryString(mContext, mInputMethodManager, infos.get(0)));
    }

    @Test
    public void performClick_launchSettingsActivity() {
        doNothing().when(mContext).startActivity(any());
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(DISALLOWED_PACKAGE_NAME);
        when(mInputMethodManager.getEnabledInputMethodList()).thenReturn(infos);

        mPreferenceController.refreshUi();

        Preference preference = mPreference.getPreference(0);
        preference.performClick();

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivity(captor.capture());
        Intent intent = captor.getValue();
        assertThat(intent).isNotNull();
        assertThat(intent.getAction()).isEqualTo(Intent.ACTION_MAIN);
        assertThat(intent.getComponent().getClassName()).isEqualTo(PLACEHOLDER_SETTINGS_ACTIVITY);
        assertThat(intent.getComponent().getPackageName()).isEqualTo(DISALLOWED_PACKAGE_NAME);
    }

    @Test
    public void performClick_missingSettingsActivity_noCrash() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(DISALLOWED_PACKAGE_NAME);
        when(mInputMethodManager.getEnabledInputMethodList()).thenReturn(infos);

        mPreferenceController.refreshUi();

        Preference preference = mPreference.getPreference(0);
        preference.performClick(); // if no exception occurs here, test passes
    }

    @Test
    public void performClick_noSettingsActivity_noCrash() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        List<InputMethodInfo> infos = createInputMethodInfoList(
                DISALLOWED_PACKAGE_NAME, /* settingsActivity= */ null);
        when(mInputMethodManager.getEnabledInputMethodList()).thenReturn(infos);

        mPreferenceController.refreshUi();

        Preference preference = mPreference.getPreference(0);
        preference.performClick(); // if no exception occurs here, test passes
    }

    private List<InputMethodInfo> createInputMethodInfoList(String packageName) {
        return createInputMethodInfoList(packageName, PLACEHOLDER_SETTINGS_ACTIVITY);
    }

    private List<InputMethodInfo> createInputMethodInfoList(String packageName,
            String settingsName) {
        List<InputMethodInfo> infos = new ArrayList<>();
        PackageManager packageManager = mContext.getPackageManager();
        infos.add(createMockInputMethodInfoWithSubtypes(
                packageManager, packageName, settingsName));
        return infos;
    }

    private InputMethodInfo createMockInputMethodInfoWithSubtypes(
            PackageManager packageManager, String packageName, String settingsName) {
        InputMethodInfo mockInfo = createMockInputMethodInfo(packageManager, packageName,
                settingsName);
        List<InputMethodSubtype> subtypes = createSubtypes();
        when(mInputMethodManager.getEnabledInputMethodSubtypeList(any(), anyBoolean()))
                .thenReturn(subtypes);

        return mockInfo;
    }

    private static InputMethodInfo createMockInputMethodInfo(
            PackageManager packageManager, String packageName, String settingsName) {
        InputMethodInfo mockInfo = mock(InputMethodInfo.class);
        when(mockInfo.getPackageName()).thenReturn(packageName);
        when(mockInfo.getId()).thenReturn(PLACEHOLDER_ID);
        when(mockInfo.loadLabel(packageManager)).thenReturn(PLACEHOLDER_LABEL);
        when(mockInfo.getServiceInfo()).thenReturn(new ServiceInfo());
        when(mockInfo.getSettingsActivity()).thenReturn(settingsName);
        return mockInfo;
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

    private static class TestEnabledKeyboardPreferenceController extends
            EnabledKeyboardPreferenceController {

        TestEnabledKeyboardPreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController,
                CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        Intent createInputMethodSettingsIntent(String packageName, String settingsActivity) {
            Intent intent = super.createInputMethodSettingsIntent(packageName, settingsActivity);
            // FLAG_ACTIVITY_NEW_TASK is required to launch an activity outside of an activity
            // context.
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
            return intent;
        }
    }
}
