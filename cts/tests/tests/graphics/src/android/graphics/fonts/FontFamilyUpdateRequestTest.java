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

package android.graphics.fonts;

import static android.graphics.fonts.FontStyle.FONT_SLANT_ITALIC;
import static android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT;
import static android.graphics.fonts.FontStyle.FONT_WEIGHT_NORMAL;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.os.ParcelFileDescriptor;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public final class FontFamilyUpdateRequestTest {

    @Test
    public void font() {
        String postScriptName = "Test";
        FontStyle style = new FontStyle(FONT_WEIGHT_NORMAL, FONT_SLANT_UPRIGHT);
        List<FontVariationAxis> axes = Arrays.asList(
                new FontVariationAxis("wght", 100f),
                new FontVariationAxis("wdth", 100f));
        FontFamilyUpdateRequest.Font font = new FontFamilyUpdateRequest.Font.Builder(
                postScriptName, style).setAxes(axes).setIndex(5).build();
        assertThat(font.getPostScriptName()).isEqualTo(postScriptName);
        assertThat(font.getStyle()).isEqualTo(style);
        assertThat(font.getAxes()).containsExactlyElementsIn(axes).inOrder();
        assertThat(font.getIndex()).isEqualTo(5);

        // Invalid parameters
        assertThrows(NullPointerException.class, () ->
                new FontFamilyUpdateRequest.Font.Builder(null, style).setAxes(axes));
        assertThrows(IllegalArgumentException.class, () ->
                new FontFamilyUpdateRequest.Font.Builder("", style).setAxes(axes));
        assertThrows(NullPointerException.class, () ->
                new FontFamilyUpdateRequest.Font.Builder(postScriptName, null)
                        .setAxes(axes));
        assertThrows(NullPointerException.class, () ->
                new FontFamilyUpdateRequest.Font.Builder(postScriptName, style)
                        .setAxes(null));
        assertThrows(NullPointerException.class, () ->
                new FontFamilyUpdateRequest.Font.Builder(postScriptName, style)
                        .setAxes(Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class, () ->
                new FontFamilyUpdateRequest.Font.Builder(postScriptName, style).setIndex(-1));

    }

    @Test
    public void fontFamily() {
        String name = "test";
        FontFamilyUpdateRequest.Font font1 = new FontFamilyUpdateRequest.Font.Builder("Test",
                new FontStyle(FONT_WEIGHT_NORMAL, FONT_SLANT_UPRIGHT)).build();
        FontFamilyUpdateRequest.Font font2 = new FontFamilyUpdateRequest.Font.Builder("Test",
                new FontStyle(FONT_WEIGHT_NORMAL, FONT_SLANT_ITALIC)).build();
        FontFamilyUpdateRequest.FontFamily fontFamily =
                new FontFamilyUpdateRequest.FontFamily.Builder(name,
                        Collections.singletonList(font1)).addFont(font2).build();
        assertThat(fontFamily.getName()).isEqualTo(name);
        assertThat(fontFamily.getFonts()).containsExactly(font1, font2).inOrder();

        // Invalid parameters
        List<FontFamilyUpdateRequest.Font> fonts = Arrays.asList(font1, font2);
        assertThrows(NullPointerException.class, () ->
                new FontFamilyUpdateRequest.FontFamily.Builder(null, fonts).build());
        assertThrows(IllegalArgumentException.class, () ->
                new FontFamilyUpdateRequest.FontFamily.Builder("", fonts).build());
        assertThrows(NullPointerException.class, () ->
                new FontFamilyUpdateRequest.FontFamily.Builder(name, null).build());
        assertThrows(IllegalArgumentException.class, () ->
                new FontFamilyUpdateRequest.FontFamily.Builder(name,
                        Collections.emptyList()).build());
        assertThrows(NullPointerException.class, () ->
                new FontFamilyUpdateRequest.FontFamily.Builder(name,
                        Collections.singletonList(null)).build());
    }

    @Test
    public void fontFamilyUpdateRequest() throws Exception {
        // Roboto-Regular.ttf is always available.
        File robotoFile = new File("/system/fonts/Roboto-Regular.ttf");
        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(robotoFile,
                ParcelFileDescriptor.MODE_READ_ONLY);
        byte[] signature = new byte[256];
        FontFileUpdateRequest fontFileUpdateRequest = new FontFileUpdateRequest(pfd, signature);

        List<FontFamilyUpdateRequest.Font> fonts = Arrays.asList(
                new FontFamilyUpdateRequest.Font.Builder("Roboto-Regular",
                        new FontStyle(FONT_WEIGHT_NORMAL, FONT_SLANT_UPRIGHT)).build(),
                new FontFamilyUpdateRequest.Font.Builder("Roboto-Regular",
                        new FontStyle(FONT_WEIGHT_NORMAL, FONT_SLANT_ITALIC)).build());
        FontFamilyUpdateRequest.FontFamily fontFamily1 =
                new FontFamilyUpdateRequest.FontFamily.Builder("test-roboto1", fonts).build();
        FontFamilyUpdateRequest.FontFamily fontFamily2 =
                new FontFamilyUpdateRequest.FontFamily.Builder("test-roboto2", fonts).build();

        FontFamilyUpdateRequest request = new FontFamilyUpdateRequest.Builder()
                .addFontFileUpdateRequest(fontFileUpdateRequest)
                .addFontFamily(fontFamily1)
                .addFontFamily(fontFamily2)
                .build();
        assertThat(request.getFontFileUpdateRequests())
                .containsExactly(fontFileUpdateRequest);
        assertThat(request.getFontFamilies()).containsExactly(fontFamily1, fontFamily2).inOrder();
    }
}
