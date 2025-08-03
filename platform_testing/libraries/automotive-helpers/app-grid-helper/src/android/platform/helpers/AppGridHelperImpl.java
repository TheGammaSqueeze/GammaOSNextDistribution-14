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

import android.app.Instrumentation;
import android.platform.helpers.ScrollUtility.ScrollActions;
import android.platform.helpers.ScrollUtility.ScrollDirection;
import android.platform.spectatio.exceptions.MissingUiElementException;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

/** Helper class for functional test for App Grid test */
public class AppGridHelperImpl extends AbstractStandardAppHelper implements IAutoAppGridHelper {
    private ScrollUtility mScrollUtility;
    private ScrollActions mScrollAction;
    private BySelector mBackwardButtonSelector;
    private BySelector mForwardButtonSelector;
    private BySelector mScrollableElementSelector;
    private ScrollDirection mScrollDirection;

    public AppGridHelperImpl(Instrumentation instr) {
        super(instr);
        mScrollUtility = ScrollUtility.getInstance(getSpectatioUiUtil());
        mScrollAction =
                ScrollActions.valueOf(
                        getActionFromConfig(AutomotiveConfigConstants.APP_LIST_SCROLL_ACTION));
        mBackwardButtonSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.APP_GRID_SCROLL_BACKWARD_BUTTON);
        mForwardButtonSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.APP_GRID_SCROLL_FORWARD_BUTTON);
        mScrollableElementSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.APP_LIST_SCROLL_ELEMENT);
        mScrollDirection =
                ScrollDirection.valueOf(
                        getActionFromConfig(AutomotiveConfigConstants.APP_LIST_SCROLL_DIRECTION));
    }

    /** {@inheritDoc} */
    @Override
    public String getPackage() {
        return getPackageFromConfig(AutomotiveConfigConstants.APP_GRID_PACKAGE);
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

    /**
     * Setup expectation: None.
     *
     * <p>Open app grid by pressing app grid facet ony if app grid is not open.
     */
    @Override
    public void open() {
        if (!isAppInForeground()) {
            getSpectatioUiUtil()
                    .executeShellCommand(
                            getCommandFromConfig(AutomotiveConfigConstants.OPEN_APP_GRID_COMMAND));
            getSpectatioUiUtil().wait5Seconds();
        }
    }

    /**
     * Setup expectations: None
     *
     * <p>Check if app grid is in foreground.
     */
    @Override
    public boolean isAppInForeground() {
        BySelector appGridViewIdSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.APP_GRID_VIEW_ID);
        return getSpectatioUiUtil().hasUiElement(appGridViewIdSelector);
    }

    /**
     * Setup expectation: None.
     *
     * <p>Exit app grid by pressing home facet only if app grid is open.
     */
    @Override
    public void exit() {
        if (isAppInForeground()) {
            getSpectatioUiUtil().pressHome();
        }
    }

    @Override
    public void openApp(String appName) {
        BySelector appNameSelector = By.text(appName);

        UiObject2 app =
                mScrollUtility.scrollAndFindUiObject(
                        mScrollAction,
                        mScrollDirection,
                        mForwardButtonSelector,
                        mBackwardButtonSelector,
                        mScrollableElementSelector,
                        appNameSelector,
                        String.format("Scroll on app grid to find %s", appName));

        getSpectatioUiUtil().validateUiObject(app, String.format("Given app %s", appName));
        getSpectatioUiUtil().clickAndWait(app);
        getSpectatioUiUtil().wait5Seconds();
    }

    /** {@inherticDoc} */
    @Override
    public boolean isAtBeginning() {
        boolean isAtBeginning = false;
        try {
            if (isAppInForeground()) {
                UiObject2 pageUp = getSpectatioUiUtil().findUiObject(mBackwardButtonSelector);
                if (pageUp != null) {
                    isAtBeginning = !pageUp.isEnabled();
                } else {
                    boolean isScrollable =
                            getSpectatioUiUtil()
                                    .findUiObject(mScrollableElementSelector)
                                    .isScrollable();
                    if (isScrollable) {
                        isAtBeginning =
                                !getSpectatioUiUtil()
                                        .scrollBackward(
                                                mScrollableElementSelector,
                                                (mScrollDirection == ScrollDirection.VERTICAL));
                        if (!isAtBeginning) {
                            // To place the scroll in previous position
                            getSpectatioUiUtil()
                                    .scrollForward(
                                            mScrollableElementSelector,
                                            (mScrollDirection == ScrollDirection.VERTICAL));
                        }
                    } else {
                        // Number of apps fits in one page, at top by default
                        isAtBeginning = true;
                    }
                }
            }
            return isAtBeginning;
        } catch (MissingUiElementException ex) {
            throw new IllegalStateException("App grid is not open.");
        }
    }

    /** {@inherticDoc} */
    @Override
    public boolean isAtEnd() {
        boolean isAtEnd = false;
        try {
            if (isAppInForeground()) {
                UiObject2 pageDown = getSpectatioUiUtil().findUiObject(mForwardButtonSelector);
                if (pageDown != null) {
                    isAtEnd = !pageDown.isEnabled();
                } else {
                    boolean isScrollable =
                            getSpectatioUiUtil()
                                    .findUiObject(mScrollableElementSelector)
                                    .isScrollable();
                    if (isScrollable) {
                        isAtEnd =
                                !getSpectatioUiUtil()
                                        .scrollForward(
                                                mScrollableElementSelector,
                                                (mScrollDirection == ScrollDirection.VERTICAL));
                        if (!isAtEnd) {
                            // To place the scroll in previous position
                            getSpectatioUiUtil()
                                    .scrollBackward(
                                            mScrollableElementSelector,
                                            (mScrollDirection == ScrollDirection.VERTICAL));
                        }
                    } else {
                        // Number of apps fits in one page, at top by default
                        isAtEnd = true;
                    }
                }
            }
            return isAtEnd;
        } catch (MissingUiElementException ex) {
            throw new IllegalStateException("App grid is not open.");
        }
    }

    @Override
    public boolean scrollBackward() {
        return mScrollUtility.scrollBackward(
                mScrollAction,
                mScrollDirection,
                mBackwardButtonSelector,
                mScrollableElementSelector,
                String.format("Scroll backward on app grid"));
    }

    /** {@inheritDoc} */
    @Override
    public void scrollToBeginning() {
        mScrollUtility.scrollToBeginning(
                mScrollAction,
                mScrollDirection,
                mBackwardButtonSelector,
                mScrollableElementSelector,
                "Scroll to beginning of app grid");
    }

    @Override
    public boolean scrollForward() {
        return mScrollUtility.scrollForward(
                mScrollAction,
                mScrollDirection,
                mForwardButtonSelector,
                mScrollableElementSelector,
                String.format("Scroll forward on app grid"));
    }

    /** {@inheritDoc} */
    @Override
    public String getScreenBlockingMessage(String appName) {
        BySelector screenBlockingSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.BLOCKING_SCREEN);
        UiObject2 screenBlocking = getSpectatioUiUtil().findUiObject(screenBlockingSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        screenBlocking,
                        String.format("Screen Blocking message when opening %s app", appName));
        BySelector screenBlockingMessageSelector =
                getUiElementFromConfig(AutomotiveConfigConstants.BLOCKING_MESSAGE);
        UiObject2 screenBlockingMessage =
                getSpectatioUiUtil().findUiObject(screenBlockingMessageSelector);
        getSpectatioUiUtil()
                .validateUiObject(
                        screenBlockingMessage,
                        String.format("Screen Blocking message for %s app", appName));
        return getSpectatioUiUtil().getTextForUiElement(screenBlockingMessage);
    }

    /** To verify if given package is in Foreground */
    @Override
    public boolean checkPackageInForeground(String packageName) {
        String foregroundPackage = getPackageFromConfig(packageName);
        return getSpectatioUiUtil().hasPackageInForeground(foregroundPackage);
    }

    @Override
    public void goToHomePage() {
        getSpectatioUiUtil().pressHome();
        getSpectatioUiUtil().wait1Second();
    }
}
