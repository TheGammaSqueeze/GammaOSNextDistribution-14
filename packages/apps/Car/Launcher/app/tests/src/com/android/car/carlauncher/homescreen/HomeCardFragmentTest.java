/*
 * Copyright (C) 2020 Google Inc.
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

package com.android.car.carlauncher.homescreen;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.graphics.drawable.Drawable;
import android.widget.ImageButton;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.Suppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.carlauncher.CarLauncher;
import com.android.car.carlauncher.R;
import com.android.car.carlauncher.homescreen.ui.CardHeader;
import com.android.car.carlauncher.homescreen.ui.DescriptiveTextView;
import com.android.car.carlauncher.homescreen.ui.DescriptiveTextWithControlsView;
import com.android.car.carlauncher.homescreen.ui.TextBlockView;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@Suppress // To be ignored until b/224978827 is fixed
@RunWith(AndroidJUnit4.class)
public class HomeCardFragmentTest {

    private static final String DESCRIPTIVE_TEXT_TITLE = "Test title text";
    private static final String DESCRIPTIVE_TEXT_SUBTITLE = "Test subtitle text";
    private static final String DESCRIPTIVE_TEXT_FOOTER = "Descriptive footer";
    private static final String TEXT_BLOCK_CONTENT = "Test text for text block";
    private static final String TEXT_BLOCK_FOOTER = "Text block footer";
    private static final CardHeader CARD_HEADER = new CardHeader("Test App Name", /* appIcon= */
            null);
    private static final DescriptiveTextView DESCRIPTIVE_TEXT_VIEW =
            new DescriptiveTextView(/* image = */ null, DESCRIPTIVE_TEXT_TITLE,
                    DESCRIPTIVE_TEXT_SUBTITLE, DESCRIPTIVE_TEXT_FOOTER);
    private static final DescriptiveTextView DESCRIPTIVE_TEXT_VIEW_NO_FOOTER =
            new DescriptiveTextView(/* image = */ null, DESCRIPTIVE_TEXT_TITLE,
                    DESCRIPTIVE_TEXT_SUBTITLE);
    private static final TextBlockView TEXT_BLOCK_VIEW = new TextBlockView(TEXT_BLOCK_CONTENT,
            TEXT_BLOCK_FOOTER);
    private static final TextBlockView TEXT_BLOCK_VIEW_NO_FOOTER = new TextBlockView(
            TEXT_BLOCK_CONTENT);

    @Rule
    public ActivityTestRule<CarLauncher> mActivityTestRule = new ActivityTestRule<CarLauncher>(
            CarLauncher.class);

    @Test
    public void updateContentView_descriptiveTextWithFooter_displaysTapForMoreView() {
        HomeCardFragment fragment = (HomeCardFragment) mActivityTestRule.getActivity()
                .getSupportFragmentManager().findFragmentById(R.id.top_card);
        fragment.updateHeaderView(CARD_HEADER);
        fragment.updateContentView(DESCRIPTIVE_TEXT_VIEW);

        onView(allOf(withId(R.id.descriptive_text_layout),
                isDescendantOfA(withId(R.id.top_card)))).check(
                matches(isDisplayed()));
        onView(allOf(withId(R.id.primary_text), withText(DESCRIPTIVE_TEXT_TITLE),
                isDescendantOfA(withId(R.id.descriptive_text_layout)),
                isDescendantOfA(withId(R.id.top_card)))).check(matches(isDisplayed()));
        onView(allOf(withId(R.id.secondary_text), withText(DESCRIPTIVE_TEXT_SUBTITLE),
                isDescendantOfA(withId(R.id.descriptive_text_layout)),
                isDescendantOfA(withId(R.id.top_card)))).check(matches(isDisplayed()));
        onView(allOf(withId(R.id.tap_for_more_text), withText(DESCRIPTIVE_TEXT_FOOTER),
                isDescendantOfA(withId(R.id.descriptive_text_layout)),
                isDescendantOfA(withId(R.id.top_card)))).check(matches(isDisplayed()));
    }

    @Test
    public void updateContentView_descriptiveTextWithNoFooter_hidesTapForMoreView() {
        HomeCardFragment fragment = (HomeCardFragment) mActivityTestRule.getActivity()
                .getSupportFragmentManager().findFragmentById(R.id.top_card);
        fragment.updateHeaderView(CARD_HEADER);
        fragment.updateContentView(DESCRIPTIVE_TEXT_VIEW_NO_FOOTER);

        onView(allOf(withId(R.id.descriptive_text_layout),
                isDescendantOfA(withId(R.id.top_card)))).check(
                matches(isDisplayed()));
        onView(allOf(withId(R.id.primary_text), withText(DESCRIPTIVE_TEXT_TITLE),
                isDescendantOfA(withId(R.id.descriptive_text_layout)),
                isDescendantOfA(withId(R.id.top_card)))).check(matches(isDisplayed()));
        onView(allOf(withId(R.id.secondary_text), withText(DESCRIPTIVE_TEXT_SUBTITLE),
                isDescendantOfA(withId(R.id.descriptive_text_layout)),
                isDescendantOfA(withId(R.id.top_card)))).check(matches(isDisplayed()));
        onView(allOf(withId(R.id.tap_for_more_text),
                isDescendantOfA(withId(R.id.descriptive_text_layout)),
                isDescendantOfA(withId(R.id.top_card)))).check(matches(not(isDisplayed())));
    }

    @Test
    public void updateContentView_textBlockWithFooter_displaysTapForMoreView() {
        HomeCardFragment fragment = (HomeCardFragment) mActivityTestRule.getActivity()
                .getSupportFragmentManager().findFragmentById(R.id.top_card);
        fragment.updateHeaderView(CARD_HEADER);
        fragment.updateContentView(TEXT_BLOCK_VIEW);

        onView(allOf(withId(R.id.text_block_layout), isDescendantOfA(withId(R.id.top_card)))).check(
                matches(isDisplayed()));
        onView(allOf(withId(R.id.text_block), withText(TEXT_BLOCK_CONTENT),
                isDescendantOfA(withId(R.id.text_block_layout)),
                isDescendantOfA(withId(R.id.top_card)))).check(matches(isDisplayed()));
        onView(allOf(withId(R.id.tap_for_more_text), withText(TEXT_BLOCK_FOOTER),
                isDescendantOfA(withId(R.id.text_block_layout)),
                isDescendantOfA(withId(R.id.top_card)))).check(matches(isDisplayed()));
    }

    @Test
    public void updateContentView_textBlockNoFooter_hidesTapForMoreView() {
        HomeCardFragment fragment = (HomeCardFragment) mActivityTestRule.getActivity()
                .getSupportFragmentManager().findFragmentById(R.id.top_card);
        fragment.updateHeaderView(CARD_HEADER);
        fragment.updateContentView(TEXT_BLOCK_VIEW_NO_FOOTER);

        onView(allOf(withId(R.id.text_block_layout), isDescendantOfA(withId(R.id.top_card)))).check(
                matches(isDisplayed()));
        onView(allOf(withId(R.id.text_block), withText(TEXT_BLOCK_CONTENT),
                isDescendantOfA(withId(R.id.text_block_layout)),
                isDescendantOfA(withId(R.id.top_card)))).check(matches(isDisplayed()));
        onView(allOf(withId(R.id.tap_for_more_text),
                isDescendantOfA(withId(R.id.text_block_layout)),
                isDescendantOfA(withId(R.id.top_card)))).check(matches(not(isDisplayed())));
    }

    @Test
    public void updateControlBarButton_updatesButtonSelectedState() {
        HomeCardFragment fragment = (HomeCardFragment) mActivityTestRule.getActivity()
                .getSupportFragmentManager().findFragmentById(R.id.top_card);
        assertNotNull(fragment);

        ImageButton leftImageButton = mock(ImageButton.class);
        fragment.setControlBarLeftButton(leftImageButton);

        Drawable mockIcon = mock(Drawable.class);
        DescriptiveTextWithControlsView.Control buttonControl = mock(
                DescriptiveTextWithControlsView.Control.class);
        DescriptiveTextWithControlsView.Control leftButtonControl = mock(
                DescriptiveTextWithControlsView.Control.class);

        // unselects the button in case the icon doesn't have a selected state
        when(mockIcon.getState()).thenReturn(new int[0]);
        when(leftButtonControl.getIcon()).thenReturn(mockIcon);
        DescriptiveTextWithControlsView controlView = new DescriptiveTextWithControlsView(null,
                "test", "test", leftButtonControl, buttonControl, buttonControl);
        fragment.updateHeaderView(CARD_HEADER);
        fragment.updateContentView(controlView);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        verify(leftImageButton).setSelected(false);

        // selects the button in case the icon have a selected state
        when(mockIcon.getState()).thenReturn(new int[]{android.R.attr.state_selected});
        when(leftButtonControl.getIcon()).thenReturn(mockIcon);
        controlView = new DescriptiveTextWithControlsView(null,
                "test", "test", leftButtonControl, buttonControl, buttonControl);
        fragment.updateContentView(controlView);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        verify(leftImageButton).setSelected(true);
    }
}
