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

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.config.DynamicRemoteFileResolver.ServiceFileResolverLoader;
import com.android.tradefed.config.remote.IRemoteFileResolver;
import com.android.tradefed.config.remote.IRemoteFileResolver.RemoteFileResolverArgs;
import com.android.tradefed.config.remote.IRemoteFileResolver.ResolvedFile;
import com.android.tradefed.invoker.logger.InvocationLocal;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple class that allows one to load files from a variety of places using URIs and the service
 * provider functionality.
 */
public class RemoteFileResolver {
    /**
     * Loads file resolvers using a dedicated {@link ServiceFileResolverLoader} that is scoped to
     * each invocation.
     */
    // TODO(hzalek): Store a DynamicRemoteFileResolver instance per invocation to avoid locals.
    private static final IFileResolverLoader DEFAULT_FILE_RESOLVER_LOADER =
            new IFileResolverLoader() {
                private final InvocationLocal<IFileResolverLoader> mInvocationLoader =
                        new InvocationLocal<IFileResolverLoader>() {
                            @Override
                            protected IFileResolverLoader initialValue() {
                                return new BootstrapServiceFileResolverLoader();
                            }
                        };

                @Override
                public IRemoteFileResolver load(String scheme, Map<String, String> config) {
                    return mInvocationLoader.get().load(scheme, config);
                }
            };

    public RemoteFileResolver() {}

    /**
     * Load a file specified by a URI and place it in the destination directory
     *
     * @param fileURI the file to load
     * @param destDir the destination to place the loaded file
     * @return a {@link File} object representing the loaded file
     * @throws BuildRetrievalError when the requested resource cannot be located
     */
    public static ResolvedFile resolveRemoteFile(URI fileURI, URI destDir)
            throws BuildRetrievalError {
        return resolveRemoteFile(fileURI, destDir, getDefaultResolver(fileURI, new HashMap<>()));
    }

    /**
     * Load a file specified by a URI and place it in the destination directory
     *
     * @param fileURI the file to load
     * @param destDir the destination to place the loaded file
     * @param resolver the {@link IRemoteFileResolver} to use to resolve the file
     * @return a {@link File} object representing the loaded file
     * @throws BuildRetrievalError when the requested resource cannot be located
     */
    public static ResolvedFile resolveRemoteFile(
            URI fileURI, URI destDir, IRemoteFileResolver resolver) throws BuildRetrievalError {
        RemoteFileResolverArgs args = new RemoteFileResolverArgs();
        args.setConsideredFile(new File(fileURI.toString()));
        args.setDestinationDir(new File(destDir));
        Map<String, String> queryArgs = new HashMap<>();
        String query = fileURI.getQuery();
        if (query != null) {
            Arrays.stream(query.split("&"))
                    .forEach(
                            x -> {
                                String[] splits = x.split("=");
                                queryArgs.put(splits[0], splits[1]);
                            });
        }
        args.addQueryArgs(queryArgs);
        return resolver.resolveRemoteFile(args);
    }

    /**
     * Load a file specified by a URI and place it in the destination directory
     *
     * @param fileURI the file to load (needed to determine protocol)
     * @param config the config with which to initialize the resolver
     * @return a {@link IRemoteFileResolver} object to load files for the given protocol
     */
    public static IRemoteFileResolver getDefaultResolver(URI fileURI, Map<String, String> config) {
        String scheme = fileURI.getScheme();
        IRemoteFileResolver resolver = DEFAULT_FILE_RESOLVER_LOADER.load(scheme, config);
        return resolver;
    }
}
