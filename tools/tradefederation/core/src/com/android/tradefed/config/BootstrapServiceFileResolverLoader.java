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
package com.android.tradefed.config;

import com.android.tradefed.config.remote.IRemoteFileResolver;
import com.android.tradefed.result.error.InfraErrorIdentifier;

import com.google.common.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Loads resolvers using the service loading facility.
 *
 * <p>Unlike the one embedded in the {@link DynamicRemoteFileResolver} class, this implementation is
 * very simple, returning a new instance of the requested resolver type each time it is called. This
 * makes the fundemental assumption that resolvers are easy to initialize and don't require {@link
 * Option}-annotated fields in their configuration. This is true for most of our resolvers today,
 * and should be true of any resolver that is suitably generic to be used for config bootstrapping.
 */
@ThreadSafe
@VisibleForTesting
public class BootstrapServiceFileResolverLoader implements IFileResolverLoader {
    // We need the indirection since in production we use the context class loader that is
    // defined when loading and not the one at construction.
    private final Supplier<ClassLoader> mClassLoaderSupplier;

    BootstrapServiceFileResolverLoader() {
        mClassLoaderSupplier = () -> Thread.currentThread().getContextClassLoader();
    }

    BootstrapServiceFileResolverLoader(ClassLoader classLoader) {
        mClassLoaderSupplier = () -> classLoader;
    }

    @Override
    public synchronized IRemoteFileResolver load(String scheme, Map<String, String> config) {
        ServiceLoader<IRemoteFileResolver> serviceLoader =
                ServiceLoader.load(IRemoteFileResolver.class, mClassLoaderSupplier.get());

        for (IRemoteFileResolver resolver : serviceLoader) {
            if (scheme.equals(resolver.getSupportedProtocol())) {
                try {
                    OptionSetter setter = new OptionSetter(resolver);
                    Collection<String> missingOptions = setter.getUnsetMandatoryOptions();
                    if (!missingOptions.isEmpty()) {
                        throw new ResolverLoadingException(
                                String.format(
                                        "Mandatory options for resolver %s are not allowed in a"
                                                + " bootstrap context",
                                        resolver.toString()),
                                InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
                    }
                } catch (ConfigurationException e) {
                    throw new ResolverLoadingException("Failed to load resolver", e);
                }
                return resolver;
            }
        }

        throw new ResolverLoadingException(
                String.format("Unsupported protocol for dynamic download %s", scheme),
                InfraErrorIdentifier.OPTION_CONFIGURATION_ERROR);
    }
}
