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

package com.android.bedstead.harrier.annotations.enterprise;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to annotate an enterprise policy for use with {@link PolicyDoesNotApplyTest} and
 * {@link PolicyAppliesTest}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnterprisePolicy {

    /**
     * An enterprise policy which can be controlled using permissions.
     */
    @interface Permission {
        /** The permission required to exercise the policy. */
        String appliedWith();
        /** Flags indicating who the policy applies to when applied in this way. */
        int appliesTo();
        /** Additional modifiers. */
        int modifiers() default NO;
    }

    /**
     * An enterprise policy which can be controlled user app ops.
     */
    @interface AppOp {
        /** The AppOp required to exercise the policy. */
        String appliedWith();
        /** Flags indicating who the policy applies to when applied in this way. */
        int appliesTo();
        /** Additional modifiers. */
        int modifiers() default NO;
    }

    /** A policy that cannot be applied. */
    int NO = 0;

    /** A policy which applies to the user of the package which applied the policy. */
    int APPLIES_TO_OWN_USER = 1;
    /** A policy which applies to unaffiliated other users. */
    int APPLIES_TO_UNAFFILIATED_OTHER_USERS = 1 << 1;
    /** A policy which applies to affiliated other users. */
    int APPLIES_TO_AFFILIATED_OTHER_USERS = 1 << 2;
    /** A policy which applies to unaffiliated profiles of the user of the package which applied the policy. */
    int APPLIES_TO_UNAFFILIATED_CHILD_PROFILES_WITHOUT_INHERITANCE = 1 << 3;

    /** A policy that is inherited by child profiles if applied on parent. */
    int INHERITABLE = 1 << 4;

    int APPLIES_TO_UNAFFILIATED_CHILD_PROFILES = APPLIES_TO_UNAFFILIATED_CHILD_PROFILES_WITHOUT_INHERITANCE | INHERITABLE;

    /** A policy which applies to affiliated profiles of the user of the package which applied the policy. */
    int APPLIES_TO_AFFILIATED_CHILD_PROFILES = 1 << 5;
    /** A policy that applies to the parent of the profile of the package which applied the policy. */
    int APPLIES_TO_PARENT = 1 << 6;

    /** A policy that applies to affiliated or unaffiliate profiles of the package which applied the policy. */
    int APPLIES_TO_CHILD_PROFILES =
            APPLIES_TO_UNAFFILIATED_CHILD_PROFILES | APPLIES_TO_AFFILIATED_CHILD_PROFILES;
    /** A policy that applies to affiliated or unaffiliated other users. */
    int APPLIES_TO_OTHER_USERS =
            APPLIES_TO_UNAFFILIATED_OTHER_USERS | APPLIES_TO_AFFILIATED_OTHER_USERS;

    /** A policy that applies to all users on the device. */
    int APPLIES_GLOBALLY = APPLIES_TO_OWN_USER | APPLIES_TO_OTHER_USERS
            | APPLIES_TO_CHILD_PROFILES | APPLIES_TO_PARENT;


    // Applied by

    /** A policy that can be applied by a system device owner. */
    int APPLIED_BY_SYSTEM_DEVICE_OWNER = 1 << 7;
    /** A policy that can be applied by a single user device owner on headless. */
    int APPLIED_BY_SINGLE_DEVICE_OWNER = 1 << 8;

    /** A policy that can be applied by a system device owner or a main user device owner. */
    int APPLIED_BY_DEVICE_OWNER =
            APPLIED_BY_SYSTEM_DEVICE_OWNER
                    | APPLIED_BY_SINGLE_DEVICE_OWNER;
    /** A policy that can be applied by a profile owner of an unaffiliated profile. */
    int APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE = 1 << 9;
    /** A policy that can be applied by a profile owner of an affiliated profile */
    int APPLIED_BY_AFFILIATED_PROFILE_OWNER_PROFILE = 1 << 10;
    /** A policy that can be applied by a profile owner of an organization owned profile */
    int APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE = 1 << 11;

    /** A policy that can be applied by a profile owner of an affiliated or unaffiliated profile. */
    int APPLIED_BY_PROFILE_OWNER_PROFILE =
            APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_PROFILE
                    | APPLIED_BY_AFFILIATED_PROFILE_OWNER_PROFILE
                    | APPLIED_BY_ORGANIZATION_OWNED_PROFILE_OWNER_PROFILE;
    /**
     * A policy that can be applied by a Profile Owner for a User (not Profile) with no Device
     * Owner.
     */
    int APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO = 1 << 12;
    /**
     * A policy that can be applied by an unaffiliated Profile Owner for a User (not Profile) with
     * a Device Owner.
     */
    int APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER_WITH_DO = 1 << 13;
    /** A policy that can be applied by a profile owner of an unaffiliated user. */
    int APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER =
            APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO
                    | APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER_WITH_DO;
    /** A policy that can be applied by a profile owner of an affiliated user. */
    int APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER = 1 << 14;
    /** A policy that can be applied by an affiliated or unaffiliated profile owner on a User (not Profile). */
    int APPLIED_BY_PROFILE_OWNER_USER =
            APPLIED_BY_UNAFFILIATED_PROFILE_OWNER_USER | APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER;
    /** A policy that can be applied by an affiliated profile owner on a user or profile. */
    int APPLIED_BY_AFFILIATED_PROFILE_OWNER = APPLIED_BY_AFFILIATED_PROFILE_OWNER_PROFILE | APPLIED_BY_AFFILIATED_PROFILE_OWNER_USER;
    /** A policy that can be applied by a profile owner, affiliate or unaffiliated, running on a user or profile. */
    int APPLIED_BY_PROFILE_OWNER =
            APPLIED_BY_PROFILE_OWNER_PROFILE
            | APPLIED_BY_PROFILE_OWNER_USER;

    int APPLIED_BY_PARENT_INSTANCE_OF_NON_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE = 1 << 15;
    int APPLIED_BY_PARENT_INSTANCE_OF_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE = 1 << 16;

    int APPLIED_BY_PARENT_INSTANCE_OF_PROFILE_OWNER_PROFILE =
            APPLIED_BY_PARENT_INSTANCE_OF_NON_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE | APPLIED_BY_PARENT_INSTANCE_OF_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE;

    // Modifiers
    /** Internal use only. Do not use */
    // This is to be used to mark specific annotations as not generating PolicyDoesNotApply tests
    int DO_NOT_APPLY_TO_POLICY_DOES_NOT_APPLY_TESTS = 1 << 17;

    /** Internal use only. Do not use */
    // This is to be used to mark specific annotations as not generating PolicyDoesNotApply tests
    int DO_NOT_APPLY_TO_CANNOT_SET_POLICY_TESTS = 1 << 18;

    /** A policy that the DPM Role Holder has permission access to */
    int APPLIED_BY_DPM_ROLE_HOLDER = 1 << 19 | (DO_NOT_APPLY_TO_CANNOT_SET_POLICY_TESTS);

    /**
     * A policy which applies even when the user is not in the foreground.
     *
     * <p>Note that lacking this flag does not mean a policy does not apply - to indicate that use
     * {@link DOES_NOT_APPLY_IN_BACKGROUND}. */
    int APPLIES_IN_BACKGROUND = 1 << 20 | (DO_NOT_APPLY_TO_POLICY_DOES_NOT_APPLY_TESTS);
    /**
     * A policy which does not apply when the user is not in the foreground.
     *
     * <p>At present this does not generate any additional tests but may do in future.
     *
     * <p>Note that lacking this flag does not mean a policy does apply - to indicate that use
     * {@link APPLIES_IN_BACKGROUND}. */
    int DOES_NOT_APPLY_IN_BACKGROUND = 1 << 21;


    /**
     * A policy which can be applied by a delegate.
     *
     * See {@link #delegatedScopes()} for the scopes which enable this.
     */
    int CAN_BE_DELEGATED = 1 << 22;

    /** A policy that can be applied by a financed device owner. */
    int APPLIED_BY_FINANCED_DEVICE_OWNER = 1 << 23;

    /** A policy that has not yet been migrated to allow for DPM Role holder access. */
    int CANNOT_BE_APPLIED_BY_ROLE_HOLDER = 1 << 24;

    /** Flags indicating DPC states which can set the policy. */
    int[] dpc() default {};

    /**
     * {@link Permission} indicating which permissions can control the policy.
     *
     * <p>Note that this currently does not generate any additional tests but may do in future.
     */
    Permission[] permissions() default {};

    /**
     * {@link AppOp} indicating which AppOps can control the policy.
     *
     * <p>Note that this currently does not generate any additional tests but may do in future.
     */
    AppOp[] appOps() default {};

    /**
     * Which delegated scopes can control the policy.
     *
     * <p>This applies to {@link #dpc()} entries with the {@link #CAN_BE_DELEGATED} flag.
     */
    String[] delegatedScopes() default {};
}
