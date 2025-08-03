/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.js;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A value object to be represented as a JSON structure in the auction script. If you already have a
 * JSON string you should use {@link JSScriptJsonArgument}.
 */
public class JSScriptRecordArgument extends JSScriptArgument {
    private final List<JSScriptArgument> mFields;

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public JSScriptRecordArgument(String name, List<JSScriptArgument> fields) {
        super(name);
        this.mFields = fields;
    }

    /** Returns a copy of the current object and adds the given {@code additionalFields}. */
    public JSScriptRecordArgument getCopyWithFields(List<JSScriptArgument> additionalFields) {
        Objects.requireNonNull(additionalFields);
        List<JSScriptArgument> newFields = new ArrayList<>(mFields);
        newFields.addAll(additionalFields);
        return new JSScriptRecordArgument(mName, newFields);
    }

    @Override
    public String initializationValue() {
        StringBuilder result = new StringBuilder();
        result.append("{");
        boolean firstArg = true;
        for (JSScriptArgument field : mFields) {
            result.append(
                    String.format(
                            "%s\n\"%s\": %s",
                            firstArg ? "" : ",", field.name(), field.initializationValue()));
            firstArg = false;
        }
        result.append("\n}");
        return result.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JSScriptRecordArgument)) return false;
        JSScriptRecordArgument that = (JSScriptRecordArgument) o;
        return mFields.equals(that.mFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mFields);
    }

    @Override
    public String toString() {
        return "JSScriptRecordArgument{" + "mFields=" + mFields + '}';
    }
}
