package com.gammaos.screenmapper;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.SystemProperties;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The main editor overlay. Contains a toolbar at the top and a canvas area
 * where MappingPointViews can be placed and dragged.
 */
public class ScreenMapEditorView extends FrameLayout {

    private final String mPackageName;
    private final WindowManager mWindowManager;
    private final List<MappingPointView> mPointViews = new ArrayList<>();
    private FrameLayout mCanvas;
    private LinearLayout mToolbar;
    private ButtonSelectorView mActiveSelector;
    private boolean mEditorMode = true;
    private Runnable mOnCloseListener;
    private Runnable mOnSaveListener;

    // Responsive sizes computed from screen dimensions
    private int mScreenW, mScreenH;
    private float mDensity;
    private int mButtonRadiusPx;
    private int mStickRadiusPx;

    public ScreenMapEditorView(Context context, String packageName) {
        super(context);
        mPackageName = packageName;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        computeResponsiveSizes(context);
        buildUI(context);
        loadExistingConfig();
    }

    private void computeResponsiveSizes(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        mScreenW = dm.widthPixels;
        mScreenH = dm.heightPixels;
        mDensity = dm.density;

        // Scale mapping point sizes relative to the shorter screen dimension.
        // Base: 35dp button, 70dp stick on a 960px-short-edge screen.
        // This keeps circles proportionally sized on any screen.
        int shortEdge = Math.min(mScreenW, mScreenH);
        float scale = shortEdge / 960f;
        mButtonRadiusPx = Math.max((int) (35 * mDensity * scale), (int) (20 * mDensity));
        mStickRadiusPx = Math.max((int) (70 * mDensity * scale), (int) (40 * mDensity));
    }

    public int getButtonRadiusPx() { return mButtonRadiusPx; }
    public int getStickRadiusPx() { return mStickRadiusPx; }

    private void buildUI(Context context) {
        // Canvas for mapping points (full screen behind toolbar)
        mCanvas = new FrameLayout(context);
        mCanvas.setLayoutParams(new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(mCanvas);

        // Responsive toolbar sizing — 4x larger buttons
        int shortEdge = Math.min(mScreenW, mScreenH);
        float toolbarTextSp = Math.max(16f, Math.min(22f, shortEdge / 40f));
        int toolbarPad = Math.max((int) (8 * mDensity), shortEdge / 60);
        int btnHPad = Math.max((int) (20 * mDensity), shortEdge / 30);
        int btnVPad = Math.max((int) (10 * mDensity), shortEdge / 60);
        int btnMargin = Math.max((int) (6 * mDensity), shortEdge / 100);

        // Outer toolbar container: Close(X) pinned right, scrollable content left
        mToolbar = new LinearLayout(context);
        mToolbar.setOrientation(LinearLayout.HORIZONTAL);
        mToolbar.setGravity(Gravity.CENTER_VERTICAL);
        mToolbar.setBackgroundColor(Color.argb(200, 30, 30, 30));
        mToolbar.setPadding(toolbarPad, toolbarPad, toolbarPad, toolbarPad);

        FrameLayout.LayoutParams toolbarLp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        toolbarLp.gravity = Gravity.TOP;
        mToolbar.setLayoutParams(toolbarLp);

        // Scrollable section for the main toolbar items
        HorizontalScrollView scrollView = new HorizontalScrollView(context);
        scrollView.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        scrollView.setLayoutParams(scrollLp);

        LinearLayout scrollContent = new LinearLayout(context);
        scrollContent.setOrientation(LinearLayout.HORIZONTAL);
        scrollContent.setGravity(Gravity.CENTER_VERTICAL);

        // Add Mapping Point button
        Button addBtn = makeToolbarButton(context, "+ Point", toolbarTextSp, btnHPad, btnVPad, btnMargin);
        addBtn.setOnClickListener(v -> addMappingPoint());
        scrollContent.addView(addBtn);

        // Clear All button
        Button clearBtn = makeToolbarButton(context, "Clear", toolbarTextSp, btnHPad, btnVPad, btnMargin);
        clearBtn.setOnClickListener(v -> clearAllPoints());
        scrollContent.addView(clearBtn);

        scrollView.addView(scrollContent);
        mToolbar.addView(scrollView);

        // Opacity slider — fills remaining space, tall touch target like QS brightness bar
        SeekBar seekBar = new SeekBar(context);
        int sliderHeight = Math.max((int) (36 * mDensity), shortEdge / 20);
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(
                0, sliderHeight, 1f);
        seekLp.setMargins(btnMargin * 2, 0, btnMargin * 2, 0);
        seekBar.setLayoutParams(seekLp);
        seekBar.setMax(100);
        seekBar.setProgress(100);
        seekBar.setPadding(0, 0, 0, 0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                float alpha = progress / 100f;
                for (MappingPointView pv : mPointViews) {
                    pv.setOverlayAlpha(alpha);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        mToolbar.addView(seekBar);

        // Save button (pinned right, always visible)
        Button saveBtn = makeToolbarButton(context, "Save", toolbarTextSp, btnHPad, btnVPad, btnMargin);
        saveBtn.setBackgroundColor(Color.argb(200, 40, 100, 40));
        saveBtn.setOnClickListener(v -> saveConfig());
        mToolbar.addView(saveBtn);

        // Close button (pinned right, always visible)
        Button closeBtn = makeToolbarButton(context, "X", toolbarTextSp, btnHPad, btnVPad, btnMargin);
        closeBtn.setBackgroundColor(Color.argb(200, 120, 40, 40));
        closeBtn.setOnClickListener(v -> {
            if (mOnCloseListener != null) mOnCloseListener.run();
        });
        mToolbar.addView(closeBtn);

        addView(mToolbar);
    }

    private Button makeToolbarButton(Context context, String text,
            float textSizeSp, int hPad, int vPad, int margin) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setTextSize(textSizeSp);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.argb(150, 60, 60, 60));
        btn.setPadding(hPad, vPad, hPad, vPad);
        btn.setMinWidth(0);
        btn.setMinHeight(0);
        btn.setMinimumWidth(0);
        btn.setMinimumHeight(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.setMargins(margin, 0, margin, 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    public void setOnCloseListener(Runnable listener) { mOnCloseListener = listener; }
    public void setOnSaveListener(Runnable listener) { mOnSaveListener = listener; }

    public void setEditorMode(boolean editor) {
        mEditorMode = editor;
        mToolbar.setVisibility(editor ? VISIBLE : GONE);
        for (MappingPointView pv : mPointViews) {
            pv.setEditable(editor);
        }
    }

    private void loadExistingConfig() {
        List<ScreenMapConfig.MappingPoint> points = ScreenMapConfig.load(mPackageName);
        for (ScreenMapConfig.MappingPoint p : points) {
            // Apply responsive radius if the saved radius doesn't match current screen
            applyResponsiveRadius(p);
            addPointView(p);
        }
    }

    private void applyResponsiveRadius(ScreenMapConfig.MappingPoint p) {
        boolean isLarge = (p.type == ScreenMapConfig.MappingPoint.Type.STICK_LEFT
                || p.type == ScreenMapConfig.MappingPoint.Type.STICK_RIGHT
                || p.type == ScreenMapConfig.MappingPoint.Type.DPAD);
        p.radius = isLarge ? mStickRadiusPx : mButtonRadiusPx;
    }

    private void addMappingPoint() {
        deselectAllPoints();
        // Find empty position that doesn't overlap existing points
        int cx, cy;
        int[] pos = findEmptyPosition(mButtonRadiusPx);
        cx = pos[0];
        cy = pos[1];

        ScreenMapConfig.MappingPoint point = new ScreenMapConfig.MappingPoint(
                ScreenMapConfig.MappingPoint.Type.BUTTON, cx, cy,
                mButtonRadiusPx, 0x130 /* BTN_A default */);

        MappingPointView pv = addPointView(point);
        showButtonSelector(pv);
    }

    private int[] findEmptyPosition(int radius) {
        // Try grid positions, starting from center, spiraling out
        int startX = mScreenW / 2;
        int startY = mScreenH / 2;
        int step = radius * 3;

        for (int ring = 0; ring < 10; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dy = -ring; dy <= ring; dy++) {
                    if (Math.abs(dx) != ring && Math.abs(dy) != ring) continue; // only ring edges
                    int cx = startX + dx * step;
                    int cy = startY + dy * step;
                    if (cx < radius || cx > mScreenW - radius) continue;
                    if (cy < radius || cy > mScreenH - radius) continue;
                    if (!overlapsExisting(cx, cy, radius)) {
                        return new int[]{cx, cy};
                    }
                }
            }
        }
        // Fallback: center
        return new int[]{startX, startY};
    }

    private boolean overlapsExisting(int cx, int cy, int radius) {
        for (MappingPointView pv : mPointViews) {
            ScreenMapConfig.MappingPoint p = pv.getPoint();
            float dx = cx - p.x;
            float dy = cy - p.y;
            float minDist = radius + p.radius;
            if (dx * dx + dy * dy < minDist * minDist) return true;
        }
        return false;
    }

    private void deselectAllPoints() {
        for (MappingPointView pv : mPointViews) {
            pv.deselect();
        }
    }

    private MappingPointView addPointView(ScreenMapConfig.MappingPoint point) {
        MappingPointView pv = new MappingPointView(getContext(), point);
        pv.setEditable(mEditorMode);
        pv.setOnPointClickedListener(view -> {
            deselectAllPoints();
            showButtonSelector(view);
        });
        pv.setOnPointRemovedListener(view -> removePointView(view));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                point.radius * 2, point.radius * 2);
        mCanvas.addView(pv, lp);
        pv.positionAt(point.x, point.y);

        mPointViews.add(pv);
        return pv;
    }

    private void removePointView(MappingPointView pv) {
        mCanvas.removeView(pv);
        mPointViews.remove(pv);
    }

    private void clearAllPoints() {
        for (MappingPointView pv : new ArrayList<>(mPointViews)) {
            mCanvas.removeView(pv);
        }
        mPointViews.clear();
    }

    private void showButtonSelector(MappingPointView targetView) {
        dismissSelector();

        ScreenMapConfig.MappingPoint point = targetView.getPoint();

        // Clamp selector center so it stays fully on screen
        int selectorRadius = ButtonSelectorView.computeRadius(getContext(), mScreenW, mScreenH);
        int cx = Math.max(selectorRadius, Math.min(mScreenW - selectorRadius, point.x));
        int cy = Math.max(selectorRadius, Math.min(mScreenH - selectorRadius, point.y));

        mActiveSelector = new ButtonSelectorView(getContext(), cx, cy, mScreenW, mScreenH);
        mActiveSelector.setOnButtonSelectedListener(
                new ButtonSelectorView.OnButtonSelectedListener() {
            @Override
            public void onButtonSelected(ScreenMapConfig.MappingPoint.Type type,
                                        int buttonCode, String label) {
                point.type = type;
                point.buttonCode = buttonCode;
                applyResponsiveRadius(point);

                int savedX = point.x;
                int savedY = point.y;
                removePointView(targetView);
                point.x = savedX;
                point.y = savedY;
                addPointView(point);

                dismissSelector();
            }

            @Override
            public void onCancelled() {
                dismissSelector();
            }
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        mCanvas.addView(mActiveSelector, lp);
    }

    private void dismissSelector() {
        if (mActiveSelector != null) {
            mCanvas.removeView(mActiveSelector);
            mActiveSelector = null;
        }
    }

    private void saveConfig() {
        dismissSelector();

        List<ScreenMapConfig.MappingPoint> points = new ArrayList<>();
        for (MappingPointView pv : mPointViews) {
            points.add(pv.getPoint());
        }

        if (points.isEmpty()) {
            ScreenMapConfig.delete(mPackageName);
            Toast.makeText(getContext(), "Mapping cleared", Toast.LENGTH_SHORT).show();
        } else {
            ScreenMapConfig.save(mPackageName, points);
            SystemProperties.set("sys.gammaos.screenmap.active", "1");
            Toast.makeText(getContext(), "Mapping saved", Toast.LENGTH_SHORT).show();
        }

        if (mOnSaveListener != null) mOnSaveListener.run();
    }

    public List<ScreenMapConfig.MappingPoint> getPoints() {
        List<ScreenMapConfig.MappingPoint> points = new ArrayList<>();
        for (MappingPointView pv : mPointViews) {
            points.add(pv.getPoint());
        }
        return points;
    }
}
