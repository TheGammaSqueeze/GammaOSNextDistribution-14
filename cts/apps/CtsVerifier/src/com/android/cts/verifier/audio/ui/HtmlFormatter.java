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

package com.android.cts.verifier.libs.ui;

import android.text.Html;

/**
 * Utility class for building well-formatted HTML.
 */
public class HtmlFormatter {
    private StringBuilder mSB = new StringBuilder();

    /**
     * Clear any accumulated text
     * @return this HtmlFormatter to allow for cascading calls.
     */
    public HtmlFormatter clear() {
        mSB = new StringBuilder();
        return this;
    }

    /**
     * Starts the HTML document block
     * @return this HtmlFormatter to allow for cascading calls.
     */
    public HtmlFormatter openDocument() {
        mSB.append("<!DOCTYPE html>\n<html lang=\"en-US\">\n<body>\n");
        return this;
    }

    /**
     * Closes the HTML document block
     * @return this HtmlFormatter to allow for cascading calls.
     */
    public HtmlFormatter closeDocument() {
        mSB.append("</body>\n</html>");
        return this;
    }

    /**
     * Opens an HTML paragraph block.
     * @return this HtmlFormatter to allow for cascading calls.
     */
    public HtmlFormatter openParagraph() {
        mSB.append("<p>");
        return this;
    }

    /**
     * Closes an HTML paragraph block.
     * @return this HtmlFormatter to allow for cascading calls.
     */
    public HtmlFormatter closeParagraph() {
        mSB.append("</p>\n");
        return this;
    }

    /**
     * Opens an HTML bold block.
     * @return this HtmlFormatter to allow for cascading calls.
     */
    public HtmlFormatter openBold() {
        mSB.append("<b>");
        return this;
    }

    /**
     * Closes an HTML bold block.
     * @return this HtmlFormatter to allow for cascading calls.
     */
    public HtmlFormatter closeBold() {
        mSB.append("</b>");
        return this;
    }

    /**
     * Opens an HTML italic block.
     * @return this HtmlFormatter to allow for cascading calls.
     */
    public HtmlFormatter openItalic() {
        mSB.append("<i>");
        return this;
    }

    /**
     * Closes an HTML italic block.
     * @return this HtmlFormatter to allow for cascading calls.
     */
    public HtmlFormatter closeItalic() {
        mSB.append("</i>");
        return this;
    }

    /**
     * Inserts a 'break' in the HTML
     * @return this HtmlFormatter to allow for cascading calls.
     */
    public HtmlFormatter appendBreak() {
        mSB.append("<br>\n");
        return this;
    }

    /**
     * Opens a text color block
     * @param color The desired color, i.e. "red", "blue"...
     * @return this HtmlFormatter to allow for cascading calls.
     */
    public HtmlFormatter openTextColor(String color) {
        mSB.append("<font color=\"" + color + "\">");
        return this;
    }

    /**
     * Closes a color block
     * @return this HtmlFormatter to allow for cascading calls.
     */
    public HtmlFormatter closeTextColor() {
        mSB.append("</font>");
        return this;
    }

    /**
     * Appends the specified text to the HTML stream.
     * @return this HtmlFormatter to allow for cascading calls.
     */
    public HtmlFormatter appendText(String text) {
        mSB.append(Html.escapeHtml(text));
        return this;
    }

    /**
     *
     * @return this HtmlFormatter to allow for cascading calls.
     */
    @Override
    public String toString() {
        return mSB.toString();
    }
}
