/*
 * Copyright (C) 2023, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#pragma once

#include <stdint.h>
#include <sys/cdefs.h>

#ifndef __STATSD_SUBS_MIN_API__
#define __STATSD_SUBS_MIN_API__ __ANDROID_API_U__
#endif

__BEGIN_DECLS

/**
 * Reason codes for why subscription callback was triggered.
 */
typedef enum AStatsManager_SubscriptionCallbackReason : uint32_t {
    /**
     * SubscriptionCallbackReason constant for subscription data transfer initiated by stats
     * service.
     *
     * Introduced in API 34.
     */
    ASTATSMANAGER_SUBSCRIPTION_CALLBACK_REASON_STATSD_INITIATED = 1,

    /**
     * SubscriptionCallbackReason constant for subscriber requesting flush of pending data.
     *
     * Introduced in API 34.
     */
    ASTATSMANAGER_SUBSCRIPTION_CALLBACK_REASON_FLUSH_REQUESTED = 2,

    /**
     * SubscriptionCallbackReason constant for final stream of data for a subscription.
     *
     * Introduced in API 34.
     */
    ASTATSMANAGER_SUBSCRIPTION_CALLBACK_REASON_SUBSCRIPTION_ENDED = 3,
} AStatsManager_SubscriptionCallbackReason;

/**
 * Callback interface for receiving subscription data by the stats service.
 *
 * This will be called on an arbitrary binder thread. There is a pool of such threads and there is a
 * no guarantee a single thread will be used even for the same subscription. Clients must ensure it
 * is safe to call callback from arbitrary threads.
 *
 * \param subscription_id the subscription id for which the callback is triggered.
 * \param reason code for why the callback is triggered.
 * \param payload encoded SubscriptionResults proto containing subscription data.
 *        Cannot be null.
 * \param num_bytes size in bytes of the payload.
 * \param cookie the opaque pointer passed in AStatsManager_addSubscription. Can be null.
 *
 * Introduced in API 34.
 */
typedef void (*AStatsManager_SubscriptionCallback)(int32_t subscription_id,
                                                   AStatsManager_SubscriptionCallbackReason reason,
                                                   uint8_t* _Nonnull payload, size_t num_bytes,
                                                   void* _Nullable cookie);

/**
 * Adds a new subscription.
 *
 * Requires caller is in the traced_probes selinux domain.
 *
 * \param subscription_config encoded ShellSubscription proto containing parameters for a new
 *        subscription. Cannot be null.
 * \param num_bytes size in bytes of the subscription_config.
 * \param callback function called to deliver subscription data back to the subscriber. Each
 *        callback can be used for more than one subscription. Cannot be null.
 * \param cookie opaque pointer to associate with the subscription. The provided callback will be
 *        invoked with this cookie as an argument when delivering data for this subscription. Can be
 *        null.
 * \return subscription ID for the new subscription. Subscription ID is a positive integer. A
 * negative value indicates an error.
 *
 * Introduced in API 34.
 */
int32_t AStatsManager_addSubscription(const uint8_t* _Nonnull subscription_config, size_t num_bytes,
                                      const AStatsManager_SubscriptionCallback _Nonnull callback,
                                      void* _Nullable cookie)
        __INTRODUCED_IN(__STATSD_SUBS_MIN_API__);

/**
 * Removes an existing subscription.
 * This will trigger a flush of the remaining subscription data through
 * AStatsManager_SubscriptionCallback with the reason as
 * ASTATSMANAGER_SUBSCRIPTION_CALLBACK_REASON_SUBSCRIPTION_ENDED.
 *
 * Requires caller is in the traced_probes selinux domain.
 *
 * \param subscription_id subscription id of the subscription to terminate.
 *
 * Introduced in API 34.
 */
void AStatsManager_removeSubscription(int32_t subscription_id)
        __INTRODUCED_IN(__STATSD_SUBS_MIN_API__);

/**
 * Request stats service to flush a subscription.
 * This will trigger AStatsManager_SubscriptionCallback with the reason as
 * ASTATSMANAGER_SUBSCRIPTION_CALLBACK_REASON_FLUSH_REQUESTED.
 *
 * Requires caller is in the traced_probes selinux domain.
 *
 * \param subscription_id ID of the subscription to be flushed.
 *
 * Introduced in API 34.
 */
void AStatsManager_flushSubscription(int32_t subscription_id)
        __INTRODUCED_IN(__STATSD_SUBS_MIN_API__);

__END_DECLS
