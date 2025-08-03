/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.shell;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Class to provide Supplier for {@link ShellCommandFactory} required by {@link
 * AdServicesShellCommandHandler}
 */
public abstract class ShellCommandFactorySupplier {
    /**
     * Add more per API shell factory implementations as we create them.
     *
     * @return {@link Supplier} with Array of {@link ShellCommandFactory}
     */
    public abstract ImmutableList<ShellCommandFactory> getAllShellCommandFactories();

    public ImmutableMap<String, ShellCommandFactory> getShellCommandFactories() {
        return ImmutableMap.copyOf(
                getAllShellCommandFactories().stream()
                        .collect(
                                Collectors.toMap(
                                        ShellCommandFactory::getCommandPrefix,
                                        Function.identity())));
    }
}
