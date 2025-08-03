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

package android.platform.spectatio.configs;

import android.platform.spectatio.constants.JsonConfigConstants;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.regex.Pattern;

/**
 * UI Resource For Spectatio Config JSON Config { "UI_ELEMENTS": { "CONFIG_NAME": { "TYPE":
 * "RESOURCE_TYPE", "VALUE": "RESOURCE_VALUE", "PACKAGE": "RESOURCE_PACKAGE" } } }
 *
 * <p>RESOURCE_TYPE: TEXT, DESCRIPTION, RESOURCE_ID, TEXT_CONTAINS, CLASS; RESOURCE_VALUE: Value of
 * the Resource; RESOURCE_PACKAGE: Package is required only to type RESOURCE_ID
 *
 * <p>Resource Value JSON { "TYPE": "RESOURCE_TYPE", "VALUE": "RESOURCE_VALUE", "PACKAGE":
 * "RESOURCE_PACKAGE" } } } is referred in code using this class
 */
public class UiElement {
    // Type of UI Element - Resource ID, Text, Description, Class
    @SerializedName("TYPE")
    private String mType;

    @SerializedName("FLAG")
    private boolean mFlag;

    @SerializedName("MAX_DEPTH")
    private int mMaxDepth;

    // Value for the UI Resource - id, text value, description or class for the resource
    @SerializedName("VALUE")
    private String mValue;

    // Application Package for the UI Resource if the type is Resource ID,
    @SerializedName("PACKAGE")
    private String mPackage;

    // Each UiElementSpecifier that comprises a MULTIPLE specifier
    @SerializedName("SPECIFIERS")
    private List<UiElement> mSpecifiers;

    // The specifier for the parent of a HAS_ASCENDANT specifier
    @SerializedName("ANCESTOR")
    private UiElement mAncestor;

    // The specifier for the child of a HAS_DESCENDANT specifier
    @SerializedName("DESCENDANT")
    private UiElement mDescendant;

    public UiElement(String type, boolean flag) {
        mType = type;
        mFlag = flag;
    }

    public UiElement(String type, String value, String pkg) {
        mType = type;
        mValue = value;
        mPackage = pkg;
    }

    public UiElement(List<UiElement> specifiers) {
        mType = JsonConfigConstants.MULTIPLE;
        mSpecifiers = specifiers;
    }

    public UiElement(String type, UiElement relative, int maxDepth) {
        mType = type;
        switch (type) {
            case JsonConfigConstants.HAS_DESCENDANT:
                mDescendant = relative;
                break;
            case JsonConfigConstants.HAS_ANCESTOR:
                mAncestor = relative;
                break;
            default:
                throw new RuntimeException(
                        "Unrecognized type given to UiElement constructor with relative argument");
        }
        mMaxDepth = maxDepth;
    }

    /** Get Resource Type ( RESOURCE_ID, TEXT, DESCRIPTION, CLASS ) */
    public String getType() {
        return mType;
    }

    /** Get Resource Value ( resource id, text value, description, class ) */
    public String getValue() {
        return mValue;
    }

    public String getPackage() {
        return mPackage;
    }

    /** Convert a UI element from the config into a BySelector */
    public BySelector getBySelectorForUiElement() {
        switch (mType) {
            case JsonConfigConstants.RESOURCE_ID:
                if (mPackage == null) {
                    return By.res(Pattern.compile(".*" + Pattern.quote(":id/" + mValue)));
                }
                return By.res(mPackage, mValue);
            case JsonConfigConstants.CLICKABLE:
                return By.clickable(mFlag);
            case JsonConfigConstants.SCROLLABLE:
                return By.scrollable(mFlag);
            case JsonConfigConstants.TEXT:
                return By.text(Pattern.compile(mValue, Pattern.CASE_INSENSITIVE));
            case JsonConfigConstants.TEXT_CONTAINS:
                return By.textContains(mValue);
            case JsonConfigConstants.DESCRIPTION:
                return By.desc(Pattern.compile(mValue, Pattern.CASE_INSENSITIVE));
            case JsonConfigConstants.CLASS:
                if (mPackage != null && !mPackage.isEmpty()) {
                    return By.clazz(mPackage, mValue);
                }
                return By.clazz(mValue);
            case JsonConfigConstants.HAS_ANCESTOR:
                return By.hasAncestor(mAncestor.getBySelectorForUiElement(), mMaxDepth);
            case JsonConfigConstants.HAS_DESCENDANT:
                return By.hasDescendant(mDescendant.getBySelectorForUiElement(), mMaxDepth);
            case JsonConfigConstants.MULTIPLE:
                BySelector selector = null;
                for (UiElement specifier : mSpecifiers) {
                    if (selector == null) {
                        selector = specifier.getBySelectorForUiElement();
                    } else {
                        specifier.extendBySelectorForUiElement(selector);
                    }
                }
                return selector;

            default:
                // Unknown UI Resource Type
                return null;
        }
    }

    private void extendBySelectorForUiElement(BySelector s) {
        switch (mType) {
            case JsonConfigConstants.RESOURCE_ID:
                s.res(mPackage, mValue);
                break;
            case JsonConfigConstants.CLICKABLE:
                s.clickable(mFlag);
                break;
            case JsonConfigConstants.SCROLLABLE:
                s.scrollable(mFlag);
                break;
            case JsonConfigConstants.TEXT:
                s.text(Pattern.compile(mValue, Pattern.CASE_INSENSITIVE));
                break;
            case JsonConfigConstants.TEXT_CONTAINS:
                s.textContains(mValue);
                break;
            case JsonConfigConstants.DESCRIPTION:
                s.desc(Pattern.compile(mValue, Pattern.CASE_INSENSITIVE));
                break;
            case JsonConfigConstants.CLASS:
                if (mPackage != null && !mPackage.isEmpty()) {
                    s.clazz(mPackage, mValue);
                    return;
                }
                s.clazz(mValue);
                break;
            case JsonConfigConstants.HAS_ANCESTOR:
                s.hasAncestor(mAncestor.getBySelectorForUiElement(), mMaxDepth);
                break;
            case JsonConfigConstants.HAS_DESCENDANT:
                s.hasDescendant(mDescendant.getBySelectorForUiElement(), mMaxDepth);
                break;
            case JsonConfigConstants.MULTIPLE:
                throw new UnsupportedOperationException(
                        "You can't put a multiple-specifier inside a multiple-specifier.");
            default:
                break;
        }
    }
}
