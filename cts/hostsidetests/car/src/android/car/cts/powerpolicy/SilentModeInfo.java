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

package android.car.cts.powerpolicy;

import java.util.Objects;

public final class SilentModeInfo {
    private static final String[] ATTR_HEADERS = {"Monitoring HW state signal",
            "Silent mode by HW state signal", "Forced silent mode"};
    private static final int NUMBER_OF_ATTRS = 3;

    public static final String COMMAND = "cmd car_service silent-mode query";
    public static final SilentModeInfo NO_SILENT = new SilentModeInfo(true, false, false);
    public static final SilentModeInfo FORCED_SILENT = new SilentModeInfo(false, true, true);

    private final boolean mForcedSilentMode;
    private final boolean mMonitoringHWStateSignal;
    private final boolean mSilentModeByHWStateSignal;

    private SilentModeInfo(boolean monitoring, boolean byHW, boolean forced) {
        mMonitoringHWStateSignal = monitoring;
        mSilentModeByHWStateSignal = byHW;
        mForcedSilentMode = forced;
    }

    public boolean getForcedSilentMode() {
        return mForcedSilentMode;
    }

    public boolean getMonitoringHWStateSignal() {
        return mMonitoringHWStateSignal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SilentModeInfo that = (SilentModeInfo) o;
        return mMonitoringHWStateSignal == that.mMonitoringHWStateSignal
                && mSilentModeByHWStateSignal == that.mSilentModeByHWStateSignal
                && mForcedSilentMode == that.mForcedSilentMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mMonitoringHWStateSignal,
                mSilentModeByHWStateSignal, mForcedSilentMode);
    }

    @Override
    public String toString() {
        return String.format("SilentModeInfo: %b, %b, %b", mMonitoringHWStateSignal,
                mSilentModeByHWStateSignal, mForcedSilentMode);
    }

    public static SilentModeInfo parse(String cmdOutput) throws Exception {
        boolean[] attrs = new boolean[ATTR_HEADERS.length];
        String[] lines = cmdOutput.split("\n");

        for (int idx = 0; idx < lines.length; idx++) {
            String[] tokens = lines[idx].trim().split(":");
            if (tokens.length != 2) {
                throw new IllegalArgumentException(
                        "SilentModeQueryResult.parse(): malformatted attr line: " + lines[idx]);
            }
            int hdrIdx;
            String hdr = tokens[0];
            String val = tokens[1];
            for (hdrIdx = 0; hdrIdx < ATTR_HEADERS.length; hdrIdx++) {
                if (hdr.contains(ATTR_HEADERS[hdrIdx])) {
                    break;
                }
            }
            if (hdrIdx == ATTR_HEADERS.length) {
                continue;
            }
            attrs[hdrIdx] = Boolean.parseBoolean(val.trim());
        }

        return new SilentModeInfo(attrs[0], attrs[1], attrs[2]);
    }
}
