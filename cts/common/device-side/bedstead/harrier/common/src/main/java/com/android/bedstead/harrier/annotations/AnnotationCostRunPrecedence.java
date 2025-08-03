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

/**
 * Used to define the order in which the test methods are run based on their {@code cost} .
 */
public final class AnnotationCostRunPrecedence {
    // Use to ensure that the test method is the first to run.
    public static final int LOW = 0;
    // To run around the middle in the order.
    public static final int MIDDLE = 5000;
    // To run last of the order.
    public static final int HIGH = 10000;
}
