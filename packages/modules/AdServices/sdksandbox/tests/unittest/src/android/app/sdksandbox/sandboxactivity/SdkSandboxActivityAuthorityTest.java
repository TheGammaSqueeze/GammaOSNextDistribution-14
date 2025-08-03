/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.app.sdksandbox.sandboxactivity;

import static android.app.sdksandbox.sandboxactivity.SdkSandboxActivityAuthority.isSdkSandboxActivityIntent;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.sdkprovider.SdkSandboxActivityHandler;
import android.app.sdksandbox.sdkprovider.SdkSandboxActivityRegistry;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.modules.utils.build.SdkLevel;
import com.android.sdksandbox.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class SdkSandboxActivityAuthorityTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private SdkSandboxActivityRegistry mRegistry;
    private SdkSandboxActivityAuthority mSdkSandboxActivityAuthority;
    private SdkSandboxActivityHandler mHandler;
    private SandboxedSdkContext mSdkContext;

    /** Getting instance of SdkSandboxActivityRegistry and mock the SDK Context. */
    @Before
    public void setUp() {
        assumeTrue(SdkLevel.isAtLeastU());
        mRegistry = SdkSandboxActivityRegistry.getInstance();
        mSdkSandboxActivityAuthority = SdkSandboxActivityAuthority.getInstance();
        mHandler = Mockito.spy(activity -> {});
        mSdkContext = Mockito.mock(SandboxedSdkContext.class);
    }

    /** Ensure to unregister registered handler. */
    @After
    public void tearDown() {
        if (SdkLevel.isAtLeastU()) {
            try {
                mRegistry.unregister(mHandler);
            } catch (IllegalArgumentException e) {
                // safe to ignore, it is already unregistered
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SANDBOX_ACTIVITY_SDK_BASED_CONTEXT)
    public void testSdkSandboxActivityIntent() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final String sandboxPackageName = context.getPackageManager().getSdkSandboxPackageName();

        assertThat(isSdkSandboxActivityIntent(context, new Intent())).isFalse();
        assertThat(
                        isSdkSandboxActivityIntent(
                                context,
                                new Intent(SdkSandboxManager.ACTION_START_SANDBOXED_ACTIVITY)))
                .isTrue();
        assertThat(isSdkSandboxActivityIntent(context, new Intent().setPackage(sandboxPackageName)))
                .isTrue();
        assertThat(
                        isSdkSandboxActivityIntent(
                                context,
                                new Intent()
                                        .setComponent(new ComponentName(sandboxPackageName, ""))))
                .isTrue();
    }

    /** Ensure the returned instance ActivityContextInfo has the expected fields. */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SANDBOX_ACTIVITY_SDK_BASED_CONTEXT)
    public void testGetActivityContextInfo() {
        final IBinder token = mRegistry.register(mSdkContext, mHandler);
        Intent intent = buildSandboxActivityIntent(token);

        Mockito.when(mSdkContext.isCustomizedSdkContextEnabled()).thenReturn(true);

        assertThat(mSdkSandboxActivityAuthority.getActivityContextInfo(intent))
                .isInstanceOf(ActivityContextInfo.class);
    }

    /**
     * Ensure that the handler should be registered before retrieving the ActivityContextInfo for
     * it.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SANDBOX_ACTIVITY_SDK_BASED_CONTEXT)
    public void testGetSdkSandboxActivityAuthorityFailForNonRegisteredHandlers() {
        final Intent intent = buildSandboxActivityIntent(new Binder());
        Mockito.when(mSdkContext.isCustomizedSdkContextEnabled()).thenReturn(true);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mSdkSandboxActivityAuthority.getActivityContextInfo(intent));
        assertThat(
                        exception
                                .getMessage()
                                .contains(
                                        "There is no registered SdkSandboxActivityHandler for the"
                                                + " passed intent"))
                .isTrue();
    }

    /**
     * Ensure that the customized SDK flag has to be enabled before retrieving the
     * ActivityContextInfo instance.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SANDBOX_ACTIVITY_SDK_BASED_CONTEXT)
    public void testGetSdkSandboxActivityAuthorityFailIfCustomizedSdkFlagIsDisabled() {
        final IBinder token = mRegistry.register(mSdkContext, mHandler);
        Intent intent = buildSandboxActivityIntent(token);

        Mockito.when(mSdkContext.isCustomizedSdkContextEnabled()).thenReturn(false);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> mSdkSandboxActivityAuthority.getActivityContextInfo(intent));
        assertThat(exception.getMessage()).isEqualTo("Customized SDK flag is disabled.");
    }

    private Intent buildSandboxActivityIntent(IBinder token) {
        final Intent intent = new Intent();
        final Bundle extras = new Bundle();
        extras.putBinder("android.app.sdksandbox.extra.SANDBOXED_ACTIVITY_HANDLER", token);
        intent.putExtras(extras);
        return intent;
    }
}
