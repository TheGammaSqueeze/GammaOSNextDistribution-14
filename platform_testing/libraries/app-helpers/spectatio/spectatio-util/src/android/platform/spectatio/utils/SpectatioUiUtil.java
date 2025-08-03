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

package android.platform.spectatio.utils;

import android.app.Instrumentation;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.RemoteException;
import android.os.SystemClock;
import android.platform.spectatio.exceptions.MissingUiElementException;
import android.util.Log;
import android.view.KeyEvent;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.google.common.base.Strings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpectatioUiUtil {
    private static final String LOG_TAG = SpectatioUiUtil.class.getSimpleName();

    private static SpectatioUiUtil sSpectatioUiUtil = null;

    private static final int SHORT_UI_RESPONSE_WAIT_MS = 1000;
    private static final int LONG_UI_RESPONSE_WAIT_MS = 5000;
    private static final int EXTRA_LONG_UI_RESPONSE_WAIT_MS = 15000;
    private static final int LONG_PRESS_DURATION_MS = 5000;
    private static final int MAX_SCROLL_COUNT = 100;
    private static final int MAX_SWIPE_STEPS = 10;
    private static final float SCROLL_PERCENT = 1.0f;
    private static final float SWIPE_PERCENT = 1.0f;

    private int mWaitTimeAfterScroll = 5; // seconds
    private int mScrollMargin = 4;

    private UiDevice mDevice;

    public enum SwipeDirection {
        TOP_TO_BOTTOM,
        BOTTOM_TO_TOP,
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT
    }

    /**
     * Defines the swipe fraction, allowing for a swipe to be performed from a 5-pad distance, a
     * quarter, half, three-quarters of the screen, or the full screen.
     *
     * <p>DEFAULT: Swipe from one side of the screen to another side, with a 5-pad distance from the
     * edge.
     *
     * <p>QUARTER: Swipe from one side, a quarter of the distance of the entire screen away from the
     * edge, to the other side.
     *
     * <p>HALF: Swipe from the center of the screen to the other side.
     *
     * <p>THREEQUARTER: Swipe from one side, three-quarters of the distance of the entire screen
     * away from the edge, to the other side.
     *
     * <p>FULL: Swipe from one edge of the screen to the other edge.
     */
    public enum SwipeFraction {
        DEFAULT,
        QUARTER,
        HALF,
        THREEQUARTER,
        FULL,
    }

    /**
     * Defines the swipe speed based on the number of steps.
     *
     * <p><a
     * href="https://developer.android.com/reference/androidx/test/uiautomator/UiDevice#swipe(int,int,int,int,int)">UiDevie#Swipe</a>
     * performs a swipe from one coordinate to another using the number of steps to determine
     * smoothness and speed. Each step execution is throttled to 5ms per step. So for a 100 steps,
     * the swipe will take about 1/2 second to complete.
     */
    public enum SwipeSpeed {
        NORMAL(200), // equals to 1000ms in duration.
        SLOW(1000), // equals to 5000ms in duration.
        FAST(50), // equals to 250ms in duration.
        FLING(20); // equals to 100ms in duration.

        final int mNumSteps;

        SwipeSpeed(int numOfSteps) {
            this.mNumSteps = numOfSteps;
        }
    }

    private SpectatioUiUtil(UiDevice mDevice) {
        this.mDevice = mDevice;
    }

    public static SpectatioUiUtil getInstance(UiDevice mDevice) {
        if (sSpectatioUiUtil == null) {
            sSpectatioUiUtil = new SpectatioUiUtil(mDevice);
        }
        return sSpectatioUiUtil;
    }

    /**
     * Initialize a UiDevice for the given instrumentation, then initialize Spectatio for that
     * device. If Spectatio has already been initialized, return the previously initialized
     * instance.
     */
    public static SpectatioUiUtil getInstance(Instrumentation instrumentation) {
        return getInstance(UiDevice.getInstance(instrumentation));
    }

    /** Sets the scroll margin and wait time after the scroll */
    public void addScrollValues(Integer scrollMargin, Integer waitTime) {
        this.mScrollMargin = scrollMargin;
        this.mWaitTimeAfterScroll = waitTime;
    }

    public boolean pressBack() {
        return mDevice.pressBack();
    }

    public boolean pressHome() {
        return mDevice.pressHome();
    }

    public boolean pressKeyCode(int keyCode) {
        return mDevice.pressKeyCode(keyCode);
    }

    public boolean pressPower() {
        return pressKeyCode(KeyEvent.KEYCODE_POWER);
    }

    public boolean longPress(UiObject2 uiObject) {
        if (!isValidUiObject(uiObject)) {
            Log.e(
                    LOG_TAG,
                    "Cannot Long Press UI Object; Provide a valid UI Object, currently it is"
                            + " NULL.");
            return false;
        }
        if (!uiObject.isLongClickable()) {
            Log.e(
                    LOG_TAG,
                    "Cannot Long Press UI Object; Provide a valid UI Object, "
                            + "current UI Object is not long clickable.");
            return false;
        }
        uiObject.longClick();
        wait1Second();
        return true;
    }

    public boolean longPressKey(int keyCode) {
        try {
            // Use English Locale because ADB Shell command does not depend on Device UI
            mDevice.executeShellCommand(
                    String.format(Locale.ENGLISH, "input keyevent --longpress %d", keyCode));
            wait1Second();
            return true;
        } catch (IOException e) {
            // Ignore
            Log.e(
                    LOG_TAG,
                    String.format(
                            "Failed to long press key code: %d, Error: %s",
                            keyCode, e.getMessage()));
        }
        return false;
    }

    public boolean longPressPower() {
        return longPressKey(KeyEvent.KEYCODE_POWER);
    }

    public boolean longPressScreenCenter() {
        Rect bounds = getScreenBounds();
        int xCenter = bounds.centerX();
        int yCenter = bounds.centerY();
        try {
            // Click method in UiDevice only takes x and y co-ordintes to tap,
            // so it can be clicked but cannot be pressed for long time
            // Use ADB command to Swipe instead (because UiDevice swipe method don't take duration)
            // i.e. simulate long press by swiping from
            // center of screen to center of screen (i.e. same points) for long duration
            // Use English Locale because ADB Shell command does not depend on Device UI
            mDevice.executeShellCommand(
                    String.format(
                            Locale.ENGLISH,
                            "input swipe %d %d %d %d %d",
                            xCenter,
                            yCenter,
                            xCenter,
                            yCenter,
                            LONG_PRESS_DURATION_MS));
            wait1Second();
            return true;
        } catch (IOException e) {
            // Ignore
            Log.e(
                    LOG_TAG,
                    String.format(
                            "Failed to long press on screen center. Error: %s", e.getMessage()));
        }
        return false;
    }

    public void wakeUp() {
        try {
            mDevice.wakeUp();
        } catch (RemoteException ex) {
            throw new IllegalStateException("Failed to wake up device.", ex);
        }
    }

    public void clickAndWait(UiObject2 uiObject) {
        validateUiObjectAndThrowIllegalArgumentException(uiObject, /* action= */ "Click");
        uiObject.click();
        wait1Second();
    }

    /**
     * Click at a specific location in the UI, and wait one second
     *
     * @param location Where to click
     */
    public void clickAndWait(Point location) {
        mDevice.click(location.x, location.y);
        wait1Second();
    }

    public void waitForIdle() {
        mDevice.waitForIdle();
    }

    public void wait1Second() {
        waitNSeconds(SHORT_UI_RESPONSE_WAIT_MS);
    }

    public void wait5Seconds() {
        waitNSeconds(LONG_UI_RESPONSE_WAIT_MS);
    }

    /** Waits for 15 seconds */
    public void wait15Seconds() {
        waitNSeconds(EXTRA_LONG_UI_RESPONSE_WAIT_MS);
    }

    public void waitNSeconds(int waitTime) {
        SystemClock.sleep(waitTime);
    }

    /**
     * Executes a shell command on device, and return the standard output in string.
     *
     * @param command the command to run
     * @return the standard output of the command, or empty string if failed without throwing an
     *     IOException
     */
    public String executeShellCommand(String command) {
        validateText(command, /* type= */ "Command");
        try {
            return mDevice.executeShellCommand(command);
        } catch (IOException e) {
            // ignore
            Log.e(
                    LOG_TAG,
                    String.format(
                            "The shell command failed to run: %s, Error: %s",
                            command, e.getMessage()));
            return "";
        }
    }

    /** Find and return the UI Object that matches the given selector */
    public UiObject2 findUiObject(BySelector selector) {
        validateSelector(selector, /* action= */ "Find UI Object");
        UiObject2 uiObject = mDevice.wait(Until.findObject(selector), LONG_UI_RESPONSE_WAIT_MS);
        return uiObject;
    }

    /** Find and return the UI Objects that matches the given selector */
    public List<UiObject2> findUiObjects(BySelector selector) {
        validateSelector(selector, /* action= */ "Find UI Object");
        List<UiObject2> uiObjects =
                mDevice.wait(Until.findObjects(selector), LONG_UI_RESPONSE_WAIT_MS);
        return uiObjects;
    }

    /**
     * Find the UI Object that matches the given text string.
     *
     * @param text Text to search on device UI. It should exactly match the text visible on UI.
     */
    public UiObject2 findUiObject(String text) {
        validateText(text, /* type= */ "Text");
        return findUiObject(By.text(text));
    }

    /**
     * Find the UI Object in given element.
     *
     * @param uiObject Find the ui object(selector) in this element.
     * @param selector Find this ui object in the given element.
     */
    public UiObject2 findUiObjectInGivenElement(UiObject2 uiObject, BySelector selector) {
        validateUiObjectAndThrowIllegalArgumentException(
                uiObject, /* action= */ "Find UI object in given element");
        validateSelector(selector, /* action= */ "Find UI object in given element");
        return uiObject.findObject(selector);
    }

    /**
     * Checks if given text is available on the Device UI. The text should be exactly same as seen
     * on the screen.
     *
     * <p>Given text will be searched on current screen. This method will not scroll on the screen
     * to check for given text.
     *
     * @param text Text to search on device UI
     * @return Returns True if the text is found, else return False.
     */
    public boolean hasUiElement(String text) {
        validateText(text, /* type= */ "Text");
        return hasUiElement(By.text(text));
    }

    /**
     * Scroll using forward and backward buttons on device screen and check if the given text is
     * present.
     *
     * <p>Method throws {@link MissingUiElementException} if the given button selectors are not
     * available on the Device UI.
     *
     * @param forward {@link BySelector} for the button to use for scrolling forward/down.
     * @param backward {@link BySelector} for the button to use for scrolling backward/up.
     * @param text Text to search on device UI
     * @return Returns True if the text is found, else return False.
     */
    public boolean scrollAndCheckIfUiElementExist(
            BySelector forward, BySelector backward, String text) throws MissingUiElementException {
        return scrollAndFindUiObject(forward, backward, text) != null;
    }

    /**
     * Scroll by performing forward and backward gestures on device screen and check if the given
     * text is present on Device UI.
     *
     * <p>Scrolling will be performed vertically by default. For horizontal scrolling use {@code
     * scrollAndCheckIfUiElementExist(BySelector scrollableSelector, String text, boolean
     * isVertical)} by passing isVertical = false.
     *
     * <p>Method throws {@link MissingUiElementException} if the given scrollable selector is not
     * available on the Device UI.
     *
     * @param scrollableSelector {@link BySelector} used for scrolling on device UI
     * @param text Text to search on device UI
     * @return Returns True if the text is found, else return False.
     */
    public boolean scrollAndCheckIfUiElementExist(BySelector scrollableSelector, String text)
            throws MissingUiElementException {
        return scrollAndCheckIfUiElementExist(scrollableSelector, text, /* isVertical= */ true);
    }

    /**
     * Scroll by performing forward and backward gestures on device screen and check if the given
     * text is present on Device UI.
     *
     * <p>Method throws {@link MissingUiElementException} if the given scrollable selector is not
     * available on the Device UI.
     *
     * @param scrollableSelector {@link BySelector} used for scrolling on device UI
     * @param text Text to search on device UI
     * @param isVertical For vertical scrolling, use isVertical = true and For Horizontal scrolling,
     *     use isVertical = false.
     * @return Returns True if the text is found, else return False.
     */
    public boolean scrollAndCheckIfUiElementExist(
            BySelector scrollableSelector, String text, boolean isVertical)
            throws MissingUiElementException {
        return scrollAndFindUiObject(scrollableSelector, text, isVertical) != null;
    }

    /**
     * Checks if given target is available on the Device UI.
     *
     * <p>Given target will be searched on current screen. This method will not scroll on the screen
     * to check for given target.
     *
     * @param target {@link BySelector} to search on device UI
     * @return Returns True if the target is found, else return False.
     */
    public boolean hasUiElement(BySelector target) {
        validateSelector(target, /* action= */ "Check For UI Object");
        return mDevice.hasObject(target);
    }

    /**
     * Scroll using forward and backward buttons on device screen and check if the given target is
     * present.
     *
     * <p>Method throws {@link MissingUiElementException} if the given button selectors are not
     * available on the Device UI.
     *
     * @param forward {@link BySelector} for the button to use for scrolling forward/down.
     * @param backward {@link BySelector} for the button to use for scrolling backward/up.
     * @param target {@link BySelector} to search on device UI
     * @return Returns True if the target is found, else return False.
     */
    public boolean scrollAndCheckIfUiElementExist(
            BySelector forward, BySelector backward, BySelector target)
            throws MissingUiElementException {
        return scrollAndFindUiObject(forward, backward, target) != null;
    }

    /**
     * Scroll by performing forward and backward gestures on device screen and check if the target
     * UI Element is present.
     *
     * <p>Scrolling will be performed vertically by default. For horizontal scrolling use {@code
     * scrollAndCheckIfUiElementExist(BySelector scrollableSelector, BySelector target, boolean
     * isVertical)} by passing isVertical = false.
     *
     * <p>Method throws {@link MissingUiElementException} if the given scrollable selector is not
     * available on the Device UI.
     *
     * @param scrollableSelector {@link BySelector} used for scrolling on device UI
     * @param target {@link BySelector} to search on device UI
     * @return Returns True if the target is found, else return False.
     */
    public boolean scrollAndCheckIfUiElementExist(BySelector scrollableSelector, BySelector target)
            throws MissingUiElementException {
        return scrollAndCheckIfUiElementExist(scrollableSelector, target, /* isVertical= */ true);
    }

    /**
     * Scroll by performing forward and backward gestures on device screen and check if the target
     * UI Element is present.
     *
     * <p>Method throws {@link MissingUiElementException} if the given scrollable selector is not
     * available on the Device UI.
     *
     * @param scrollableSelector {@link BySelector} used for scrolling on device UI
     * @param target {@link BySelector} to search on device UI
     * @param isVertical For vertical scrolling, use isVertical = true and For Horizontal scrolling,
     *     use isVertical = false.
     * @return Returns True if the target is found, else return False.
     */
    public boolean scrollAndCheckIfUiElementExist(
            BySelector scrollableSelector, BySelector target, boolean isVertical)
            throws MissingUiElementException {
        return scrollAndFindUiObject(scrollableSelector, target, isVertical) != null;
    }

    public boolean hasPackageInForeground(String packageName) {
        validateText(packageName, /* type= */ "Package");
        return mDevice.hasObject(By.pkg(packageName).depth(0));
    }

    public void swipeUp() {
        // Swipe Up From bottom of screen to the top in one step
        swipe(SwipeDirection.BOTTOM_TO_TOP, /*numOfSteps*/ MAX_SWIPE_STEPS);
    }

    public void swipeDown() {
        // Swipe Down From top of screen to the bottom in one step
        swipe(SwipeDirection.TOP_TO_BOTTOM, /*numOfSteps*/ MAX_SWIPE_STEPS);
    }

    public void swipeRight() {
        // Swipe Right From left of screen to the right in one step
        swipe(SwipeDirection.LEFT_TO_RIGHT, /*numOfSteps*/ MAX_SWIPE_STEPS);
    }

    public void swipeLeft() {
        // Swipe Left From right of screen to the left in one step
        swipe(SwipeDirection.RIGHT_TO_LEFT, /*numOfSteps*/ MAX_SWIPE_STEPS);
    }

    public void swipe(SwipeDirection swipeDirection, int numOfSteps) {
        swipe(swipeDirection, numOfSteps, SwipeFraction.DEFAULT);
    }

    /**
     * Perform a swipe gesture
     *
     * @param swipeDirection The direction to perform the swipe in
     * @param numOfSteps How many steps the swipe will take
     * @param swipeFraction The fraction of the screen to swipe across
     */
    public void swipe(SwipeDirection swipeDirection, int numOfSteps, SwipeFraction swipeFraction) {
        Rect bounds = getScreenBounds();

        List<Point> swipePoints = getPointsToSwipe(bounds, swipeDirection, swipeFraction);

        Point startPoint = swipePoints.get(0);
        Point finishPoint = swipePoints.get(1);

        // Swipe from start pont to finish point in given number of steps
        mDevice.swipe(startPoint.x, startPoint.y, finishPoint.x, finishPoint.y, numOfSteps);
    }

    /**
     * Perform a swipe gesture
     *
     * @param swipeDirection The direction to perform the swipe in
     * @param swipeSpeed How fast to swipe
     */
    public void swipe(SwipeDirection swipeDirection, SwipeSpeed swipeSpeed) throws IOException {
        swipe(swipeDirection, swipeSpeed.mNumSteps);
    }

    /**
     * Perform a swipe gesture
     *
     * @param swipeDirection The direction to perform the swipe in
     * @param swipeSpeed How fast to swipe
     * @param swipeFraction The fraction of the screen to swipe across
     */
    public void swipe(
            SwipeDirection swipeDirection, SwipeSpeed swipeSpeed, SwipeFraction swipeFraction)
            throws IOException {
        swipe(swipeDirection, swipeSpeed.mNumSteps, swipeFraction);
    }

    private List<Point> getPointsToSwipe(
            Rect bounds, SwipeDirection swipeDirection, SwipeFraction swipeFraction) {
        int xStart;
        int yStart;
        int xFinish;
        int yFinish;

        int padXStart = 5;
        int padXFinish = 5;
        int padYStart = 5;
        int padYFinish = 5;

        switch (swipeFraction) {
            case FULL:
                padXStart = 0;
                padXFinish = 0;
                padYStart = 0;
                padYFinish = 0;
                break;
            case QUARTER:
                padXStart = bounds.right / 4;
                padYStart = bounds.bottom / 4;
                break;
            case HALF:
                padXStart = bounds.centerX();
                padYStart = bounds.centerY();
                break;
            case THREEQUARTER:
                padXStart = bounds.right / 4 * 3;
                padYStart = bounds.bottom / 4 * 3;
                break;
        }

        switch (swipeDirection) {
                // Scroll left = swipe from left to right.
            case LEFT_TO_RIGHT:
                xStart = bounds.left + padXStart;
                xFinish = bounds.right - padXFinish;
                yStart = bounds.centerY();
                yFinish = bounds.centerY();
                break;
                // Scroll right = swipe from right to left.
            case RIGHT_TO_LEFT:
                xStart = bounds.right - padXStart;
                xFinish = bounds.left + padXFinish;
                yStart = bounds.centerY();
                yFinish = bounds.centerY();
                break;
                // Scroll up = swipe from top to bottom.
            case TOP_TO_BOTTOM:
                xStart = bounds.centerX();
                xFinish = bounds.centerX();
                yStart = bounds.top + padYStart;
                yFinish = bounds.bottom - padYFinish;
                break;
                // Scroll down = swipe to bottom to top.
            case BOTTOM_TO_TOP:
            default:
                xStart = bounds.centerX();
                xFinish = bounds.centerX();
                yStart = bounds.bottom - padYStart;
                yFinish = bounds.top + padYFinish;
                break;
        }

        List<Point> swipePoints = new ArrayList<>();
        // Start Point
        swipePoints.add(new Point(xStart, yStart));
        // Finish Point
        swipePoints.add(new Point(xFinish, yFinish));

        return swipePoints;
    }

    private Rect getScreenBounds() {
        Point dimensions = mDevice.getDisplaySizeDp();
        return new Rect(0, 0, dimensions.x, dimensions.y);
    }

    public void swipeRight(UiObject2 uiObject) {
        validateUiObjectAndThrowIllegalArgumentException(uiObject, /* action= */ "Swipe Right");
        uiObject.swipe(Direction.RIGHT, SWIPE_PERCENT);
    }

    public void swipeLeft(UiObject2 uiObject) {
        validateUiObjectAndThrowIllegalArgumentException(uiObject, /* action= */ "Swipe Left");
        uiObject.swipe(Direction.LEFT, SWIPE_PERCENT);
    }

    public void swipeUp(UiObject2 uiObject) {
        validateUiObjectAndThrowIllegalArgumentException(uiObject, /* action= */ "Swipe Up");
        uiObject.swipe(Direction.UP, SWIPE_PERCENT);
    }

    public void swipeDown(UiObject2 uiObject) {
        validateUiObjectAndThrowIllegalArgumentException(uiObject, /* action= */ "Swipe Down");
        uiObject.swipe(Direction.DOWN, SWIPE_PERCENT);
    }

    public void setTextForUiElement(UiObject2 uiObject, String text) {
        validateUiObjectAndThrowIllegalArgumentException(uiObject, /* action= */ "Set Text");
        validateText(text, /* type= */ "Text");
        uiObject.setText(text);
    }

    public String getTextForUiElement(UiObject2 uiObject) {
        validateUiObjectAndThrowIllegalArgumentException(uiObject, /* action= */ "Get Text");
        return uiObject.getText();
    }

    /**
     * Scroll on the device screen using forward or backward buttons.
     *
     * <p>Pass Forward/Down Button Selector to scroll forward. Pass Backward/Up Button Selector to
     * scroll backward. Method throws {@link MissingUiElementException} if the given button is not
     * available on the Device UI.
     *
     * @param scrollButtonSelector {@link BySelector} for the button to use for scrolling.
     * @return Method returns true for successful scroll else returns false
     */
    public boolean scrollUsingButton(BySelector scrollButtonSelector)
            throws MissingUiElementException {
        validateSelector(scrollButtonSelector, /* action= */ "Scroll Using Button");
        UiObject2 scrollButton = findUiObject(scrollButtonSelector);
        validateUiObjectAndThrowMissingUiElementException(
                scrollButton, scrollButtonSelector, /* action= */ "Scroll Using Button");

        String previousView = getViewHierarchy();
        if (!scrollButton.isEnabled()) {
            // Already towards the end, cannot scroll
            return false;
        }

        clickAndWait(scrollButton);

        String currentView = getViewHierarchy();

        // If current view is same as previous view, scroll did not work, so return false
        return !currentView.equals(previousView);
    }

    /**
     * Scroll using forward and backward buttons on device screen and find the text.
     *
     * <p>Method throws {@link MissingUiElementException} if the given button selectors are not
     * available on the Device UI.
     *
     * @param forward {@link BySelector} for the button to use for scrolling forward/down.
     * @param backward {@link BySelector} for the button to use for scrolling backward/up.
     * @param text Text to search on device UI. It should be exactly same as visible on device UI.
     * @return {@link UiObject2} for given text will be returned. It returns NULL if given text is
     *     not found on the Device UI.
     */
    public UiObject2 scrollAndFindUiObject(BySelector forward, BySelector backward, String text)
            throws MissingUiElementException {
        validateText(text, /* type= */ "Text");
        return scrollAndFindUiObject(forward, backward, By.text(text));
    }

    /**
     * Scroll using forward and backward buttons on device screen and find the target UI Element.
     *
     * <p>Method throws {@link MissingUiElementException} if the given button selectors are not
     * available on the Device UI.
     *
     * @param forward {@link BySelector} for the button to use for scrolling forward/down.
     * @param backward {@link BySelector} for the button to use for scrolling backward/up.
     * @param target {@link BySelector} for UI Element to search on device UI.
     * @return {@link UiObject2} for target UI Element will be returned. It returns NULL if given
     *     target is not found on the Device UI.
     */
    public UiObject2 scrollAndFindUiObject(
            BySelector forward, BySelector backward, BySelector target)
            throws MissingUiElementException {
        validateSelector(forward, /* action= */ "Scroll Forward");
        validateSelector(backward, /* action= */ "Scroll Backward");
        validateSelector(target, /* action= */ "Find UI Object");
        // Find the object on current page
        UiObject2 uiObject = findUiObject(target);
        if (isValidUiObject(uiObject)) {
            return uiObject;
        }
        scrollToBeginning(backward);
        return scrollForwardAndFindUiObject(forward, target);
    }

    private UiObject2 scrollForwardAndFindUiObject(BySelector forward, BySelector target)
            throws MissingUiElementException {
        UiObject2 uiObject = findUiObject(target);
        if (isValidUiObject(uiObject)) {
            return uiObject;
        }
        int scrollCount = 0;
        boolean canScroll = true;
        while (!isValidUiObject(uiObject) && canScroll && scrollCount < MAX_SCROLL_COUNT) {
            canScroll = scrollUsingButton(forward);
            scrollCount++;
            uiObject = findUiObject(target);
        }
        return uiObject;
    }

    public void scrollToBeginning(BySelector backward) throws MissingUiElementException {
        int scrollCount = 0;
        boolean canScroll = true;
        while (canScroll && scrollCount < MAX_SCROLL_COUNT) {
            canScroll = scrollUsingButton(backward);
            scrollCount++;
        }
    }

    /**
     * Swipe in a direction until a target UI Object is found
     *
     * @param swipeDirection Direction to swipe
     * @param numOfSteps Ticks per swipe
     * @param swipeFraction How far to swipe
     * @param target The UI Object to find
     * @return The found object, or null if there isn't one
     */
    public UiObject2 swipeAndFindUiObject(
            SwipeDirection swipeDirection,
            int numOfSteps,
            SwipeFraction swipeFraction,
            BySelector target) {
        validateSelector(target, "Find UI Object");
        UiObject2 uiObject = findUiObject(target);
        if (isValidUiObject(uiObject)) {
            return uiObject;
        }

        String previousView = null;
        String currentView = getViewHierarchy();
        while (!currentView.equals(previousView)) {
            swipe(swipeDirection, numOfSteps, swipeFraction);
            uiObject = findUiObject(target);
            if (isValidUiObject(uiObject)) {
                return uiObject;
            }
            previousView = currentView;
            currentView = getViewHierarchy();
        }
        return null;
    }

    private String getViewHierarchy() {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            mDevice.dumpWindowHierarchy(outputStream);
            outputStream.close();
            return outputStream.toString();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to get view hierarchy.");
        }
    }

    /**
     * Scroll by performing forward and backward gestures on device screen and find the text.
     *
     * <p>Scrolling will be performed vertically by default. For horizontal scrolling use {@code
     * scrollAndFindUiObject(BySelector scrollableSelector, String text, boolean isVertical)} by
     * passing isVertical = false.
     *
     * <p>Method throws {@link MissingUiElementException} if the given scrollable selector is not
     * available on the Device UI.
     *
     * @param scrollableSelector {@link BySelector} for the scrollable UI Element on device UI.
     * @param text Text to search on device UI. It should be exactly same as visible on device UI.
     * @return {@link UiObject2} for given text will be returned. It returns NULL if given text is
     *     not found on the Device UI.
     */
    public UiObject2 scrollAndFindUiObject(BySelector scrollableSelector, String text)
            throws MissingUiElementException {
        validateText(text, /* type= */ "Text");
        return scrollAndFindUiObject(scrollableSelector, By.text(text));
    }

    /**
     * Scroll by performing forward and backward gestures on device screen and find the text.
     *
     * <p>For vertical scrolling, use isVertical = true For Horizontal scrolling, use isVertical =
     * false.
     *
     * <p>Method throws {@link MissingUiElementException} if the given scrollable selector is not
     * available on the Device UI.
     *
     * @param scrollableSelector {@link BySelector} for the scrollable UI Element on device UI.
     * @param text Text to search on device UI. It should be exactly same as visible on device UI.
     * @return {@link UiObject2} for given text will be returned. It returns NULL if given text is
     *     not found on the Device UI.
     */
    public UiObject2 scrollAndFindUiObject(
            BySelector scrollableSelector, String text, boolean isVertical)
            throws MissingUiElementException {
        validateText(text, /* type= */ "Text");
        return scrollAndFindUiObject(scrollableSelector, By.text(text), isVertical);
    }

    /**
     * Scroll by performing forward and backward gestures on device screen and find the target UI
     * Element.
     *
     * <p>Scrolling will be performed vertically by default. For horizontal scrolling use {@code
     * scrollAndFindUiObject(BySelector scrollableSelector, BySelector target, boolean isVertical)}
     * by passing isVertical = false.
     *
     * <p>Method throws {@link MissingUiElementException} if the given scrollable selector is not
     * available on the Device UI.
     *
     * @param scrollableSelector {@link BySelector} for the scrollable UI Element on device UI.
     * @param target {@link BySelector} for UI Element to search on device UI.
     * @return {@link UiObject2} for target UI Element will be returned. It returns NULL if given
     *     target is not found on the Device UI.
     */
    public UiObject2 scrollAndFindUiObject(BySelector scrollableSelector, BySelector target)
            throws MissingUiElementException {
        return scrollAndFindUiObject(scrollableSelector, target, /* isVertical= */ true);
    }

    /**
     * Scroll by performing forward and backward gestures on device screen and find the target UI
     * Element.
     *
     * <p>For vertical scrolling, use isVertical = true For Horizontal scrolling, use isVertical =
     * false.
     *
     * <p>Method throws {@link MissingUiElementException} if the given scrollable selector is not
     * available on the Device UI.
     *
     * @param scrollableSelector {@link BySelector} for the scrollable UI Element on device UI.
     * @param target {@link BySelector} for UI Element to search on device UI.
     * @param isVertical For vertical scrolling, use isVertical = true and For Horizontal scrolling,
     *     use isVertical = false.
     * @return {@link UiObject2} for target UI Element will be returned. It returns NULL if given
     *     target is not found on the Device UI.
     */
    public UiObject2 scrollAndFindUiObject(
            BySelector scrollableSelector, BySelector target, boolean isVertical)
            throws MissingUiElementException {
        validateSelector(scrollableSelector, /* action= */ "Scroll");
        validateSelector(target, /* action= */ "Find UI Object");
        // Find UI element on current page
        UiObject2 uiObject = findUiObject(target);
        if (isValidUiObject(uiObject)) {
            return uiObject;
        }
        scrollToBeginning(scrollableSelector, isVertical);
        return scrollForwardAndFindUiObject(scrollableSelector, target, isVertical);
    }

    private UiObject2 scrollForwardAndFindUiObject(
            BySelector scrollableSelector, BySelector target, boolean isVertical)
            throws MissingUiElementException {
        UiObject2 uiObject = findUiObject(target);
        if (isValidUiObject(uiObject)) {
            return uiObject;
        }
        int scrollCount = 0;
        boolean canScroll = true;
        while (!isValidUiObject(uiObject) && canScroll && scrollCount < MAX_SCROLL_COUNT) {
            canScroll = scrollForward(scrollableSelector, isVertical);
            scrollCount++;
            uiObject = findUiObject(target);
        }
        return uiObject;
    }

    public void scrollToBeginning(BySelector scrollableSelector, boolean isVertical)
            throws MissingUiElementException {
        int scrollCount = 0;
        boolean canScroll = true;
        while (canScroll && scrollCount < MAX_SCROLL_COUNT) {
            canScroll = scrollBackward(scrollableSelector, isVertical);
            scrollCount++;
        }
    }

    private Direction getDirection(boolean isVertical, boolean scrollForward) {
        // Default Scroll = Vertical and Forward
        // Go DOWN to scroll forward vertically
        Direction direction = Direction.DOWN;
        if (isVertical && !scrollForward) {
            // Scroll = Vertical and Backward
            // Go UP to scroll backward vertically
            direction = Direction.UP;
        }
        if (!isVertical && scrollForward) {
            // Scroll = Horizontal and Forward
            // Go RIGHT to scroll forward horizontally
            direction = Direction.RIGHT;
        }
        if (!isVertical && !scrollForward) {
            // Scroll = Horizontal and Backward
            // Go LEFT to scroll backward horizontally
            direction = Direction.LEFT;
        }
        return direction;
    }

    private UiObject2 validateAndGetScrollableObject(BySelector scrollableSelector)
            throws MissingUiElementException {
        UiObject2 scrollableObject = findUiObject(scrollableSelector);
        validateUiObjectAndThrowMissingUiElementException(
                scrollableObject, scrollableSelector, /* action= */ "Scroll");
        if (!scrollableObject.isScrollable()) {
            scrollableObject = scrollableObject.findObject(By.scrollable(true));
        }
        if ((scrollableObject == null) || !scrollableObject.isScrollable()) {
            throw new IllegalStateException(
                    String.format(
                            "Cannot scroll; UI Object for selector %s is not scrollable and has no"
                                    + " scrollable children.",
                            scrollableSelector));
        }
        return scrollableObject;
    }

    /**
     * Scroll forward one page by performing forward gestures on device screen.
     *
     * <p>Scrolling will be performed vertically by default. For horizontal scrolling use {@code
     * scrollForward(BySelector scrollableSelector, boolean isVertical)} by passing isVertical =
     * false.
     *
     * <p>Method throws {@link MissingUiElementException} if given scrollable selector is not
     * available on the Device UI.
     *
     * @param scrollableSelector {@link BySelector} for the scrollable UI Element on device UI.
     * @return Returns true for successful forward scroll, else false.
     */
    public boolean scrollForward(BySelector scrollableSelector) throws MissingUiElementException {
        return scrollForward(scrollableSelector, /* isVertical= */ true);
    }

    /**
     * Scroll forward one page by performing forward gestures on device screen.
     *
     * <p>For vertical scrolling, use isVertical = true For Horizontal scrolling, use isVertical =
     * false.
     *
     * <p>Method throws {@link MissingUiElementException} if given scrollable selector is not
     * available on the Device UI.
     *
     * @param scrollableSelector {@link BySelector} for the scrollable UI Element on device UI.
     * @return Returns true for successful forward scroll, else false.
     */
    public boolean scrollForward(BySelector scrollableSelector, boolean isVertical)
            throws MissingUiElementException {
        return scroll(scrollableSelector, getDirection(isVertical, /* scrollForward= */ true));
    }

    /**
     * Scroll backward one page by performing backward gestures on device screen.
     *
     * <p>Scrolling will be performed vertically by default. For horizontal scrolling use {@code
     * scrollBackward(BySelector scrollableSelector, boolean isVertical)} by passing isVertical =
     * false.
     *
     * <p>Method throws {@link MissingUiElementException} if given scrollable selector is not
     * available on the Device UI.
     *
     * @param scrollableSelector {@link BySelector} for the scrollable UI Element on device UI.
     * @return Returns true for successful backard scroll, else false.
     */
    public boolean scrollBackward(BySelector scrollableSelector) throws MissingUiElementException {
        return scrollBackward(scrollableSelector, /* isVertical= */ true);
    }

    /**
     * Scroll backward one page by performing backward gestures on device screen.
     *
     * <p>For vertical scrolling, use isVertical = true For Horizontal scrolling, use isVertical =
     * false.
     *
     * <p>Method throws {@link MissingUiElementException} if given scrollable selector is not
     * available on the Device UI.
     *
     * @param scrollableSelector {@link BySelector} for the scrollable UI Element on device UI.
     * @return Returns true for successful backward scroll, else false.
     */
    public boolean scrollBackward(BySelector scrollableSelector, boolean isVertical)
            throws MissingUiElementException {
        return scroll(scrollableSelector, getDirection(isVertical, /* scrollForward= */ false));
    }

    private boolean scroll(BySelector scrollableSelector, Direction direction)
            throws MissingUiElementException {

        UiObject2 scrollableObject = validateAndGetScrollableObject(scrollableSelector);

        Rect bounds = scrollableObject.getVisibleBounds();
        int horizontalMargin = (int) (Math.abs(bounds.width()) / mScrollMargin);
        int verticalMargin = (int) (Math.abs(bounds.height()) / mScrollMargin);

        scrollableObject.setGestureMargins(
                horizontalMargin, // left
                verticalMargin, // top
                horizontalMargin, // right
                verticalMargin); // bottom

        String previousView = getViewHierarchy();

        scrollableObject.scroll(direction, SCROLL_PERCENT);
        waitNSeconds(mWaitTimeAfterScroll);

        String currentView = getViewHierarchy();

        // If current view is same as previous view, scroll did not work, so return false
        return !currentView.equals(previousView);
    }

    private void validateText(String text, String type) {
        if (Strings.isNullOrEmpty(text)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Provide a valid %s, current %s value is either NULL or empty.",
                            type, type));
        }
    }

    private void validateSelector(BySelector selector, String action) {
        if (selector == null) {
            throw new IllegalArgumentException(
                    String.format(
                            "Cannot %s; Provide a valid selector to %s, currently it is NULL.",
                            action, action));
        }
    }

    /**
     * A simple null-check on a single uiObject2 instance
     *
     * @param uiObject - The object to be checked.
     * @param action - The UI action being performed when the object was generated or searched-for.
     */
    public void validateUiObject(UiObject2 uiObject, String action) {
        if (uiObject == null) {
            throw new MissingUiElementException(
                    String.format("Unable to find UI Element for %s.", action));
        }
    }

    /**
     * A simple null-check on a list of UIObjects
     *
     * @param uiObjects - The list to check
     * @param action - A string description of the UI action being taken when this list was
     *     generated.
     */
    public void validateUiObjects(List<UiObject2> uiObjects, String action) {
        if (uiObjects == null) {
            throw new MissingUiElementException(
                    String.format("Unable to find UI Element for %s.", action));
        }
    }

    public boolean isValidUiObject(UiObject2 uiObject) {
        return uiObject != null;
    }

    private void validateUiObjectAndThrowIllegalArgumentException(
            UiObject2 uiObject, String action) {
        if (!isValidUiObject(uiObject)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Cannot %s; Provide a valid UI Object to %s, currently it is NULL.",
                            action, action));
        }
    }

    private void validateUiObjectAndThrowMissingUiElementException(
            UiObject2 uiObject, BySelector selector, String action)
            throws MissingUiElementException {
        if (!isValidUiObject(uiObject)) {
            throw new MissingUiElementException(
                    String.format(
                            "Cannot %s; Unable to find UI Object for %s selector.",
                            action, selector));
        }
    }
}
