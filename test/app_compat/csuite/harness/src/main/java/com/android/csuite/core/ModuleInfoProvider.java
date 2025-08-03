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

package com.android.csuite.core;

import com.android.tradefed.config.IConfiguration;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.MustBeClosed;

import java.io.IOException;
import java.util.stream.Stream;

/** An interface for providing module configuration contents. */
public interface ModuleInfoProvider {
    @VisibleForTesting String MODULE_INFO_PROVIDER_OBJECT_TYPE = "MODULE_INFO_PROVIDER";

    final class ModuleInfo {
        private final String mName;
        private final String mContent;

        public ModuleInfo(String name, String content) {
            mName = name;
            mContent = content;
        }

        public String getName() {
            return mName;
        }

        public String getContent() {
            return mContent;
        }
    }

    /**
     * Returns a stream of module configuration contents.
     *
     * @param configuration TradeFed suite configuration.
     * @return A stream of ModuleInfo objects.
     * @throws IOException if any IO exception occurs.
     */
    @MustBeClosed
    Stream<ModuleInfo> get(IConfiguration configuration) throws IOException;
}
