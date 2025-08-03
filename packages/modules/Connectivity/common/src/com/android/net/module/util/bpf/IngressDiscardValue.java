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

package com.android.net.module.util.bpf;

import com.android.net.module.util.Struct;

/** Value type for ingress discard map */
public class IngressDiscardValue extends Struct {
    // Allowed interface indexes.
    // Use the same value for iif1 and iif2 if there is only a single allowed interface index.
    @Field(order = 0, type = Type.S32)
    public final int iif1;
    @Field(order = 1, type = Type.S32)
    public final int iif2;

    public IngressDiscardValue(final int iif1, final int iif2) {
        this.iif1 = iif1;
        this.iif2 = iif2;
    }
}
