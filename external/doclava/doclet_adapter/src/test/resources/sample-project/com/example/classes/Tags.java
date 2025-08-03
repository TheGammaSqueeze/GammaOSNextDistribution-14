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

package com.example.classes;

public class Tags {

    /**
     * @see PublicClass
     * @see PublicAnnotation with comments
     * @see <a href="https://example.com">html link</a>
     */
    public static class See {

    }

    /**
     * @throws IllegalArgumentException
     * @throws IllegalArgumentException with some comments
     */
    public static class Throws {

    }

    /**
     * First sentence of the class that consists of three tags: text, inline ({@code something})
     * and text. Then goes the second sentence of class javadoc. It is followed by a third
     * sentence that has some inline tags, such as {@code some code},
     * {@link java.lang.Integer label} and {@linkplain java.lang.Byte label}.
     *
     * @author someone with a very long name
     * @version version
     * @see Throws
     * @since 1.0
     */
    public static class Various {

        /**
         * @serial field description
         * @see #method(String) for details
         */
        private int serial;

        /**
         * First sentence of method javadoc. Then goes the second sentence which contains some
         * inline tags, such as {@code code block} and {@link #serial label}.
         *
         * @exception IllegalArgumentException iae desc
         * @throws NullPointerException npe desc
         * @param x description of param named x
         * @return number, always zero
         * @see Integer
         * @since 1.1
         */
        public Integer method(String x) throws Exception {
            return 0;
        }

        /**
         * This method is marked as deprecated in javadoc with a tag.
         *
         * @deprecated
         */
        public void deprecatedMethod() {

        }

    }

    /**
     * Fixture to test {@link com.google.doclava.javadoc.ParamTagImpl}.
     *
     * @param <T> stored in Box
     */
    public static class Box<T> {
        private T storedObject;
        private int price;

        private Box() {}

        /**
         * @param storedObject Something valuable
         * @param price
         */
        public Box(T storedObject, int price) {
            this.storedObject = storedObject;
            this.price = price;
        }

        public T value() {
            return storedObject;
        }
    }

    /**
     * @see
     */
    public class Malformed {

    }
}
