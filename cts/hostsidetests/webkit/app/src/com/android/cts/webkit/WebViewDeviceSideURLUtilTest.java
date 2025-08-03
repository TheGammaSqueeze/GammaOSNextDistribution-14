/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.cts.webkit;

import static org.junit.Assert.assertEquals;

import android.webkit.URLUtil;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the {@link com.android.MyAwesomeApi}.
 *
 * <p>These test methods have additional setup and post run checks done host side by {@link
 * com.android.cts.myawesomeapi.MyAwesomeApiHostTest}.
 */
@RunWith(AndroidJUnit4.class)
public final class WebViewDeviceSideURLUtilTest {
    /** See {@code Manifest#LOG_COMPAT_CHANGE} */
    private static final String LOG_COMPAT_CHANGE = "android.permission.LOG_COMPAT_CHANGE";

    /** See {@code Manifest#READ_COMPAT_CHANGE_CONFIG} */
    private static final String READ_COMPAT_CHANGE_CONFIG =
            "android.permission.READ_COMPAT_CHANGE_CONFIG";

    @Before
    public void setUp() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(READ_COMPAT_CHANGE_CONFIG, LOG_COMPAT_CHANGE);
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    /* Test run by WebViewHostSideURLUtilTest.testGuessFileNameChangeDisabled */
    @Test
    public void guessFileName_legacyBehavior() {
        // Tests for the URL + MimeType parts of guessing
        assertEquals(
                "Path segment should be extracted from URL",
                "image.gif",
                URLUtil.guessFileName(
                        "https://example.com/resources/image?size=large", null, "image/gif"));

        assertEquals(
                "Keep the extension when it matches the mime type",
                "image.gif",
                URLUtil.guessFileName(
                        "https://example.com/resources/image.gif?size=large", null, "image/gif"));

        assertEquals(
                "Replace an extension when the extension and mime types don't match",
                "image.gif",
                URLUtil.guessFileName(
                        "https://example.com/resources/image.png?size=large", null, "image/gif"));

        assertEquals(
                "HTML mime type should yield .html extension",
                "index.html",
                URLUtil.guessFileName("https://example.com/index", null, "text/html"));

        assertEquals(
                "Unknown text/ mime type should yield .txt extension",
                "index.txt",
                URLUtil.guessFileName("https://example.com/index", null, "text/fantasy"));

        assertEquals(
                "Fallback filename is 'downloadfile.bin'",
                "downloadfile.bin",
                URLUtil.guessFileName("https://example.com/", null, null));

        // Tests for content disposition + mime type
        assertEquals(
                "The content disposition file name should be preferred over URL",
                "Test.png",
                URLUtil.guessFileName(
                        "https://example.com/wrong", "attachment; filename=Test.png", "image/png"));

        assertEquals(
                "Content disposition file name should have correct extension replaced",
                "Test.gif",
                URLUtil.guessFileName(
                        "https://example.com/wrong", "attachment; filename=Test.png", "image/gif"));

        assertEquals(
                "Inline content disposition should be ignored",
                "test.png",
                URLUtil.guessFileName(
                        "https://example.com/test.png", "inline; filename=Wrong.png", null));

        //        assertEquals(
        //                "Presence of semicolon after filename will break parsing despite RFC",
        //                "test.bin",
        //                URLUtil.guessFileName(
        //                        "https://example.com/test.png", "attachment; filename=Wrong.png;",
        // null));

        assertEquals(
                "Presence of filename* after filename will break parsing",
                "test.bin",
                URLUtil.guessFileName(
                        "https://example.com/test.png",
                        "attachment; filename=Wrong.png; filename*=UTF-8''Wrong.png",
                        null));

        // Tests for parsing of Content-Disposition
        assertEquals(
                "Slashes will only use the latter part of the filename",
                "Test.png",
                guessForContentDisposition("attachment; filename=Wrong/Test.png"));

        assertEquals(
                "Slashes in escape sequences only use the latter part of the filename",
                "Test.png",
                guessForContentDisposition("attachment; filename=\"Wrong\\/Test.png\""));

        assertEquals(
                "Unknown dispositions are treated as 'attachment'",
                "Test.png",
                guessForContentDisposition("unknown; filename=Test.png"));

        assertEquals(
                "disposition attribute names are case insensitive",
                "Test.png",
                guessForContentDisposition("attachment; fIlEnAmE=Test.png"));

        assertEquals(
                "Unquoted disposition values are accepted for filename",
                "Test.png",
                guessForContentDisposition("attachment; filename=Test.png"));

        assertEquals(
                "Double quoted filenames are accepted",
                "Test.png",
                guessForContentDisposition("attachment; filename=\"Test.png\""));

        assertEquals(
                "Spaces in double quoted strings are accepted",
                "Te st.png",
                guessForContentDisposition("attachment ; filename = \"Te st.png\""));

        assertEquals(
                "Spaces in single quoted strings are accepted",
                "Te st.png",
                guessForContentDisposition("attachment ; filename = 'Te st.png'"));

        assertEquals(
                "Extra spaces after unquoted value are discarded",
                "Test.png",
                guessForContentDisposition("attachment ; filename = Test.png  ;"));

        assertEquals(
                "disposition type is case insensitive",
                "Test.png",
                guessForContentDisposition("Attachment; filename=\"Test.png\""));

        assertEquals(
                "Filename value can be single-quoted",
                "Test.png",
                guessForContentDisposition("attachment; filename='Test.png'"));

        assertEquals(
                "A semicolon after the last value is ignored",
                "Test.png",
                guessForContentDisposition("attachment; filename=\"Test.png\";"));

        assertEquals(
                "Semicolon can appear in single-quoted strings",
                "Test;.png",
                guessForContentDisposition("attachment; filename='Test;.png'"));

        assertEquals(
                "Semicolon can appear in double-quoted strings",
                "Test;.png",
                guessForContentDisposition("attachment; filename=\"Test;.png\""));

        assertEquals(
                "An equals sign in a single-quoted string is accepted",
                "Test=.png",
                guessForContentDisposition("attachment; filename='Test=.png'"));

        assertEquals(
                "An equals sign in a double-quoted string is accepted",
                "Test=.png",
                guessForContentDisposition("attachment; filename=\"Test=.png\""));
    }

    /* Test run by WebViewHostSideURLUtilTest.testGuessFileNameChangeEnabled */
    @Test
    public void guessFileName_usesRfc6266() {
        // Tests for the URL + MimeType parts of guessing
        assertEquals(
                "Path segment should be extracted from URL",
                "image.gif",
                URLUtil.guessFileName(
                        "https://example.com/resources/image?size=large", null, "image/gif"));

        assertEquals(
                "Keep the extension when it matches the mime type",
                "image.gif",
                URLUtil.guessFileName(
                        "https://example.com/resources/image.gif?size=large", null, "image/gif"));

        assertEquals(
                "Append an extension when the extension and mime types don't match",
                "image.png.gif",
                URLUtil.guessFileName(
                        "https://example.com/resources/image.png?size=large", null, "image/gif"));

        assertEquals(
                "HTML mime type should yield .html extension",
                "index.html",
                URLUtil.guessFileName("https://example.com/index", null, "text/html"));

        assertEquals(
                "Unknown text/ mime type should yield .txt extension",
                "index.txt",
                URLUtil.guessFileName("https://example.com/index", null, "text/fantasy"));

        assertEquals(
                "Fallback filename is 'downloadfile.bin'",
                "downloadfile.bin",
                URLUtil.guessFileName("https://example.com/", null, null));

        // Tests for content disposition + mime type
        assertEquals(
                "The content disposition file name should be preferred over URL",
                "Test.png",
                URLUtil.guessFileName(
                        "https://example.com/wrong", "attachment; filename=Test.png", "image/png"));

        assertEquals(
                "Content disposition file name should have correct extension appended",
                "Test.png.gif",
                URLUtil.guessFileName(
                        "https://example.com/wrong", "attachment; filename=Test.png", "image/gif"));

        assertEquals(
                "Inline content disposition should be ignored",
                "test.png",
                URLUtil.guessFileName(
                        "https://example.com/test.png", "inline; filename=Wrong.png", null));

        // Tests for parsing of Content-Disposition
        assertEquals(
                "Slashes should be replaced with underscores",
                "Test_Test.png",
                guessForContentDisposition("attachment; filename=Test/Test.png"));

        assertEquals(
                "Slashes should be replaced with underscores in single quoted strings",
                "Test_Test.png",
                guessForContentDisposition("attachment; filename='Test/Test.png'"));

        assertEquals(
                "Slashes in escape sequences should be replaced with underscores",
                "Test_Test.png",
                guessForContentDisposition("attachment; filename=\"Test\\/Test.png\""));

        assertEquals(
                "The filename* from content disposition should be preferred",
                "Test.png",
                guessForContentDisposition(
                        "attachment; filename=\"Wrong.png\"; filename*=utf-8''Test.png"));

        assertEquals(
                "Unknown dispositions are treated as 'attachment'",
                "Test.png",
                guessForContentDisposition("unknown; filename=Test.png"));

        assertEquals(
                "disposition attribute names are case insensitive",
                "Test.png",
                guessForContentDisposition("attachment; fIlEnAmE=Test.png"));

        assertEquals(
                "Unquoted disposition values are accepted for filename",
                "Test.png",
                guessForContentDisposition("attachment; filename=Test.png"));

        assertEquals(
                "Double quoted filenames are accepted",
                "Test.png",
                guessForContentDisposition("attachment; filename=\"Test.png\""));

        assertEquals(
                "Spaces in double quoted strings are accepted",
                "Te st.png",
                guessForContentDisposition("attachment ; filename = \"Te st.png\""));

        assertEquals(
                "Spaces in single quoted strings are accepted",
                "Te st.png",
                guessForContentDisposition("attachment ; filename = 'Te st.png'"));

        assertEquals(
                "Extra spaces after unquoted value are discarded",
                "Test.png",
                guessForContentDisposition("attachment ; filename = Test.png  ;"));

        assertEquals(
                "disposition type is case insensitive",
                "Test.png",
                guessForContentDisposition("Attachment; filename=\"Test.png\""));

        assertEquals(
                "Filename value can be single-quoted",
                "Test.png",
                guessForContentDisposition("attachment; filename='Test.png'"));

        assertEquals(
                "A semicolon after the last value is ignored",
                "Test.png",
                guessForContentDisposition("attachment; filename=\"Test.png\";"));

        assertEquals(
                "A filename* value can be read without special characters",
                "Test.png",
                guessForContentDisposition("attachment; filename*=UTF-8''Test.png"));

        assertEquals(
                "The value of filename* will be preferred over filename in either order",
                "Test.png",
                guessForContentDisposition(
                        "attachment; filename*=UTF-8''Test.png; filename=\"Wrong.png\""));

        assertEquals(
                "The value of filename* will be preferred over filename in either order",
                "Test.png",
                guessForContentDisposition(
                        "attachment; filename=\"Wrong.png\"; filename*=utf-8''Test.png"));

        assertEquals(
                "A language parameter in filename* is ignored",
                "£ rates.bin",
                guessForContentDisposition(
                        "attachment; filename*=iso-8859-1'en'%A3%20rates.bin;"
                                + " filename=\"Wrong.png\""));

        assertEquals(
                "Encoding name can be upper case",
                "£ and € rates.bin",
                guessForContentDisposition(
                        "attachment; filename=\"Wrong.png\" ; "
                                + "filename*=UTF-8''%c2%a3%20and%20%e2%82%ac%20rates.bin"));

        assertEquals(
                "An invalid encoded filename* will be ignored",
                "Test.png",
                guessForContentDisposition(
                        "attachment; filename=\"Test.png\"; filename*=UTF-8''%broken"));

        assertEquals(
                "Unknown encodings are ignored",
                "Test.png",
                guessForContentDisposition(
                        "attachment; filename=\"Test.png\"; filename*=UTF-9''Wrong.png"));

        assertEquals(
                "Semicolon can appear in single-quoted strings",
                "Test;.png",
                guessForContentDisposition("attachment; filename='Test;.png'"));

        assertEquals(
                "Semicolon can appear in double-quoted strings",
                "Test;.png",
                guessForContentDisposition("attachment; filename=\"Test;.png\""));

        assertEquals(
                "Semicolon in single-quoted string still allows filename* to be used",
                "Test.png",
                guessForContentDisposition(
                        "attachment; filename='Wrong;.png'; filename*=utf-8''Test.png"));

        assertEquals(
                "Semicolon in double-quoted string still allows filename* to be used",
                "Test.png",
                guessForContentDisposition(
                        "attachment; filename='Wrong;.png'; filename*=utf-8''Test.png"));

        assertEquals(
                "An equals sign in a single-quoted string is accepted",
                "Test=.png",
                guessForContentDisposition("attachment; filename='Test=.png'"));

        assertEquals(
                "An equals sign in a double-quoted string is accepted",
                "Test=.png",
                guessForContentDisposition("attachment; filename=\"Test=.png\""));

        assertEquals(
                "An equals sign in a single-quoted filename does not block filename*",
                "Test.png",
                guessForContentDisposition(
                        "attachment; filename='Wrong=.png'; filename*=utf-8''Test.png"));

        assertEquals(
                "An equals sign in a double-quoted filename does not block filename*",
                "Test.png",
                guessForContentDisposition(
                        "attachment; filename=\"Wrong=.png\"; filename*=utf-8''Test.png"));

        assertEquals(
                "Single quotes can be escaped in a single-quoted filename",
                "Test'.png",
                guessForContentDisposition("attachment; filename='Test\\'.png'"));

        assertEquals(
                "Double quotes can be escaped in a double-quoted filename",
                "Test\".png",
                guessForContentDisposition("attachment; filename=\"Test\\\".png\""));

        assertEquals(
                "The backslash character can be escaped in a single-quoted filename",
                "Test\\*.png",
                guessForContentDisposition("attachment; filename='Test\\\\\\*.png'"));

        assertEquals(
                "The backslash character can be escaped in a double-quoted filename",
                "Test\\*.png",
                guessForContentDisposition("attachment; filename=\"Test\\\\\\*.png\""));
    }

    /** Helper method to shorten tests by always providing a URL and mimetype to guessFileName */
    private String guessForContentDisposition(String contentDisposition) {
        return URLUtil.guessFileName(
                "https://example.com/url_value_was_used_wrongly", contentDisposition, null);
    }
}
