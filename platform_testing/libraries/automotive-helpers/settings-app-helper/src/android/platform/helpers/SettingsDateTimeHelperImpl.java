/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.platform.helpers;

import static junit.framework.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.ContentResolver;
import android.platform.helpers.ScrollUtility.ScrollActions;
import android.platform.helpers.ScrollUtility.ScrollDirection;
import android.text.format.DateFormat;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/** Helper file for Date&Time test */
public class SettingsDateTimeHelperImpl extends AbstractStandardAppHelper
        implements IAutoDateTimeSettingsHelper {
    private static final Locale LOCALE = Locale.ENGLISH;
    private static final TextStyle TEXT_STYLE = TextStyle.SHORT;
    private static final String LOG_TAG = SettingsDateTimeHelperImpl.class.getSimpleName();
    private ScrollUtility mScrollUtility;
    private ScrollActions mScrollAction;
    private BySelector mBackwardButtonSelector;
    private BySelector mForwardButtonSelector;
    private BySelector mScrollableElementSelector;
    private BySelector mSummarySelector;
    private ScrollDirection mScrollDirection;

    public SettingsDateTimeHelperImpl(Instrumentation instr) {
        super(instr);
        mScrollUtility = ScrollUtility.getInstance(getSpectatioUiUtil());
        mScrollAction =
                ScrollActions.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.DATE_TIME_SETTINGS_SCROLL_ACTION));
        mBackwardButtonSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.DATE_TIME_SETTINGS_SCROLL_BACKWARD_BUTTON);
        mForwardButtonSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.DATE_TIME_SETTINGS_SCROLL_FORWARD_BUTTON);
        mScrollableElementSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.DATE_TIME_SETTINGS_SCROLL_ELEMENT);
        mSummarySelector = getUiElementFromConfig(AutomotiveConfigConstants.SETTINGS_SUMMARY);
        mScrollDirection =
                ScrollDirection.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.DATE_TIME_SETTINGS_SCROLL_DIRECTION));
        mScrollUtility.setScrollValues(
                Integer.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.DATE_TIME_SETTINGS_SCROLL_MARGIN)),
                Integer.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.DATE_TIME_SETTINGS_SCROLL_WAIT_TIME)));
    }

    /** {@inheritDoc} */
    @Override
    public String getPackage() {
        return getPackageFromConfig(AutomotiveConfigConstants.SETTINGS_PACKAGE);
    }

    /** {@inheritDoc} */
    @Override
    public String getLauncherName() {
        throw new UnsupportedOperationException("Operation not supported.");
    }

    /** {@inheritDoc} */
    @Override
    public void dismissInitialDialogs() {
        // Nothing to dismiss
    }

    /** {@inheritDoc} */
    @Override
    public void setDate(LocalDate date) {
        UiObject2 autoDateTimeSwitchWidget = getAutoDateTimeSwitchWidget();
        UiObject2 autoDateTimeMenu =
                getMenu(
                        getUiElementFromConfig(
                                AutomotiveConfigConstants
                                        .DATE_TIME_SETTINGS_SET_TIME_AUTOMATICALLY));
        if (autoDateTimeSwitchWidget.isChecked()) {
            getSpectatioUiUtil().clickAndWait(autoDateTimeMenu);
            getSpectatioUiUtil().wait1Second();
        }
        UiObject2 setDateMenu = getSetDateMenu();
        assertTrue("set date menu is not clickable", setDateMenu.isEnabled()); // from UI
        getSpectatioUiUtil().clickAndWait(setDateMenu);
        getSpectatioUiUtil().wait1Second();
        assertTrue(
                "automatic date & time is not switched off",
                !isAutomaticOn("auto_time")); // from API
        int year = date.getYear();
        Month month = date.getMonth();
        int day = date.getDayOfMonth();
        String month_string = month.getDisplayName(TEXT_STYLE, LOCALE);
        String day_string = "";
        if (day < 10) {
            day_string = "0" + String.valueOf(day);
        } else {
            day_string = "" + String.valueOf(day);
        }
        String year_string = "" + year;
        setCalendar(1, day_string);
        setCalendar(0, month_string);
        setCalendar(2, year_string);
        getSpectatioUiUtil().pressBack();
    }

    private void setCalendar(int index, String s) {
        UiSelector selector =
                new UiSelector()
                        .className(
                                getPackageFromConfig(
                                        AutomotiveConfigConstants.NUMBER_PICKER_WIDGET_CLASS))
                        .index(index);
        boolean scrollForwards = true;
        if (index == 2) {
            UiSelector yearSelector =
                    selector.childSelector(
                            new UiSelector()
                                    .className(
                                            getPackageFromConfig(
                                                    AutomotiveConfigConstants
                                                            .EDIT_TEXT_WIDGET_CLASS)));
            String curYear = "";
            try {
                curYear = new UiObject(yearSelector).getText();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            if (Integer.valueOf(curYear) > Integer.valueOf(s)) scrollForwards = false;
        }
        scrollToObjectInPicker(index, s, scrollForwards);
    }

    /** {@inheritDoc} */
    @Override
    public LocalDate getDate() {
        UiObject2 obj = getSetDateMenu();
        if (obj == null) {
            throw new RuntimeException("Unable to find set date menu.");
        }
        String uiDate = getMenuSummaryText(obj);
        String[] arr = uiDate.split(" ");
        if (arr.length != 3) {
            throw new RuntimeException("Cannot find date from UI");
        }
        int year = Integer.valueOf(arr[2]);
        int month = Month.valueOf(arr[0].toUpperCase()).getValue();
        int day = Integer.valueOf(arr[1].substring(0, arr[1].length() - 1));
        return LocalDate.of(year, month, day);
    }

    /** {@inheritDoc} */
    @Override
    public void setTimeInTwelveHourFormat(int hour, int minute, boolean am) {
        // Get current time
        String currentTime = getTime();

        // check Automatic date & time switch is turned off
        UiObject2 autoDateTimeSwitchWidget = getAutoDateTimeSwitchWidget();
        UiObject2 autoDateTimeMenu =
                getMenu(
                        getUiElementFromConfig(
                                AutomotiveConfigConstants
                                        .DATE_TIME_SETTINGS_SET_TIME_AUTOMATICALLY));
        if (autoDateTimeSwitchWidget.isChecked()) {
            getSpectatioUiUtil().clickAndWait(autoDateTimeMenu);
            getSpectatioUiUtil().wait1Second();
        }
        // check 24-hour format switch is turned off
        if (isTwentyFourHourFormatEnabled()) {
            toggleTwentyFourHourFormatSwitch();
        }

        UiObject2 setTimeMenu = getSetTimeMenu();
        assertTrue("set time menu is not clickable", setTimeMenu.isEnabled()); // from UI
        getSpectatioUiUtil().clickAndWait(setTimeMenu);
        assertTrue(
                "automatic date & time is not switched off",
                !isAutomaticOn("auto_time")); // from API
        String minute_string = "" + minute;
        String am_pm = "";
        am_pm = am ? "AM" : "PM";
        if (minute < 10) {
            minute_string = "0" + minute;
        }
        setTime(2, minute_string, currentTime);
        setTime(0, String.valueOf(hour), currentTime);
        setTime(1, am_pm, currentTime);
        getSpectatioUiUtil().pressBack();
    }

    /** {@inheritDoc} */
    @Override
    public void setTimeInTwentyFourHourFormat(int hour, int minute) {
        // Get current time
        String currentTime = getTime();

        // check Automatic date & time switch is turned off
        UiObject2 autoDateTimeSwitchWidget = getAutoDateTimeSwitchWidget();
        UiObject2 autoDateTimeMenu =
                getMenu(
                        getUiElementFromConfig(
                                AutomotiveConfigConstants
                                        .DATE_TIME_SETTINGS_SET_TIME_AUTOMATICALLY));
        if (autoDateTimeSwitchWidget.isChecked()) {
            getSpectatioUiUtil().clickAndWait(autoDateTimeMenu);
        }
        // check 24-hour format switch is turned on
        if (!isTwentyFourHourFormatEnabled()) {
            toggleTwentyFourHourFormatSwitch();
        }

        UiObject2 setTimeMenu = getSetTimeMenu();
        assertTrue("set time menu is not clickable", setTimeMenu.isEnabled()); // from UI
        getSpectatioUiUtil().clickAndWait(setTimeMenu);
        assertTrue(
                "automatic date & time is not switched off",
                !isAutomaticOn("auto_time")); // from API
        String minute_string = "" + minute;
        if (minute < 10) {
            minute_string = "0" + minute;
        }
        String hour_string = "" + hour;
        if (hour < 10) {
            hour_string = "0" + hour;
        }
        setTime(2, minute_string, currentTime);
        setTime(0, hour_string, currentTime);
        getSpectatioUiUtil().pressBack();
    }

    private void setTime(int index, String s, String currentTime) {
        UiSelector selector =
                new UiSelector()
                        .className(
                                getPackageFromConfig(
                                        AutomotiveConfigConstants.NUMBER_PICKER_WIDGET_CLASS))
                        .index(index);
        boolean scrollForwards = true;
        String curAM_PM;
        if (index == 1) {
            UiSelector am_pm_Selector =
                    selector.childSelector(
                            new UiSelector()
                                    .className(
                                            getPackageFromConfig(
                                                    AutomotiveConfigConstants
                                                            .EDIT_TEXT_WIDGET_CLASS)));
            try {
                curAM_PM = new UiObject(am_pm_Selector).getText();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            if (curAM_PM.equals("PM")) scrollForwards = false;
        } else if (index == 2) {
            int currentMinute = Integer.parseInt(currentTime.split(":")[1].split("\\s+")[0]);
            int setMinute = Integer.parseInt(s);

            /* Set scrollForwards such that the minute is scrolled a max of 30 times */
            if (currentMinute > setMinute) {
                if (currentMinute - setMinute <= 30) scrollForwards = false;
            } else if (setMinute > currentMinute) {
                if (setMinute - currentMinute > 30) scrollForwards = false;
            }
        } else {
            int currentHour = Integer.parseInt(currentTime.split(":")[0]);
            int setHour = Integer.parseInt(s);

            /* Set max scrolls based on whether we're in 12 or 24 hour format */
            int maxScrolls =
                    (currentTime.trim().endsWith("AM") || currentTime.trim().endsWith("PM"))
                            ? 6
                            : 12;

            /* Calculate forward or backward like we did for minutes */
            if (currentHour > setHour) {
                if (currentHour - setHour <= maxScrolls) scrollForwards = false;
            } else if (setHour > currentHour) {
                if (setHour - currentHour > maxScrolls) scrollForwards = false;
            }
        }
        scrollToObjectInPicker(index, s, scrollForwards);
    }

    private void scrollToObjectInPicker(int index, String s, boolean scrollForwards) {
        UiSelector selector =
                new UiSelector()
                        .className(
                                getPackageFromConfig(
                                        AutomotiveConfigConstants.NUMBER_PICKER_WIDGET_CLASS))
                        .index(index);
        UiScrollable scrollable = new UiScrollable(selector);
        scrollable.setAsVerticalList();
        UiObject2 obj =
                getSpectatioUiUtil()
                        .findUiObject(
                                By.text(s)
                                        .clazz(
                                                getPackageFromConfig(
                                                        AutomotiveConfigConstants
                                                                .EDIT_TEXT_WIDGET_CLASS)));

        /* For hour and minute, search by child object instead of text */
        if (index == 0 || index == 2) {
            UiSelector dayOrMonthSelector =
                    selector.childSelector(
                            new UiSelector()
                                    .className(
                                            getPackageFromConfig(
                                                    AutomotiveConfigConstants
                                                            .EDIT_TEXT_WIDGET_CLASS)));

            /* Once we have the child selector, search for text within that selector */
            String currentValue = "";
            try {
                currentValue = new UiObject(dayOrMonthSelector).getText().trim();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            while (!currentValue.equals(s.trim())) {
                try {
                    if (scrollForwards) {
                        scrollable.scrollForward();
                    } else {
                        scrollable.scrollBackward();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                dayOrMonthSelector =
                        selector.childSelector(
                                new UiSelector()
                                        .className(
                                                getPackageFromConfig(
                                                        AutomotiveConfigConstants
                                                                .EDIT_TEXT_WIDGET_CLASS)));

                try {
                    currentValue = new UiObject(dayOrMonthSelector).getText().trim();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            while (obj == null) {
                try {
                    if (scrollForwards) {
                        scrollable.scrollForward();
                    } else {
                        scrollable.scrollBackward();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                obj =
                        getSpectatioUiUtil()
                                .findUiObject(
                                        By.text(s)
                                                .clazz(
                                                        getPackageFromConfig(
                                                                AutomotiveConfigConstants
                                                                        .EDIT_TEXT_WIDGET_CLASS)));
            }

            if (obj == null) throw new RuntimeException("cannot find value in the picker");
        }
    }

    /** {@inheritDoc} */
    @Override
    public String getTime() {
        UiObject2 obj = getSetTimeMenu();
        if (obj == null) {
            throw new RuntimeException("Unable to find time menu.");
        }
        String uiTime = getMenuSummaryText(obj);
        return uiTime;
    }

    /** {@inheritDoc} */
    @Override
    public void setTimeZone(String timezone) {
        UiObject2 autoTimeZoneSwitchWidget = getAutoTimeZoneSwitchWidget();
        UiObject2 autoTimeZoneMenu =
                getMenu(
                        getUiElementFromConfig(
                                AutomotiveConfigConstants
                                        .DATE_TIME_SETTINGS_SET_TIME_ZONE_AUTOMATICALLY));
        if (getAutoTimeZoneSwitchWidget().isChecked()) {
            getSpectatioUiUtil().clickAndWait(autoTimeZoneMenu);
        }
        UiObject2 selectTimeZoneMenu = getSelectTimeZoneMenu();
        assertTrue(
                "select time zone menu is not clickable",
                selectTimeZoneMenu.isEnabled()); // from UI
        assertTrue(
                "automatic time zone is not switched off",
                !isAutomaticOn("auto_time_zone")); // from API
        getSpectatioUiUtil().clickAndWait(selectTimeZoneMenu);
        BySelector selector = By.clickable(true).hasDescendant(By.text(timezone));
        ScrollActions scrollAction =
                ScrollActions.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.DATE_TIME_SETTINGS_SCROLL_ACTION));
        BySelector backwardButtonSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.DATE_TIME_SETTINGS_SCROLL_BACKWARD_BUTTON);
        BySelector forwardButtonSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.DATE_TIME_SETTINGS_SCROLL_FORWARD_BUTTON);
        BySelector scrollElementSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.DATE_TIME_SETTINGS_SCROLL_ELEMENT);
        ScrollDirection scrollDirection =
                ScrollDirection.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.DATE_TIME_SETTINGS_SCROLL_DIRECTION));
        UiObject2 object =
                mScrollUtility.scrollAndFindUiObject(
                        scrollAction,
                        scrollDirection,
                        forwardButtonSelector,
                        backwardButtonSelector,
                        scrollElementSelector,
                        selector,
                        String.format("Scroll on date & time to find %s", timezone));
        getSpectatioUiUtil()
                .validateUiObject(object, String.format("Unable to find timezone %s", timezone));
        getSpectatioUiUtil().clickAndWait(object);
    }

    /** {@inheritDoc} */
    @Override
    public boolean toggleTwentyFourHourFormatSwitch() {
        UiObject2 twentyFourHourFormatSwitch = getUseTwentyFourHourFormatSwitchWidget();
        if (twentyFourHourFormatSwitch.isChecked()) {
            assertTrue(
                    "System time format is different from UI format",
                    DateFormat.is24HourFormat(mInstrumentation.getContext()));
        } else {
            assertTrue(
                    "System time format is different from UI format",
                    !DateFormat.is24HourFormat(mInstrumentation.getContext()));
        }
        getSpectatioUiUtil().clickAndWait(twentyFourHourFormatSwitch);
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public String getTimeZone() {
        UiObject2 obj = getSelectTimeZoneMenu();
        if (obj == null) {
            throw new RuntimeException("Unable to find timezone menu.");
        }
        String timeZone = getMenuSummaryText(obj);
        return timeZone;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isTwentyFourHourFormatEnabled() {
        UiObject2 twentyFourHourFormatSwitchWidget = getUseTwentyFourHourFormatSwitchWidget();
        return twentyFourHourFormatSwitchWidget.isChecked();
    }

    private UiObject2 getSetDateMenu() {
        return getMenu(
                getUiElementFromConfig(AutomotiveConfigConstants.DATE_TIME_SETTINGS_SET_DATE));
    }

    private UiObject2 getSetTimeMenu() {
        return getMenu(
                getUiElementFromConfig(AutomotiveConfigConstants.DATE_TIME_SETTINGS_SET_TIME));
    }

    private UiObject2 getTwentyFourFormatMenu() {
        return getMenu(
                getUiElementFromConfig(
                        AutomotiveConfigConstants.DATE_TIME_SETTINGS_USE_24_HOUR_FORMAT));
    }

    private UiObject2 getSelectTimeZoneMenu() {
        return getMenu(
                getUiElementFromConfig(
                        AutomotiveConfigConstants.DATE_TIME_SETTINGS_SELECT_TIME_ZONE));
    }

    private UiObject2 getMenu(BySelector bySelector) {
        BySelector selector = By.clickable(true).hasDescendant(bySelector);
        ScrollActions scrollAction =
                ScrollActions.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.DATE_TIME_SETTINGS_SCROLL_ACTION));
        BySelector backwardButtonSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.DATE_TIME_SETTINGS_SCROLL_BACKWARD_BUTTON);
        BySelector forwardButtonSelector =
                getUiElementFromConfig(
                        AutomotiveConfigConstants.DATE_TIME_SETTINGS_SCROLL_FORWARD_BUTTON);
        BySelector scrollElementSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.DATE_TIME_SETTINGS_SCROLL_ELEMENT);
        ScrollDirection scrollDirection =
                ScrollDirection.valueOf(
                        getActionFromConfig(
                                AutomotiveConfigConstants.DATE_TIME_SETTINGS_SCROLL_DIRECTION));
        UiObject2 object =
                mScrollUtility.scrollAndFindUiObject(
                        scrollAction,
                        scrollDirection,
                        forwardButtonSelector,
                        backwardButtonSelector,
                        scrollElementSelector,
                        selector,
                        String.format("Scroll on date & time to find %s", selector));
        return object;
    }

    private UiObject2 getAutoDateTimeSwitchWidget() {
        return getSwitchWidget(
                getUiElementFromConfig(
                        AutomotiveConfigConstants.DATE_TIME_SETTINGS_SET_TIME_AUTOMATICALLY));
    }

    private UiObject2 getAutoTimeZoneSwitchWidget() {
        return getSwitchWidget(
                getUiElementFromConfig(
                        AutomotiveConfigConstants.DATE_TIME_SETTINGS_SET_TIME_ZONE_AUTOMATICALLY));
    }

    private UiObject2 getUseTwentyFourHourFormatSwitchWidget() {
        return getSwitchWidget(
                getUiElementFromConfig(
                        AutomotiveConfigConstants.DATE_TIME_SETTINGS_USE_24_HOUR_FORMAT));
    }

    private UiObject2 getSwitchWidget(BySelector bySelector) {
        BySelector selector = By.hasDescendant(bySelector);
        UiObject2 object =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mScrollableElementSelector,
                        selector,
                        String.format("Scroll on date & time to find %s", selector));
        List<UiObject2> list = object.getParent().getChildren();
        UiObject2 switchWidget = list.get(1).getChildren().get(0).getChildren().get(0);
        return switchWidget;
    }

    private String getMenuSummaryText(UiObject2 obj) {
        return obj.findObject(mSummarySelector).getText();
    }

    private boolean isAutomaticOn(String name) {
        ContentResolver cr = mInstrumentation.getContext().getContentResolver();
        int status = 0;
        try {
            status = android.provider.Settings.Global.getInt(cr, name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return status == 1 ? true : false;
    }

}
