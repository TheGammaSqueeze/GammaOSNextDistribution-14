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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.UserIdInt;
import android.app.AppOpsManager;
import android.app.admin.DeviceAdminInfo;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.os.IBinder;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.settingslib.RestrictedLockUtilsInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public abstract class BaseEnterpriseTestCase {

    protected static final UserHandle MY_USER_ID = UserHandle.of(UserHandle.myUserId());

    protected final Context mRealContext = ApplicationProvider.getApplicationContext();
    protected final Context mSpiedContext = spy(mRealContext);

    protected final String mPackageName = mRealContext.getPackageName();

    protected final PackageManager mRealPm = mRealContext.getPackageManager();
    protected PackageManager mSpiedPm = spy(mRealPm);

    protected final String mAppName = mRealContext.getApplicationInfo().loadLabel(mRealPm)
            .toString();

    protected final ComponentName mDefaultAdmin =
            new ComponentName(mSpiedContext, DefaultDeviceAdminReceiver.class);
    protected final ComponentName mFancyAdmin =
            new ComponentName(mSpiedContext, FancyDeviceAdminReceiver.class);

    protected ResolveInfo mDefaultResolveInfo;
    protected ResolveInfo mFancyResolveInfo;
    protected DeviceAdminInfo mDefaultDeviceAdminInfo;
    protected DeviceAdminInfo mFancyDeviceAdminInfo;

    @Mock
    protected AppOpsManager mAppOpsMgr;

    @Mock
    protected DevicePolicyManager mDpm;

    @Mock
    protected UserManager mUm;

    private MockitoSession mSession;

    @Before
    public final void setFixtures() throws Exception {
        if (mSession != null) {
            throw new IllegalStateException("Already set session - subclasses should NOT explicitly"
                    + " call setFixtures()");
        }
        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .spyStatic(UserManager.class)
                .mockStatic(RestrictedLockUtilsInternal.class)
                .strictness(Strictness.LENIENT)
                .startMocking();

        assertWithMessage("mDpm").that(mDpm).isNotNull();
        assertWithMessage("mUm").that(mUm).isNotNull();

        when(mSpiedContext.getSystemService(AppOpsManager.class)).thenReturn(mAppOpsMgr);
        when(mSpiedContext.getSystemService(DevicePolicyManager.class)).thenReturn(mDpm);
        when(mSpiedContext.getSystemService(PackageManager.class)).thenReturn(mSpiedPm);
        when(mSpiedContext.getPackageManager()).thenReturn(mSpiedPm);
        when(mSpiedContext.getSystemService(UserManager.class)).thenReturn(mUm);
        doReturn(mUm).when(() -> UserManager.get(any()));

        ActivityInfo defaultActivityInfo =
                mRealPm.getReceiverInfo(mDefaultAdmin, PackageManager.GET_META_DATA);
        mDefaultDeviceAdminInfo = new DeviceAdminInfo(mRealContext, defaultActivityInfo);
        mDefaultResolveInfo = new ResolveInfo();
        mDefaultResolveInfo.activityInfo = defaultActivityInfo;

        ActivityInfo fancyActivityInfo =
                mRealPm.getReceiverInfo(mFancyAdmin, PackageManager.GET_META_DATA);
        mFancyDeviceAdminInfo = new DeviceAdminInfo(mRealContext, fancyActivityInfo);
        mFancyResolveInfo = new ResolveInfo();
        mFancyResolveInfo.activityInfo = fancyActivityInfo;
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
            mSession = null;
        }
    }

    protected final void mockProfileOwner() {
        mockActiveAdmin(mDefaultAdmin);
        when(mDpm.getProfileOwner()).thenReturn(mDefaultAdmin);
    }

    protected final void mockProfileOwnerAsUser() {
        when(mDpm.getProfileOwnerAsUser(MY_USER_ID)).thenReturn(mDefaultAdmin);
        when(mDpm.getProfileOwnerAsUser(MY_USER_ID.getIdentifier())).thenReturn(mDefaultAdmin);
    }

    protected final void mockNoProfileOwnerAsUser() {
        when(mDpm.getProfileOwnerAsUser(MY_USER_ID)).thenReturn(null);
        when(mDpm.getProfileOwnerAsUser(MY_USER_ID.getIdentifier())).thenReturn(null);
    }

    protected final void mockDeviceOwner() {
        when(mDpm.isDeviceManaged()).thenReturn(true);
        mockActiveAdmin(mDefaultAdmin);
        when(mDpm.isDeviceManaged()).thenReturn(true);
        when(mDpm.getDeviceOwnerComponentOnCallingUser()).thenReturn(mDefaultAdmin);
        when(mDpm.getDeviceOwnerComponentOnAnyUser()).thenReturn(mDefaultAdmin);
    }

    protected final void mockNoDeviceOwner() {
        when(mDpm.getDeviceOwnerComponentOnCallingUser()).thenReturn(null);
        when(mDpm.getDeviceOwnerComponentOnAnyUser()).thenReturn(null);
    }

    protected final void mockNotManaged() {
        when(mDpm.isDeviceManaged()).thenReturn(false);
    }

    protected final void mockOrganizationName(String orgName) {
        when(mDpm.getDeviceOwnerOrganizationName()).thenReturn(orgName);
    }

    protected final void mockFinancialDevice() {
        when(mDpm.isDeviceManaged()).thenReturn(true);
        when(mDpm.getDeviceOwnerType(mDefaultAdmin))
                .thenReturn(DevicePolicyManager.DEVICE_OWNER_TYPE_FINANCED);
    }

    protected final void mockActiveAdmin(ComponentName componentName) {
        when(mDpm.isAdminActive(componentName)).thenReturn(true);
    }

    protected final void mockInactiveAdmin(ComponentName componentName) {
        when(mDpm.isAdminActive(componentName)).thenReturn(false);
    }

    protected final void mockGetActiveAdmins(ComponentName... componentNames) {
        when(mDpm.getActiveAdmins()).thenReturn(Arrays.asList(componentNames));
    }

    protected final void mockQueryBroadcastReceivers(ResolveInfo... resolveInfoArray) {
        // Need to use doReturn() instead of when() because mSpiedPm is a spy.
        doReturn(Arrays.asList(resolveInfoArray))
                .when(mSpiedPm).queryBroadcastReceivers(any(Intent.class), anyInt());
    }

    protected final void mockGetLongSupportMessageForUser(CharSequence message) {
        when(mDpm.getLongSupportMessageForUser(eq(mDefaultAdmin), anyInt())).thenReturn(message);
    }

    protected final void mockRemovingAdmin(ComponentName admin, int userId) {
        when(mDpm.isRemovingAdmin(admin, userId)).thenReturn(true);
    }

    protected final void mockGrantedPolicies(ComponentName admin, int ... policies) {
        for (int policy: policies) {
            when(mDpm.hasGrantedPolicy(admin, policy)).thenReturn(true);
        }
    }

    protected final void mockHasDeviceAdminFeature() {
        when(mSpiedPm.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)).thenReturn(true);
    }

    protected final void mockNoDeviceAdminFeature() {
        when(mSpiedPm.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)).thenReturn(false);
    }

    protected final void mockSystemUser() {
        when(mUm.isSystemUser()).thenReturn(true);
    }

    protected final void mockNonSystemUser() {
        when(mUm.isSystemUser()).thenReturn(false);
    }

    protected final void mockNullEnforcedAdmin(String restriction, int userId) {
        when(RestrictedLockUtilsInternal
                .checkIfRestrictionEnforced(mSpiedContext, restriction, userId)).thenReturn(null);
    }

    protected final void mockGetLastBugreportTime(long time) {
        when(mDpm.getLastBugReportRequestTime()).thenReturn(time);
    }

    protected final void mockCompMode() {
        mockDeviceOwner();
        UserInfo userInfo = new UserInfo(10, "UserAbc", null, 0,
                UserManager.USER_TYPE_PROFILE_MANAGED);
        when(mUm.getProfiles(anyInt())).thenReturn(Arrays.asList(userInfo));
    }

    protected final void mockGetLastNetworkLogRetrievalTime(long time) {
        when(mDpm.getLastNetworkLogRetrievalTime()).thenReturn(time);
    }

    protected final void mockGetLastSecurityLogRetrievalTime(long time) {
        when(mDpm.getLastSecurityLogRetrievalTime()).thenReturn(time);
    }

    protected final void mockIsCurrentInputMethodSetByOwner(boolean value) {
        when(mDpm.isCurrentInputMethodSetByOwner()).thenReturn(value);
    }

    protected final void mockGetMaximumFailedPasswordsForWipe(@UserIdInt int userId, int max) {
        when(mDpm.getMaximumFailedPasswordsForWipe(mDefaultAdmin, userId)).thenReturn(max);
    }

    protected final void verifyAdminActivated() {
        verify(mDpm).setActiveAdmin(eq(mDefaultAdmin), anyBoolean());
    }

    protected final void verifyAdminNeverActivated() {
        verify(mDpm, never()).setActiveAdmin(any(), anyBoolean());
    }

    protected final void verifyAdminDeactivated() {
        verify(mDpm).removeActiveAdmin(mDefaultAdmin);
    }

    protected final void verifyAdminNeverDeactivated() {
        verify(mDpm, never()).removeActiveAdmin(any());
    }

    protected final void verifyAppOpsMgrSetUserRestriction(int code, boolean restricted) {
        verify(mAppOpsMgr).setUserRestriction(eq(code), eq(restricted), any(IBinder.class));
    }
}
