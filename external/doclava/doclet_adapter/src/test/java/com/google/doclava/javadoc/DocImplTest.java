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

public class DocImplTest extends BaseTest {

    private ClassDocImpl see;
    private ClassDocImpl throwz;
    private ClassDocImpl various;

    @Before
    public void setUp() {
        super.setUp();

        see = ClassDocImpl.create(CLASS.tags$See, context);
        throwz = ClassDocImpl.create(CLASS.tags$Throws, context);
        various = ClassDocImpl.create(CLASS.tags$Various, context);
    }

    @Test
    public void commentText() {
        assertEquals("", see.commentText());
        assertEquals("", throwz.commentText());
        assertEquals("""
                        First sentence of the class that consists of three tags: text, inline ({@code something})
                         and text. Then goes the second sentence of class javadoc. It is followed by a third
                         sentence that has some inline tags, such as {@code some code},
                         {@link java.lang.Integer label} and {@linkplain java.lang.Byte label}.""",
                various.commentText());
    }

    @Test
    public void tags() {
        assertEquals(3, see.tags().length);
        assertEquals(2, throwz.tags().length);
        assertEquals(4, various.tags().length);
    }

    @Test
    public void tags_ofKind() {
        assertEquals(3, see.tags("@see").length);
        assertEquals(0, see.tags("@throws").length);

        assertEquals(2, throwz.tags("@throws").length);
        assertEquals(0, throwz.tags("@see").length);

        assertEquals(1, various.tags("@see").length);
        assertEquals(0, various.tags("@custom").length);
    }

    @Test
    public void seeTags() {
        assertEquals(3, see.seeTags().length);
        assertEquals(0, throwz.seeTags().length);
        assertEquals(1, various.seeTags().length);
    }

    @Test
    public void inlineTags() {
        Tag[] inlineTags = various.inlineTags();
        assertEquals(9, inlineTags.length);

        assertEquals("@text", inlineTags[0].name());
        assertEquals("@code", inlineTags[1].name());
        assertEquals("@text", inlineTags[2].name());
        assertEquals("@code", inlineTags[3].name());
        assertEquals("@text", inlineTags[4].name());
        assertEquals("@link", inlineTags[5].name());
        assertEquals("@text", inlineTags[6].name());
        assertEquals("@linkplain", inlineTags[7].name());
        assertEquals("@text", inlineTags[8].name());
    }

    @Test
    public void firstSentenceTags() {
        Tag[] fst = various.firstSentenceTags();
        assertEquals(3, fst.length);
        assertEquals("@text", fst[0].name());
        assertEquals("First sentence of the class that consists of three tags: text, inline (",
                fst[0].text());
        assertEquals("@code", fst[1].name());
        assertEquals("{@code something}", fst[1].text());

        assertEquals("@text", fst[2].name());
        assertEquals(")\n and text.", fst[2].text());
    }

    /**
     * @see DocImpl#getRawCommentText()
     */
    @Test
    public void getRawCommentText() {
        // To be 1-1 compatible with previous implementation, this test case should be used:
        String expectedIdentical = """
                 First sentence of the class that consists of three tags: text, inline ({@code something})
                 and text. Then goes the second sentence of class javadoc. It is followed by a third
                 sentence that has some inline tags, such as {@code some code},
                 {@link java.lang.Integer label} and {@linkplain java.lang.Byte label}.

                 @author someone with a very long name
                 @version version
                 @see Throws
                 @since 1.0
                """;

        // This is a "prettified" version which is produced by new implementation.
        String expected = """
                First sentence of the class that consists of three tags: text, inline ({@code something})
                 and text. Then goes the second sentence of class javadoc. It is followed by a third
                 sentence that has some inline tags, such as {@code some code},
                 {@link java.lang.Integer label} and {@linkplain java.lang.Byte label}.
                @author someone with a very long name
                @version version
                @see Throws
                @since 1.0""";

        String actual = various.getRawCommentText();

        assertEquals(expected, actual);
    }

    @Ignore("Not yet implemented")
    @Test
    public void setRawCommentText() {
    }
}