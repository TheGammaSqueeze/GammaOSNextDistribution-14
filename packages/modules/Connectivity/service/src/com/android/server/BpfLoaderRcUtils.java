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

package com.android.server;

import android.annotation.NonNull;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * BpfRcUtils is responsible for comparing the bpf loader rc file.
 *
 * {@hide}
 */
public class BpfLoaderRcUtils {
    public static final String TAG = BpfLoaderRcUtils.class.getSimpleName();

    private static final List<String> BPF_LOADER_RC_S_T = List.of(
            "service bpfloader /system/bin/bpfloader",
            "capabilities CHOWN SYS_ADMIN NET_ADMIN",
            "rlimit memlock 1073741824 1073741824",
            "oneshot",
            "reboot_on_failure reboot,bpfloader-failed",
            "updatable"
    );

    private static final List<String> BPF_LOADER_RC_U = List.of(
            "service bpfloader /system/bin/bpfloader",
            "capabilities CHOWN SYS_ADMIN NET_ADMIN",
            "group root graphics network_stack net_admin net_bw_acct net_bw_stats net_raw system",
            "user root",
            "rlimit memlock 1073741824 1073741824",
            "oneshot",
            "reboot_on_failure reboot,bpfloader-failed",
            "updatable"
    );

    private static final List<String> BPF_LOADER_RC_UQPR2 = List.of(
            "service bpfloader /system/bin/netbpfload",
            "capabilities CHOWN SYS_ADMIN NET_ADMIN",
            "group root graphics network_stack net_admin net_bw_acct net_bw_stats net_raw system",
            "user root",
            "rlimit memlock 1073741824 1073741824",
            "oneshot",
            "reboot_on_failure reboot,bpfloader-failed",
            "updatable"
    );


    private static final String BPF_LOADER_RC_FILE_PATH = "/etc/init/bpfloader.rc";
    private static final String NET_BPF_LOAD_RC_FILE_PATH = "/etc/init/netbpfload.rc";

    private BpfLoaderRcUtils() {
    }

    /**
     * Load the bpf rc file content from the input stream.
     */
    @VisibleForTesting
    public static List<String> loadExistingBpfRcFile(@NonNull InputStream inputStream) {
        List<String> contents = new ArrayList<>();
        boolean bpfSectionFound = false;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.ISO_8859_1))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("#")) {
                    continue;
                }
                // If bpf service section was found and new service or action section start. The
                // read should stop.
                if (bpfSectionFound && (line.startsWith("service ") || (line.startsWith("on ")))) {
                    break;
                }
                if (line.startsWith("service bpfloader ")) {
                    bpfSectionFound = true;
                }
                if (bpfSectionFound) {
                    contents.add(line);
                }
            }
        } catch (IOException e) {
            Log.wtf("read input stream failed.", e);
            contents.clear();
            return contents;
        }
        return contents;
    }

    /**
     * Check the bpfLoader rc file on the system image matches any of the template files.
     */
    public static boolean checkBpfLoaderRc() {
        File bpfRcFile = new File(BPF_LOADER_RC_FILE_PATH);
        if (!bpfRcFile.exists()) {
            if (SdkLevel.isAtLeastU()) {
                bpfRcFile = new File(NET_BPF_LOAD_RC_FILE_PATH);
            }
            if (!bpfRcFile.exists()) {
                Log.wtf(TAG,
                        "neither " + BPF_LOADER_RC_FILE_PATH + " nor " + NET_BPF_LOAD_RC_FILE_PATH
                                + " exist.");
                return false;
            }
            // Check bpf rc file in U QPR2
            return compareBpfLoaderRc(bpfRcFile, BPF_LOADER_RC_UQPR2);
        }

        if (SdkLevel.isAtLeastU()) {
            // Check bpf rc file in U
            return compareBpfLoaderRc(bpfRcFile, BPF_LOADER_RC_U);
        }
        // Check bpf rc file in S/T
        return compareBpfLoaderRc(bpfRcFile, BPF_LOADER_RC_S_T);
    }

    private static boolean compareBpfLoaderRc(@NonNull File bpfRcFile,
            @NonNull List<String> template) {
        try {
            List<String> actualContent = loadExistingBpfRcFile(new FileInputStream(bpfRcFile));
            if (!actualContent.equals(template)) {
                Log.wtf(TAG, "BPF rc file is not same as the template files " + actualContent);
                return false;
            }
        } catch (FileNotFoundException e) {
            Log.wtf(bpfRcFile.getPath() + " doesn't exist.", e);
            return false;
        }
        return true;
    }
}
