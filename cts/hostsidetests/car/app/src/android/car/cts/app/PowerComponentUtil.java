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

package android.car.cts.app;

import android.car.hardware.power.PowerComponent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class PowerComponentUtil {
    private static final String POWER_COMPONENT_AUDIO = "AUDIO";
    private static final String POWER_COMPONENT_MEDIA = "MEDIA";
    private static final String POWER_COMPONENT_DISPLAY = "DISPLAY";
    private static final String POWER_COMPONENT_BLUETOOTH = "BLUETOOTH";
    private static final String POWER_COMPONENT_WIFI = "WIFI";
    private static final String POWER_COMPONENT_CELLULAR = "CELLULAR";
    private static final String POWER_COMPONENT_ETHERNET = "ETHERNET";
    private static final String POWER_COMPONENT_PROJECTION = "PROJECTION";
    private static final String POWER_COMPONENT_NFC = "NFC";
    private static final String POWER_COMPONENT_INPUT = "INPUT";
    private static final String POWER_COMPONENT_VOICE_INTERACTION = "VOICE_INTERACTION";
    private static final String POWER_COMPONENT_VISUAL_INTERACTION = "VISUAL_INTERACTION";
    private static final String POWER_COMPONENT_TRUSTED_DEVICE_DETECTION =
            "TRUSTED_DEVICE_DETECTION";
    private static final String POWER_COMPONENT_LOCATION = "LOCATION";
    private static final String POWER_COMPONENT_MICROPHONE = "MICROPHONE";
    private static final String POWER_COMPONENT_CPU = "CPU";

    public static final int INVALID_POWER_COMPONENT = -1;

    private PowerComponentUtil() throws Exception {
        throw new Exception("utility class is not instantiable");
    }

    public static int toPowerComponent(@Nullable String component) {
        if (component == null) {
            return INVALID_POWER_COMPONENT;
        }
        switch (component) {
            case POWER_COMPONENT_AUDIO:
                return PowerComponent.AUDIO;
            case POWER_COMPONENT_MEDIA:
                return PowerComponent.MEDIA;
            case POWER_COMPONENT_DISPLAY:
                return PowerComponent.DISPLAY;
            case POWER_COMPONENT_BLUETOOTH:
                return PowerComponent.BLUETOOTH;
            case POWER_COMPONENT_WIFI:
                return PowerComponent.WIFI;
            case POWER_COMPONENT_CELLULAR:
                return PowerComponent.CELLULAR;
            case POWER_COMPONENT_ETHERNET:
                return PowerComponent.ETHERNET;
            case POWER_COMPONENT_PROJECTION:
                return PowerComponent.PROJECTION;
            case POWER_COMPONENT_NFC:
                return PowerComponent.NFC;
            case POWER_COMPONENT_INPUT:
                return PowerComponent.INPUT;
            case POWER_COMPONENT_VOICE_INTERACTION:
                return PowerComponent.VOICE_INTERACTION;
            case POWER_COMPONENT_VISUAL_INTERACTION:
                return PowerComponent.VISUAL_INTERACTION;
            case POWER_COMPONENT_TRUSTED_DEVICE_DETECTION:
                return PowerComponent.TRUSTED_DEVICE_DETECTION;
            case POWER_COMPONENT_LOCATION:
                return PowerComponent.LOCATION;
            case POWER_COMPONENT_MICROPHONE:
                return PowerComponent.MICROPHONE;
            case POWER_COMPONENT_CPU:
                return PowerComponent.CPU;
            default:
                return INVALID_POWER_COMPONENT;
        }
    }

    @NonNull
    public static String componentToString(int component) {
        switch (component) {
            case PowerComponent.AUDIO:
                return POWER_COMPONENT_AUDIO;
            case PowerComponent.MEDIA:
                return POWER_COMPONENT_MEDIA;
            case PowerComponent.DISPLAY:
                return POWER_COMPONENT_DISPLAY;
            case PowerComponent.BLUETOOTH:
                return POWER_COMPONENT_BLUETOOTH;
            case PowerComponent.WIFI:
                return POWER_COMPONENT_WIFI;
            case PowerComponent.CELLULAR:
                return POWER_COMPONENT_CELLULAR;
            case PowerComponent.ETHERNET:
                return POWER_COMPONENT_ETHERNET;
            case PowerComponent.PROJECTION:
                return POWER_COMPONENT_PROJECTION;
            case PowerComponent.NFC:
                return POWER_COMPONENT_NFC;
            case PowerComponent.INPUT:
                return POWER_COMPONENT_INPUT;
            case PowerComponent.VOICE_INTERACTION:
                return POWER_COMPONENT_VOICE_INTERACTION;
            case PowerComponent.VISUAL_INTERACTION:
                return POWER_COMPONENT_VISUAL_INTERACTION;
            case PowerComponent.TRUSTED_DEVICE_DETECTION:
                return POWER_COMPONENT_TRUSTED_DEVICE_DETECTION;
            case PowerComponent.LOCATION:
                return POWER_COMPONENT_LOCATION;
            case PowerComponent.MICROPHONE:
                return POWER_COMPONENT_MICROPHONE;
            case PowerComponent.CPU:
                return POWER_COMPONENT_CPU;
            default:
                return "unknown component";
        }
    }
}
