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
package tests.java.lang;
import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.Before;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ClassLoaderTest {

    class TestClassLoader extends ClassLoader {
        public Class<?> findSystemClassWrapper(String s) throws ClassNotFoundException {
            return findSystemClass(s);
        }

        public Class<?> defineClassWrapper(String name, byte[] b, int off, int len)
                throws ClassFormatError {
            return defineClass(name, b, off, len);
        }

        public void setSignersWrapper(Class<?> c, Object[] signers) {
            setSigners(c, signers);
        }
    }

    private TestClassLoader loader;

    @Before
    public void setUp() throws Exception {
        loader = new TestClassLoader();
    }

    @Test
    public void test_findSystemClass() {
        try {
            loader.findSystemClassWrapper("java.lang.String");
        } catch (ClassNotFoundException cnfe) {
            throw new RuntimeException(cnfe);
        }
        try {
            loader.findSystemClassWrapper("nonexistentclass");
            fail("Expected ClassNotFoundException");
        } catch (ClassNotFoundException cnfe) {
        }
    }

    @Test
    public void test_defineClass() {
        try {
            byte b[] = new byte[1];
            loader.defineClassWrapper("java.lang.String", b, 0, 0);
            fail();
        } catch (UnsupportedOperationException e) {
            assertEquals(e.getMessage(), "can't load this type of class file");
        }
    }

    // setSigners does nothing
    @Test
    public void test_setSigners() {
        Object[] signers = new Object[] { null };
        loader.setSignersWrapper(Object.class, signers);
    }
}