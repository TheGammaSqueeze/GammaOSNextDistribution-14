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
package com.android.server.adservices;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.util.Log;

import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.server.LocalManagerRegistry;
import com.android.server.sdksandbox.SdkSandboxManagerLocal;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

public final class AdServicesManagerServiceLifeCycleTest {

    private static final String TAG = AdServicesManagerServiceLifeCycleTest.class.getSimpleName();

    @Mock private Context mContext;
    @Mock private AdServicesManagerService mService;
    @Mock private SdkSandboxManagerLocal mSdkSandboxManagerLocal;

    // Need to use a spy to mock publishBinderService()
    private AdServicesManagerService.Lifecycle mSpyLifecycle;

    @Rule
    public final AdServicesExtendedMockitoRule extendedMockito =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(LocalManagerRegistry.class)
                    .build();

    @Before
    public void setUp() {
        mSpyLifecycle = spy(new AdServicesManagerService.Lifecycle(mContext, mService));
        doNothing().when(mSpyLifecycle).publishBinderService();
        mockGetLocalManager(SdkSandboxManagerLocal.class, mSdkSandboxManagerLocal);
    }

    @Test
    public void testOnStart_noSdkSandboxManagerLocal() {
        mockGetLocalManagerNotFound(SdkSandboxManagerLocal.class);

        assertThrows(IllegalStateException.class, () -> mSpyLifecycle.onStart());
    }

    @Test
    public void testOnStart_binderRegistrationFails() {
        doThrow(new RuntimeException("D'OH!")).when(mSpyLifecycle).publishBinderService();

        mSpyLifecycle.onStart();

        verifyBinderPublished();
        verifyAdServiceRegisteredOnSdkManager(/* published= */ false);
    }

    @Test
    public void testOnStart() {
        mSpyLifecycle.onStart();

        verifyBinderPublished();
        verifyAdServiceRegisteredOnSdkManager(/* published= */ true);
    }

    private static <T> void mockGetLocalManager(Class<T> managerClass, T manager) {
        Log.v(TAG, "mockGetLocalManager(" + managerClass + ", " + manager + ")");
        doReturn(manager).when(() -> LocalManagerRegistry.getManager(managerClass));
    }

    private static void mockGetLocalManagerNotFound(Class<?> managerClass) {
        Log.v(TAG, "mockGetLocalManagerNotFound(" + managerClass + ")");
        doReturn(null).when(() -> LocalManagerRegistry.getManager(managerClass));
    }

    private void verifyAdServiceRegisteredOnSdkManager(boolean published) {
        verify(mSdkSandboxManagerLocal).registerAdServicesManagerService(mService, published);
    }

    private void verifyBinderPublished() {
        verify(mSpyLifecycle).publishBinderService();
    }
}
