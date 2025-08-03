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

package com.android.managedprovisioning.task;

import static java.util.Objects.requireNonNull;

import android.content.Context;

import com.android.managedprovisioning.model.ProvisioningParams;

import java.util.function.Consumer;

/**
 * A mock {@link AbstractProvisioningTask}.
 */
public final class MockTask extends AbstractProvisioningTask {
    private final Consumer<AbstractProvisioningTask> mConsumer;

    /**
     * Constructs a {@link MockTask} with a given {@link Consumer} to execute in its {@link #run}
     * method.
     */
    public MockTask(
            Context context,
            ProvisioningParams provisioningParams,
            Callback callback,
            Consumer<AbstractProvisioningTask> consumer) {
        super(context, provisioningParams, callback);
        mConsumer = requireNonNull(consumer);
    }

    @Override
    public void run(int userId) {
        mConsumer.accept(this);
    }
}
