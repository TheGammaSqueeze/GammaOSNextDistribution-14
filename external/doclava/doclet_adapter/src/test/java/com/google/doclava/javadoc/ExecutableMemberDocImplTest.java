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

package com.google.doclava.javadoc;

import static org.junit.Assert.assertFalse;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class ExecutableMemberDocImplTest extends BaseTest {

    private ConstructorDocImpl empty;

    @Before
    public void setUp() {
        super.setUp();
        empty = ConstructorDocImpl.create(CONSTRUCTOR.empty, context);
    }

    @Ignore("Not yet implemented")
    @Test
    public void isNative() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void isSynchronized() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void isVarArgs() {
    }

    @Test
    public void isSynthetic() {
        assertFalse(empty.isSynthetic());
    }

    @Ignore("Not yet implemented")
    @Test
    public void isIncluded() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void throwsTags() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void paramTags() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void typeParamTags() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void thrownExceptions() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void thrownExceptionTypes() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void parameters() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void receiverType() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void typeParameters() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void signature() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void flatSignature() {
    }
}