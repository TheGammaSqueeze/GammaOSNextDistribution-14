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

package com.android.server.cts.device.statsdatom;

import static android.hardware.display.HdrConversionMode.HDR_CONVERSION_FORCE;
import static android.hardware.display.HdrConversionMode.HDR_CONVERSION_PASSTHROUGH;
import static android.view.Display.HdrCapabilities.HDR_TYPES;
import static android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION;
import static android.view.Display.HdrCapabilities.HDR_TYPE_HDR10;
import static android.view.Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS;
import static android.view.Display.HdrCapabilities.HDR_TYPE_HLG;
import static android.view.Display.HdrCapabilities.HDR_TYPE_INVALID;

import android.hardware.display.DisplayManager;
import android.hardware.display.HdrConversionMode;
import android.util.Slog;
import android.view.Display;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Test;

import java.util.Arrays;
import java.util.Locale;
import java.util.Scanner;

public class HdrCapabilitiesAtomTests {

    private static final String TAG = HdrCapabilitiesAtomTests.class.getSimpleName();

    @Test
    public void cacheOriginalHdrConversionMode() {
        DisplayManager displayManager = InstrumentationRegistry.getTargetContext().getSystemService(
                DisplayManager.class);

        HdrConversionMode hdrConversionModeSetting = displayManager.getHdrConversionModeSetting();
        Slog.i(TAG, String.format(Locale.getDefault(), "original-hdr-conversion-mode: %d %d",
                hdrConversionModeSetting.getConversionMode(),
                hdrConversionModeSetting.getPreferredHdrOutputType()));
    }

    @Test
    public void restoreOriginalHdrConversionMode() throws Exception {
        String logs = SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                "logcat -v brief -d HdrCapabilitiesAtomTests I *:S");
        HdrConversionMode hdrConversionModeSetting = null;
        try (Scanner in = new Scanner(logs)) {
            while (in.hasNextLine()) {
                String line = in.nextLine();

                if (line.contains("original-hdr-conversion-mode")) {
                    String[] trim = line.split(":")[2].trim().split(" ");
                    hdrConversionModeSetting = new HdrConversionMode(Integer.parseInt(trim[0]),
                            Integer.parseInt(trim[1]));
                }
            }
        }
        setHdrConversionMode(hdrConversionModeSetting.getConversionMode(),
                hdrConversionModeSetting.getPreferredHdrOutputType());
    }

    @Test
    public void getDeviceHdrOutCapabilities() {
        DisplayManager displayManager = InstrumentationRegistry.getTargetContext().getSystemService(
                DisplayManager.class);

        int[] supportedHdrOutputTypes = displayManager.getSupportedHdrOutputTypes();

        StringBuilder sb = new StringBuilder();
        for (Integer supportedHdrOutputType : supportedHdrOutputTypes) {
            sb.append(supportedHdrOutputType).append(" ");
        }

        Slog.i(TAG, String.format(Locale.getDefault(), "hdr-output-capabilities: %s", sb));
    }

    @Test
    public void has4k30Issue() {
        DisplayManager displayManager = InstrumentationRegistry.getTargetContext().getSystemService(
                DisplayManager.class);
        Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        Display.Mode[] supportedModes = display.getSupportedModes();

        long modesThatSupportDv = Arrays.stream(supportedModes)
                .filter(mode -> Arrays.stream(mode.getSupportedHdrTypes())
                        .anyMatch(hdrType -> hdrType == HDR_TYPE_DOLBY_VISION))
                .count();

        boolean has4k30Issue = modesThatSupportDv != 0
                && modesThatSupportDv < supportedModes.length;

        Slog.i(TAG, String.format(Locale.getDefault(), "has-4k30-issue: %b", has4k30Issue));
    }

    @Test
    public void getSupportedHdrTypes() {
        StringBuilder sb = new StringBuilder();
        for (Integer hdrType : HDR_TYPES) {
            sb.append(hdrType).append(" ");
        }
        Slog.i(TAG, String.format(Locale.getDefault(), "hdr-types: %s", sb));
    }

    @Test
    public void setHdrConversionPassthrough() {
        setHdrConversionMode(HDR_CONVERSION_PASSTHROUGH, HDR_TYPE_INVALID);
    }

    @Test
    public void setHdrConversionForceDV() {
        setHdrConversionMode(HDR_CONVERSION_FORCE, HDR_TYPE_DOLBY_VISION);
    }

    @Test
    public void setHdrConversionForceHDR10() {
        setHdrConversionMode(HDR_CONVERSION_FORCE, HDR_TYPE_HDR10);
    }

    @Test
    public void setHdrConversionForceHLG() {
        setHdrConversionMode(HDR_CONVERSION_FORCE, HDR_TYPE_HLG);
    }

    @Test
    public void setHdrConversionForceHDR10Plus() {
        setHdrConversionMode(HDR_CONVERSION_FORCE, HDR_TYPE_HDR10_PLUS);
    }

    private void setHdrConversionMode(int hdrConversionMode, int preferredType) {
        DisplayManager displayManager = InstrumentationRegistry.getTargetContext().getSystemService(
                DisplayManager.class);

        ShellIdentityUtils.invokeWithShellPermissions(
                () -> displayManager.setHdrConversionMode(new HdrConversionMode(
                        hdrConversionMode, preferredType)));
    }
}
