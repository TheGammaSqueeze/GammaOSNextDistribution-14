/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tradefed.build;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.invoker.ExecutionFiles;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.util.FileUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;

/** Unit tests for {@link BootstrapBuildProvider}. */
@RunWith(JUnit4.class)
public class BootstrapBuildProviderTest {
    private BootstrapBuildProvider mProvider;
    @Mock ITestDevice mMockDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mProvider =
                new BootstrapBuildProvider() {
                    @Override
                    ExecutionFiles getInvocationFiles() {
                        return TestInformation.newBuilder().build().executionFiles();
                    }
                };
    }

    @Test
    public void testGetBuild() throws Exception {
        when(mMockDevice.getBuildId()).thenReturn("5");
        when(mMockDevice.getIDevice()).thenReturn(mock(IDevice.class));
        when(mMockDevice.waitForDeviceShell(Mockito.anyLong())).thenReturn(true);
        when(mMockDevice.getProperty(Mockito.any())).thenReturn("property");
        when(mMockDevice.getProductVariant()).thenReturn("variant");
        when(mMockDevice.getBuildFlavor()).thenReturn("flavor");
        when(mMockDevice.getBuildAlias()).thenReturn("alias");

        IBuildInfo res = mProvider.getBuild(mMockDevice);
        assertNotNull(res);
        try {
            assertTrue(res instanceof IDeviceBuildInfo);
            // Ensure tests dir is never null
            assertTrue(((IDeviceBuildInfo) res).getTestsDir() != null);
        } finally {
            mProvider.cleanUp(res);
        }
    }

    @Test
    public void testGetBuild_add_extra_file() throws Exception {
        when(mMockDevice.getBuildId()).thenReturn("5");
        when(mMockDevice.getIDevice()).thenReturn(mock(IDevice.class));
        when(mMockDevice.waitForDeviceShell(Mockito.anyLong())).thenReturn(true);
        when(mMockDevice.getProperty(Mockito.any())).thenReturn("property");
        when(mMockDevice.getProductVariant()).thenReturn("variant");
        when(mMockDevice.getBuildFlavor()).thenReturn("flavor");
        when(mMockDevice.getBuildAlias()).thenReturn("alias");

        OptionSetter setter = new OptionSetter(mProvider);
        File tmpDir = FileUtil.createTempDir("tmp");
        File file_1 = new File(tmpDir, "sys.img");
        setter.setOptionValue("extra-file", "file_1", file_1.getAbsolutePath());
        IBuildInfo res = mProvider.getBuild(mMockDevice);
        assertNotNull(res);
        try {
            assertTrue(res instanceof IDeviceBuildInfo);
            // Ensure tests dir is never null
            assertTrue(((IDeviceBuildInfo) res).getTestsDir() != null);
            assertEquals(((IDeviceBuildInfo) res).getFile("file_1"), file_1);
        } finally {
            mProvider.cleanUp(res);
            FileUtil.recursiveDelete(tmpDir);
        }
    }

    /**
     * Test that when using the provider with a StubDevice information that cannot be queried are
     * stubbed.
     */
    @Test
    public void testGetBuild_stubDevice() throws Exception {
        when(mMockDevice.getBuildId()).thenReturn("5");
        when(mMockDevice.getIDevice()).thenReturn(new StubDevice("serial"));
        when(mMockDevice.getBuildFlavor()).thenReturn("flavor");
        when(mMockDevice.getBuildAlias()).thenReturn("alias");

        IBuildInfo res = mProvider.getBuild(mMockDevice);
        assertNotNull(res);
        try {
            assertTrue(res instanceof IDeviceBuildInfo);
            // Ensure tests dir is never null
            assertTrue(((IDeviceBuildInfo) res).getTestsDir() != null);
            assertEquals("stub", res.getBuildBranch());
        } finally {
            mProvider.cleanUp(res);
        }
    }

    /** When TestsDir is given, cleanUp won't delete it. */
    @Test
    public void testCleanup_withExistingTestsDir() throws Exception {
        when(mMockDevice.getBuildId()).thenReturn("5");
        when(mMockDevice.getIDevice()).thenReturn(mock(IDevice.class));
        when(mMockDevice.waitForDeviceShell(Mockito.anyLong())).thenReturn(true);
        when(mMockDevice.getProperty(Mockito.any())).thenReturn("property");
        when(mMockDevice.getProductVariant()).thenReturn("variant");
        when(mMockDevice.getBuildFlavor()).thenReturn("flavor");
        when(mMockDevice.getBuildAlias()).thenReturn("alias");

        OptionSetter setter = new OptionSetter(mProvider);
        File mTestsDir = FileUtil.createTempDir("testCleanup", new File("/tmp"));
        setter.setOptionValue("tests-dir", mTestsDir.getAbsolutePath());
        IBuildInfo res = mProvider.getBuild(mMockDevice);

        mProvider.cleanUp(res);

        assertTrue(mTestsDir.exists());
        // cleanup test dirs.
        FileUtil.recursiveDelete(mTestsDir);
    }

    /** When TestsDir is not given, cleanUp will delete the created temporary dir. */
    @Test
    public void testCleanup_withTestsDirIsNull() throws Exception {
        when(mMockDevice.getBuildId()).thenReturn("5");
        when(mMockDevice.getIDevice()).thenReturn(mock(IDevice.class));
        when(mMockDevice.waitForDeviceShell(Mockito.anyLong())).thenReturn(true);
        when(mMockDevice.getProperty(Mockito.any())).thenReturn("property");
        when(mMockDevice.getProductVariant()).thenReturn("variant");
        when(mMockDevice.getBuildFlavor()).thenReturn("flavor");
        when(mMockDevice.getBuildAlias()).thenReturn("alias");
        IBuildInfo res = mProvider.getBuild(mMockDevice);

        mProvider.cleanUp(res);

        for (String fileKey : res.getVersionedFileKeys()) {
            assertFalse(res.getVersionedFile(fileKey).getFile().exists());
        }
    }

    /** When TestsDir is given and not a directory, cleanUp won't delete it. */
    @Test
    public void testCleanup_withTestsDirIsNotDir() throws Exception {
        when(mMockDevice.getBuildId()).thenReturn("5");
        when(mMockDevice.getIDevice()).thenReturn(mock(IDevice.class));
        when(mMockDevice.waitForDeviceShell(Mockito.anyLong())).thenReturn(true);
        when(mMockDevice.getProperty(Mockito.any())).thenReturn("property");
        when(mMockDevice.getProductVariant()).thenReturn("variant");
        when(mMockDevice.getBuildFlavor()).thenReturn("flavor");
        when(mMockDevice.getBuildAlias()).thenReturn("alias");

        OptionSetter setter = new OptionSetter(mProvider);
        File mNotDir = FileUtil.createTempFile("testCleanup", "file_c");
        setter.setOptionValue("tests-dir", mNotDir.getAbsolutePath());
        IBuildInfo res = mProvider.getBuild(mMockDevice);

        mProvider.cleanUp(res);

        assertTrue(mNotDir.exists());
        // cleanup test files.
        FileUtil.deleteFile(mNotDir);
    }
}
