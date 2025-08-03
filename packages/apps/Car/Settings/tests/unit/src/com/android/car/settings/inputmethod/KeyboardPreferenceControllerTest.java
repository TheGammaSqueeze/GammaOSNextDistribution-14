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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class KeyboardPreferenceControllerTest {
    private static final String EMPTY = "";
    private static final String PLACEHOLDER_LABEL = "placeholder label";
    private static final String PLACEHOLDER_LABEL_1 = "placeholder label 1";
    private static final String PLACEHOLDER_LABEL_2 = "placeholder label 2";
    private static final String PLACEHOLDER_SETTINGS_ACTIVITY = "placeholder settings activity";
    private static final String PLACEHOLDER_PACKAGE_NAME = "placeholder package name";
    private static final String ALLOWED_PACKAGE_NAME = "allowed package name";
    private static final String DISALLOWED_PACKAGE_NAME = "disallowed package name";
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private KeyboardPreferenceController mPreferenceController;
    private Preference mPreference;
    private CarUxRestrictions mCarUxRestrictions;
    private List<String> mPermittedList;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private DevicePolicyManager mDevicePolicyManager;
    @Mock
    private InputMethodManager mInputMethodManager;

    @Before
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

        mPreferenceController = new KeyboardPreferenceController(mContext,
                /* preferenceKey= */ "key", mMockFragmentController,
                mCarUxRestrictions);
        mPreference = new Preference(mContext);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @Test
    public void refreshUi_noInputMethodInfo() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);
        when(mInputMethodManager.getEnabledInputMethodList()).thenReturn(Collections.emptyList());

        mPreferenceController.refreshUi();
        assertThat(mPreference.getSummary()).isEqualTo(EMPTY);
    }

    @Test
    public void refreshUi_permitAllInputMethods_hasOneInputMethodInfo() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);

        List<InputMethodInfo> infos = new ArrayList<>();
        PackageManager packageManager = mContext.getPackageManager();
        infos.add(
                createInputMethodInfo(packageManager, PLACEHOLDER_PACKAGE_NAME, PLACEHOLDER_LABEL));
        when(mInputMethodManager.getEnabledInputMethodList()).thenReturn(infos);

        mPreferenceController.refreshUi();
        assertThat(mPreference.getSummary()).isNotNull();
        assertThat(mPreference.getSummary().toString().contains(PLACEHOLDER_LABEL)).isTrue();
    }

    @Test
    public void refreshUi_permitAllInputMethods_hasTwoInputMethodInfo() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);

        List<InputMethodInfo> infos = new ArrayList<>();
        PackageManager packageManager = mContext.getPackageManager();
        infos.add(createInputMethodInfo(packageManager, PLACEHOLDER_PACKAGE_NAME,
                PLACEHOLDER_LABEL));
        infos.add(createInputMethodInfo(packageManager, PLACEHOLDER_PACKAGE_NAME,
                PLACEHOLDER_LABEL_1));
        when(mInputMethodManager.getEnabledInputMethodList()).thenReturn(infos);

        mPreferenceController.refreshUi();
        assertThat(mPreference.getSummary()).isNotNull();
        assertThat(mPreference.getSummary().toString().contains(PLACEHOLDER_LABEL)).isTrue();
        assertThat(mPreference.getSummary().toString().contains(PLACEHOLDER_LABEL_1)).isTrue();
    }

    @Test
    public void refreshUi_permitAllInputMethods_hasThreeInputMethodInfo() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(null);

        List<InputMethodInfo> infos = new ArrayList<>();
        PackageManager packageManager = mContext.getPackageManager();
        infos.add(createInputMethodInfo(packageManager, PLACEHOLDER_PACKAGE_NAME,
                PLACEHOLDER_LABEL));
        infos.add(createInputMethodInfo(packageManager, PLACEHOLDER_PACKAGE_NAME,
                PLACEHOLDER_LABEL_1));
        infos.add(createInputMethodInfo(packageManager, PLACEHOLDER_PACKAGE_NAME,
                PLACEHOLDER_LABEL_2));
        when(mInputMethodManager.getEnabledInputMethodList()).thenReturn(infos);

        mPreferenceController.refreshUi();
        assertThat(mPreference.getSummary()).isNotNull();
        assertThat(mPreference.getSummary().toString().contains(PLACEHOLDER_LABEL)).isTrue();
        assertThat(mPreference.getSummary().toString().contains(PLACEHOLDER_LABEL_1)).isTrue();
        assertThat(mPreference.getSummary().toString().contains(PLACEHOLDER_LABEL_2)).isTrue();
    }

    @Test
    public void refreshUi_hasAllowedImeByOrganization() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(
                mPermittedList);

        List<InputMethodInfo> infos = new ArrayList<>();
        PackageManager packageManager = mContext.getPackageManager();
        infos.add(createInputMethodInfo(packageManager, ALLOWED_PACKAGE_NAME, PLACEHOLDER_LABEL));
        when(mInputMethodManager.getEnabledInputMethodList()).thenReturn(infos);

        mPreferenceController.refreshUi();
        assertThat(mPreference.getSummary()).isEqualTo(PLACEHOLDER_LABEL);
    }

    @Test
    public void refreshUi_disallowedByOrganization() {
        when(mDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).thenReturn(
                mPermittedList);

        List<InputMethodInfo> infos = new ArrayList<>();
        PackageManager packageManager = mContext.getPackageManager();
        infos.add(
                createInputMethodInfo(packageManager, DISALLOWED_PACKAGE_NAME, PLACEHOLDER_LABEL));
        when(mInputMethodManager.getEnabledInputMethodList()).thenReturn(infos);

        mPreferenceController.refreshUi();
        assertThat(mPreference.getSummary()).isEqualTo(EMPTY);
    }

    private static InputMethodInfo createInputMethodInfo(
            PackageManager packageManager, String packageName, String label) {
        ResolveInfo resolveInfo = mock(ResolveInfo.class);
        ServiceInfo serviceInfo = new ServiceInfo();
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = packageName;
        applicationInfo.enabled = true;
        applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        serviceInfo.applicationInfo = applicationInfo;
        serviceInfo.enabled = true;
        serviceInfo.packageName = packageName;
        serviceInfo.name = label;
        serviceInfo.exported = true;
        serviceInfo.nonLocalizedLabel = label;
        resolveInfo.serviceInfo = serviceInfo;
        resolveInfo.nonLocalizedLabel = label;
        when(resolveInfo.loadLabel(packageManager)).thenReturn(label);
        return new InputMethodInfo(resolveInfo,
                /* isAuxIme= */ false,
                PLACEHOLDER_SETTINGS_ACTIVITY,
                /* subtypes= */ null,
                /* isDefaultResId= */ 1,
                /* forceDefault= */ false);
    }
}
