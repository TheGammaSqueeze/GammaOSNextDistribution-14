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

package android.permission.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test shell command capability enforcement
 */
@RunWith(AndroidJUnit4.class)
public class ShellCommandPermissionTest {

    static final int EXPECTED_ERROR_CODE = 255;

    /**
     * Runs the given command, waits for it to exit, and verifies the return
     * code indicates failure.
     */
    private void executeShellCommandAndWaitForError(String command)
            throws Exception {
        try {
            java.lang.Process proc = Runtime.getRuntime().exec(command);
            assertThat(proc.waitFor()).isEqualTo(EXPECTED_ERROR_CODE);
        } catch (InterruptedException e) {
            fail("Unsuccessful shell command");
        }
    }

    @Test
    public void testTraceIpc() throws Exception {
        executeShellCommandAndWaitForError(
                "cmd activity trace-ipc stop --dump-file /data/system/last-fstrim");
    }

    @Test
    public void testDumpheap() throws Exception {
        executeShellCommandAndWaitForError(
                "cmd activity dumpheap system_server /data/system/last-fstrim");
    }
}
