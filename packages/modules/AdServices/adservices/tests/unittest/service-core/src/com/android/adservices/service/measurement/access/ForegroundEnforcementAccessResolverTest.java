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

package com.android.adservices.service.measurement.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.adservices.common.AdServicesStatusUtils;
import android.content.Context;

import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.compat.ProcessCompatUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ForegroundEnforcementAccessResolverTest {

    private static final String ERROR_MESSAGE = "Measurement API was not called from foreground.";

    @Mock private Context mContext;
    @Mock private AppImportanceFilter mAppImportanceFilter;

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(ErrorLogUtil.class)
                    .spyStatic(ProcessCompatUtils.class)
                    .build();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testIsAllowed_flagEnforced_assertCallerForeground() {
        // Execute
        new ForegroundEnforcementAccessResolver(
                        1, 1, mAppImportanceFilter, () -> /* enforced */ true)
                .getAccessInfo(mContext);

        // Validation
        verify(mAppImportanceFilter, times(1))
                .assertCallerIsInForeground(anyInt(), anyInt(), any());
    }

    @Test
    public void testIsAllowed_flagNotEnforced_dontAssertCallerForeground() {
        // Execute
        new ForegroundEnforcementAccessResolver(
                        1, 1, mAppImportanceFilter, () -> /* not enforced */ false)
                .getAccessInfo(mContext);

        // Validation
        verify(mAppImportanceFilter, never()).assertCallerIsInForeground(anyInt(), anyInt(), any());
    }

    @Test
    public void testIsAllowed_callerNotSandbox_assertCallerForeground() {

            // Not Sandbox
            ExtendedMockito.doReturn(false)
                    .when(() -> ProcessCompatUtils.isSdkSandboxUid(anyInt()));

        // Execute
        new ForegroundEnforcementAccessResolver(
                        1, 1, mAppImportanceFilter, () -> /* enforced */ true)
                .getAccessInfo(mContext);

            // Validation
            verify(mAppImportanceFilter, times(1))
                    .assertCallerIsInForeground(anyInt(), anyInt(), any());
    }

    @Test
    public void testIsAllowed_callerIsSandbox_dontAssertCallerForeground() {
            // Is Sandbox
            ExtendedMockito.doReturn(true).when(() -> ProcessCompatUtils.isSdkSandboxUid(anyInt()));

        // Execute
        new ForegroundEnforcementAccessResolver(
                        1, 1, mAppImportanceFilter, () -> /* enforced */ true)
                .getAccessInfo(mContext);

            // Validation
            verify(mAppImportanceFilter, never())
                    .assertCallerIsInForeground(anyInt(), anyInt(), any());
    }

    @Test
    public void testIsAllowed_isForeground_returnTrue() {
        // Setup
        doNothing()
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(anyInt(), anyInt(), any());

        // Execute
        AccessInfo result =
                new ForegroundEnforcementAccessResolver(
                                1, 1, mAppImportanceFilter, () -> /* enforced */ true)
                        .getAccessInfo(mContext);

        // Validation
        assertTrue(result.isAllowedAccess());
        verify(mAppImportanceFilter, times(1))
                .assertCallerIsInForeground(anyInt(), anyInt(), any());
    }

    @Test
    public void testIsAllowed_isNotForeground_returnFalse() {
        // Setup
        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(anyInt(), anyInt(), any());

        // Execute
        ForegroundEnforcementAccessResolver foregroundEnforcementAccessResolver =
                new ForegroundEnforcementAccessResolver(
                        1, 1, mAppImportanceFilter, () -> /* enforced */ true);
        final AccessInfo result = foregroundEnforcementAccessResolver.getAccessInfo(mContext);

        // Validation
        assertFalse(result.isAllowedAccess());
        assertEquals(
                AdServicesStatusUtils.FAILURE_REASON_FOREGROUND_APP_NOT_IN_FOREGROUND,
                result.getDeniedAccessReason());
        verify(mAppImportanceFilter, times(1))
                .assertCallerIsInForeground(anyInt(), anyInt(), any());
    }

    @Test
    public void testIsAllowed_CatchesAllExceptions_returnsFalse() {
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        // Setup
        doThrow(new SecurityException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(anyInt(), anyInt(), any());

        // Execute
        ForegroundEnforcementAccessResolver foregroundEnforcementAccessResolver =
                new ForegroundEnforcementAccessResolver(
                        1, 1, mAppImportanceFilter, () -> /* enforced */ true);
        final AccessInfo result = foregroundEnforcementAccessResolver.getAccessInfo(mContext);

        // Validation
        assertFalse(result.isAllowedAccess());
        assertEquals(
                AdServicesStatusUtils.FAILURE_REASON_FOREGROUND_ASSERTION_EXCEPTION,
                result.getDeniedAccessReason());
        verify(mAppImportanceFilter, times(1))
                .assertCallerIsInForeground(anyInt(), anyInt(), any());
    }

    @Test
    public void testGetErrorMessage() {
        assertEquals(
                ERROR_MESSAGE,
                new ForegroundEnforcementAccessResolver(1, 1, mAppImportanceFilter, () -> true)
                        .getErrorMessage());
    }

    @Test
    public void testGetErrorStatusCode() {
        assertEquals(
                AdServicesStatusUtils.STATUS_BACKGROUND_CALLER,
                new ForegroundEnforcementAccessResolver(1, 1, mAppImportanceFilter, () -> true)
                        .getErrorStatusCode());
    }
}
