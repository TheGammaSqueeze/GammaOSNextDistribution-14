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

package com.android.bedstead.nene.resources;

import com.android.bedstead.nene.annotations.Experimental;

/**
 * TestApi to access system level resources.
 */
@Experimental
public final class SystemResources extends ResourcesWrapper {

    private static final android.content.res.Resources sResources =
            android.content.res.Resources.getSystem();
    static final SystemResources sInstance = new SystemResources(sResources);

    private SystemResources(android.content.res.Resources resources) {
        super(resources);
    }
}
