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

package com.android.bedstead.harrier;

import java.util.Set;

/**
 * This class must be extended for any
 * {@link com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy}
 * that needs to specify specific arguments for the test they are used on.
 */
public abstract class PolicyArguments<T> {
    protected T valueTypeIdentifier;

    /**
     * Arguments valid to the
     * {@link com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy}
     */
    public abstract Set<T> validArguments();
}
