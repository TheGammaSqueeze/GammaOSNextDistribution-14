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

package com.android.federatedcompute.services.training.jni;

/**
 * This class provides callbacks for the C++ federated learning logic, "FL runner".
 *
 * <p>This interface is used by "fl_runner_jni.cc".
 */
public interface SimpleTaskEnvironment {
    /**
     * Returns the path of the directory that the C++ implementation should use to store persistent
     * files. If files created by the C++ runtime in this directory are deleted, it may not function
     * properly.
     */
    String getBaseDir();

    /**
     * Returns the path of the directory that the C++ implementation should use to store temporary
     * files. If files created by the C++ runtime in this directory are deleted, it will still
     * function properly.
     */
    String getCacheDir();

    /**
     * Checks whether the device conditions - e.g. Network, Battery, Idleness - allow for running a
     * federated computation.
     */
    boolean trainingConditionsSatisfied();

    /** Returns an {@link JavaExampleIterator} object. */
    JavaExampleIterator createExampleIterator(byte[] exampleSelector);

    /** Returns an {@link JavaExampleIterator} object. */
    JavaExampleIterator createExampleIteratorWithContext(
            byte[] exampleSelector, byte[] selectorContext);
}
