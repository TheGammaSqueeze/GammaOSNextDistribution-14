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

package com.android.bedstead.harrier.annotations;

/** Used to define the order in which the bedstead annotations are run based on their
 * {@code priority} .
 */
public final class AnnotationPriorityRunPrecedence {
    // Use to ensure that an annotation is the first to run.
    public static final int FIRST = 0;
    // To run early in the order
    public static final int EARLY = 5000;
    // To run around the middle of the order
    public static final int MIDDLE = 10000;
    // To run late in the order
    public static final int LATE = 15000;
    // To be used when it does not matter when the annotation is run.
    public static final int PRECEDENCE_NOT_IMPORTANT = 20000;
    // Use to ensure that an annotation is the last to run.
    public static final int LAST = Integer.MAX_VALUE;

    public static final int REQUIRE_RUN_ON_PRECEDENCE = EARLY - 1;
}
