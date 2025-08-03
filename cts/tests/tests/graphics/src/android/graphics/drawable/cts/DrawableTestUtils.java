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

package android.graphics.drawable.cts;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.IntegerRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import junit.framework.Assert;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * The useful methods for graphics.drawable test.
 */
public class DrawableTestUtils {
    private static final String LOGTAG = "DrawableTestUtils";

    // All of these constants range 0..1, with higher values being more lenient to differences
    // between images. Values of zero mean no differences will be tolerated.

    // Fail immediately if any *single* pixel diff exceeds this threshold
    static final float FATAL_PIXEL_ERROR_THRESHOLD = 0.2f;
    // Fail if the count of pixels with diffs above REGULAR_PIXEL_ERROR_THRESHOLD exceeds this ratio
    static final float MAX_REGULAR_ERROR_RATIO = 0.05f;
    // Threshold to count this pixel as a non-fatal error, the sum of which will be compared
    // against MAX_REGULAR_ERROR_RATIO
    static final float REGULAR_PIXEL_ERROR_THRESHOLD = 0.02f;

    public static void skipCurrentTag(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type=parser.next()) != XmlPullParser.END_DOCUMENT
               && (type != XmlPullParser.END_TAG
                       || parser.getDepth() > outerDepth)) {
        }
    }

    /**
     * Retrieve an AttributeSet from a XML.
     *
     * @param parser the XmlPullParser to use for the xml parsing.
     * @param searchedNodeName the name of the target node.
     * @return the AttributeSet retrieved from specified node.
     * @throws IOException
     * @throws XmlPullParserException
     */
    public static AttributeSet getAttributeSet(XmlResourceParser parser, String searchedNodeName)
            throws XmlPullParserException, IOException {
        AttributeSet attrs = null;
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && type != XmlPullParser.START_TAG) {
        }
        String nodeName = parser.getName();
        if (!"alias".equals(nodeName)) {
            throw new RuntimeException();
        }
        int outerDepth = parser.getDepth();
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }
            nodeName = parser.getName();
            if (searchedNodeName.equals(nodeName)) {
                outerDepth = parser.getDepth();
                while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                        && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                    if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                        continue;
                    }
                    nodeName = parser.getName();
                    attrs = Xml.asAttributeSet(parser);
                    break;
                }
                break;
            } else {
                skipCurrentTag(parser);
            }
        }
        return attrs;
    }

    public static XmlResourceParser getResourceParser(Resources res, int resId)
            throws XmlPullParserException, IOException {
        final XmlResourceParser parser = res.getXml(resId);
        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG
                && type != XmlPullParser.END_DOCUMENT) {
            // Empty loop
        }
        return parser;
    }

    public static void setResourcesDensity(Resources res, int densityDpi) {
        final Configuration config = new Configuration();
        config.setTo(res.getConfiguration());
        config.densityDpi = densityDpi;
        res.updateConfiguration(config, null);
    }

    /**
     * Implements scaling as used by the Bitmap class. Resulting values are
     * rounded up (as distinct from resource scaling, which truncates or rounds
     * to the nearest pixel).
     *
     * @param size the pixel size to scale
     * @param sdensity the source density that corresponds to the size
     * @param tdensity the target density
     * @return the pixel size scaled for the target density
     */
    public static int scaleBitmapFromDensity(int size, int sdensity, int tdensity) {
        if (sdensity == 0 || tdensity == 0 || sdensity == tdensity) {
            return size;
        }

        // Scale by tdensity / sdensity, rounding up.
        return ((size * tdensity) + (sdensity >> 1)) / sdensity;
    }

    /**
     * Asserts that two images are similar within the given thresholds.
     *
     * @param message Error message
     * @param expected Expected bitmap
     * @param actual Actual bitmap
     * @param fatalPixelErrorThreshold 0..1 - Fails immediately if any *single* pixel diff exceeds
     *     this threshold
     * @param maxRegularErrorRatio 0..1 - Fails if the count of pixels with diffs above
     *     regularPixelErrorThreshold exceeds this ratio
     * @param regularPixelErrorThreshold 0..1 - Threshold to count this pixel as a non-fatal error,
     *     the sum of which will be compared against MAX_REGULAR_ERROR_RATIO
     */
    public static void compareImages(
            String message,
            Bitmap expected,
            Bitmap actual,
            float fatalPixelErrorThreshold,
            float maxRegularErrorRatio,
            float regularPixelErrorThreshold) {
        int idealWidth = expected.getWidth();
        int idealHeight = expected.getHeight();

        Assert.assertTrue(idealWidth == actual.getWidth());
        Assert.assertTrue(idealHeight == actual.getHeight());

        int totalDiffPixelCount = 0;
        float totalPixelCount = idealWidth * idealHeight;
        for (int x = 0; x < idealWidth; x++) {
            for (int y = 0; y < idealHeight; y++) {
                int idealColor = expected.getPixel(x, y);
                int givenColor = actual.getPixel(x, y);
                if (idealColor == givenColor)
                    continue;
                if (Color.alpha(idealColor) + Color.alpha(givenColor) == 0) {
                    continue;
                }

                float idealAlpha = Color.alpha(idealColor) / 255.0f;
                float givenAlpha = Color.alpha(givenColor) / 255.0f;

                // compare premultiplied color values
                float pixelError = 0;
                pixelError += Math.abs((idealAlpha * Color.red(idealColor))
                                     - (givenAlpha * Color.red(givenColor)));
                pixelError += Math.abs((idealAlpha * Color.green(idealColor))
                                     - (givenAlpha * Color.green(givenColor)));
                pixelError += Math.abs((idealAlpha * Color.blue(idealColor))
                                     - (givenAlpha * Color.blue(givenColor)));
                pixelError += Math.abs(Color.alpha(idealColor) - Color.alpha(givenColor));
                pixelError /= 1024.0f;

                if (pixelError > fatalPixelErrorThreshold) {
                    Assert.fail(
                            String.format(
                                    "%s: pixelError of %f exceeds fatalPixelErrorThreshold of %f"
                                            + " for pixel (%d, %d)",
                            message,
                            pixelError,
                            fatalPixelErrorThreshold,
                            x,
                            y));
                }

                if (pixelError > regularPixelErrorThreshold) {
                    totalDiffPixelCount++;
                }
            }
        }
        float countedErrorRatio = totalDiffPixelCount / totalPixelCount;
        if (countedErrorRatio > maxRegularErrorRatio) {
            Assert.fail(
                    String.format(
                            "%s: countedErrorRatio of %f exceeds maxRegularErrorRatio of %f for"
                                    + " %dx%d image",
                            message,
                            countedErrorRatio,
                            maxRegularErrorRatio,
                            idealWidth,
                            idealHeight));
        }
    }

    /**
     * Returns the {@link Color} at the specified location in the {@link Drawable}.
     */
    public static int getPixel(Drawable d, int x, int y) {
        final int w = Math.max(d.getIntrinsicWidth(), x + 1);
        final int h = Math.max(d.getIntrinsicHeight(), y + 1);
        final Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        final Canvas c = new Canvas(b);
        d.setBounds(0, 0, w, h);
        d.draw(c);

        final int pixel = b.getPixel(x, y);
        b.recycle();
        return pixel;
    }

    /**
     * Save a bitmap for debugging or golden image (re)generation purpose.
     * The file name will be referred from the resource id, plus optionally {@code extras}, and
     * "_golden"
     */
    static void saveAutoNamedVectorDrawableIntoPNG(@NonNull Context context, @NonNull Bitmap bitmap,
            @IntegerRes int resId, @Nullable String extras)
            throws IOException {
        String originalFilePath = context.getResources().getString(resId);
        File originalFile = new File(originalFilePath);
        String fileFullName = originalFile.getName();
        String fileTitle = fileFullName.substring(0, fileFullName.lastIndexOf("."));
        String outputFolder = context.getExternalFilesDir(null).getAbsolutePath();
        if (extras != null) {
            fileTitle += "_" + extras;
        }
        saveVectorDrawableIntoPNG(bitmap, outputFolder, fileTitle);
    }

    /**
     * Save a {@code bitmap} to the {@code fileFullName} plus "_golden".
     */
    static void saveVectorDrawableIntoPNG(@NonNull Bitmap bitmap, @NonNull String outputFolder,
            @NonNull String fileFullName)
            throws IOException {
        // Save the image to the disk.
        FileOutputStream out = null;
        try {
            File folder = new File(outputFolder);
            if (!folder.exists()) {
                folder.mkdir();
            }
            String outputFilename = outputFolder + "/" + fileFullName + "_golden";
            outputFilename +=".png";
            File outputFile = new File(outputFilename);
            if (!outputFile.exists()) {
                outputFile.createNewFile();
            }

            out = new FileOutputStream(outputFile, false);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            Log.v(LOGTAG, "Write test No." + outputFilename + " to file successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
}
