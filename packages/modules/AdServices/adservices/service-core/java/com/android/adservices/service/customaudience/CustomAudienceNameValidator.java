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

package com.android.adservices.service.customaudience;

import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_NAME_TOO_LONG;

import android.annotation.NonNull;

import com.android.adservices.service.common.Validator;

import com.google.common.collect.ImmutableCollection;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/** Validator for the {@code name} field of a Custom Audience. */
public class CustomAudienceNameValidator implements Validator<String> {
    private final int mCustomAudienceMaxNameSizeB;

    public CustomAudienceNameValidator(int customAudienceMaxNameSizeB) {
        mCustomAudienceMaxNameSizeB = customAudienceMaxNameSizeB;
    }

    /**
     * Validates the {@code name} field of a Custom Audience as follows:
     *
     * <ul>
     *   <li>Size is less than {@code customAudienceMaxNameSizeB} (set while instantiating a {@link
     *       CustomAudienceNameValidator})
     * </ul>
     *
     * @param name the {@code name} field of a Custom Audience.
     * @param violations the collection of violations to add to.
     */
    @Override
    public void addValidation(
            @NonNull String name, @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(violations);

        // Validate the size is within limits.
        int nameSizeB = name.getBytes(StandardCharsets.UTF_8).length;
        if (nameSizeB > mCustomAudienceMaxNameSizeB) {
            violations.add(
                    String.format(
                            Locale.ENGLISH,
                            VIOLATION_NAME_TOO_LONG,
                            mCustomAudienceMaxNameSizeB,
                            nameSizeB));
        }
    }
}
