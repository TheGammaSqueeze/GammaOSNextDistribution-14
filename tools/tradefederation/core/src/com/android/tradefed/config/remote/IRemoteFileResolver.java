/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.config.remote;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.error.InfraErrorIdentifier;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;

/**
 * Interface for objects that can resolve a remote file into a local one. For example:
 * gs://bucket/dir/file.txt would be downloaded and changed to a local path.
 */
public interface IRemoteFileResolver {

    /**
     * Resolve the remote file.
     *
     * @param consideredFile {@link File} evaluated as remote.
     * @return The resolved local file.
     * @throws BuildRetrievalError if something goes wrong.
     */
    public default @Nonnull File resolveRemoteFiles(File consideredFile)
            throws BuildRetrievalError {
        throw new BuildRetrievalError(
                "Should not have been called", InfraErrorIdentifier.ARTIFACT_UNSUPPORTED_PATH);
    }

    /**
     * Resolve the remote file.
     *
     * @param consideredFile {@link File} evaluated as remote.
     * @param queryArgs The arguments passed as a query to the URL.
     * @return The resolved local file.
     * @throws BuildRetrievalError if something goes wrong.
     */
    public default @Nonnull File resolveRemoteFiles(
            File consideredFile, Map<String, String> queryArgs) throws BuildRetrievalError {
        return resolveRemoteFiles(consideredFile);
    }

    /**
     * Resolve the remote file in a future-proof interface
     *
     * @param args {@link RemoteFileResolverArgs} describing the remote to download and how.
     * @return The resolved local file representation.
     * @throws BuildRetrievalError if something goes wrong.
     */
    public default @Nonnull ResolvedFile resolveRemoteFile(RemoteFileResolverArgs args)
            throws BuildRetrievalError {
        File file = resolveRemoteFiles(args.getConsideredFile(), args.getQueryArgs());
        return new ResolvedFile(file);
    }

    /** Returns the associated protocol supported for download. */
    public @Nonnull String getSupportedProtocol();

    /**
     * Optional way for the implementation to receive an {@ink ITestDevice} representation of the
     * device under tests.
     *
     * @param device The {@link ITestDevice} of the current invocation.
     */
    public default void setPrimaryDevice(ITestDevice device) {
        // Do nothing by default
    }

    /** The args passed to the resolvers */
    public class RemoteFileResolverArgs {

        private File mConsideredFile;
        private Map<String, String> mQueryArgs = new LinkedHashMap<>();
        private File mDestinationDir;

        public RemoteFileResolverArgs setConsideredFile(File consideredFile) {
            mConsideredFile = consideredFile;
            return this;
        }

        public RemoteFileResolverArgs addQueryArg(String key, String value) {
            mQueryArgs.put(key, value);
            return this;
        }

        public RemoteFileResolverArgs addQueryArgs(Map<String, String> queryArgs) {
            mQueryArgs.putAll(queryArgs);
            return this;
        }

        public RemoteFileResolverArgs setDestinationDir(File destinationDir) {
            mDestinationDir = destinationDir;
            return this;
        }

        public File getConsideredFile() {
            return mConsideredFile;
        }

        public Map<String, String> getQueryArgs() {
            return mQueryArgs;
        }

        public File getDestinationDir() {
            return mDestinationDir;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((mConsideredFile == null) ? 0 : mConsideredFile.hashCode());
            result = prime * result + ((mDestinationDir == null) ? 0 : mDestinationDir.hashCode());
            result = prime * result + ((mQueryArgs == null) ? 0 : mQueryArgs.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            RemoteFileResolverArgs other = (RemoteFileResolverArgs) obj;
            if (mConsideredFile == null) {
                if (other.mConsideredFile != null) {
                    return false;
                }
            } else if (!mConsideredFile.equals(other.mConsideredFile)) {
                return false;
            }
            if (mDestinationDir == null) {
                if (other.mDestinationDir != null) {
                    return false;
                }
            } else if (!mDestinationDir.equals(other.mDestinationDir)) {
                return false;
            }
            if (mQueryArgs == null) {
                if (other.mQueryArgs != null) {
                    return false;
                }
            } else if (!mQueryArgs.equals(other.mQueryArgs)) {
                return false;
            }
            return true;
        }
    }

    /** Class holding information about the resolved file and some metadata. */
    public class ResolvedFile {
        private File mResolvedFile;
        private boolean mShouldCleanUp = true;

        public ResolvedFile(File resolvedFile) {
            mResolvedFile = resolvedFile;
        }

        public File getResolvedFile() {
            return mResolvedFile;
        }

        /**
         * Whether the resolved file should be deleted at the end of the invocation or not. Set to
         * false for a file that shouldn't be deleted. For example: a local file you own.
         */
        public ResolvedFile cleanUp(boolean cleanUp) {
            mShouldCleanUp = cleanUp;
            return this;
        }

        public boolean shouldCleanUp() {
            return mShouldCleanUp;
        }
    }
}
