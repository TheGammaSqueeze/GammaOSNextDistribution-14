/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.autofillservice.cts.testcore;

import static android.autofillservice.cts.testcore.Timeouts.DATASET_PICKER_NOT_SHOWN_NAPTIME_MS;
import static android.autofillservice.cts.testcore.Timeouts.LONG_PRESS_MS;
import static android.autofillservice.cts.testcore.Timeouts.UI_TIMEOUT;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiObject2;

import com.android.compatibility.common.util.RequiredFeatureRule;
import com.android.cts.mockime.MockIme;

import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

/**
 * UiBot for the inline suggestion.
 */
public final class InlineUiBot extends UiBot {

    private static final String TAG = "AutoFillInlineCtsUiBot";
    public static final String SUGGESTION_STRIP_DESC = "MockIme Inline Suggestion View";

    private static final BySelector SUGGESTION_STRIP_SELECTOR = By.desc(SUGGESTION_STRIP_DESC);

    private static final RequiredFeatureRule REQUIRES_IME_RULE = new RequiredFeatureRule(
            PackageManager.FEATURE_INPUT_METHODS);

    private final Context mContext;

    public InlineUiBot(Context context) {
        super(UI_TIMEOUT);
        mContext = context;
    }

    public static RuleChain annotateRule(TestRule rule) {
        return RuleChain.outerRule(REQUIRES_IME_RULE).around(rule);
    }

    @Override
    public void assertNoDatasets() throws Exception {
        assertNoDatasetsEver();
    }

    @Override
    public void assertNoDatasetsEver() throws Exception {
        assertNeverShown("suggestion strip", SUGGESTION_STRIP_SELECTOR,
                DATASET_PICKER_NOT_SHOWN_NAPTIME_MS);
    }

    /**
     * Selects the suggestion in the {@link MockIme}'s suggestion strip by the given text.
     */
    public void selectSuggestion(String name) throws Exception {
        final UiObject2 strip = findSuggestionStrip();
        final UiObject2 dataset = strip.findObject(By.text(name));
        if (dataset == null) {
            throw new AssertionError("no dataset " + name + " in " + getChildrenAsText(strip));
        }
        dataset.click();
    }

    @Override
    public void selectDataset(String name) throws Exception {
        int retryCount = 0;
        while (retryCount < MAX_UIOBJECT_RETRY_COUNT) {
            try {
                selectSuggestion(name);
                break;
            } catch (AssertionError ex) {
                retryCount++;
                if (retryCount >= MAX_UIOBJECT_RETRY_COUNT) {
                    throw ex;
                } else {
                    SystemClock.sleep(100);
                }
            }
        }
    }

    @Override
    public void longPressSuggestion(String name) throws Exception {
        final UiObject2 strip = findSuggestionStrip();
        final UiObject2 dataset = strip.findObject(By.text(name));
        if (dataset == null) {
            throw new AssertionError("no dataset " + name + " in " + getChildrenAsText(strip));
        }
        dataset.click(LONG_PRESS_MS);
    }

    @Override
    public UiObject2 assertDatasets(String... names) throws Exception {
        UiObject2 picker = null;
        int retryCount = 0;
        while (retryCount < MAX_UIOBJECT_RETRY_COUNT) {
            try {
                picker = findSuggestionStrip();
                return assertDatasets(picker, names);
            } catch (StaleObjectException ex) {
                retryCount++;
                if (retryCount >= MAX_UIOBJECT_RETRY_COUNT) {
                    throw ex;
                } else {
                    SystemClock.sleep(100);
                }
            }
        }

        // Unreachable, either:
        // 1. returns assertDatasets(picker, names)
        // 2. StaleObjectException is thrown above
        return picker;
    }

    @Override
    public void assertSuggestion(String name) throws Exception {
        final UiObject2 strip = findSuggestionStrip();
        final UiObject2 dataset = strip.findObject(By.text(name));
        if (dataset == null) {
            throw new AssertionError("no dataset " + name + " in " + getChildrenAsText(strip));
        }
    }

    @Override
    public void assertNoSuggestion(String name) throws Exception {
        final UiObject2 strip = findSuggestionStrip();
        final UiObject2 dataset = strip.findObject(By.text(name));
        if (dataset != null) {
            throw new AssertionError("has dataset " + name + " in " + getChildrenAsText(strip));
        }
    }

    @Override
    public void scrollSuggestionView(Direction direction, int speed) throws Exception {
        final UiObject2 strip = findSuggestionStrip();
        final int defaultWidth = strip.getVisibleBounds().width() / 4;
        final int width = getEdgeSensitivityWidth(defaultWidth);
        strip.setGestureMargin(width);
        strip.fling(direction, speed);
    }

    public void assertTooltipShowing(String text) throws Exception {
        final UiObject2 strip = waitForObject(By.text(text), UI_TIMEOUT);
        if (strip == null) {
            throw new AssertionError("not find inline tooltip by text: " + text);
        }
    }

    private UiObject2 findSuggestionStrip() throws Exception {
        return waitForObject(SUGGESTION_STRIP_SELECTOR, Timeouts.UI_TIMEOUT);
    }

    private int getEdgeSensitivityWidth(int defaultWidth) {
        Resources resources = mContext.getResources();
        int resId = resources.getIdentifier("config_backGestureInset", "dimen", "android");
        try {
            int width = resources.getDimensionPixelSize(resId) + 1;
            if (width >= defaultWidth) {
                return width;
            }
            Log.i(TAG, "Got edge sensitivity width of " + width
                    + ", which is less than the default of " + defaultWidth + ". Using default");
            return defaultWidth;
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Failed to get edge sensitivity width. Defaulting to " + defaultWidth, e);
            return defaultWidth;
        }
    }
}
