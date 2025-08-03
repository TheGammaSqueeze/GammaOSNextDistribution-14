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

package android.car.cts.utils;

import android.app.UiAutomation;
import android.os.ParcelFileDescriptor;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Class contains static methods to dump proto for car services
 */
public final class ProtoDumpUtils {
    private ProtoDumpUtils() {
    }

    /**
     * Dump proto by shell command
     *
     * @param serviceName Name of service to be dumped.
     * @return byte array for dumped proto
     */
    public static byte[] executeProtoDumpShellCommand(String serviceName) {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation();
        ParcelFileDescriptor pfd = uiAutomation.executeShellCommand(
                "dumpsys car_service --services " + serviceName + " --proto");
        try (FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
            byte[] buf = new byte[512];
            int bytesRead;
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            while ((bytesRead = fis.read(buf)) != -1) {
                stdout.write(buf, 0, bytesRead);
            }
            return stdout.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
