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

package com.android.adservices.service.common.compat;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.atLeastOnce;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertWithMessage;

import android.os.Build;
import android.os.SystemProperties;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Assume;
import org.junit.Test;
import org.mockito.MockitoSession;

public class BuildCompatUtilsTest {

    @Test
    public void testIsDebuggable_SPlus_debuggable() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .mockStatic(Build.class)
                        .spyStatic(BuildCompatUtils.class)
                        .startMocking();

        try {
            doReturn(true).when(Build::isDebuggable);
            assertWithMessage("BuildCompatUtils.isDebuggable")
                    .that(BuildCompatUtils.isDebuggable())
                    .isTrue();
            verify(Build::isDebuggable, atLeastOnce());
            verify(BuildCompatUtils::computeIsDebuggable_R, never());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testIsDebuggable_SPlus_notDebuggable() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .mockStatic(Build.class)
                        .spyStatic(BuildCompatUtils.class)
                        .startMocking();

        try {
            doReturn(false).when(Build::isDebuggable);
            assertWithMessage("BuildCompatUtils.isDebuggable")
                    .that(BuildCompatUtils.isDebuggable())
                    .isFalse();
            verify(Build::isDebuggable, atLeastOnce());
            verify(BuildCompatUtils::computeIsDebuggable_R, never());
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testIsDebuggable_RMinus_debuggable() {
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .mockStatic(SdkLevel.class)
                        .spyStatic(BuildCompatUtils.class)
                        .startMocking();

        try {
            doReturn(false).when(SdkLevel::isAtLeastS);
            doReturn(true).when(BuildCompatUtils::computeIsDebuggable_R);
            assertWithMessage("BuildCompatUtils.isDebuggable")
                    .that(BuildCompatUtils.isDebuggable())
                    .isTrue();
            verify(BuildCompatUtils::computeIsDebuggable_R);
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testIsDebuggable_RMinus_notDebuggable() {
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .mockStatic(SdkLevel.class)
                        .spyStatic(BuildCompatUtils.class)
                        .startMocking();

        try {
            doReturn(false).when(SdkLevel::isAtLeastS);
            doReturn(false).when(BuildCompatUtils::computeIsDebuggable_R);
            assertWithMessage("BuildCompatUtils.isDebuggable")
                    .that(BuildCompatUtils.isDebuggable())
                    .isFalse();
            verify(BuildCompatUtils::computeIsDebuggable_R);
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testComputeIsDebuggable_notDebuggable() {
        MockitoSession session =
                ExtendedMockito.mockitoSession().spyStatic(SystemProperties.class).startMocking();

        try {
            doReturn(0).when(() -> SystemProperties.getInt(anyString(), anyInt()));
            assertWithMessage("BuildCompatUtils.computeIsDebuggable")
                    .that(BuildCompatUtils.computeIsDebuggable_R())
                    .isFalse();
            verify(() -> SystemProperties.getInt("ro.debuggable", 0));
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testComputeIsDebuggable_debuggable() {
        MockitoSession session =
                ExtendedMockito.mockitoSession().spyStatic(SystemProperties.class).startMocking();

        try {
            doReturn(1).when(() -> SystemProperties.getInt(anyString(), anyInt()));
            assertWithMessage("BuildCompatUtils.computeIsDebuggable")
                    .that(BuildCompatUtils.computeIsDebuggable_R())
                    .isTrue();
            verify(() -> SystemProperties.getInt("ro.debuggable", 0));
        } finally {
            session.finishMocking();
        }
    }
}
