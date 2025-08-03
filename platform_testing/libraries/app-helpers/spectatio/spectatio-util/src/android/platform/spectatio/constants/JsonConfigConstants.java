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

package android.platform.spectatio.constants;

public class JsonConfigConstants {
    // Spectatio Config Constants
    public static final String ACTIONS = "ACTIONS";
    public static final String COMMANDS = "COMMANDS";
    public static final String PACKAGES = "PACKAGES";
    public static final String UI_ELEMENTS = "UI_ELEMENTS";
    public static final String WORKFLOWS = "WORKFLOWS";

    // UI Element Constants
    public static final String TYPE = "TYPE";
    public static final String VALUE = "VALUE";
    public static final String FLAG = "FLAG";
    public static final String PACKAGE = "PACKAGE";
    public static final String ANCESTOR = "ANCESTOR";
    public static final String DESCENDANT = "DESCENDANT";
    public static final String MAX_DEPTH = "MAX_DEPTH";
    public static final String SPECIFIERS = "SPECIFIERS";

    public static final String RESOURCE_ID = "RESOURCE_ID";
    public static final String SCROLLABLE = "SCROLLABLE";
    public static final String CLICKABLE = "CLICKABLE";
    public static final String TEXT = "TEXT";
    public static final String TEXT_CONTAINS = "TEXT_CONTAINS";
    public static final String DESCRIPTION = "DESCRIPTION";
    public static final String CLASS = "CLASS";
    public static final String HAS_ANCESTOR = "HAS_ANCESTOR";
    public static final String HAS_DESCENDANT = "HAS_DESCENDANT";
    public static final String MULTIPLE = "MULTIPLE";

    // Workflow Task Constants
    // Supported Properties
    public static final String NAME = "NAME";
    public static final String WORKFLOW_TYPE = "TYPE";
    public static final String CONFIG = "CONFIG";
    public static final String REPEAT_COUNT = "REPEAT_COUNT";
    public static final String SCROLL_CONFIG = "SCROLL_CONFIG";
    public static final String SWIPE_CONFIG = "SWIPE_CONFIG";

    // Supported Workflow Tasks
    public static enum SupportedWorkFlowTasks {
        // Execute the given Command
        COMMAND,
        // Press Key e.g. Home, Back, Power, etc.
        PRESS,
        // Long Press Key e.g. Power, Screen Center, etc.
        LONG_PRESS,
        // Click on given UI Element ( throws exception if UI Element does not exist )
        CLICK,
        // Click on given UI Element if it exist otherwise ignore ( i.e. No Exception
        // even if UI Element is missing )
        CLICK_IF_EXIST,
        // Long Click on given UI Element ( throws exception if UI Element does not exist )
        LONG_CLICK,
        // Validates if package is in foreground ( throws exception if it is not in foreground )
        HAS_PACKAGE_IN_FOREGROUND,
        // Validates if Ui Element is in foreground ( throws exception if it is not in foreground )
        HAS_UI_ELEMENT_IN_FOREGROUND,
        // Finds the given UI Element by Scrolling and Click on it ( Throws an exception if
        // UI Element not found )
        SCROLL_TO_FIND_AND_CLICK,
        // Finds the given UI Element by Scrolling and Click on it if found ( i.e. No Exception
        // even if UI Element is missing )
        SCROLL_TO_FIND_AND_CLICK_IF_EXIST,
        // Swipes once
        SWIPE,
        // Finds the given UI Element by Swiping and Click on it ( Throws an exception if
        // UI Element not found )
        SWIPE_TO_FIND_AND_CLICK,
        // Finds the given UI Element by Swiping and Click on it if found ( i.e. No Exception
        // even if UI Element is missing )
        SWIPE_TO_FIND_AND_CLICK_IF_EXIST,
        // Wait For Given Time in milliseconds
        WAIT_MS;
    }

    // Workflow Task Config
    public static final String CONFIG_TEXT = "TEXT";
    public static final String CONFIG_UI_ELEMENT = "UI_ELEMENT";

    // Scroll Config Constants
    // Supported Properties
    public static final String SCROLL_ACTION = "SCROLL_ACTION";
    public static final String SCROLL_DIRECTION = "SCROLL_DIRECTION";
    public static final String SCROLL_FORWARD = "SCROLL_FORWARD";
    public static final String SCROLL_BACKWARD = "SCROLL_BACKWARD";
    public static final String SCROLL_ELEMENT = "SCROLL_ELEMENT";
    public static final String SCROLL_MARGIN = "SCROLL_MARGIN";
    public static final String SCROLL_WAIT_TIME = "SCROLL_WAIT_TIME";

    public enum FindType {
        NONE,
        SCROLL,
        SWIPE,
    }

    // Scroll Action
    public enum ScrollActions {
        USE_BUTTON,
        USE_GESTURE;
    }

    // Scroll Direction
    public enum ScrollDirection {
        VERTICAL,
        HORIZONTAL;
    }
}
