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

package com.android.ondevicepersonalization.services.display.velocity;

import org.owasp.encoder.Encode;

/**
 * Tool containing methods passed to the velocity engine
 * to be used during rendering within the template
 */
public class OnDevicePersonalizationVelocityTool {

    /**
     * Encode string for HTML context.
     */
    public static String encodeHtml(String s) {
        return Encode.forHtml(s);
    }

    /**
     * Encode string for URL context.
     */
    public static String encodeUrl(String s) {
        return Encode.forUriComponent(s);
    }

    /**
     * Encode string for JS context.
     */
    public static String encodeJs(String s) {
        return Encode.forJavaScript(s);
    }

    /**
     * Encode string for CSS string context.
     */
    public static String encodeCssString(String s) {
        return Encode.forCssString(s);
    }

    /**
     * Encode string for CSS URL context.
     */
    public static String encodeCssUrl(String s) {
        return Encode.forCssUrl(s);
    }
}
