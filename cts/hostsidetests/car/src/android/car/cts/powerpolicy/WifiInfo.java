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

public final class WifiInfo extends ComponentPowerStateInfo {
    public static final String COMMAND = "dumpsys wifi | grep \"Wi-Fi is\"";
    public static final String[] ENABLE_COMMANDS = {
        "settings --user 10 put global wifi 1",
        "svc wifi enable"
    };
    public static final String[] DISABLE_COMMANDS = {
        "settings --user 10 put global wifi 0",
        "svc wifi disable"
    };
    private static final String ENABLE_STATEMENT = "Wi-Fi is enabled";
    private static final String DISABLE_STATEMENT = "Wi-Fi is disabled";

    private WifiInfo(boolean powerState) {
        super("WIFI", powerState);
    }

    public static WifiInfo parse(String cmdOutput) throws Exception {
        String[] lines = cmdOutput.split("\n");
        if (lines.length == 0) {
            throw new IllegalArgumentException("empty cmd output");
        }
        String latest = lines[0].trim();
        if (latest.equals(ENABLE_STATEMENT)) {
            return new WifiInfo(true);
        }
        if (latest.equals(DISABLE_STATEMENT)) {
            return new WifiInfo(false);
        }
        return null;
    }
}
