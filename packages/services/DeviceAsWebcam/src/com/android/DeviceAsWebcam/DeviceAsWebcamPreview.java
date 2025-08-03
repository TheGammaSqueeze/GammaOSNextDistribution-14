/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.DeviceAsWebcam;

import static android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.DisplayCutout;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.FragmentActivity;
import androidx.window.layout.WindowMetrics;
import androidx.window.layout.WindowMetricsCalculator;

import com.android.DeviceAsWebcam.utils.UserPrefs;
import com.android.DeviceAsWebcam.view.CameraPickerDialog;
import com.android.DeviceAsWebcam.view.ZoomController;
import com.android.deviceaswebcam.flags.Flags;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class DeviceAsWebcamPreview extends FragmentActivity {
    private static final String TAG = DeviceAsWebcamPreview.class.getSimpleName();
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final int ROTATION_ANIMATION_DURATION_MS = 300;

    private final Executor mThreadExecutor = Executors.newFixedThreadPool(2);
    private final ConditionVariable mServiceReady = new ConditionVariable();

    private boolean mTextureViewSetup = false;
    private Size mPreviewSize;
    private DeviceAsWebcamFgService mLocalFgService;
    private AccessibilityManager mAccessibilityManager;
    private int mCurrRotation = Surface.ROTATION_0;
    private Size mCurrDisplaySize = new Size(0, 0);

    private View mRootView;
    private FrameLayout mTextureViewContainer;
    private CardView mTextureViewCard;
    private TextureView mTextureView;
    private View mFocusIndicator;
    private ZoomController mZoomController = null;
    private ImageButton mToggleCameraButton;
    private ImageButton mHighQualityToggleButton;
    private CameraPickerDialog mCameraPickerDialog;

    private UserPrefs mUserPrefs;

    // A listener to monitor the preview size change events. This might be invoked when toggling
    // camera or the webcam stream is started after the preview stream.
    Consumer<Size> mPreviewSizeChangeListener = size -> runOnUiThread(() -> {
                mPreviewSize = size;
                setTextureViewScale();
            }
    );

    // Listener for when Accessibility service are enabled or disabled.
    AccessibilityManager.AccessibilityServicesStateChangeListener mAccessibilityListener =
            accessibilityManager -> {
                List<AccessibilityServiceInfo> services =
                        accessibilityManager.getEnabledAccessibilityServiceList(FEEDBACK_ALL_MASK);
                boolean areServicesEnabled = !services.isEmpty();
                runOnUiThread(() ->
                        mZoomController.onAccessibilityServicesEnabled(areServicesEnabled));
            };


    /**
     * {@link View.OnLayoutChangeListener} to add to
     * {@link DeviceAsWebcamPreview#mTextureViewContainer} for when we need to know
     * when changes to the view are committed.
     * <p>
     * NOTE: This removes itself as a listener after one call to prevent spurious callbacks
     *       once the texture view has been resized.
     */
    View.OnLayoutChangeListener mTextureViewContainerLayoutListener =
            new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    // Remove self to prevent further calls to onLayoutChange.
                    view.removeOnLayoutChangeListener(this);
                    // Update the texture view to fit the new bounds.
                    runOnUiThread(() -> {
                        if (mPreviewSize != null) {
                            setTextureViewScale();
                        }
                    });
                }
            };

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture texture, int width,
                        int height) {
                    runOnUiThread(() -> {
                        if (VERBOSE) {
                            Log.v(TAG, "onSurfaceTextureAvailable " + width + " x " + height);
                        }
                        mServiceReady.block();

                        if (!mTextureViewSetup) {
                            setupTextureViewLayout();
                        }

                        if (mLocalFgService == null) {
                            return;
                        }
                        mLocalFgService.setOnDestroyedCallback(() -> onServiceDestroyed());

                        if (mPreviewSize == null) {
                            return;
                        }
                        mLocalFgService.setPreviewSurfaceTexture(texture, mPreviewSize,
                                mPreviewSizeChangeListener);
                        List<CameraId> availableCameraIds =
                                mLocalFgService.getAvailableCameraIds();
                        if (availableCameraIds != null && availableCameraIds.size() > 1) {
                            setupSwitchCameraSelector();
                            mToggleCameraButton.setVisibility(View.VISIBLE);
                            if (canToggleCamera()) {
                                mToggleCameraButton.setOnClickListener(v -> toggleCamera());
                            } else {
                                mToggleCameraButton.setOnClickListener(
                                        v -> mCameraPickerDialog.show(getSupportFragmentManager(),
                                                "CameraPickerDialog"));
                            }
                            mToggleCameraButton.setOnLongClickListener(v -> {
                                mCameraPickerDialog.show(getSupportFragmentManager(),
                                        "CameraPickerDialog");
                                return true;
                            });
                        } else {
                            mToggleCameraButton.setVisibility(View.GONE);
                        }
                        rotateUiByRotationDegrees(mLocalFgService.getCurrentRotation());
                        mLocalFgService.setRotationUpdateListener(
                                rotation -> {
                                    rotateUiByRotationDegrees(rotation);
                                });
                    });
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width,
                        int height) {
                    if (VERBOSE) {
                        Log.v(TAG, "onSurfaceTextureSizeChanged " + width + " x " + height);
                    }
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                    runOnUiThread(() -> {
                        if (mLocalFgService != null) {
                            mLocalFgService.removePreviewSurfaceTexture();
                        }
                    });
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture texture) {
                }
            };

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mLocalFgService = ((DeviceAsWebcamFgService.LocalBinder) service).getService();
            if (VERBOSE) {
                Log.v(TAG, "Got Fg service");
            }
            mServiceReady.open();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            // Serialize updating mLocalFgService on UI Thread as all consumers of mLocalFgService
            // run on the UI Thread.
            runOnUiThread(() -> {
                mLocalFgService = null;
                finish();
            });
        }
    };

    private MotionEventToZoomRatioConverter mMotionEventToZoomRatioConverter = null;
    private final MotionEventToZoomRatioConverter.ZoomRatioUpdatedListener mZoomRatioListener =
            new MotionEventToZoomRatioConverter.ZoomRatioUpdatedListener() {
                @Override
                public void onZoomRatioUpdated(float updatedZoomRatio) {
                    if (mLocalFgService == null) {
                        return;
                    }

                    mLocalFgService.setZoomRatio(updatedZoomRatio);
                    mZoomController.setZoomRatio(updatedZoomRatio,
                            ZoomController.ZOOM_UI_SEEK_BAR_MODE);
                }
            };

    private GestureDetector.SimpleOnGestureListener mTapToFocusListener =
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent motionEvent) {
                    return tapToFocus(motionEvent);
                }
            };

    private void setTextureViewScale() {
        FrameLayout.LayoutParams frameLayout = new FrameLayout.LayoutParams(mPreviewSize.getWidth(),
                mPreviewSize.getHeight(), Gravity.CENTER);
        mTextureView.setLayoutParams(frameLayout);

        int pWidth = mTextureViewContainer.getWidth();
        int pHeight = mTextureViewContainer.getHeight();
        float scaleYToUnstretched = (float) mPreviewSize.getWidth() / mPreviewSize.getHeight();
        float scaleXToUnstretched = (float) mPreviewSize.getHeight() / mPreviewSize.getWidth();
        float additionalScaleForX = (float) pWidth / mPreviewSize.getHeight();
        float additionalScaleForY = (float) pHeight / mPreviewSize.getWidth();

        // To fit the preview, either letterbox or pillar box.
        float additionalScaleChosen = Math.min(additionalScaleForX, additionalScaleForY);

        float texScaleX = scaleXToUnstretched * additionalScaleChosen;
        float texScaleY = scaleYToUnstretched * additionalScaleChosen;

        mTextureView.setScaleX(texScaleX);
        mTextureView.setScaleY(texScaleY);

        // Resize the card view to match TextureView's final size exactly. This is to clip
        // textureView corners.
        ViewGroup.LayoutParams cardLayoutParams = mTextureViewCard.getLayoutParams();
        // Reduce size by two pixels to remove any rounding errors from casting to int.
        cardLayoutParams.height = ((int) (mPreviewSize.getHeight() * texScaleY)) - 2;
        cardLayoutParams.width = ((int) (mPreviewSize.getWidth() * texScaleX)) - 2;
        mTextureViewCard.setLayoutParams(cardLayoutParams);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        runOnUiThread(this::setupMainLayout);
    }

    private void setupMainLayout() {
        int currRotation = getDisplay().getRotation();
        WindowMetrics windowMetrics =
                WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this);
        Size displaySize;
        int width = windowMetrics.getBounds().width();
        int height = windowMetrics.getBounds().height();
        if (currRotation == Surface.ROTATION_90 || currRotation == Surface.ROTATION_270) {
            // flip height and width because we want the height and width if the display
            // in its natural orientation
            displaySize = new Size(/*width=*/ height, /*height=*/ width);
        } else {
            displaySize = new Size(width, height);
        }

        if (mCurrRotation == currRotation && mCurrDisplaySize.equals(displaySize)) {
            // Exit early if we have already drawn the UI for this state.
            return;
        }

        mCurrDisplaySize = displaySize;
        mCurrRotation = currRotation;

        DisplayCutout displayCutout =
                getWindowManager().getCurrentWindowMetrics().getWindowInsets().getDisplayCutout();
        if (displayCutout == null) {
            displayCutout = DisplayCutout.NO_CUTOUT;
        }

        // We set up the UI to always be fixed to the device's natural orientation.
        // If the device is rotated, we counter-rotate the UI to ensure that
        // our UI has a "locked" orientation.

        // resize the root view to match the display. Full screen preview covers the entire
        // screen
        ViewGroup.LayoutParams rootParams = mRootView.getLayoutParams();
        rootParams.width = mCurrDisplaySize.getWidth();
        rootParams.height = mCurrDisplaySize.getHeight();
        mRootView.setLayoutParams(rootParams);

        // Counter-rotate the view to undo device's rotation
        // Also add margin to the preview to ensure it doesn't clip any cutouts.
        int topMargin = (int) getResources().getDimension(R.dimen.preview_margin_top_min);
        switch (mCurrRotation) {
            case Surface.ROTATION_90:
                mRootView.setRotation(-90);
                topMargin = Math.max(topMargin, displayCutout.getSafeInsetLeft());
                break;
            case Surface.ROTATION_270:
                mRootView.setRotation(90);
                topMargin = Math.max(topMargin, displayCutout.getSafeInsetRight());
                break;
            case Surface.ROTATION_0:
                mRootView.setRotation(0);
                topMargin = Math.max(topMargin, displayCutout.getSafeInsetTop());
                break;
            case Surface.ROTATION_180:
                mRootView.setRotation(180);
                topMargin = Math.max(topMargin, displayCutout.getSafeInsetBottom());
                break;
        }

        ViewGroup.MarginLayoutParams layoutParams =
                (ViewGroup.MarginLayoutParams) mTextureViewContainer.getLayoutParams();
        layoutParams.topMargin = topMargin;
        mTextureViewContainer.setLayoutParams(layoutParams);

        // subscribe to layout changes of the texture view container so we can
        // resize the texture view once the container has been drawn with the new
        // margins
        mTextureViewContainer.addOnLayoutChangeListener(mTextureViewContainerLayoutListener);
    }


    @SuppressLint("ClickableViewAccessibility")
    private void setupZoomUiControl() {
        if (mLocalFgService == null || mLocalFgService.getCameraInfo() == null) {
            return;
        }

        Range<Float> zoomRatioRange = mLocalFgService.getCameraInfo().getZoomRatioRange();

        if (zoomRatioRange == null) {
            return;
        }

        // Retrieves current zoom ratio setting from CameraController so that the zoom ratio set by
        // the previous closed activity can be correctly restored
        float currentZoomRatio = mLocalFgService.getZoomRatio();

        mMotionEventToZoomRatioConverter = new MotionEventToZoomRatioConverter(
                getApplicationContext(), zoomRatioRange, currentZoomRatio,
                mZoomRatioListener);

        GestureDetector tapToFocusGestureDetector = new GestureDetector(getApplicationContext(),
                mTapToFocusListener);

        // Restores the focus indicator if tap-to-focus points exist
        float[] tapToFocusPoints = mLocalFgService.getTapToFocusPoints();
        if (tapToFocusPoints != null) {
            showFocusIndicator(tapToFocusPoints);
        }

        mTextureView.setOnTouchListener(
                (view, event) -> {
                    mMotionEventToZoomRatioConverter.onTouchEvent(event);
                    tapToFocusGestureDetector.onTouchEvent(event);
                    return true;
                });

        mZoomController.init(getLayoutInflater(), zoomRatioRange);
        mZoomController.setZoomRatio(currentZoomRatio, ZoomController.ZOOM_UI_TOGGLE_MODE);
        mZoomController.setOnZoomRatioUpdatedListener(
                value -> {
                    if (mLocalFgService != null) {
                        mLocalFgService.setZoomRatio(value);
                    }
                    mMotionEventToZoomRatioConverter.setZoomRatio(value);
                });
        if (mAccessibilityManager != null) {
            mAccessibilityListener.onAccessibilityServicesStateChanged(mAccessibilityManager);
        }
    }

    private void setupZoomRatioSeekBar() {
        if (mLocalFgService == null) {
            return;
        }

        mZoomController.setSupportedZoomRatioRange(
                mLocalFgService.getCameraInfo().getZoomRatioRange());
    }

    private void setupSwitchCameraSelector() {
        if (mLocalFgService == null || mLocalFgService.getCameraInfo() == null) {
            return;
        }
        setToggleCameraContentDescription();
        mCameraPickerDialog.updateAvailableCameras(createCameraListForPicker(),
                mLocalFgService.getCameraInfo().getCameraId());

        updateHighQualityButtonState(mLocalFgService.isHighQualityModeEnabled());
        mHighQualityToggleButton.setOnClickListener(v -> {
            // Disable the toggle button to prevent spamming
            mHighQualityToggleButton.setEnabled(false);
            toggleHQWithWarningIfNeeded();
        });
    }

    private void toggleHQWithWarningIfNeeded() {
        boolean targetHqMode = !mLocalFgService.isHighQualityModeEnabled();
        boolean warningEnabled = mUserPrefs.fetchHighQualityWarningEnabled(
                /*defaultValue=*/ true);

        // No need to show the dialog if HQ mode is being turned off, or if the user has
        // explicitly clicked "Don't show again" before.
        if (!targetHqMode || !warningEnabled) {
            setHighQualityMode(targetHqMode);
            return;
        }

        AlertDialog alertDialog = new AlertDialog.Builder(/*context=*/ this)
                .setCancelable(false)
                .create();

        View customView = alertDialog.getLayoutInflater().inflate(
                R.layout.hq_dialog_warning, /*root=*/ null);
        alertDialog.setView(customView);
        CheckBox dontShow = customView.findViewById(R.id.hq_warning_dont_show_again_checkbox);
        dontShow.setOnCheckedChangeListener(
                (buttonView, isChecked) -> mUserPrefs.storeHighQualityWarningEnabled(!isChecked));

        Button ackButton = customView.findViewById(R.id.hq_warning_ack_button);
        ackButton.setOnClickListener(v -> {
            setHighQualityMode(true);
            alertDialog.dismiss();
        });

        alertDialog.show();
    }

    private void setHighQualityMode(boolean enabled) {
        Runnable callback = () -> {
            // Immediately delegate callback to UI thread to prevent blocking the thread that
            // callback was called from.
            runOnUiThread(() -> {
                setupSwitchCameraSelector();
                setupZoomUiControl();
                rotateUiByRotationDegrees(mLocalFgService.getCurrentRotation(),
                        /*animationDuration*/ 0L);
                mHighQualityToggleButton.setEnabled(true);
            });
        };
        mLocalFgService.setHighQualityModeEnabled(enabled, callback);
    }

    private void updateHighQualityButtonState(boolean highQualityModeEnabled) {
        int img = highQualityModeEnabled ?
                R.drawable.ic_high_quality_on : R.drawable.ic_high_quality_off;
        mHighQualityToggleButton.setImageResource(img);

        // NOTE: This is "flipped" because if High Quality mode is enabled, we want the content
        // description to say that it will be disabled when the button is pressed.
        int contentDesc = highQualityModeEnabled ?
                R.string.toggle_high_quality_description_off :
                R.string.toggle_high_quality_description_on;
        mHighQualityToggleButton.setContentDescription(getText(contentDesc));
    }

    private void rotateUiByRotationDegrees(int rotation) {
        rotateUiByRotationDegrees(rotation, /*animate*/ ROTATION_ANIMATION_DURATION_MS);
    }

    private void rotateUiByRotationDegrees(int rotation, long animationDuration) {
        if (mLocalFgService == null) {
            // Don't do anything if no foreground service is connected
            return;
        }
        int finalRotation = calculateUiRotation(rotation);
        runOnUiThread(() -> {
            ObjectAnimator anim = ObjectAnimator.ofFloat(mToggleCameraButton,
                            /*propertyName=*/"rotation", finalRotation)
                    .setDuration(animationDuration);
            anim.setInterpolator(new AccelerateDecelerateInterpolator());
            anim.start();
            mToggleCameraButton.performHapticFeedback(
                    HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE);

            mZoomController.setTextDisplayRotation(finalRotation, (int) animationDuration);
            mHighQualityToggleButton.animate()
                    .rotation(finalRotation).setDuration(animationDuration);
        });
    }

    private int calculateUiRotation(int rotation) {
        // Rotates the UI control container according to the device sensor rotation degrees and the
        // camera sensor orientation.
        int sensorOrientation = mLocalFgService.getCameraInfo().getSensorOrientation();
        if (mLocalFgService.getCameraInfo().getLensFacing()
                == CameraCharacteristics.LENS_FACING_BACK) {
            rotation = (rotation + sensorOrientation) % 360;
        } else {
            rotation = (360 + rotation - sensorOrientation) % 360;
        }

        // Rotation angle of the view must be [-179, 180] to ensure we always rotate the
        // view through the natural orientation (0)
        return rotation <= 180 ? rotation : rotation - 360;
    }

    private void setupTextureViewLayout() {
        mPreviewSize = mLocalFgService.getSuitablePreviewSize();
        if (mPreviewSize != null) {
            setTextureViewScale();
            setupZoomUiControl();
        }
    }

    private void onServiceDestroyed() {
        ConditionVariable cv = new ConditionVariable();
        cv.close();
        runOnUiThread(() -> {
            try {
                mLocalFgService = null;
                finish();
            } finally {
                cv.open();
            }
        });
        cv.block();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preview_layout);
        mRootView = findViewById(R.id.container_view);
        mTextureViewContainer = findViewById(R.id.texture_view_container);
        mTextureViewCard = findViewById(R.id.texture_view_card);
        mTextureView = findViewById(R.id.texture_view);
        mFocusIndicator = findViewById(R.id.focus_indicator);
        mFocusIndicator.setBackground(createFocusIndicatorDrawable());
        mToggleCameraButton = findViewById(R.id.toggle_camera_button);
        mZoomController = findViewById(R.id.zoom_ui_controller);
        mHighQualityToggleButton = findViewById(R.id.high_quality_button);

        // Use "seamless" animation for rotations as we fix the UI relative to the device.
        // "seamless" will make the transition invisible to the users.
        WindowManager.LayoutParams windowAttrs = getWindow().getAttributes();
        windowAttrs.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS;
        getWindow().setAttributes(windowAttrs);

        mAccessibilityManager = getSystemService(AccessibilityManager.class);
        if (mAccessibilityManager != null) {
            mAccessibilityManager.addAccessibilityServicesStateChangeListener(
                    mAccessibilityListener);
        }

        mUserPrefs = new UserPrefs(this.getApplicationContext());
        mCameraPickerDialog = new CameraPickerDialog(this::switchCamera);

        setupMainLayout();

        // Needed because onConfigChanged is not called when device rotates from landscape to
        // reverse-landscape or from portrait to reverse-portrait.
        mRootView.setOnApplyWindowInsetsListener((view, inset) -> {
            runOnUiThread(this::setupMainLayout);
            return WindowInsets.CONSUMED;
        });

        if (!Flags.highQualityToggle()) {
            mHighQualityToggleButton.setVisibility(View.GONE);
        }

        bindService(new Intent(this, DeviceAsWebcamFgService.class), 0, mThreadExecutor,
                mConnection);
    }

    private Drawable createFocusIndicatorDrawable() {
        int indicatorSize = getResources().getDimensionPixelSize(R.dimen.focus_indicator_size);
        Bitmap bitmap = Bitmap.createBitmap(indicatorSize, indicatorSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        OvalShape ovalShape = new OvalShape();
        ShapeDrawable shapeDrawable = new ShapeDrawable(ovalShape);
        Paint paint = shapeDrawable.getPaint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);

        int strokeWidth = getResources().getDimensionPixelSize(
                R.dimen.focus_indicator_stroke_width);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(getResources().getColor(android.R.color.white, null));
        int halfIndicatorSize = indicatorSize / 2;
        canvas.drawCircle(halfIndicatorSize, halfIndicatorSize, halfIndicatorSize - strokeWidth,
                paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(getResources().getColor(R.color.focus_indicator_background_color, null));
        canvas.drawCircle(halfIndicatorSize, halfIndicatorSize, halfIndicatorSize - strokeWidth,
                paint);

        return new BitmapDrawable(getResources(), bitmap);
    }

    private void hideSystemUiAndActionBar() {
        // Hides status bar
        Window window = getWindow();
        window.setStatusBarColor(android.R.color.system_neutral1_800);
        window.setDecorFitsSystemWindows(false);
        WindowInsetsController controller = window.getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        hideSystemUiAndActionBar();
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            mServiceReady.block();
            if (!mTextureViewSetup) {
                setupTextureViewLayout();
                mTextureViewSetup = true;
            }
            if (mLocalFgService != null && mPreviewSize != null) {
                mLocalFgService.setPreviewSurfaceTexture(mTextureView.getSurfaceTexture(),
                        mPreviewSize, mPreviewSizeChangeListener);
                rotateUiByRotationDegrees(mLocalFgService.getCurrentRotation());
                mLocalFgService.setRotationUpdateListener(rotation ->
                        runOnUiThread(() -> rotateUiByRotationDegrees(rotation)));
                mZoomController.setZoomRatio(mLocalFgService.getZoomRatio(),
                        ZoomController.ZOOM_UI_TOGGLE_MODE);
            }
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        if (mLocalFgService != null) {
            mLocalFgService.removePreviewSurfaceTexture();
            mLocalFgService.setRotationUpdateListener(null);
        }
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mLocalFgService == null || (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
                && keyCode != KeyEvent.KEYCODE_VOLUME_UP)) {
            return super.onKeyDown(keyCode, event);
        }

        float zoomRatio = mLocalFgService.getZoomRatio();

        // Uses volume key events to adjust zoom ratio
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)){
            zoomRatio -= 0.1f;
        } else {
            zoomRatio += 0.1f;
        }

        // Clamps the zoom ratio in the supported range
        Range<Float> zoomRatioRange = mLocalFgService.getCameraInfo().getZoomRatioRange();
        zoomRatio = Math.min(Math.max(zoomRatio, zoomRatioRange.getLower()),
                zoomRatioRange.getUpper());

        // Updates the new value to all related controls
        mLocalFgService.setZoomRatio(zoomRatio);
        mZoomController.setZoomRatio(zoomRatio, ZoomController.ZOOM_UI_SEEK_BAR_MODE);
        mMotionEventToZoomRatioConverter.setZoomRatio(zoomRatio);

        return true;
    }

    @Override
    public void onDestroy() {
        if (mAccessibilityManager != null) {
            mAccessibilityManager.removeAccessibilityServicesStateChangeListener(
                    mAccessibilityListener);
        }
        if (mLocalFgService != null) {
            mLocalFgService.setOnDestroyedCallback(null);
        }
        unbindService(mConnection);
        super.onDestroy();
    }

    /**
     * Returns {@code true} when the device has both available back and front cameras. Otherwise,
     * returns {@code false}.
     */
    private boolean canToggleCamera() {
        if (mLocalFgService == null) {
            return false;
        }

        List<CameraId> availableCameraIds = mLocalFgService.getAvailableCameraIds();
        boolean hasBackCamera = false;
        boolean hasFrontCamera = false;

        for (CameraId cameraId : availableCameraIds) {
            CameraInfo cameraInfo = mLocalFgService.getOrCreateCameraInfo(cameraId);
            if (cameraInfo.getLensFacing() == CameraCharacteristics.LENS_FACING_BACK) {
                hasBackCamera = true;
            } else if (cameraInfo.getLensFacing() == CameraCharacteristics.LENS_FACING_FRONT) {
                hasFrontCamera = true;
            }
        }

        return hasBackCamera && hasFrontCamera;
    }

    private void setToggleCameraContentDescription() {
        if (mLocalFgService == null) {
            return;
        }
        int lensFacing = mLocalFgService.getCameraInfo().getLensFacing();
        CharSequence descr = getText(R.string.toggle_camera_button_description_front);
        if (lensFacing == CameraMetadata.LENS_FACING_FRONT) {
            descr = getText(R.string.toggle_camera_button_description_back);
        }
        mToggleCameraButton.setContentDescription(descr);
    }

    private void toggleCamera() {
        if (mLocalFgService == null) {
            return;
        }

        mLocalFgService.toggleCamera();
        setToggleCameraContentDescription();
        mFocusIndicator.setVisibility(View.GONE);
        mMotionEventToZoomRatioConverter.reset(mLocalFgService.getZoomRatio(),
                mLocalFgService.getCameraInfo().getZoomRatioRange());
        setupZoomRatioSeekBar();
        mZoomController.setZoomRatio(mLocalFgService.getZoomRatio(),
                ZoomController.ZOOM_UI_TOGGLE_MODE);
        mCameraPickerDialog.updateSelectedCamera(mLocalFgService.getCameraInfo().getCameraId());
    }

    private void switchCamera(CameraId cameraId) {
        if (mLocalFgService == null) {
            return;
        }

        mLocalFgService.switchCamera(cameraId);
        setToggleCameraContentDescription();
        mMotionEventToZoomRatioConverter.reset(mLocalFgService.getZoomRatio(),
                mLocalFgService.getCameraInfo().getZoomRatioRange());
        setupZoomRatioSeekBar();
        mZoomController.setZoomRatio(mLocalFgService.getZoomRatio(),
                ZoomController.ZOOM_UI_TOGGLE_MODE);
        // CameraPickerDialog does not update its UI until the preview activity
        // notifies it of the change. So notify CameraPickerDialog about the camera change.
        mCameraPickerDialog.updateSelectedCamera(cameraId);
    }

    private boolean tapToFocus(MotionEvent motionEvent) {
        if (mLocalFgService == null || mLocalFgService.getCameraInfo() == null) {
            return false;
        }

        float[] normalizedPoint = calculateNormalizedPoint(motionEvent);

        if (isTapToResetAutoFocus(normalizedPoint)) {
            mFocusIndicator.setVisibility(View.GONE);
            mLocalFgService.resetToAutoFocus();
        } else {
            showFocusIndicator(normalizedPoint);
            mLocalFgService.tapToFocus(normalizedPoint);
        }

        return true;
    }

    /**
     * Returns whether the new points overlap with the original tap-to-focus points or not.
     */
    private boolean isTapToResetAutoFocus(float[] newNormalizedPoints) {
        float[] oldNormalizedPoints = mLocalFgService.getTapToFocusPoints();

        if (oldNormalizedPoints == null) {
            return false;
        }

        // Calculates the distance between the new and old points
        float distanceX = Math.abs(newNormalizedPoints[1] - oldNormalizedPoints[1])
                * mTextureViewCard.getWidth();
        float distanceY = Math.abs(newNormalizedPoints[0] - oldNormalizedPoints[0])
                * mTextureViewCard.getHeight();
        double distance = Math.sqrt(distanceX*distanceX + distanceY*distanceY);

        int indicatorRadius = getResources().getDimensionPixelSize(R.dimen.focus_indicator_size)
                / 2;

        // Checks whether the distance is less than the circle radius of focus indicator
        return indicatorRadius >= distance;
    }

    /**
     * Calculates the normalized point which will be the point between [0, 0] to [1, 1] mapping to
     * the preview size.
     */
    private float[] calculateNormalizedPoint(MotionEvent motionEvent) {
        return new float[]{motionEvent.getX() / mPreviewSize.getWidth(),
                motionEvent.getY() / mPreviewSize.getHeight()};
    }

    /**
     * Show the focus indicator and hide it automatically after a proper duration.
     */
    private void showFocusIndicator(float[] normalizedPoint) {
        int indicatorSize = getResources().getDimensionPixelSize(R.dimen.focus_indicator_size);
        float translationX =
                normalizedPoint[0] * mTextureViewCard.getWidth() - indicatorSize / 2f;
        float translationY = normalizedPoint[1] * mTextureViewCard.getHeight()
                - indicatorSize / 2f;
        mFocusIndicator.setTranslationX(translationX);
        mFocusIndicator.setTranslationY(translationY);
        mFocusIndicator.setVisibility(View.VISIBLE);
    }

    private List<CameraPickerDialog.ListItem> createCameraListForPicker() {
        List<CameraId> availableCameraIds = mLocalFgService.getAvailableCameraIds();
        if (availableCameraIds == null) {
            Log.w(TAG, "No cameras listed for picker. Why is Webcam Service running?");
            return List.of();
        }

        return availableCameraIds.stream()
                .map(mLocalFgService::getOrCreateCameraInfo)
                .filter(Objects::nonNull)
                .map(CameraPickerDialog.ListItem::new)
                .toList();
    }
}
