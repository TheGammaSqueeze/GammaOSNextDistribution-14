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
package com.android.tradefed.command;

import com.android.tradefed.util.ResourceUtil;

import java.io.File;

/** Detects and returns whether this is a local developer running Tradefed. */
public class LocalDeveloper {

    private static final String CLIENT_ID_FILE = "/local_dev/client_id_file.json";

    /** Returns error code 0 for a local developer, and non-zero for not local. */
    public static void main(final String[] mainArgs) {
        String localClientEnv = System.getenv("LOCAL_CLIENT_FILE");
        File clientFile = null;
        // If we are explicitly given a local client file, use it.
        if (localClientEnv == null) {
            System.exit(1);
        }
        clientFile = new File(localClientEnv);
        if (clientFile.exists()) {
            System.exit(0);
        }
        // If the bundled client file exists
        boolean res = ResourceUtil.extractResourceAsFile(CLIENT_ID_FILE, clientFile);
        if (res) {
            System.exit(0);
        }
        // Delete the tmp file in case of issue
        clientFile.delete();
        System.exit(1);
    }
}
