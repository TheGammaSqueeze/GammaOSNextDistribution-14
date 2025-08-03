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

package com.android.server.connectivity.mdns;

import android.annotation.NonNull;

import com.android.server.connectivity.mdns.util.MdnsUtils;

import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * This class provides {@link ScheduledExecutorService} instances to {@link MdnsServiceTypeClient}
 * instances, and provides method to shutdown all the created executors.
 */
public class ExecutorProvider {

    private final Set<ScheduledExecutorService> serviceTypeClientSchedulerExecutors =
            MdnsUtils.newSet();

    /** Returns a new {@link ScheduledExecutorService} instance. */
    public ScheduledExecutorService newServiceTypeClientSchedulerExecutor() {
        // TODO: actually use a pool ?
        ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);
        serviceTypeClientSchedulerExecutors.add(executor);
        return executor;
    }

    /** Shuts down all the created {@link ScheduledExecutorService} instances. */
    public void shutdownAll() {
        for (ScheduledExecutorService executor : serviceTypeClientSchedulerExecutors) {
            if (executor.isShutdown()) {
                continue;
            }
            executor.shutdownNow();
        }
        serviceTypeClientSchedulerExecutors.clear();
    }

    /**
     * Shutdown one executor service and remove the executor service from the set.
     * @param executorService the executorService to be shutdown
     */
    public void shutdownExecutorService(@NonNull ScheduledExecutorService executorService) {
        if (!executorService.isShutdown()) {
            executorService.shutdownNow();
        }
        serviceTypeClientSchedulerExecutors.remove(executorService);
    }
}