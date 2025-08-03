/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.cts.install;

import static com.android.cts.install.lib.InstallUtils.getPackageInstaller;

import android.content.Context;
import android.content.pm.PackageInstaller;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.ExternalResource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

/** Utils for recording session state and retrieving recorded session info. */
final class SessionRule extends ExternalResource {
    private static final String STATE_FILENAME = "ctsstagedinstall_state";

    private final Context mContext;
    private final File mTestStateFile;

    SessionRule() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mTestStateFile = new File(mContext.getFilesDir(), STATE_FILENAME);
    }

    @Override
    protected void before() throws Throwable {
        mTestStateFile.createNewFile();
    }

    /**
     * Performs cleanup phase for this rule. Actual purpose of this method is to be called before
     * and after each host-side test to reduce tests flakiness.
     */
    void cleanUp() throws IOException {
        Files.deleteIfExists(mTestStateFile.toPath());
    }

    void recordSessionId(int sessionId) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(mTestStateFile))) {
            writer.write(String.valueOf(sessionId));
        }
    }

    int retrieveSessionId() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(mTestStateFile))) {
            return Integer.parseInt(reader.readLine());
        }
    }

    /**
     * Returns {@link android.content.pm.PackageInstaller.SessionInfo} with session id recorded
     * in {@link #mTestStateFile}. Assert error if no session found.
     */
    PackageInstaller.SessionInfo retrieveSessionInfo() throws IOException {
        return Optional.ofNullable(getPackageInstaller().getSessionInfo(retrieveSessionId()))
                .orElseThrow(() -> new AssertionError(
                        "Expecting to find session with getSessionInfo()"));
    }
}
