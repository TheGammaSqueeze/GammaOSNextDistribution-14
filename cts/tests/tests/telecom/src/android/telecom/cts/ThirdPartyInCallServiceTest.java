/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.telecom.cts;

import static android.telecom.cts.TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.telecom.cts.thirdptydialer.CtsThirdPtyDialerInCallServiceControl;
import android.telecom.cts.thirdptyincallservice.CtsThirdPartyInCallServiceControl;
import android.telecom.cts.thirdptyincallservice.ICtsThirdPartyInCallServiceControl;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ThirdPartyInCallServiceTest extends BaseTelecomTestWithMockServices {

    private static final String TAG = ThirdPartyInCallServiceTest.class.getSimpleName();
    private Context mContext;
    private CtsRoleManagerAdapter mCtsRoleManagerAdapter;
    ICtsThirdPartyInCallServiceControl mICtsThirdPartyInCallServiceControl;
    private boolean mSkipNullUnboundLatch;
    private String mPreviousRoleHolder;
    private String mThirdPartyPackageName;
    private boolean mIsDialerRoleAvailable;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        mCtsRoleManagerAdapter = new CtsRoleManagerAdapter(getInstrumentation());

        // Do not continue with tests if the Dialer role is not available.
        mIsDialerRoleAvailable = mCtsRoleManagerAdapter.isDialerRoleAvailable();
        if (!mIsDialerRoleAvailable) return;

        setUpControl();
        mICtsThirdPartyInCallServiceControl.resetLatchForServiceBound(false);
        mThirdPartyPackageName = CtsThirdPtyDialerInCallServiceControl.class.getPackage().getName();
        mPreviousRoleHolder = mCtsRoleManagerAdapter.getRoleHolder(RoleManager.ROLE_DIALER)
            .stream()
            .findFirst().orElse(null);
    }

    @Override
    public void tearDown() throws Exception {
        if (mIsDialerRoleAvailable) {
            mICtsThirdPartyInCallServiceControl.resetCalls();
            if (mPreviousRoleHolder == null) {
                mCtsRoleManagerAdapter.removeDialerRoleHolder(mThirdPartyPackageName);
            } else {
                mCtsRoleManagerAdapter.setDialerRoleHolder(mPreviousRoleHolder);
            }
        }
        super.tearDown();
    }

    /**
     * Verifies that the {@link Manifest.permission#READ_VOICEMAIL} and
     * {@link Manifest.permission#WRITE_VOICEMAIL} permission is autogranted to
     * the app holding the {@link RoleManager#ROLE_DIALER} role.
     * @throws Exception
     */
    public void testPermissionGranted() throws Exception {
        if (!mIsDialerRoleAvailable) return;

        assertFalse(mICtsThirdPartyInCallServiceControl.checkPermissionGrant(
                Manifest.permission.READ_VOICEMAIL));
        assertFalse(mICtsThirdPartyInCallServiceControl.checkPermissionGrant(
                Manifest.permission.WRITE_VOICEMAIL));
        mCtsRoleManagerAdapter.setDialerRoleHolder(mThirdPartyPackageName);
        assertTrue(mICtsThirdPartyInCallServiceControl.checkPermissionGrant(
                Manifest.permission.READ_VOICEMAIL));
        assertTrue(mICtsThirdPartyInCallServiceControl.checkPermissionGrant(
                Manifest.permission.WRITE_VOICEMAIL));
    }

    private void setUpControl() throws InterruptedException {
        Intent bindIntent = new Intent(CtsThirdPartyInCallServiceControl.CONTROL_INTERFACE_ACTION);
        // mContext is android.telecom.cts, which doesn't include thirdptyincallservice.
        ComponentName controlComponentName =
                ComponentName.createRelative(
                        CtsThirdPtyDialerInCallServiceControl.class.getPackage().getName(),
                        CtsThirdPtyDialerInCallServiceControl.class.getName());

        bindIntent.setComponent(controlComponentName);
        final CountDownLatch bindLatch = new CountDownLatch(1);
        boolean success = mContext.bindService(bindIntent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.i(TAG, "Service Connected: " + name);
                mICtsThirdPartyInCallServiceControl =
                        ICtsThirdPartyInCallServiceControl.Stub.asInterface(service);
                bindLatch.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mICtsThirdPartyInCallServiceControl = null;
            }
        }, Context.BIND_AUTO_CREATE);
        if (!success) {
            fail("Failed to get control interface -- bind error");
        }
        bindLatch.await(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
}
