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

public final class LocationInfo extends ComponentPowerStateInfo {
    public static final String COMMAND = "cmd location is-location-enabled";
    public static final String[] ENABLE_COMMANDS = {
        "settings put global location 1",
        "cmd location set-location-enabled true"
    };
    public static final String[] DISABLE_COMMANDS = {
        "settings put global location 0",
        "cmd location set-location-enabled false"
    };

    private LocationInfo(boolean powerState) {
        super("LOCATION", powerState);
    }

    public static LocationInfo parse(String cmdOutput) throws Exception {
        return new LocationInfo(Boolean.parseBoolean(cmdOutput.trim()));
    }
}
