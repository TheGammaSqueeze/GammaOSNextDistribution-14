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
package com.android.modules.utils.testing;

import com.android.modules.utils.testing.AbstractExtendedMockitoRule.AbstractBuilder;
import com.android.modules.utils.testing.ExtendedMockitoRule.Builder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rule to make it easier to use Extended Mockito:
 *
 * <ul>
 *   <li>Automatically creates and finishes the mock session.
 *   <li>Provides multiple ways to set which classes must be statically mocked or spied
 *   <li>Automatically starts mocking (so tests don't need a mockito runner or rule)
 *   <li>Automatically clears the inlined mocks at the end (to avoid OOM)
 *   <li>Allows other customization like strictness
 * </ul>
 * <p>Typical usage:
 * <pre class="prettyprint">
 * &#064;Rule
 * public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
 *     .spyStatic(SomeClassWithStaticMethodsToBeMocked)
 *     .build();
 * </pre>
 */
public final class ExtendedMockitoRule extends
        AbstractExtendedMockitoRule<ExtendedMockitoRule, Builder> {

    public ExtendedMockitoRule(Builder builder) {
        super(builder);
    }
    /**
     * Builder for the rule.
     */
    public static final class Builder extends AbstractBuilder<ExtendedMockitoRule, Builder> {

        /**
         * Constructs a builder for the giving test instance (typically {@code this}) and initialize
         * mocks on it.
         */
        public Builder(Object testClassInstance) {
            super(testClassInstance);
        }

        /**
         * Constructs a builder that doesn't initialize mocks.
         *
         * <p>Typically used on test classes that already initialize mocks somewhere else.
         */
        public Builder() {
            super();
        }

        /**
         * Builds the rule.
         */
        public ExtendedMockitoRule build() {
            return new ExtendedMockitoRule(this);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Repeatable(SpyStaticClasses.class)
    public @interface SpyStatic {
        Class<?> value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    public @interface SpyStaticClasses {
        SpyStatic[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Repeatable(MockStaticClasses.class)
    public @interface MockStatic {
        Class<?> value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    public @interface MockStaticClasses {
        MockStatic[] value();
    }
}
