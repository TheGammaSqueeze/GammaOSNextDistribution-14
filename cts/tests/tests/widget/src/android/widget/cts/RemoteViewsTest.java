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

package android.widget.cts;

import static android.appwidget.flags.Flags.drawDataParcel;
import static android.graphics.Bitmap.Config.ARGB_8888;
import static android.util.TypedValue.COMPLEX_UNIT_DIP;
import static android.util.TypedValue.COMPLEX_UNIT_PX;
import static android.view.View.MeasureSpec.makeMeasureSpec;
import static android.view.inputmethod.Flags.FLAG_HOME_SCREEN_HANDWRITING_DELEGATOR;
import static android.widget.RemoteViews.MARGIN_BOTTOM;
import static android.widget.RemoteViews.MARGIN_END;
import static android.widget.RemoteViews.MARGIN_LEFT;
import static android.widget.RemoteViews.MARGIN_RIGHT;
import static android.widget.RemoteViews.MARGIN_START;
import static android.widget.RemoteViews.MARGIN_TOP;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.TestUtils.waitUntil;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.util.SizeF;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsoluteLayout;
import android.widget.AnalogClock;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.RelativeLayout;
import android.widget.RemoteViews;
import android.widget.RemoteViews.ActionException;
import android.widget.SeekBar;
import android.widget.StackView;
import android.widget.Switch;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.ViewFlipper;
import android.widget.cts.util.TestUtils;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ThrowingRunnable;
import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Test {@link RemoteViews}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class RemoteViewsTest {
    private static final String PACKAGE_NAME = "android.widget.cts";

    private static final int INVALID_ID = -1;

    private static final long TEST_TIMEOUT = 5000;

    @Rule
    public ActivityTestRule<RemoteViewsCtsActivity> mActivityRule =
            new ActivityTestRule<>(RemoteViewsCtsActivity.class);

    @Rule
    public ExpectedException mExpectedException = ExpectedException.none();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private Instrumentation mInstrumentation;

    private Context mContext;

    private RemoteViews mRemoteViews;

    private View mResult;

    private String mInitialNightMode;

    @Before
    public void setUp() throws Throwable {
        // Ensure the UI is currently NOT in night mode.
        mInitialNightMode = changeNightMode(false);

        mActivityRule.runOnUiThread(() -> {
            mInstrumentation = InstrumentationRegistry.getInstrumentation();
            mContext = mInstrumentation.getTargetContext();
            mRemoteViews = new RemoteViews(PACKAGE_NAME, R.layout.remoteviews_good);
            mResult = mRemoteViews.apply(mContext, null);

            // Add our host view to the activity behind this test. This is similar to how launchers
            // add widgets to the on-screen UI.
            ViewGroup root = (ViewGroup) mActivityRule.getActivity().findViewById(
                    R.id.remoteView_host);
            FrameLayout.MarginLayoutParams lp = new FrameLayout.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            mResult.setLayoutParams(lp);

            root.addView(mResult);
        });
    }

    @After
    public void tearDown() {
        runShellCommand("cmd uimode night " + mInitialNightMode);
    }

    @Test
    public void testConstructor() {
        new RemoteViews(PACKAGE_NAME, R.layout.remoteviews_good);

        new RemoteViews(Parcel.obtain());
    }

    @Test
    public void testGetPackage() {
        assertEquals(PACKAGE_NAME, mRemoteViews.getPackage());

        mRemoteViews = new RemoteViews(null, R.layout.remoteviews_good);
        assertNull(mRemoteViews.getPackage());
    }

    @Test
    public void testGetLayoutId() {
        assertEquals(R.layout.remoteviews_good, mRemoteViews.getLayoutId());

        mRemoteViews = new RemoteViews(PACKAGE_NAME, R.layout.listview_layout);
        assertEquals(R.layout.listview_layout, mRemoteViews.getLayoutId());

        mRemoteViews = new RemoteViews(PACKAGE_NAME, INVALID_ID);
        assertEquals(INVALID_ID, mRemoteViews.getLayoutId());

        mRemoteViews = new RemoteViews(PACKAGE_NAME, 0);
        assertEquals(0, mRemoteViews.getLayoutId());
    }

    @Test
    public void testSetContentDescription() throws Throwable {
        View view = mResult.findViewById(R.id.remoteView_frame);

        assertNull(view.getContentDescription());

        CharSequence contentDescription = mContext.getString(R.string.remote_content_description);
        mRemoteViews.setContentDescription(R.id.remoteView_frame, contentDescription);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertTrue(TextUtils.equals(contentDescription, view.getContentDescription()));
    }

    @Test
    public void testSetViewVisibility() throws Throwable {
        View view = mResult.findViewById(R.id.remoteView_chronometer);
        assertEquals(View.VISIBLE, view.getVisibility());

        mRemoteViews.setViewVisibility(R.id.remoteView_chronometer, View.INVISIBLE);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(View.INVISIBLE, view.getVisibility());

        mRemoteViews.setViewVisibility(R.id.remoteView_chronometer, View.GONE);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(View.GONE, view.getVisibility());

        mRemoteViews.setViewVisibility(R.id.remoteView_chronometer, View.VISIBLE);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(View.VISIBLE, view.getVisibility());
    }

    @Test
    public void testSetTextViewText() throws Throwable {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
        assertEquals("", textView.getText().toString());

        String expected = "This is content";
        mRemoteViews.setTextViewText(R.id.remoteView_text, expected);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(expected, textView.getText().toString());

        mRemoteViews.setTextViewText(R.id.remoteView_text, null);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals("", textView.getText().toString());

        mRemoteViews.setTextViewText(R.id.remoteView_absolute, "");
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetTextViewTextSize() throws Throwable {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);

        mRemoteViews.setTextViewTextSize(R.id.remoteView_text, TypedValue.COMPLEX_UNIT_SP, 18);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(mContext.getResources().getDisplayMetrics().scaledDensity * 18,
                textView.getTextSize(), 0.001f);

        mRemoteViews.setTextViewTextSize(R.id.remoteView_absolute, TypedValue.COMPLEX_UNIT_SP, 20);
        assertThrowsOnReapply(Throwable.class);
    }

    @Test
    public void testSetIcon() throws Throwable {
        ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
        assertNull(image.getDrawable());

        Icon iconBlack = Icon.createWithResource(mContext, R.drawable.icon_black);
        mRemoteViews.setIcon(R.id.remoteView_image, "setImageIcon", iconBlack);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertNotNull(image.getDrawable());
        BitmapDrawable dBlack = (BitmapDrawable) mContext.getDrawable(R.drawable.icon_black);
        WidgetTestUtils.assertEquals(dBlack.getBitmap(),
                ((BitmapDrawable) image.getDrawable()).getBitmap());
    }

    @Test
    public void testSetIcon_nightMode() throws Throwable {
        ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
        Icon iconLight = Icon.createWithResource(mContext, R.drawable.icon_green);
        Icon iconDark = Icon.createWithResource(mContext, R.drawable.icon_blue);
        mRemoteViews.setIcon(R.id.remoteView_image, "setImageIcon", iconLight, iconDark);

        applyNightModeThenReapplyAndTest(false, () -> {
            assertNotNull(image.getDrawable());
            BitmapDrawable dLight = (BitmapDrawable) mContext.getDrawable(R.drawable.icon_green);
            WidgetTestUtils.assertEquals(dLight.getBitmap(),
                    ((BitmapDrawable) image.getDrawable()).getBitmap());
        });
        applyNightModeThenReapplyAndTest(true, () -> {
            assertNotNull(image.getDrawable());
            BitmapDrawable dDark = (BitmapDrawable) mContext.getDrawable(R.drawable.icon_blue);
            WidgetTestUtils.assertEquals(dDark.getBitmap(),
                    ((BitmapDrawable) image.getDrawable()).getBitmap());
        });
    }

    @Test
    public void testSetImageViewIcon() throws Throwable {
        ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
        assertNull(image.getDrawable());

        Icon iconBlue = Icon.createWithResource(mContext, R.drawable.icon_blue);
        mRemoteViews.setImageViewIcon(R.id.remoteView_image, iconBlue);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertNotNull(image.getDrawable());
        BitmapDrawable dBlue = (BitmapDrawable) mContext.getDrawable(R.drawable.icon_blue);
        WidgetTestUtils.assertEquals(dBlue.getBitmap(),
                ((BitmapDrawable) image.getDrawable()).getBitmap());

    }

    @Test
    public void testSetImageViewResource() throws Throwable {
        ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
        assertNull(image.getDrawable());

        mRemoteViews.setImageViewResource(R.id.remoteView_image, R.drawable.testimage);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertNotNull(image.getDrawable());
        BitmapDrawable d = (BitmapDrawable) mContext.getDrawable(R.drawable.testimage);
        WidgetTestUtils.assertEquals(d.getBitmap(),
                ((BitmapDrawable) image.getDrawable()).getBitmap());

        mRemoteViews.setImageViewResource(R.id.remoteView_absolute, R.drawable.testimage);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetImageViewUri() throws Throwable {
        String path = getTestImagePath();
        File imageFile = new File(path);

        try {
            createSampleImage(imageFile, R.raw.testimage);

            Uri uri = Uri.parse(path);
            ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
            assertNull(image.getDrawable());

            mRemoteViews.setImageViewUri(R.id.remoteView_image, uri);
            mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));

            Bitmap imageViewBitmap = ((BitmapDrawable) image.getDrawable()).getBitmap();
            Bitmap expectedBitmap = WidgetTestUtils.getUnscaledAndDitheredBitmap(
                    mContext.getResources(), R.raw.testimage, imageViewBitmap.getConfig());
            WidgetTestUtils.assertEquals(expectedBitmap, imageViewBitmap);
        } finally {
            imageFile.delete();
        }
    }

    /**
     * Returns absolute file path of location where test image should be stored
     */
    private String getTestImagePath() {
        return mContext.getFilesDir() + "/test.jpg";
    }

    @Test
    public void testSetChronometer() throws Throwable {
        long base1 = 50;
        long base2 = -50;
        Chronometer chronometer = (Chronometer) mResult.findViewById(R.id.remoteView_chronometer);

        mRemoteViews.setChronometer(R.id.remoteView_chronometer, base1, "HH:MM:SS",
                false);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(base1, chronometer.getBase());
        assertEquals("HH:MM:SS", chronometer.getFormat());

        mRemoteViews.setChronometer(R.id.remoteView_chronometer, base2, "HH:MM:SS",
                false);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(base2, chronometer.getBase());
        assertEquals("HH:MM:SS", chronometer.getFormat());

        mRemoteViews.setChronometer(R.id.remoteView_chronometer, base1, "invalid",
                true);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(base1, chronometer.getBase());
        assertEquals("invalid", chronometer.getFormat());

        mRemoteViews.setChronometer(R.id.remoteView_absolute, base1, "invalid", true);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetChronometerCountDown() throws Throwable {
        Chronometer chronometer = (Chronometer) mResult.findViewById(R.id.remoteView_chronometer);

        mRemoteViews.setChronometerCountDown(R.id.remoteView_chronometer, true);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertTrue(chronometer.isCountDown());

        mRemoteViews.setChronometerCountDown(R.id.remoteView_chronometer, false);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertFalse(chronometer.isCountDown());

        mRemoteViews.setChronometerCountDown(R.id.remoteView_absolute, true);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetProgressBar() throws Throwable {
        ProgressBar progress = (ProgressBar) mResult.findViewById(R.id.remoteView_progress);
        assertEquals(100, progress.getMax());
        assertEquals(0, progress.getProgress());
        // the view uses style progressBarHorizontal, so the default is false
        assertFalse(progress.isIndeterminate());

        mRemoteViews.setProgressBar(R.id.remoteView_progress, 80, 50, true);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        // make the bar indeterminate will not affect max and progress
        assertEquals(100, progress.getMax());
        assertEquals(0, progress.getProgress());
        assertTrue(progress.isIndeterminate());

        mRemoteViews.setProgressBar(R.id.remoteView_progress, 60, 50, false);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(60, progress.getMax());
        assertEquals(50, progress.getProgress());
        assertFalse(progress.isIndeterminate());

        mRemoteViews.setProgressBar(R.id.remoteView_relative, 60, 50, false);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testApply() {
        assertNotNull(mResult);
        assertNotNull(mResult.findViewById(R.id.remoteViews_good));
        assertNotNull(mResult.findViewById(R.id.remoteView_absolute));
        assertNotNull(mResult.findViewById(R.id.remoteView_chronometer));
        assertNotNull(mResult.findViewById(R.id.remoteView_frame));
        assertNotNull(mResult.findViewById(R.id.remoteView_image));
        assertNotNull(mResult.findViewById(R.id.remoteView_linear));
        assertNotNull(mResult.findViewById(R.id.remoteView_progress));
        assertNotNull(mResult.findViewById(R.id.remoteView_relative));
        assertNotNull(mResult.findViewById(R.id.remoteView_text));
    }

    @Test
    public void testReapply() throws Throwable {
        ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
        assertNull(image.getDrawable());

        mRemoteViews.setImageViewResource(R.id.remoteView_image, R.drawable.testimage);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, image));
        assertNotNull(image.getDrawable());
        BitmapDrawable d = (BitmapDrawable) mContext
                .getResources().getDrawable(R.drawable.testimage);
        WidgetTestUtils.assertEquals(d.getBitmap(),
                ((BitmapDrawable) image.getDrawable()).getBitmap());
    }

    @Test
    public void testDrawInstructionsVersion() {
        if (!drawDataParcel()) {
            return;
        }
        assertTrue(RemoteViews.DrawInstructions.getSupportedVersion() >= 1L);
    }

    @Test
    public void testApplyWithDrawInstructions() throws Throwable {
        if (!drawDataParcel()) {
            return;
        }
        final RemoteViews.DrawInstructions drawInstructions = getDrawInstructions();
        final String key = "mykey";
        final String action = "myaction";
        final MockBroadcastReceiver receiver = new MockBroadcastReceiver();
        mContext.registerReceiver(receiver, new IntentFilter(action), Context.RECEIVER_EXPORTED);

        final Intent intent = new Intent(action).setPackage(mContext.getPackageName());
        final PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        mContext,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        final Intent[] intents = new Intent[]{
                new Intent().putExtra(key, 1),
                new Intent().putExtra(key, 2),
                new Intent().putExtra(key, 3),
                new Intent().putExtra(key, 4)
        };
        final ActivityMonitor am = mInstrumentation.addMonitor(
                MockURLSpanTestActivity.class.getName(), null, false);
        Activity newActivity = am.waitForActivityWithTimeout(TEST_TIMEOUT);
        assertNull(newActivity);
        final int width = 100;
        final int height = 100;
        final int offset = 2;
        final Bitmap bitmap1 = Bitmap.createBitmap(width, height, ARGB_8888);
        final Bitmap bitmap2 = Bitmap.createBitmap(width, height, ARGB_8888);
        mRemoteViews = new RemoteViews(drawInstructions);
        for (int i = 0; i < 4; i++) {
            mRemoteViews.setPendingIntentTemplate(i + 1, pendingIntent);
            mRemoteViews.setOnClickFillInIntent(i + 1, intents[i]);
        }
        applyNightModeThenApplyAndTest(false /* nightMode */, () -> {});
        mActivityRule.runOnUiThread(() -> {
            mResult.measure(makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            mResult.layout(0, 0, width, height);
            mResult.draw(new Canvas(bitmap1));
        });
        verifyColorsOnFourCorners(Color.WHITE, bitmap1);
        bitmap1.recycle();

        // Switch to night mode
        applyNightModeThenReapplyAndTest(true /* nightMode */, () -> {});
        mActivityRule.runOnUiThread(() -> {
            mResult.draw(new Canvas(bitmap2));
        });
        verifyColorsOnFourCorners(Color.BLACK, bitmap2);
        bitmap2.recycle();

        // Verify clicks
        mActivityRule.runOnUiThread(() -> {
            final ViewGroup root = (ViewGroup) mActivityRule.getActivity().findViewById(
                    R.id.remoteView_host);
            root.removeAllViews();
            root.addView(mResult);
        });
        verifyClick(receiver, offset, offset, 1);
        verifyClick(receiver, width - offset, offset, 2);
        verifyClick(receiver, offset, height - offset, 3);
        verifyClick(receiver, width - offset, height - offset, 4);
    }

    private RemoteViews.DrawInstructions getDrawInstructions() throws IOException {
        try (InputStream is = mContext.getResources().openRawResource(R.raw.widget)
        ) {
            final byte[] bytes = new byte[(int) is.available()];
            final int result = is.read(bytes);
            return new RemoteViews.DrawInstructions.Builder(
                    Collections.singletonList(bytes)).build();
        }
    }

    private static void verifyColorsOnFourCorners(final int expectedColor, final Bitmap bitmap) {
        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();
        final int offset = 2;
        assertEquals(expectedColor, bitmap.getPixel(offset, offset));
        assertEquals(expectedColor, bitmap.getPixel(w - offset, offset));
        assertEquals(expectedColor, bitmap.getPixel(offset, h - offset));
        assertEquals(expectedColor, bitmap.getPixel(w - offset, h - offset));
    }

    private void verifyClick(final MockBroadcastReceiver receiver, final int x, final int y,
            final int expectedValue) throws Throwable {
        mActivityRule.runOnUiThread(() -> performClick(mResult, x, y));
        final Intent intent = receiver.awaitIntent();
        assertNotNull(intent);
        assertEquals(expectedValue, intent.getIntExtra("mykey", 0));
    }

    private static void performClick(final View v, final int x, final int y) {
        final long ts = SystemClock.uptimeMillis();
        final MotionEvent down = MotionEvent.obtain(
                ts,
                ts + 100,
                MotionEvent.ACTION_DOWN, x, y, 0);
        final MotionEvent up = MotionEvent.obtain(
                ts + 200,
                ts + 300,
                MotionEvent.ACTION_UP, x, y, 0);
        v.dispatchTouchEvent(down);
        v.dispatchTouchEvent(up);
    }

    @Test
    public void testOnLoadClass() {
        mRemoteViews = new RemoteViews(Parcel.obtain());

        assertTrue(mRemoteViews.onLoadClass(AbsoluteLayout.class));
        assertTrue(mRemoteViews.onLoadClass(AnalogClock.class));
        assertTrue(mRemoteViews.onLoadClass(Button.class));
        assertTrue(mRemoteViews.onLoadClass(CheckBox.class));
        assertTrue(mRemoteViews.onLoadClass(Chronometer.class));
        assertTrue(mRemoteViews.onLoadClass(FrameLayout.class));
        assertTrue(mRemoteViews.onLoadClass(GridLayout.class));
        assertTrue(mRemoteViews.onLoadClass(GridView.class));
        assertTrue(mRemoteViews.onLoadClass(ImageButton.class));
        assertTrue(mRemoteViews.onLoadClass(ImageView.class));
        assertTrue(mRemoteViews.onLoadClass(LinearLayout.class));
        assertTrue(mRemoteViews.onLoadClass(ListView.class));
        assertTrue(mRemoteViews.onLoadClass(ProgressBar.class));
        assertTrue(mRemoteViews.onLoadClass(RadioButton.class));
        assertTrue(mRemoteViews.onLoadClass(RadioGroup.class));
        assertTrue(mRemoteViews.onLoadClass(RelativeLayout.class));
        assertTrue(mRemoteViews.onLoadClass(StackView.class));
        assertTrue(mRemoteViews.onLoadClass(Switch.class));
        assertTrue(mRemoteViews.onLoadClass(TextClock.class));
        assertTrue(mRemoteViews.onLoadClass(TextView.class));
        assertTrue(mRemoteViews.onLoadClass(ViewFlipper.class));

        // those classes without annotation @RemoteView
        assertFalse(mRemoteViews.onLoadClass(EditText.class));
        assertFalse(mRemoteViews.onLoadClass(DatePicker.class));
        assertFalse(mRemoteViews.onLoadClass(NumberPicker.class));
        assertFalse(mRemoteViews.onLoadClass(RatingBar.class));
        assertFalse(mRemoteViews.onLoadClass(SeekBar.class));
    }

    @Test
    public void testDescribeContents() {
        mRemoteViews = new RemoteViews(Parcel.obtain());
        mRemoteViews.describeContents();
    }

    @Test
    public void testWriteToParcel() {
        mRemoteViews.setTextViewText(R.id.remoteView_text, "This is content");
        mRemoteViews.setViewVisibility(R.id.remoteView_frame, View.GONE);

        // the package and layout are successfully written into parcel
        mRemoteViews = parcelAndUnparcel(mRemoteViews);
        View result = mRemoteViews.apply(mContext, null);
        assertEquals(PACKAGE_NAME, mRemoteViews.getPackage());
        assertEquals(R.layout.remoteviews_good, mRemoteViews.getLayoutId());
        assertEquals("This is content", ((TextView) result.findViewById(R.id.remoteView_text))
                .getText().toString());
        assertEquals(View.GONE, result.findViewById(R.id.remoteView_frame).getVisibility());

        // currently the flag is not used
        parcelAndUnparcel(mRemoteViews, /* flags= */ -1);

        RemoteViews[] remote = RemoteViews.CREATOR.newArray(1);
        assertNotNull(remote);
        assertEquals(1, remote.length);
    }

    @Test(expected=NullPointerException.class)
    public void testWriteNullToParcel() {
        mRemoteViews.writeToParcel(null, 0);
    }

    @Test
    public void testWriteToParcel_landscapePortrait() {
        RemoteViews landscape = new RemoteViews(PACKAGE_NAME, R.layout.remoteviews_good);
        landscape.setTextViewText(R.id.remoteView_text, "Hello world");
        RemoteViews portrait = new RemoteViews(PACKAGE_NAME, R.layout.remoteviews_good);
        portrait.setTextViewText(R.id.remoteView_text, "Hello world");
        int landscapeParcelledSize = getParcelledSize(landscape);
        mRemoteViews = new RemoteViews(landscape, portrait);

        mRemoteViews = parcelAndUnparcel(mRemoteViews);
        assertEquals(PACKAGE_NAME, mRemoteViews.getPackage());
        View result = mRemoteViews.apply(mContext, null);
        assertEquals("Hello world", ((TextView) result.findViewById(R.id.remoteView_text))
                .getText().toString());

        // The ApplicationInfo should only have been written once, so this should be much smaller
        // than twice the size of parcelling one RemoteViews.
        assertLessThan(landscapeParcelledSize * 2, getParcelledSize(mRemoteViews));
    }

    @Test
    public void testWriteToParcel_sizeMap() {
        List<SizeF> sizes =
                Arrays.asList(new SizeF(50, 50), new SizeF(100, 100), new SizeF(100, 200));
        Map<SizeF, RemoteViews> sizeMap = new ArrayMap<>();
        int singelParcelledSize = 0;
        for (SizeF size : sizes) {
            RemoteViews views = new RemoteViews(PACKAGE_NAME, R.layout.remoteviews_good);
            views.setTextViewText(R.id.remoteView_text, "Hello world");
            sizeMap.put(size, views);
            singelParcelledSize = getParcelledSize(views);
        }
        mRemoteViews = new RemoteViews(sizeMap);

        mRemoteViews = parcelAndUnparcel(mRemoteViews);
        assertEquals(PACKAGE_NAME, mRemoteViews.getPackage());
        View result = mRemoteViews.apply(mContext, null);
        assertEquals("Hello world", ((TextView) result.findViewById(R.id.remoteView_text))
                .getText().toString());

        // The ApplicationInfo should only have been written once, so this should be much smaller
        // than thrice the size of parcelling one RemoteViews.
        assertLessThan(singelParcelledSize * 3, getParcelledSize(mRemoteViews));
    }

    @Test(expected=NegativeArraySizeException.class)
    public void testCreateNegativeSizedArray() {
        RemoteViews.CREATOR.newArray(-1);
    }

    @Test
    public void testSetImageViewBitmap() throws Throwable {
        ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
        assertNull(image.getDrawable());

        Bitmap bitmap =
                BitmapFactory.decodeResource(mContext.getResources(), R.drawable.testimage);
        mRemoteViews.setImageViewBitmap(R.id.remoteView_image, bitmap);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertNotNull(image.getDrawable());
        WidgetTestUtils.assertEquals(bitmap, ((BitmapDrawable) image.getDrawable()).getBitmap());

        mRemoteViews.setImageViewBitmap(R.id.remoteView_absolute, bitmap);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetImageViewBitmap_afterCopying() throws Throwable {
        Bitmap bitmap =
                BitmapFactory.decodeResource(mContext.getResources(), R.drawable.testimage);
        RemoteViews original =
                new RemoteViews(mContext.getPackageName(), R.layout.remoteviews_good);
        original.setImageViewBitmap(R.id.remoteView_image, bitmap);
        RemoteViews copy = new RemoteViews(original);

        AtomicReference<View> view = new AtomicReference<>();
        mActivityRule.runOnUiThread(() -> view.set(copy.apply(mContext, null)));

        ImageView image = view.get().findViewById(R.id.remoteView_image);
        assertNotNull(image.getDrawable());
        WidgetTestUtils.assertEquals(bitmap, ((BitmapDrawable) image.getDrawable()).getBitmap());
    }

    @Test
    public void testSetBitmap() throws Throwable {
        ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
        assertNull(image.getDrawable());

        Bitmap bitmap =
                BitmapFactory.decodeResource(mContext.getResources(), R.drawable.testimage);
        mRemoteViews.setBitmap(R.id.remoteView_image, "setImageBitmap", bitmap);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertNotNull(image.getDrawable());
        WidgetTestUtils.assertEquals(bitmap, ((BitmapDrawable) image.getDrawable()).getBitmap());

        mRemoteViews.setBitmap(R.id.remoteView_absolute, "setImageBitmap", bitmap);
        assertThrowsOnReapply(ActionException.class);

        assertEquals(bitmap.getAllocationByteCount(), mRemoteViews.estimateMemoryUsage());
    }

    @Test
    public void testSetBoolean() throws Throwable {
        ProgressBar progress = (ProgressBar) mResult.findViewById(R.id.remoteView_progress);
        // the view uses style progressBarHorizontal, so the default is false
        assertFalse(progress.isIndeterminate());

        mRemoteViews.setBoolean(R.id.remoteView_progress, "setIndeterminate", true);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertTrue(progress.isIndeterminate());

        mRemoteViews.setBoolean(R.id.remoteView_relative, "setIndeterminate", false);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetCharSequence() throws Throwable {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
        assertEquals("", textView.getText().toString());

        String expected = "test setCharSequence";
        mRemoteViews.setCharSequence(R.id.remoteView_text, "setText", expected);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(expected, textView.getText().toString());

        mRemoteViews.setCharSequence(R.id.remoteView_text, "setText", null);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals("", textView.getText().toString());

        mRemoteViews.setCharSequence(R.id.remoteView_absolute, "setText", "");
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetCharSequenceAttr() throws Throwable {
        mRemoteViews.setCharSequenceAttr(R.id.remoteView_text, "setText", R.attr.themeString);
        applyNightModeThenApplyAndTest(false, () -> {
            TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
            assertEquals("Day", textView.getText().toString());
        });

        applyNightModeThenApplyAndTest(true, () -> {
            TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
            assertEquals("Night", textView.getText().toString());
        });

        mRemoteViews.setCharSequenceAttr(R.id.remoteView_absolute, "setText",
                R.attr.themeColor);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetInt() throws Throwable {
        View view = mResult.findViewById(R.id.remoteView_chronometer);
        assertEquals(View.VISIBLE, view.getVisibility());

        mRemoteViews.setInt(R.id.remoteView_chronometer, "setVisibility", View.INVISIBLE);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(View.INVISIBLE, view.getVisibility());

        mRemoteViews.setInt(R.id.remoteView_chronometer, "setVisibility", View.GONE);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(View.GONE, view.getVisibility());

        mRemoteViews.setInt(R.id.remoteView_chronometer, "setVisibility", View.VISIBLE);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(View.VISIBLE, view.getVisibility());
    }

    @Test
    public void testSetString() throws Throwable {
        String format = "HH:MM:SS";
        Chronometer chronometer = (Chronometer) mResult.findViewById(R.id.remoteView_chronometer);
        assertNull(chronometer.getFormat());

        mRemoteViews.setString(R.id.remoteView_chronometer, "setFormat", format);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(format, chronometer.getFormat());

        mRemoteViews.setString(R.id.remoteView_image, "setFormat", format);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetUri() throws Throwable {
        String path = getTestImagePath();
        File imagefile = new File(path);

        try {
            createSampleImage(imagefile, R.raw.testimage);

            Uri uri = Uri.parse(path);
            ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
            assertNull(image.getDrawable());

            mRemoteViews.setUri(R.id.remoteView_image, "setImageURI", uri);
            mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));

            Bitmap imageViewBitmap = ((BitmapDrawable) image.getDrawable()).getBitmap();
            Bitmap expectedBitmap = WidgetTestUtils.getUnscaledAndDitheredBitmap(
                    mContext.getResources(), R.raw.testimage, imageViewBitmap.getConfig());
            WidgetTestUtils.assertEquals(expectedBitmap, imageViewBitmap);

            mRemoteViews.setUri(R.id.remoteView_absolute, "setImageURI", uri);
            assertThrowsOnReapply(ActionException.class);
        } finally {
            // remove the test image file
            imagefile.delete();
        }
    }

    @Test
    public void testSetTextColor() throws Throwable {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);

        mRemoteViews.setTextColor(R.id.remoteView_text, R.color.testcolor1);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertSame(ColorStateList.valueOf(R.color.testcolor1), textView.getTextColors());

        mRemoteViews.setTextColor(R.id.remoteView_text, R.color.testcolor2);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertSame(ColorStateList.valueOf(R.color.testcolor2), textView.getTextColors());

        mRemoteViews.setTextColor(R.id.remoteView_absolute, R.color.testcolor1);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetTextCompoundDrawables() throws Throwable {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);

        TestUtils.verifyCompoundDrawables(textView, -1, -1, -1, -1);

        mRemoteViews.setTextViewCompoundDrawables(R.id.remoteView_text, R.drawable.start,
                R.drawable.pass, R.drawable.failed, 0);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        TestUtils.verifyCompoundDrawables(textView, R.drawable.start, R.drawable.failed,
                R.drawable.pass, -1);

        mRemoteViews.setTextViewCompoundDrawables(R.id.remoteView_text, 0,
                R.drawable.icon_black, R.drawable.icon_red, R.drawable.icon_green);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        TestUtils.verifyCompoundDrawables(textView, -1,  R.drawable.icon_red, R.drawable.icon_black,
                R.drawable.icon_green);

        mRemoteViews.setTextViewCompoundDrawables(R.id.remoteView_absolute, 0,
                R.drawable.start, R.drawable.failed, 0);
        assertThrowsOnReapply(Throwable.class);
    }

    @Test
    public void testSetTextCompoundDrawablesRelative() throws Throwable {
        TextView textViewLtr = (TextView) mResult.findViewById(R.id.remoteView_text_ltr);
        TextView textViewRtl = (TextView) mResult.findViewById(R.id.remoteView_text_rtl);

        TestUtils.verifyCompoundDrawables(textViewLtr, -1, -1, -1, -1);
        TestUtils.verifyCompoundDrawables(textViewRtl, -1, -1, -1, -1);

        mRemoteViews.setTextViewCompoundDrawablesRelative(R.id.remoteView_text_ltr,
                R.drawable.start, R.drawable.pass, R.drawable.failed, 0);
        mRemoteViews.setTextViewCompoundDrawablesRelative(R.id.remoteView_text_rtl,
                R.drawable.start, R.drawable.pass, R.drawable.failed, 0);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        TestUtils.verifyCompoundDrawables(textViewLtr, R.drawable.start, R.drawable.failed,
                R.drawable.pass, -1);
        TestUtils.verifyCompoundDrawables(textViewRtl, R.drawable.failed, R.drawable.start,
                R.drawable.pass, -1);

        mRemoteViews.setTextViewCompoundDrawablesRelative(R.id.remoteView_text_ltr, 0,
                R.drawable.icon_black, R.drawable.icon_red, R.drawable.icon_green);
        mRemoteViews.setTextViewCompoundDrawablesRelative(R.id.remoteView_text_rtl, 0,
                R.drawable.icon_black, R.drawable.icon_red, R.drawable.icon_green);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        TestUtils.verifyCompoundDrawables(textViewLtr, -1, R.drawable.icon_red,
                R.drawable.icon_black, R.drawable.icon_green);
        TestUtils.verifyCompoundDrawables(textViewRtl, R.drawable.icon_red, -1,
                R.drawable.icon_black, R.drawable.icon_green);

        mRemoteViews.setTextViewCompoundDrawablesRelative(R.id.remoteView_absolute, 0,
                R.drawable.start, R.drawable.failed, 0);
        assertThrowsOnReapply(Throwable.class);
    }

    @LargeTest
    @Test
    public void testSetOnClickPendingIntent() throws Throwable {
        Uri uri = Uri.parse("ctstest://RemoteView/test");
        ActivityMonitor am = mInstrumentation.addMonitor(MockURLSpanTestActivity.class.getName(),
                null, false);
        View view = mResult.findViewById(R.id.remoteView_image);
        view.performClick();
        Activity newActivity = am.waitForActivityWithTimeout(TEST_TIMEOUT);
        assertNull(newActivity);

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        mRemoteViews.setOnClickPendingIntent(R.id.remoteView_image, pendingIntent);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        mActivityRule.runOnUiThread(() -> view.performClick());
        newActivity = am.waitForActivityWithTimeout(TEST_TIMEOUT);
        assertNotNull(newActivity);
        assertTrue(newActivity instanceof MockURLSpanTestActivity);
        newActivity.finish();
    }

    @LargeTest
    @Test
    @RequiresFlagsEnabled(FLAG_HOME_SCREEN_HANDWRITING_DELEGATOR)
    public void testSetOnStylusHandwritingPendingIntent() throws Throwable {
        ActivityMonitor am = mInstrumentation.addMonitor(MockURLSpanTestActivity.class.getName(),
                null, false);

        Uri uri = Uri.parse("ctstest://RemoteView/test");
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        mRemoteViews.setOnStylusHandwritingPendingIntent(R.id.remoteView_image, pendingIntent);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        View view = mResult.findViewById(R.id.remoteView_image);
        mActivityRule.runOnUiThread(view.getHandwritingDelegatorCallback());

        Activity newActivity = am.waitForActivityWithTimeout(TEST_TIMEOUT);
        assertNotNull(newActivity);
        assertTrue(newActivity instanceof MockURLSpanTestActivity);
        newActivity.finish();
    }

    @LargeTest
    @Test
    @RequiresFlagsEnabled(FLAG_HOME_SCREEN_HANDWRITING_DELEGATOR)
    public void testSetOnStylusHandwritingPendingIntent_null() throws Throwable {
        ActivityMonitor am = mInstrumentation.addMonitor(MockURLSpanTestActivity.class.getName(),
                null, false);

        Uri uri = Uri.parse("ctstest://RemoteView/test");
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        mRemoteViews.setOnStylusHandwritingPendingIntent(R.id.remoteView_image, pendingIntent);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        mRemoteViews.setOnStylusHandwritingPendingIntent(R.id.remoteView_image, null);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));

        View view = mResult.findViewById(R.id.remoteView_image);
        assertNull(view.getHandwritingDelegatorCallback());
    }

    @Test
    public void testSetOnCheckedChangeResponse() throws Throwable {
        String action = "my-checked-change-action";
        MockBroadcastReceiver receiver =  new MockBroadcastReceiver();
        mContext.registerReceiver(receiver, new IntentFilter(action),
                Context.RECEIVER_EXPORTED_UNAUDITED);

        Intent intent = new Intent(action).setPackage(mContext.getPackageName());
        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        mContext,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        mRemoteViews.setOnCheckedChangeResponse(R.id.remoteView_checkBox,
                RemoteViews.RemoteResponse.fromPendingIntent(pendingIntent));

        // View being checked to true should launch the intent with the extra set to true.
        CompoundButton view = mResult.findViewById(R.id.remoteView_checkBox);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        mActivityRule.runOnUiThread(() -> view.setChecked(true));
        mInstrumentation.waitForIdleSync();
        assertNotNull(receiver.mIntent);
        assertTrue(receiver.mIntent.getBooleanExtra(RemoteViews.EXTRA_CHECKED, false));

        // Changing the checked state from a RemoteViews action should not launch the intent.
        receiver.mIntent = null;
        mRemoteViews.setCompoundButtonChecked(R.id.remoteView_checkBox, false);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        mInstrumentation.waitForIdleSync();
        assertFalse(view.isChecked());
        assertNull(receiver.mIntent);

        // View being checked to false should launch the intent with the extra set to false.
        receiver.mIntent = null;
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        mActivityRule.runOnUiThread(() -> view.setChecked(true));
        mActivityRule.runOnUiThread(() -> view.setChecked(false));
        mInstrumentation.waitForIdleSync();
        assertNotNull(receiver.mIntent);
        assertFalse(receiver.mIntent.getBooleanExtra(RemoteViews.EXTRA_CHECKED, true));
    }

    @Test
    public void testSetLong() throws Throwable {
        long base1 = 50;
        long base2 = -50;
        Chronometer chronometer = (Chronometer) mResult.findViewById(R.id.remoteView_chronometer);

        mRemoteViews.setLong(R.id.remoteView_chronometer, "setBase", base1);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(base1, chronometer.getBase());

        mRemoteViews.setLong(R.id.remoteView_chronometer, "setBase", base2);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(base2, chronometer.getBase());

        mRemoteViews.setLong(R.id.remoteView_absolute, "setBase", base1);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetFloat() throws Throwable {
        LinearLayout linearLayout = (LinearLayout) mResult.findViewById(R.id.remoteView_linear);
        assertTrue(linearLayout.getWeightSum() <= 0.0f);

        mRemoteViews.setFloat(R.id.remoteView_linear, "setWeightSum", 0.5f);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(0.5f, linearLayout.getWeightSum(), 0.001f);

        mRemoteViews.setFloat(R.id.remoteView_absolute, "setWeightSum", 1.0f);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetByte() throws Throwable {
        MyRemotableView customView = (MyRemotableView) mResult.findViewById(R.id.remoteView_custom);
        assertEquals(0, customView.getByteField());

        byte b = 100;
        mRemoteViews.setByte(R.id.remoteView_custom, "setByteField", b);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(b, customView.getByteField());

        mRemoteViews.setByte(R.id.remoteView_absolute, "setByteField", b);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetChar() throws Throwable {
        MyRemotableView customView = (MyRemotableView) mResult.findViewById(R.id.remoteView_custom);
        assertEquals('\u0000', customView.getCharField());

        mRemoteViews.setChar(R.id.remoteView_custom, "setCharField", 'q');
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals('q', customView.getCharField());

        mRemoteViews.setChar(R.id.remoteView_absolute, "setCharField", 'w');
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetDouble() throws Throwable {
        MyRemotableView customView = (MyRemotableView) mResult.findViewById(R.id.remoteView_custom);
        assertEquals(0.0, customView.getDoubleField(), 0.0f);

        mRemoteViews.setDouble(R.id.remoteView_custom, "setDoubleField", 0.5);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(0.5, customView.getDoubleField(), 0.001f);

        mRemoteViews.setDouble(R.id.remoteView_absolute, "setDoubleField", 1.0);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetShort() throws Throwable {
        MyRemotableView customView = (MyRemotableView) mResult.findViewById(R.id.remoteView_custom);
        assertEquals(0, customView.getShortField());

        short s = 25;
        mRemoteViews.setShort(R.id.remoteView_custom, "setShortField", s);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(s, customView.getShortField());

        mRemoteViews.setShort(R.id.remoteView_absolute, "setShortField", s);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetBundle() throws Throwable {
        MyRemotableView customView = (MyRemotableView) mResult.findViewById(R.id.remoteView_custom);
        assertNull(customView.getBundleField());

        final Bundle bundle = new Bundle();
        bundle.putString("STR", "brexit");
        bundle.putInt("INT", 2016);
        mRemoteViews.setBundle(R.id.remoteView_custom, "setBundleField", bundle);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        final Bundle fromRemote = customView.getBundleField();
        assertEquals("brexit", fromRemote.getString("STR", ""));
        assertEquals(2016, fromRemote.getInt("INT", 0));

        mRemoteViews.setBundle(R.id.remoteView_absolute, "setBundleField", bundle);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetIntent() throws Throwable {
        MyRemotableView customView = (MyRemotableView) mResult.findViewById(R.id.remoteView_custom);
        assertNull(customView.getIntentField());

        final Intent intent = new Intent(mContext, SwitchCtsActivity.class);
        intent.putExtra("STR", "brexit");
        intent.putExtra("INT", 2016);
        mRemoteViews.setIntent(R.id.remoteView_custom, "setIntentField", intent);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        final Intent fromRemote = customView.getIntentField();
        assertEquals(SwitchCtsActivity.class.getName(), fromRemote.getComponent().getClassName());
        assertEquals("brexit", fromRemote.getStringExtra("STR"));
        assertEquals(2016, fromRemote.getIntExtra("INT", 0));

        mRemoteViews.setIntent(R.id.remoteView_absolute, "setIntentField", intent);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetBlendMode() throws Throwable {
        ImageView imageView = mResult.findViewById(R.id.remoteView_image);

        mRemoteViews.setBlendMode(R.id.remoteView_image, "setImageTintBlendMode", BlendMode.PLUS);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(BlendMode.PLUS, imageView.getImageTintBlendMode());

        mRemoteViews.setBlendMode(R.id.remoteView_image, "setImageTintBlendMode", BlendMode.SRC_IN);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(BlendMode.SRC_IN, imageView.getImageTintBlendMode());

        mRemoteViews.setBlendMode(R.id.remoteView_image, "setImageTintBlendMode", null);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertNull(imageView.getImageTintBlendMode());
    }

    @Test
    public void testRemoveAllViews() throws Throwable {
        ViewGroup root = (ViewGroup) mResult.findViewById(R.id.remoteViews_good);
        assertTrue(root.getChildCount() > 0);

        mRemoteViews.removeAllViews(R.id.remoteViews_good);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(0, root.getChildCount());
    }

    @Test
    public void testAddView() throws Throwable {
        ViewGroup root = (ViewGroup) mResult.findViewById(R.id.remoteViews_good);
        int originalChildCount = root.getChildCount();

        assertNull(root.findViewById(R.id.remoteView_frame_extra));

        // Create a RemoteViews wrapper around a layout and add it to our root
        RemoteViews extra = new RemoteViews(PACKAGE_NAME, R.layout.remoteviews_extra);
        mRemoteViews.addView(R.id.remoteViews_good, extra);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));

        // Verify that our root has that layout as its last (new) child
        assertEquals(originalChildCount + 1, root.getChildCount());
        assertNotNull(root.findViewById(R.id.remoteView_frame_extra));
        assertEquals(R.id.remoteView_frame_extra, root.getChildAt(originalChildCount).getId());
    }

    @Test
    public void testSetLabelFor() throws Throwable {
        View labelView = mResult.findViewById(R.id.remoteView_label);
        assertEquals(View.NO_ID, labelView.getLabelFor());

        mRemoteViews.setLabelFor(R.id.remoteView_label, R.id.remoteView_text);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(R.id.remoteView_text, labelView.getLabelFor());
    }

    @Test
    public void testSetAccessibilityTraversalAfter() throws Throwable {
        View textView = mResult.findViewById(R.id.remoteView_text);

        mRemoteViews.setAccessibilityTraversalAfter(R.id.remoteView_text, R.id.remoteView_frame);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(R.id.remoteView_frame, textView.getAccessibilityTraversalAfter());

        mRemoteViews.setAccessibilityTraversalAfter(R.id.remoteView_text, R.id.remoteView_linear);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(R.id.remoteView_linear, textView.getAccessibilityTraversalAfter());
    }

    @Test
    public void testSetAccessibilityTraversalBefore() throws Throwable {
        View textView = mResult.findViewById(R.id.remoteView_text);

        mRemoteViews.setAccessibilityTraversalBefore(R.id.remoteView_text, R.id.remoteView_frame);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(R.id.remoteView_frame, textView.getAccessibilityTraversalBefore());

        mRemoteViews.setAccessibilityTraversalBefore(R.id.remoteView_text, R.id.remoteView_linear);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(R.id.remoteView_linear, textView.getAccessibilityTraversalBefore());
    }

    @Test
    public void testSetViewPadding() throws Throwable {
        View textView = mResult.findViewById(R.id.remoteView_text);

        mRemoteViews.setViewPadding(R.id.remoteView_text, 10, 20, 30, 40);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(10, textView.getPaddingLeft());
        assertEquals(20, textView.getPaddingTop());
        assertEquals(30, textView.getPaddingRight());
        assertEquals(40, textView.getPaddingBottom());

        mRemoteViews.setViewPadding(R.id.remoteView_text, 40, 30, 20, 10);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(40, textView.getPaddingLeft());
        assertEquals(30, textView.getPaddingTop());
        assertEquals(20, textView.getPaddingRight());
        assertEquals(10, textView.getPaddingBottom());
    }

    @Test
    public void testSetViewLayoutMargin() throws Throwable {
        View textView = mResult.findViewById(R.id.remoteView_text);

        mRemoteViews.setViewLayoutMargin(R.id.remoteView_text, MARGIN_LEFT, 10, COMPLEX_UNIT_PX);
        mRemoteViews.setViewLayoutMargin(R.id.remoteView_text, MARGIN_TOP, 20, COMPLEX_UNIT_PX);
        mRemoteViews.setViewLayoutMargin(R.id.remoteView_text, MARGIN_RIGHT, 30, COMPLEX_UNIT_PX);
        mRemoteViews.setViewLayoutMargin(R.id.remoteView_text, MARGIN_BOTTOM, 40, COMPLEX_UNIT_PX);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertMargins(textView, 10, 20, 30, 40);

        mRemoteViews.setViewLayoutMargin(R.id.remoteView_text, MARGIN_LEFT, 10, COMPLEX_UNIT_DIP);
        mRemoteViews.setViewLayoutMargin(R.id.remoteView_text, MARGIN_TOP, 20, COMPLEX_UNIT_DIP);
        mRemoteViews.setViewLayoutMargin(R.id.remoteView_text, MARGIN_RIGHT, 30, COMPLEX_UNIT_DIP);
        mRemoteViews.setViewLayoutMargin(R.id.remoteView_text, MARGIN_BOTTOM, 40, COMPLEX_UNIT_DIP);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        DisplayMetrics displayMetrics = textView.getResources().getDisplayMetrics();
        assertMargins(
                textView,
                resolveDimenOffset(10, COMPLEX_UNIT_DIP, displayMetrics),
                resolveDimenOffset(20, COMPLEX_UNIT_DIP, displayMetrics),
                resolveDimenOffset(30, COMPLEX_UNIT_DIP, displayMetrics),
                resolveDimenOffset(40, COMPLEX_UNIT_DIP, displayMetrics));
    }

    @Test
    public void testSetViewLayoutMargin_layoutDirection() throws Throwable {
        View textViewLtr = mResult.findViewById(R.id.remoteView_text_ltr);
        mRemoteViews.setViewLayoutMargin(textViewLtr.getId(), MARGIN_START, 10, COMPLEX_UNIT_DIP);
        mRemoteViews.setViewLayoutMargin(textViewLtr.getId(), MARGIN_TOP, 20, COMPLEX_UNIT_DIP);
        mRemoteViews.setViewLayoutMargin(textViewLtr.getId(), MARGIN_END, 30, COMPLEX_UNIT_DIP);
        mRemoteViews.setViewLayoutMargin(textViewLtr.getId(), MARGIN_BOTTOM, 40, COMPLEX_UNIT_DIP);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        DisplayMetrics displayMetrics = textViewLtr.getResources().getDisplayMetrics();
        assertMargins(
                textViewLtr,
                resolveDimenOffset(10, COMPLEX_UNIT_DIP, displayMetrics),
                resolveDimenOffset(20, COMPLEX_UNIT_DIP, displayMetrics),
                resolveDimenOffset(30, COMPLEX_UNIT_DIP, displayMetrics),
                resolveDimenOffset(40, COMPLEX_UNIT_DIP, displayMetrics));

        View textViewRtl = mResult.findViewById(R.id.remoteView_text_rtl);
        mRemoteViews.setViewLayoutMargin(textViewRtl.getId(), MARGIN_START, 10, COMPLEX_UNIT_DIP);
        mRemoteViews.setViewLayoutMargin(textViewRtl.getId(), MARGIN_TOP, 20, COMPLEX_UNIT_DIP);
        mRemoteViews.setViewLayoutMargin(textViewRtl.getId(), MARGIN_END, 30, COMPLEX_UNIT_DIP);
        mRemoteViews.setViewLayoutMargin(textViewRtl.getId(), MARGIN_BOTTOM, 40, COMPLEX_UNIT_DIP);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        displayMetrics = textViewRtl.getResources().getDisplayMetrics();
        assertMargins(
                textViewRtl,
                resolveDimenOffset(30, COMPLEX_UNIT_DIP, displayMetrics),
                resolveDimenOffset(20, COMPLEX_UNIT_DIP, displayMetrics),
                resolveDimenOffset(10, COMPLEX_UNIT_DIP, displayMetrics),
                resolveDimenOffset(40, COMPLEX_UNIT_DIP, displayMetrics));
    }

    @Test
    public void testSetViewLayoutMarginDimen() throws Throwable {
        View textView = mResult.findViewById(R.id.remoteView_text);
        mRemoteViews.setViewLayoutMarginDimen(
                R.id.remoteView_text, MARGIN_LEFT, R.dimen.textview_padding_left);
        mRemoteViews.setViewLayoutMarginDimen(
                R.id.remoteView_text, MARGIN_TOP, R.dimen.textview_padding_top);
        mRemoteViews.setViewLayoutMarginDimen(
                R.id.remoteView_text, MARGIN_RIGHT, R.dimen.textview_padding_right);
        mRemoteViews.setViewLayoutMarginDimen(
                R.id.remoteView_text, MARGIN_BOTTOM, R.dimen.textview_padding_bottom);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertMargins(
                textView,
                textView.getResources().getDimensionPixelOffset(R.dimen.textview_padding_left),
                textView.getResources().getDimensionPixelOffset(R.dimen.textview_padding_top),
                textView.getResources().getDimensionPixelOffset(R.dimen.textview_padding_right),
                textView.getResources().getDimensionPixelOffset(R.dimen.textview_padding_bottom));
    }

    @Test
    public void testSetViewLayoutMarginDimen_layoutDirection() throws Throwable {
        View textViewLtr = mResult.findViewById(R.id.remoteView_text_ltr);
        mRemoteViews.setViewLayoutMarginDimen(
                R.id.remoteView_text_ltr, MARGIN_START, R.dimen.textview_padding_left);
        mRemoteViews.setViewLayoutMarginDimen(
                R.id.remoteView_text_ltr, MARGIN_TOP, R.dimen.textview_padding_top);
        mRemoteViews.setViewLayoutMarginDimen(
                R.id.remoteView_text_ltr, MARGIN_END, R.dimen.textview_padding_right);
        mRemoteViews.setViewLayoutMarginDimen(
                R.id.remoteView_text_ltr, MARGIN_BOTTOM, R.dimen.textview_padding_bottom);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertMargins(
                textViewLtr,
                textViewLtr.getResources().getDimensionPixelOffset(R.dimen.textview_padding_left),
                textViewLtr.getResources().getDimensionPixelOffset(R.dimen.textview_padding_top),
                textViewLtr.getResources().getDimensionPixelOffset(R.dimen.textview_padding_right),
                textViewLtr.getResources().getDimensionPixelOffset(
                        R.dimen.textview_padding_bottom));

        View textViewRtl = mResult.findViewById(R.id.remoteView_text_rtl);
        mRemoteViews.setViewLayoutMarginDimen(
                R.id.remoteView_text_rtl, MARGIN_START, R.dimen.textview_padding_left);
        mRemoteViews.setViewLayoutMarginDimen(
                R.id.remoteView_text_rtl, MARGIN_TOP, R.dimen.textview_padding_top);
        mRemoteViews.setViewLayoutMarginDimen(
                R.id.remoteView_text_rtl, MARGIN_END, R.dimen.textview_padding_right);
        mRemoteViews.setViewLayoutMarginDimen(
                R.id.remoteView_text_rtl, MARGIN_BOTTOM, R.dimen.textview_padding_bottom);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertMargins(
                textViewRtl,
                textViewRtl.getResources().getDimensionPixelOffset(R.dimen.textview_padding_right),
                textViewRtl.getResources().getDimensionPixelOffset(R.dimen.textview_padding_top),
                textViewRtl.getResources().getDimensionPixelOffset(R.dimen.textview_padding_left),
                textViewRtl.getResources().getDimensionPixelOffset(
                        R.dimen.textview_padding_bottom));
    }

    @Test
    public void testSetViewLayoutMarginAttr() throws Throwable {
        mRemoteViews.setViewLayoutMarginAttr(
                R.id.remoteView_text, MARGIN_LEFT, R.attr.themeDimension);
        mRemoteViews.setViewLayoutMarginAttr(
                R.id.remoteView_text, MARGIN_TOP, R.attr.themeDimension2);
        mRemoteViews.setViewLayoutMarginAttr(
                R.id.remoteView_text, MARGIN_RIGHT, R.attr.themeDimension3);
        mRemoteViews.setViewLayoutMarginAttr(
                R.id.remoteView_text, MARGIN_BOTTOM, R.attr.themeDimension4);

        applyNightModeThenApplyAndTest(false, () -> {
            View textView = mResult.findViewById(R.id.remoteView_text);
            DisplayMetrics displayMetrics = textView.getResources().getDisplayMetrics();
            assertMargins(
                    textView,
                    resolveDimenOffset(5.5123f, COMPLEX_UNIT_DIP, displayMetrics),
                    resolveDimenOffset(2.5f, COMPLEX_UNIT_DIP, displayMetrics),
                    resolveDimenOffset(3.5f, COMPLEX_UNIT_DIP, displayMetrics),
                    resolveDimenOffset(4.5f, COMPLEX_UNIT_DIP, displayMetrics));
        });

        applyNightModeThenApplyAndTest(true, () -> {
            View textView = mResult.findViewById(R.id.remoteView_text);
            DisplayMetrics displayMetrics = textView.getResources().getDisplayMetrics();
            assertMargins(
                    textView,
                    resolveDimenOffset(7.5123f, COMPLEX_UNIT_DIP, displayMetrics),
                    resolveDimenOffset(4.5f, COMPLEX_UNIT_DIP, displayMetrics),
                    resolveDimenOffset(5.5f, COMPLEX_UNIT_DIP, displayMetrics),
                    resolveDimenOffset(6.5f, COMPLEX_UNIT_DIP, displayMetrics));
        });

        // Test that zeros resolve to 0
        mRemoteViews.setViewLayoutMarginAttr(R.id.remoteView_text, MARGIN_LEFT, 0);
        mRemoteViews.setViewLayoutMarginAttr(R.id.remoteView_text, MARGIN_TOP, 0);
        mRemoteViews.setViewLayoutMarginAttr(R.id.remoteView_text, MARGIN_RIGHT, 0);
        applyNightModeThenApplyAndTest(false, () -> {
            View textView = mResult.findViewById(R.id.remoteView_text);
            DisplayMetrics displayMetrics = textView.getResources().getDisplayMetrics();
            assertMargins(textView, 0, 0, 0,
                    resolveDimenOffset(4.5f, COMPLEX_UNIT_DIP, displayMetrics));
        });
        applyNightModeThenApplyAndTest(true, () -> {
            View textView = mResult.findViewById(R.id.remoteView_text);
            DisplayMetrics displayMetrics = textView.getResources().getDisplayMetrics();
            assertMargins(textView, 0, 0, 0,
                    resolveDimenOffset(6.5f, COMPLEX_UNIT_DIP, displayMetrics));
        });

        mRemoteViews.setViewLayoutMarginAttr(
                R.id.remoteView_text, MARGIN_LEFT, R.attr.themeColor);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetViewLayoutMarginAttr_layoutDirection() throws Throwable {
        mRemoteViews.setViewLayoutMarginAttr(
                R.id.remoteView_text_ltr, MARGIN_START, R.attr.themeDimension);
        mRemoteViews.setViewLayoutMarginAttr(
                R.id.remoteView_text_ltr, MARGIN_TOP, R.attr.themeDimension2);
        mRemoteViews.setViewLayoutMarginAttr(
                R.id.remoteView_text_ltr, MARGIN_END, R.attr.themeDimension3);
        mRemoteViews.setViewLayoutMarginAttr(
                R.id.remoteView_text_ltr, MARGIN_BOTTOM, R.attr.themeDimension4);

        mRemoteViews.setViewLayoutMarginAttr(
                R.id.remoteView_text_rtl, MARGIN_START, R.attr.themeDimension);
        mRemoteViews.setViewLayoutMarginAttr(
                R.id.remoteView_text_rtl, MARGIN_TOP, R.attr.themeDimension2);
        mRemoteViews.setViewLayoutMarginAttr(
                R.id.remoteView_text_rtl, MARGIN_END, R.attr.themeDimension3);
        mRemoteViews.setViewLayoutMarginAttr(
                R.id.remoteView_text_rtl, MARGIN_BOTTOM, R.attr.themeDimension4);

        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));

        View textViewLtr = mResult.findViewById(R.id.remoteView_text_ltr);
        DisplayMetrics displayMetrics = textViewLtr.getResources().getDisplayMetrics();

        assertMargins(
                textViewLtr,
                resolveDimenOffset(5.5123f, COMPLEX_UNIT_DIP, displayMetrics),
                resolveDimenOffset(2.5f, COMPLEX_UNIT_DIP, displayMetrics),
                resolveDimenOffset(3.5f, COMPLEX_UNIT_DIP, displayMetrics),
                resolveDimenOffset(4.5f, COMPLEX_UNIT_DIP, displayMetrics));

        View textViewRtl = mResult.findViewById(R.id.remoteView_text_rtl);
        assertMargins(
                textViewRtl,
                resolveDimenOffset(3.5f, COMPLEX_UNIT_DIP, displayMetrics),
                resolveDimenOffset(2.5f, COMPLEX_UNIT_DIP, displayMetrics),
                resolveDimenOffset(5.5123f, COMPLEX_UNIT_DIP, displayMetrics),
                resolveDimenOffset(4.5f, COMPLEX_UNIT_DIP, displayMetrics));
    }

    @Test
    public void testSetViewLayoutWidth() throws Throwable {
        View textView = mResult.findViewById(R.id.remoteView_text);
        DisplayMetrics displayMetrics = textView.getResources().getDisplayMetrics();

        mRemoteViews.setViewLayoutWidth(R.id.remoteView_text, 10, COMPLEX_UNIT_PX);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(10, textView.getLayoutParams().width);

        mRemoteViews.setViewLayoutWidth(R.id.remoteView_text, 20, COMPLEX_UNIT_DIP);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(
                resolveDimenSize(20, COMPLEX_UNIT_DIP, displayMetrics),
                textView.getLayoutParams().width);

        mRemoteViews.setViewLayoutWidth(
                R.id.remoteView_text, ViewGroup.LayoutParams.MATCH_PARENT, COMPLEX_UNIT_PX);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, textView.getLayoutParams().width);
    }

    @Test
    public void testSetViewLayoutWidthDimen() throws Throwable {
        View textView = mResult.findViewById(R.id.remoteView_text);
        mRemoteViews.setViewLayoutWidthDimen(R.id.remoteView_text, R.dimen.textview_fixed_width);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(
                textView.getResources().getDimensionPixelSize(R.dimen.textview_fixed_width),
                textView.getLayoutParams().width);

        mRemoteViews.setViewLayoutWidthDimen(R.id.remoteView_text, 0);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(0, textView.getLayoutParams().width);
    }

    @Test
    public void testSetViewLayoutWidthAttr() throws Throwable {
        View textView = mResult.findViewById(R.id.remoteView_text);
        mRemoteViews.setViewLayoutWidthAttr(R.id.remoteView_text, R.attr.themeDimension);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(
                resolveDimenSize(5.5123f, COMPLEX_UNIT_DIP,
                        textView.getResources().getDisplayMetrics()),
                textView.getLayoutParams().width);

        mRemoteViews.setViewLayoutWidthAttr(R.id.remoteView_text, 0);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(0, textView.getLayoutParams().width);

        mRemoteViews.setViewLayoutWidthAttr(R.id.remoteView_text, R.attr.themeColor);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetViewLayoutHeight() throws Throwable {
        View textView = mResult.findViewById(R.id.remoteView_text);
        DisplayMetrics displayMetrics = textView.getResources().getDisplayMetrics();

        mRemoteViews.setViewLayoutHeight(R.id.remoteView_text, 10, COMPLEX_UNIT_PX);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(10, textView.getLayoutParams().height);

        mRemoteViews.setViewLayoutHeight(R.id.remoteView_text, 20, COMPLEX_UNIT_DIP);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(
                resolveDimenSize(20, COMPLEX_UNIT_DIP, displayMetrics),
                textView.getLayoutParams().height);

        mRemoteViews.setViewLayoutHeight(
                R.id.remoteView_text, ViewGroup.LayoutParams.MATCH_PARENT, COMPLEX_UNIT_PX);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, textView.getLayoutParams().height);
    }

    @Test
    public void testSetViewLayoutHeightDimen() throws Throwable {
        View textView = mResult.findViewById(R.id.remoteView_text);
        mRemoteViews.setViewLayoutHeightDimen(R.id.remoteView_text, R.dimen.textview_fixed_height);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(
                textView.getResources().getDimensionPixelSize(R.dimen.textview_fixed_height),
                textView.getLayoutParams().height);

        mRemoteViews.setViewLayoutHeightDimen(R.id.remoteView_text, 0);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(0, textView.getLayoutParams().height);
    }

    @Test
    public void testSetViewLayoutHeightAttr() throws Throwable {
        View textView = mResult.findViewById(R.id.remoteView_text);
        mRemoteViews.setViewLayoutHeightAttr(R.id.remoteView_text, R.attr.themeDimension);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(
                resolveDimenSize(5.5123f, COMPLEX_UNIT_DIP,
                        textView.getResources().getDisplayMetrics()),
                textView.getLayoutParams().height);

        mRemoteViews.setViewLayoutHeightAttr(R.id.remoteView_text, 0);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(0, textView.getLayoutParams().height);

        mRemoteViews.setViewLayoutHeightAttr(
                R.id.remoteView_text, R.attr.themeColor);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetIntDimen_fromResources() throws Throwable {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
        int expectedValue = mContext.getResources().getDimensionPixelSize(R.dimen.popup_row_height);

        mRemoteViews.setIntDimen(R.id.remoteView_text, "setCompoundDrawablePadding",
                R.dimen.popup_row_height);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(expectedValue, textView.getCompoundDrawablePadding());

        // test that passing 0 for the dimen sets 0 on the method.
        mRemoteViews.setIntDimen(R.id.remoteView_text, "setCompoundDrawablePadding", 0);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(0, textView.getCompoundDrawablePadding());

        mRemoteViews.setIntDimen(R.id.remoteView_text, "setCompoundDrawablePadding",
                R.color.testcolor1);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetIntDimen_fromUnitDimension() throws Throwable {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
        DisplayMetrics displayMetrics = textView.getResources().getDisplayMetrics();

        mRemoteViews.setIntDimen(R.id.remoteView_text, "setCompoundDrawablePadding",
                12f, COMPLEX_UNIT_DIP);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(resolveDimenSize(12f, COMPLEX_UNIT_DIP, displayMetrics),
                textView.getCompoundDrawablePadding());

        mRemoteViews.setIntDimen(R.id.remoteView_text, "setCompoundDrawablePadding",
                12f, TypedValue.COMPLEX_UNIT_SP);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(resolveDimenSize(12f, TypedValue.COMPLEX_UNIT_SP, displayMetrics),
                textView.getCompoundDrawablePadding());

        mRemoteViews.setIntDimen(R.id.remoteView_text, "setCompoundDrawablePadding",
                12f, TypedValue.COMPLEX_UNIT_PX);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(resolveDimenSize(12f, TypedValue.COMPLEX_UNIT_PX, displayMetrics),
                textView.getCompoundDrawablePadding());

        mRemoteViews.setIntDimen(R.id.remoteView_text, "setCompoundDrawablePadding",
                12f, 123456);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetIntDimenAttr() throws Throwable {
        mRemoteViews.setIntDimenAttr(R.id.remoteView_text, "setCompoundDrawablePadding",
                R.attr.themeDimension);
        applyNightModeThenApplyAndTest(false, () -> {
            TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
            assertEquals(resolveDimenSize(5.5123f, COMPLEX_UNIT_DIP,
                    textView.getResources().getDisplayMetrics()),
                    textView.getCompoundDrawablePadding());
        });

        applyNightModeThenApplyAndTest(true, () -> {
            TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
            assertEquals(resolveDimenSize(7.5123f, COMPLEX_UNIT_DIP,
                    textView.getResources().getDisplayMetrics()),
                    textView.getCompoundDrawablePadding());
        });

        // test that 0 resolves to 0
        mRemoteViews.setIntDimenAttr(R.id.remoteView_text, "setCompoundDrawablePadding", 0);
        applyNightModeThenApplyAndTest(false, () -> {
            TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
            assertEquals(0, textView.getCompoundDrawablePadding());
        });

        applyNightModeThenApplyAndTest(true, () -> {
            TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
            assertEquals(0, textView.getCompoundDrawablePadding());
        });

        mRemoteViews.setIntDimenAttr(R.id.remoteView_text, "setCompoundDrawablePadding",
                R.attr.themeColor);
        assertThrowsOnReapply(ActionException.class);
    }


    @Test
    public void testSetFloatDimen_fromResources() throws Throwable {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);

        mRemoteViews.setFloatDimen(R.id.remoteView_text, "setTextScaleX",
                R.dimen.remoteviews_float_dimen);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(textView.getResources().getDimension(R.dimen.remoteviews_float_dimen),
                textView.getTextScaleX(), 1e-4f);

        // test that passing 0 for the dimen sets 0f on the method.
        mRemoteViews.setFloatDimen(R.id.remoteView_text, "setTextScaleX", 0);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(0f, textView.getTextScaleX(), 1e-4f);

        mRemoteViews.setFloatDimen(R.id.remoteView_text, "setTextScaleX", R.color.testcolor1);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetFloatDimen_fromUnitDimension() throws Throwable {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
        DisplayMetrics displayMetrics = textView.getResources().getDisplayMetrics();

        mRemoteViews.setFloatDimen(R.id.remoteView_text, "setTextScaleX",
                3.5f, COMPLEX_UNIT_DIP);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(TypedValue.applyDimension(COMPLEX_UNIT_DIP, 3.5f, displayMetrics),
                textView.getTextScaleX(), 1e-4f);

        mRemoteViews.setFloatDimen(R.id.remoteView_text, "setTextScaleX",
                3.5f, TypedValue.COMPLEX_UNIT_SP);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 3.5f, displayMetrics),
                textView.getTextScaleX(), 1e-4f);

        mRemoteViews.setFloatDimen(R.id.remoteView_text, "setTextScaleX",
                3.5f, TypedValue.COMPLEX_UNIT_PX);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 3.5f, displayMetrics),
                textView.getTextScaleX(), 1e-4f);

        mRemoteViews.setFloatDimen(R.id.remoteView_text, "setTextScaleX",
                3.5f, 123456);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetFloatDimenAttr() throws Throwable {
        mRemoteViews.setFloatDimenAttr(R.id.remoteView_text, "setTextScaleX",
                R.attr.themeDimension);
        applyNightModeThenApplyAndTest(false, () -> {
            TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
            assertEquals(TypedValue.applyDimension(COMPLEX_UNIT_DIP, 5.5123f,
                    textView.getResources().getDisplayMetrics()), textView.getTextScaleX(), 1e-4f);
        });

        applyNightModeThenApplyAndTest(true, () -> {
            TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
            assertEquals(TypedValue.applyDimension(COMPLEX_UNIT_DIP, 7.5123f,
                    textView.getResources().getDisplayMetrics()), textView.getTextScaleX(), 1e-4f);
        });

        // test that 0 resolves to 0
        mRemoteViews.setFloatDimenAttr(R.id.remoteView_text, "setTextScaleX", 0);
        applyNightModeThenApplyAndTest(false, () -> {
            TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
            assertEquals(0f, textView.getTextScaleX(), 1e-4f);
        });

        applyNightModeThenApplyAndTest(true, () -> {
            TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
            assertEquals(0f, textView.getTextScaleX(), 1e-4f);
        });

        mRemoteViews.setFloatDimenAttr(R.id.remoteView_text, "setTextScaleX",
                R.attr.themeColor);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetColor() throws Throwable {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
        int expectedValue = mContext.getColor(R.color.testcolor1);

        mRemoteViews.setColor(R.id.remoteView_text, "setTextColor", R.color.testcolor1);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertSameColorStateList(ColorStateList.valueOf(expectedValue), textView.getTextColors());

        mRemoteViews.setColor(R.id.remoteView_text, "setTextColor", 0);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertSameColorStateList(ColorStateList.valueOf(0), textView.getTextColors());

        mRemoteViews.setColor(R.id.remoteView_text, "setTextColor", R.dimen.popup_row_height);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetColorAttr() throws Throwable {
        // Ensure the configuration is "light"
        mRemoteViews.setColorAttr(R.id.remoteView_text, "setTextColor", R.attr.themeColor);

        applyNightModeThenApplyAndTest(false, () -> {
            TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
            assertSameColorStateList(ColorStateList.valueOf(0x0f00ff00), textView.getTextColors());
        });

        // Switch to night mode
        applyNightModeThenApplyAndTest(true, () -> {
            TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
            assertSameColorStateList(ColorStateList.valueOf(0x0f00ffff), textView.getTextColors());
        });

        // Set to 0 and test that the colors are set to 0
        mRemoteViews.setColorAttr(R.id.remoteView_text, "setTextColor", 0);

        applyNightModeThenApplyAndTest(false, () -> {
            TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
            assertSameColorStateList(ColorStateList.valueOf(0), textView.getTextColors());
        });

        // Switch to night mode
        applyNightModeThenApplyAndTest(true, () -> {
            TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
            assertSameColorStateList(ColorStateList.valueOf(0), textView.getTextColors());
        });

        mRemoteViews.setColorAttr(R.id.remoteView_text, "setTextColor", R.attr.themeDimension);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetColorStateList() throws Throwable {
        ProgressBar progressBar = mResult.findViewById(R.id.remoteView_progress);

        ColorStateList tintList = new ColorStateList(
                new int[][] {{android.R.attr.state_checked}, {}},
                new int[] {Color.BLACK, Color.WHITE});
        mRemoteViews.setColorStateList(R.id.remoteView_progress, "setProgressTintList", tintList);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertSameColorStateList(tintList, progressBar.getProgressTintList());

        mRemoteViews.setColorStateList(R.id.remoteView_progress, "setProgressTintList", null);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertNull(progressBar.getProgressTintList());

        TextView textView = mResult.findViewById(R.id.remoteView_text);
        mRemoteViews.setColorStateList(R.id.remoteView_text, "setTextColor", tintList);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertSameColorStateList(tintList, textView.getTextColors());

        ColorStateList solid = ColorStateList.valueOf(Color.RED);
        mRemoteViews.setColorStateList(R.id.remoteView_text, "setBackgroundTintList", solid);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertSameColorStateList(solid, textView.getBackgroundTintList());
    }

    @Test
    public void testSetColorStateListAttr() throws Throwable {
        mRemoteViews.setColorStateListAttr(R.id.remoteView_progress, "setProgressTintList",
                R.attr.themeColor);
        applyNightModeThenApplyAndTest(false, () -> {
            ProgressBar progressBar = mResult.findViewById(R.id.remoteView_progress);
            assertSameColorStateList(ColorStateList.valueOf(0x0f00ff00),
                    progressBar.getProgressTintList());
        });

        applyNightModeThenApplyAndTest(true, () -> {
            ProgressBar progressBar = mResult.findViewById(R.id.remoteView_progress);
            assertSameColorStateList(ColorStateList.valueOf(0x0f00ffff),
                    progressBar.getProgressTintList());
        });

        mRemoteViews.setColorStateListAttr(R.id.remoteView_progress, "setProgressTintList", 0);
        applyNightModeThenApplyAndTest(false, () -> {
            ProgressBar progressBar = mResult.findViewById(R.id.remoteView_progress);
            assertSameColorStateList(null, progressBar.getProgressTintList());
        });

        applyNightModeThenApplyAndTest(true, () -> {
            ProgressBar progressBar = mResult.findViewById(R.id.remoteView_progress);
            assertSameColorStateList(null, progressBar.getProgressTintList());
        });

        mRemoteViews.setColorAttr(R.id.remoteView_progress,
                "setProgressTintList", R.attr.themeDimension);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetColorStateInt_nightMode() throws Throwable {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
        mRemoteViews.setColorInt(R.id.remoteView_text, "setTextColor", Color.BLACK, Color.WHITE);

        applyNightModeThenReapplyAndTest(
                false,
                () -> assertSameColorStateList(ColorStateList.valueOf(Color.BLACK),
                        textView.getTextColors())
        );
        applyNightModeThenReapplyAndTest(
                true,
                () -> assertSameColorStateList(ColorStateList.valueOf(Color.WHITE),
                        textView.getTextColors())
        );
    }

    @Test
    public void testSetColorStateList_fromResources() throws Throwable {
        ProgressBar progressBar = (ProgressBar) mResult.findViewById(R.id.remoteView_progress);
        ColorStateList expectedValue = mContext.getColorStateList(R.color.testcolorstatelist1);

        mRemoteViews.setColorStateList(R.id.remoteView_progress, "setProgressTintList",
                R.color.testcolorstatelist1);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertSameColorStateList(expectedValue, progressBar.getProgressTintList());

        mRemoteViews.setColorStateList(R.id.remoteView_progress, "setProgressTintList",
                R.color.testcolor1);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        expectedValue = mContext.getResources().getColorStateList(R.color.testcolor1,
                mContext.getTheme());
        assertSameColorStateList(expectedValue, progressBar.getProgressTintList());

        // 0 should resolve to null
        mRemoteViews.setColorStateList(R.id.remoteView_progress, "setProgressTintList", 0);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertSameColorStateList(null, progressBar.getProgressTintList());

        mRemoteViews.setColorStateList(R.id.remoteView_progress, "setProgressTintList",
                R.dimen.popup_row_height);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetColorStateList_nightMode() throws Throwable {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
        ColorStateList lightMode = ColorStateList.valueOf(Color.BLACK);
        ColorStateList darkMode = ColorStateList.valueOf(Color.WHITE);
        mRemoteViews.setColorStateList(R.id.remoteView_text, "setTextColor", lightMode, darkMode);

        applyNightModeThenReapplyAndTest(false,
                () -> assertSameColorStateList(lightMode, textView.getTextColors()));
        applyNightModeThenReapplyAndTest(true,
                () -> assertSameColorStateList(darkMode, textView.getTextColors()));
    }

    @Test
    public void testSetViewOutlinePreferredRadius() throws Throwable {
        View root = mResult.findViewById(R.id.remoteViews_good);
        DisplayMetrics displayMetrics = root.getResources().getDisplayMetrics();

        mRemoteViews.setViewOutlinePreferredRadius(
                R.id.remoteViews_good, 8, COMPLEX_UNIT_DIP);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(
                TypedValue.applyDimension(COMPLEX_UNIT_DIP, 8, displayMetrics),
                ((RemoteViews.RemoteViewOutlineProvider) root.getOutlineProvider()).getRadius(),
                0.1 /* delta */);

        mRemoteViews.setViewOutlinePreferredRadius(
                R.id.remoteViews_good, 16, COMPLEX_UNIT_PX);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(
                16,
                ((RemoteViews.RemoteViewOutlineProvider) root.getOutlineProvider()).getRadius(),
                0.1 /* delta */);
    }

    @Test
    public void testSetViewOutlinePreferredRadiusDimen() throws Throwable {
        View root = mResult.findViewById(R.id.remoteViews_good);

        mRemoteViews.setViewOutlinePreferredRadiusDimen(
                R.id.remoteViews_good, R.dimen.popup_row_height);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(
                root.getResources().getDimension(R.dimen.popup_row_height),
                ((RemoteViews.RemoteViewOutlineProvider) root.getOutlineProvider()).getRadius(),
                0.1 /* delta */);

        mRemoteViews.setViewOutlinePreferredRadiusDimen(
                R.id.remoteViews_good, 0);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(
                0,
                ((RemoteViews.RemoteViewOutlineProvider) root.getOutlineProvider()).getRadius(),
                0.1 /* delta */);
    }

    @Test
    public void testSetViewOutlinePreferredRadiusAttr() throws Throwable {
        mRemoteViews.setViewOutlinePreferredRadiusAttr(
                R.id.remoteViews_good, R.attr.themeDimension);

        applyNightModeThenApplyAndTest(false,
                () -> {
                    View root = mResult.findViewById(R.id.remoteViews_good);
                    assertEquals(
                            TypedValue.applyDimension(COMPLEX_UNIT_DIP, 5.5123f,
                                    root.getResources().getDisplayMetrics()),
                            ((RemoteViews.RemoteViewOutlineProvider)
                                    root.getOutlineProvider()).getRadius(),
                            0.1 /* delta */);
                });

        applyNightModeThenApplyAndTest(true,
                () -> {
                    View root = mResult.findViewById(R.id.remoteViews_good);
                    assertEquals(
                            TypedValue.applyDimension(COMPLEX_UNIT_DIP, 7.5123f,
                                    root.getResources().getDisplayMetrics()),
                            ((RemoteViews.RemoteViewOutlineProvider)
                                    root.getOutlineProvider()).getRadius(),
                            0.1 /* delta */);
                });

        mRemoteViews.setViewOutlinePreferredRadiusAttr(
                R.id.remoteViews_good, R.attr.themeColor);
        assertThrowsOnReapply(ActionException.class);
    }

    @Test
    public void testSetSwitchChecked() throws Throwable {
        Switch toggle = mResult.findViewById(R.id.remoteView_switch);

        mRemoteViews.setCompoundButtonChecked(R.id.remoteView_switch, true);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertTrue(toggle.isChecked());

        mRemoteViews.setCompoundButtonChecked(R.id.remoteView_switch, false);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertFalse(toggle.isChecked());
    }

    @Test
    public void testSetCheckBoxChecked() throws Throwable {
        CheckBox checkBox = mResult.findViewById(R.id.remoteView_checkBox);

        mRemoteViews.setCompoundButtonChecked(R.id.remoteView_checkBox, true);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertTrue(checkBox.isChecked());

        mRemoteViews.setCompoundButtonChecked(R.id.remoteView_checkBox, false);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertFalse(checkBox.isChecked());
    }

    @Test
    public void testSetRadioButtonChecked() throws Throwable {
        RadioButton radioButton = mResult.findViewById(R.id.remoteView_radioButton1);

        mRemoteViews.setCompoundButtonChecked(R.id.remoteView_radioButton1, true);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertTrue(radioButton.isChecked());

        mRemoteViews.setCompoundButtonChecked(R.id.remoteView_radioButton1, false);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertFalse(radioButton.isChecked());
    }

    @Test
    public void testSetRadioGroupChecked() throws Throwable {
        RadioGroup radioGroup = mResult.findViewById(R.id.remoteView_radioGroup);
        RadioButton button1 = mResult.findViewById(R.id.remoteView_radioButton1);
        RadioButton button2 = mResult.findViewById(R.id.remoteView_radioButton2);

        mRemoteViews.setRadioGroupChecked(R.id.remoteView_radioGroup, R.id.remoteView_radioButton1);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertTrue(button1.isChecked());
        assertFalse(button2.isChecked());

        mRemoteViews.setRadioGroupChecked(R.id.remoteView_radioGroup, R.id.remoteView_radioButton2);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertFalse(button1.isChecked());
        assertTrue(button2.isChecked());

        mRemoteViews.setRadioGroupChecked(R.id.remoteView_radioGroup, -1);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertFalse(button1.isChecked());
        assertFalse(button2.isChecked());
    }

    @Test
    public void testCanRecycleView() throws Throwable {
        mRemoteViews = new RemoteViews(PACKAGE_NAME, R.layout.remoteviews_textview,
                2 /* viewId */);

        mActivityRule.runOnUiThread(() -> {
            mResult = mRemoteViews.apply(mContext, null);
        });

        mRemoteViews = new RemoteViews(PACKAGE_NAME, R.layout.remoteviews_textview,
                3 /* viewId */);
        assertFalse(mRemoteViews.canRecycleView(mResult));

        mRemoteViews = new RemoteViews(PACKAGE_NAME, R.layout.remoteviews_textview);
        assertFalse(mRemoteViews.canRecycleView(mResult));

        mRemoteViews = new RemoteViews(PACKAGE_NAME, R.layout.remoteviews_textview,
                2 /* viewId */);
        assertTrue(mRemoteViews.canRecycleView(mResult));

        mRemoteViews = new RemoteViews(PACKAGE_NAME, R.layout.listview_layout, 2 /* viewId */);
        assertFalse(mRemoteViews.canRecycleView(mResult));

        assertFalse(mRemoteViews.canRecycleView(null));
        assertFalse(mRemoteViews.canRecycleView(new View(mContext)));
    }

    private void createSampleImage(File imagefile, int resid) throws IOException {
        try (InputStream source = mContext.getResources().openRawResource(resid);
             OutputStream target = new FileOutputStream(imagefile)) {

            byte[] buffer = new byte[1024];
            for (int len = source.read(buffer); len > 0; len = source.read(buffer)) {
                target.write(buffer, 0, len);
            }
        }
    }

    /**
     * Sets the night mode, reapplies the remote views, runs test, and then restores the previous
     * night mode.
     */
    private void applyNightModeThenReapplyAndTest(boolean nightMode, ThrowingRunnable test)
            throws Throwable {
        applyNightModeAndTest(nightMode, () -> mRemoteViews.reapply(mContext, mResult), test);
    }

    /**
     * Sets the night mode, reapplies the remote views, runs test, and then restores the previous
     * night mode.
     */
    private void applyNightModeThenApplyAndTest(
            boolean nightMode, ThrowingRunnable test) throws Throwable {
        applyNightModeAndTest(nightMode,
                () -> mResult = mRemoteViews.apply(mContext, null), test);
    }

    private void applyNightModeAndTest(
            boolean nightMode, Runnable uiThreadSetup, ThrowingRunnable test) throws Throwable {
        final String initialNightMode = changeNightMode(nightMode);
        try {
            mActivityRule.runOnUiThread(uiThreadSetup);
            test.run();
        } finally {
            runShellCommand("cmd uimode night " + initialNightMode);
        }
    }

    private static void assertMargins(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
            fail("View doesn't have MarginLayoutParams");
        }

        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) layoutParams;
        assertEquals("[left margin]", left, margins.leftMargin);
        assertEquals("[top margin]", top, margins.topMargin);
        assertEquals("[right margin]", right, margins.rightMargin);
        assertEquals("[bottom margin]", bottom, margins.bottomMargin);
    }

    private static int resolveDimenOffset(float value, int unit, DisplayMetrics displayMetrics) {
        return TypedValue.complexToDimensionPixelOffset(
                TypedValue.createComplexDimension(value, unit), displayMetrics);
    }

    private static int resolveDimenSize(float value, int unit, DisplayMetrics displayMetrics) {
        return TypedValue.complexToDimensionPixelSize(
                TypedValue.createComplexDimension(value, unit), displayMetrics);
    }

    private static final class MockBroadcastReceiver extends BroadcastReceiver {

        Intent mIntent;

        private CountDownLatch mCountDownLatch;

        @Override
        public synchronized void onReceive(Context context, Intent intent) {
            mIntent = intent;
            if (mCountDownLatch != null) {
                mCountDownLatch.countDown();
                mCountDownLatch = null;
            }
        }

        /** Waits for an intent to be received and returns it. */
        public Intent awaitIntent() {
            CountDownLatch countDownLatch;
            synchronized (this) {
                // If we already have an intent, don't wait and just return it now.
                if (mIntent != null) return getIntentAndResetLocked();

                countDownLatch = new CountDownLatch(1);
                mCountDownLatch = countDownLatch;
            }

            try {
                // Note: if the latch already counted down, this will return true immediately.
                countDownLatch.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            synchronized (this) {
                if (mIntent == null) {
                    Assert.fail("Expected to receive a broadcast within 20 seconds");
                }

                return getIntentAndResetLocked();
            }
        }

        private Intent getIntentAndResetLocked() {
            final Intent intent = mIntent;
            mIntent = null;
            return intent;
        }
    }

    private void assertSameColorStateList(ColorStateList expected, ColorStateList actual) {
        assertEquals(expected == null ? null : expected.toString(),
                actual == null ? null : actual.toString());
    }

    private <T extends Throwable> void assertThrowsOnReapply(Class<T> klass) throws Throwable {
        assertThrows(klass,
                () -> mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult)));
    }

    private String getCurrentNightMode() {
        final String nightModeText = runShellCommand("cmd uimode night");
        final String[] nightModeSplit = nightModeText.split(":");
        if (nightModeSplit.length != 2) {
            fail("Failed to get initial night mode value from " + nightModeText);
        }
        return nightModeSplit[1].trim();
    }

    // Change the night mode and return the previous mode
    private String changeNightMode(boolean nightMode) throws Exception {
        String previousMode = getCurrentNightMode();
        String wantedNightMode = nightMode ? "yes" : "no";
        runShellCommand("cmd uimode night " + wantedNightMode);
        waitUntil(
                /* message= */ "Night mode did not change to " + wantedNightMode,
                /* timeoutSecond= */ 5,
                /* predicate= */ () -> wantedNightMode.equals(getCurrentNightMode())
        );
        return previousMode;
    }

    private static RemoteViews parcelAndUnparcel(RemoteViews views) {
        return parcelAndUnparcel(views, /* flags= */ 0);
    }

    /**
     * Returns the result of writing {@code views} to a {@link Parcel} and then creating a new
     * {@link RemoteViews} from the parcel.
     */
    private static RemoteViews parcelAndUnparcel(RemoteViews views, int flags) {
        return parcelAndRun(views, flags, RemoteViews.CREATOR::createFromParcel);
    }

    /** Returns the data size from writing {@code parcelable} to a {@link Parcel}. */
    private static int getParcelledSize(Parcelable parcelable) {
        return parcelAndRun(parcelable, /* flags= */ 0, Parcel::dataSize);
    }

    private static <T> T parcelAndRun(
            Parcelable parcelable,
            int flags,
            Function<Parcel, T> function) {
        Parcel parcel = Parcel.obtain();
        parcelable.writeToParcel(parcel, flags);
        parcel.setDataPosition(0);
        try {
            return function.apply(parcel);
        } finally {
            parcel.recycle();
        }
    }

    private static void assertLessThan(int expected, int actual) {
        if (actual >= expected) {
            Assert.fail("Expected to be less than " + expected + ", but was " + actual);
        }
    }
}
