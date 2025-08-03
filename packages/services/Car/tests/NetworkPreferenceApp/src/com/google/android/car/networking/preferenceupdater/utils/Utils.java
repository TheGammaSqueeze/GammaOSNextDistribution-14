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
package com.google.android.car.networking.preferenceupdater.utils;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiNetworkSuggestion;
import android.text.TextUtils;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Utilities */
public final class Utils {
    /** Convert list of strings to string */
    public static String toString(Set<String> st) {
        return TextUtils.join(",", st);
    }

    /** Converts comma separated string to set of strings */
    public static Set<String> toSet(String st) {
        return Stream.of(TextUtils.split(st, ",")).collect(Collectors.toSet());
    }

    /** Builds wifi suggestions based on provided ssid and whether it is oem paid */
    public static WifiNetworkSuggestion buildWifiSuggestion(String ssid, boolean isOemPaid) {
        WifiNetworkSuggestion.Builder builder = new WifiNetworkSuggestion.Builder();
        String[] elements = ssid.split(":");
        builder.setSsid(WifiInfo.sanitizeSsid(elements[0]));
        if (elements.length > 1) {
            builder.setWpa2Passphrase(elements[1]);
        }
        if (isOemPaid) {
            builder.setOemPaid(true);
        } else {
            builder.setOemPrivate(true);
        }
        return builder.build();
    }
}
