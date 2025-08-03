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

package com.android.tradefed.util;

import com.android.tradefed.log.LogUtil.CLog;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Utility methods for dealing with protobuf messages type-agnostically. */
public class ProtoUtil {

    /**
     * Get values of a nested field reference, i.e. field_1.field_2.field_3, from a proto message as
     * a list of strings. Returns an empty list when a field cannot be found.
     *
     * <p>If the field reference contains repeated fields, each instance is expanded, resulting in a
     * list of strings.
     *
     * @param message The protobuf {@link Message} or object to be parsed.
     * @param references A list of field references starting at the root of the message. e.g. if we
     *     want to read {@code field_2} under the value of {@code field_1} in {@code
     *     messageOrObject} the list would be {@code field1}, {@code field2}.
     * @return A list of all the fields values referred to by the reference. If {@code references}
     *     is empty, returns {@code message.toString()} as a list. If {@code references} is invalid,
     *     returns an empty list.
     */
    public static List<String> getNestedFieldFromMessageAsStrings(
            Message message, List<String> references) {
        return getNestedFieldFromMessageAsStringsHelper(message, references);
    }

    /**
     * A helper method to {@code getNestedFieldFromMessageAsStrings} where the "message" can be an
     * object in case we reach a primitive value field during recursive parsing.
     */
    private static List<String> getNestedFieldFromMessageAsStringsHelper(
            Object messageOrObject, List<String> references) {
        if (references.isEmpty()) {
            return Arrays.asList(String.valueOf(messageOrObject));
        }
        if (!(messageOrObject instanceof Message)) {
            CLog.e(
                    "Attempting to read field %s from object of type %s, "
                            + "which is not a proto message.",
                    references.get(0), messageOrObject.getClass());
            return new ArrayList<String>();
        }
        Message message = (Message) messageOrObject;
        String reference = references.get(0);
        FieldDescriptor fieldDescriptor = message.getDescriptorForType().findFieldByName(reference);
        if (fieldDescriptor == null) {
            CLog.e("Could not find field %s in message %s.", reference, message);
            return new ArrayList<String>();
        }
        Object fieldValue = message.getField(fieldDescriptor);
        if (fieldValue instanceof List) {
            return ((List<? extends Object>) fieldValue)
                    .stream()
                    .flatMap(
                            v ->
                                    getNestedFieldFromMessageAsStringsHelper(
                                                    v, references.subList(1, references.size()))
                                            .stream())
                    .collect(Collectors.toList());
        }
        return getNestedFieldFromMessageAsStringsHelper(
                fieldValue, references.subList(1, references.size()));
    }
}
