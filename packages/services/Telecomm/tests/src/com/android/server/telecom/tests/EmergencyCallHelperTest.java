/*
 * Copyright (C) 2022 Tc
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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.telecom.PhoneAccountHandle;

import androidx.test.filters.SmallTest;

import com.android.server.telecom.Call;
import com.android.server.telecom.DefaultDialerCache;
import com.android.server.telecom.EmergencyCallHelper;
import com.android.server.telecom.Timeouts;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class EmergencyCallHelperTest extends TelecomTestCase {
  private static final String SYSTEM_DIALER_PACKAGE = "abc.xyz";
  private EmergencyCallHelper mEmergencyCallHelper;
  @Mock
  private PackageManager mPackageManager;
  @Mock
  private DefaultDialerCache mDefaultDialerCache;
  @Mock
  private Timeouts.Adapter mTimeoutsAdapter;
  @Mock
  private UserHandle mUserHandle;
  @Mock
  private Call mCall;
  @Mock private PhoneAccountHandle mPhoneAccountHandle;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);
    mContext = mComponentContextFixture.getTestDouble().getApplicationContext();
    when(mContext.getPackageManager()).thenReturn(mPackageManager);
    mEmergencyCallHelper = new EmergencyCallHelper(mContext, mDefaultDialerCache,
        mTimeoutsAdapter);
    when(mDefaultDialerCache.getSystemDialerApplication()).thenReturn(SYSTEM_DIALER_PACKAGE);

    //start with no perms
    when(mPackageManager.checkPermission(eq(ACCESS_BACKGROUND_LOCATION),
        eq(SYSTEM_DIALER_PACKAGE))).thenReturn(
        PackageManager.PERMISSION_DENIED);

    when(mPackageManager.checkPermission(eq(ACCESS_FINE_LOCATION),
        eq(SYSTEM_DIALER_PACKAGE))).thenReturn(
        PackageManager.PERMISSION_DENIED);

    when(mCall.isEmergencyCall()).thenReturn(true);
    when(mContext.getResources().getBoolean(R.bool.grant_location_permission_enabled)).thenReturn(
        true);
    when(mTimeoutsAdapter.getEmergencyCallbackWindowMillis(any(ContentResolver.class))).thenReturn(
            5000L);
  }

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  private void verifyRevokeInvokedFor(String perm) {
    verify(mPackageManager, times(1)).revokeRuntimePermission(eq(SYSTEM_DIALER_PACKAGE),
        eq(perm), eq(mUserHandle));
  }

  private void verifyRevokeNotInvokedFor(String perm) {
    verify(mPackageManager, never()).revokeRuntimePermission(eq(SYSTEM_DIALER_PACKAGE),
        eq(perm), eq(mUserHandle));
  }

  private void verifyGrantInvokedFor(String perm) {
    verify(mPackageManager, times(1)).grantRuntimePermission(
        nullable(String.class),
        eq(perm), eq(mUserHandle));
  }

  private void verifyGrantNotInvokedFor(String perm) {
    verify(mPackageManager, never()).grantRuntimePermission(
        nullable(String.class),
        eq(perm), eq(mUserHandle));
  }

  @SmallTest
  @Test
  public void testEmergencyCallHelperRevokesOnlyFinePermAfterBackgroundPermGrantException() {

    //granting of background location perm fails
    doThrow(new SecurityException()).when(mPackageManager).grantRuntimePermission(
        nullable(String.class),
        eq(ACCESS_BACKGROUND_LOCATION), eq(mUserHandle));

    mEmergencyCallHelper.maybeGrantTemporaryLocationPermission(mCall, mUserHandle);
    mEmergencyCallHelper.maybeRevokeTemporaryLocationPermission();

    verifyGrantInvokedFor(ACCESS_BACKGROUND_LOCATION);
    verifyGrantInvokedFor(ACCESS_FINE_LOCATION);
    //only fine perm should be revoked
    verifyRevokeNotInvokedFor(ACCESS_BACKGROUND_LOCATION);
    verifyRevokeInvokedFor(ACCESS_FINE_LOCATION);
  }

  @SmallTest
  @Test
  public void testEmergencyCallHelperRevokesOnlyBackgroundPermAfterFinePermGrantException() {

    //granting of fine location perm fails
    doThrow(new SecurityException()).when(mPackageManager).grantRuntimePermission(
        nullable(String.class),
        eq(ACCESS_FINE_LOCATION), eq(mUserHandle));

    mEmergencyCallHelper.maybeGrantTemporaryLocationPermission(mCall, mUserHandle);
    mEmergencyCallHelper.maybeRevokeTemporaryLocationPermission();

    //only background perm should be revoked
    verifyGrantInvokedFor(ACCESS_BACKGROUND_LOCATION);
    verifyGrantInvokedFor(ACCESS_FINE_LOCATION);
    //only fine perm should be revoked
    verifyRevokeNotInvokedFor(ACCESS_FINE_LOCATION);
    verifyRevokeInvokedFor(ACCESS_BACKGROUND_LOCATION);
  }

  @SmallTest
  @Test
  public void testNoPermGrantWhenPackageHasAllPerms() {

    when(mPackageManager.checkPermission(eq(ACCESS_BACKGROUND_LOCATION),
        eq(SYSTEM_DIALER_PACKAGE))).thenReturn(
        PackageManager.PERMISSION_GRANTED);

    when(mPackageManager.checkPermission(eq(ACCESS_FINE_LOCATION),
        eq(SYSTEM_DIALER_PACKAGE))).thenReturn(
        PackageManager.PERMISSION_GRANTED);

    mEmergencyCallHelper.maybeGrantTemporaryLocationPermission(mCall, mUserHandle);
    mEmergencyCallHelper.maybeRevokeTemporaryLocationPermission();

    //permissions should neither be granted or revoked
    verifyGrantNotInvokedFor(ACCESS_BACKGROUND_LOCATION);
    verifyGrantNotInvokedFor(ACCESS_FINE_LOCATION);
    verifyRevokeNotInvokedFor(ACCESS_BACKGROUND_LOCATION);
    verifyRevokeNotInvokedFor(ACCESS_FINE_LOCATION);
  }

  @SmallTest
  @Test
  public void testNoPermGrantForNonEmergencyCall() {

    when(mCall.isEmergencyCall()).thenReturn(false);

    mEmergencyCallHelper.maybeGrantTemporaryLocationPermission(mCall, mUserHandle);
    mEmergencyCallHelper.maybeRevokeTemporaryLocationPermission();

    //permissions should neither be granted or revoked
    verifyGrantNotInvokedFor(ACCESS_BACKGROUND_LOCATION);
    verifyGrantNotInvokedFor(ACCESS_FINE_LOCATION);
    verifyRevokeNotInvokedFor(ACCESS_BACKGROUND_LOCATION);
    verifyRevokeNotInvokedFor(ACCESS_FINE_LOCATION);
  }

  @SmallTest
  @Test
  public void testNoPermGrantWhenGrantLocationPermissionIsFalse() {

    when(mContext.getResources().getBoolean(R.bool.grant_location_permission_enabled)).thenReturn(
        false);

    mEmergencyCallHelper.maybeGrantTemporaryLocationPermission(mCall, mUserHandle);
    mEmergencyCallHelper.maybeRevokeTemporaryLocationPermission();

    //permissions should neither be granted or revoked
    verifyGrantNotInvokedFor(ACCESS_BACKGROUND_LOCATION);
    verifyGrantNotInvokedFor(ACCESS_FINE_LOCATION);
    verifyRevokeNotInvokedFor(ACCESS_BACKGROUND_LOCATION);
    verifyRevokeNotInvokedFor(ACCESS_FINE_LOCATION);
  }

  @SmallTest
  @Test
  public void testOnlyFineLocationPermIsGrantedAndRevoked() {

    when(mPackageManager.checkPermission(eq(ACCESS_BACKGROUND_LOCATION),
        eq(SYSTEM_DIALER_PACKAGE))).thenReturn(
        PackageManager.PERMISSION_GRANTED);

    mEmergencyCallHelper.maybeGrantTemporaryLocationPermission(mCall, mUserHandle);
    mEmergencyCallHelper.maybeRevokeTemporaryLocationPermission();

    //permissions should neither be granted or revoked
    verifyGrantNotInvokedFor(ACCESS_BACKGROUND_LOCATION);
    verifyGrantInvokedFor(ACCESS_FINE_LOCATION);
    verifyRevokeNotInvokedFor(ACCESS_BACKGROUND_LOCATION);
    verifyRevokeInvokedFor(ACCESS_FINE_LOCATION);
  }

  @SmallTest
  @Test
  public void testOnlyBackgroundLocationPermIsGrantedAndRevoked() {

    when(mPackageManager.checkPermission(eq(ACCESS_FINE_LOCATION),
        eq(SYSTEM_DIALER_PACKAGE))).thenReturn(
        PackageManager.PERMISSION_GRANTED);

    mEmergencyCallHelper.maybeGrantTemporaryLocationPermission(mCall, mUserHandle);
    mEmergencyCallHelper.maybeRevokeTemporaryLocationPermission();

    //permissions should neither be granted or revoked
    verifyGrantNotInvokedFor(ACCESS_FINE_LOCATION);
    verifyGrantInvokedFor(ACCESS_BACKGROUND_LOCATION);
    verifyRevokeNotInvokedFor(ACCESS_FINE_LOCATION);
    verifyRevokeInvokedFor(ACCESS_BACKGROUND_LOCATION);
  }

  @SmallTest
  @Test
  public void testIsLastOutgoingEmergencyCallPAH() {
    PhoneAccountHandle dummyHandle = new PhoneAccountHandle(new ComponentName("pkg", "cls"), "foo");
    long currentTimeMillis = System.currentTimeMillis();
    mEmergencyCallHelper.setLastOutgoingEmergencyCallPAH(mPhoneAccountHandle);
    mEmergencyCallHelper.setLastOutgoingEmergencyCallTimestampMillis(currentTimeMillis);

    // Verify that ECBM is active on mPhoneAccountHandle.
    assertTrue(mEmergencyCallHelper.isLastOutgoingEmergencyCallPAH(mPhoneAccountHandle));
    assertFalse(mEmergencyCallHelper.isLastOutgoingEmergencyCallPAH(dummyHandle));

    // Expire ECBM and verify that mPhoneAccountHandle is no longer supported for ECBM.
    mEmergencyCallHelper.setLastOutgoingEmergencyCallTimestampMillis(currentTimeMillis/2);
    assertFalse(mEmergencyCallHelper.isLastOutgoingEmergencyCallPAH(mPhoneAccountHandle));
  }
}
