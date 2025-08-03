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
package com.android.car.settings.enterprise;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import static android.app.admin.DeviceAdminInfo.USES_ENCRYPTED_STORAGE;
import static android.app.admin.DeviceAdminInfo.USES_POLICY_EXPIRE_PASSWORD;
import static android.app.admin.DeviceAdminInfo.USES_POLICY_FORCE_LOCK;
import static android.app.admin.DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD;
import static android.app.admin.DeviceAdminInfo.USES_POLICY_RESET_PASSWORD;
import static android.app.admin.DeviceAdminInfo.USES_POLICY_WATCH_LOGIN;
import static android.app.admin.DeviceAdminInfo.USES_POLICY_WIPE_DATA;
import static android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN;
import static android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION;
import static android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN;
import static android.car.test.mocks.AndroidMockitoHelper.syncCallOnMainThread;

import static com.android.car.settings.enterprise.DeviceAdminAddActivity.EXTRA_DEVICE_ADMIN_PACKAGE_NAME;
import static com.android.car.settings.enterprise.DeviceAdminAddFragment.UNINSTALL_DEVICE_ADMIN_REQUEST_CODE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.XmlResourceParser;

import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceScreen;
import androidx.test.core.app.ApplicationProvider;

import com.android.car.settings.R;
import com.android.car.settings.enterprise.DeviceAdminAddHeaderPreferenceController.ActivationListener;
import com.android.car.ui.toolbar.MenuItem;
import com.android.car.ui.toolbar.ToolbarController;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.Arrays;

public final class DeviceAdminAddFragmentTest extends BaseEnterpriseTestCase {

    private static final String EXPLANATION = "To get to the other side";

    private final Context mRealContext = ApplicationProvider.getApplicationContext();

    private DeviceAdminAddFragment mRealFragment;
    private DeviceAdminAddFragment mSpiedFragment;

    @Mock
    private ToolbarController mToolbarController;

    @Mock
    private FragmentActivity mActivity;

    @Mock
    private PreferenceScreen mPreferenceScreen;

    @Mock
    private DeviceAdminAddHeaderPreferenceController mHeaderController;
    @Mock
    private DeviceAdminAddExplanationPreferenceController mExplanationController;
    @Mock
    private DeviceAdminAddSupportPreferenceController mSupportController;
    @Mock
    private DeviceAdminAddWarningPreferenceController mWarningController;
    @Mock
    private DeviceAdminAddPoliciesPreferenceController mPoliciesController;

    @Captor
    private ArgumentCaptor<ActivationListener> mActivationListenerCaptor;

    @Before
    public void createFragments() throws Exception {
        mRealFragment = syncCallOnMainThread(() -> new DeviceAdminAddFragment());
        mSpiedFragment = spy(mRealFragment);

        when(mExplanationController.setDeviceAdmin(any())).thenReturn(mExplanationController);
        when(mHeaderController.setDeviceAdmin(any())).thenReturn(mHeaderController);

        // Note: Must use doReturn (instead of when..doReturn() below because it's a spy
        doReturn(mActivity).when(mSpiedFragment).requireActivity();
        doReturn(mHeaderController).when(mSpiedFragment)
                .use(eq(DeviceAdminAddHeaderPreferenceController.class), anyInt());
        doReturn(mExplanationController).when(mSpiedFragment)
                .use(eq(DeviceAdminAddExplanationPreferenceController.class), anyInt());
        doReturn(mSupportController).when(mSpiedFragment)
                .use(eq(DeviceAdminAddSupportPreferenceController.class), anyInt());
        doReturn(mWarningController).when(mSpiedFragment)
                .use(eq(DeviceAdminAddWarningPreferenceController.class), anyInt());
        doReturn(mPoliciesController).when(mSpiedFragment)
                .use(eq(DeviceAdminAddPoliciesPreferenceController.class), anyInt());
    }

    @Test
    public void testGetPreferenceScreenResId() {
        int resId = mRealFragment.getPreferenceScreenResId();

        XmlResourceParser parser = mRealContext.getResources().getXml(resId);
        assertWithMessage("xml with id %s", resId).that(parser).isNotNull();
    }

    @Test
    public void testSetupToolbar_noIntent() {
        mSpiedFragment.setToolbarTitle(mToolbarController);

        verify(mToolbarController, never()).setTitle(anyInt());
    }

    @Test
    public void testSetupToolbar_notFromActionAddDeviceAdmin() {
        mockActivityIntent(new Intent());

        mSpiedFragment.setToolbarTitle(mToolbarController);

        verify(mToolbarController, never()).setTitle(anyInt());
    }

    @Test
    public void testSetupToolbar_fromActionAddDeviceAdmin() {
        mockActivityIntent(new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN));

        mSpiedFragment.setToolbarTitle(mToolbarController);

        verify(mToolbarController).setTitle(R.string.add_device_admin_msg);
    }

    @Test
    public void testAttach_noIntent() {
        mRealFragment.onAttach(mSpiedContext, mActivity);

        verifyActivityFinished();
        verifyControllersNeverUsed();
    }

    @Test
    public void testAttach_noExtra() {
        mockActivityIntent(new Intent());

        mRealFragment.onAttach(mSpiedContext, mActivity);

        verifyActivityFinished();
        verifyControllersNeverUsed();
    }

    @Test
    public void testAttach_adminExtra_adminNotFound() {
        mockActivityIntent(new Intent()
                .putExtra(EXTRA_DEVICE_ADMIN, new ComponentName("Guy", "Incognito")));

        mRealFragment.onAttach(mSpiedContext, mActivity);

        verifyActivityFinished();
        verifyControllersNeverUsed();
    }

    @Test
    public void testAttach_adminExtra_ok_active() {
        mockActivityIntent(new Intent()
                .putExtra(EXTRA_DEVICE_ADMIN, mDefaultAdmin)
                .putExtra(EXTRA_ADD_EXPLANATION, EXPLANATION));
        mockActiveAdmin(mDefaultAdmin);

        mSpiedFragment.onAttach(mSpiedContext, mActivity);

        verifyActivityNeverFinished();
        verifyControllersUsed(mDefaultAdmin);
    }

    @Test
    public void testAttach_addDeviceAdminAction_active() {
        mockActivityIntent(new Intent(ACTION_ADD_DEVICE_ADMIN)
                .putExtra(EXTRA_DEVICE_ADMIN, mDefaultAdmin)
                .putExtra(EXTRA_ADD_EXPLANATION, EXPLANATION));
        mockActiveAdmin(mDefaultAdmin);

        mSpiedFragment.onAttach(mSpiedContext, mActivity);

        verityActivityResultSet(Activity.RESULT_OK);
        verifyActivityFinished();
        verifyControllersNeverUsed();
    }

    @Test
    public void testAttach_addDeviceAdminAction_active_removing() {
        mockActivityIntent(new Intent(ACTION_ADD_DEVICE_ADMIN)
                .putExtra(EXTRA_DEVICE_ADMIN, mDefaultAdmin)
                .putExtra(EXTRA_ADD_EXPLANATION, EXPLANATION));
        mockActiveAdmin(mDefaultAdmin);
        mockRemovingAdmin(mDefaultAdmin, mSpiedContext.getUserId());

        mSpiedFragment.onAttach(mSpiedContext, mActivity);

        verityActivityResultNeverSet();
        verifyActivityFinished();
        verifyControllersNeverUsed();
    }

    @Test
    public void testAttach_addDeviceAdminAction_active_noRefreshing() {
        mockActivityIntent(new Intent(ACTION_ADD_DEVICE_ADMIN)
                .putExtra(EXTRA_DEVICE_ADMIN, mFancyAdmin)
                .putExtra(EXTRA_ADD_EXPLANATION, EXPLANATION));
        mockActiveAdmin(mFancyAdmin);
        // Grant all policies that mFancyAdmin has.
        mockGrantedPolicies(mFancyAdmin, USES_POLICY_LIMIT_PASSWORD, USES_POLICY_WATCH_LOGIN,
                USES_POLICY_RESET_PASSWORD, USES_POLICY_FORCE_LOCK, USES_POLICY_WIPE_DATA,
                USES_POLICY_EXPIRE_PASSWORD, USES_ENCRYPTED_STORAGE);

        mSpiedFragment.onAttach(mSpiedContext, mActivity);

        verityActivityResultSet(Activity.RESULT_OK);
        verifyActivityFinished();
        verifyControllersNeverUsed();
    }

    @Test
    public void testAttach_addDeviceAdminAction_active_refreshing() {
        mockActivityIntent(new Intent(ACTION_ADD_DEVICE_ADMIN)
                .putExtra(EXTRA_DEVICE_ADMIN, mFancyAdmin)
                .putExtra(EXTRA_ADD_EXPLANATION, EXPLANATION));
        mockActiveAdmin(mFancyAdmin);
        // Grant all policies that mFancyAdmin has besides USES_ENCRYPTED_STORAGE.
        mockGrantedPolicies(mFancyAdmin, USES_POLICY_LIMIT_PASSWORD, USES_POLICY_WATCH_LOGIN,
                USES_POLICY_RESET_PASSWORD, USES_POLICY_FORCE_LOCK, USES_POLICY_WIPE_DATA,
                USES_POLICY_EXPIRE_PASSWORD);
        mSpiedFragment.onAttach(mSpiedContext, mActivity);

        verifyActivityNeverFinished();
        verifyControllersUsed(mFancyAdmin);
    }

    @Test
    public void testAttach_addDeviceAdminAction_inactive_userActivates() {
        ActivationListener listener = attachActivityForResultTesting(ACTION_ADD_DEVICE_ADMIN);

        listener.onChanged(true);

        verityActivityResultSet(Activity.RESULT_OK);
        verifyActivityNeverFinished();
    }

    @Test
    public void testAttach_addDeviceAdminAction_inactive_userDeactivates() {
        ActivationListener listener = attachActivityForResultTesting(ACTION_ADD_DEVICE_ADMIN);

        listener.onChanged(false);

        verityActivityResultSet(Activity.RESULT_CANCELED);
        verifyActivityNeverFinished();
    }

    @Test
    public void testAttach_inactive_notDeviceAdminAction() {
        ActivationListener listener = attachActivityForResultTesting("ACTION_JACKSON");

        listener.onChanged(true); // value doesn't matter

        verityActivityResultNeverSet();
        verifyActivityNeverFinished();
    }

    @Test
    public void testAttach_adminExtra_ok_inactiveButValid() {
        mockActivityIntent(new Intent()
                .putExtra(EXTRA_DEVICE_ADMIN, mDefaultAdmin)
                .putExtra(EXTRA_ADD_EXPLANATION, EXPLANATION));
        mockInactiveAdmin(mDefaultAdmin);
        mockValidAdmin();

        mSpiedFragment.onAttach(mSpiedContext, mActivity);

        verifyActivityNeverFinished();
        verifyControllersUsed(mDefaultAdmin);
    }

    // TODO(b/202342351): add similar test for when new DeviceAdminInfo(context, ri) throws an
    // exception (for example, when metadata is invalid).
    @Test
    public void testAttach_adminExtra_adminFoundButInvalid() {
        mockActivityIntent(new Intent()
                .putExtra(EXTRA_DEVICE_ADMIN, mDefaultAdmin)
                .putExtra(EXTRA_ADD_EXPLANATION, EXPLANATION));
        mockInactiveAdmin(mDefaultAdmin);
        // Don't need to mock pm

        mRealFragment.onAttach(mSpiedContext, mActivity);

        verifyActivityFinished();
        verifyControllersNeverUsed();
    }

    @Test
    public void testAttach_packageExtra_adminNotFound() {
        mockActivityIntent(new Intent().putExtra(EXTRA_DEVICE_ADMIN_PACKAGE_NAME, "D'OH!"));

        mRealFragment.onAttach(mSpiedContext, mActivity);

        verifyActivityFinished();
        verifyControllersNeverUsed();
    }

    @Test
    public void testAttach_packageExtra_ok() {
        mockActivityIntent(new Intent()
                .putExtra(EXTRA_DEVICE_ADMIN_PACKAGE_NAME, mPackageName)
                .putExtra(EXTRA_ADD_EXPLANATION, EXPLANATION));
        mockGetActiveAdmins(mDefaultAdmin);

        mSpiedFragment.onAttach(mSpiedContext, mActivity);

        verifyActivityNeverFinished();
        verifyControllersUsed(mDefaultAdmin);
    }

    @Test
    public void testAttach_adminExtra_doesNotShowUninstallButton() {
        mockActivityIntent(new Intent()
                .putExtra(EXTRA_DEVICE_ADMIN, mDefaultAdmin)
                .putExtra(EXTRA_ADD_EXPLANATION, EXPLANATION));
        mockValidAdmin();

        mSpiedFragment.onAttach(mSpiedContext, mActivity);

        verify(mSpiedFragment).setUninstallButton(any(Context.class), eq(false));
    }

    @Test
    public void testAttach_packageExtra_showsUninstallButton() {
        mockActivityIntent(new Intent()
                .putExtra(EXTRA_DEVICE_ADMIN_PACKAGE_NAME, mPackageName)
                .putExtra(EXTRA_ADD_EXPLANATION, EXPLANATION));
        mockGetActiveAdmins(mDefaultAdmin);

        mSpiedFragment.onAttach(mSpiedContext, mActivity);

        verify(mSpiedFragment).setUninstallButton(any(Context.class), eq(true));
    }

    @Test
    public void testSetUninstallButton_showButtonFalse() {
        mRealFragment.setUninstallButton(mRealContext, false);

        MenuItem button = mRealFragment.getToolbarMenuItems().get(0);
        assertThat(button).isNotNull();
        assertThat(button.getTitle().toString())
                .isEqualTo(mRealContext.getString(R.string.deactivate_and_uninstall_device_admin));
        assertThat(button.isEnabled()).isFalse();
        assertThat(button.isVisible()).isFalse();
    }

    @Test
    public void testSetUninstallButton_showButtonTrue() {
        mRealFragment.setUninstallButton(mRealContext, true);

        MenuItem button = mRealFragment.getToolbarMenuItems().get(0);
        assertThat(button).isNotNull();
        assertThat(button.getTitle().toString())
                .isEqualTo(mRealContext.getString(R.string.deactivate_and_uninstall_device_admin));
        assertThat(button.isEnabled()).isTrue();
        assertThat(button.isVisible()).isTrue();
    }

    @Test
    public void testUninstallOnClickListener_activeAdmin() {
        mSpiedFragment.setDevicePolicyManager(mDpm);
        mSpiedFragment.setPackageToUninstall(mPackageName, mDefaultAdmin);
        mSpiedFragment.setUninstallButton(mSpiedContext, true);
        mockActiveAdmin(mDefaultAdmin);
        doNothing().when(mSpiedFragment).startActivityForResult(any(Intent.class), anyInt());

        mSpiedFragment.getToolbarMenuItems().get(0).getOnClickListener().onClick(/* item= */ null);

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(
                Intent.class);
        verify(mSpiedFragment).startActivityForResult(intentArgumentCaptor.capture(),
                eq(UNINSTALL_DEVICE_ADMIN_REQUEST_CODE));

        Intent intent = intentArgumentCaptor.getValue();
        assertThat(intent.getAction()).isEqualTo(Intent.ACTION_UNINSTALL_PACKAGE);
        assertThat(intent.getData().toString()).isEqualTo("package:" + mPackageName);
        assertThat(intent.getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false)).isTrue();
        verify(mDpm).removeActiveAdmin(mDefaultAdmin);
    }

    @Test
    public void testUninstallOnClickListener_inactiveAdmin() {
        mSpiedFragment.setDevicePolicyManager(mDpm);
        mSpiedFragment.setPackageToUninstall(mPackageName, mDefaultAdmin);
        mSpiedFragment.setUninstallButton(mSpiedContext, true);
        mockInactiveAdmin(mDefaultAdmin);
        doNothing().when(mSpiedFragment).startActivityForResult(any(Intent.class), anyInt());

        mSpiedFragment.getToolbarMenuItems().get(0).getOnClickListener().onClick(/* item= */ null);

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(
                Intent.class);
        verify(mSpiedFragment).startActivityForResult(intentArgumentCaptor.capture(),
                eq(UNINSTALL_DEVICE_ADMIN_REQUEST_CODE));

        Intent intent = intentArgumentCaptor.getValue();
        assertThat(intent.getAction()).isEqualTo(Intent.ACTION_UNINSTALL_PACKAGE);
        assertThat(intent.getData().toString()).isEqualTo("package:" + mPackageName);
        assertThat(intent.getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false)).isTrue();
        verify(mDpm, never()).removeActiveAdmin(mDefaultAdmin);
    }

    @Test
    public void testOnActivityResult_resultOk() {
        mSpiedFragment.onActivityResult(UNINSTALL_DEVICE_ADMIN_REQUEST_CODE, RESULT_OK, null);

        verify(mActivity).setResult(RESULT_OK);
        verify(mActivity).finish();
    }

    @Test
    public void testOnActivityResult_resultNotOk() {
        mSpiedFragment.onActivityResult(UNINSTALL_DEVICE_ADMIN_REQUEST_CODE, RESULT_CANCELED,
                null);

        verify(mActivity, never()).setResult(anyInt());
        verify(mActivity, never()).finish();
    }

    @Test
    public void testSetPreferenceScreenTitle() {
        mockActivityIntent(new Intent()
                .putExtra(EXTRA_DEVICE_ADMIN, mDefaultAdmin)
                .putExtra(EXTRA_ADD_EXPLANATION, EXPLANATION));
        mockActiveAdmin(mDefaultAdmin);
        mSpiedFragment.onAttach(mSpiedContext, mActivity);

        mSpiedFragment.setPreferenceScreenTitle(mPreferenceScreen);

        verify(mPreferenceScreen).setTitle((mDefaultDeviceAdminInfo.loadLabel(mRealPm)));
    }

    private ActivationListener attachActivityForResultTesting(String action) {
        mockValidAdmin();
        mockActivityIntent(new Intent(action)
                .putExtra(EXTRA_DEVICE_ADMIN, mDefaultAdmin));

        mSpiedFragment.onAttach(mSpiedContext, mActivity);

        verify(mHeaderController).setActivationListener(mActivationListenerCaptor.capture());
        return mActivationListenerCaptor.getValue();
    }

    private void mockActivityIntent(Intent intent) {
        when(mActivity.getIntent()).thenReturn(intent);
    }

    private void mockValidAdmin() {
        // TODO(b/202342351): use a custom matcher for
        // DeviceAdminReceiver.ACTION_DEVICE_ADMIN_ENABLED instead of any()
        doReturn(Arrays.asList(mDefaultResolveInfo)).when(mSpiedPm).queryBroadcastReceivers(any(),
                eq(PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS));
    }

    private void verifyActivityFinished() {
        verify(mActivity).finish();
    }

    private void verifyActivityNeverFinished() {
        verify(mActivity, never()).finish();
    }

    private void verityActivityResultSet(int result) {
        verify(mActivity).setResult(result);
    }

    private void verityActivityResultNeverSet() {
        verify(mActivity, never()).setResult(anyInt());
    }

    private void verifyControllersNeverUsed() {
        verify(mSpiedFragment, never()).use(any(), anyInt());
    }

    private void verifyControllersUsed(ComponentName admin) {
        verify(mSpiedFragment).use(DeviceAdminAddHeaderPreferenceController.class,
                R.string.pk_device_admin_add_header);
        verifySetDeviceAdmin(mHeaderController, admin);
        verify(mHeaderController).setActivationListener(any());

        verify(mSpiedFragment).use(DeviceAdminAddExplanationPreferenceController.class,
                R.string.pk_device_admin_add_explanation);
        verifySetDeviceAdmin(mExplanationController, admin);
        verify(mExplanationController).setExplanation(EXPLANATION);

        verify(mSpiedFragment).use(DeviceAdminAddSupportPreferenceController.class,
                R.string.pk_device_admin_add_support);
        verifySetDeviceAdmin(mSupportController, admin);

        verify(mSpiedFragment).use(DeviceAdminAddWarningPreferenceController.class,
                R.string.pk_device_admin_add_warning);
        verifySetDeviceAdmin(mWarningController, admin);

        verify(mSpiedFragment).use(DeviceAdminAddPoliciesPreferenceController.class,
                R.string.pk_device_admin_add_policies);
        verifySetDeviceAdmin(mPoliciesController, admin);
    }

    private void verifySetDeviceAdmin(BaseDeviceAdminAddPreferenceController<?> controller,
            ComponentName admin) {
        verify(controller).setDeviceAdmin(argThat(info->info.getComponent().equals(admin)));
    }
}
