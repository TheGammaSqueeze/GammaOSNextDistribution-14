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
package com.android.tradefed.config;

import com.android.tradefed.config.remote.IRemoteFileResolver;
import com.android.tradefed.error.HarnessRuntimeException;
import com.android.tradefed.error.IHarnessException;
import com.android.tradefed.result.error.ErrorIdentifier;

import com.google.common.annotations.VisibleForTesting;

import java.util.Map;

import javax.annotation.Nullable;

/** Loads implementations of {@link IRemoteFileResolver}. */
public interface IFileResolverLoader {
    /**
     * Loads a resolver that can handle the provided scheme.
     *
     * @param scheme the URI scheme that the loaded resolver is expected to handle.
     * @param config a map of all dynamic resolver configuration key-value pairs specified by the
     *     'dynamic-resolver-args' TF command-line flag.
     * @throws ResolverLoadingException if the resolver that handles the specified scheme cannot be
     *     loaded and/or initialized.
     */
    @Nullable
    IRemoteFileResolver load(String scheme, Map<String, String> config);

    /** Exception thrown if a resolver cannot be loaded or initialized. */
    @VisibleForTesting
    static final class ResolverLoadingException extends HarnessRuntimeException {
        public ResolverLoadingException(@Nullable String message, ErrorIdentifier errorId) {
            super(message, errorId);
        }

        public ResolverLoadingException(@Nullable String message, IHarnessException cause) {
            super(message, cause);
        }
    }
}
