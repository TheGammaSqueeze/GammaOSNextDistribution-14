/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tradefed.invoker.shard;

import com.google.internal.android.engprod.v1.ProvideTestTargetRequest;
import com.google.internal.android.engprod.v1.ProvideTestTargetResponse;
import com.google.internal.android.engprod.v1.RequestTestTargetRequest;
import com.google.internal.android.engprod.v1.RequestTestTargetResponse;

/**
 * Wrapper interface for the sharding client
 *
 * <p>This exists so that we can swap in an HTTP one or testing one if needed.
 */
public interface IDynamicShardingClient {
    /** Provide a test target to the server */
    public ProvideTestTargetResponse provideTestTarget(ProvideTestTargetRequest request);

    /** Request a test target from the server */
    public RequestTestTargetResponse requestTestTarget(RequestTestTargetRequest request);
}
