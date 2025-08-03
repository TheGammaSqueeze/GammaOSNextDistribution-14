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

package com.android.sts.common.util;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/** Provide a way for tests to get their description while running. */
public class DescriptionProvider extends TestWatcher {
    private volatile Description description;

    @Override
    protected void starting(Description description) {
        this.description = description;
    }

    public Description getDescription() {
        return description;
    }
}
