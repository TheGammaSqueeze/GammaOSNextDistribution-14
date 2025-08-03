/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.content.res;

import org.junit.Test;

import static android.util.TypedValue.TYPE_ATTRIBUTE;
import static android.util.TypedValue.TYPE_DIMENSION;
import static android.util.TypedValue.TYPE_FLOAT;
import static android.util.TypedValue.TYPE_INT_BOOLEAN;
import static android.util.TypedValue.TYPE_INT_COLOR_ARGB4;
import static android.util.TypedValue.TYPE_INT_COLOR_ARGB8;
import static android.util.TypedValue.TYPE_INT_COLOR_RGB4;
import static android.util.TypedValue.TYPE_INT_COLOR_RGB8;
import static android.util.TypedValue.TYPE_INT_DEC;
import static android.util.TypedValue.TYPE_INT_HEX;
import static android.util.TypedValue.TYPE_NULL;
import static android.util.TypedValue.TYPE_REFERENCE;
import static android.util.TypedValue.TYPE_STRING;
import static org.junit.Assert.assertEquals;

public class BridgeTypedArrayTest {

    @Test
    public void getType() {
        assertEquals(TYPE_NULL, BridgeTypedArray.getType(null));
        assertEquals(TYPE_STRING, BridgeTypedArray.getType(""));
        assertEquals(TYPE_REFERENCE, BridgeTypedArray.getType("@drawable/my_drawable"));
        assertEquals(TYPE_ATTRIBUTE, BridgeTypedArray.getType("?attr/colorPrimary"));
        assertEquals(TYPE_INT_BOOLEAN, BridgeTypedArray.getType("true"));
        assertEquals(TYPE_STRING, BridgeTypedArray.getType("False"));
        assertEquals(TYPE_INT_HEX, BridgeTypedArray.getType("0xffa39d"));
        assertEquals(TYPE_STRING, BridgeTypedArray.getType("0xnothex"));
        assertEquals(TYPE_INT_COLOR_RGB4, BridgeTypedArray.getType("#f34"));
        assertEquals(TYPE_INT_COLOR_ARGB4, BridgeTypedArray.getType("#2f34"));
        assertEquals(TYPE_INT_COLOR_RGB8, BridgeTypedArray.getType("#f34ab4"));
        assertEquals(TYPE_INT_COLOR_ARGB8, BridgeTypedArray.getType("#1f34dc28"));
        assertEquals(TYPE_STRING, BridgeTypedArray.getType("#notacolor"));
        assertEquals(TYPE_DIMENSION, BridgeTypedArray.getType("16dp"));
        assertEquals(TYPE_DIMENSION, BridgeTypedArray.getType(".16dp"));
        assertEquals(TYPE_DIMENSION, BridgeTypedArray.getType("9999.dp"));
        assertEquals(TYPE_STRING, BridgeTypedArray.getType("16notaunit"));
        assertEquals(TYPE_INT_DEC, BridgeTypedArray.getType("98543"));
        assertEquals(TYPE_FLOAT, BridgeTypedArray.getType("43.364"));
        assertEquals(TYPE_STRING, BridgeTypedArray.getType("5432dp342"));
    }
}
