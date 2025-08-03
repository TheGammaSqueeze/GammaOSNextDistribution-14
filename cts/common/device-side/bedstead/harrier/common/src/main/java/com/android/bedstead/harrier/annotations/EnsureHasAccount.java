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

import static com.android.bedstead.harrier.UserType.INSTRUMENTED_USER;
import static com.android.bedstead.harrier.annotations.EnsureHasNoAccounts.ENSURE_HAS_NO_ACCOUNTS_PRIORITY;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_MODIFY_ACCOUNTS;

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
@EnsureDoesNotHaveUserRestriction(DISALLOW_MODIFY_ACCOUNTS)
// TODO(263353411): Make this take a query argument the same as remotedpc - instead of individual parameters
public @interface EnsureHasAccount {

    String DEFAULT_ACCOUNT_KEY = "account";

    /** Key used to refer to the account. */
    String key() default DEFAULT_ACCOUNT_KEY;

    /** Which user type the account must be added on. */
    UserType onUser() default INSTRUMENTED_USER;

    /**
     * Features which the account must have.
     *
     * <p>Lack of a feature here indicates the account should not have this feature
     */
    String[] features() default {};

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
    int priority() default ENSURE_HAS_NO_ACCOUNTS_PRIORITY + 1;
}
