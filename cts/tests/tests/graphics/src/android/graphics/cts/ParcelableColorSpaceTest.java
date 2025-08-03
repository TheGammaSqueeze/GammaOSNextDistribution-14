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

package android.graphics.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;

import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.ParcelableColorSpace;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@SmallTest
@RunWith(JUnitParamsRunner.class)
public class ParcelableColorSpaceTest {

    public Object[] getNamedColorSpaces() {
        ColorSpace.Named[] names = ColorSpace.Named.values();
        Object[] colorSpaces = new Object[names.length];
        for (int i = 0; i < names.length; i++) {
            colorSpaces[i] = ColorSpace.get(names[i]);
        }
        return colorSpaces;
    }

    @Test
    @Parameters(method = "getNamedColorSpaces")
    public void testNamedReadWrite(ColorSpace colorSpace) {
        Parcel parcel = Parcel.obtain();
        try {
            ParcelableColorSpace inParcelable = new ParcelableColorSpace(colorSpace);
            parcel.writeParcelable(inParcelable, 0);
            parcel.setDataPosition(0);
            ParcelableColorSpace outParcelable = parcel.readParcelable(
                    ParcelableColorSpace.class.getClassLoader());
            assertNotNull(outParcelable);
            assertEquals(inParcelable, outParcelable);
            assertEquals(inParcelable.getColorSpace(), outParcelable.getColorSpace());
            // Because these are named, they should all be the same instances
            assertSame(colorSpace, outParcelable.getColorSpace());
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void testReadWriteCustom() {
        float[] xyz = new float[] {1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f};
        ColorSpace colorSpace = new ColorSpace.Rgb("DemoSpace", xyz, 1.9);
        Parcel parcel = Parcel.obtain();
        try {
            ParcelableColorSpace inParcelable = new ParcelableColorSpace(colorSpace);
            parcel.writeParcelable(inParcelable, 0);
            parcel.setDataPosition(0);
            ParcelableColorSpace outParcelable = parcel.readParcelable(
                    ParcelableColorSpace.class.getClassLoader());
            assertNotNull(outParcelable);
            assertEquals(inParcelable, outParcelable);
            assertEquals(inParcelable.getColorSpace(), outParcelable.getColorSpace());
            assertSame(colorSpace, inParcelable.getColorSpace());
            assertNotSame(colorSpace, outParcelable.getColorSpace());
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void testWriteInvalid() {
        float[] xyz = new float[] {1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f};
        ColorSpace colorSpace = new ColorSpace.Rgb("DemoSpace", xyz,
                x -> x, y -> y * 2);
        assertThrows(IllegalArgumentException.class, () -> {
            ParcelableColorSpace inParcelable = new ParcelableColorSpace(colorSpace);
        });
    }

    @Test
    @Parameters(method = "getNamedColorSpaces")
    public void testIsParcelableNamed(ColorSpace colorSpace) {
        assertTrue(ParcelableColorSpace.isParcelable(colorSpace));
        // Just make sure the constructor doesn't throw
        assertEquals(colorSpace, new ParcelableColorSpace(colorSpace).getColorSpace());
    }

    @Test
    public void testIsParceableCustom() {
        float[] xyz = new float[] {1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f};
        ColorSpace colorSpace = new ColorSpace.Rgb("DemoSpace", xyz, 1.9);
        assertTrue(ParcelableColorSpace.isParcelable(colorSpace));
        // Just make sure the constructor doesn't throw
        assertEquals(colorSpace, new ParcelableColorSpace(colorSpace).getColorSpace());
    }

    @Test
    public void testIsParcelableInvalid() {
        float[] xyz = new float[] {1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f};
        ColorSpace colorSpace = new ColorSpace.Rgb("DemoSpace", xyz,
                x -> x, y -> y * 2);
        assertFalse(ParcelableColorSpace.isParcelable(colorSpace));
    }

    @Test
    public void testIsColorSpaceContainer() {
        ColorSpace colorSpace = ColorSpace.get(ColorSpace.Named.BT2020);
        ParcelableColorSpace parcelableColorSpace = new ParcelableColorSpace(colorSpace);
        Bitmap bitmap = Bitmap.createBitmap(10, 10,
                Bitmap.Config.RGBA_F16, false, parcelableColorSpace.getColorSpace());
        assertNotNull(bitmap);
        ColorSpace bitmapColorSpace = bitmap.getColorSpace();
        assertNotNull(bitmapColorSpace);
        assertEquals(colorSpace.getId(), bitmapColorSpace.getId());
        assertEquals(parcelableColorSpace.getColorSpace(), bitmapColorSpace);
    }
}
