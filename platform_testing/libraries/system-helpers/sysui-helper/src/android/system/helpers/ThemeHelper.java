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

package android.system.helpers;

import static android.app.WallpaperManager.FLAG_SYSTEM;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Color;
import android.graphics.Rect;
import android.text.TextUtils;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.uiautomator.UiDevice;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A common helper class for theme scenario tests. */
public class ThemeHelper {

    private static final float COLOR_TOLERANCE = 0.01F;

    @NonNull private final UiDevice mUiDevice;
    @NonNull private final Context mContext;
    @NonNull private final WallpaperManager mWallpaperManager;

    public ThemeHelper(@NonNull UiDevice uiDevice, @NonNull Context context) {
        mUiDevice = uiDevice;
        mContext = context;
        mWallpaperManager =
                Objects.requireNonNull(mContext.getSystemService(WallpaperManager.class));
    }

    /**
     * Sets wallpaper
     *
     * @param color the color for wallpaper
     * @throws IOException exception during setWallpaper
     */
    public void setWallpaper(@ColorInt int color) throws IOException {
        final Bitmap bitmap =
                Bitmap.createBitmap(
                        mUiDevice.getDisplayWidth(),
                        mUiDevice.getDisplayHeight(),
                        Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);

        final byte[] byteArray;
        try {
            final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            final boolean compressResult =
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream);
            if (!compressResult) {
                throw new IOException("Fail to compress bitmap");
            }
            byteArray = outStream.toByteArray();
            mWallpaperManager.setStream(
                    new ByteArrayInputStream(byteArray), null, false, FLAG_SYSTEM);
        } finally {
            bitmap.recycle();
        }
    }

    /**
     * Gets primary color from WallpaperColors
     *
     * @return primary color
     */
    @ColorInt
    public int getWallpaperPrimaryColor() {
        return mWallpaperManager.getWallpaperColors(FLAG_SYSTEM).getPrimaryColor().toArgb();
    }

    private void deleteFileIfExist(@Nullable String fileAbsPath) {
        if (TextUtils.isEmpty(fileAbsPath)) {
            return;
        }
        new File(fileAbsPath).deleteOnExit();
    }

    /**
     * Gets highest count of color inside this bitmap
     *
     * @param bitmap the bitmap for analysis
     * @return most rendered color
     */
    @NonNull
    public Color getMostColor(@NonNull Bitmap bitmap) {
        HashMap<Integer, Integer> colors = new HashMap<>();
        for (int x = 0; x < bitmap.getWidth(); ++x) {
            for (int y = 0; y < bitmap.getHeight(); ++y) {
                @ColorInt int color = bitmap.getColor(x, y).toArgb();
                colors.put(color, colors.containsKey(color) ? colors.get(color) + 1 : 1);
            }
        }
        List<Map.Entry<Integer, Integer>> colorList = new ArrayList<>(colors.entrySet());
        final Map.Entry<Integer, Integer> mostColorCountEntry =
                colorList.stream().max(Comparator.comparingInt(Map.Entry::getValue)).get();
        return Color.valueOf(mostColorCountEntry.getKey());
    }

    private String takeScreenshotToFile(@NonNull String filename) {
        File f = new File(mContext.getFilesDir(), filename);
        mUiDevice.takeScreenshot(f);
        if (f.exists()) {
            return f.getAbsolutePath();
        }
        return null;
    }

    /**
     * Takes a screenshot and calculates in the specific rect
     *
     * @param rect the rect for calculating the most rendered color
     * @return most rendered color
     * @throws IOException exception if taking screenshot fails
     */
    public Color getScreenshotMostColorAsRect(@NonNull Rect rect) throws IOException {
        String fileAbsPath = null;
        Bitmap bitmap = null;
        BitmapRegionDecoder bitmapRegionDecoder = null;
        try {
            fileAbsPath = takeScreenshotToFile("test1");
            bitmapRegionDecoder = BitmapRegionDecoder.newInstance(fileAbsPath);
            bitmap = bitmapRegionDecoder.decodeRegion(rect, null);
            return getMostColor(bitmap);
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
            if (bitmapRegionDecoder != null) {
                bitmapRegionDecoder.recycle();
            }
            deleteFileIfExist(fileAbsPath);
        }
    }

    /**
     * Gets current activated Quick Setting background color.
     *
     * @return color The theme color for activated quick setting tile background
     */
    @NonNull
    public Color getSysuiActivatedThemeColor() {
        return Color.valueOf(mContext.getColor(android.R.color.system_accent1_100));
    }

    /**
     * Validate the colors in between are similar or not.
     *
     * @param color1 the first color
     * @param color2 the second color
     * @return true if the colors in between are similar, false otherwise
     */
    public boolean isSimilarColor(@NonNull Color color1, @NonNull Color color2) {
        return color1.alpha() == color2.alpha()
                && Math.abs(color1.red() - color2.red()) < COLOR_TOLERANCE
                && Math.abs(color1.green() - color2.green()) < COLOR_TOLERANCE
                && Math.abs(color1.blue() - color2.blue()) < COLOR_TOLERANCE;
    }
}
