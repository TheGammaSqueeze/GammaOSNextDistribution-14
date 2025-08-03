/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.autofillservice.cts.testcore.UiBot.PORTRAIT;
import static android.provider.Settings.Secure.AUTOFILL_SERVICE;
import static android.provider.Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE;
import static android.provider.Settings.Secure.USER_SETUP_COMPLETE;
import static android.service.autofill.FillEventHistory.Event.TYPE_AUTHENTICATION_SELECTED;
import static android.service.autofill.FillEventHistory.Event.TYPE_CONTEXT_COMMITTED;
import static android.service.autofill.FillEventHistory.Event.TYPE_DATASETS_SHOWN;
import static android.service.autofill.FillEventHistory.Event.TYPE_DATASET_AUTHENTICATION_SELECTED;
import static android.service.autofill.FillEventHistory.Event.TYPE_DATASET_SELECTED;
import static android.service.autofill.FillEventHistory.Event.TYPE_SAVE_SHOWN;
import static android.service.autofill.FillEventHistory.Event.TYPE_VIEW_REQUESTED_AUTOFILL;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.app.assist.AssistStructure;
import android.app.assist.AssistStructure.ViewNode;
import android.app.assist.AssistStructure.WindowNode;
import android.autofillservice.cts.R;
import android.autofillservice.cts.activities.AbstractAutoFillActivity;
import android.content.AutofillOptions;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.hardware.devicestate.DeviceStateManager;
import android.hardware.devicestate.DeviceStateManager.DeviceStateCallback;
import android.icu.util.Calendar;
import android.os.Bundle;
import android.os.Environment;
import android.os.UserManager;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.service.autofill.CustomDescription;
import android.service.autofill.FieldClassification;
import android.service.autofill.FieldClassification.Match;
import android.service.autofill.FillContext;
import android.service.autofill.FillEventHistory;
import android.service.autofill.InlinePresentation;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStructure.HtmlInfo;
import android.view.WindowInsets;
import android.view.autofill.AutofillFeatureFlags;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillManager.AutofillCallback;
import android.view.autofill.AutofillValue;
import android.webkit.WebView;
import android.widget.RemoteViews;
import android.widget.inline.InlinePresentationSpec;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.autofill.inline.v1.InlineSuggestionUi;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.android.compatibility.common.util.BitmapUtils;
import com.android.compatibility.common.util.DeviceConfigStateManager;
import com.android.compatibility.common.util.OneTimeSettingsListener;
import com.android.compatibility.common.util.ShellUtils;
import com.android.compatibility.common.util.TestNameUtils;
import com.android.compatibility.common.util.Timeout;
import com.android.compatibility.common.util.UserSettings;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Helper for common funcionalities.
 */
public final class Helper {

    public static final String TAG = "AutoFillCtsHelper";

    public static final boolean VERBOSE = false;

    public static final String MY_PACKAGE = "android.autofillservice.cts";

    public static final String ID_USERNAME_LABEL = "username_label";
    public static final String ID_USERNAME = "username";
    public static final String ID_PASSWORD_LABEL = "password_label";
    public static final String ID_PASSWORD = "password";
    public static final String ID_CARD_NUMBER = "card_number";
    public static final String ID_LOGIN = "login";
    public static final String ID_OUTPUT = "output";
    public static final String ID_STATIC_TEXT = "static_text";
    public static final String ID_EMPTY = "empty";
    public static final String ID_CANCEL_FILL = "cancel_fill";
    public static final String ID_IMEACTION_TEXT = "ime_option_text";
    public static final String ID_IMEACTION_TEXT_IMPORTANT_FOR_AUTOFILL =
        "ime_option_text_important_for_autofill";
    public static final String ID_IMEACTION_LABEL = "ime_option_text_label";

    public static final String NULL_DATASET_ID = null;

    public static final char LARGE_STRING_CHAR = '6';
    // NOTE: cannot be much large as it could ANR and fail the test.
    public static final int LARGE_STRING_SIZE = 100_000;
    public static final String LARGE_STRING = com.android.compatibility.common.util.TextUtils
            .repeat(LARGE_STRING_CHAR, LARGE_STRING_SIZE);

    /**
     * Can be used in cases where the autofill values is required by irrelevant (like adding a
     * value to an authenticated dataset).
     */
    public static final String UNUSED_AUTOFILL_VALUE = null;

    private static final String ACCELLEROMETER_CHANGE =
            "content insert --uri content://settings/system --bind name:s:accelerometer_rotation "
                    + "--bind value:i:%d";

    private static final String LOCAL_DIRECTORY = Environment.getExternalStorageDirectory()
            + "/CtsAutoFillServiceTestCases";

    private static final Timeout SETTINGS_BASED_SHELL_CMD_TIMEOUT = new Timeout(
            "SETTINGS_SHELL_CMD_TIMEOUT", OneTimeSettingsListener.DEFAULT_TIMEOUT_MS / 2, 2,
            OneTimeSettingsListener.DEFAULT_TIMEOUT_MS);

    public static final String DEVICE_CONFIG_AUTOFILL_DIALOG_HINTS = "autofill_dialog_hints";

    private static final UserSettings sUserSettings = new UserSettings();

    /**
     * Helper interface used to filter nodes.
     *
     * @param <T> node type
     */
    interface NodeFilter<T> {
        /**
         * Returns whether the node passes the filter for such given id.
         */
        boolean matches(T node, Object id);
    }

    private static final NodeFilter<ViewNode> RESOURCE_ID_FILTER = (node, id) -> {
        return id.equals(node.getIdEntry());
    };

    private static final NodeFilter<ViewNode> HTML_NAME_FILTER = (node, id) -> {
        return id.equals(getHtmlName(node));
    };

    private static final NodeFilter<ViewNode> HTML_NAME_OR_RESOURCE_ID_FILTER = (node, id) -> {
        return id.equals(getHtmlName(node)) || id.equals(node.getIdEntry());
    };

    private static final NodeFilter<ViewNode> TEXT_FILTER = (node, id) -> {
        return id.equals(node.getText());
    };

    private static final NodeFilter<ViewNode> AUTOFILL_HINT_FILTER = (node, id) -> {
        return hasHint(node.getAutofillHints(), id);
    };

    private static final NodeFilter<ViewNode> WEBVIEW_FORM_FILTER = (node, id) -> {
        final String className = node.getClassName();
        if (!className.equals("android.webkit.WebView")) return false;

        final HtmlInfo htmlInfo = assertHasHtmlTag(node, "form");
        final String formName = getAttributeValue(htmlInfo, "name");
        return id.equals(formName);
    };

    private static final NodeFilter<View> AUTOFILL_HINT_VIEW_FILTER = (view, id) -> {
        return hasHint(view.getAutofillHints(), id);
    };

    private static String toString(AssistStructure structure, StringBuilder builder) {
        builder.append("[component=").append(structure.getActivityComponent());
        final int nodes = structure.getWindowNodeCount();
        for (int i = 0; i < nodes; i++) {
            final WindowNode windowNode = structure.getWindowNodeAt(i);
            dump(builder, windowNode.getRootViewNode(), " ", 0);
        }
        return builder.append(']').toString();
    }

    @NonNull
    public static String toString(@NonNull AssistStructure structure) {
        return toString(structure, new StringBuilder());
    }

    @Nullable
    public static String toString(@Nullable AutofillValue value) {
        if (value == null) return null;
        if (value.isText()) {
            // We don't care about PII...
            final CharSequence text = value.getTextValue();
            return text == null ? null : text.toString();
        }
        return value.toString();
    }

    /**
     * Dump the assist structure on logcat.
     */
    public static void dumpStructure(String message, AssistStructure structure) {
        Log.i(TAG, toString(structure, new StringBuilder(message)));
    }

    /**
     * Dump the contexts on logcat.
     */
    public static void dumpStructure(String message, List<FillContext> contexts) {
        for (FillContext context : contexts) {
            dumpStructure(message, context.getStructure());
        }
    }

    /**
     * Dumps the state of the autofill service on logcat.
     */
    public static void dumpAutofillService(@NonNull String tag) {
        final String autofillDump = runShellCommand("dumpsys autofill");
        Log.i(tag, "dumpsys autofill\n\n" + autofillDump);
        final String myServiceDump = runShellCommand("dumpsys activity service %s",
                InstrumentedAutoFillService.SERVICE_NAME);
        Log.i(tag, "my service dump: \n" + myServiceDump);
    }

    /**
     * Dumps the state of {@link android.service.autofill.InlineSuggestionRenderService}, and assert
     * that it says the number of active inline suggestion views is the given number.
     *
     * <p>Note that ideally we should have a test api to fetch the number and verify against it.
     * But at the time this test is added for Android 11, we have passed the deadline for adding
     * the new test api, hence this approach.
     */
    public static void assertActiveViewCountFromInlineSuggestionRenderService(int count) {
        String response = runShellCommand(
                "dumpsys activity service .InlineSuggestionRenderService");
        Log.d(TAG, "InlineSuggestionRenderService dump: " + response);
        Pattern pattern = Pattern.compile(".*mActiveInlineSuggestions: " + count + ".*");
        assertWithMessage("Expecting view count " + count
                + ", but seeing different count from service dumpsys " + response).that(
                pattern.matcher(response).find()).isTrue();
    }

    /**
     * Sets whether the user completed the initial setup.
     */
    public static void setUserComplete(boolean complete) {
        sUserSettings.syncSet(USER_SETUP_COMPLETE, complete ? "1" : null);
    }

    private static void dump(@NonNull StringBuilder builder, @NonNull ViewNode node,
            @NonNull String prefix, int childId) {
        final int childrenSize = node.getChildCount();
        builder.append("\n").append(prefix)
            .append("child #").append(childId).append(':');
        append(builder, "afId", node.getAutofillId());
        append(builder, "afType", node.getAutofillType());
        append(builder, "afValue", toString(node.getAutofillValue()));
        append(builder, "resId", node.getIdEntry());
        append(builder, "class", node.getClassName());
        append(builder, "text", node.getText());
        append(builder, "webDomain", node.getWebDomain());
        append(builder, "checked", node.isChecked());
        append(builder, "focused", node.isFocused());
        final HtmlInfo htmlInfo = node.getHtmlInfo();
        if (htmlInfo != null) {
            builder.append(", HtmlInfo[tag=").append(htmlInfo.getTag())
                .append(", attrs: ").append(htmlInfo.getAttributes()).append(']');
        }
        if (childrenSize > 0) {
            append(builder, "#children", childrenSize).append("\n").append(prefix);
            prefix += " ";
            if (childrenSize > 0) {
                for (int i = 0; i < childrenSize; i++) {
                    dump(builder, node.getChildAt(i), prefix, i);
                }
            }
        }
    }

    /**
     * Appends a field value to a {@link StringBuilder} when it's not {@code null}.
     */
    @NonNull
    public static StringBuilder append(@NonNull StringBuilder builder, @NonNull String field,
            @Nullable Object value) {
        if (value == null) return builder;

        if ((value instanceof Boolean) && ((Boolean) value)) {
            return builder.append(", ").append(field);
        }

        if (value instanceof Integer && ((Integer) value) == 0
                || value instanceof CharSequence && TextUtils.isEmpty((CharSequence) value)) {
            return builder;
        }

        return builder.append(", ").append(field).append('=').append(value);
    }

    /**
     * Appends a field value to a {@link StringBuilder} when it's {@code true}.
     */
    @NonNull
    public static StringBuilder append(@NonNull StringBuilder builder, @NonNull String field,
            boolean value) {
        if (value) {
            builder.append(", ").append(field);
        }
        return builder;
    }

    /**
     * Gets a node if it matches the filter criteria for the given id.
     */
    public static ViewNode findNodeByFilter(@NonNull AssistStructure structure, @NonNull Object id,
            @NonNull NodeFilter<ViewNode> filter) {
        Log.v(TAG, "Parsing request for activity " + structure.getActivityComponent());
        final int nodes = structure.getWindowNodeCount();
        for (int i = 0; i < nodes; i++) {
            final WindowNode windowNode = structure.getWindowNodeAt(i);
            final ViewNode rootNode = windowNode.getRootViewNode();
            final ViewNode node = findNodeByFilter(rootNode, id, filter);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    /**
     * Gets a node if it matches the filter criteria for the given id.
     */
    public static ViewNode findNodeByFilter(@NonNull List<FillContext> contexts, @NonNull Object id,
            @NonNull NodeFilter<ViewNode> filter) {
        for (FillContext context : contexts) {
            ViewNode node = findNodeByFilter(context.getStructure(), id, filter);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    /**
     * Gets a node if it matches the filter criteria for the given id.
     */
    public static ViewNode findNodeByFilter(@NonNull ViewNode node, @NonNull Object id,
            @NonNull NodeFilter<ViewNode> filter) {
        if (filter.matches(node, id)) {
            return node;
        }
        final int childrenSize = node.getChildCount();
        if (childrenSize > 0) {
            for (int i = 0; i < childrenSize; i++) {
                final ViewNode found = findNodeByFilter(node.getChildAt(i), id, filter);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Gets a node given its Android resource id, or {@code null} if not found.
     */
    public static ViewNode findNodeByResourceId(AssistStructure structure, String resourceId) {
        return findNodeByFilter(structure, resourceId, RESOURCE_ID_FILTER);
    }

    /**
     * Gets a node given its Android resource id, or {@code null} if not found.
     */
    public static ViewNode findNodeByResourceId(List<FillContext> contexts, String resourceId) {
        return findNodeByFilter(contexts, resourceId, RESOURCE_ID_FILTER);
    }

    /**
     * Gets a node given its Android resource id, or {@code null} if not found.
     */
    public static ViewNode findNodeByResourceId(ViewNode node, String resourceId) {
        return findNodeByFilter(node, resourceId, RESOURCE_ID_FILTER);
    }

    /**
     * Gets a node given the name of its HTML INPUT tag, or {@code null} if not found.
     */
    public static ViewNode findNodeByHtmlName(AssistStructure structure, String htmlName) {
        return findNodeByFilter(structure, htmlName, HTML_NAME_FILTER);
    }

    /**
     * Gets a node given the name of its HTML INPUT tag, or {@code null} if not found.
     */
    public static ViewNode findNodeByHtmlName(List<FillContext> contexts, String htmlName) {
        return findNodeByFilter(contexts, htmlName, HTML_NAME_FILTER);
    }

    /**
     * Gets a node given the name of its HTML INPUT tag, or {@code null} if not found.
     */
    public static ViewNode findNodeByHtmlName(ViewNode node, String htmlName) {
        return findNodeByFilter(node, htmlName, HTML_NAME_FILTER);
    }

    /**
     * Gets a node given the value of its (single) autofill hint property, or {@code null} if not
     * found.
     */
    public static ViewNode findNodeByAutofillHint(ViewNode node, String hint) {
        return findNodeByFilter(node, hint, AUTOFILL_HINT_FILTER);
    }

    /**
     * Gets a node given the name of its HTML INPUT tag or Android resoirce id, or {@code null} if
     * not found.
     */
    public static ViewNode findNodeByHtmlNameOrResourceId(List<FillContext> contexts, String id) {
        return findNodeByFilter(contexts, id, HTML_NAME_OR_RESOURCE_ID_FILTER);
    }

    /**
     * Gets a node given its Android resource id.
     */
    @NonNull
    public static AutofillId findAutofillIdByResourceId(@NonNull FillContext context,
            @NonNull String resourceId) {
        final ViewNode node = findNodeByFilter(context.getStructure(), resourceId,
                RESOURCE_ID_FILTER);
        assertWithMessage("No node for resourceId %s", resourceId).that(node).isNotNull();
        return node.getAutofillId();
    }

    /**
     * Gets the {@code name} attribute of a node representing an HTML input tag.
     */
    @Nullable
    public static String getHtmlName(@NonNull ViewNode node) {
        final HtmlInfo htmlInfo = node.getHtmlInfo();
        if (htmlInfo == null) {
            return null;
        }
        final String tag = htmlInfo.getTag();
        if (!"input".equals(tag)) {
            Log.w(TAG, "getHtmlName(): invalid tag (" + tag + ") on " + htmlInfo);
            return null;
        }
        for (Pair<String, String> attr : htmlInfo.getAttributes()) {
            if ("name".equals(attr.first)) {
                return attr.second;
            }
        }
        Log.w(TAG, "getHtmlName(): no 'name' attribute on " + htmlInfo);
        return null;
    }

    /**
     * Gets a node given its expected text, or {@code null} if not found.
     */
    public static ViewNode findNodeByText(AssistStructure structure, String text) {
        return findNodeByFilter(structure, text, TEXT_FILTER);
    }

    /**
     * Gets a node given its expected text, or {@code null} if not found.
     */
    public static ViewNode findNodeByText(ViewNode node, String text) {
        return findNodeByFilter(node, text, TEXT_FILTER);
    }

    /**
     * Gets a view that contains the an autofill hint, or {@code null} if not found.
     */
    public static View findViewByAutofillHint(Activity activity, String hint) {
        final View rootView = activity.getWindow().getDecorView().getRootView();
        return findViewByAutofillHint(rootView, hint);
    }

    /**
     * Gets a view (or a descendant of it) that contains the an autofill hint, or {@code null} if
     * not found.
     */
    public static View findViewByAutofillHint(View view, String hint) {
        if (AUTOFILL_HINT_VIEW_FILTER.matches(view, hint)) return view;
        if ((view instanceof ViewGroup)) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                final View child = findViewByAutofillHint(group.getChildAt(i), hint);
                if (child != null) return child;
            }
        }
        return null;
    }

    /**
     * Asserts a text-based node is sanitized.
     */
    public static void assertTextIsSanitized(ViewNode node) {
        final CharSequence text = node.getText();
        final String resourceId = node.getIdEntry();
        if (!TextUtils.isEmpty(text)) {
            throw new AssertionError("text on sanitized field " + resourceId + ": " + text);
        }

        assertNotFromResources(node);
        assertNodeHasNoAutofillValue(node);
    }

    private static void assertNotFromResources(ViewNode node) {
        assertThat(node.getTextIdEntry()).isNull();
    }

    public static void assertNodeHasNoAutofillValue(ViewNode node) {
        final AutofillValue value = node.getAutofillValue();
        if (value != null) {
            final String text = value.isText() ? value.getTextValue().toString() : "N/A";
            throw new AssertionError("node has value: " + value + " text=" + text);
        }
    }

    /**
     * Asserts the contents of a text-based node that is also auto-fillable.
     */
    public static void assertTextOnly(ViewNode node, String expectedValue) {
        assertText(node, expectedValue, false);
        assertNotFromResources(node);
    }

    /**
     * Asserts the contents of a text-based node that is also auto-fillable.
     */
    public static void assertTextOnly(AssistStructure structure, String resourceId,
            String expectedValue) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        assertText(node, expectedValue, false);
        assertNotFromResources(node);
    }

    /**
     * Asserts the contents of a text-based node that is also auto-fillable.
     */
    public static void assertTextAndValue(ViewNode node, String expectedValue) {
        assertText(node, expectedValue, true);
        assertNotFromResources(node);
    }

    /**
     * Asserts a text-based node exists and verify its values.
     */
    public static ViewNode assertTextAndValue(AssistStructure structure, String resourceId,
            String expectedValue) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        assertTextAndValue(node, expectedValue);
        return node;
    }

    /**
     * Asserts a text-based node exists and is sanitized.
     */
    public static ViewNode assertValue(AssistStructure structure, String resourceId,
            String expectedValue) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        assertTextValue(node, expectedValue);
        return node;
    }

    /**
     * Asserts the values of a text-based node whose string come from resoruces.
     */
    public static ViewNode assertTextFromResources(AssistStructure structure, String resourceId,
            String expectedValue, boolean isAutofillable, String expectedTextIdEntry) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        assertText(node, expectedValue, isAutofillable);
        assertThat(node.getTextIdEntry()).isEqualTo(expectedTextIdEntry);
        return node;
    }

    public static ViewNode assertHintFromResources(AssistStructure structure, String resourceId,
            String expectedValue, String expectedHintIdEntry) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        assertThat(node.getHint()).isEqualTo(expectedValue);
        assertThat(node.getHintIdEntry()).isEqualTo(expectedHintIdEntry);
        return node;
    }

    private static void assertText(ViewNode node, String expectedValue, boolean isAutofillable) {
        assertWithMessage("wrong text on %s", node.getAutofillId()).that(node.getText().toString())
                .isEqualTo(expectedValue);
        final AutofillValue value = node.getAutofillValue();
        final AutofillId id = node.getAutofillId();
        if (isAutofillable) {
            assertWithMessage("null auto-fill value on %s", id).that(value).isNotNull();
            assertWithMessage("wrong auto-fill value on %s", id)
                    .that(value.getTextValue().toString()).isEqualTo(expectedValue);
        } else {
            assertWithMessage("node %s should not have AutofillValue", id).that(value).isNull();
        }
    }

    /**
     * Asserts the auto-fill value of a text-based node.
     */
    public static ViewNode assertTextValue(ViewNode node, String expectedText) {
        final AutofillValue value = node.getAutofillValue();
        final AutofillId id = node.getAutofillId();
        assertWithMessage("null autofill value on %s", id).that(value).isNotNull();
        assertWithMessage("wrong autofill type on %s", id).that(value.isText()).isTrue();
        assertWithMessage("wrong autofill value on %s", id).that(value.getTextValue().toString())
                .isEqualTo(expectedText);
        return node;
    }

    /**
     * Asserts the auto-fill value of a list-based node.
     */
    public static ViewNode assertListValue(ViewNode node, int expectedIndex) {
        final AutofillValue value = node.getAutofillValue();
        final AutofillId id = node.getAutofillId();
        assertWithMessage("null autofill value on %s", id).that(value).isNotNull();
        assertWithMessage("wrong autofill type on %s", id).that(value.isList()).isTrue();
        assertWithMessage("wrong autofill value on %s", id).that(value.getListValue())
                .isEqualTo(expectedIndex);
        return node;
    }

    /**
     * Asserts the auto-fill value of a toggle-based node.
     */
    public static void assertToggleValue(ViewNode node, boolean expectedToggle) {
        final AutofillValue value = node.getAutofillValue();
        final AutofillId id = node.getAutofillId();
        assertWithMessage("null autofill value on %s", id).that(value).isNotNull();
        assertWithMessage("wrong autofill type on %s", id).that(value.isToggle()).isTrue();
        assertWithMessage("wrong autofill value on %s", id).that(value.getToggleValue())
                .isEqualTo(expectedToggle);
    }

    /**
     * Asserts the auto-fill value of a date-based node.
     */
    public static void assertDateValue(Object object, AutofillValue value, int year, int month,
            int day) {
        assertWithMessage("null autofill value on %s", object).that(value).isNotNull();
        assertWithMessage("wrong autofill type on %s", object).that(value.isDate()).isTrue();

        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(value.getDateValue());

        assertWithMessage("Wrong year on AutofillValue %s", value)
            .that(cal.get(Calendar.YEAR)).isEqualTo(year);
        assertWithMessage("Wrong month on AutofillValue %s", value)
            .that(cal.get(Calendar.MONTH)).isEqualTo(month);
        assertWithMessage("Wrong day on AutofillValue %s", value)
             .that(cal.get(Calendar.DAY_OF_MONTH)).isEqualTo(day);
    }

    /**
     * Asserts the auto-fill value of a date-based node.
     */
    public static void assertDateValue(ViewNode node, int year, int month, int day) {
        assertDateValue(node, node.getAutofillValue(), year, month, day);
    }

    /**
     * Asserts the auto-fill value of a date-based view.
     */
    public static void assertDateValue(View view, int year, int month, int day) {
        assertDateValue(view, view.getAutofillValue(), year, month, day);
    }

    /**
     * Asserts the auto-fill value of a time-based node.
     */
    private static void assertTimeValue(Object object, AutofillValue value, int hour, int minute) {
        assertWithMessage("null autofill value on %s", object).that(value).isNotNull();
        assertWithMessage("wrong autofill type on %s", object).that(value.isDate()).isTrue();

        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(value.getDateValue());

        assertWithMessage("Wrong hour on AutofillValue %s", value)
            .that(cal.get(Calendar.HOUR_OF_DAY)).isEqualTo(hour);
        assertWithMessage("Wrong minute on AutofillValue %s", value)
            .that(cal.get(Calendar.MINUTE)).isEqualTo(minute);
    }

    /**
     * Asserts the auto-fill value of a time-based node.
     */
    public static void assertTimeValue(ViewNode node, int hour, int minute) {
        assertTimeValue(node, node.getAutofillValue(), hour, minute);
    }

    /**
     * Asserts the auto-fill value of a time-based view.
     */
    public static void assertTimeValue(View view, int hour, int minute) {
        assertTimeValue(view, view.getAutofillValue(), hour, minute);
    }

    /**
     * Asserts a text-based node exists and is sanitized.
     */
    public static ViewNode assertTextIsSanitized(AssistStructure structure, String resourceId) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        assertWithMessage("no ViewNode with id %s", resourceId).that(node).isNotNull();
        assertTextIsSanitized(node);
        return node;
    }

    /**
     * Asserts a list-based node exists and is sanitized.
     */
    public static void assertListValueIsSanitized(AssistStructure structure, String resourceId) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        assertWithMessage("no ViewNode with id %s", resourceId).that(node).isNotNull();
        assertTextIsSanitized(node);
    }

    /**
     * Asserts a toggle node exists and is sanitized.
     */
    public static void assertToggleIsSanitized(AssistStructure structure, String resourceId) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        assertNodeHasNoAutofillValue(node);
        assertWithMessage("ViewNode %s should not be checked", resourceId).that(node.isChecked())
                .isFalse();
    }

    /**
     * Asserts a node exists and has the {@code expected} number of children.
     */
    public static void assertNumberOfChildren(AssistStructure structure, String resourceId,
            int expected) {
        final ViewNode node = findNodeByResourceId(structure, resourceId);
        final int actual = node.getChildCount();
        if (actual != expected) {
            dumpStructure("assertNumberOfChildren()", structure);
            throw new AssertionError("assertNumberOfChildren() for " + resourceId
                    + " failed: expected " + expected + ", got " + actual);
        }
    }

    /**
     * Asserts the number of children in the Assist structure.
     */
    public static void assertNumberOfChildrenWithWindowTitle(AssistStructure structure,
            int expected, CharSequence windowTitle) {
        final int actual = getNumberNodes(structure, windowTitle);
        if (actual != expected) {
            dumpStructure("assertNumberOfChildren()", structure);
            throw new AssertionError("assertNumberOfChildren() for structure failed: expected "
                    + expected + ", got " + actual);
        }
    }

    /**
     * Gets the total number of nodes in an structure.
     * A node that has a non-null IdPackage which does not match the test package is not counted.
     */
    public static int getNumberNodes(AssistStructure structure,
            CharSequence windowTitle) {
        int count = 0;
        final int nodes = structure.getWindowNodeCount();
        for (int i = 0; i < nodes; i++) {
            final WindowNode windowNode = structure.getWindowNodeAt(i);
            if (windowNode.getTitle().equals(windowTitle)) {
                final ViewNode rootNode = windowNode.getRootViewNode();
                count += getNumberNodes(rootNode);
            }
        }
        return count;
    }

    /**
     * Gets the activity title.
     */
    public static CharSequence getActivityTitle(Instrumentation instrumentation,
            Activity activity) {
        final StringBuilder titleBuilder = new StringBuilder();
        instrumentation.runOnMainSync(() -> titleBuilder.append(activity.getTitle()));
        return titleBuilder;
    }

    /**
     * Gets the total number of nodes in an node, including all descendants and the node itself.
     * A node that has a non-null IdPackage which does not match the test package is not counted.
     */
    public static int getNumberNodes(ViewNode node) {
        if (node.getIdPackage() != null && !node.getIdPackage().equals(MY_PACKAGE)) {
            Log.w(TAG, "ViewNode ignored in getNumberNodes because of mismatched package: "
                    + node.getIdPackage());
            return 0;
        }
        int count = 1;
        final int childrenSize = node.getChildCount();
        for (int i = 0; i < childrenSize; i++) {
            count += getNumberNodes(node.getChildAt(i));
        }
        return count;
    }

    /**
     * Creates an array of {@link AutofillId} mapped from the {@code structure} nodes with the given
     * {@code resourceIds}.
     */
    public static AutofillId[] getAutofillIds(Function<String, AutofillId> autofillIdResolver,
            String[] resourceIds) {
        if (resourceIds == null) return null;

        final AutofillId[] requiredIds = new AutofillId[resourceIds.length];
        for (int i = 0; i < resourceIds.length; i++) {
            final String resourceId = resourceIds[i];
            requiredIds[i] = autofillIdResolver.apply(resourceId);
        }
        return requiredIds;
    }

    /**
     * Prevents the screen to rotate by itself
     */
    public static void disableAutoRotation(UiBot uiBot) throws Exception {
        runShellCommand(ACCELLEROMETER_CHANGE, 0);
        uiBot.setScreenOrientation(PORTRAIT);
    }

    /**
     * Allows the screen to rotate by itself
     */
    public static void allowAutoRotation() {
        runShellCommand(ACCELLEROMETER_CHANGE, 1);
    }

    /**
     * Gets the maximum number of partitions per session.
     */
    public static int getMaxPartitions() {
        return Integer.parseInt(runShellCommand("cmd autofill get max_partitions"));
    }

    /**
     * Sets the maximum number of partitions per session.
     */
    public static void setMaxPartitions(int value) throws Exception {
        runShellCommand("cmd autofill set max_partitions %d", value);
        SETTINGS_BASED_SHELL_CMD_TIMEOUT.run("get max_partitions", () -> {
            return getMaxPartitions() == value ? Boolean.TRUE : null;
        });
    }

    /**
     * Gets the maximum number of visible datasets.
     */
    public static int getMaxVisibleDatasets() {
        return Integer.parseInt(runShellCommand("cmd autofill get max_visible_datasets"));
    }

    /**
     * Sets the maximum number of visible datasets.
     */
    public static void setMaxVisibleDatasets(int value) throws Exception {
        runShellCommand("cmd autofill set max_visible_datasets %d", value);
        SETTINGS_BASED_SHELL_CMD_TIMEOUT.run("get max_visible_datasets", () -> {
            return getMaxVisibleDatasets() == value ? Boolean.TRUE : null;
        });
    }

    /**
     * Checks if autofill window is fullscreen, see com.android.server.autofill.ui.FillUi.
     */
    public static boolean isAutofillWindowFullScreen(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    /**
     * Checks if PCC is enabled for the device
     */
    public static boolean isPccSupported(Context context) {
        final PackageManager packageManager = context.getPackageManager();
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            Log.v(TAG, "isPccSupported(): is auto");
            return false;
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PC)) {
            Log.v(TAG, "isPccSupported(): is PC");
            return false;
        }
        return true;
    }

    /**
     * Returns if devices is Automotive device
     */
    public static boolean isAutomotive(Context context) {
        final PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    public static boolean isMainUser(Context context) {
        boolean mainUser = false;
        try {
            final UserManager userManager = context.getSystemService(UserManager.class);
            mainUser = userManager.isMainUser();
        } catch (SecurityException ex) {
            // Nothing
            mainUser = false;
        }

        return mainUser;

    }

    /**
     * Checks if screen orientation can be changed.
     */
    public static boolean isRotationSupported(Context context) {
        final PackageManager packageManager = context.getPackageManager();
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            Log.v(TAG, "isRotationSupported(): is auto");
            return false;
        }
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            Log.v(TAG, "isRotationSupported(): has leanback feature");
            return false;
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PC)) {
            Log.v(TAG, "isRotationSupported(): is PC");
            return false;
        }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_SCREEN_LANDSCAPE)
                || !packageManager.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT)) {
            Log.v(TAG, "isRotationSupported(): no screen orientation feature");
            return false;
        }
        return true;
    }

    private static boolean getBoolean(Context context, String id) {
        final Resources resources = context.getResources();
        final int booleanId = resources.getIdentifier(id, "bool", "android");
        return resources.getBoolean(booleanId);
    }

    /**
     * Uses Shell command to get the Autofill logging level.
     */
    public static String getLoggingLevel() {
        return runShellCommand("cmd autofill get log_level");
    }

    /**
     * Uses Shell command to set the Autofill logging level.
     */
    public static void setLoggingLevel(String level) {
        runShellCommand("cmd autofill set log_level %s", level);
    }

    /**
     * Uses Settings to enable the given autofill service for the default user, and checks the
     * value was properly check, throwing an exception if it was not.
     */
    public static void enableAutofillService(String serviceName) {
        if (isAutofillServiceEnabled(serviceName)) return;

        // Sets the setting synchronously. Note that the config itself is sets synchronously but
        // launch of the service is asynchronous after the config is updated.
        sUserSettings.syncSet(AUTOFILL_SERVICE, serviceName);

        // Waits until the service is actually enabled.
        try {
            Timeouts.CONNECTION_TIMEOUT.run("Enabling Autofill service", () -> {
                return isAutofillServiceEnabled(serviceName) ? serviceName : null;
            });
        } catch (Exception e) {
            throw new AssertionError("Enabling Autofill service failed.");
        }
    }

    /**
     * Uses Settings to disable the given autofill service for the default user, and waits until
     * the setting is deleted.
     */
    public static void disableAutofillService() {
        final String currentService = sUserSettings.get(AUTOFILL_SERVICE);
        if (currentService == null) {
            Log.v(TAG, "disableAutofillService(): already disabled");
            return;
        }
        Log.v(TAG, "Disabling " + currentService);
        sUserSettings.syncDelete(AUTOFILL_SERVICE);
    }

    /**
     * Checks whether the given service is set as the autofill service for the default user.
     */
    public static boolean isAutofillServiceEnabled(@NonNull String serviceName) {
        final String actualName = getAutofillServiceName();
        return serviceName.equals(actualName);
    }

    /**
     * Gets then name of the autofill service for the default user.
     */
    public static String getAutofillServiceName() {
        return sUserSettings.get(AUTOFILL_SERVICE);
    }

    /**
     * Asserts whether the given service is enabled as the autofill service for the default user.
     */
    public static void assertAutofillServiceStatus(@NonNull String serviceName, boolean enabled) {
        final String actual = sUserSettings.get(AUTOFILL_SERVICE);
        final String expected = enabled ? serviceName : null;
        assertWithMessage("Invalid value for secure setting %s", AUTOFILL_SERVICE)
                .that(actual).isEqualTo(expected);
    }

    /**
     * Enables / disables the default augmented autofill service.
     */
    public static void setDefaultAugmentedAutofillServiceEnabled(boolean enabled) {
        Log.d(TAG, "setDefaultAugmentedAutofillServiceEnabled(): " + enabled);
        runShellCommand("cmd autofill set default-augmented-service-enabled 0 %s",
                Boolean.toString(enabled));
    }

    /**
     * Sets the pcc detection service temporarily for 300 seconds.
     */
    public static void setAutofillDetectionService(String service) {
        Log.d(TAG, "setAutofillDetectionService");
        runShellCommand("cmd autofill set temporary-detection-service 0 %s 30000",
                service);
    }

    /**
     * Reset the pcc detection service
     */
    public static void resetAutofillDetectionService() {
        Log.d(TAG, "resetAutofillDetectionService");
        runShellCommand("cmd autofill set temporary-detection-service 0");
    }

    /**
     * Gets the instrumentation context.
     */
    public static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    /**
     * Asserts the node has an {@code HTMLInfo} property, with the given tag.
     */
    public static HtmlInfo assertHasHtmlTag(ViewNode node, String expectedTag) {
        final HtmlInfo info = node.getHtmlInfo();
        assertWithMessage("node doesn't have htmlInfo").that(info).isNotNull();
        assertWithMessage("wrong tag").that(info.getTag()).isEqualTo(expectedTag);
        return info;
    }

    /**
     * Gets the value of an {@code HTMLInfo} attribute.
     */
    @Nullable
    public static String getAttributeValue(HtmlInfo info, String attribute) {
        for (Pair<String, String> pair : info.getAttributes()) {
            if (pair.first.equals(attribute)) {
                return pair.second;
            }
        }
        return null;
    }

    /**
     * Asserts a {@code HTMLInfo} has an attribute with a given value.
     */
    public static void assertHasAttribute(HtmlInfo info, String attribute, String expectedValue) {
        final String actualValue = getAttributeValue(info, attribute);
        assertWithMessage("Attribute %s not found", attribute).that(actualValue).isNotNull();
        assertWithMessage("Wrong value for Attribute %s", attribute)
            .that(actualValue).isEqualTo(expectedValue);
    }

    /**
     * Finds a {@link WebView} node given its expected form name.
     */
    public static ViewNode findWebViewNodeByFormName(AssistStructure structure, String formName) {
        return findNodeByFilter(structure, formName, WEBVIEW_FORM_FILTER);
    }

    private static void assertClientState(Object container, Bundle clientState,
            String key, String value) {
        assertWithMessage("'%s' should have client state", container)
            .that(clientState).isNotNull();
        assertWithMessage("Wrong number of client state extras on '%s'", container)
            .that(clientState.keySet().size()).isEqualTo(1);
        assertWithMessage("Wrong value for client state key (%s) on '%s'", key, container)
            .that(clientState.getString(key)).isEqualTo(value);
    }

    /**
     * Asserts the content of a {@link FillEventHistory#getClientState()}.
     *
     * @param history event to be asserted
     * @param key the only key expected in the client state bundle
     * @param value the only value expected in the client state bundle
     */
    @SuppressWarnings("javadoc")
    public static void assertDeprecatedClientState(@NonNull FillEventHistory history,
            @NonNull String key, @NonNull String value) {
        assertThat(history).isNotNull();
        @SuppressWarnings("deprecation")
        final Bundle clientState = history.getClientState();
        assertClientState(history, clientState, key, value);
    }

    /**
     * Asserts the {@link FillEventHistory#getClientState()} is not set.
     *
     * @param history event to be asserted
     */
    @SuppressWarnings("javadoc")
    public static void assertNoDeprecatedClientState(@NonNull FillEventHistory history) {
        assertThat(history).isNotNull();
        @SuppressWarnings("deprecation")
        final Bundle clientState = history.getClientState();
        assertWithMessage("History '%s' should not have client state", history)
             .that(clientState).isNull();
    }

    private static void assertFillEventPresentationType(FillEventHistory.Event event,
            int expectedType) {
        assertThat(event.getUiType()).isEqualTo(expectedType);
    }

    /**
     * Asserts the content of a {@link android.service.autofill.FillEventHistory.Event}.
     *
     * @param event event to be asserted
     * @param eventType expected type
     * @param datasetId dataset set id expected in the event
     * @param key the only key expected in the client state bundle (or {@code null} if it shouldn't
     * have client state)
     * @param value the only value expected in the client state bundle (or {@code null} if it
     * shouldn't have client state)
     * @param fieldClassificationResults expected results when asserting field classification
     */
    private static void assertFillEvent(@NonNull FillEventHistory.Event event,
            int eventType, @Nullable String datasetId,
            @Nullable String key, @Nullable String value,
            @Nullable FieldClassificationResult[] fieldClassificationResults) {
        assertThat(event).isNotNull();
        assertWithMessage("Wrong type for %s", event).that(event.getType()).isEqualTo(eventType);
        if (datasetId == null) {
            assertWithMessage("Event %s should not have dataset id", event)
                .that(event.getDatasetId()).isNull();
        } else {
            assertWithMessage("Wrong dataset id for %s", event)
                .that(event.getDatasetId()).isEqualTo(datasetId);
        }
        final Bundle clientState = event.getClientState();
        if (key == null) {
            assertWithMessage("Event '%s' should not have client state", event)
                .that(clientState).isNull();
        } else {
            assertClientState(event, clientState, key, value);
        }
        assertWithMessage("Event '%s' should not have selected datasets", event)
                .that(event.getSelectedDatasetIds()).isEmpty();
        assertWithMessage("Event '%s' should not have ignored datasets", event)
                .that(event.getIgnoredDatasetIds()).isEmpty();
        assertWithMessage("Event '%s' should not have changed fields", event)
                .that(event.getChangedFields()).isEmpty();
        assertWithMessage("Event '%s' should not have manually-entered fields", event)
                .that(event.getManuallyEnteredField()).isEmpty();
        final Map<AutofillId, FieldClassification> detectedFields = event.getFieldsClassification();
        if (fieldClassificationResults == null) {
            assertThat(detectedFields).isEmpty();
        } else {
            assertThat(detectedFields).hasSize(fieldClassificationResults.length);
            int i = 0;
            for (Entry<AutofillId, FieldClassification> entry : detectedFields.entrySet()) {
                assertMatches(i, entry, fieldClassificationResults[i]);
                i++;
            }
        }
    }

    private static void assertMatches(int i, Entry<AutofillId, FieldClassification> actualResult,
            FieldClassificationResult expectedResult) {
        assertWithMessage("Wrong field id at index %s", i).that(actualResult.getKey())
                .isEqualTo(expectedResult.id);
        final List<Match> matches = actualResult.getValue().getMatches();
        assertWithMessage("Wrong number of matches: " + matches).that(matches.size())
                .isEqualTo(expectedResult.categoryIds.length);
        for (int j = 0; j < matches.size(); j++) {
            final Match match = matches.get(j);
            assertWithMessage("Wrong categoryId at (%s, %s): %s", i, j, match)
                .that(match.getCategoryId()).isEqualTo(expectedResult.categoryIds[j]);
            assertWithMessage("Wrong score at (%s, %s): %s", i, j, match)
                .that(match.getScore()).isWithin(0.01f).of(expectedResult.scores[j]);
        }
    }

    /**
     * Asserts the content of a
     * {@link android.service.autofill.FillEventHistory.Event#TYPE_DATASET_SELECTED} event.
     *
     * @param event event to be asserted
     * @param datasetId dataset set id expected in the event
     */
    public static void assertFillEventForDatasetSelected(@NonNull FillEventHistory.Event event,
            @Nullable String datasetId, int uiType) {
        assertFillEvent(event, TYPE_DATASET_SELECTED, datasetId, null, null, null);
        assertFillEventPresentationType(event, uiType);
    }

    /**
     * Asserts that {@android.service.autofill.FillEventHistory.Event#TYPE_VIEW_REQUESTED_AUTOFILL}
     * is present in the FillEventHistory
     */
    public static void assertFillEventForViewEntered(@NonNull FillEventHistory.Event event) {
        assertFillEvent(event, TYPE_VIEW_REQUESTED_AUTOFILL, null, null, null, null);
    }

    /**
     * Asserts the content of a
     * {@link android.service.autofill.FillEventHistory.Event#TYPE_DATASET_SELECTED} event.
     *
     * @param event event to be asserted
     * @param datasetId dataset set id expected in the event
     * @param key the only key expected in the client state bundle
     * @param value the only value expected in the client state bundle
     */
    public static void assertFillEventForDatasetSelected(@NonNull FillEventHistory.Event event,
            @Nullable String datasetId, @Nullable String key, @Nullable String value, int uiType) {
        assertFillEvent(event, TYPE_DATASET_SELECTED, datasetId, key, value, null);
        assertFillEventPresentationType(event, uiType);
    }

    /**
     * Asserts the content of a
     * {@link android.service.autofill.FillEventHistory.Event#TYPE_SAVE_SHOWN} event.
     *
     * @param event event to be asserted
     * @param datasetId dataset set id expected in the event
     * @param key the only key expected in the client state bundle
     * @param value the only value expected in the client state bundle
     */
    public static void assertFillEventForSaveShown(@NonNull FillEventHistory.Event event,
            @Nullable String datasetId, @NonNull String key, @NonNull String value) {
        assertFillEvent(event, TYPE_SAVE_SHOWN, datasetId, key, value, null);
    }

    /**
     * Asserts the content of a
     * {@link android.service.autofill.FillEventHistory.Event#TYPE_SAVE_SHOWN} event.
     *
     * @param event event to be asserted
     * @param datasetId dataset set id expected in the event
     */
    public static void assertFillEventForSaveShown(@NonNull FillEventHistory.Event event,
            @Nullable String datasetId) {
        assertFillEvent(event, TYPE_SAVE_SHOWN, datasetId, null, null, null);
    }

    /**
     * Asserts the content of a
     * {@link android.service.autofill.FillEventHistory.Event#TYPE_DATASETS_SHOWN} event.
     *
     * @param event event to be asserted
     * @param key the only key expected in the client state bundle
     * @param value the only value expected in the client state bundle
     * @param uiType the expected ui presentation type
     */
    public static void assertFillEventForDatasetShown(@NonNull FillEventHistory.Event event,
            @NonNull String key, @NonNull String value, int uiType) {
        assertFillEvent(event, TYPE_DATASETS_SHOWN, NULL_DATASET_ID, key, value, null);
        assertFillEventPresentationType(event, uiType);
    }

    /**
     * Asserts the content of a
     * {@link android.service.autofill.FillEventHistory.Event#TYPE_DATASETS_SHOWN} event.
     *
     * @param event event to be asserted
     */
    public static void assertFillEventForDatasetShown(@NonNull FillEventHistory.Event event,
            int uiType) {
        assertFillEvent(event, TYPE_DATASETS_SHOWN, NULL_DATASET_ID, null, null, null);
        assertFillEventPresentationType(event, uiType);
    }

    /**
     * Asserts the content of a
     * {@link android.service.autofill.FillEventHistory.Event#TYPE_DATASET_AUTHENTICATION_SELECTED}
     * event.
     *
     * @param event event to be asserted
     * @param datasetId dataset set id expected in the event
     * @param key the only key expected in the client state bundle
     * @param value the only value expected in the client state bundle
     * @param uiType the expected ui presentation type
     */
    public static void assertFillEventForDatasetAuthenticationSelected(
            @NonNull FillEventHistory.Event event,
            @Nullable String datasetId, @NonNull String key, @NonNull String value, int uiType) {
        assertFillEvent(event, TYPE_DATASET_AUTHENTICATION_SELECTED, datasetId, key, value, null);
        assertFillEventPresentationType(event, uiType);
    }

    /**
     * Asserts the content of a
     * {@link android.service.autofill.FillEventHistory.Event#TYPE_AUTHENTICATION_SELECTED} event.
     *
     * @param event event to be asserted
     * @param datasetId dataset set id expected in the event
     * @param key the only key expected in the client state bundle
     * @param value the only value expected in the client state bundle
     * @param uiType the expected ui presentation type
     */
    public static void assertFillEventForAuthenticationSelected(
            @NonNull FillEventHistory.Event event,
            @Nullable String datasetId, @NonNull String key, @NonNull String value, int uiType) {
        assertFillEvent(event, TYPE_AUTHENTICATION_SELECTED, datasetId, key, value, null);
        assertFillEventPresentationType(event, uiType);
    }

    public static void assertFillEventForFieldsClassification(@NonNull FillEventHistory.Event event,
            @NonNull AutofillId fieldId, @NonNull String categoryId, float score) {
        assertFillEvent(event, TYPE_CONTEXT_COMMITTED, null, null, null,
                new FieldClassificationResult[] {
                        new FieldClassificationResult(fieldId, categoryId, score)
                });
    }

    public static void assertFillEventForFieldsClassification(@NonNull FillEventHistory.Event event,
            @NonNull FieldClassificationResult[] results) {
        assertFillEvent(event, TYPE_CONTEXT_COMMITTED, null, null, null, results);
    }

    public static void assertFillEventForContextCommitted(@NonNull FillEventHistory.Event event) {
        assertFillEvent(event, TYPE_CONTEXT_COMMITTED, null, null, null, null);
    }

    @NonNull
    public static String getActivityName(List<FillContext> contexts) {
        if (contexts == null) return "N/A (null contexts)";

        if (contexts.isEmpty()) return "N/A (empty contexts)";

        final AssistStructure structure = contexts.get(contexts.size() - 1).getStructure();
        if (structure == null) return "N/A (no AssistStructure)";

        final ComponentName componentName = structure.getActivityComponent();
        if (componentName == null) return "N/A (no component name)";

        return componentName.flattenToShortString();
    }

    public static void assertFloat(float actualValue, float expectedValue) {
        assertThat(actualValue).isWithin(1.0e-10f).of(expectedValue);
    }

    public static void assertHasFlags(int actualFlags, int expectedFlags) {
        assertWithMessage("Flags %s not in %s", expectedFlags, actualFlags)
                .that(actualFlags & expectedFlags).isEqualTo(expectedFlags);
    }

    public static void assertNoFlags(int actualFlags, int expectedFlags) {
        assertWithMessage("Flags %s in %s", expectedFlags, actualFlags)
                .that(actualFlags & expectedFlags).isEqualTo(0);
    }

    public static String callbackEventAsString(int event) {
        switch (event) {
            case AutofillCallback.EVENT_INPUT_HIDDEN:
                return "HIDDEN";
            case AutofillCallback.EVENT_INPUT_SHOWN:
                return "SHOWN";
            case AutofillCallback.EVENT_INPUT_UNAVAILABLE:
                return "UNAVAILABLE";
            default:
                return "UNKNOWN:" + event;
        }
    }

    public static String importantForAutofillAsString(int mode) {
        switch (mode) {
            case View.IMPORTANT_FOR_AUTOFILL_AUTO:
                return "IMPORTANT_FOR_AUTOFILL_AUTO";
            case View.IMPORTANT_FOR_AUTOFILL_YES:
                return "IMPORTANT_FOR_AUTOFILL_YES";
            case View.IMPORTANT_FOR_AUTOFILL_YES_EXCLUDE_DESCENDANTS:
                return "IMPORTANT_FOR_AUTOFILL_YES_EXCLUDE_DESCENDANTS";
            case View.IMPORTANT_FOR_AUTOFILL_NO:
                return "IMPORTANT_FOR_AUTOFILL_NO";
            case View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS:
                return "IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS";
            default:
                return "UNKNOWN:" + mode;
        }
    }

    public static boolean hasHint(@Nullable String[] hints, @Nullable Object expectedHint) {
        if (hints == null || expectedHint == null) return false;
        for (String actualHint : hints) {
            if (expectedHint.equals(actualHint)) return true;
        }
        return false;
    }

    public static Bundle newClientState(String key, String value) {
        final Bundle clientState = new Bundle();
        clientState.putString(key, value);
        return clientState;
    }

    public static void assertAuthenticationClientState(String where, Bundle data,
            String expectedKey, String expectedValue) {
        assertWithMessage("no client state on %s", where).that(data).isNotNull();
        final String extraValue = data.getString(expectedKey);
        assertWithMessage("invalid value for %s on %s", expectedKey, where)
                .that(extraValue).isEqualTo(expectedValue);
    }

    /**
     * Asserts that 2 bitmaps have are the same. If they aren't throws an exception and dump them
     * locally so their can be visually inspected.
     *
     * @param filename base name of the files generated in case of error
     * @param bitmap1 first bitmap to be compared
     * @param bitmap2 second bitmap to be compared
     */
    // TODO: move to common code
    public static void assertBitmapsAreSame(@NonNull String filename, @Nullable Bitmap bitmap1,
            @Nullable Bitmap bitmap2) throws IOException {
        assertWithMessage("1st bitmap is null").that(bitmap1).isNotNull();
        assertWithMessage("2nd bitmap is null").that(bitmap2).isNotNull();
        final boolean same = bitmap1.sameAs(bitmap2);
        if (same) {
            Log.v(TAG, "bitmap comparison passed for " + filename);
            return;
        }

        final File dir = getLocalDirectory();
        if (dir == null) {
            throw new AssertionError("bitmap comparison failed for " + filename
                    + ", and bitmaps could not be dumped on " + dir);
        }
        final File dump1 = dumpBitmap(bitmap1, dir, filename + "-1.png");
        final File dump2 = dumpBitmap(bitmap2, dir, filename + "-2.png");
        throw new AssertionError(
                "bitmap comparison failed; check contents of " + dump1 + " and " + dump2);
    }

    @Nullable
    private static File getLocalDirectory() {
        final File dir = new File(LOCAL_DIRECTORY);
        dir.mkdirs();
        if (!dir.exists()) {
            Log.e(TAG, "Could not create directory " + dir);
            return null;
        }
        return dir;
    }

    @Nullable
    private static File createFile(@NonNull File dir, @NonNull String filename) throws IOException {
        final File file = new File(dir, filename);
        if (file.exists()) {
            Log.v(TAG, "Deleting file " + file);
            file.delete();
        }
        if (!file.createNewFile()) {
            Log.e(TAG, "Could not create file " + file);
            return null;
        }
        return file;
    }

    @Nullable
    private static File dumpBitmap(@NonNull Bitmap bitmap, @NonNull File dir,
            @NonNull String filename) throws IOException {
        final File file = createFile(dir, filename);
        if (file != null) {
            dumpBitmap(bitmap, file);

        }
        return file;
    }

    @Nullable
    public static File dumpBitmap(@NonNull Bitmap bitmap, @NonNull File file) {
        Log.i(TAG, "Dumping bitmap at " + file);
        BitmapUtils.saveBitmap(bitmap, file.getParent(), file.getName());
        return file;
    }

    /**
     * Creates a file in the device, using the name of the current test as a prefix.
     */
    @Nullable
    public static File createTestFile(@NonNull String name) throws IOException {
        final File dir = getLocalDirectory();
        if (dir == null) return null;

        final String prefix = TestNameUtils.getCurrentTestName().replaceAll("\\.|\\(|\\/", "_")
                .replaceAll("\\)", "");
        final String filename = prefix + "-" + name;

        return createFile(dir, filename);
    }

    /**
     * Offers an object to a queue or times out.
     *
     * @return {@code true} if the offer was accepted, {$code false} if it timed out or was
     * interrupted.
     */
    public static <T> boolean offer(BlockingQueue<T> queue, T obj, long timeoutMs) {
        boolean offered = false;
        try {
            offered = queue.offer(obj, timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.w(TAG, "interrupted offering", e);
            Thread.currentThread().interrupt();
        }
        if (!offered) {
            Log.e(TAG, "could not offer " + obj + " in " + timeoutMs + "ms");
        }
        return offered;
    }

    /**
     * Calls this method to assert given {@code string} is equal to {@link #LARGE_STRING}, as
     * comparing its value using standard assertions might ANR.
     */
    public static void assertEqualsToLargeString(@NonNull String string) {
        assertThat(string).isNotNull();
        assertThat(string).hasLength(LARGE_STRING_SIZE);
        assertThat(string.charAt(0)).isEqualTo(LARGE_STRING_CHAR);
        assertThat(string.charAt(LARGE_STRING_SIZE - 1)).isEqualTo(LARGE_STRING_CHAR);
    }

    /**
     * Asserts that autofill is enabled in the context, retrying if necessariy.
     */
    public static void assertAutofillEnabled(@NonNull Context context, boolean expected)
            throws Exception {
        assertAutofillEnabled(context.getSystemService(AutofillManager.class), expected);
    }

    /**
     * Asserts that autofill is enabled in the manager, retrying if necessariy.
     */
    public static void assertAutofillEnabled(@NonNull AutofillManager afm, boolean expected)
            throws Exception {
        Timeouts.IDLE_UNBIND_TIMEOUT.run("assertEnabled(" + expected + ")", () -> {
            final boolean actual = afm.isEnabled();
            Log.v(TAG, "assertEnabled(): expected=" + expected + ", actual=" + actual);
            return actual == expected ? "not_used" : null;
        });
    }

    /**
     * Asserts these autofill ids are the same, except for the session.
     */
    public static void assertEqualsIgnoreSession(@NonNull AutofillId id1, @NonNull AutofillId id2) {
        assertWithMessage("id1 is null").that(id1).isNotNull();
        assertWithMessage("id2 is null").that(id2).isNotNull();
        assertWithMessage("%s is not equal to %s", id1, id2).that(id1.equalsIgnoreSession(id2))
                .isTrue();
    }

    /**
     * Asserts {@link View#isAutofilled()} state of the given view, waiting if necessarity to avoid
     * race conditions.
     */
    public static void assertViewAutofillState(@NonNull View view, boolean expected)
            throws Exception {
        Timeouts.FILL_TIMEOUT.run("assertViewAutofillState(" + view + ", " + expected + ")",
                () -> {
                    final boolean actual = view.isAutofilled();
                    Log.v(TAG, "assertViewAutofillState(): expected=" + expected + ", actual="
                            + actual);
                    return actual == expected ? "not_used" : null;
                });
    }

    /**
     * Allows the test to draw overlaid windows.
     *
     * <p>Should call {@link #disallowOverlays()} afterwards.
     */
    public static void allowOverlays() {
        ShellUtils.setOverlayPermissions(MY_PACKAGE, true);
    }

    /**
     * Disallow the test to draw overlaid windows.
     *
     * <p>Should call {@link #disallowOverlays()} afterwards.
     */
    public static void disallowOverlays() {
        ShellUtils.setOverlayPermissions(MY_PACKAGE, false);
    }

    public static RemoteViews createPresentation(String message) {
        final RemoteViews presentation = new RemoteViews(getContext()
                .getPackageName(), R.layout.list_item);
        presentation.setTextViewText(R.id.text1, message);
        return presentation;
    }

    public static InlinePresentation createInlinePresentation(String message) {
        final PendingIntent dummyIntent = PendingIntent.getActivity(getContext(), 0, new Intent(),
                PendingIntent.FLAG_IMMUTABLE);
        return createInlinePresentation(message, dummyIntent, false);
    }

    public static InlinePresentation createInlinePresentation(String message,
            PendingIntent attribution) {
        return createInlinePresentation(message, attribution, false);
    }

    public static InlinePresentation createPinnedInlinePresentation(String message) {
        final PendingIntent dummyIntent = PendingIntent.getActivity(getContext(), 0, new Intent(),
                PendingIntent.FLAG_IMMUTABLE);
        return createInlinePresentation(message, dummyIntent, true);
    }

    private static InlinePresentation createInlinePresentation(@NonNull String message,
            @NonNull PendingIntent attribution, boolean pinned) {
        return new InlinePresentation(
                InlineSuggestionUi.newContentBuilder(attribution)
                        .setTitle(message).build().getSlice(),
                new InlinePresentationSpec.Builder(new Size(100, 100), new Size(400, 100))
                        .build(), /* pinned= */ pinned);
    }

    public static InlinePresentation createInlineTooltipPresentation(
            @NonNull String message) {
        final PendingIntent dummyIntent = PendingIntent.getActivity(getContext(), 0, new Intent(),
                PendingIntent.FLAG_IMMUTABLE);
        return createInlineTooltipPresentation(message, dummyIntent);
    }

    private static InlinePresentation createInlineTooltipPresentation(
            @NonNull String message, @NonNull PendingIntent attribution) {
        return InlinePresentation.createTooltipPresentation(
                InlineSuggestionUi.newContentBuilder(attribution)
                        .setTitle(message).build().getSlice(),
                new InlinePresentationSpec.Builder(new Size(100, 100), new Size(400, 100))
                        .build());
    }

    public static void mockSwitchInputMethod(@NonNull Context context) throws Exception {
        final ContentResolver cr = context.getContentResolver();
        final int subtype = Settings.Secure.getInt(cr, SELECTED_INPUT_METHOD_SUBTYPE);
        Settings.Secure.putInt(cr, SELECTED_INPUT_METHOD_SUBTYPE, subtype);
    }

    /**
     * Reset AutofillOptions to avoid cts package was added to augmented autofill allowlist.
     */
    public static void resetApplicationAutofillOptions(@NonNull Context context) {
        AutofillOptions options = AutofillOptions.forWhitelistingItself();
        options.augmentedAutofillEnabled = false;
        context.getApplicationContext().setAutofillOptions(options);
    }

    /**
     * Clear AutofillOptions.
     */
    public static void clearApplicationAutofillOptions(@NonNull Context context) {
        context.getApplicationContext().setAutofillOptions(null);
    }

    /**
     * Set device config to set flag values.
     */
    public static void setDeviceConfig(
            @NonNull Context context, @NonNull String feature, boolean value) {
        DeviceConfigStateManager deviceConfigStateManager =
                new DeviceConfigStateManager(context, DeviceConfig.NAMESPACE_AUTOFILL, feature);
        setDeviceConfig(deviceConfigStateManager, String.valueOf(value));
    }

    /**
     * Enable fill dialog feature
     */
    public static void enableFillDialogFeature(@NonNull Context context) {
        DeviceConfigStateManager deviceConfigStateManager =
                new DeviceConfigStateManager(context, DeviceConfig.NAMESPACE_AUTOFILL,
                        AutofillFeatureFlags.DEVICE_CONFIG_AUTOFILL_DIALOG_ENABLED);
        setDeviceConfig(deviceConfigStateManager, "true");
    }

    /**
     * Enable fill dialog feature
     */
    public static void disableFillDialogFeature(@NonNull Context context) {
        DeviceConfigStateManager deviceConfigStateManager =
                new DeviceConfigStateManager(context, DeviceConfig.NAMESPACE_AUTOFILL,
                        AutofillFeatureFlags.DEVICE_CONFIG_AUTOFILL_DIALOG_ENABLED);
        setDeviceConfig(deviceConfigStateManager, "false");
    }

    /**
     * Enable PCC Detection Feature Hints
     */
    public static void enablePccDetectionFeature(@NonNull Context context, String...types) {
        DeviceConfigStateManager deviceConfigStateManager =
                new DeviceConfigStateManager(context, DeviceConfig.NAMESPACE_AUTOFILL,
                        AutofillFeatureFlags.DEVICE_CONFIG_AUTOFILL_PCC_FEATURE_PROVIDER_HINTS);
        setDeviceConfig(deviceConfigStateManager, TextUtils.join(",", types));

        DeviceConfigStateManager deviceConfigStateManager2 =
                new DeviceConfigStateManager(context, DeviceConfig.NAMESPACE_AUTOFILL,
                        AutofillFeatureFlags.DEVICE_CONFIG_AUTOFILL_PCC_CLASSIFICATION_ENABLED);
        setDeviceConfig(deviceConfigStateManager2, "true");
    }

    /**
     * Enable PCC Detection Feature Hints
     */
    public static void preferPccDetectionOverProvider(@NonNull Context context, boolean preferPcc) {
        DeviceConfigStateManager deviceConfigStateManager =
                new DeviceConfigStateManager(context, DeviceConfig.NAMESPACE_AUTOFILL,
                        "prefer_provider_over_pcc");
        setDeviceConfig(deviceConfigStateManager, String.valueOf(!preferPcc));
    }

    /**
     * Disable PCC Detection Feature
     */
    public static void disablePccDetectionFeature(@NonNull Context context) {
        DeviceConfigStateManager deviceConfigStateManager2 =
                new DeviceConfigStateManager(context, DeviceConfig.NAMESPACE_AUTOFILL,
                        AutofillFeatureFlags.DEVICE_CONFIG_AUTOFILL_PCC_CLASSIFICATION_ENABLED);
        setDeviceConfig(deviceConfigStateManager2, "false");
    }

    /**
     * Set hints list for fill dialog
     */
    public static void setFillDialogHints(@NonNull Context context, @Nullable String hints) {
        DeviceConfigStateManager deviceConfigStateManager =
                new DeviceConfigStateManager(context, DeviceConfig.NAMESPACE_AUTOFILL,
                        DEVICE_CONFIG_AUTOFILL_DIALOG_HINTS);
        setDeviceConfig(deviceConfigStateManager, hints);
    }

    public static void setDeviceConfig(@NonNull DeviceConfigStateManager deviceConfigStateManager,
            @Nullable String value) {
        final String previousValue = deviceConfigStateManager.get();
        if (TextUtils.isEmpty(value) && TextUtils.isEmpty(previousValue)
                || TextUtils.equals(previousValue, value)) {
            Log.v(TAG, "No changed in config: " + deviceConfigStateManager);
            return;
        }

        deviceConfigStateManager.set(value);
    }

    public static boolean isPccFieldClassificationSet(@NonNull Context context) {
        return Boolean.valueOf(runShellCommand(
                "cmd autofill get field-detection-service-enabled " + context.getUserId()));
    }

    /**
     * Whether IME is showing
     */
    public static boolean isImeShowing(WindowInsets rootWindowInsets) {
        if (rootWindowInsets != null && rootWindowInsets.isVisible(WindowInsets.Type.ime())) {
            return true;
        }
        return false;
    }

    public static int getSystemResourceId(String id, String type, String packageName) {
        return Resources.getSystem().getIdentifier(id, type, packageName);
    }

    /**
     * Asserts whether mock IME is showing
     */
    public static void assertMockImeStatus(AbstractAutoFillActivity activity,
            boolean expectedImeShow) throws Exception {
        Timeouts.MOCK_IME_TIMEOUT.run("assertMockImeStatus(" + expectedImeShow + ")",
                () -> {
                    final boolean actual = isImeShowing(activity.getRootWindowInsets());
                    Log.v(TAG, "assertMockImeStatus(): expected=" + expectedImeShow + ", actual="
                            + actual);
                    return actual == expectedImeShow ? "expected" : null;
                });
    }

    /**
     * Make sure the activity that the name is clazz resumed.
     */
    public static void assertActivityShownInBackground(Class<?> clazz) throws Exception {
        Timeouts.UI_TIMEOUT.run("activity is not resumed: " + clazz, () -> {
            ArrayList<Boolean> result = new ArrayList<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                final Collection<Activity> stage = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED);
                for (Activity act : stage) {
                    if (act.getClass().equals(clazz)) {
                        result.add(Boolean.TRUE);
                    }
                }
            });
            return result.isEmpty() ? null : Boolean.TRUE;
        });
    }

    /**
     * Whether the device is TV.
     * @param context
     * @return true if the device is TV, false otherwise
     */
    public static boolean isTv(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION);
    }

    private Helper() {
        throw new UnsupportedOperationException("contain static methods only");
    }

    public enum DeviceStateEnum {
        HALF_FOLDED,
        REAR_DISPLAY
    };

    /**
     * Test if the device is in half-folded or rear display state.
     */
    private static final class DeviceStateAssessor implements DeviceStateCallback {
        DeviceStateManager mDeviceStateManager;
        int[] mHalfFoldedStates;
        int[] mRearDisplayStates;
        int mCurrentState = -1;

        DeviceStateAssessor(Context context) {
            Resources systemRes = Resources.getSystem();
            mHalfFoldedStates = getStatesFromConfig(systemRes, "config_halfFoldedDeviceStates");
            mRearDisplayStates = getStatesFromConfig(systemRes, "config_rearDisplayDeviceStates");
            try {
                mDeviceStateManager = context.getSystemService(DeviceStateManager.class);
                mDeviceStateManager.registerCallback(context.getMainExecutor(), this);
                Log.v(TAG, "DeviceStateAssessor initialized halfFoldedStates.length="
                        + mHalfFoldedStates.length + ", readDisplayStates.length="
                        + mRearDisplayStates.length);
            } catch (java.lang.IllegalStateException e) {
                Log.v(TAG, "DeviceStateManager not available: cannot check for half-fold");
            }
        }

        private int[] getStatesFromConfig(Resources systemRes, String configKey) {
            int statesArrayIdentifier = systemRes.getIdentifier(configKey, "array", "android");
            if (statesArrayIdentifier == 0) {
                return new int[0];
            } else {
                return systemRes.getIntArray(statesArrayIdentifier);
            }
        }

        public void onStateChanged(int state) {
            synchronized (this) {
                mCurrentState = state;
                this.notify();
            }
        }

        void close() {
            if (mDeviceStateManager != null) {
                mDeviceStateManager.unregisterCallback(this);
            }
        }

        boolean isDeviceInState(DeviceStateEnum deviceState) throws InterruptedException {
            int[] states;
            switch(deviceState) {
                case HALF_FOLDED:
                    states = mHalfFoldedStates;
                    break;
                case REAR_DISPLAY:
                    states = mRearDisplayStates;
                    break;
                default:
                    return false;
            }
            if (states.length == 0 || mDeviceStateManager == null) {
                return false;
            }
            synchronized (this) {
                if (mCurrentState == -1) {
                    this.wait(1000);
                }
            }
            if (mCurrentState == -1) {
                Log.w(TAG, "DeviceStateCallback not called within 1 second");
            }
            Log.v(TAG, "Current state=" + mCurrentState + ", states[0]="
                    + states[0]);
            return Arrays.stream(states).anyMatch(x -> x == mCurrentState);
        }
    }

    public static boolean isDeviceInState(Context context, DeviceStateEnum deviceState) {
        DeviceStateAssessor deviceStateAssessor = new DeviceStateAssessor(context);
        try {
            return deviceStateAssessor.isDeviceInState(deviceState);
        } catch (InterruptedException e) {
            return false;
        } finally {
            deviceStateAssessor.close();
        }
    }

    public static class CustomDescriptionUtils {
        public static RemoteViews newTemplate(String packageName) {
            return new RemoteViews(packageName, R.layout.custom_description_with_link);
        }

        public static final CustomDescription.Builder newCustomDescriptionBuilder(
                Context context, Class<? extends Activity> activityClass, String packageName) {
            final Intent intent = new Intent(context, activityClass);
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
            return newCustomDescriptionBuilder(context, intent, packageName);
        }

        public static final CustomDescription newCustomDescription(
                Context context, Class<? extends Activity> activityClass, String packageName) {
            return newCustomDescriptionBuilder(context, activityClass, packageName).build();
        }

        public static final CustomDescription.Builder newCustomDescriptionBuilder(
                Context context, Intent intent, String packageName) {
            final RemoteViews presentation = newTemplate(packageName);
            final PendingIntent pendingIntent =
                    PendingIntent.getActivity(
                            context,
                            0,
                            intent,
                            PendingIntent.FLAG_MUTABLE,
                            ActivityOptions.makeBasic()
                                    .setPendingIntentCreatorBackgroundActivityStartMode(
                                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                                    .toBundle());
            presentation.setOnClickPendingIntent(R.id.link, pendingIntent);
            return new CustomDescription.Builder(presentation);
        }

        public static final CustomDescription newCustomDescription(
                Context context, Intent intent, String packageName) {
            return newCustomDescriptionBuilder(context, intent, packageName).build();
        }
    }

    public static class FieldClassificationResult {
        public final AutofillId id;
        public final String[] categoryIds;
        public final float[] scores;

        public FieldClassificationResult(@NonNull AutofillId id, @NonNull String categoryId,
                float score) {
            this(id, new String[]{categoryId}, new float[]{score});
        }

        public FieldClassificationResult(@NonNull AutofillId id, @NonNull String[] categoryIds,
                float[] scores) {
            this.id = id;
            this.categoryIds = categoryIds;
            this.scores = scores;
        }
    }
}
