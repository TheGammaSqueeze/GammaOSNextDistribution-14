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

package com.android.sts.common;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertTrue;

import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;
import java.util.regex.Pattern;

/** Unit tests for {@link ProcessUtil}. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class ProcessUtilTest extends BaseHostJUnit4Test {

    @Before
    public void setUp() throws Exception {
        assertTrue("could not unroot", getDevice().disableAdbRoot());
    }

    @After
    public void tearDown() throws Exception {
        assertTrue("could not unroot", getDevice().disableAdbRoot());
    }

    @Test(expected = IllegalStateException.class)
    public void testFindLoadedByProcessNonRoot() throws Exception {
        // expect failure because the shell user has no permission to read process info of other
        // users
        ProcessUtil.findFileLoadedByProcess(
                getDevice(), "system_server", Pattern.compile(Pattern.quote("libc.so")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindLoadedByProcessMultipleProcesses() throws Exception {
        // pattern 'android' has multiple (android.hardware.drm, android.hardware.gnss, etc)
        ProcessUtil.findFileLoadedByProcess(getDevice(), "android", null);
    }

    @Test
    public void testFindLoadedByProcessUtilRoot() throws Exception {
        assertTrue("must test with rootable device", getDevice().enableAdbRoot());
        Optional<IFileEntry> fileEntryOptional =
                ProcessUtil.findFileLoadedByProcess(
                        getDevice(), "system_server", Pattern.compile(Pattern.quote("libc.so")));
        assertWithMessage("file entry should not be empty")
                .that(fileEntryOptional.isPresent())
                .isTrue();
        IFileEntry fileEntry = fileEntryOptional.get();
        assertWithMessage("file entry should be a path to libc.so")
                .that(fileEntry.getFullPath())
                .contains("libc.so");
    }

    @Test
    public void testFindLoadedByProcessUtilNoMatch() throws Exception {
        assertTrue("must test with rootable device", getDevice().enableAdbRoot());
        Optional<IFileEntry> fileEntryOptional =
                ProcessUtil.findFileLoadedByProcess(
                        getDevice(),
                        "system_server",
                        Pattern.compile(Pattern.quote("doesnotexist.foobar")));
        assertWithMessage("file entry should be empty if no matches")
                .that(fileEntryOptional.isPresent())
                .isFalse();
    }
}
