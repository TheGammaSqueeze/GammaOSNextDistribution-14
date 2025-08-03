/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.view.Display;

import com.android.wallpaper.config.BaseFlags;
import com.android.wallpaper.module.InjectorProvider;

/**
 * Static utility methods for wallpaper cropping operations.
 */
public final class WallpaperCropUtils {

    private static final float WALLPAPER_SCREENS_SPAN = 2f;
    private static final float ASPECT_RATIO_LANDSCAPE = 16 / 10f;
    private static final float ASPECT_RATIO_PORTRAIT = 10 / 16f;
    private static final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE = 1.2f;
    private static final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT = 1.5f;

    // Suppress default constructor for noninstantiability.
    private WallpaperCropUtils() {
        throw new AssertionError();
    }

    /**
     * Calculates parallax travel (i.e., extra width) for a screen with the given resolution.
     */
    public static float wallpaperTravelToScreenWidthRatio(int width, int height) {
        float aspectRatio = width / (float) height;

        // At an aspect ratio of 16/10, the wallpaper parallax effect should span 1.2 * screen width
        // At an aspect ratio of 10/16, the wallpaper parallax effect should span 1.5 * screen width
        // We will use these two data points to extrapolate how much the wallpaper parallax effect
        // to span (i.e., travel) at any aspect ratio. We use the following two linear formulas,
        // where
        // the coefficient on x is the aspect ratio (width/height):
        //   (16/10)x + y = 1.2
        //   (10/16)x + y = 1.5
        // We solve for x and y and end up with a final formula:
        float x = (WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE
                - WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT)
                / (ASPECT_RATIO_LANDSCAPE - ASPECT_RATIO_PORTRAIT);
        float y = WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT - x * ASPECT_RATIO_PORTRAIT;
        return x * aspectRatio + y;
    }

    /**
     * Calculates ideal crop surface size for a device such that there is room for parallax in both
     * landscape and portrait screen orientations.
     */
    public static Point getDefaultCropSurfaceSize(Resources resources, Display display) {
        Point minDims = new Point();
        Point maxDims = new Point();
        display.getCurrentSizeRange(minDims, maxDims);

        int maxDim = Math.max(maxDims.x, maxDims.y);
        int minDim = Math.max(minDims.x, minDims.y);

        if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN_MR1) {
            Point realSize = new Point();
            display.getRealSize(realSize);
            maxDim = Math.max(realSize.x, realSize.y);
            minDim = Math.min(realSize.x, realSize.y);
        }

        return calculateCropSurfaceSize(resources, maxDim, minDim, display.getWidth(),
                display.getHeight());
    }

    /**
     * Calculates ideal crop surface size for a surface of dimensions maxDim x minDim such that
     * there is room for parallax in both* landscape and portrait screen orientations.
     */
    public static Point calculateCropSurfaceSize(Resources resources, int maxDim, int minDim,
            int width, int height) {
        final int defaultWidth, defaultHeight;
        if (resources.getConfiguration().smallestScreenWidthDp >= 720) {
            defaultWidth = (int) (maxDim * wallpaperTravelToScreenWidthRatio(maxDim, minDim));
        } else {
            defaultWidth = Math.max((int) (minDim * WALLPAPER_SCREENS_SPAN), maxDim);
        }
        defaultHeight = width < height ? maxDim : minDim;

        return new Point(defaultWidth, defaultHeight);
    }

    /**
     * Calculates the relative position between an outer and inner rectangle when the outer one is
     * center-cropped to the inner one.
     *
     * @param outer      Size of outer rectangle as a Point (x,y).
     * @param inner      Size of inner rectangle as a Point (x,y).
     * @param alignStart Whether the inner rectangle should be aligned to the start of the layout
     *                   with the outer one and ignore horizontal centering.
     * @param isRtl      Whether the layout direction is RTL (or false for LTR).
     * @return Position relative to the top left corner of the outer rectangle, where the size of
     * each rectangle is represented by Points, in coordinates (x,y) relative to the outer rectangle
     * where the top left corner is (0,0)
     * @throws IllegalArgumentException if inner rectangle is not contained within outer rectangle
     *                                  which would return a position with at least one negative
     *                                  coordinate.
     */
    public static Point calculateCenterPosition(Point outer, Point inner, boolean alignStart,
            boolean isRtl) {
        if (inner.x > outer.x || inner.y > outer.y) {
            throw new IllegalArgumentException("Inner rectangle " + inner + " should be contained"
                    + " completely within the outer rectangle " + outer + ".");
        }

        Point relativePosition = new Point();

        if (alignStart) {
            relativePosition.x = isRtl ? outer.x - inner.x : 0;
        } else {
            relativePosition.x = Math.round((outer.x - inner.x) / 2f);
        }
        relativePosition.y = Math.round((outer.y - inner.y) / 2f);

        return relativePosition;
    }

    /**
     * Calculates the minimum zoom such that the maximum surface area of the outer rectangle is
     * visible within the inner rectangle.
     *
     * @param outer Size of outer rectangle as a Point (x,y).
     * @param inner Size of inner rectangle as a Point (x,y).
     */
    public static float calculateMinZoom(Point outer, Point inner) {
        float minZoom;
        if (inner.x / (float) inner.y > outer.x / (float) outer.y) {
            minZoom = inner.x / (float) outer.x;
        } else {
            minZoom = inner.y / (float) outer.y;
        }
        return minZoom;
    }


    /**
     * Calculates the center position of a wallpaper of the given size, based on a "crop surface"
     * (with extra width to account for parallax) superimposed on the screen. Trying to show as
     * much of the wallpaper as possible on the crop surface and align screen to crop surface such
     * that the centered wallpaper matches what would be seen by the user in the left-most home
     * screen.
     *
     * @param wallpaperSize full size of the wallpaper
     * @param visibleWallpaperRect visible area available for the wallpaper
     * @return a point corresponding to the position of wallpaper that should be in the center
     *      of the screen.
     */
    public static PointF calculateDefaultCenter(Context context, Point wallpaperSize,
            Rect visibleWallpaperRect) {

        WallpaperCropUtils.adjustCurrentWallpaperCropRect(context, wallpaperSize,
                visibleWallpaperRect);

        return new PointF(visibleWallpaperRect.centerX(),
                visibleWallpaperRect.centerY());
    }

    /**
     * Calculates the rectangle to crop a wallpaper of the given size, and considering the given
     * scrollX and scrollY offsets
     * @param wallpaperZoom zoom applied to the raw wallpaper image
     * @param wallpaperSize full ("raw") wallpaper size
     * @param defaultCropSurfaceSize @see #getDefaultCropSurfaceSize(Resources, Display)
     * @param targetHostSize target size where the wallpaper will be rendered (eg, the display size)
     * @param scrollX x-axis offset the cropping area from the wallpaper's 0,0 position
     * @param scrollY y-axis offset the cropping area from the wallpaper's 0,0 position
     * @return a Rect representing the area of the wallpaper to crop.
     */
    public static Rect calculateCropRect(Context context, float wallpaperZoom, Point wallpaperSize,
            Point defaultCropSurfaceSize, Point targetHostSize, int scrollX, int scrollY,
            boolean cropExtraWidth) {
        BaseFlags flags = InjectorProvider.getInjector().getFlags();
        boolean isMultiCropEnabled =
                flags.isMultiCropPreviewUiEnabled() && flags.isMultiCropEnabled();
        // Calculate Rect of wallpaper in physical pixel terms (i.e., scaled to current zoom).
        int scaledWallpaperWidth = Math.round(wallpaperSize.x * wallpaperZoom);
        int scaledWallpaperHeight = Math.round(wallpaperSize.y * wallpaperZoom);
        Rect scaledWallpaperRect = new Rect();
        scaledWallpaperRect.set(0, 0, scaledWallpaperWidth, scaledWallpaperHeight);

        // Crop rect should start off as the visible screen and then include extra width and height
        // if available within wallpaper at the current zoom.
        Rect cropRect = new Rect(scrollX, scrollY, scrollX + targetHostSize.x,
                scrollY + targetHostSize.y);

        int extraWidth = defaultCropSurfaceSize.x - targetHostSize.x;
        int extraHeightTopAndBottom = (int) ((defaultCropSurfaceSize.y - targetHostSize.y) / 2f);

        if (cropExtraWidth) {
            // Try to increase size of screenRect to include extra width depending on the layout
            // direction.
            if (RtlUtils.isRtl(context)) {
                cropRect.left = Math.max(cropRect.left - extraWidth, scaledWallpaperRect.left);
            } else {
                cropRect.right = Math.min(cropRect.right + extraWidth, scaledWallpaperRect.right);
            }
        }

        // Try to increase the size of the cropRect to to include extra height.
        int availableExtraHeightTop = cropRect.top - Math.max(
                scaledWallpaperRect.top,
                cropRect.top - extraHeightTopAndBottom);
        int availableExtraHeightBottom = Math.min(
                scaledWallpaperRect.bottom,
                cropRect.bottom + extraHeightTopAndBottom) - cropRect.bottom;
        if (isMultiCropEnabled) {
            // availableExtraHeightBottom shouldn't be negative, but it could happen if wallpaper
            // zoom < 1, in that case it should be zero. Safe guard availableExtraHeightTop as well.
            // Looks like a bug, but we always have > 1 zoom for existing preview, so flag guard
            // this block.
            availableExtraHeightTop = Math.max(availableExtraHeightTop, 0);
            availableExtraHeightBottom = Math.max(availableExtraHeightBottom, 0);
        }

        int availableExtraHeightTopAndBottom =
                Math.min(availableExtraHeightTop, availableExtraHeightBottom);
        cropRect.top -= availableExtraHeightTopAndBottom;
        cropRect.bottom += availableExtraHeightTopAndBottom;

        return cropRect;
    }

    /**
     * Calculates the center area of the outer rectangle which is visible in the inner rectangle
     * after applying the minimum zoom.
     *
     * @param outer the size of outer rectangle as a Point (x,y).
     * @param inner the size of inner rectangle as a Point (x,y).
     */
    public static Rect calculateVisibleRect(Point outer, Point inner) {
        PointF visibleRectCenter = new PointF(outer.x / 2f, outer.y / 2f);
        if (inner.x / (float) inner.y > outer.x / (float) outer.y) {
            float minZoom = inner.x / (float) outer.x;
            float visibleRectHeight = inner.y / minZoom;
            return new Rect(0, (int) (visibleRectCenter.y - visibleRectHeight / 2),
                    outer.x, (int) (visibleRectCenter.y + visibleRectHeight / 2));
        } else {
            float minZoom = inner.y / (float) outer.y;
            float visibleRectWidth = inner.x / minZoom;
            return new Rect((int) (visibleRectCenter.x - visibleRectWidth / 2),
                    0, (int) (visibleRectCenter.x + visibleRectWidth / 2), outer.y);
        }
    }

    public static void adjustCurrentWallpaperCropRect(Context context, Point assetDimensions,
            Rect cropRect) {
        adjustCropRect(context, cropRect, true /* zoomIn */);
    }

    /** Adjust the crop rect by zooming in with systemWallpaperMaxScale. */
    public static void adjustCropRect(Context context, Rect cropRect, boolean zoomIn) {
        float centerX = cropRect.centerX();
        float centerY = cropRect.centerY();
        float width = cropRect.width();
        float height = cropRect.height();
        float systemWallpaperMaxScale = getSystemWallpaperMaximumScale(context);
        float scale = zoomIn ? systemWallpaperMaxScale : 1.0f / systemWallpaperMaxScale;

        // Adjust the rect according to the system wallpaper's maximum scale.
        int left = (int) (centerX - (width / 2) / scale);
        int top = (int) (centerY - (height / 2) / scale);
        int right = (int) (centerX + (width / 2) / scale);
        int bottom = (int) (centerY + (height / 2) / scale);
        cropRect.set(left, top, right, bottom);
    }

    /** Adjust the given Point, representing a size by  systemWallpaperMaxScale. */
    public static void scaleSize(Context context, Point size) {
        float systemWallpaperMaxScale = getSystemWallpaperMaximumScale(context);
        size.set((int) (size.x * systemWallpaperMaxScale),
                (int) (size.y * systemWallpaperMaxScale));
    }
    /**
     * Calculates {@link Rect} of the wallpaper which we want to crop to in physical pixel terms
     * (i.e., scaled to current zoom) when the wallpaper is laid on a fullscreen view.
     */
    public static Rect calculateCropRect(Context context, Display display, Point rawWallpaperSize,
            Rect visibleRawWallpaperRect, float wallpaperZoom) {
        Point screenSize = ScreenSizeCalculator.getInstance().getScreenSize(display);
        Point defaultCropSize = WallpaperCropUtils.getDefaultCropSurfaceSize(
                context.getResources(), display);
        return calculateCropRect(context, screenSize, defaultCropSize, rawWallpaperSize,
                visibleRawWallpaperRect, wallpaperZoom);
    }

    /**
     * Calculates {@link Rect} of the wallpaper which we want to crop to in physical pixel terms
     * (i.e., scaled to current zoom).
     *
     * @param hostViewSize            the size of the view hosting the wallpaper as a Point (x,y).
     * @param cropSize                the default size of the crop as a Point (x,y).
     * @param rawWallpaperSize        the size of the raw wallpaper as a Point (x,y).
     * @param visibleRawWallpaperRect the area of the raw wallpaper which is expected to see.
     * @param wallpaperZoom           the factor which is used to scale the raw wallpaper.
     * @param cropExtraWidth          true to crop extra wallpaper width for panel sliding.
     */
    public static Rect calculateCropRect(Context context, Point hostViewSize, Point cropSize,
            Point rawWallpaperSize, Rect visibleRawWallpaperRect, float wallpaperZoom,
            boolean cropExtraWidth) {
        int scrollX = (int) (visibleRawWallpaperRect.left * wallpaperZoom);
        int scrollY = (int) (visibleRawWallpaperRect.top * wallpaperZoom);

        return calculateCropRect(context, wallpaperZoom, rawWallpaperSize, cropSize, hostViewSize,
                scrollX, scrollY, cropExtraWidth);
    }

    /**
     * Calculates {@link Rect} of the wallpaper which we want to crop to in physical pixel terms
     * (i.e., scaled to current zoom).
     *
     * @param hostViewSize            the size of the view hosting the wallpaper as a Point (x,y).
     * @param cropSize                the default size of the crop as a Point (x,y).
     * @param rawWallpaperSize        the size of the raw wallpaper as a Point (x,y).
     * @param visibleRawWallpaperRect the area of the raw wallpaper which is expected to see.
     * @param wallpaperZoom           the factor which is used to scale the raw wallpaper.
     */
    public static Rect calculateCropRect(Context context, Point hostViewSize, Point cropSize,
            Point rawWallpaperSize, Rect visibleRawWallpaperRect, float wallpaperZoom) {
        return calculateCropRect(context, hostViewSize, cropSize, rawWallpaperSize,
                visibleRawWallpaperRect, wallpaperZoom, /* cropExtraWidth= */ true);
    }

    /**
     * Resize the wallpaper size so it's new size fits in a outWidth by outHeight rectangle.
     *
     * @param wallpaperSize Rectangle with the current wallpaper size. It will be resized.
     * @param outWidth      the width of the rectangle in which the wallpaperSize needs to fit.
     * @param outHeight     the height of the rectangle in which the wallpaperSize needs to fit.
     */
    public static void fitToSize(Rect wallpaperSize, int outWidth, int outHeight) {
        if (wallpaperSize.isEmpty()) {
            return;
        }
        float maxSizeOut = Math.max(outWidth, outHeight);
        float maxSizeIn = Math.max(wallpaperSize.width(), wallpaperSize.height());
        float scale = maxSizeOut / maxSizeIn;

        // Scale the wallpaper size
        if (scale != 1.0f) {
            wallpaperSize.left = (int) (wallpaperSize.left * scale + 0.5f);
            wallpaperSize.top = (int) (wallpaperSize.top * scale + 0.5f);
            wallpaperSize.right = (int) (wallpaperSize.right * scale + 0.5f);
            wallpaperSize.bottom = (int) (wallpaperSize.bottom * scale + 0.5f);
        }
    }

    /**
     * Get the system wallpaper's maximum scale value.
     */
    public static float getSystemWallpaperMaximumScale(Context context) {
        return context.getResources()
                .getFloat(Resources.getSystem().getIdentifier(
                        /* name= */ "config_wallpaperMaxScale",
                        /* defType= */ "dimen",
                        /* defPackage= */ "android"));
    }

    /**
     * Gets the scale of screen size and crop rect real size
     *
     * @param wallpaperScale The scale of crop rect and real size rect
     * @param cropRect The area wallpaper cropped
     * @param screenWidth  The width of screen size
     * @param screenHeight The height of screen size
     */
    public static float getScaleOfScreenResolution(float wallpaperScale, Rect cropRect,
            int screenWidth, int screenHeight) {
        int rectRealWidth = Math.round((float) cropRect.width() / wallpaperScale);
        int rectRealHeight = Math.round((float) cropRect.height() / wallpaperScale);
        int cropWidth = cropRect.width();
        int cropHeight = cropRect.height();
        // Not scale with screen resolution because cropRect is bigger than screen size.
        if (cropWidth >= screenWidth || cropHeight >= screenHeight) {
            return 1;
        }

        int newWidth = screenWidth;
        int newHeight = screenHeight;
        // Screen size is bigger than crop real size so we only need enlarge to real size
        if (newWidth > rectRealWidth || newHeight > rectRealHeight) {
            newWidth = rectRealWidth;
            newHeight = rectRealWidth;
        }
        float screenScale = Math.min((float) newWidth / cropWidth, (float) newHeight / cropHeight);

        // screenScale < 1 means our real crop size is smaller than crop size it should be.
        // So we do nothing in this case, otherwise it'll cause wallpaper smaller than we expected.
        if (screenScale < 1) {
            return 1;
        }
        return screenScale;
    }
}
