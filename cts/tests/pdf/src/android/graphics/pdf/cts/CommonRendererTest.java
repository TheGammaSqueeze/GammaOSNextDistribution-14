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

package android.graphics.pdf.cts;

import static android.graphics.pdf.cts.Utils.A4_HEIGHT_PTS;
import static android.graphics.pdf.cts.Utils.A4_PORTRAIT;
import static android.graphics.pdf.cts.Utils.A4_WIDTH_PTS;
import static android.graphics.pdf.cts.Utils.SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR;
import static android.graphics.pdf.cts.Utils.createPreVRenderer;
import static android.graphics.pdf.cts.Utils.createRenderer;
import static android.graphics.pdf.cts.Utils.createRendererUsingNewConstructor;
import static android.graphics.pdf.cts.Utils.getColorProbes;
import static android.graphics.pdf.cts.Utils.renderAndCompare;
import static android.graphics.pdf.cts.Utils.renderPreV;
import static android.graphics.pdf.cts.Utils.renderPreVAndCompare;
import static android.graphics.pdf.cts.Utils.renderWithTransform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.PdfRendererPreV;
import android.graphics.pdf.RenderParams;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

// This class contains common page API #render tests for PdfRendererPreV and PdfRenderer.
@RunWith(AndroidJUnit4.class)
public class CommonRendererTest {

    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    /**
     * Implementation for {@link #renderNoTransformationAndComparePointsForScreen} and {@link
     * #renderNoTransformationAndComparePointsForPrint}.
     *
     * @param bitmap The bitmap use to compare
     * @throws Exception If anything was unexpected
     */
    private void renderNoTransformationAndComparePoints(Bitmap bitmap) throws Exception {
        int[] probes = getColorProbes(bitmap);

        // Compare rendering to expected result. This ensures that all other tests in this class do
        // not accidentally all compare empty bitmaps.
        assertEquals(Color.RED, probes[0]);
        assertEquals(Color.RED, probes[1]);
        assertEquals(Color.GREEN, probes[2]);
        assertEquals(Color.GREEN, probes[3]);
        assertEquals(Color.RED, probes[4]);
        assertEquals(Color.RED, probes[5]);
        assertEquals(Color.GREEN, probes[6]);
        assertEquals(Color.GREEN, probes[7]);
        assertEquals(Color.BLUE, probes[8]);
        assertEquals(Color.BLUE, probes[9]);
        assertEquals(Color.BLACK, probes[10]);
        assertEquals(Color.BLACK, probes[11]);
        assertEquals(Color.BLUE, probes[12]);
        assertEquals(Color.BLUE, probes[13]);
        assertEquals(Color.BLACK, probes[14]);
        assertEquals(Color.BLACK, probes[15]);
    }

    @Test
    public void renderNoTransformationAndComparePointsForScreen() throws Exception {
        renderNoTransformationAndComparePoints(
                getBitmap(PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY, false, false));

        // Render and compare with new overloaded constructor.
        renderNoTransformationAndComparePoints(
                getBitmap(PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY, false, true));

        // Assert PreV API
        renderNoTransformationAndComparePoints(
                getBitmap(PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY, true, true));

    }

    @Test
    public void renderNoTransformationAndComparePointsForPrint() throws Exception {
        renderNoTransformationAndComparePoints(
                getBitmap(PdfRenderer.Page.RENDER_MODE_FOR_PRINT, false, false));

        // Render and compare with new overloaded constructor.
        renderNoTransformationAndComparePoints(
                getBitmap(PdfRenderer.Page.RENDER_MODE_FOR_PRINT, false, true));

        // Assert PreV API
        renderNoTransformationAndComparePoints(
                getBitmap(PdfRenderer.Page.RENDER_MODE_FOR_PRINT, true, true));

    }

    @Test
    public void renderPerspective() throws Exception {
        Matrix transform = new Matrix();

        transform.setValues(new float[]{1, 1, 1, 1, 1, 1, 1, 1, 1});

        assertException(null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
    }

    @Test
    public void render45degreeRotationTranslationAndScaleAndClip() throws Exception {
        Matrix transform = new Matrix();
        // Rotate on top left corner
        transform.postRotate(45);
        // Move
        transform.postTranslate(A4_WIDTH_PTS / 4, A4_HEIGHT_PTS / 4);
        // Scale to 75%
        transform.postScale(0.75f, 0.75f);
        // Clip
        Rect clip = new Rect(20, 20, A4_WIDTH_PTS - 20, A4_HEIGHT_PTS - 20);

        assertUsingRenderAndCompare(A4_WIDTH_PTS, A4_HEIGHT_PTS, clip, transform);
    }

    @Test
    public void renderStretched() throws Exception {
        assertUsingRenderAndCompare(A4_WIDTH_PTS * 4 / 3, A4_HEIGHT_PTS * 3 / 4, null, null);
    }

    @Test
    public void renderWithClip() throws Exception {
        Rect clip = new Rect(20, 20, A4_WIDTH_PTS - 50, A4_HEIGHT_PTS - 50);

        assertUsingRenderAndCompare(A4_WIDTH_PTS, A4_HEIGHT_PTS, clip, null);
    }

    @Test
    public void renderWithAllClipped() throws Exception {
        Rect clip = new Rect(A4_WIDTH_PTS / 2, A4_HEIGHT_PTS / 2, A4_WIDTH_PTS / 2,
                A4_HEIGHT_PTS / 2);

        assertUsingRenderAndCompare(A4_WIDTH_PTS, A4_HEIGHT_PTS, clip, null);

    }

    @Test
    public void renderWithBadLowerCornerOfClip() throws Exception {
        Rect clip = new Rect(0, 0, A4_WIDTH_PTS + 20, A4_HEIGHT_PTS + 20);

        assertException(clip, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

    }

    @Test
    public void renderWithBadUpperCornerOfClip() throws Exception {
        Rect clip = new Rect(-20, -20, A4_WIDTH_PTS, A4_HEIGHT_PTS);

        assertException(clip, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
    }

    @Test
    public void renderTwoModes() throws Exception {
        assertException(null, null,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY | PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
    }

    @Test
    public void renderBadMode() throws Exception {
        assertException(null, null, 1 << 30);
    }

    @Test
    public void renderAllModes() throws Exception {
        assertException(null, null, -1);
    }

    @Test
    public void renderNoMode() throws Exception {
        assertException(null, null, 0);
    }

    @Test
    public void renderOnNullBitmap_throwsException() throws Exception {
        try (PdfRenderer renderer = createRenderer(A4_PORTRAIT, mContext);
             PdfRenderer.Page page = renderer.openPage(0)) {
            assertThrows(NullPointerException.class,
                    () -> page.render(null, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY));
        }
    }

    @Test
    public void renderOnNullBitmapWithNewConstructor_throwsException() throws Exception {
        try (PdfRenderer renderer = createRendererUsingNewConstructor(A4_PORTRAIT, mContext,
                SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR);
             PdfRenderer.Page page = renderer.openPage(0)) {
            assertThrows(NullPointerException.class,
                    () -> page.render(null, null, null, new RenderParams.Builder(
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY).setRenderFlags(
                            RenderParams.FLAG_RENDER_HIGHLIGHT_ANNOTATIONS).build()));
        }
    }

    @Test
    public void renderPreVOnNUllBitmap_throwsException() throws Exception {
        try (PdfRendererPreV renderer = createPreVRenderer(A4_PORTRAIT, mContext, null);
             PdfRendererPreV.Page page = renderer.openPage(0)) {
            assertThrows(NullPointerException.class,
                    () -> page.render(null, null, null, new RenderParams.Builder(1).build()));
        }
    }

    private void assertException(Rect destClip, Matrix transformation, int renderMode) {
        assertThrows(IllegalArgumentException.class,
                () -> renderWithTransform(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, destClip,
                        transformation, renderMode, RenderParams.FLAG_RENDER_TEXT_ANNOTATIONS,
                        false, mContext));

        // Render and compare with new overloaded constructor.
        assertThrows(IllegalArgumentException.class,
                () -> renderWithTransform(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, destClip,
                        transformation, renderMode, RenderParams.FLAG_RENDER_TEXT_ANNOTATIONS, true,
                        mContext));

        // asserts PreV API
        assertThrows(IllegalArgumentException.class,
                () -> renderPreV(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, destClip, transformation,
                        renderMode, RenderParams.FLAG_RENDER_TEXT_ANNOTATIONS, mContext));
    }

    private void assertUsingRenderAndCompare(int width, int height, Rect clip, Matrix transform)
            throws Exception {
        renderAndCompare(width, height, A4_PORTRAIT, clip, transform,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY, RenderParams.FLAG_RENDER_TEXT_ANNOTATIONS,
                false, mContext);

        // Render and compare with new overloaded constructor.
        renderAndCompare(width, height, A4_PORTRAIT, clip, transform,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY, RenderParams.FLAG_RENDER_TEXT_ANNOTATIONS,
                true, mContext);

        // For PreV API
        renderPreVAndCompare(width, height, A4_PORTRAIT, clip, transform,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY, RenderParams.FLAG_RENDER_TEXT_ANNOTATIONS,
                mContext);
    }

    private Bitmap getBitmap(int renderMode, boolean isPreV, boolean useNewConstructor)
            throws Exception {
        if (isPreV) {
            return renderPreV(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, null, null, renderMode,
                    RenderParams.FLAG_RENDER_TEXT_ANNOTATIONS, mContext);
        }

        return renderWithTransform(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, null, null, renderMode,
                RenderParams.FLAG_RENDER_TEXT_ANNOTATIONS, useNewConstructor, mContext);
    }

}
