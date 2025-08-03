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

import com.android.bedstead.harrier.annotations.enterprise.EnterprisePolicy;

/**
 * A DTO class that is used to contain an {@link EnterprisePolicy}
 * along with the class name of the policy that it is being used for
 */
public final class EnterprisePolicyWrapper {

    private String mPolicyClass;
    private int[] mDpc;
    private EnterprisePolicy.Permission[] mPermissions;
    private EnterprisePolicy.AppOp[] mAppOps;
    private String[] mDelegatedScopes;

    public EnterprisePolicyWrapper(String policyClass, int[] dpc,
            EnterprisePolicy.Permission[] permissions, EnterprisePolicy.AppOp[] appOps,
            String[] delegatedScopes) {
        this.mPolicyClass = policyClass;
        this.mDpc = dpc;
        this.mPermissions = permissions;
        this.mAppOps = appOps;
        this.mDelegatedScopes = delegatedScopes;
    }

    public String policyClass() {
        return mPolicyClass;
    }

    public int[] dpc() {
        return mDpc;
    }

    public EnterprisePolicy.Permission[] permissions() {
        return mPermissions;
    }

    public EnterprisePolicy.AppOp[] appOps() {
        return mAppOps;
    }

    public String[] delegatedScopes() {
        return mDelegatedScopes;
    }
}
