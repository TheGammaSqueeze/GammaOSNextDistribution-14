/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.webkit.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.webkit.MimeTypeMap;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MimeTypeMapTest extends SharedWebViewTest{

    private MimeTypeMap mMimeTypeMap;

    @Before
    public void setUp() throws Exception {
        mMimeTypeMap = MimeTypeMap.getSingleton();
    }

    @Override
    protected SharedWebViewTestEnvironment createTestEnvironment() {
        return new SharedWebViewTestEnvironment.Builder().build();
    }

    @Test
    public void testGetFileExtensionFromUrl() {
        assertEquals("html", MimeTypeMap.getFileExtensionFromUrl("http://localhost/index.html"));
        assertEquals("html", MimeTypeMap.getFileExtensionFromUrl("http://host/x.html?x=y"));
        assertEquals("", MimeTypeMap.getFileExtensionFromUrl("http://www.example.com/"));
        assertEquals("", MimeTypeMap.getFileExtensionFromUrl("https://example.com/foo"));
        assertEquals("", MimeTypeMap.getFileExtensionFromUrl(null));
        assertEquals("", MimeTypeMap.getFileExtensionFromUrl(""));
        assertEquals("", MimeTypeMap.getFileExtensionFromUrl("http://abc/&%$.()*"));

        // ToBeFixed: Uncomment the following line after fixing the implementation
        //assertEquals("", MimeTypeMap.getFileExtensionFromUrl("http://www.example.com"));
    }

    @Test
    public void testHasMimeType() {
        assertTrue(mMimeTypeMap.hasMimeType("audio/mpeg"));
        assertTrue(mMimeTypeMap.hasMimeType("text/plain"));

        assertFalse(mMimeTypeMap.hasMimeType("some_random_string"));

        assertFalse(mMimeTypeMap.hasMimeType(""));
        assertFalse(mMimeTypeMap.hasMimeType(null));
    }

    @Test
    public void testGetMimeTypeFromExtension() {
        assertEquals("audio/mpeg", mMimeTypeMap.getMimeTypeFromExtension("mp3"));
        assertEquals("application/zip", mMimeTypeMap.getMimeTypeFromExtension("zip"));

        assertNull(mMimeTypeMap.getMimeTypeFromExtension("some_random_string"));

        assertNull(mMimeTypeMap.getMimeTypeFromExtension(null));
        assertNull(mMimeTypeMap.getMimeTypeFromExtension(""));
    }

    @Test
    public void testHasExtension() {
        assertTrue(mMimeTypeMap.hasExtension("mp3"));
        assertTrue(mMimeTypeMap.hasExtension("zip"));

        assertFalse(mMimeTypeMap.hasExtension("some_random_string"));

        assertFalse(mMimeTypeMap.hasExtension(""));
        assertFalse(mMimeTypeMap.hasExtension(null));
    }

    @Test
    public void testGetExtensionFromMimeType() {
        assertEquals("mp3", mMimeTypeMap.getExtensionFromMimeType("audio/mpeg"));
        assertEquals("png", mMimeTypeMap.getExtensionFromMimeType("image/png"));
        assertEquals("zip", mMimeTypeMap.getExtensionFromMimeType("application/zip"));

        assertNull(mMimeTypeMap.getExtensionFromMimeType("some_random_string"));

        assertNull(mMimeTypeMap.getExtensionFromMimeType(null));
        assertNull(mMimeTypeMap.getExtensionFromMimeType(""));
    }

    @Test
    public void testGetSingleton() {
        MimeTypeMap firstMimeTypeMap = MimeTypeMap.getSingleton();
        MimeTypeMap secondMimeTypeMap = MimeTypeMap.getSingleton();

        assertSame(firstMimeTypeMap, secondMimeTypeMap);
    }
}
