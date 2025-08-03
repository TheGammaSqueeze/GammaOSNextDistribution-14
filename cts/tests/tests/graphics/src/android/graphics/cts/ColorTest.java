/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.res.Resources;
import android.graphics.Color;
import android.util.Log;
import android.util.TypedValue;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ColorTest {

    private static final String LOG_TAG = ColorTest.class.getSimpleName();

    @Test
    public void resourceColor() {
        int[][] colors = {
                { 0xff000000, android.R.color.background_dark  },
                { 0xffffffff, android.R.color.background_light },
                { 0xff000000, android.R.color.black },
                { 0xffaaaaaa, android.R.color.darker_gray },
                { 0xff00ddff, android.R.color.holo_blue_bright },
                { 0xff0099cc, android.R.color.holo_blue_dark },
                { 0xff33b5e5, android.R.color.holo_blue_light },
                { 0xff669900, android.R.color.holo_green_dark },
                { 0xff99cc00, android.R.color.holo_green_light },
                { 0xffff8800, android.R.color.holo_orange_dark },
                { 0xffffbb33, android.R.color.holo_orange_light },
                { 0xffaa66cc, android.R.color.holo_purple },
                { 0xffcc0000, android.R.color.holo_red_dark },
                { 0xffff4444, android.R.color.holo_red_light },
                { 0xffffffff, android.R.color.primary_text_dark },
                { 0xffffffff, android.R.color.primary_text_dark_nodisable },
                { 0xff000000, android.R.color.primary_text_light },
                { 0xff000000, android.R.color.primary_text_light_nodisable },
                { 0xffbebebe, android.R.color.secondary_text_dark },
                { 0xffbebebe, android.R.color.secondary_text_dark_nodisable },
                { 0xff323232, android.R.color.secondary_text_light },
                { 0xffbebebe, android.R.color.secondary_text_light_nodisable },
                { 0xff808080, android.R.color.tab_indicator_text },
                { 0xff808080, android.R.color.tertiary_text_dark },
                { 0xff808080, android.R.color.tertiary_text_light },
                { 0x00000000, android.R.color.transparent },
                { 0xffffffff, android.R.color.white },
                { 0xff000000, android.R.color.widget_edittext_dark },
        };

        int[] systemColors = {
                android.R.color.system_neutral1_0,
                android.R.color.system_neutral1_10,
                android.R.color.system_neutral1_50,
                android.R.color.system_neutral1_100,
                android.R.color.system_neutral1_200,
                android.R.color.system_neutral1_300,
                android.R.color.system_neutral1_400,
                android.R.color.system_neutral1_500,
                android.R.color.system_neutral1_600,
                android.R.color.system_neutral1_700,
                android.R.color.system_neutral1_800,
                android.R.color.system_neutral1_900,
                android.R.color.system_neutral1_1000,
                android.R.color.system_neutral2_0,
                android.R.color.system_neutral2_10,
                android.R.color.system_neutral2_50,
                android.R.color.system_neutral2_100,
                android.R.color.system_neutral2_200,
                android.R.color.system_neutral2_300,
                android.R.color.system_neutral2_400,
                android.R.color.system_neutral2_500,
                android.R.color.system_neutral2_600,
                android.R.color.system_neutral2_700,
                android.R.color.system_neutral2_800,
                android.R.color.system_neutral2_900,
                android.R.color.system_neutral2_1000,
                android.R.color.system_accent1_0,
                android.R.color.system_accent1_10,
                android.R.color.system_accent1_50,
                android.R.color.system_accent1_100,
                android.R.color.system_accent1_200,
                android.R.color.system_accent1_300,
                android.R.color.system_accent1_400,
                android.R.color.system_accent1_500,
                android.R.color.system_accent1_600,
                android.R.color.system_accent1_700,
                android.R.color.system_accent1_800,
                android.R.color.system_accent1_900,
                android.R.color.system_accent1_1000,
                android.R.color.system_accent2_0,
                android.R.color.system_accent2_10,
                android.R.color.system_accent2_50,
                android.R.color.system_accent2_100,
                android.R.color.system_accent2_200,
                android.R.color.system_accent2_300,
                android.R.color.system_accent2_400,
                android.R.color.system_accent2_500,
                android.R.color.system_accent2_600,
                android.R.color.system_accent2_700,
                android.R.color.system_accent2_800,
                android.R.color.system_accent2_900,
                android.R.color.system_accent2_1000,
                android.R.color.system_accent3_0,
                android.R.color.system_accent3_10,
                android.R.color.system_accent3_50,
                android.R.color.system_accent3_100,
                android.R.color.system_accent3_200,
                android.R.color.system_accent3_300,
                android.R.color.system_accent3_400,
                android.R.color.system_accent3_500,
                android.R.color.system_accent3_600,
                android.R.color.system_accent3_700,
                android.R.color.system_accent3_800,
                android.R.color.system_accent3_900,
                android.R.color.system_accent3_1000,
                android.R.color.system_error_0,
                android.R.color.system_error_10,
                android.R.color.system_error_50,
                android.R.color.system_error_100,
                android.R.color.system_error_200,
                android.R.color.system_error_300,
                android.R.color.system_error_400,
                android.R.color.system_error_500,
                android.R.color.system_error_600,
                android.R.color.system_error_700,
                android.R.color.system_error_800,
                android.R.color.system_error_900,
                android.R.color.system_error_1000,
        };

        int[] materialSystemColors = {
                android.R.color.system_primary_container_light,
                android.R.color.system_on_primary_container_light,
                android.R.color.system_primary_light,
                android.R.color.system_on_primary_light,
                android.R.color.system_secondary_container_light,
                android.R.color.system_on_secondary_container_light,
                android.R.color.system_secondary_light,
                android.R.color.system_on_secondary_light,
                android.R.color.system_tertiary_container_light,
                android.R.color.system_on_tertiary_container_light,
                android.R.color.system_tertiary_light,
                android.R.color.system_on_tertiary_light,
                android.R.color.system_background_light,
                android.R.color.system_on_background_light,
                android.R.color.system_surface_light,
                android.R.color.system_on_surface_light,
                android.R.color.system_surface_container_low_light,
                android.R.color.system_surface_container_lowest_light,
                android.R.color.system_surface_container_light,
                android.R.color.system_surface_container_high_light,
                android.R.color.system_surface_container_highest_light,
                android.R.color.system_surface_bright_light,
                android.R.color.system_surface_dim_light,
                android.R.color.system_surface_variant_light,
                android.R.color.system_on_surface_variant_light,
                android.R.color.system_outline_light,
                android.R.color.system_outline_variant_light,
                android.R.color.system_error_light,
                android.R.color.system_on_error_light,
                android.R.color.system_error_container_light,
                android.R.color.system_on_error_container_light,
                android.R.color.system_primary_fixed,
                android.R.color.system_primary_fixed_dim,
                android.R.color.system_on_primary_fixed,
                android.R.color.system_on_primary_fixed_variant,
                android.R.color.system_secondary_fixed,
                android.R.color.system_secondary_fixed_dim,
                android.R.color.system_on_secondary_fixed,
                android.R.color.system_on_secondary_fixed_variant,
                android.R.color.system_tertiary_fixed,
                android.R.color.system_tertiary_fixed_dim,
                android.R.color.system_on_tertiary_fixed,
                android.R.color.system_on_tertiary_fixed_variant,
                android.R.color.system_control_activated_light,
                android.R.color.system_control_normal_light,
                android.R.color.system_control_highlight_light,
                android.R.color.system_text_primary_inverse_light,
                android.R.color.system_text_secondary_and_tertiary_inverse_light,
                android.R.color.system_text_primary_inverse_disable_only_light,
                android.R.color.system_text_secondary_and_tertiary_inverse_disabled_light,
                android.R.color.system_text_hint_inverse_light,
                android.R.color.system_palette_key_color_primary_light,
                android.R.color.system_palette_key_color_secondary_light,
                android.R.color.system_palette_key_color_tertiary_light,
                android.R.color.system_palette_key_color_neutral_light,
                android.R.color.system_palette_key_color_neutral_variant_light,
                android.R.color.system_primary_container_dark,
                android.R.color.system_on_primary_container_dark,
                android.R.color.system_primary_dark,
                android.R.color.system_on_primary_dark,
                android.R.color.system_secondary_container_dark,
                android.R.color.system_on_secondary_container_dark,
                android.R.color.system_secondary_dark,
                android.R.color.system_on_secondary_dark,
                android.R.color.system_tertiary_container_dark,
                android.R.color.system_on_tertiary_container_dark,
                android.R.color.system_tertiary_dark,
                android.R.color.system_on_tertiary_dark,
                android.R.color.system_background_dark,
                android.R.color.system_on_background_dark,
                android.R.color.system_surface_dark,
                android.R.color.system_on_surface_dark,
                android.R.color.system_surface_container_low_dark,
                android.R.color.system_surface_container_lowest_dark,
                android.R.color.system_surface_container_dark,
                android.R.color.system_surface_container_high_dark,
                android.R.color.system_surface_container_highest_dark,
                android.R.color.system_surface_bright_dark,
                android.R.color.system_surface_dim_dark,
                android.R.color.system_surface_variant_dark,
                android.R.color.system_on_surface_variant_dark,
                android.R.color.system_outline_dark,
                android.R.color.system_outline_variant_dark,
                android.R.color.system_error_dark,
                android.R.color.system_on_error_dark,
                android.R.color.system_error_container_dark,
                android.R.color.system_on_error_container_dark,
                android.R.color.system_control_activated_dark,
                android.R.color.system_control_normal_dark,
                android.R.color.system_control_highlight_dark,
                android.R.color.system_text_primary_inverse_dark,
                android.R.color.system_text_secondary_and_tertiary_inverse_dark,
                android.R.color.system_text_primary_inverse_disable_only_dark,
                android.R.color.system_text_secondary_and_tertiary_inverse_disabled_dark,
                android.R.color.system_text_hint_inverse_dark,
                android.R.color.system_palette_key_color_primary_dark,
                android.R.color.system_palette_key_color_secondary_dark,
                android.R.color.system_palette_key_color_tertiary_dark,
                android.R.color.system_palette_key_color_neutral_dark,
                android.R.color.system_palette_key_color_neutral_variant_dark,
                android.R.color.system_surface_disabled,
                android.R.color.system_on_surface_disabled,
                android.R.color.system_outline_disabled,
        };

        List<Integer> expectedColorStateLists = Arrays.asList(
                android.R.color.primary_text_dark,
                android.R.color.primary_text_dark_nodisable,
                android.R.color.primary_text_light,
                android.R.color.primary_text_light_nodisable,
                android.R.color.secondary_text_dark,
                android.R.color.secondary_text_dark_nodisable,
                android.R.color.secondary_text_light,
                android.R.color.secondary_text_light_nodisable,
                android.R.color.tab_indicator_text,
                android.R.color.tertiary_text_dark,
                android.R.color.tertiary_text_light,
                android.R.color.widget_edittext_dark
        );

        Resources resources = getInstrumentation().getTargetContext().getResources();
        for (int[] pair : colors) {
            final int resourceId = pair[1];
            final int expectedColor = pair[0];

            // validate color from getColor
            int observedColor = resources.getColor(resourceId, null);
            assertEquals("Color = " + Integer.toHexString(observedColor) + ", "
                            + Integer.toHexString(expectedColor) + " expected",
                    expectedColor,
                    observedColor);

            // validate color from getValue
            TypedValue value = new TypedValue();
            resources.getValue(resourceId, value, true);

            // colors shouldn't depend on config changes
            assertEquals(0, value.changingConfigurations);

            if (expectedColorStateLists.contains(resourceId)) {
                // ColorStateLists are strings
                assertEquals("CSLs should be strings", TypedValue.TYPE_STRING, value.type);
            } else {
                // colors should be raw ints
                assertTrue("Type should be int",
                        value.type >= TypedValue.TYPE_FIRST_INT
                        && value.type <= TypedValue.TYPE_LAST_INT);

                // Validate color from getValue
                assertEquals("Color should be expected value", expectedColor, value.data);
            }
        }

        // System-API colors are used to allow updateable platform components to use the same colors
        // as the system. The actual value of the color does not matter. Hence only enforce that
        // 'colors' contains all the public colors and ignore System-api colors.
        ArrayList<String> missingColors = new ArrayList<>();
        ArrayList<Integer> allKnownColors = new ArrayList<>();
        allKnownColors.addAll(Arrays.stream(colors).map(pair -> pair[1]).toList());
        allKnownColors.addAll(Arrays.stream(systemColors).boxed().toList());
        allKnownColors.addAll(Arrays.stream(materialSystemColors).boxed().toList());
        int numPublicApiColors = 0;
        for (Field declaredColor : android.R.color.class.getDeclaredFields()) {
            if (Arrays.stream(declaredColor.getDeclaredAnnotations()).anyMatch(
                    (Annotation a) -> a.toString().contains("SystemApi"))) {
                Log.i(LOG_TAG, declaredColor.getName() + " is SystemApi");
            } else {
                Integer value = -1;
                try {
                    value = (Integer) declaredColor.get(null);
                } catch (IllegalAccessException ignored) { }

                if (!allKnownColors.remove(value)) {
                    missingColors.add(declaredColor.getName());
                }
                numPublicApiColors++;
            }
        }

        assertEquals("Test no longer in sync with colors in android.R.color. "
                + "Declared in list, but not public API : " + allKnownColors
                + ". Missing in declared colors: " + missingColors,
                colors.length + systemColors.length
                + materialSystemColors.length, numPublicApiColors);
    }
    @Test
    public void testAlpha() {
        assertEquals(0xff, Color.alpha(Color.RED));
        assertEquals(0xff, Color.alpha(Color.YELLOW));
    }

    @Test
    public void testArgb() {
        assertEquals(Color.RED, Color.argb(0xff, 0xff, 0x00, 0x00));
        assertEquals(Color.YELLOW, Color.argb(0xff, 0xff, 0xff, 0x00));
        assertEquals(Color.RED, Color.argb(1.0f, 1.0f, 0.0f, 0.0f));
        assertEquals(Color.YELLOW, Color.argb(1.0f, 1.0f, 1.0f, 0.0f));
    }

    @Test
    public void testBlue() {
        assertEquals(0x00, Color.blue(Color.RED));
        assertEquals(0x00, Color.blue(Color.YELLOW));
    }

    @Test
    public void testGreen() {
        assertEquals(0x00, Color.green(Color.RED));
        assertEquals(0xff, Color.green(Color.GREEN));
    }

    @Test(expected = RuntimeException.class)
    public void testHSVToColorArrayTooShort() {
        // abnormal case: hsv length less than 3
        float[] hsv = new float[2];
        Color.HSVToColor(hsv);
    }

    @Test
    public void testHSVToColor() {
        float[] hsv = new float[3];
        Color.colorToHSV(Color.RED, hsv);
        assertEquals(Color.RED, Color.HSVToColor(hsv));
    }

    @Test
    public void testHSVToColorWithAlpha() {
        float[] hsv = new float[3];
        Color.colorToHSV(Color.RED, hsv);
        assertEquals(Color.RED, Color.HSVToColor(0xff, hsv));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseColorStringOfInvalidLength() {
        // abnormal case: colorString starts with '#' but length is neither 7 nor 9
        Color.parseColor("#ff00ff0");
    }

    @Test
    public void testParseColor() {
        assertEquals(Color.RED, Color.parseColor("#ff0000"));
        assertEquals(Color.RED, Color.parseColor("#ffff0000"));

        assertEquals(Color.BLACK, Color.parseColor("black"));
        assertEquals(Color.DKGRAY, Color.parseColor("darkgray"));
        assertEquals(Color.GRAY, Color.parseColor("gray"));
        assertEquals(Color.LTGRAY, Color.parseColor("lightgray"));
        assertEquals(Color.WHITE, Color.parseColor("white"));
        assertEquals(Color.RED, Color.parseColor("red"));
        assertEquals(Color.GREEN, Color.parseColor("green"));
        assertEquals(Color.BLUE, Color.parseColor("blue"));
        assertEquals(Color.YELLOW, Color.parseColor("yellow"));
        assertEquals(Color.CYAN, Color.parseColor("cyan"));
        assertEquals(Color.MAGENTA, Color.parseColor("magenta"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseColorUnsupportedFormat() {
        // abnormal case: colorString doesn't start with '#' and is unknown color
        Color.parseColor("hello");
    }

    @Test
    public void testRed() {
        assertEquals(0xff, Color.red(Color.RED));
        assertEquals(0xff, Color.red(Color.YELLOW));
    }

    @Test
    public void testRgb() {
        assertEquals(Color.RED, Color.rgb(0xff, 0x00, 0x00));
        assertEquals(Color.YELLOW, Color.rgb(0xff, 0xff, 0x00));
        assertEquals(Color.RED, Color.rgb(1.0f, 0.0f, 0.0f));
        assertEquals(Color.YELLOW, Color.rgb(1.0f, 1.0f, 0.0f));
    }

    @Test(expected = RuntimeException.class)
    public void testRGBToHSVArrayTooShort() {
        // abnormal case: hsv length less than 3
        float[] hsv = new float[2];
        Color.RGBToHSV(0xff, 0x00, 0x00, hsv);
    }

    @Test
    public void testRGBToHSV() {
        float[] hsv = new float[3];
        Color.RGBToHSV(0xff, 0x00, 0x00, hsv);
        assertEquals(Color.RED, Color.HSVToColor(hsv));
    }

    @Test
    public void testLuminance() {
        assertEquals(0, Color.luminance(Color.BLACK), 0);
        float eps = 0.000001f;
        assertEquals(0.0722, Color.luminance(Color.BLUE), eps);
        assertEquals(0.2126, Color.luminance(Color.RED), eps);
        assertEquals(0.7152, Color.luminance(Color.GREEN), eps);
        assertEquals(1, Color.luminance(Color.WHITE), 0);
    }
}
