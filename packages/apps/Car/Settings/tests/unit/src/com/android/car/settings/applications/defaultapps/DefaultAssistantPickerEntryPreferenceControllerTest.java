/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.car.settings.applications.defaultapps;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.role.RoleManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiTwoActionIconPreference;
import com.android.settingslib.applications.DefaultAppInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class DefaultAssistantPickerEntryPreferenceControllerTest {

    private static final String TEST_PACKAGE = "com.android.car.settings.testutils";
    private static final String TEST_CLASS = "BaseTestActivity";
    private static final String TEST_COMPONENT =
            new ComponentName(TEST_PACKAGE, TEST_CLASS).flattenToString();
    private final int mUserId = UserHandle.myUserId();

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private CarUiTwoActionIconPreference mPreference;
    private TestDefaultAssistantPickerEntryPreferenceController mController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private RoleManager mMockRoleManager;
    @Mock
    private PackageManager mMockPackageManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        when(mContext.getSystemService(RoleManager.class)).thenReturn(mMockRoleManager);
        when(mContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.queryIntentServices(any(), eq(PackageManager.GET_META_DATA)))
                .thenReturn(Collections.emptyList());
        doNothing().when(mContext).startActivity(any());

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreference = new CarUiTwoActionIconPreference(mContext);
        mController = new TestDefaultAssistantPickerEntryPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
        mController.onCreate(mLifecycleOwner);
    }

    @Test
    public void getCurrentDefaultAppInfo_noAssistant_returnsNull() {
        when(mMockRoleManager.getRoleHolders(RoleManager.ROLE_ASSISTANT))
                .thenReturn(Collections.emptyList());

        DefaultAppInfo info = mController.getCurrentDefaultAppInfo();

        assertThat(info).isNull();
    }

    @Test
    public void getCurrentDefaultAppInfo_hasService_returnsDefaultAppInfo() {
        when(mMockRoleManager.getRoleHolders(RoleManager.ROLE_ASSISTANT))
                .thenReturn(Collections.singletonList(TEST_PACKAGE));
        when(mMockPackageManager.queryIntentServices(any(), eq(PackageManager.GET_META_DATA)))
                .thenReturn(Collections.singletonList(new ResolveInfo()));
        mController.setSettingsActivity(TEST_CLASS);

        DefaultAppInfo info = mController.getCurrentDefaultAppInfo();

        assertThat(info.getKey()).isEqualTo(TEST_COMPONENT);
    }

    @Test
    public void getSettingIntent_noAssistant_returnsNull() {
        when(mMockRoleManager.getRoleHolders(RoleManager.ROLE_ASSISTANT))
                .thenReturn(Collections.emptyList());
        DefaultAppInfo info = new DefaultAppInfo(mContext, mContext.getPackageManager(),
                mUserId, ComponentName.unflattenFromString(TEST_COMPONENT));

        Intent settingIntent = mController.getSettingIntent(info);

        assertThat(settingIntent).isNull();
    }

    @Test
    public void getSettingIntent_hasAssistant_noAssistSupport_returnsNull() {
        when(mMockRoleManager.getRoleHolders(RoleManager.ROLE_ASSISTANT))
                .thenReturn(Collections.singletonList(TEST_PACKAGE));
        when(mMockPackageManager.queryIntentServices(any(), eq(PackageManager.GET_META_DATA)))
                .thenReturn(Collections.singletonList(new ResolveInfo()));
        DefaultAppInfo info = new DefaultAppInfo(mContext, mContext.getPackageManager(),
                mUserId, ComponentName.unflattenFromString(TEST_COMPONENT));

        Intent settingIntent = mController.getSettingIntent(info);

        assertThat(settingIntent).isNull();
    }

    @Test
    public void getSettingIntent_hasAssistant_supportsAssist_hasSettingsActivity_returnsIntent() {
        when(mMockRoleManager.getRoleHolders(RoleManager.ROLE_ASSISTANT))
                .thenReturn(Collections.singletonList(TEST_PACKAGE));
        when(mMockPackageManager.queryIntentServices(any(), eq(PackageManager.GET_META_DATA)))
                .thenReturn(Collections.singletonList(new ResolveInfo()));
        mController.setSettingsActivity(TEST_CLASS);
        DefaultAppInfo info = new DefaultAppInfo(mContext, mContext.getPackageManager(),
                mUserId, ComponentName.unflattenFromString(TEST_COMPONENT));

        Intent result = mController.getSettingIntent(info);

        assertThat(result.getAction()).isEqualTo(Intent.ACTION_MAIN);
        assertThat(result.getComponent()).isEqualTo(
                new ComponentName(TEST_PACKAGE, TEST_CLASS));
    }

    @Test
    public void performClick_permissionControllerExists_startsPermissionController() {
        String testPackage = "com.test.permissions";
        when(mMockPackageManager.getPermissionControllerPackageName()).thenReturn(testPackage);

        mController.handlePreferenceClicked(mPreference);

        ArgumentCaptor<Intent> argumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivity(argumentCaptor.capture());
        Intent intent = argumentCaptor.getValue();
        assertThat(intent.getAction()).isEqualTo(Intent.ACTION_MANAGE_DEFAULT_APP);
        assertThat(intent.getPackage()).isEqualTo(testPackage);
        assertThat(intent.getStringExtra(Intent.EXTRA_ROLE_NAME)).isEqualTo(
                RoleManager.ROLE_ASSISTANT);
    }

    @Test
    public void performClick_permissionControllerDoesntExist_doesNotStartPermissionController() {
        when(mMockPackageManager.getPermissionControllerPackageName()).thenReturn(null);

        mController.handlePreferenceClicked(mPreference);

        verify(mContext, never()).startActivity(any());
    }

    private static class TestDefaultAssistantPickerEntryPreferenceController extends
            DefaultAssistantPickerEntryPreferenceController {

        private String mSettingsActivity;

        TestDefaultAssistantPickerEntryPreferenceController(Context context,
                String preferenceKey, FragmentController fragmentController,
                CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        public void setSettingsActivity(String activity) {
            mSettingsActivity = activity;
        }

        @Override
        String getAssistSettingsActivity(PackageManager pm, ResolveInfo resolveInfo) {
            return mSettingsActivity;
        }
    }
}
