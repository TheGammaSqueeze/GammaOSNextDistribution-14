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
import static android.os.Process.myUserHandle;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.KeyguardManager;
import android.app.KeyguardManager.WeakEscrowTokenActivatedListener;
import android.app.KeyguardManager.WeakEscrowTokenRemovedListener;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class KeyguardWeakEscrowTokenTest {
    private static final String TEST_PIN1 = "1234";
    private static final String TEST_PIN2 = "4321";

    private final byte[] mTestToken1 = "test_token1".getBytes(StandardCharsets.UTF_8);
    private final byte[] mTestToken2 = "test_token2".getBytes(StandardCharsets.UTF_8);
    private final Executor mTestExecutor = Runnable::run;

    private Context mContext;
    private UserHandle mTestUser;
    private KeyguardManager mKeyguardManager;
    private WeakEscrowTokenActivatedListener mMockTokenActivatedListener;
    private WeakEscrowTokenRemovedListener mMockTokenRemovedListener;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mKeyguardManager = mContext.getSystemService(KeyguardManager.class);
        mTestUser = myUserHandle();
        mMockTokenActivatedListener = mock(WeakEscrowTokenActivatedListener.class);
        mMockTokenRemovedListener = mock(WeakEscrowTokenRemovedListener.class);
    }

    @After
    public void cleanUp() throws IOException {
        if (!isAutomotiveDevice() || mKeyguardManager == null) return;
        runWithShellPermissionIdentity(() -> {
            try {
                mKeyguardManager
                        .unregisterWeakEscrowTokenRemovedListener(mMockTokenRemovedListener);
            } catch (IllegalArgumentException e) {
                // Mock listener was not registered before.
            }
        }, MANAGE_WEAK_ESCROW_TOKEN);
        removeLockCredential(TEST_PIN1);
        removeLockCredential(TEST_PIN2);
    }

    @Test
    public void testAddWeakEscrowToken_noCredentialSetUp_tokenActivatedImmediately() {
        assumeIsAutomotiveDevice();
        assumeKeyguardManagerAvailable();

        runWithShellPermissionIdentity(() -> {
            long handle = mKeyguardManager.addWeakEscrowToken(mTestToken1, mTestUser, mTestExecutor,
                    mMockTokenActivatedListener);
            boolean isActive = mKeyguardManager.isWeakEscrowTokenActive(handle, mTestUser);
            boolean isValid = mKeyguardManager.isWeakEscrowTokenValid(handle, mTestToken1,
                    mTestUser);

            assertThat(isActive).isTrue();
            assertThat(isValid).isTrue();
            verify(mMockTokenActivatedListener).onWeakEscrowTokenActivated(eq(handle),
                    eq(mTestUser));

            // Clean up
            mKeyguardManager.removeWeakEscrowToken(handle, mTestUser);
        }, MANAGE_WEAK_ESCROW_TOKEN);
    }

    @Test
    public void testAddWeakEscrowToken_hasCredentialSetUp_tokenActivatedAfterVerification() {
        assumeIsAutomotiveDevice();
        assumeKeyguardManagerAvailable();

        runWithShellPermissionIdentity(() -> {
            setLockCredential(TEST_PIN1);
            long handle = mKeyguardManager.addWeakEscrowToken(mTestToken1, mTestUser, mTestExecutor,
                    mMockTokenActivatedListener);
            boolean isActiveBeforeVerification = mKeyguardManager.isWeakEscrowTokenActive(handle,
                    mTestUser);
            verifyCredential(TEST_PIN1);
            boolean isActiveAfterVerification = mKeyguardManager.isWeakEscrowTokenActive(handle,
                    mTestUser);

            assertThat(isActiveBeforeVerification).isFalse();
            assertThat(isActiveAfterVerification).isTrue();

            // Clean up
            mKeyguardManager.removeWeakEscrowToken(handle, mTestUser);
        }, MANAGE_WEAK_ESCROW_TOKEN);
    }

    @Test
    public void testRemoveWeakEscrowToken_tokenRemoved() {
        assumeIsAutomotiveDevice();
        assumeKeyguardManagerAvailable();

        runWithShellPermissionIdentity(() -> {
            long handle = mKeyguardManager.addWeakEscrowToken(mTestToken1, mTestUser, mTestExecutor,
                    mMockTokenActivatedListener);
            boolean isActiveBeforeRemove = mKeyguardManager.isWeakEscrowTokenActive(handle,
                    mTestUser);
            mKeyguardManager.removeWeakEscrowToken(handle, mTestUser);
            boolean isActiveAfterRemove = mKeyguardManager.isWeakEscrowTokenActive(handle,
                    mTestUser);

            assertThat(isActiveBeforeRemove).isTrue();
            assertThat(isActiveAfterRemove).isFalse();
        }, MANAGE_WEAK_ESCROW_TOKEN);
    }

    @Test
    public void testRegisterWeakEscrowTokenRemovedListener_listenerRegistered() {
        assumeIsAutomotiveDevice();
        assumeKeyguardManagerAvailable();

        runWithShellPermissionIdentity(() -> {
            mKeyguardManager.registerWeakEscrowTokenRemovedListener(mTestExecutor,
                    mMockTokenRemovedListener);
            long handle = mKeyguardManager.addWeakEscrowToken(mTestToken1, mTestUser,
                    mTestExecutor, mMockTokenActivatedListener);
            mKeyguardManager.removeWeakEscrowToken(handle, mTestUser);

            verify(mMockTokenRemovedListener).onWeakEscrowTokenRemoved(eq(handle), eq(mTestUser));
        }, MANAGE_WEAK_ESCROW_TOKEN);
    }

    @Test
    public void testUnregisterWeakEscrowTokenRemovedListener_listenerUnregistered() {
        assumeIsAutomotiveDevice();
        assumeKeyguardManagerAvailable();

        runWithShellPermissionIdentity(() -> {
            mKeyguardManager.registerWeakEscrowTokenRemovedListener(mTestExecutor,
                    mMockTokenRemovedListener);
            long handle0 = mKeyguardManager.addWeakEscrowToken(mTestToken1, mTestUser,
                    mTestExecutor, mMockTokenActivatedListener);
            long handle1 = mKeyguardManager.addWeakEscrowToken(mTestToken2, mTestUser,
                    mTestExecutor, mMockTokenActivatedListener);
            mKeyguardManager.removeWeakEscrowToken(handle0, mTestUser);
            mKeyguardManager.unregisterWeakEscrowTokenRemovedListener(mMockTokenRemovedListener);
            mKeyguardManager.removeWeakEscrowToken(handle1, mTestUser);

            verify(mMockTokenRemovedListener).onWeakEscrowTokenRemoved(eq(handle0), eq(mTestUser));
            verify(mMockTokenRemovedListener, never()).onWeakEscrowTokenRemoved(eq(handle1),
                    eq(mTestUser));
        }, MANAGE_WEAK_ESCROW_TOKEN);
    }

    @Test
    public void testWeakEscrowTokenRemovedWhenCredentialChanged() {
        assumeIsAutomotiveDevice();
        assumeKeyguardManagerAvailable();

        runWithShellPermissionIdentity(() -> {
            long handle = mKeyguardManager.addWeakEscrowToken(mTestToken1, mTestUser, mTestExecutor,
                    mMockTokenActivatedListener);
            setLockCredential(TEST_PIN1);
            boolean isActiveBeforeCredentialChange = mKeyguardManager
                    .isWeakEscrowTokenActive(handle, mTestUser);
            updateLockCredential(TEST_PIN1, TEST_PIN2);
            boolean isActiveAfterCredentialChange = mKeyguardManager.isWeakEscrowTokenActive(handle,
                    mTestUser);

            assertThat(isActiveBeforeCredentialChange).isTrue();
            assertThat(isActiveAfterCredentialChange).isFalse();
        }, MANAGE_WEAK_ESCROW_TOKEN);
    }

    @Test
    public void testAutoEscrowTokenRemovedWhenCredentialRemoved() {
        assumeIsAutomotiveDevice();
        assumeKeyguardManagerAvailable();

        runWithShellPermissionIdentity(() -> {
            long handle = mKeyguardManager.addWeakEscrowToken(mTestToken1, mTestUser, mTestExecutor,
                    mMockTokenActivatedListener);
            setLockCredential(TEST_PIN1);
            boolean isActiveBeforeCredentialRemove = mKeyguardManager
                    .isWeakEscrowTokenActive(handle, mTestUser);
            removeLockCredential(TEST_PIN1);
            boolean isActiveAfterCredentialRemove = mKeyguardManager.isWeakEscrowTokenActive(handle,
                    mTestUser);

            assertThat(isActiveBeforeCredentialRemove).isTrue();
            assertThat(isActiveAfterCredentialRemove).isFalse();
        }, MANAGE_WEAK_ESCROW_TOKEN);
    }

    private void assumeIsAutomotiveDevice() {
        assumeTrue("Test skipped because it's not running on automotive device.",
                isAutomotiveDevice());
    }

    private boolean isAutomotiveDevice() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private void assumeKeyguardManagerAvailable() {
        assumeFalse("Test skipped because KeyguardManager is not available.",
                mKeyguardManager == null);
    }

    private static void removeLockCredential(String oldCredential) throws IOException {
        runShellCommand(InstrumentationRegistry.getInstrumentation(), "locksettings clear --old "
                + oldCredential);
    }

    private static void updateLockCredential(String oldCredential, String credential)
            throws IOException {
        runShellCommand(InstrumentationRegistry.getInstrumentation(),
                String.format("locksettings set-pin --old %s %s", oldCredential, credential));
    }

    private static void setLockCredential(String credential) throws IOException {
        runShellCommand(InstrumentationRegistry.getInstrumentation(), "locksettings set-pin "
                + credential);
    }

    private static void verifyCredential(String credential) throws IOException {
        runShellCommand(InstrumentationRegistry.getInstrumentation(), "locksettings verify --old "
                + credential);
    }
}
