/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.pdf.LoadParams;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.PdfRendererPreV;
import android.graphics.pdf.RenderParams;
import android.graphics.pdf.models.PageMatchBounds;
import android.os.ParcelFileDescriptor;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Utilities for this package
 */
class Utils {
    static final int A4_WIDTH_PTS = 595;
    static final int A4_HEIGHT_PTS = 841;

    static final int PROTECTED_PDF = android.graphics.pdf.cts.R.raw.sample_test_protected;
    static final int SAMPLE_PDF = android.graphics.pdf.cts.R.raw.sample_test;

    static final LoadParams LOAD_PARAMS = new LoadParams.Builder().setPassword("qwerty").build();

    static final LoadParams SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR =
            new LoadParams.Builder().setPassword("qwerty").build();

    static final LoadParams INCORRECT_LOAD_PARAMS = new LoadParams.Builder().setPassword(
            "abc-def").build();

    static final int A4_PORTRAIT = android.graphics.pdf.cts.R.raw.a4_portrait_rgbb;
    static final int A5_PORTRAIT = android.graphics.pdf.cts.R.raw.a5_portrait_rgbb;
    private static final String LOG_TAG = "Utils";
    private static final Map<Integer, File> sFiles = new ArrayMap<>();
    private static final Map<Integer, Bitmap> sRenderedBitmaps = new ArrayMap<>();

    private static final Map<Integer, Bitmap> sNewRenderBitmaps = new ArrayMap<>();
    private static final Map<Integer, Bitmap> sPreVRenderedBitmaps = new ArrayMap<>();

    /**
     * Create a {@link PdfRenderer} pointing to a file copied from a resource.
     *
     * @param docRes  The resource to load
     * @param context The context to use for creating the renderer
     * @return the renderer
     * @throws IOException If anything went wrong
     */
    @NonNull
    static PdfRenderer createRenderer(@RawRes int docRes, @NonNull Context context)
            throws IOException {
        return new PdfRenderer(getParcelFileDescriptorFromResourceId(docRes, context));
    }

    /**
     * Create a {@link PdfRenderer} pointing to a file copied from a resource from the new
     * PdfRenderer constructor.
     *
     * @param docRes     The resource to load
     * @param context    The context to use for creating the renderer
     * @param loadParams The params to use for creating the renderer
     * @return the renderer
     * @throws IOException If anything went wrong
     */
    @NonNull
    static PdfRenderer createRendererUsingNewConstructor(@RawRes int docRes,
            @NonNull Context context, @Nullable LoadParams loadParams) throws IOException {
        return new PdfRenderer(getParcelFileDescriptorFromResourceId(docRes, context), loadParams);
    }

    /**
     * Create a {@link PdfRendererPreV} pointing to a file copied from a resource.
     *
     * @param docRes        The resource to load
     * @param context       The context to use for creating the renderer
     * @param loadPdfParams LoadPdfParams for the protected pdf, will be null in case not
     *                      protected
     * @return the renderer
     * @throws IOException If anything went wrong
     */
    @NonNull
    static PdfRendererPreV createPreVRenderer(@RawRes int docRes, @NonNull Context context,
            @Nullable LoadParams loadPdfParams) throws IOException {
        if (Objects.equals(loadPdfParams, null)) {
            return new PdfRendererPreV(getParcelFileDescriptorFromResourceId(docRes, context));
        }
        return new PdfRendererPreV(getParcelFileDescriptorFromResourceId(docRes, context),
                loadPdfParams);
    }

    /**
     * Create a {@link ParcelFileDescriptor} pointing to a file copied from a resource.
     *
     * @param docRes  The resource to load
     * @param context The context to use for creating the parcel file descriptor
     * @return the ParcelFileDescriptor
     * @throws IOException If anything went wrong
     */
    @NonNull
    static ParcelFileDescriptor getParcelFileDescriptorFromResourceId(@RawRes int docRes,
            @NonNull Context context) throws IOException {
        File pdfFile = sFiles.get(docRes);
        if (pdfFile == null) {
            pdfFile = File.createTempFile("pdf", null, context.getCacheDir());
            // Copy resource to file so that we can open it as a ParcelFileDescriptor

            InputStream inputStream = context.getResources().openRawResource(docRes);
            // Create a FileOutputStream to write the resource content to the target file.
            FileOutputStream outputStream = new FileOutputStream(pdfFile);

            // Copy the content of the resource file to the target file.
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            // Close streams.
            inputStream.close();
            outputStream.close();
            sFiles.put(docRes, pdfFile);
        }
        return Objects.requireNonNull(
                ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY));
    }

    /**
     * Render a pdf onto a bitmap <u>while</u> applying the transformation <u>in the</u>
     * PDFRenderer. Hence, use PdfRenderer.*'s translation and clipping methods.
     *
     * @param bmWidth           The width of the destination bitmap
     * @param bmHeight          The height of the destination bitmap
     * @param docRes            The resolution of the doc
     * @param clipping          The clipping for the PDF document
     * @param transformation    The transformation of the PDF
     * @param renderMode        The render mode to use to render the PDF
     * @param renderFlag        The render flag to use to render the PDF
     * @param useNewConstructor if True, render will use the new overloaded constructor
     * @param context           The context to use for creating the renderer
     * @return The rendered bitmap
     */
    @NonNull
    static Bitmap renderWithTransform(int bmWidth, int bmHeight, @RawRes int docRes,
            @Nullable Rect clipping, @Nullable Matrix transformation, int renderMode,
            int renderFlag, boolean useNewConstructor, @NonNull Context context)
            throws IOException {
        Bitmap bm = Bitmap.createBitmap(bmWidth, bmHeight, Bitmap.Config.ARGB_8888);

        if (useNewConstructor) {
            try (PdfRenderer renderer = createRendererUsingNewConstructor(docRes, context,
                    SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR)) {
                try (PdfRenderer.Page page = renderer.openPage(0)) {
                    page.render(bm, clipping, transformation, new RenderParams.Builder(
                            renderMode).setRenderFlags(renderFlag).build());

                    return bm;
                }
            }
        } else {
            try (PdfRenderer renderer = createRenderer(docRes, context)) {
                try (PdfRenderer.Page page = renderer.openPage(0)) {
                    page.render(bm, clipping, transformation, renderMode);

                    return bm;
                }
            }
        }
    }

    /**
     * Render a pdf onto a bitmap <u>and then</u> apply then render the resulting bitmap onto
     * another bitmap while applying the transformation. Hence use canvas' translation and clipping
     * methods.
     *
     * @param bmWidth           The width of the destination bitmap
     * @param bmHeight          The height of the destination bitmap
     * @param docRes            The resolution of the doc
     * @param clipping          The clipping for the PDF document
     * @param transformation    The transformation of the PDF
     * @param renderMode        The render mode to use to render the PDF
     * @param renderFlag        The render flag to use to render the PDF
     * @param useNewConstructor if True, render will use the new overloaded
     *                          constructor
     * @param context           The context to use for creating the renderer
     * @return The rendered bitmap
     */
    @NonNull
    private static Bitmap renderAndThenTransform(int bmWidth, int bmHeight, @RawRes int docRes,
            @Nullable Rect clipping, @Nullable Matrix transformation, int renderMode,
            int renderFlag, boolean useNewConstructor, @NonNull Context context)
            throws IOException {
        Bitmap renderedBm;

        if (useNewConstructor) {
            renderedBm = sNewRenderBitmaps.get(docRes);

            if (renderedBm == null) {
                try (PdfRenderer renderer = createRendererUsingNewConstructor(docRes, context,
                        SAMPLE_LOAD_PARAMS_FOR_TESTING_NEW_CONSTRUCTOR)) {
                    try (PdfRenderer.Page page = renderer.openPage(0)) {
                        renderedBm = Bitmap.createBitmap(page.getWidth(), page.getHeight(),
                                Bitmap.Config.ARGB_8888);
                        page.render(renderedBm, null, null, new RenderParams.Builder(
                                renderMode).setRenderFlags(renderFlag).build());
                    }
                }
                sNewRenderBitmaps.put(docRes, renderedBm);
            }
        } else {
            renderedBm = sRenderedBitmaps.get(docRes);

            if (renderedBm == null) {
                try (PdfRenderer renderer = Utils.createRenderer(docRes, context)) {
                    try (PdfRenderer.Page page = renderer.openPage(0)) {
                        renderedBm = Bitmap.createBitmap(page.getWidth(), page.getHeight(),
                                Bitmap.Config.ARGB_8888);
                        page.render(renderedBm, null, null, renderMode);
                    }
                }
                sRenderedBitmaps.put(docRes, renderedBm);
            }
        }

        return applyTransformationOnBitmap(renderedBm, bmWidth, bmHeight, clipping, transformation);
    }

    /**
     * Apply the transformation and clipping on the given bitmap.
     * First the bitmap is drawn based on the transformation matrix, and then we draw
     * the bitmap using {@link Canvas}, do the scaling/translating to fill the destination clipping
     * provided.
     *
     * @param bitmap         Bitmap on which transformation will be applied.
     * @param bmWidth        The width of the bitmap
     * @param bmHeight       The height of the bitmap
     * @param clipping       The clipping for the PDF document
     * @param transformation The transformation of the PDF
     * @return The new transformed bitmap.
     */
    @NonNull
    static Bitmap applyTransformationOnBitmap(Bitmap bitmap, int bmWidth, int bmHeight,
            @Nullable Rect clipping, @Nullable Matrix transformation) {
        if (transformation == null) {
            // According to PdfRenderer.page#render and PdfRendererPreV.page#render
            // transformation == null means that the bitmap should be stretched to clipping (if
            // provided) or otherwise destination size
            transformation = new Matrix();

            if (clipping != null) {
                transformation.postScale((float) clipping.width() / bitmap.getWidth(),
                        (float) clipping.height() / bitmap.getHeight());
                transformation.postTranslate(clipping.left, clipping.top);
            } else {
                transformation.postScale((float) bmWidth / bitmap.getWidth(),
                        (float) bmHeight / bitmap.getHeight());
            }
        }

        Bitmap transformedBm = Bitmap.createBitmap(bmWidth, bmHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(transformedBm);
        canvas.drawBitmap(bitmap, transformation, null);

        Bitmap clippedBm;
        if (clipping != null) {
            clippedBm = Bitmap.createBitmap(bmWidth, bmHeight, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(clippedBm);
            canvas.drawBitmap(transformedBm, clipping, clipping, null);
            transformedBm.recycle();
        } else {
            clippedBm = transformedBm;
        }

        return clippedBm;
    }

    /**
     * Get the fraction of non-matching pixels of two bitmaps. 1 == no pixels match, 0 == all pixels
     * match.
     *
     * @param a The first bitmap
     * @param b The second bitmap
     * @return The fraction of non-matching pixels.
     */
    @FloatRange(from = 0, to = 1)
    private static float getNonMatching(@NonNull Bitmap a, @NonNull Bitmap b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return 1;
        }

        int[] aPx = new int[a.getWidth() * a.getHeight()];
        int[] bPx = new int[b.getWidth() * b.getHeight()];
        a.getPixels(aPx, 0, a.getWidth(), 0, 0, a.getWidth(), a.getHeight());
        b.getPixels(bPx, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());

        int badPixels = 0;
        int totalPixels = a.getWidth() * a.getHeight();
        for (int i = 0; i < totalPixels; i++) {
            if (aPx[i] != bPx[i]) {
                badPixels++;
            }
        }

        return ((float) badPixels) / totalPixels;
    }

    /**
     * Render the PDF two times. Once with applying the transformation and clipping. The other time
     * render the PDF onto a bitmap and then clip and transform that image. The result should be
     * the same beside some minor aliasing.
     * Note: The comparison uses new overloaded constructor and render methods if useNewConstructor
     * is true
     *
     * @param width             The width of the resulting bitmap
     * @param height            The height of the resulting bitmap
     * @param docRes            The resource of the PDF document
     * @param clipping          The clipping to apply
     * @param transformation    The transformation to apply
     * @param renderMode        The render mode to use
     * @param renderFlag        The render flag to use
     * @param useNewConstructor If true the comparison will happen using the overloaded constructor
     *                          and render method
     * @param context           The context to use for creating the renderer
     */
    static void renderAndCompare(int width, int height, @RawRes int docRes, @Nullable Rect clipping,
            @Nullable Matrix transformation, int renderMode, int renderFlag,
            boolean useNewConstructor, @NonNull Context context) throws IOException {

        Bitmap originalBitmap = renderWithTransform(width, height, docRes, clipping, transformation,
                renderMode, renderFlag, useNewConstructor, context);
        Bitmap transformedBitmap = renderAndThenTransform(width, height, docRes, clipping,
                transformation, renderMode, renderFlag, useNewConstructor, context);

        compareBitmap(originalBitmap, transformedBitmap, height, width, docRes, clipping,
                transformation);
    }

    /**
     * Render the PDF two times. Once with applying the transformation and clipping. The other time
     * render the PDF onto a bitmap and then clip and transform that image. The result should be
     * the same beside some minor aliasing.
     *
     * @param width          The width of the resulting bitmap
     * @param height         The height of the resulting bitmap
     * @param docRes         The resource of the PDF document
     * @param clipping       The clipping to apply
     * @param transformation The transformation to apply
     * @param renderMode     The render mode to use
     * @param renderFlag     The render flag to use
     * @param context        The context to use for creating the Pre V renderer
     */
    static void renderPreVAndCompare(int width, int height, @RawRes int docRes,
            @Nullable Rect clipping, @Nullable Matrix transformation, int renderMode,
            int renderFlag, @NonNull Context context) throws IOException {

        Bitmap originalBitmap = renderPreV(width, height, docRes, clipping, transformation,
                renderMode, renderFlag, context);
        Bitmap transformedBitmap = renderPreVAndThenTransform(width, height, docRes, clipping,
                transformation, renderMode, renderFlag, context);

        compareBitmap(originalBitmap, transformedBitmap, height, width, docRes, clipping,
                transformation);
    }

    static void compareBitmap(Bitmap originalBitmap, Bitmap transformedBitmap, int height,
            int width, int docRes, Rect clipping, Matrix transformation) {
        try {
            // We allow 1% aliasing error
            float nonMatching = getNonMatching(originalBitmap, transformedBitmap);

            if (nonMatching == 0) {
                Log.d(LOG_TAG, "bitmaps match");
            } else if (nonMatching > 0.01) {
                fail("Testing width:" + width + ", height:" + height + ", docRes:" + docRes
                        + ", clipping:" + clipping + ", transform:" + transformation + ". Bitmaps "
                        + "differ by " + Math.ceil(nonMatching * 10000) / 100
                        + "%. That is too much.");
            } else {
                Log.d(LOG_TAG, "bitmaps differ by " + Math.ceil(nonMatching * 10000) / 100 + "%");
            }
        } finally {
            originalBitmap.recycle();
            transformedBitmap.recycle();
        }
    }

    /**
     * Calculate the area associated by the bounds of {@link Rect} present inside the match Rects
     *
     * @param matchRectList List of {@link  android.graphics.pdf.models.PageMatchBounds}
     * @return area
     */
    static int calculateArea(List<PageMatchBounds> matchRectList) {
        int area = 0;
        for (PageMatchBounds matchRect : matchRectList) {
            for (Rect rect : matchRect.getBounds()) {
                int rectArea = rect.height() * rect.width();
                // as rect can return negative height and width as well
                if (rectArea < 0) {
                    rectArea *= -1;
                }
                area += rectArea;
            }
        }
        return area;
    }

    /**
     * Creates a file in CTS apk internal storage
     *
     * @param context  The context use for creating the file
     * @param fileName Name use for creating the file
     * @return File in apps internal storage
     */
    @NonNull
    static File getFile(Context context, String fileName) throws IOException {
        // Create or access the internal storage directory for the CTS test app.
        File internalStorageDir = context.getFilesDir();
        // Create a new file in the internal storage directory.
        File file = new File(internalStorageDir, fileName);
        file.createNewFile();
        return file;
    }

    /**
     * Render a pdf onto a bitmap.
     *
     * @param bmWidth        The width of the destination bitmap (tileWidth)
     * @param bmHeight       The height of the destination bitmap (tileHeight)
     * @param docRes         The resource to load PDF from
     * @param destClip       The dest clip to render the bitmap (Coordinates)
     *                       the x-axis position on the page of the tile (i.e. the bitmap left
     *                       edge)
     *                       the y-axis position on the page of the tile (i.e. the bitmap
     *                       top edge)
     * @param transformation The transformation to be applied on the bitmap
     * @param renderMode     The render mode use to render the PDF
     * @param renderFlag     The render flag use to render the PDF
     * @param context        The context to use for creating the renderer
     * @return The rendered bitmap
     */
    @NonNull
    static Bitmap renderPreV(int bmWidth, int bmHeight, @RawRes int docRes, @Nullable Rect destClip,
            @Nullable Matrix transformation, int renderMode, int renderFlag,
            @NonNull Context context) throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(docRes, context, null)) {
            try (PdfRendererPreV.Page page = renderer.openPage(0)) {
                Bitmap bm = Bitmap.createBitmap(bmWidth, bmHeight, Bitmap.Config.ARGB_8888);
                page.render(bm, destClip, transformation, new RenderParams.Builder(
                        renderMode).setRenderFlags(renderFlag).build());
                return bm;
            }
        }
    }

    /**
     * Render a pdf onto a bitmap. Render the bitmap onto another bitmap while applying the
     * transformation. Hence use canvas' translation and clipping methods.
     *
     * @param bmWidth        The width of the destination bitmap
     * @param bmHeight       The height of the destination bitmap
     * @param docRes         The resource id of the doc
     * @param clipping       The clipping for the PDF document
     * @param transformation The transformation of the PDF
     * @param renderMode     The render mode to use to render the PDF
     * @param renderFlag     The render flag to use to render the PDF
     * @param context        The context to use for creating the renderer
     * @return The rendered bitmap
     */
    @NonNull
    private static Bitmap renderPreVAndThenTransform(int bmWidth, int bmHeight, @RawRes int docRes,
            @Nullable Rect clipping, @Nullable Matrix transformation, int renderMode,
            int renderFlag, @NonNull Context context) throws IOException {
        Bitmap renderedBm;

        renderedBm = sPreVRenderedBitmaps.get(docRes);

        if (renderedBm == null) {
            try (PdfRendererPreV renderer = Utils.createPreVRenderer(docRes, context, null)) {
                try (PdfRendererPreV.Page page = renderer.openPage(0)) {
                    renderedBm = Bitmap.createBitmap(page.getWidth(), page.getHeight(),
                            Bitmap.Config.ARGB_8888);
                    page.render(renderedBm, null, null, new RenderParams.Builder(
                            renderMode).setRenderFlags(renderFlag).build());
                }
            }
            sPreVRenderedBitmaps.put(docRes, renderedBm);
        }

        return applyTransformationOnBitmap(renderedBm, bmWidth, bmHeight, clipping, transformation);
    }

    /**
     * Take 16 color probes in the middle of the 16 segments of the page in the following pattern:
     * <pre>
     * +----+----+----+----+
     * |  0 :  1 :  2 :  3 |
     * +....:....:....:....+
     * |  4 :  5 :  6 :  7 |
     * +....:....:....:....+
     * |  8 :  9 : 10 : 11 |
     * +....:....:....:....+
     * | 12 : 13 : 14 : 15 |
     * +----+----+----+----+
     * </pre>
     *
     * @param bm The bitmap to probe
     * @return The color at the probes
     */
    @NonNull
    static int[] getColorProbes(@NonNull Bitmap bm) {
        int[] probes = new int[16];

        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                probes[row * 4 + column] = bm.getPixel((int) (bm.getWidth() * (column + 0.5) / 4),
                        (int) (bm.getHeight() * (row + 0.5) / 4));
            }
        }

        return probes;
    }
}
