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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.sun.javadoc.ProgramElementDoc;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class ParamTagImplTest extends BaseTest {

    // /**
    //  * @param <T> stored in Box
    //  */
    // public static class Box<T> { ... }
    private ParamTagImpl paramTag_classArg_T_withComment;

    // /**
    //  * @param storedObject Something valuable
    //  * @param price
    //  */
    // public Box(T storedObject, int price) { ... }
    private ParamTagImpl paramTag_methodArg_T_withComment;
    private ParamTagImpl paramTag_methodArg_int_emptyComment;

    @Before
    public void setUp() {
        super.setUp();

        ClassDocImpl box = ClassDocImpl.create(GENERIC.box, context);
        List<ParamTagImpl> tags = getParamTags(box);
        assertEquals(1, tags.size());
        paramTag_classArg_T_withComment = tags.get(0);

        ConstructorDocImpl ctor = ConstructorDocImpl.create(CONSTRUCTOR.paramTag_arg2_T_int,
                context);
        tags = getParamTags(ctor);
        assertEquals(2, tags.size());
        paramTag_methodArg_T_withComment = tags.get(0);
        paramTag_methodArg_int_emptyComment = tags.get(1);
    }

    private List<ParamTagImpl> getParamTags(ProgramElementDoc pe) {
        return Arrays.stream(pe.tags())
                .filter(tag -> tag instanceof ParamTagImpl)
                .map(tag -> (ParamTagImpl) tag)
                .toList();
    }

    @Test
    public void parameterName() {
        assertEquals("T", paramTag_classArg_T_withComment.parameterName());
        assertEquals("storedObject", paramTag_methodArg_T_withComment.parameterName());
        assertEquals("price", paramTag_methodArg_int_emptyComment.parameterName());
    }

    @Test
    public void parameterComment() {
        assertEquals("stored in Box", paramTag_classArg_T_withComment.parameterComment());
        assertEquals("Something valuable", paramTag_methodArg_T_withComment.parameterComment());
        assertEquals("", paramTag_methodArg_int_emptyComment.parameterComment());
    }

    @Test
    public void isTypeParameter() {
        assertTrue(paramTag_classArg_T_withComment.isTypeParameter());
        assertFalse(paramTag_methodArg_T_withComment.isTypeParameter());
        assertFalse(paramTag_methodArg_int_emptyComment.isTypeParameter());
    }
}