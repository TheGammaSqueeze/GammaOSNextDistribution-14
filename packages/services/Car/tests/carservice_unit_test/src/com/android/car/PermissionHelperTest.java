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
package com.android.car;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.util.Log;

import org.junit.Test;
import org.mockito.Mock;

public final class PermissionHelperTest extends AbstractExtendedMockitoTestCase {

    private static final String TAG = PermissionHelperTest.class.getSimpleName();
    private static final String MESSAGE = "D'OH!";

    private static final String PERMISSION1 = "LicenseToKill";
    private static final String PERMISSION2 = "LicenseToLove";

    @Mock
    private Context mContext;

    public PermissionHelperTest() {
        super(NO_LOG_TAGS);
    }

    @Test
    public void testHasAtLeastOnePermission_none() {
        mockPermission(PERMISSION1, PERMISSION_DENIED);
        mockPermission(PERMISSION2, PERMISSION_DENIED);

        assertWithMessage("has at least %s", PERMISSION2).that(
                PermissionHelper.hasAtLeastOnePermissionGranted(mContext, PERMISSION1,
                        PERMISSION2))
                .isFalse();
    }

    @Test
    public void testHasAtLeastOnePermission_one() {
        mockPermission(PERMISSION1, PERMISSION_DENIED);
        mockPermission(PERMISSION2, PERMISSION_GRANTED);

        assertWithMessage("has at least %s", PERMISSION2).that(
                PermissionHelper.hasAtLeastOnePermissionGranted(mContext, PERMISSION1,
                        PERMISSION2))
                .isTrue();
    }

    @Test
    public void testHasAtLeastOnePermission_both() {
        mockPermission(PERMISSION1, PERMISSION_GRANTED);
        mockPermission(PERMISSION2, PERMISSION_GRANTED);

        assertWithMessage("has at least %s", PERMISSION2).that(
                PermissionHelper.hasAtLeastOnePermissionGranted(mContext, PERMISSION1,
                        PERMISSION2))
                .isTrue();
    }

    @Test
    public void testCheckHasAtLeastOnePermission_none() {
        mockPermission(PERMISSION1, PERMISSION_DENIED);
        mockPermission(PERMISSION2, PERMISSION_DENIED);

        SecurityException exception = assertThrows(SecurityException.class, () -> PermissionHelper
                .checkHasAtLeastOnePermissionGranted(mContext, MESSAGE, PERMISSION1, PERMISSION2));

        assertExceptionMessageContains(exception, MESSAGE);
    }

    @Test
    public void testCheckHasAtLeastOnePermissionGranted_one() {
        mockPermission(PERMISSION1, PERMISSION_DENIED);
        mockPermission(PERMISSION2, PERMISSION_GRANTED);

        PermissionHelper.checkHasAtLeastOnePermissionGranted(mContext, MESSAGE, PERMISSION1,
                PERMISSION2);
    }

    @Test
    public void testCheckHasAtLeastOnePermissionGranted_both() {
        mockPermission(PERMISSION1, PERMISSION_DENIED);
        mockPermission(PERMISSION2, PERMISSION_GRANTED);

        PermissionHelper.checkHasAtLeastOnePermissionGranted(mContext, MESSAGE, PERMISSION1,
                PERMISSION2);
    }

    @Test
    public void testCheckHasDumpPermissionGranted_notGranted() {
        mockPermission(android.Manifest.permission.DUMP, PERMISSION_DENIED);

        SecurityException exception = assertThrows(SecurityException.class,
                () -> PermissionHelper.checkHasDumpPermissionGranted(mContext, MESSAGE));

        assertExceptionMessageContains(exception, MESSAGE);
        assertExceptionMessageContains(exception, android.Manifest.permission.DUMP);
    }

    @Test
    public void testCheckHasDumpPermissionGranted_granted() {
        mockPermission(android.Manifest.permission.DUMP, PERMISSION_GRANTED);

        PermissionHelper.checkHasDumpPermissionGranted(mContext, MESSAGE);
    }

    private void assertExceptionMessageContains(Exception exception, String subString) {
        assertWithMessage("exception (%s) message", exception).that(exception.getMessage())
                .contains(subString);
    }

    private void mockPermission(String permission, int value) {
        Log.d(TAG, "mockHasPermissions(): permission=" + permission
                + ", granted=" + (value == PERMISSION_GRANTED));

        doReturn(value).when(mContext).checkCallingOrSelfPermission(eq(permission));
    }
}
