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

package android.app.cts;

import static android.Manifest.permission.MANAGE_WEAK_ESCROW_TOKEN;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.mockito.Mockito.mock;

import android.app.KeyguardManager;
import android.app.KeyguardManager.WeakEscrowTokenActivatedListener;
import android.app.KeyguardManager.WeakEscrowTokenRemovedListener;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class WeakEscrowTokenAutoFeatureTest {
    private static final UserHandle TEST_USER = UserHandle.of(10);
    private static final long TEST_HANDLE = 10L;

    private final byte[] mTestToken = "test_token".getBytes(StandardCharsets.UTF_8);
    private final Executor mTestExecutor = Runnable::run;

    private Context mContext;
    private KeyguardManager mKeyguardManager;
    private WeakEscrowTokenActivatedListener mMockTokenActivatedListener;
    private WeakEscrowTokenRemovedListener mMockTokenRemovedListener;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mKeyguardManager = mContext.getSystemService(KeyguardManager.class);
        mMockTokenActivatedListener = mock(WeakEscrowTokenActivatedListener.class);
        mMockTokenRemovedListener = mock(WeakEscrowTokenRemovedListener.class);
    }

    @Test
    public void testAddWeakEscrowToken() {
        assumeIsNotAutomotiveDevice();
        assumeKeyguardManagerAvailable();
        runWithShellPermissionIdentity(() ->
                assertThrows(IllegalArgumentException.class, () ->
                        mKeyguardManager.addWeakEscrowToken(mTestToken, TEST_USER, mTestExecutor,
                                mMockTokenActivatedListener)),
                MANAGE_WEAK_ESCROW_TOKEN);
    }

    @Test
    public void testRemoveWeakEscrowToken() {
        assumeIsNotAutomotiveDevice();
        assumeKeyguardManagerAvailable();
        runWithShellPermissionIdentity(() ->
                        assertThrows(IllegalArgumentException.class, () ->
                                mKeyguardManager.removeWeakEscrowToken(TEST_HANDLE, TEST_USER)),
                MANAGE_WEAK_ESCROW_TOKEN);
    }

    @Test
    public void testIsWeakEscrowTokenActive() {
        assumeIsNotAutomotiveDevice();
        assumeKeyguardManagerAvailable();
        runWithShellPermissionIdentity(() ->
                        assertThrows(IllegalArgumentException.class, () ->
                                mKeyguardManager.isWeakEscrowTokenActive(TEST_HANDLE, TEST_USER)),
                MANAGE_WEAK_ESCROW_TOKEN);
    }

    @Test
    public void testIsWeakEscrowTokenValid() {
        assumeIsNotAutomotiveDevice();
        assumeKeyguardManagerAvailable();
        runWithShellPermissionIdentity(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> mKeyguardManager.isWeakEscrowTokenValid(TEST_HANDLE, mTestToken,
                                TEST_USER)),
                MANAGE_WEAK_ESCROW_TOKEN);
    }

    @Test
    public void testRegisterWeakEscrowTokenRemovedListener() {
        assumeIsNotAutomotiveDevice();
        assumeKeyguardManagerAvailable();
        runWithShellPermissionIdentity(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> mKeyguardManager.registerWeakEscrowTokenRemovedListener(mTestExecutor,
                                mMockTokenRemovedListener)),
                MANAGE_WEAK_ESCROW_TOKEN);
    }

    private void assumeIsNotAutomotiveDevice() {
        assumeFalse("Test skipped because it's running on automotive device.",
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
    }

    private void assumeKeyguardManagerAvailable() {
        assumeFalse("Test skipped because KeyguardManager is not available.",
                mKeyguardManager == null);
    }
}
