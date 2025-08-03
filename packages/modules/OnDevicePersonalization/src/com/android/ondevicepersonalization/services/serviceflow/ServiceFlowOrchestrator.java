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

package com.android.ondevicepersonalization.services.serviceflow;

import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;

/** Orchestrator that handles the scheduling of all service flows. */
public class ServiceFlowOrchestrator {

    ServiceFlowOrchestrator() {}

    private static class ServiceFlowOrchestratorLazyInstanceHolder {
        static final ServiceFlowOrchestrator LAZY_INSTANCE =
                new ServiceFlowOrchestrator();
    }

    /** Returns the global ServiceFlowOrchestrator. */
    public static ServiceFlowOrchestrator getInstance() {
        return ServiceFlowOrchestratorLazyInstanceHolder.LAZY_INSTANCE;
    }

    /** Schedules a given service flow task with the orchestrator. */
    public void schedule(ServiceFlowType serviceFlowType, Object... args) {
        ServiceFlow serviceFlow = ServiceFlowFactory.createInstance(serviceFlowType, args);

        ServiceFlowTask serviceFlowTask =
                new ServiceFlowTask(serviceFlowType, serviceFlow);

        var unused = switch (serviceFlowType.getPriority()) {
            case HIGH -> OnDevicePersonalizationExecutors.getHighPriorityBackgroundExecutor()
                    .submit(serviceFlowTask::run);
            case NORMAL -> OnDevicePersonalizationExecutors.getBackgroundExecutor()
                    .submit(serviceFlowTask::run);
            case LOW -> OnDevicePersonalizationExecutors.getLowPriorityBackgroundExecutor()
                    .submit(serviceFlowTask::run);
        };
    }
}
