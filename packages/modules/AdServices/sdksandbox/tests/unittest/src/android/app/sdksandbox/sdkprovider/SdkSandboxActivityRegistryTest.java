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

package android.app.sdksandbox.sdkprovider;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.sandboxactivity.ActivityContextInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class SdkSandboxActivityRegistryTest {

    private static final String SDK_NAME = "SDK_NAME";
    private SdkSandboxActivityRegistry mRegistry;
    private SdkSandboxActivityHandler mHandler;
    private SandboxedSdkContext mSdkContext;

    @Before
    public void setUp() {
        assumeTrue(SdkLevel.isAtLeastU());
        mRegistry = SdkSandboxActivityRegistry.getInstance();
        mHandler = Mockito.spy(activity -> {});
        mSdkContext = Mockito.mock(SandboxedSdkContext.class);
        Mockito.when(mSdkContext.getSdkName()).thenReturn(SDK_NAME);
    }

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
    public void testRegisterSdkSandboxActivityHandler() {
        IBinder token1 = mRegistry.register(mSdkContext, mHandler);
        IBinder token2 = mRegistry.register(mSdkContext, mHandler);
        assertThat(token2).isEqualTo(token1);
    }

    @Test
    public void testUnregisterSdkSandboxActivityHandler() {
        IBinder token = mRegistry.register(mSdkContext, mHandler);
        Intent intent = buildSandboxActivityIntent(token);
        mRegistry.unregister(mHandler);
        new Handler(Looper.getMainLooper())
                .runWithScissors(
                        () -> {
                            Activity activity = new Activity();
                            IllegalArgumentException exception =
                                    assertThrows(
                                            IllegalArgumentException.class,
                                            () ->
                                                    mRegistry.notifyOnActivityCreation(
                                                            intent, activity));
                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "There is no registered "
                                                    + "SdkSandboxActivityHandler to notify");
                        },
                        1000);
    }

    @Test
    public void testNotifyOnActivityCreation() {
        IBinder token = mRegistry.register(mSdkContext, mHandler);
        Intent intent = buildSandboxActivityIntent(token);
        new Handler(Looper.getMainLooper())
                .runWithScissors(
                        () -> {
                            Activity activity = new Activity();
                            mRegistry.notifyOnActivityCreation(intent, activity);

                            ArgumentCaptor<Activity> activityArgumentCaptor =
                                    ArgumentCaptor.forClass(Activity.class);
                            Mockito.verify(mHandler)
                                    .onActivityCreated(activityArgumentCaptor.capture());
                            assertThat(activityArgumentCaptor.getValue()).isEqualTo(activity);
                        },
                        1000);
    }

    @Test
    public void testNotifyOnActivityCreationMultipleTimeSucceed() {
        IBinder token = mRegistry.register(mSdkContext, mHandler);
        Intent intent = buildSandboxActivityIntent(token);
        new Handler(Looper.getMainLooper())
                .runWithScissors(
                        () -> {
                            Activity activity = new Activity();
                            mRegistry.notifyOnActivityCreation(intent, activity);
                            mRegistry.notifyOnActivityCreation(intent, activity);
                        },
                        1000);
    }

    @Test
    public void testUnregisterAllHandlersForSdkName() {
        SdkSandboxActivityHandler handler1Sdk1 = activity -> {};
        SdkSandboxActivityHandler handler2Sdk1 = activity -> {};

        // Register SDK1 handlers
        IBinder token1Sdk1 = mRegistry.register(mSdkContext, handler1Sdk1);
        IBinder token2Sdk1 = mRegistry.register(mSdkContext, handler2Sdk1);

        // Before unregistering, registering the same handlers should return the same tokens.
        assertThat(mRegistry.register(mSdkContext, handler1Sdk1)).isEqualTo(token1Sdk1);
        assertThat(mRegistry.register(mSdkContext, handler2Sdk1)).isEqualTo(token2Sdk1);

        // Unregistering SDK1 handlers
        mRegistry.unregisterAllActivityHandlersForSdk(SDK_NAME);

        // Registering SDK1 handlers should return different tokens as they are unregistered.
        assertThat(mRegistry.register(mSdkContext, handler1Sdk1)).isNotEqualTo(token1Sdk1);
        assertThat(mRegistry.register(mSdkContext, handler2Sdk1)).isNotEqualTo(token2Sdk1);
    }

    /**
     * Ensure that ActivityContextInfo returned from the SdkSandboxActivityRegistry has the right
     * fields.
     */
    @Test
    public void testGetActivityContextInfo() {
        final IBinder token = mRegistry.register(mSdkContext, mHandler);
        Intent intent = buildSandboxActivityIntent(token);

        Mockito.when(mSdkContext.isCustomizedSdkContextEnabled()).thenReturn(true);

        ActivityContextInfo contextInfo = mRegistry.getContextInfo(intent);

        assertThat(contextInfo.getContextFlags())
                .isEqualTo(Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        contextInfo.getSdkApplicationInfo();
        Mockito.verify(mSdkContext, Mockito.times(1)).getApplicationInfo();
    }

    /** Ensure that the handler has to be registered for retrieving the ActivityContextInfo . */
    @Test
    public void testGetActivityContextInfoIsNullForNonRegisteredHandlers() {
        final Intent intent = buildSandboxActivityIntent(new Binder());
        Mockito.when(mSdkContext.isCustomizedSdkContextEnabled()).thenReturn(true);

        assertThat(mRegistry.getContextInfo(intent)).isNull();
    }

    /**
     * Test retrieving the SDK context from SdkSandboxActivityRegistry, passing an intent refers to
     * the registered handler.
     */
    @Test
    public void testGetSdkContext() {
        final IBinder token = mRegistry.register(mSdkContext, mHandler);
        Intent intent = buildSandboxActivityIntent(token);

        SandboxedSdkContext sdkContext = mRegistry.getSdkContext(intent);
        assertThat(sdkContext).isEqualTo(mSdkContext);
    }

    /** Ensure that handler has to be registered to retrieve the SDK context. */
    @Test
    public void testGetSdkContextIsNullForUnregisteredIntent() {
        Intent intent = buildSandboxActivityIntent(new Binder());

        SandboxedSdkContext sdkContext = mRegistry.getSdkContext(intent);
        assertThat(sdkContext).isNull();
    }

    /**
     * Ensure that the customized SDK context flag has to be enabled for retrieving the
     * ActivityContextInfo.
     */
    @Test
    public void testGetActivityContextInfoFailIfCustomizedSdkFlagIsDisabled() {
        final IBinder token = mRegistry.register(mSdkContext, mHandler);
        Intent intent = buildSandboxActivityIntent(token);

        Mockito.when(mSdkContext.isCustomizedSdkContextEnabled()).thenReturn(false);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> mRegistry.getContextInfo(intent));
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
