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

package com.android.server.healthconnect.storage.datatypehelpers;

import static com.android.server.healthconnect.TestUtils.TEST_USER;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Environment;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.server.healthconnect.HealthConnectUserContext;

import org.junit.rules.ExternalResource;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.File;
import java.util.Arrays;

/** A test rule that deals with ground work of setting up a mock Health Connect database. */
public class HealthConnectDatabaseTestRule extends ExternalResource {
    private MockitoSession mStaticMockSession;
    private File mMockDataDirectory;
    private HealthConnectUserContext mContext;

    @Override
    public void before() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(Environment.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        mContext =
                new HealthConnectUserContext(
                        InstrumentationRegistry.getInstrumentation().getContext(), TEST_USER);
        mMockDataDirectory = mContext.getDir("mock_data", Context.MODE_PRIVATE);
        when(Environment.getDataDirectory()).thenReturn(mMockDataDirectory);
    }

    @Override
    public void after() {
        deleteDir(mMockDataDirectory);
        mStaticMockSession.finishMocking();
    }

    public HealthConnectUserContext getUserContext() {
        return mContext;
    }

    private static void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (var file : files) {
                if (file.isDirectory()) {
                    deleteDir(file);
                } else {
                    assertThat(file.delete()).isTrue();
                }
            }
        }
        assertWithMessage(
                        "Directory "
                                + dir.getAbsolutePath()
                                + " is not empty, Files present = "
                                + Arrays.toString(dir.list()))
                .that(dir.delete())
                .isTrue();
    }
}
