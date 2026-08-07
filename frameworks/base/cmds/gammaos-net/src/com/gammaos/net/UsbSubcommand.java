/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.gammaos.net;

import android.content.Context;
import android.hardware.usb.UsbManager;

/**
 * {@code gammaos-net usb <functions>} - switch the USB gadget functions (mtp / ptp / rndis / none)
 * through {@link UsbManager#setCurrentFunctions}, the same call packages/apps/Settings and the ATV
 * power menu use.
 *
 * <p>The gammaos-nano menu cannot run {@code svc usb setFunctions} itself: nano runs in init's
 * bootstrap mount namespace, where {@code /apex/com.android.art} is not mounted, so app_process (the
 * process {@code svc} launches) fails to start and the command silently does nothing. This helper is
 * launched via gammaos-net.sh, which nsenters into init's full namespace first, so the switch
 * actually takes effect - exactly like the wifi/bt subcommands.
 */
public final class UsbSubcommand {
    private UsbSubcommand() {}

    static int run(Context ctx, String[] args) {
        // args[0] == "usb"; args[1] == the function token ("mtp"/"ptp"/"rndis"/"" or "none" = charge).
        final String token = (args.length >= 2 && args[1] != null && !args[1].isEmpty())
                ? args[1] : "none";
        final UsbManager usb = ctx.getSystemService(UsbManager.class);
        if (usb == null) {
            System.err.println("gammaos-net: UsbManager unavailable");
            return 2;
        }
        final long functions;
        try {
            functions = UsbManager.usbFunctionsFromString(token);
        } catch (RuntimeException e) {
            System.err.println("gammaos-net: unknown usb functions '" + token + "': " + e);
            return 2;
        }
        // setCurrentFunctions applies the chosen transfer function; UsbDeviceManager layers ADB back
        // on automatically when adb is enabled, so switching modes never drops adb.
        usb.setCurrentFunctions(functions);
        System.out.println("usb setCurrentFunctions " + token);
        return 0;
    }
}
