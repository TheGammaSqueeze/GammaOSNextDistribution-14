/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.pvmfw;

import com.android.pvmfw.test.host.Pvmfw;

import java.io.File;
import java.io.IOException;

/** CLI for {@link com.android.microdroid.test.host.Pvmfw}. */
public class PvmfwTool {
    public static void printUsage() {
        System.out.println("pvmfw-tool: Appends pvmfw.bin and config payloads.");
        System.out.println("Requires BCC and optional debug policy dtbo files");
        System.out.println("");
        System.out.println("Usage: pvmfw-tool <out> <pvmfw.bin> <bcc.dat> [<dp.dtbo>]");
    }

    public static void main(String[] args) {
        if (args.length != 4 && args.length != 3) {
            printUsage();
            System.exit(1);
        }

        File out = new File(args[0]);
        File pvmfw_bin = new File(args[1]);
        File bcc_dat = new File(args[2]);

        try {
            Pvmfw.Builder builder = new Pvmfw.Builder(pvmfw_bin, bcc_dat);
            if (args.length == 4) {
                File dtbo = new File(args[3]);
                builder.setDebugPolicyOverlay(dtbo);
            }
            builder.build().serialize(out);
        } catch (IOException e) {
            e.printStackTrace();
            printUsage();
            System.exit(1);
        }
    }
}
