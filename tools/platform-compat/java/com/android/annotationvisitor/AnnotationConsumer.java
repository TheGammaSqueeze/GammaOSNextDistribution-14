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

package com.android.annotationvisitor;

import java.util.Map;
import java.util.Set;

public interface AnnotationConsumer {
    /**
     * Handle a parsed annotation for a class member.
     *
     * @param apiSignature Signature of the class member.
     * @param annotationProperties Map of stringified properties of this annotation.
     * @param parsedFlags Array of flags parsed from the annotation for this member.
     */
    public void consume(String apiSignature, Map<String, String> annotationProperties,
            Set<String> parsedFlags);

    public void close();
}
