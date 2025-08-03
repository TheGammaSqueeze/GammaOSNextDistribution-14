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

package com.android.bedstead.harrier.annotations;

import static com.android.bedstead.harrier.UserType.ANY;
import static com.android.bedstead.harrier.annotations.EnsureHasAccountAuthenticator.ENSURE_HAS_ACCOUNT_AUTHENTICATOR_PRIORITY;

import com.android.bedstead.harrier.UserType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ensure that an account exists.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
// TODO: Add options (features of the user, type of the user, etc.)
public @interface EnsureHasNoAccounts {

    int ENSURE_HAS_NO_ACCOUNTS_PRIORITY = ENSURE_HAS_ACCOUNT_AUTHENTICATOR_PRIORITY + 1;

    /** Which user type the account must not be present added on. */
    UserType onUser() default ANY;

    /** Exclude pre created accounts. */
    boolean allowPreCreatedAccounts() default true;

    /** Behaviour if there are some accounts for the user. */
    com.android.bedstead.harrier.annotations.FailureMode failureMode()
            default com.android.bedstead.harrier.annotations.FailureMode.SKIP;


     /**
     * Priority sets the order that annotations will be resolved.
     *
     * <p>Annotations with a lower priority will be resolved before annotations with a higher
     * priority.
     *
     * <p>If there is an order requirement between annotations, ensure that the priority of the
     * annotation which must be resolved first is lower than the one which must be resolved later.
     *
     * <p>Priority can be set to a {@link AnnotationPriorityRunPrecedence} constant, or to any {@link int}.
     */
    int priority() default ENSURE_HAS_NO_ACCOUNTS_PRIORITY;
}
