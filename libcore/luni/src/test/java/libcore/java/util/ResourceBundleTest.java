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

package libcore.java.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import tests.support.resource.Support_Resources;

@RunWith(JUnit4.class)
public class ResourceBundleTest {

    private static final String PROP_RESOURCE_NAME = Support_Resources.RESOURCE_PACKAGE_NAME
            + ".hyts_resource";

    @Before
    public void setUp() {
        // Due to http://b/231440892, clear global cache before each test
        ResourceBundle.clearCache();
    }

    @After
    public void tearDown() {
        // Due to http://b/231440892, clear global cache after each test
        ResourceBundle.clearCache();
    }

    @Test
    public void testGetBundle_withControl() {
        Control propControl = Control.getControl(Control.FORMAT_PROPERTIES);
        Control classControl = Control.getControl(Control.FORMAT_CLASS);
        ClassLoader resClassLoader = Support_Resources.class.getClassLoader();
        Locale locale = Locale.getDefault();

        // Test for getBundle(String, Control)
        ResourceBundle bundle = ResourceBundle.getBundle(PROP_RESOURCE_NAME, propControl);
        assertEquals("parent", bundle.getString("property"));
        ResourceBundle.clearCache(resClassLoader);
        try {
            ResourceBundle.getBundle(PROP_RESOURCE_NAME, classControl);
            fail("ResourceBundle.getBundle() is expected to throw MissingResourceException");
        } catch (MissingResourceException e) {
            // expected
        }

        // clearCache() must be called after the previous lookup failure. See http://b/231440892.
        ResourceBundle.clearCache(resClassLoader);
        // Test for getBundle(String, Locale, Control)
        bundle = ResourceBundle.getBundle(PROP_RESOURCE_NAME, locale, propControl);
        assertEquals("parent", bundle.getString("property"));
        ResourceBundle.clearCache(resClassLoader);
        try {
            ResourceBundle.getBundle(PROP_RESOURCE_NAME, locale, classControl);
            fail("ResourceBundle.getBundle() is expected to throw MissingResourceException");
        } catch (MissingResourceException e) {
            // expected
        }

        // clearCache() must be called after the previous lookup failure. See http://b/231440892.
        ResourceBundle.clearCache(resClassLoader);
        // Test for getBundle(String, Locale, ClassLoader, Control)
        bundle = ResourceBundle.getBundle(PROP_RESOURCE_NAME, locale, resClassLoader, propControl);
        assertEquals("parent", bundle.getString("property"));

        // clearCache() and clearCache(resClassLoader) should have the same effect, because the
        // classes are in the same class loader.
        assertEquals(resClassLoader, ResourceBundleTest.class.getClassLoader());
        ResourceBundle.clearCache();
        try {
            ResourceBundle.getBundle(PROP_RESOURCE_NAME, locale, resClassLoader, classControl);
            fail("ResourceBundle.getBundle() is expected to throw MissingResourceException");
        } catch (MissingResourceException e) {
            // expected
        }
    }

    @Test
    public void testContainsKey() {
        ResourceBundle bundle = ResourceBundle.getBundle(PROP_RESOURCE_NAME);
        assertTrue(bundle.containsKey("property"));
        assertFalse(bundle.containsKey("anotherProperty"));
    }
}
