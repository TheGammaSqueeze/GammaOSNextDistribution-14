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
package com.android.tradefed.util;

import static org.junit.Assert.assertEquals;

import java.io.File;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class TestRunnerUtilTest {

    @Test
    public void testGetLdLibraryPath() throws IOException {
        File tmpDir = null;
        try {
            tmpDir = FileUtil.createTempDir("workdir");
            File testcasesDir = new File(tmpDir, "testcases");
            File testModuleDir = new File(testcasesDir, "test_module");
            File abiDir = new File(testModuleDir, "x86");
            abiDir.mkdirs();
            File hostOutDir = new File(tmpDir, "host/out");
            hostOutDir.mkdirs();

            List<String> paths = new ArrayList<>();
            String libs[] = {"lib", "lib64"};
            for (String lib : libs) {
                File libDir = new File(abiDir, lib);
                libDir.mkdirs();
                paths.add(libDir.getAbsolutePath());
                libDir = new File(testcasesDir, lib);
                libDir.mkdirs();
                paths.add(libDir.getAbsolutePath());
                libDir = new File(hostOutDir, lib);
                libDir.mkdirs();
                paths.add(libDir.getAbsolutePath());
            }

            TestRunnerUtil.singleton = Mockito.mock(TestRunnerUtil.class);
            Mockito.when(TestRunnerUtil.singleton.getEnv("ANDROID_HOST_OUT"))
                    .thenReturn(hostOutDir.getAbsolutePath());

            assertEquals(
                    String.join(java.io.File.pathSeparator, paths),
                    TestRunnerUtil.getLdLibraryPath(new File(abiDir, "test_binary")));
        } finally {
            FileUtil.recursiveDelete(tmpDir);
        }
    }
}
