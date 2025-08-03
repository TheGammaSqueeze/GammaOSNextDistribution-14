/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.car.hardware.power;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.SparseBooleanArray;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Utility class used when dealing with PowerComponent.
 *
 * @hide
 */
public final class PowerComponentUtil {
    /**
     * The component is marked as enabled in the power policy.
     */
    public static final int COMPONENT_STATE_ENABLED = 1;

    /**
     * The component is marked as disabled in the power policy.
     */
    public static final int COMPONENT_STATE_DISABLED = 2;

    /**
     * The component is not specified in the power policy.
     */
    public static final int COMPONENT_STATE_UNTOUCHED = 3;

    @IntDef(prefix = { "COMPONENT_STATE_" }, value = {
            COMPONENT_STATE_ENABLED,
            COMPONENT_STATE_DISABLED,
            COMPONENT_STATE_UNTOUCHED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ComponentState { }

    /**
     * Represetns an invalid power component.
     */
    public static final int INVALID_POWER_COMPONENT = -1;

    /**
     * The first component in {@link PowerComponent}.
     */
    public static final int FIRST_POWER_COMPONENT = PowerComponent.AUDIO;

    /**
     * The last component in {@link PowerComponent}.
     *
     * <p> This should be updated when a new component is added to {@link PowerComponent}.
     */
    public static final int LAST_POWER_COMPONENT = PowerComponent.CPU;

    private static final String POWER_COMPONENT_PREFIX = "POWER_COMPONENT_";

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

    private interface ComponentFilter {
        boolean filter(int[] components);
    }

    // PowerComponentUtil is intended to provide static variables and methods.
    private PowerComponentUtil() {}

    /**
     * Checks whether the given component is valid.
     */
    public static boolean isValidPowerComponent(int component) {
        return component >= FIRST_POWER_COMPONENT && component <= LAST_POWER_COMPONENT;
    }

    /**
     * Checks whether the given policy has one ore more components specified in the given filter.
     */
    public static boolean hasComponents(@NonNull CarPowerPolicy policy,
            @NonNull CarPowerPolicyFilter filter) {
        SparseBooleanArray filterSet = new SparseBooleanArray();
        int[] components = filter.getComponents();
        for (int i = 0; i < components.length; i++) {
            filterSet.put(components[i], true);
        }

        ComponentFilter componentFilter = (c) -> {
            for (int i = 0; i < c.length; i++) {
                if (filterSet.get(c[i])) {
                    return true;
                }
            }
            return false;
        };

        if (componentFilter.filter(policy.getEnabledComponents())) {
            return true;
        }
        return componentFilter.filter(policy.getDisabledComponents());
    }

    /**
     * Matches the given string to {@link PowerComponent}.
     */
    public static int toPowerComponent(@Nullable String componentArg, boolean prefix) {
        String component = componentArg;
        if (component == null) {
            return INVALID_POWER_COMPONENT;
        }
        if (prefix) {
            if (!component.startsWith(POWER_COMPONENT_PREFIX)) {
                return INVALID_POWER_COMPONENT;
            }
            component = component.substring(POWER_COMPONENT_PREFIX.length());
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
                if (component.matches("\\d+")) {
                    int componentNumber = Integer.parseInt(component);
                    if (componentNumber >= PowerComponent.MINIMUM_CUSTOM_COMPONENT_VALUE) {
                        return componentNumber;
                    }
                }
                return INVALID_POWER_COMPONENT;
        }
    }

    /**
     * Convert {@link PowerComponent} to string.
     */
    @NonNull
    public static String powerComponentToString(int component) {
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
                if (component >= PowerComponent.MINIMUM_CUSTOM_COMPONENT_VALUE) {
                    return Integer.toString(component);
                }
                return "unknown component";
        }
    }

    /**
     * Convert list of {@link PowerComponent} to list of strings.
     */
    @NonNull
    public static List<String> powerComponentsToStrings(Iterable<Integer> components) {
        List<String> powerComponents = new ArrayList<>();
        Iterator<Integer> componentsIterator = components.iterator();
        while (componentsIterator.hasNext()) {
            Integer component = componentsIterator.next();
            powerComponents.add(powerComponentToString(component));
        }
        return powerComponents;
    }

    /**
     * Convert list of {@link PowerComponent} to string containing a list of the components,
     * each separated by a comma and a space. (Example: "AUDIO, WIFI")
     */
    @NonNull
    public static String powerComponentsToString(Iterable<Integer> components) {
        StringBuilder builder = new StringBuilder();
        Iterator<Integer> componentsIterator = components.iterator();
        // Do first element separately to not start the list with a comma
        if (componentsIterator.hasNext()) {
            builder.append(powerComponentToString(componentsIterator.next()));
        }
        while (componentsIterator.hasNext()) {
            builder.append(
                    String.format(", %s", powerComponentToString(componentsIterator.next())));
        }
        return builder.toString();
    }

    /**
     * Convert list of strings to list of {@link PowerComponent}.
     */
    @NonNull
    public static Iterable<Integer> toPowerComponents(Iterable<String> components, boolean prefix) {
        List<Integer> powerComponents = new ArrayList<>();
        Iterator<String> componentsIterator = components.iterator();
        while (componentsIterator.hasNext()) {
            String component = componentsIterator.next();
            powerComponents.add(toPowerComponent(component, prefix));
        }
        return powerComponents;
    }
}
