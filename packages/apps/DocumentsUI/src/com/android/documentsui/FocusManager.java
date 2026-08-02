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

package com.android.documentsui;

import static com.android.documentsui.base.DocumentInfo.getCursorString;
import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static androidx.core.util.Preconditions.checkNotNull;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import android.database.Cursor;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.DocumentsContract.Document;
import android.text.Editable;
import android.text.Spannable;
import android.text.method.KeyListener;
import android.text.method.TextKeyListener;
import android.text.method.TextKeyListener.Capitalize;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.selection.FocusDelegate;
import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.documentsui.Model.Update;
import com.android.documentsui.base.EventListener;
import com.android.documentsui.base.Events;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.Procedure;
import com.android.documentsui.dirlist.DocumentHolder;
import com.android.documentsui.dirlist.DocumentsAdapter;
import com.android.documentsui.dirlist.FocusHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * The implementation to handle focus and keyboard driven navigation.
 */
public final class FocusManager extends FocusDelegate<String> implements FocusHandler {
    private static final String TAG = "FocusManager";

    private final ContentScope mScope = new ContentScope();

    private final Features mFeatures;
    private final SelectionTracker<String> mSelectionMgr;
    private final DrawerController mDrawer;
    private final Procedure mRootsFocuser;
    private final TitleSearchHelper mSearchHelper;

    private boolean mNavDrawerHasFocus;

    public FocusManager(
            Features features,
            SelectionTracker<String> selectionMgr,
            DrawerController drawer,
            Procedure rootsFocuser,
            @ColorRes int color) {

        mFeatures = checkNotNull(features);
        mSelectionMgr = selectionMgr;
        mDrawer = drawer;
        mRootsFocuser = rootsFocuser;

        mSearchHelper = new TitleSearchHelper(color);
    }

    @Override
    public boolean advanceFocusArea() {
        // Toggles focus between the roots (storage) sidebar and the directory list. Historically
        // this was only used on pre-O devices (O+ has built-in keyboard navigation), but it is
        // also driven directly by the gamepad L1 shortcut regardless of that feature flag.
        boolean focusChanged = false;
        if (mNavDrawerHasFocus) {
            mDrawer.setOpen(false);
            focusChanged = focusDirectoryList();
        } else {
            mDrawer.setOpen(true);
            focusChanged = mRootsFocuser.run();
        }

        if (focusChanged) {
            mNavDrawerHasFocus = !mNavDrawerHasFocus;
            return true;
        }

        return false;
    }

    @Override
    public boolean handleKey(DocumentHolder doc, int keyCode, KeyEvent event) {
        // Search helper gets first crack, for doing type-to-focus.
        if (mSearchHelper.handleKey(doc, keyCode, event)) {
            return true;
        }

        if (Events.isNavigationKeyCode(keyCode)) {
            // Find the target item and focus it.
            int endPos = findTargetPosition(doc.itemView, keyCode, event);

            if (endPos != RecyclerView.NO_POSITION) {
                focusItem(endPos);
            }
            // Swallow all navigation keystrokes. Otherwise they go to the app's global
            // key-handler, which will route them back to the DF and cause focus to be reset.
            return true;
        }
        return false;
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        // Remember focus events on items.
        if (hasFocus && mScope.isValid() && v.getParent() == mScope.view) {
            mScope.lastFocusPosition = mScope.view.getChildAdapterPosition(v);
        }
    }

    @Override
    public boolean focusDirectoryList() {
        if (!mScope.isValid() || mScope.adapter.getItemCount() == 0) {
            if (DEBUG) {
                Log.v(TAG, "Nothing to focus.");
            }
            return false;
        }

        // If there's a selection going on, we don't want to grant user the ability to focus
        // on any individfocusSomethingual item to prevent ambiguity in operations (Cut selection
        // vs. Cut focused
        // item)
        if (mSelectionMgr.hasSelection()) {
            if (DEBUG) {
                Log.v(TAG, "Existing selection found. No focus will be done.");
            }
            return false;
        }

        final int focusPos = (mScope.lastFocusPosition != RecyclerView.NO_POSITION)
                ? mScope.lastFocusPosition
                : mScope.layout.findFirstVisibleItemPosition();
        if (focusPos == RecyclerView.NO_POSITION) {
            return false;
        }

        focusItem(focusPos);
        return true;
    }

    /*
     * Attempts to reset focus on the item corresponding to {@code mPendingFocusId} if it exists and
     * has a valid position in the adapter. It then automatically resets {@code mPendingFocusId}.
     */
    @Override
    public void onLayoutCompleted() {
        if (mScope.pendingFocusId == null) {
            return;
        }

        int pos = mScope.adapter.getStableIds().indexOf(mScope.pendingFocusId);
        if (pos != -1) {
            focusItem(pos);
        }
        mScope.pendingFocusId = null;
    }

    @Override
    public void clearFocus() {
        if (mScope.isValid()) {
            mScope.view.clearFocus();
        }
    }

    /*
     * Attempts to put focus on the document associated with the given modelId. If item does not
     * exist yet in the layout, this sets a pending modelId to be used when {@code
     * #applyPendingFocus()} is called next time.
     */
    @Override
    public void focusDocument(String modelId) {
        if (!mScope.isValid()) {
            if (DEBUG) {
                Log.v(TAG, "Invalid mScope. No focus will be done.");
            }
            return;
        }
        int pos = mScope.adapter.getAdapterPosition(modelId);
        if (pos != -1 && mScope.view.findViewHolderForAdapterPosition(pos) != null) {
            focusItem(pos);
        } else {
            mScope.pendingFocusId = modelId;
        }
    }

    @Override
    public void focusItem(ItemDetails<String> item) {
        focusDocument(item.getSelectionKey());
    }

    @Override
    public int getFocusedPosition() {
        return mScope.lastFocusPosition;
    }

    @Override
    public boolean hasFocusedItem() {
        return mScope.lastFocusPosition != RecyclerView.NO_POSITION;
    }

    @Override
    public @Nullable String getFocusModelId() {
        if (mScope.lastFocusPosition != RecyclerView.NO_POSITION) {
            DocumentHolder holder = (DocumentHolder) mScope.view
                    .findViewHolderForAdapterPosition(mScope.lastFocusPosition);
            return holder.getModelId();
        }
        return null;
    }

    /**
     * Finds the destination position where the focus should land for a given navigation event.
     *
     * @param view The view that received the event.
     * @param keyCode The key code for the event.
     * @param event
     * @return The adapter position of the destination item. Could be RecyclerView.NO_POSITION.
     */
    private int findTargetPosition(View view, int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MOVE_HOME:
                return 0;
            case KeyEvent.KEYCODE_MOVE_END:
                return mScope.adapter.getItemCount() - 1;
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return findPagedTargetPosition(view, keyCode, event);
        }

        // Directional navigation is computed deterministically from adapter positions and the
        // grid's span layout, NOT from a geometric focus search. A geometric focusSearch can
        // silently fail (returning nothing) whenever the destination view isn't currently laid
        // out or when tiles differ in size - e.g. the tall image thumbnails shown for a folder of
        // photos - which left focus "stuck", unable to move in some directions. Working from
        // positions keeps all four directions reliable regardless of folder contents or scroll
        // state; focusItem() then scrolls the destination into view if it isn't laid out yet.
        int currentPosition = mScope.view.getChildAdapterPosition(view);
        if (currentPosition == RecyclerView.NO_POSITION) {
            return RecyclerView.NO_POSITION;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return findVerticalTargetPosition(currentPosition, false);
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return findVerticalTargetPosition(currentPosition, true);
            case KeyEvent.KEYCODE_DPAD_LEFT:
                // Left/right only apply in grid mode; they step to the adjacent focusable item in
                // linear order (snaking across rows), so every item stays reachable.
                return inGridMode()
                        ? findHorizontalTargetPosition(currentPosition, false)
                        : RecyclerView.NO_POSITION;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return inGridMode()
                        ? findHorizontalTargetPosition(currentPosition, true)
                        : RecyclerView.NO_POSITION;
        }

        return RecyclerView.NO_POSITION;
    }

    /**
     * Computes the adapter position that horizontal (left/right) navigation should move focus to:
     * the nearest focusable item before/after the current one in linear order, skipping
     * non-focusable dividers. This snakes across row boundaries so every item is reachable, and
     * unlike a geometric focus search it never gets stuck when the neighbour is off-screen or a
     * different size (tall image tiles).
     */
    private int findHorizontalTargetPosition(int current, boolean forward) {
        final int itemCount = mScope.adapter.getItemCount();
        final int step = forward ? 1 : -1;
        for (int pos = current + step; pos >= 0 && pos < itemCount; pos += step) {
            if (isFocusablePosition(pos)) {
                return pos;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    /**
     * Computes the adapter position that vertical (up/down) navigation should move focus to.
     *
     * <p>The grid can contain full-width, non-focusable rows (section breaks, header/info
     * messages) and, in photo-picking mode, tiles that span more than one column. To move
     * predictably we reason in terms of span groups (visual rows) and span indices (columns)
     * reported by the {@link GridLayoutManager.SpanSizeLookup}, skipping non-focusable items.
     * This keeps up/down working regardless of folder contents or scroll state; the subsequent
     * {@link #focusItem(int)} call scrolls the destination into view if it is not laid out yet.
     * In list mode (span count 1) this degenerates to "previous/next focusable item".
     *
     * @param current The adapter position currently focused.
     * @param down {@code true} for down navigation, {@code false} for up.
     * @return The destination adapter position, or {@link RecyclerView#NO_POSITION} if there is
     *         no focusable item in the requested direction.
     */
    private int findVerticalTargetPosition(int current, boolean down) {
        final GridLayoutManager.SpanSizeLookup lookup = mScope.layout.getSpanSizeLookup();
        final int spanCount = mScope.layout.getSpanCount();
        final int itemCount = mScope.adapter.getItemCount();
        final int currentRow = lookup.getSpanGroupIndex(current, spanCount);
        final int currentColumn = lookup.getSpanIndex(current, spanCount);

        int targetRow = -1;
        int fallback = RecyclerView.NO_POSITION;

        final int step = down ? 1 : -1;
        for (int pos = current + step; pos >= 0 && pos < itemCount; pos += step) {
            if (!isFocusablePosition(pos)) {
                continue;
            }
            final int row = lookup.getSpanGroupIndex(pos, spanCount);
            // Skip any item still on the current visual row (tiles further along the same row).
            if (down ? (row <= currentRow) : (row >= currentRow)) {
                continue;
            }
            if (targetRow == -1) {
                // First focusable item in the row immediately adjacent in this direction.
                targetRow = row;
            } else if (row != targetRow) {
                // Moved past the adjacent row; the best match is whatever we already recorded.
                break;
            }
            fallback = pos;
            final int column = lookup.getSpanIndex(pos, spanCount);
            final int span = lookup.getSpanSize(pos);
            if (currentColumn >= column && currentColumn < column + span) {
                // Column-aligned item directly above/below the current one.
                return pos;
            }
        }

        // No column-aligned item (the adjacent row is shorter or has different spans); fall back
        // to the nearest focusable item in that row, or NO_POSITION at the top/bottom edge.
        return fallback;
    }

    /** @return whether the item at the given adapter position is a focusable document/directory. */
    private boolean isFocusablePosition(int pos) {
        final int type = mScope.adapter.getItemViewType(pos);
        return type == DocumentsAdapter.ITEM_TYPE_DOCUMENT
                || type == DocumentsAdapter.ITEM_TYPE_DIRECTORY;
    }

    /**
     * Given a PgUp/PgDn event and the current view, find the position of the target view. This
     * returns:
     * <li>The position of the topmost (or bottom-most) visible item, if the current item is not the
     * top- or bottom-most visible item.
     * <li>The position of an item that is one page's worth of items up (or down) if the current
     * item is the top- or bottom-most visible item.
     * <li>The first (or last) item, if paging up (or down) would go past those limits.
     *
     * @param view The view that received the key event.
     * @param keyCode Must be KEYCODE_PAGE_UP or KEYCODE_PAGE_DOWN.
     * @param event
     * @return The adapter position of the target item.
     */
    private int findPagedTargetPosition(View view, int keyCode, KeyEvent event) {
        int first = mScope.layout.findFirstVisibleItemPosition();
        int last = mScope.layout.findLastVisibleItemPosition();
        int current = mScope.view.getChildAdapterPosition(view);
        int pageSize = last - first + 1;

        if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
            if (current > first) {
                // If the current item isn't the first item, target the first item.
                return first;
            } else {
                // If the current item is the first item, target the item one page up.
                int target = current - pageSize;
                return target < 0 ? 0 : target;
            }
        }

        if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
            if (current < last) {
                // If the current item isn't the last item, target the last item.
                return last;
            } else {
                // If the current item is the last item, target the item one page down.
                int target = current + pageSize;
                int max = mScope.adapter.getItemCount() - 1;
                return target < max ? target : max;
            }
        }

        throw new IllegalArgumentException("Unsupported keyCode: " + keyCode);
    }

    /**
     * Requests focus for the item in the given adapter position, scrolling the RecyclerView if
     * necessary.
     *
     * @param pos
     */
    private void focusItem(final int pos) {
        focusItem(pos, null);
    }

    /**
     * Requests focus for the item in the given adapter position, scrolling the RecyclerView if
     * necessary.
     *
     * @param pos
     * @param callback A callback to call after the given item has been focused.
     */
    private void focusItem(final int pos, @Nullable final FocusCallback callback) {
        if (mScope.pendingFocusId != null) {
            Log.v(TAG, "clearing pending focus id: " + mScope.pendingFocusId);
            mScope.pendingFocusId = null;
        }

        final RecyclerView recyclerView = mScope.view;
        final RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(pos);

        if (vh != null) {
            // The item is laid out. Focus it and, if it is not fully within the on-screen
            // viewport, scroll it in by the exact delta. We must NOT use
            // smoothScrollToPosition here: this directory's app bar sets
            // shouldHeaderOverlapScrollingChild=true, so the RecyclerView is measured taller
            // than the screen (by the app bar's collapse range) and its "end" for scroll math
            // is below the visible bottom - smoothScrollToPosition (and RecyclerView's own
            // scroll-on-focus) therefore treat rows that are actually off the bottom edge as
            // already visible, which is what let controller focus walk off-screen.
            focusAndReveal(recyclerView, vh.itemView, callback);
        } else {
            // Not laid out yet (a large jump such as page-down or move-to-end). Bring it into
            // the visible content area deterministically, then focus + reveal after layout.
            // scrollToPositionWithOffset places the row just below the app-bar top padding
            // (on-screen), unlike smoothScrollToPosition which would snap it to the RV's
            // off-screen measured bottom.
            mScope.layout.scrollToPositionWithOffset(pos, recyclerView.getPaddingTop());
            recyclerView.post(new Runnable() {
                @Override
                public void run() {
                    RecyclerView.ViewHolder settled =
                            recyclerView.findViewHolderForAdapterPosition(pos);
                    if (settled != null) {
                        focusAndReveal(recyclerView, settled.itemView, callback);
                    } else {
                        Log.w(TAG, "Unable to focus position " + pos + " after scroll");
                    }
                }
            });
        }
    }

    /**
     * Focuses {@code item} and, if it is not fully inside the RecyclerView's on-screen viewport,
     * scrolls by the minimum delta to reveal it. Works for both directions (down: item below the
     * viewport; up: item above the top padding / behind the app bar).
     */
    private static void focusAndReveal(RecyclerView rv, View item, @Nullable FocusCallback cb) {
        final boolean gotFocus = item.requestFocus();
        if (gotFocus && cb != null) {
            cb.onFocus(item);
        }
        final int delta = revealDelta(rv, item);
        if (delta != 0) {
            rv.smoothScrollBy(0, delta);
        }
    }

    /**
     * @return the vertical scroll delta needed to bring {@code item} fully into the RecyclerView's
     * on-screen viewport, or 0 if it is already fully visible. Positive scrolls content up (reveal
     * an item below the fold); negative scrolls down (reveal an item above the top padding).
     *
     * <p>The viewport bottom is the true on-screen bottom (from getGlobalVisibleRect), NOT
     * rv.getHeight(): with shouldHeaderOverlapScrollingChild the RecyclerView is measured taller
     * than the screen, so rv.getHeight() overstates the visible area and would report off-screen
     * rows as visible. The top is the RecyclerView's top padding (sized to the app bar).
     */
    private static int revealDelta(RecyclerView rv, View item) {
        final int viewportTop = rv.getPaddingTop();
        int viewportBottom = onScreenBottomLocal(rv);
        viewportBottom = Math.min(viewportBottom, rv.getHeight() - rv.getPaddingBottom());

        final int top = item.getTop();
        final int bottom = item.getBottom();
        if (bottom > viewportBottom) {
            return bottom - viewportBottom;
        }
        if (top < viewportTop) {
            return top - viewportTop;
        }
        return 0;
    }

    /**
     * @return the RecyclerView's visible bottom edge in its own (local) coordinates - i.e. clipped
     * to what is actually on screen. Falls back to the measured height if the view is not visible.
     */
    private static int onScreenBottomLocal(RecyclerView rv) {
        final Rect visible = new Rect();
        if (!rv.getGlobalVisibleRect(visible)) {
            return rv.getHeight();
        }
        final int[] loc = new int[2];
        rv.getLocationOnScreen(loc);
        return visible.bottom - loc[1];
    }

    /** @return Whether the layout manager is currently in a grid-configuration. */
    private boolean inGridMode() {
        return mScope.layout.getSpanCount() > 1;
    }

    private interface FocusCallback {
        public void onFocus(View view);
    }

    /**
     * A helper class for handling type-to-focus. Instantiate this class, and pass it KeyEvents via
     * the {@link #handleKey(DocumentHolder, int, KeyEvent)} method. The class internally will build
     * up a string from individual key events, and perform searching based on that string. When an
     * item is found that matches the search term, that item will be focused. This class also
     * highlights instances of the search term found in the view.
     */
    private class TitleSearchHelper {
        private static final int SEARCH_TIMEOUT = 500; // ms

        private final KeyListener mTextListener = new TextKeyListener(Capitalize.NONE, false);
        private final Editable mSearchString = Editable.Factory.getInstance().newEditable("");
        private final Highlighter mHighlighter = new Highlighter();
        private final BackgroundColorSpan mSpan;

        private List<String> mIndex;
        private boolean mActive;
        private Timer mTimer;
        private KeyEvent mLastEvent;
        private Handler mUiRunner;

        public TitleSearchHelper(@ColorRes int color) {
            mSpan = new BackgroundColorSpan(color);
            // Handler for running things on the main UI thread. Needed for updating the UI from a
            // timer (see #activate, below).
            mUiRunner = new Handler(Looper.getMainLooper());
        }

        /**
         * Handles alphanumeric keystrokes for type-to-focus. This method builds a search term out
         * of individual key events, and then performs a search for the given string.
         *
         * @param doc The document holder receiving the key event.
         * @param keyCode
         * @param event
         * @return Whether the event was handled.
         */
        public boolean handleKey(DocumentHolder doc, int keyCode, KeyEvent event) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_ESCAPE:
                case KeyEvent.KEYCODE_ENTER:
                    if (mActive) {
                        // These keys end any active searches.
                        endSearch();
                        return true;
                    } else {
                        // Don't handle these key events if there is no active search.
                        return false;
                    }
                case KeyEvent.KEYCODE_SPACE:
                    // This allows users to search for files with spaces in their names, but ignores
                    // spacebar events when a text search is not active. Ignoring the spacebar
                    // event is necessary because other handlers (see FocusManager#handleKey) also
                    // listen for and handle it.
                    if (!mActive) {
                        return false;
                    }
            }

            // Navigation keys also end active searches.
            if (Events.isNavigationKeyCode(keyCode)) {
                endSearch();
                // Don't handle the keycode, so navigation still occurs.
                return false;
            }

            // Build up the search string, and perform the search.
            boolean handled = mTextListener.onKeyDown(doc.itemView, mSearchString, keyCode, event);

            // Delete is processed by the text listener, but not "handled". Check separately for it.
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                handled = true;
            }

            if (handled) {
                mLastEvent = event;
                if (mSearchString.length() == 0) {
                    // Don't perform empty searches.
                    return false;
                }
                search();
            }

            return handled;
        }

        /**
         * Activates the search helper, which changes its key handling and updates the search index
         * and highlights if necessary. Call this each time the search term is updated.
         */
        private void search() {
            if (!mActive) {
                // The model listener invalidates the search index when the model changes.
                mScope.model.addUpdateListener(mModelListener);

                // Used to keep the current search alive until the timeout expires. If the user
                // presses another key within that time, that keystroke is added to the current
                // search. Otherwise, the current search ends, and subsequent keystrokes start a new
                // search.
                mTimer = new Timer();
                mActive = true;
            }

            // If the search index was invalidated, rebuild it
            if (mIndex == null) {
                buildIndex();
            }

            // Search for the current search term.
            // Perform case-insensitive search.
            String searchString = mSearchString.toString().toLowerCase();
            for (int pos = 0; pos < mIndex.size(); pos++) {
                String title = mIndex.get(pos);
                if (title != null && title.startsWith(searchString)) {
                    focusItem(
                            pos,
                            new FocusCallback() {
                                @Override
                                public void onFocus(View view) {
                                    mHighlighter.applyHighlight(view);
                                    // Using a timer repeat period of SEARCH_TIMEOUT/2 means the
                                    // amount of
                                    // time between the last keystroke and a search expiring is
                                    // actually
                                    // between 500 and 750 ms. A smaller timer period results in
                                    // less
                                    // variability but does more polling.
                                    mTimer.schedule(new TimeoutTask(), 0, SEARCH_TIMEOUT / 2);
                                }
                            });
                    break;
                }
            }
        }

        /** Ends the current search (see {@link #search()}. */
        private void endSearch() {
            if (mActive) {
                mScope.model.removeUpdateListener(mModelListener);
                mTimer.cancel();
            }

            mHighlighter.removeHighlight();

            mIndex = null;
            mSearchString.clear();
            mActive = false;
        }

        /**
         * Builds a search index for finding items by title. Queries the model and adapter, so both
         * must be set up before calling this method.
         */
        private void buildIndex() {
            int itemCount = mScope.adapter.getItemCount();
            List<String> index = new ArrayList<>(itemCount);
            for (int i = 0; i < itemCount; i++) {
                String modelId = mScope.adapter.getStableId(i);
                Cursor cursor = mScope.model.getItem(modelId);
                if (modelId != null && cursor != null) {
                    String title = getCursorString(cursor, Document.COLUMN_DISPLAY_NAME);
                    // Perform case-insensitive search.
                    index.add(title.toLowerCase());
                } else {
                    index.add("");
                }
            }
            mIndex = index;
        }

        private EventListener<Model.Update> mModelListener = new EventListener<Model.Update>() {
            @Override
            public void accept(Update event) {
                // Invalidate the search index when the model updates.
                mIndex = null;
            }
        };

        private class TimeoutTask extends TimerTask {
            @Override
            public void run() {
                long last = mLastEvent.getEventTime();
                long now = SystemClock.uptimeMillis();
                if ((now - last) > SEARCH_TIMEOUT) {
                    // endSearch must run on the main thread because it does UI work
                    mUiRunner.post(
                            new Runnable() {
                                @Override
                                public void run() {
                                    endSearch();
                                }
                            });
                }
            }
        };

        private class Highlighter {
            private Spannable mCurrentHighlight;

            /**
             * Applies title highlights to the given view. The view must have a title field that is
             * a spannable text field. If this condition is not met, this function does nothing.
             *
             * @param view
             */
            private void applyHighlight(View view) {
                TextView titleView = (TextView) view.findViewById(android.R.id.title);
                if (titleView == null) {
                    return;
                }

                CharSequence tmpText = titleView.getText();
                if (tmpText instanceof Spannable) {
                    if (mCurrentHighlight != null) {
                        mCurrentHighlight.removeSpan(mSpan);
                    }
                    mCurrentHighlight = (Spannable) tmpText;
                    mCurrentHighlight.setSpan(
                            mSpan, 0, mSearchString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            /**
             * Removes title highlights from the given view. The view must have a title field that
             * is a spannable text field. If this condition is not met, this function does nothing.
             *
             * @param view
             */
            private void removeHighlight() {
                if (mCurrentHighlight != null) {
                    mCurrentHighlight.removeSpan(mSpan);
                }
            }
        };
    }

    public FocusManager reset(RecyclerView view, Model model) {
        assert (view != null);
        assert (model != null);
        mScope.view = view;
        mScope.adapter = (DocumentsAdapter) view.getAdapter();
        mScope.layout = (GridLayoutManager) view.getLayoutManager();
        mScope.model = model;

        mScope.lastFocusPosition = RecyclerView.NO_POSITION;
        mScope.pendingFocusId = null;

        return this;
    }

    private static final class ContentScope {
        private @Nullable RecyclerView view;
        private @Nullable DocumentsAdapter adapter;
        private @Nullable GridLayoutManager layout;
        private @Nullable Model model;

        private @Nullable String pendingFocusId;
        private int lastFocusPosition = RecyclerView.NO_POSITION;

        boolean isValid() {
            return (view != null && model != null);
        }
    }
}
