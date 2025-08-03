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

package android.webkit.cts;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.NullWebViewUtils;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

/**
 * Tests for {@link android.webkit.WebSettings#setAlgorithmicDarkeningAllowed(boolean)}
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class WebViewDarkModeDarkThemeTest extends WebViewDarkModeTestBase {

    @Rule
    public ActivityTestRule<WebViewDarkThemeCtsActivity> mActivityRule =
            new ActivityTestRule<>(WebViewDarkThemeCtsActivity.class);

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("WebView is not available", NullWebViewUtils.isWebViewAvailable());
        init(mActivityRule.getActivity());
    }

    @After
    public void tearDown() throws Exception {
        cleanup();
    }


    @Test
    public void testSimplifiedDarkMode_rendersDark() throws Throwable {
        setWebViewSize(64, 64);

        // Set the webview non-focusable to avoid drawing the focus highlight.
        WebkitUtils.onMainThreadSync(() -> {
            getOnUiThread().getWebView().setFocusable(false);
        });

        Map<Integer, Integer> histogram;
        Integer[] colourValues;

        // Loading about:blank which doesn't support dark style result in a light background.
        assertFalse("Algorithmic darkening should be disallowed by default",
                getSettings().isAlgorithmicDarkeningAllowed());

        getOnUiThread().loadUrlAndWaitForCompletion("about:blank");

        assertEquals("prefers-color-scheme shall set to dark.", "true",
                getOnUiThread().evaluateJavascriptSync(
                        "window.matchMedia && "
                        + "window.matchMedia('(prefers-color-scheme: dark)').matches"));
        histogram = getBitmapHistogram(getOnUiThread().captureBitmap(), 0, 0, 64, 64);
        assertEquals("Bitmap should have a single colour", histogram.size(), 1);
        colourValues = histogram.keySet().toArray(new Integer[0]);
        assertThat("Bitmap colour should be light",
                Color.luminance(colourValues[0]), greaterThan(0.5f));

        // Allowing algorithmic darkening in dark theme app should result in a dark background.
        getSettings().setAlgorithmicDarkeningAllowed(true);
        assertTrue("Algorithmic darkening should be allowed",
                getSettings().isAlgorithmicDarkeningAllowed());
        getOnUiThread().loadUrlAndWaitForCompletion("about:blank");
        assertEquals("prefers-color-scheme shall set to dark.", "true",
                getOnUiThread().evaluateJavascriptSync(
                        "window.matchMedia && "
                        + "window.matchMedia('(prefers-color-scheme: dark)').matches"));
        histogram = getBitmapHistogram(getOnUiThread().captureBitmap(), 0, 0, 64, 64);
        assertEquals("Bitmap should have a single colour", histogram.size(), 1);
        colourValues = histogram.keySet().toArray(new Integer[0]);
        assertThat("Bitmap colour should be dark",
                Color.luminance(colourValues[0]), lessThan(0.5f));
    }
}
