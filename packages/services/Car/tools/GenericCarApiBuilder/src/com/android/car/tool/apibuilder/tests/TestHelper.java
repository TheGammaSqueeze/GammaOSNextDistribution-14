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

package com.android.car.tool.apibuilder.tests;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Clock;
import java.util.Random;

public class TestHelper {

    public File getResourceFile(String filename) throws Exception {
        InputStream inputStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(filename);

        long startime = Clock.systemUTC().millis();
        File tempFile = new File(filename + "_temp_" + new Random().nextInt());

        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            byte[] buf = new byte[1024];
            int chunkSize;
            while ((chunkSize = inputStream.read(buf)) != -1) {
                out.write(buf, 0, chunkSize);
            }
        }
        return tempFile;
    }
}
