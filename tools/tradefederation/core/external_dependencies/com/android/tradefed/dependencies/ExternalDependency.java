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

package com.android.tradefed.dependencies;

/** Base External Dependency class. */
public abstract class ExternalDependency {
    @Override
    public int hashCode() {
        // Information about a dependency class can be aggregated, regardless of the object
        return this.getClass().getName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        // Used to create a Set of ExternalDependencies regardless of the object
        return this.getClass().getName().equals(obj.getClass().getName());
    }
}
