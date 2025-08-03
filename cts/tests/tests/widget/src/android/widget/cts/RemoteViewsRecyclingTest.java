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

package android.widget.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.test.InstrumentationRegistry;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;

/**
 * Test {@link RemoteViews} recycling when adding views dynamically.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class RemoteViewsRecyclingTest {
    private static final String PACKAGE_NAME = "android.widget.cts";
    private static final int LAYOUT_ID = 1;
    private static final int FIRST_TEXT_ID = 2;
    private static final int MIDDLE_TEXT_ID = 3;
    private static final int AFTER_TEXT_ID = 4;
    private static final int END_TEXT1_ID = 5;
    private static final int END_TEXT2_ID = 6;

    @Rule
    public ActivityTestRule<RemoteViewsCtsActivity> mActivityRule =
            new ActivityTestRule<>(RemoteViewsCtsActivity.class);

    @Rule
    public ExpectedException mExpectedException = ExpectedException.none();

    private Instrumentation mInstrumentation;

    private Context mContext;

    private View mResult;

    private Executor mExecutor = runnable -> runnable.run();

    @UiThreadTest
    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
    }

    private void recycleWhenIdentical(boolean async) throws Throwable {
        RemoteViews rv = createRemoteViews(R.layout.remoteviews_recycle);
        rv.removeAllViews(R.id.remoteViews_recycle_container);
        rv.addStableView(R.id.remoteViews_recycle_container,
                createRemoteViews(R.layout.remoteviews_textview, View.NO_ID), FIRST_TEXT_ID
        );
        rv.addView(R.id.remoteViews_recycle_container,
                createRemoteViews(R.layout.remoteviews_textview, View.NO_ID));
        rv.addStableView(R.id.remoteViews_recycle_container,
                createRemoteViews(R.layout.remoteviews_textview), AFTER_TEXT_ID
        );
        applyRemoteViews(rv);
        ViewGroup container = mResult.findViewById(R.id.remoteViews_recycle_container);
        View text1 = container.getChildAt(0);
        View text2 = container.getChildAt(1);
        View text3 = container.getChildAt(2);

        reapplyRemoteViews(rv, async);

        container = mResult.findViewById(R.id.remoteViews_recycle_container);
        assertNotNull(container);
        assertSame("TextViews with stable id FIRST_TEXT_ID", text1, container.getChildAt(0));
        assertNotSame("TextViews without stable id", text2, container.getChildAt(1));
        assertSame("TextViews with stable id AFTER_TEXT_ID", text3, container.getChildAt(2));
        assertEquals(3, container.getChildCount());
    }

    @Test
    public void recycleWhenIdenticalSync() throws Throwable {
        recycleWhenIdentical(false /* async */);
    }

    @Test
    public void recycleWhenIdenticalAsync() throws Throwable {
        recycleWhenIdentical(false /* async */);
    }

    private void doesntRecycleWhenNotAskingForRecycling(boolean async) throws Throwable {
        RemoteViews rv = createRemoteViews(R.layout.remoteviews_recycle);
        rv.removeAllViews(R.id.remoteViews_recycle_container);
        addTextsWithoutStableIds(rv, R.id.remoteViews_recycle_container, false /* insertInMiddle */,
                false /* addAtEnd */);
        applyRemoteViews(rv);
        ViewGroup container = mResult.findViewById(R.id.remoteViews_recycle_container);
        View text1 = container.getChildAt(0);
        View text2 = container.getChildAt(1);

        reapplyRemoteViews(rv, async);

        container = mResult.findViewById(R.id.remoteViews_recycle_container);
        assertNotNull(container);
        assertNotSame("TextViews with stable id FIRST_TEXT_ID", text1, container.getChildAt(0));
        assertNotSame("TextViews with stable id AFTER_TEXT_ID", text2, container.getChildAt(1));
        assertEquals(2, container.getChildCount());
    }

    @Test
    public void doesntRecycleWhenNotAskingForRecyclingSync() throws Throwable {
        doesntRecycleWhenNotAskingForRecycling(false /* async */);
    }

    @Test
    public void doesntRecycleWhenNotAskingForRecyclingAsync() throws Throwable {
        doesntRecycleWhenNotAskingForRecycling(true /* async */);
    }

    private void recycleWhenInsertView(boolean async) throws Throwable {
        RemoteViews rv = createRemoteViews(R.layout.remoteviews_recycle);
        rv.removeAllViews(R.id.remoteViews_recycle_container);
        addTextsWithStableIds(rv, R.id.remoteViews_recycle_container, false /* insertInMiddle */,
                false /* addAtEnd */);
        applyRemoteViews(rv);
        ViewGroup container = mResult.findViewById(R.id.remoteViews_recycle_container);
        View text1 = container.getChildAt(0);
        View text2 = container.getChildAt(1);

        rv = createRemoteViews(R.layout.remoteviews_recycle);
        rv.removeAllViews(R.id.remoteViews_recycle_container);
        addTextsWithStableIds(rv, R.id.remoteViews_recycle_container, true /* insertInMiddle */,
                false /* addAtEnd */);
        reapplyRemoteViews(rv, async);

        container = mResult.findViewById(R.id.remoteViews_recycle_container);
        assertNotNull(container);
        assertNotNull(container.getChildAt(1));
        assertSame("TextViews with stable id FIRST_TEXT_ID", text1, container.getChildAt(0));
        assertSame("TextViews with stable id AFTER_TEXT_ID", text2, container.getChildAt(2));
        assertNotNull(container);
        assertEquals(3, container.getChildCount());
    }

    @Test
    public void recycleWhenInsertViewSync() throws Throwable {
        recycleWhenInsertView(false /* async */);
    }

    @Test
    public void recycleWhenInsertViewAsync() throws Throwable {
        recycleWhenInsertView(true /* async */);
    }

    private void recycleWhenRemovingMiddleView(boolean async) throws Throwable {
        RemoteViews rv = createRemoteViews(R.layout.remoteviews_recycle);
        rv.removeAllViews(R.id.remoteViews_recycle_container);
        addTextsWithStableIds(rv, R.id.remoteViews_recycle_container, true /* insertInMiddle */,
                false /* addAtEnd */);
        applyRemoteViews(rv);
        ViewGroup container = mResult.findViewById(R.id.remoteViews_recycle_container);
        View text1 = container.getChildAt(0);
        View text2 = container.getChildAt(2);

        rv = createRemoteViews(R.layout.remoteviews_recycle);
        rv.removeAllViews(R.id.remoteViews_recycle_container);
        addTextsWithStableIds(rv, R.id.remoteViews_recycle_container, false /* insertInMiddle */,
                false /* addAtEnd */);
        reapplyRemoteViews(rv, async);

        container = mResult.findViewById(R.id.remoteViews_recycle_container);
        assertNotNull(container);
        assertSame("TextViews with stable id FIRST_TEXT_ID", text1, container.getChildAt(0));
        assertSame("TextViews with stable id AFTER_TEXT_ID", text2, container.getChildAt(1));
        assertEquals(2, container.getChildCount());
    }

    @Test
    public void recycleWhenRemovingMiddleViewSync() throws Throwable {
        recycleWhenRemovingMiddleView(false /* async */);
    }

    @Test
    public void recycleWhenRemovingMiddleViewAsync() throws Throwable {
        recycleWhenRemovingMiddleView(true /* async */);
    }

    private void recycleWhenAddingAtEnd(boolean async) throws Throwable {
        RemoteViews rv = createRemoteViews(R.layout.remoteviews_recycle);
        rv.removeAllViews(R.id.remoteViews_recycle_container);
        addTextsWithStableIds(rv, R.id.remoteViews_recycle_container, false /* insertInMiddle */,
                false /* addAtEnd */);
        applyRemoteViews(rv);
        ViewGroup container = mResult.findViewById(R.id.remoteViews_recycle_container);
        View text1 = container.getChildAt(0);
        View text2 = container.getChildAt(1);

        rv = createRemoteViews(R.layout.remoteviews_recycle);
        rv.removeAllViews(R.id.remoteViews_recycle_container);
        addTextsWithStableIds(rv, R.id.remoteViews_recycle_container, false /* insertInMiddle */,
                true /* addAtEnd */);
        reapplyRemoteViews(rv, async);

        container = mResult.findViewById(R.id.remoteViews_recycle_container);
        assertNotNull(container);
        assertSame("TextViews with stable id FIRST_TEXT_ID", text1, container.getChildAt(0));
        assertSame("TextViews with stable id AFTER_TEXT_ID", text2, container.getChildAt(1));
        assertEquals(4, container.getChildCount());
    }

    @Test
    public void recycleWhenAddingAtEndSync() throws Throwable {
        recycleWhenAddingAtEnd(false /* async */);
    }

    @Test
    public void recycleWhenAddingAtEndAsync() throws Throwable {
        recycleWhenAddingAtEnd(true /* async */);
    }

    private void recycleWhenRemovingFromEnd(boolean async) throws Throwable {
        RemoteViews rv = createRemoteViews(R.layout.remoteviews_recycle);
        rv.removeAllViews(R.id.remoteViews_recycle_container);
        addTextsWithStableIds(rv, R.id.remoteViews_recycle_container, false /* insertInMiddle */,
                true /* addAtEnd */);
        applyRemoteViews(rv);
        ViewGroup container = mResult.findViewById(R.id.remoteViews_recycle_container);
        View text1 = container.getChildAt(0);
        View text2 = container.getChildAt(1);

        rv = createRemoteViews(R.layout.remoteviews_recycle);
        rv.removeAllViews(R.id.remoteViews_recycle_container);
        addTextsWithStableIds(rv, R.id.remoteViews_recycle_container, false /* insertInMiddle */,
                false /* addAtEnd */);
        reapplyRemoteViews(rv, async);

        container = mResult.findViewById(R.id.remoteViews_recycle_container);
        assertNotNull(container);
        assertSame("TextViews with stable id FIRST_TEXT_ID", text1, container.getChildAt(0));
        assertSame("TextViews with stable id AFTER_TEXT_ID", text2, container.getChildAt(1));
        assertEquals(2, container.getChildCount());
    }

    @Test
    public void recycleWhenRemovingFromEndSync() throws Throwable {
        recycleWhenRemovingFromEnd(false /* async */);
    }

    @Test
    public void recycleWhenRemovingFromEndAsync() throws Throwable {
        recycleWhenRemovingFromEnd(true /* async */);
    }

    private void doesntRecycleWhenLayoutDoesntMatch(boolean async) throws Throwable {
        RemoteViews rv = createRemoteViews(R.layout.remoteviews_recycle);
        rv.removeAllViews(R.id.remoteViews_recycle_container);
        rv.addStableView(R.id.remoteViews_recycle_container,
                createRemoteViews(R.layout.remoteviews_textview), FIRST_TEXT_ID);
        applyRemoteViews(rv);
        ViewGroup container = mResult.findViewById(R.id.remoteViews_recycle_container);
        View text = container.getChildAt(0);

        rv = createRemoteViews(R.layout.remoteviews_recycle);
        rv.removeAllViews(R.id.remoteViews_recycle_container);
        rv.addStableView(R.id.remoteViews_recycle_container,
                createRemoteViews(R.layout.remoteviews_container), FIRST_TEXT_ID);
        reapplyRemoteViews(rv, async);

        container = mResult.findViewById(R.id.remoteViews_recycle_container);
        assertNotNull(container);
        assertNotNull(container.getChildAt(0));
        assertNotSame("TextViews", text, container.getChildAt(0));
        assertEquals(1, container.getChildCount());
    }

    @Test
    public void doesntRecycleWhenLayoutDoesntMatchSync() throws Throwable {
        doesntRecycleWhenLayoutDoesntMatch(false /* async */);
    }

    @Test
    public void doesntRecycleWhenLayoutDoesntMatchAsync() throws Throwable {
        doesntRecycleWhenLayoutDoesntMatch(true /* async */);
    }

    private void doesntRecycleWhenViewIdDoesntMatch(boolean async) throws Throwable {
        RemoteViews rv = createRemoteViews(R.layout.remoteviews_recycle);
        rv.removeAllViews(R.id.remoteViews_recycle_container);
        RemoteViews childView = createRemoteViews(R.layout.remoteviews_textview, 2345);
        rv.addStableView(R.id.remoteViews_recycle_container, childView, FIRST_TEXT_ID);
        applyRemoteViews(rv);
        ViewGroup container = mResult.findViewById(R.id.remoteViews_recycle_container);
        View text = container.getChildAt(0);

        rv = createRemoteViews(R.layout.remoteviews_recycle);
        rv.removeAllViews(R.id.remoteViews_recycle_container);
        childView = createRemoteViews(R.layout.remoteviews_textview, 3456);
        rv.addStableView(R.id.remoteViews_recycle_container, childView, FIRST_TEXT_ID);
        applyRemoteViews(rv);
        reapplyRemoteViews(rv, async);

        container = mResult.findViewById(R.id.remoteViews_recycle_container);
        assertNotSame("TextViews", text, container.getChildAt(0));
    }

    @Test
    public void doesntRecycleWhenViewIdDoesntMatchSync() throws Throwable {
        doesntRecycleWhenViewIdDoesntMatch(false /* async */);
    }

    @Test
    public void doesntRecycleWhenViewIdDoesntMatchAsync() throws Throwable {
        doesntRecycleWhenViewIdDoesntMatch(true /* async */);
    }

    private void recycleWhenViewIdDoesntMatchFailsInMultipleLayout(boolean async) throws Throwable {
        RemoteViews childRv = createRemoteViews(R.layout.remoteviews_recycle, 2);
        RemoteViews rv = new RemoteViews(childRv, childRv);
        applyRemoteViews(rv);

        RemoteViews childRv2 = createRemoteViews(R.layout.remoteviews_recycle, 3);
        RemoteViews rv2 = new RemoteViews(childRv2, childRv2);

        try {
            reapplyRemoteViews(rv2, async);
        } catch (RuntimeException ex) {
            return; // success
        } catch (Throwable t) {
            fail("Excepted a RuntimeException, received " + t.toString());
        }
        fail("Excepted a RuntimeException, no exception received.");
    }

    @Test
    public void recycleWhenViewIdDoesntMatchFailsInMultipleLayoutSync() throws Throwable {
        recycleWhenViewIdDoesntMatchFailsInMultipleLayout(false /* async */);
    }

    @Test
    public void recycleWhenViewIdDoesntMatchFailsInMultipleLayoutAsync() throws Throwable {
        recycleWhenViewIdDoesntMatchFailsInMultipleLayout(true /* async */);
    }

    private void recycleWhenViewIdDoesntMatchFailsInSimpleLayout(boolean async) throws Throwable {
        RemoteViews rv = createRemoteViews(R.layout.remoteviews_recycle, 2);
        applyRemoteViews(rv);

        RemoteViews rv2 = createRemoteViews(R.layout.remoteviews_recycle, 3);
        try {
            reapplyRemoteViews(rv2, async);
        } catch (RuntimeException ex) {
            return; // success
        } catch (Throwable t) {
            fail("Excepted a RuntimeException, received " + t.toString());
        }
        fail("Excepted a RuntimeException, no exception received.");
    }

    @Test
    public void recycleWhenViewIdDoesntMatchFailsInSimpleLayoutSync() throws Throwable {
        recycleWhenViewIdDoesntMatchFailsInSimpleLayout(false /* async */);
    }

    @Test
    public void recycleWhenViewIdDoesntMatchFailsInSimpleLayoutAsync() throws Throwable {
        recycleWhenViewIdDoesntMatchFailsInSimpleLayout(true /* async */);
    }

    private void recycleWhenLayoutIdDoesntMatchSucceedsInSimpleLayout(boolean async)
            throws Throwable {
        RemoteViews rv = createRemoteViews(R.layout.remoteviews_recycle);
        applyRemoteViews(rv);

        RemoteViews rv2 = createRemoteViews(R.layout.remoteviews_textview);
        rv2.setTextViewText(R.id.remoteViews_recycle_static, "New text");
        reapplyRemoteViews(rv2, async);

        TextView textView = mResult.findViewById(R.id.remoteViews_recycle_static);
        assertEquals("New text", textView.getText());
    }

    @Test
    public void recycleWhenLayoutIdDoesntMatchSucceedsInSimpleLayoutSync() throws Throwable {
        recycleWhenLayoutIdDoesntMatchSucceedsInSimpleLayout(false);
    }

    @Test
    public void recycleWhenLayoutIdDoesntMatchSucceedsInSimpleLayoutAsync() throws Throwable {
        recycleWhenLayoutIdDoesntMatchSucceedsInSimpleLayout(true);
    }

    private void recycleWhenRemovingFromEndAndInsertInMiddleAtManyLevels(boolean async)
            throws Throwable {
        RemoteViews rv = createRemoteViews(R.layout.remoteviews_recycle);
        rv.removeAllViews(R.id.remoteViews_recycle_container);
        rv.addStableView(R.id.remoteViews_recycle_container,
                createRemoteViews(R.layout.remoteviews_container), LAYOUT_ID);
        addTextsWithStableIds(rv, R.id.remoteViews_recycle_container, false /* insertInMiddle */,
                true /* addAtEnd */);
        rv.removeAllViews(R.id.remoteView_container);
        addTextsWithStableIds(rv, R.id.remoteView_container, false /* insertInMiddle */,
                true /* addAtEnd */);
        applyRemoteViews(rv);
        ViewGroup container = mResult.findViewById(R.id.remoteViews_recycle_container);
        ViewGroup container2 = (ViewGroup) container.getChildAt(0);
        View text1 = container.getChildAt(1);
        View text2 = container.getChildAt(2);
        View text3 = container2.getChildAt(0);
        View text4 = container2.getChildAt(1);

        rv = createRemoteViews(R.layout.remoteviews_recycle);
        rv.removeAllViews(R.id.remoteViews_recycle_container);
        rv.addStableView(R.id.remoteViews_recycle_container,
                createRemoteViews(R.layout.remoteviews_container), LAYOUT_ID);
        addTextsWithStableIds(rv, R.id.remoteViews_recycle_container, true /* insertInMiddle */,
                false /* addAtEnd */);
        rv.removeAllViews(R.id.remoteView_container);
        addTextsWithStableIds(rv, R.id.remoteView_container, true /* insertInMiddle */,
                false /* addAtEnd */);
        reapplyRemoteViews(rv, async);

        assertNotNull(container);
        assertNotNull(container2);
        assertSame("ViewGroup with stable id LAYOUT_ID", container2, container.getChildAt(0));
        assertSame("TextViews with stable id FIRST_TEXT_ID", text1, container.getChildAt(1));
        assertSame("TextViews with stable id AFTER_TEXT_ID", text2, container.getChildAt(3));
        assertSame("TextViews with stable id FIRST_TEXT_ID", text3, container2.getChildAt(0));
        assertSame("TextViews with stable id AFTER_TEXT_ID", text4, container2.getChildAt(2));
        assertEquals(4, container.getChildCount());
        assertEquals(3, container2.getChildCount());
    }

    @Test
    public void recycleWhenRemovingFromEndAndInsertInMiddleAtManyLevelsSync() throws Throwable {
        recycleWhenRemovingFromEndAndInsertInMiddleAtManyLevels(false /* async */);
    }

    @Test
    public void recycleWhenRemovingFromEndAndInsertInMiddleAtManyLevelsAsync() throws Throwable {
        recycleWhenRemovingFromEndAndInsertInMiddleAtManyLevels(true /* async */);
    }

    private void applyRemoteViews(RemoteViews remoteViews) throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mResult = remoteViews.apply(mContext, null);
            // Add our host view to the activity behind this test. This is similar to how launchers
            // add widgets to the on-screen UI.
            ViewGroup root = mActivityRule.getActivity().findViewById(R.id.remoteView_host);
            root.removeAllViews();
            FrameLayout.MarginLayoutParams lp = new FrameLayout.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            mResult.setLayoutParams(lp);

            root.addView(mResult);
        });
    }

    private void reapplyRemoteViews(RemoteViews remoteViews, boolean async) throws Throwable {
        if (async) {
            mActivityRule.runOnUiThread(
                    () -> remoteViews.reapplyAsync(mContext, mResult, null, null));
            Thread.sleep(100); // Wait for the UI to be updated
        } else {
            mActivityRule.runOnUiThread(() -> remoteViews.reapply(mContext, mResult));
        }
    }

    private RemoteViews createRemoteViews(int layout) {
        return new RemoteViews(PACKAGE_NAME, layout);
    }

    private RemoteViews createRemoteViews(int layout, int viewId) {
        return new RemoteViews(PACKAGE_NAME, layout, viewId);
    }

    private void addTextsWithStableIds(RemoteViews views, int layoutId, boolean insertInMiddle,
            boolean addAtEnd) {
        views.addStableView(layoutId, createRemoteViews(R.layout.remoteviews_textview),
                FIRST_TEXT_ID);
        if (insertInMiddle) {
            views.addStableView(layoutId,
                    createRemoteViews(R.layout.remoteviews_textview, View.NO_ID),
                    MIDDLE_TEXT_ID);
        }
        views.addStableView(layoutId, createRemoteViews(R.layout.remoteviews_textview),
                AFTER_TEXT_ID);
        if (addAtEnd) {
            views.addStableView(layoutId,
                    createRemoteViews(R.layout.remoteviews_textview, View.NO_ID),
                    END_TEXT1_ID);
            views.addStableView(layoutId,
                    createRemoteViews(R.layout.remoteviews_textview, View.NO_ID),
                    END_TEXT2_ID);
        }
    }

    private void addTextsWithoutStableIds(RemoteViews views, int layoutId, boolean insertInMiddle,
            boolean addAtEnd) {
        views.addView(layoutId, createRemoteViews(R.layout.remoteviews_textview));
        if (insertInMiddle) {
            views.addView(layoutId, createRemoteViews(R.layout.remoteviews_textview));
        }
        views.addView(layoutId, createRemoteViews(R.layout.remoteviews_textview));
        if (addAtEnd) {
            views.addView(layoutId, createRemoteViews(R.layout.remoteviews_textview));
            views.addView(layoutId, createRemoteViews(R.layout.remoteviews_textview));
        }
    }
}
