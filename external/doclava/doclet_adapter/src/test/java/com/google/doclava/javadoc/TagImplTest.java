/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.sun.javadoc.Tag;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class TagImplTest extends BaseTest {

    private ClassDocImpl various;
    private Tag author;
    private Tag version;
    private Tag see;
    private Tag since;

    @Before
    public void setUp() {
        super.setUp();

        various = ClassDocImpl.create(CLASS.tags$Various, context);

        author = various.tags()[0];
        version = various.tags()[1];
        see = various.tags()[2];
        since = various.tags()[3];
    }

    @Test
    public void name() {
        assertEquals("@author", author.name());
        assertEquals("@version", version.name());
        assertEquals("@see", see.name());
        assertEquals("@since", since.name());
    }

    @Test
    public void holder() {
        assertEquals(various, author.holder());
        assertEquals(various, see.holder());
    }

    @Test
    public void kind() {
        assertEquals("@author", author.kind());
        assertEquals("@see", see.kind());
    }

    @Test
    public void kind_remapKindHelper() {
        // Exceptional cases
        assertEquals("throws", TagImpl.remapKind("exception"));
        assertEquals("see", TagImpl.remapKind("link"));
        assertEquals("see", TagImpl.remapKind("linkplain"));
        assertEquals("serial", TagImpl.remapKind("serialData"));

        // Otherwise, should be an `identity` function
        assertEquals("see", TagImpl.remapKind("see"));
        assertEquals("author", TagImpl.remapKind("author"));
        assertEquals("customTag", TagImpl.remapKind("customTag"));
    }

    @Test
    public void text() {
        assertEquals("@author someone with a very long name", author.text());
    }

    @Ignore("Not yet implemented")
    @Test
    public void inlineTags() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void firstSentenceTags() {
    }

    @Ignore("Not yet implemented")
    @Test
    public void position() {
    }
}