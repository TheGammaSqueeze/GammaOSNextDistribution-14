/*
 * Copyright 2021, The Android Open Source Project
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

#include <interface/metrics/metrics.h>
#include <stdint.h>

/**
 * DOC: Metrics consumer
 *
 * Metrics consumer interface provides a way to report Trusty metrics event.
 * For example, Trusty kernel can connect to this service and report app
 * crashes.
 *
 * Metrics event reporters, e.g. kernel, are expected to connect to this service
 * and report events via TIPC. The service acknowledges each event with a
 * TIPC response.
 *
 * Format of the event messages are the same as the one defined by metrics
 * interface exposed to non-secure.
 */

#define METRICS_CONSUMER_PORT "com.android.trusty.metrics.consumer"
