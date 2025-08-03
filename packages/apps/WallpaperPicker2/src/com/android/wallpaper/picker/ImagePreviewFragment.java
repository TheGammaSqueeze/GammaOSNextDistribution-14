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
package com.android.wallpaper.picker;

import static android.stats.style.StyleEnums.SET_WALLPAPER_ENTRY_POINT_WALLPAPER_PREVIEW;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.wallpaper.util.WallpaperSurfaceCallback.LOW_RES_BITMAP_BLUR_RADIUS;

import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.CurrentWallpaperAsset;
import com.android.wallpaper.model.SetWallpaperViewModel;
import com.android.wallpaper.model.WallpaperInfo.ColorInfo;
import com.android.wallpaper.module.BitmapCropper;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.WallpaperPersister.Destination;
import com.android.wallpaper.module.WallpaperPreferences;
import com.android.wallpaper.util.DisplayUtils;
import com.android.wallpaper.util.OnFullResImageViewStateChangedListener;
import com.android.wallpaper.util.ResourceUtils;
import com.android.wallpaper.util.RtlUtils;
import com.android.wallpaper.util.ScreenSizeCalculator;
import com.android.wallpaper.util.WallpaperColorsExtractor;
import com.android.wallpaper.util.WallpaperCropUtils;

import com.bumptech.glide.Glide;
import com.bumptech.glide.MemoryCategory;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Fragment which displays the UI for previewing an individual static image wallpaper and its
 * attribution information.
 */
public class ImagePreviewFragment extends PreviewFragment {

    private static final String TAG = "ImagePreviewFragment";

    private static final float DEFAULT_WALLPAPER_MAX_ZOOM = 8f;
    private static final Interpolator ALPHA_OUT = new PathInterpolator(0f, 0f, 0.8f, 1f);
    private static final Executor sExecutor = Executors.newCachedThreadPool();

    private final WallpaperSurfaceCallback mWallpaperSurfaceCallback =
            new WallpaperSurfaceCallback();
    private final Injector mInjector = InjectorProvider.getInjector();

    /**
     * Size of the screen considered for cropping the wallpaper (typically the same as
     * {@link #mScreenSize} but it could be different on multi-display)
     */
    private Point mWallpaperScreenSize;
    /**
     * The size of the current screen
     */
    private Point mScreenSize;
    protected Point mRawWallpaperSize; // Native size of wallpaper image.
    private WallpaperPreferences mWallpaperPreferences;
    protected Asset mWallpaperAsset;
    protected Future<ColorInfo> mColorFuture;
    private WallpaperPreviewBitmapTransformation mPreviewBitmapTransformation;
    private BitmapCropper mBitmapCropper;
    private WallpaperColorsExtractor mWallpaperColorsExtractor;
    private DisplayUtils mDisplayUtils;
    private WallpaperManager mWallpaperManager;

    // UI
    protected SurfaceView mWallpaperSurface;
    protected ImageView mLowResImageView;
    protected SubsamplingScaleImageView mFullResImageView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = requireContext();
        Context appContext = context.getApplicationContext();
        mWallpaperAsset = mWallpaper.getAsset(appContext);
        mColorFuture = mWallpaper.computeColorInfo(context);
        mWallpaperPreferences = mInjector.getPreferences(context);
        mPreviewBitmapTransformation = new WallpaperPreviewBitmapTransformation(
                appContext, RtlUtils.isRtl(context));
        mBitmapCropper = mInjector.getBitmapCropper();
        mWallpaperColorsExtractor = new WallpaperColorsExtractor(sExecutor, Handler.getMain());
        mWallpaperManager = context.getSystemService(WallpaperManager.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        if (view == null) {
            return null;
        }
        // Until we have initialized mRawWallpaperSize, we can't set wallpaper
        mSetWallpaperButton.setEnabled(false);
        mSetWallpaperButtonContainer.setEnabled(false);
        Activity activity = requireActivity();
        mDisplayUtils = mInjector.getDisplayUtils(activity);
        ScreenSizeCalculator screenSizeCalculator = ScreenSizeCalculator.getInstance();
        mScreenSize = screenSizeCalculator.getScreenSize(
                activity.getWindowManager().getDefaultDisplay());
        // "Wallpaper screen" size will be the size of the largest screen available
        mWallpaperScreenSize = screenSizeCalculator.getScreenSize(
                mDisplayUtils.getWallpaperDisplay());
        // Touch forwarding layout
        setUpTouchForwardingLayout();
        // Wallpaper surface
        mWallpaperSurface = view.findViewById(R.id.wallpaper_surface);
        mWallpaperSurface.getHolder().addCallback(mWallpaperSurfaceCallback);
        // Trim memory from Glide to make room for the full-size image in this fragment.
        Glide.get(activity).setMemoryCategory(MemoryCategory.LOW);
        return view;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public SubsamplingScaleImageView getFullResImageView() {
        return mFullResImageView;
    }

    private void setUpTouchForwardingLayout() {
        mTouchForwardingLayout.setForwardingEnabled(true);
        mTouchForwardingLayout.setOnClickListener(v -> {
            toggleWallpaperPreviewControl();
            mTouchForwardingLayout.announceForAccessibility(
                    getString(mPreviewScrim.getVisibility() == View.VISIBLE
                            ? R.string.show_preview_controls_content_description
                            : R.string.hide_preview_controls_content_description)
            );
        });
        mFloatingSheet.addFloatingSheetCallback(
                new BottomSheetBehavior.BottomSheetCallback() {
                    @Override
                    public void onStateChanged(@NonNull View bottomSheet, int newState) {
                        if (newState == STATE_EXPANDED) {
                            mTouchForwardingLayout.setForwardingEnabled(false);
                        } else if (newState == STATE_HIDDEN) {
                            mTouchForwardingLayout.setForwardingEnabled(true);
                        }
                    }

                    @Override
                    public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                    }
                });
    }

    @Override
    public void onDestroy() {
        if (mFullResImageView != null) {
            mFullResImageView.recycle();
        }
        mWallpaperSurfaceCallback.cleanUp();
        super.onDestroy();
    }

    @Override
    protected void setWallpaper(@Destination int destination) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        if (mRawWallpaperSize == null) {
            // This shouldn't happen, avoid direct call into setWallpaper without initializing
            // mRawWallpaperSize first
            showSetWallpaperErrorDialog();
            return;
        }
        // Only crop extra wallpaper width for single display devices.
        Rect cropRect = calculateCropRect(context, !mDisplayUtils.hasMultiInternalDisplays());
        float screenScale = WallpaperCropUtils.getScaleOfScreenResolution(
                mFullResImageView.getScale(), cropRect, mWallpaperScreenSize.x,
                mWallpaperScreenSize.y);
        Rect scaledCropRect = new Rect(
                Math.round((float) cropRect.left * screenScale),
                Math.round((float) cropRect.top * screenScale),
                Math.round((float) cropRect.right * screenScale),
                Math.round((float) cropRect.bottom * screenScale));
        mWallpaperSetter.setCurrentWallpaper(
                getActivity(),
                mWallpaper,
                mWallpaperAsset,
                SET_WALLPAPER_ENTRY_POINT_WALLPAPER_PREVIEW,
                destination,
                mFullResImageView.getScale() * screenScale,
                scaledCropRect,
                mWallpaperColors,
                SetWallpaperViewModel.getCallback(mViewModelProvider));
    }

    /**
     * Initializes image view by initializing tiling, setting a fallback page bitmap, and
     * initializing a zoom-scroll observer and click listener.
     */
    private synchronized void initFullResView() {
        if (mRawWallpaperSize == null || mFullResImageView == null
                || mFullResImageView.isImageLoaded()) {
            return;
        }

        final String storedWallpaperId = mWallpaper.getStoredWallpaperId(getContext());
        final boolean isWallpaperColorCached =
                storedWallpaperId != null && mWallpaperPreferences.getWallpaperColors(
                        storedWallpaperId) != null;
        if (isWallpaperColorCached) {
            // Post-execute onWallpaperColorsChanged() to avoid UI blocking from the call
            Handler.getMain().post(() -> onWallpaperColorsChanged(
                    mWallpaperPreferences.getWallpaperColors(
                            mWallpaper.getStoredWallpaperId(getContext()))));
        }

        // Minimum scale will only be respected under this scale type.
        mFullResImageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CUSTOM);
        // When we set a minimum scale bigger than the scale with which the full image is shown,
        // disallow user to pan outside the view we show the wallpaper in.
        mFullResImageView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE);

        Point targetPageBitmapSize = new Point(mRawWallpaperSize);
        mWallpaperAsset.decodeBitmap(targetPageBitmapSize.x, targetPageBitmapSize.y,
                pageBitmap -> {
                    if (getActivity() == null || mFullResImageView == null) {
                        return;
                    }

                    if (pageBitmap == null) {
                        showLoadWallpaperErrorDialog();
                        return;
                    }

                    mFullResImageView.setImage(ImageSource.bitmap(pageBitmap));
                    setDefaultWallpaperZoomAndScroll(
                            mWallpaperAsset instanceof CurrentWallpaperAsset);
                    mFullResImageView.setOnStateChangedListener(
                            new OnFullResImageViewStateChangedListener() {
                                @Override
                                public void onDebouncedCenterChanged(PointF newCenter, int origin) {
                                    recalculateColors();
                                }
                            }
                    );
                    if (!isWallpaperColorCached) {
                        mFullResImageView.setAlpha(0);
                        // If not cached, delay the cross fade until the colors extracted
                        extractColorFromBitmap(pageBitmap, true);
                    } else {
                        onSurfaceReady();
                    }
                });
    }

    /**
     * Recalculate the color from a new crop of the wallpaper. Note that we do not cache the
     * extracted. We only cache the color the first time we extract from the wallpaper as its
     * original size.
     */
    private void recalculateColors() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        mBitmapCropper.cropAndScaleBitmap(mWallpaperAsset, mFullResImageView.getScale(),
                calculateCropRect(context, /* cropExtraWidth= */ true), /* adjustForRtl= */ false,
                new BitmapCropper.Callback() {
                    @Override
                    public void onBitmapCropped(Bitmap croppedBitmap) {
                        extractColorFromBitmap(croppedBitmap, false);
                    }

                    @Override
                    public void onError(@Nullable Throwable e) {
                        Log.w(TAG, "Recalculate colors, crop and scale bitmap failed.", e);
                    }
                });
    }

    private void extractColorFromBitmap(Bitmap croppedBitmap, boolean cacheColor) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        mWallpaperColorsExtractor.extractWallpaperColors(croppedBitmap,
                colors -> {
                    if (mFullResImageView.getAlpha() == 0) {
                        onSurfaceReady();
                    }
                    onWallpaperColorsChanged(colors);
                    if (cacheColor) {
                        mWallpaperPreferences.storeWallpaperColors(
                                mWallpaper.getStoredWallpaperId(context), colors);
                    }
                });
    }

    /**
     * This should be called when the full resolution image is loaded and the wallpaper color is
     * ready, either extracted from the wallpaper or retrieved from cache.
     */
    private void onSurfaceReady() {
        mProgressBar.setVisibility(View.GONE);
        crossFadeInFullResView();
        // Set button enabled for the visual change
        mSetWallpaperButton.setEnabled(true);
        // Set button container enabled to make it clickable
        mSetWallpaperButtonContainer.setEnabled(true);
    }

    /**
     * Fade in the full resolution view.
     */
    protected void crossFadeInFullResView() {
        if (getActivity() == null || !isAdded()) {
            return;
        }
        long shortAnimationDuration = getResources().getInteger(
                android.R.integer.config_shortAnimTime);

        mFullResImageView.setAlpha(0f);
        mFullResImageView.animate()
                .alpha(1f)
                .setInterpolator(ALPHA_OUT)
                .setDuration(shortAnimationDuration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (mLowResImageView != null) {
                            mLowResImageView.setImageBitmap(null);
                        }
                    }
                });
    }

    /**
     * Sets the default wallpaper zoom and scroll position based on a "crop surface" (with extra
     * width to account for parallax) superimposed on the screen. Shows as much of the wallpaper as
     * possible on the crop surface and align screen to crop surface such that the default preview
     * matches what would be seen by the user in the left-most home screen.
     *
     * <p>This method is called once in the Fragment lifecycle after the wallpaper asset has loaded
     * and rendered to the layout.
     *
     * @param offsetToStart {@code true} if we want to offset the visible rectangle to the start
     *                      side of the raw wallpaper; {@code false} otherwise.
     */
    private void setDefaultWallpaperZoomAndScroll(boolean offsetToStart) {
        // Determine minimum zoom to fit maximum visible area of wallpaper on crop surface.
        int cropWidth = mWallpaperSurface.getMeasuredWidth();
        int cropHeight = mWallpaperSurface.getMeasuredHeight();
        Point crop = new Point(cropWidth, cropHeight);
        Rect visibleRawWallpaperRect =
                WallpaperCropUtils.calculateVisibleRect(mRawWallpaperSize, crop);
        if (offsetToStart && mDisplayUtils.isSingleDisplayOrUnfoldedHorizontalHinge(
                requireActivity())) {
            if (RtlUtils.isRtl(requireContext())) {
                visibleRawWallpaperRect.offsetTo(mRawWallpaperSize.x
                        - visibleRawWallpaperRect.width(), visibleRawWallpaperRect.top);
            } else {
                visibleRawWallpaperRect.offsetTo(/* newLeft= */ 0, visibleRawWallpaperRect.top);
            }
        }

        final PointF centerPosition = new PointF(visibleRawWallpaperRect.centerX(),
                visibleRawWallpaperRect.centerY());

        Point visibleRawWallpaperSize = new Point(visibleRawWallpaperRect.width(),
                visibleRawWallpaperRect.height());

        final float defaultWallpaperZoom = WallpaperCropUtils.calculateMinZoom(
                visibleRawWallpaperSize, crop);

        // Set min wallpaper zoom and max zoom for the full resolution image view
        mFullResImageView.setMaxScale(Math.max(DEFAULT_WALLPAPER_MAX_ZOOM, defaultWallpaperZoom));
        mFullResImageView.setMinScale(defaultWallpaperZoom);

        // Set center to composite positioning between scaled wallpaper and screen
        mFullResImageView.setScaleAndCenter(defaultWallpaperZoom, centerPosition);
    }

    private Rect calculateCropRect(Context context, boolean cropExtraWidth) {
        float wallpaperZoom = mFullResImageView.getScale();
        Context appContext = context.getApplicationContext();

        Rect visibleFileRect = new Rect();
        mFullResImageView.visibleFileRect(visibleFileRect);

        int cropWidth = mWallpaperSurface.getMeasuredWidth();
        int cropHeight = mWallpaperSurface.getMeasuredHeight();
        int maxCrop = Math.max(cropWidth, cropHeight);
        int minCrop = Math.min(cropWidth, cropHeight);
        Point hostViewSize = new Point(cropWidth, cropHeight);

        Resources res = appContext.getResources();
        Point cropSurfaceSize = WallpaperCropUtils.calculateCropSurfaceSize(res, maxCrop, minCrop,
                cropWidth, cropHeight);
        Rect result = WallpaperCropUtils.calculateCropRect(appContext, hostViewSize,
                cropSurfaceSize, mRawWallpaperSize, visibleFileRect, wallpaperZoom, cropExtraWidth);

        // Cancel the rescaling in the multi crop case. In that case the crop will be sent to
        // WallpaperManager. WallpaperManager expects a crop that is not yet rescaled to match
        // the screen size (as opposed to BitmapCropper which is used in the single crop case).
        // TODO(b/270726737, b/281648899) clean that comment and that part of the code
        if (mWallpaperManager.isMultiCropEnabled()) result.scale(1f / mFullResImageView.getScale());
        return result;
    }

    /**
     * surfaceCreated() is called right after Fragment.onResume() and surfaceDestroyed() is called
     * after Fragment.onPause(). We do not clean up the surface when surfaceDestroyed() and hold
     * it till the next onResume(). We do not need to decode the image again and thus can skip the
     * whole logic in surfaceCreated().
     */
    private class WallpaperSurfaceCallback implements SurfaceHolder.Callback {
        private Surface mLastSurface;
        private SurfaceControlViewHost mHost;

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Context context = getContext();
            Activity activity = getActivity();
            if (context == null || activity == null || mLastSurface == holder.getSurface()) {
                return;
            }

            mLastSurface = holder.getSurface();
            if (mFullResImageView != null) {
                mFullResImageView.recycle();
            }

            mProgressBar.setVisibility(View.VISIBLE);
            View wallpaperPreviewContainer = LayoutInflater.from(context).inflate(
                    R.layout.fullscreen_wallpaper_preview, null);
            mFullResImageView = wallpaperPreviewContainer.findViewById(R.id.full_res_image);
            mLowResImageView = wallpaperPreviewContainer.findViewById(R.id.low_res_image);
            mLowResImageView.setRenderEffect(
                    RenderEffect.createBlurEffect(LOW_RES_BITMAP_BLUR_RADIUS,
                            LOW_RES_BITMAP_BLUR_RADIUS, Shader.TileMode.CLAMP));
            // Calculate the size of mWallpaperSurface based on system zoom's scale and
            // on the larger screen size (if more than one) so that the wallpaper is
            // rendered in a larger surface than what preview shows, simulating the behavior of
            // the actual wallpaper surface and so we can crop it to a size that fits in all
            // screens.
            float scale = WallpaperCropUtils.getSystemWallpaperMaximumScale(context);
            int origWidth = mWallpaperSurface.getWidth();
            int origHeight = mWallpaperSurface.getHeight();

            int scaledOrigWidth = origWidth;
            int scaledOrigHeight = origHeight;

            if (mDisplayUtils.hasMultiInternalDisplays()) {
                final Point maxDisplaysDimen = mDisplayUtils.getMaxDisplaysDimension();
                scaledOrigWidth = Math.round(
                        origWidth * Math.max(1, (float) maxDisplaysDimen.x / mScreenSize.x));
                scaledOrigHeight = Math.round(
                        origHeight * Math.max(1, (float) maxDisplaysDimen.y / mScreenSize.y));
            }
            int width = (int) (scaledOrigWidth * scale);
            int height = (int) (scaledOrigHeight * scale);
            int left = (origWidth - width) / 2;
            int top = (origHeight - height) / 2;

            if (RtlUtils.isRtl(context)) {
                left *= -1;
            }

            LayoutParams params = mWallpaperSurface.getLayoutParams();
            params.width = width;
            params.height = height;
            mWallpaperSurface.setX(left);
            mWallpaperSurface.setY(top);
            mWallpaperSurface.setLayoutParams(params);
            mWallpaperSurface.requestLayout();

            // Load low res image first before the full res image is available
            int placeHolderColor = ResourceUtils.getColorAttr(activity,
                    android.R.attr.colorBackground);
            if (mColorFuture.isDone()) {
                try {
                    int colorValue = mColorFuture.get().getPlaceholderColor();
                    if (colorValue != Color.TRANSPARENT) {
                        placeHolderColor = colorValue;
                    }
                } catch (InterruptedException | ExecutionException e) {
                    // Do nothing intended
                }
            }
            mWallpaperAsset.loadLowResDrawable(activity, mLowResImageView, placeHolderColor,
                    mPreviewBitmapTransformation);

            wallpaperPreviewContainer.measure(
                    makeMeasureSpec(width, EXACTLY),
                    makeMeasureSpec(height, EXACTLY));
            wallpaperPreviewContainer.layout(0, 0, width, height);
            mTouchForwardingLayout.setTargetView(mFullResImageView);

            cleanUp();
            mHost = new SurfaceControlViewHost(context,
                    context.getDisplay(), mWallpaperSurface.getHostToken());
            mHost.setView(wallpaperPreviewContainer, wallpaperPreviewContainer.getWidth(),
                    wallpaperPreviewContainer.getHeight());
            mWallpaperSurface.setChildSurfacePackage(mHost.getSurfacePackage());

            mWallpaperAsset.decodeRawDimensions(getActivity(), dimensions -> {
                if (getActivity() == null) {
                    return;
                }

                if (dimensions == null) {
                    showLoadWallpaperErrorDialog();
                    return;
                }

                mRawWallpaperSize = dimensions;
                // We can enable set wallpaper now but defer to full res view ready
                initFullResView();
            });
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            // Do nothing intended
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // Do nothing intended
        }

        public void cleanUp() {
            if (mHost != null) {
                mHost.release();
                mHost = null;
            }
        }
    }
}
