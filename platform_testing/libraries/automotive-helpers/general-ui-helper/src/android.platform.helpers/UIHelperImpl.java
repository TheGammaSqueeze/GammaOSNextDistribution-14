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
import android.platform.spectatio.utils.SpectatioUiUtil;
import android.util.Log;

import androidx.test.uiautomator.UiObject2;

/** Helper class for general UI actions */
public class UIHelperImpl extends AbstractStandardAppHelper implements IAutoGeneralUIHelper {

    private static final String LOG_TAG = UIHelperImpl.class.getSimpleName();

    private SpectatioUiUtil mSpectatioUiUtil;

    public UIHelperImpl(Instrumentation instr) {
        super(instr);
        mSpectatioUiUtil = getSpectatioUiUtil();
    }

    /** {@inheritDoc} */
    @Override
    public String getPackage() {
        return getPackageFromConfig(AutomotiveConfigConstants.GENERAL_UI_PACKAGE);
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
    public void clickElementWithText(String text) {
        // TODO: How can me make button-pressing an asynchronous task?
        try {
            UiObject2 toClick = mSpectatioUiUtil.findUiObject(text);
            mSpectatioUiUtil.clickAndWait(toClick);
        } catch (Exception e) {
            Log.e(
                    LOG_TAG,
                    String.format(
                            "Error when clicking element with text %s : %s", text, e.toString()));
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasElementWithText(String text) {
        return mSpectatioUiUtil.hasUiElement(text);
    }
}
