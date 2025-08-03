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
package com.android.eventlib.events.services

import com.android.eventlib.events.services.ServiceBoundEvent.ServiceBoundEventQuery
import com.android.eventlib.events.services.ServiceConfigurationChangedEvent.ServiceConfigurationChangedEventQuery
import com.android.eventlib.events.services.ServiceCreatedEvent.ServiceCreatedEventQuery
import com.android.eventlib.events.services.ServiceDestroyedEvent.ServiceDestroyedEventQuery
import com.android.eventlib.events.services.ServiceLowMemoryEvent.ServiceLowMemoryEventQuery
import com.android.eventlib.events.services.ServiceMemoryTrimmedEvent.ServiceMemoryTrimmedEventQuery
import com.android.eventlib.events.services.ServiceReboundEvent.ServiceReboundEventQuery
import com.android.eventlib.events.services.ServiceStartedEvent.ServiceStartedEventQuery
import com.android.eventlib.events.services.ServiceTaskRemovedEvent.ServiceTaskRemovedEventQuery
import com.android.eventlib.events.services.ServiceUnboundEvent.ServiceUnboundEventQuery

/**
 * Quick access to event queries about services.
 */
interface ServiceEvents {
    /**
     * Query for when an service is created.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun serviceCreated(): ServiceCreatedEventQuery

    /**
     * Query for when an service is started.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun serviceStarted(): ServiceStartedEventQuery

    /**
     * Query for when an service is destroyed.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun serviceDestroyed(): ServiceDestroyedEventQuery

    /**
     * Query for when an service's configuration is changed.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun serviceConfigurationChanged(): ServiceConfigurationChangedEventQuery

    /**
     * Query for when an service has low memory.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun serviceLowMemory(): ServiceLowMemoryEventQuery

    /**
     * Query for when an service has it's memory trimmed.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun serviceMemoryTrimmed(): ServiceMemoryTrimmedEventQuery

    /**
     * Query for when an service is bound.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun serviceBound(): ServiceBoundEventQuery

    /**
     * Query for when an service is unbound.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun serviceUnbound(): ServiceUnboundEventQuery

    /**
     * Query for when an service is re-bound.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun serviceRebound(): ServiceReboundEventQuery

    /**
     * Query for when an service has a task removed.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun serviceTaskRemoved(): ServiceTaskRemovedEventQuery
}
