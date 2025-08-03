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

package android.server.wm;

import android.text.TextUtils;

/**
 * A class to represent an {@link android.content.Intent} extra that can be passed as a command line
 * parameter. More options can be added as needed. Supported parameters are as follows
 *
 * <ol>
 *     <li>String</li>
 *     <li>boolean</li>
 * </ol>
 */
public final class CliIntentExtra {
    private final String mOption;
    private final String mKey;
    private final String mValue;

    private CliIntentExtra(String option, String key, String value) {
        if (TextUtils.isEmpty(option)) {
            throw new IllegalArgumentException("Option must not be empty");
        }
        if (TextUtils.isEmpty(key)) {
            throw new IllegalArgumentException("Key must not be empty");
        }
        if (TextUtils.isEmpty(value)) {
            throw new IllegalArgumentException("Value must not be empty");
        }
        mOption = option;
        mKey = key;
        mValue = value;
    }

    /**
     * Returns the option to be used when creating the command line option. The option is provided
     * in the constructor.
     *
     * @return {@link String} representing the option for the command line.
     */
    String option() {
        return mOption;
    }

    /**
     * Returns the key for the key-value pair that will be passed as an intent extra
     *
     * @return {@link String} representing the key for the {@link android.content.Intent} extra
     */
    String key() {
        return mKey;
    }

    /**
     * Returns the value for the key-value pair that will be passed as an
     * {@link android.content.Intent} extra.  All values are normalized to a {@link String} so they
     * can be passed as a command line argument.
     *
     * @return {@link String} representing the parsed value for the key-value pair
     */
    String value() {
        return mValue;
    }

    /**
     * Appends the command line option and arguments to the command line command. The option, key,
     * and value are appended separated by a space.
     *
     * @param sb {@link StringBuilder} representing the command
     */
    void appendTo(StringBuilder sb) {
        sb.append(" ").append(option()).append(" ").append(key()).append(" ").append(value());
    }

    /**
     * Creates a {@link CliIntentExtra} for {@link String} intent extra.
     *
     * @param key the key in the key-value pair passed into the {@link android.content.Intent} extra
     * @param value the value in the key-value pair pased into the {@link android.content.Intent}
     *              extra
     * @return {@link CliIntentExtra} to construct a command with the key value pair as parameters
     * for an {@link android.content.Intent}
     */
    public static CliIntentExtra extraString(String key, String value) {
        return new CliIntentExtra("--es", key, value);
    }

    /**
     * Creates a {@link CliIntentExtra} for {@link Boolean} intent extra.
     *
     * @param key the key in the key-value pair passed into the {@link android.content.Intent} extra
     * @param value the value in the key-value pair pased into the {@link android.content.Intent}
     *              extra
     * @return {@link CliIntentExtra} to construct a command with the key value pair as parameters
     * for an {@link android.content.Intent}
     */
    public static CliIntentExtra extraBool(String key, boolean value) {
        return new CliIntentExtra("--ez", key, Boolean.toString(value));
    }

    /**
     * Creates a {@link CliIntentExtra} for {@link Integer} intent extra.
     *
     * @param key the key in the key-value pair passed into the {@link android.content.Intent} extra
     * @param value the value in the key-value pair pased into the {@link android.content.Intent}
     *              extra
     * @return {@link CliIntentExtra} to construct a command with the key value pair as parameters
     * for an {@link android.content.Intent}
     */
    public static CliIntentExtra extraInt(String key, int value) {
        return new CliIntentExtra("--ei", key, Integer.toString(value));
    }
}
