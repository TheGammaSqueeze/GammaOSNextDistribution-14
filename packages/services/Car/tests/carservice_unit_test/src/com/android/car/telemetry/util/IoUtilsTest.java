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

package com.android.car.telemetry.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.car.telemetry.TelemetryProto;
import android.os.PersistableBundle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RunWith(JUnit4.class)
public class IoUtilsTest {

    private File mTestDir;
    private File mTestFile;

    @Before
    public void setUp() throws Exception {
        mTestDir = Files.createTempDirectory("car_telemetry_test").toFile();
        mTestFile = new File(mTestDir, "test_file");
    }

    @Test
    public void testReadBundle() throws Exception {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("key", "value");
        bundle.putDouble("pi", 3.14159);
        bundle.putLong("current timestamp millis", System.currentTimeMillis());
        writeBundleToFile(mTestFile, bundle);

        assertThat(IoUtils.readBundle(mTestFile).toString()).isEqualTo(bundle.toString());
    }

    @Test
    public void testReadBundle_invalidBundle_shouldThrowException() throws Exception {
        Files.write(mTestFile.toPath(), "test".getBytes(StandardCharsets.UTF_8));

        assertThrows(IOException.class, () -> IoUtils.readBundle(mTestFile));
    }

    @Test
    public void testWriteBundle() throws Exception {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("key", "value");
        bundle.putDouble("pi", 3.14159);
        bundle.putLong("current timestamp millis", System.currentTimeMillis());

        IoUtils.writeBundle(mTestFile, bundle);

        assertThat(readBundleFromFile(mTestFile).toString()).isEqualTo(bundle.toString());
    }

    @Test
    public void testWriteProto_metricsConfig() throws Exception {
        TelemetryProto.MetricsConfig config = TelemetryProto.MetricsConfig.newBuilder()
                .setName("my_metrics_config").setVersion(1).setScript("no-op").build();

        IoUtils.writeProto(mTestFile, config);

        assertThat(readMetricsConfigFromFile(mTestFile)).isEqualTo(config);
    }

    @Test
    public void testWriteProto_telemetryError() throws Exception {
        TelemetryProto.TelemetryError error = TelemetryProto.TelemetryError.newBuilder()
                .setErrorType(TelemetryProto.TelemetryError.ErrorType.LUA_SCRIPT_ERROR)
                .setMessage("fake error").build();
        IoUtils.writeProto(mTestFile, error);

        assertThat(readTelemetryErrorFromFile(mTestFile)).isEqualTo(error);
    }

    @Test
    public void testDeleteIfExists() throws Exception {
        mTestFile.createNewFile();

        assertThat(IoUtils.deleteSilently(mTestDir, mTestFile.getName())).isTrue();
        assertThat(IoUtils.deleteSilently(mTestDir, mTestFile.getName())).isFalse();
    }

    @Test
    public void testDeleteAll() throws Exception {
        File file1 = new File(mTestDir, "file1");
        file1.createNewFile();
        File file2 = new File(mTestDir, "file2");
        file2.createNewFile();

        IoUtils.deleteAllSilently(mTestDir);

        assertThat(mTestDir.listFiles()).isEmpty();
    }

    @Test
    public void testDeleteStaleData() throws Exception {
        File stale1 = new File(mTestDir, "stale1");
        stale1.createNewFile();
        stale1.setLastModified(0);
        File stale2 = new File(mTestDir, "stale2");
        stale2.createNewFile();
        stale2.setLastModified(System.currentTimeMillis() - 10_000_000L);
        File active = new File(mTestDir, "active");
        active.createNewFile();

        IoUtils.deleteOldFiles(1_000_000L, mTestDir);

        assertThat(mTestDir.listFiles().length).isEqualTo(1);
        assertThat(stale1.exists()).isFalse();
        assertThat(stale2.exists()).isFalse();
        assertThat(active.exists()).isTrue();
    }

    /**
     * Writes a persistable bundle to the result directory with the given directory and file name,
     * and verifies that it was successfully written.
     */
    private void writeBundleToFile(
            File file, PersistableBundle persistableBundle) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            persistableBundle.writeToStream(byteArrayOutputStream);
            Files.write(file.toPath(), byteArrayOutputStream.toByteArray());
        }
        assertWithMessage("bundle is not written to the result directory")
                .that(file.exists()).isTrue();
    }

    /** Reads a persistable bundle from the given path without using any wrapper class. */
    private PersistableBundle readBundleFromFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return PersistableBundle.readFromStream(fis);
        }
    }

    /** Reads a MetricsConfig from the given path without using any wrapper class. */
    private TelemetryProto.MetricsConfig readMetricsConfigFromFile(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return TelemetryProto.MetricsConfig.parseFrom(bytes);
    }

    /** Reads a TelemetryError from the given path without using any wrapper class. */
    private TelemetryProto.TelemetryError readTelemetryErrorFromFile(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return TelemetryProto.TelemetryError.parseFrom(bytes);
    }
}
