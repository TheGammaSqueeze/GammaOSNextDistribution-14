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

package android.ext.services.notification;

import static android.app.Notification.CATEGORY_EMAIL;
import static android.app.Notification.CATEGORY_MESSAGE;
import static android.app.Notification.CATEGORY_SOCIAL;
import static android.app.Notification.EXTRA_MESSAGES;
import static android.app.Notification.EXTRA_SUB_TEXT;
import static android.app.Notification.EXTRA_TEXT;
import static android.app.Notification.EXTRA_TEXT_LINES;
import static android.app.Notification.EXTRA_TITLE;

import android.app.Notification;
import android.app.Notification.MessagingStyle;
import android.app.Notification.MessagingStyle.Message;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.Flags;

import com.android.modules.utils.build.SdkLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class with helper methods related to detecting OTP codes in notifications
 */
public class NotificationOtpDetectionHelper {

    // Use an ArrayList because a List.of list will throw NPE when calling "contains(null)"
    private static final List<String> SENSITIVE_NOTIFICATION_CATEGORIES = new ArrayList<>(
            List.of(CATEGORY_MESSAGE, CATEGORY_EMAIL, CATEGORY_SOCIAL));

    private static final int MAX_SENSITIVE_TEXT_LEN = 600;

    // A regex matching a line start, space, open paren, or colon
    private static final String START = "(^|[\\s\\(:])";


    // One single OTP char. An alphanumeric code, (Specifically "not a non-word character,
    // underscore, or dash"), followed by an optional dash
    private static final String OTP_CHAR = "([\\p{IsAlphabetic}0-9]-?)";

    // Performs a lookahead to find a digit after 0 to 7 OTP_CHARs. This ensures that our potential
    // OTP code contains at least one number
    private static final String FIND_DIGIT = "(?=" + OTP_CHAR + "{0,7}\\d)";

    // Matches between 5 and 8 OTP_CHARs. Here, we are assuming an OTP code is 5-8 characters long
    private static final String OTP_CHARS = "(" + OTP_CHAR + "{5,8})";

    // A regex matching a line end or non-word char (except dash or underscore)
    private static final String END = "(\\W|$)";

    // A regex matching four digit numerical codes, excluding the common years of 19XX and 20XX
    private static final String FOUR_DIGIT_EXCLUDE_YEAR =
            "(1[0-8]\\d\\d|2[1-9]\\d\\d|[03-9]\\d\\d\\d)";

    private static final String ALPHANUM_OTP = "(" + START + FIND_DIGIT + OTP_CHARS + END + ")";

    private static final String FOUR_DIGIT_OTP = "(" + START + FOUR_DIGIT_EXCLUDE_YEAR + END + ")";

    /**
     * Combining the 5 regular expressions above, we get an OTP regex:
     * 1. start with START
     * 2. lookahead to find a digit mixed in with OTP_CHARs
     * 3. find 4-8 OTP_CHARs
     * 4. finish with END
     */
    private static final Matcher OTP_REGEX =
            Pattern.compile(ALPHANUM_OTP + "|" + FOUR_DIGIT_OTP).matcher("");
    /**
     * A Date regular expression. Looks for dates with the month, day, and year separated by dashes.
     * Handles one and two digit months and days, and four or two-digit years. It makes the
     * following assumptions:
     * Dates and months will never be higher than 39
     * If a four digit year is used, the leading digit will be 1 or 2
     * It must begin with the START regex from the OTP regex, and finish with the END regex.
     * This regex is used to eliminate the most common false positive of the OTP regex.
     */
    private static final Matcher DATE_WITH_DASHES =
            Pattern.compile(START + "[0-3]?\\d-[0-3]?\\d-([12]\\d)?\\d\\d" + END).matcher("");

    /**
     * Checks if the sensitive parts of a notification might contain an OTP, based on a regular
     * expression.
     * @param notification The notification whose content should be checked
     * @param ensureNotDate If true, will ensure the content does not match the date regex
     * @return True if the regex matches and ensureNotDate is false, or the date regex failed to
     * match, false otherwise.
     */
    public static boolean matchesOtpRegex(Notification notification, boolean ensureNotDate) {
        if (notification == null || !SdkLevel.isAtLeastV()) {
            return false;
        }

        String sensitiveText = getTextForDetection(notification);
        Matcher otpMatcher = OTP_REGEX.reset(sensitiveText);
        boolean optMatch = otpMatcher.find();
        if (!ensureNotDate || !optMatch) {
            return optMatch;
        }
        Matcher dateMatcher = DATE_WITH_DASHES.reset(sensitiveText);
        if (!dateMatcher.find()) {
            return true;
        }
        // Both the OTP and the date regex match. Now to verify that they match on the same text.
        otpMatcher.reset();
        dateMatcher.reset();
        while (otpMatcher.find()) {
            int otpStart = otpMatcher.start();
            int dateMatcherStart = -1;
            while (!dateMatcher.hitEnd() && dateMatcherStart < otpStart) {
                dateMatcherStart =
                        dateMatcher.find() ? dateMatcher.start() : Integer.MAX_VALUE;
            }
            if (dateMatcherStart != otpStart) {
                // There's at least one OTP that is not matched exactly by a date.
                return true;
            }
        }
        // Every OTP match has a corresponding date match
        return false;
    }

    /**
     * Gets the sections of text in a notification that should be checked for sensitive content.
     * This includes the text, title, subtext, messages, and extra text lines.
     * @param notification The notification whose content should be filtered
     * @return The extracted text fields
     */
    public static String getTextForDetection(Notification notification) {
        if (notification.extras == null || !SdkLevel.isAtLeastV()
                || !Flags.redactSensitiveNotificationsFromUntrustedListeners()) {
            return "";
        }
        Bundle extras = notification.extras;
        CharSequence title = notification.extras.getCharSequence(EXTRA_TITLE);
        CharSequence text = notification.extras.getCharSequence(EXTRA_TEXT);
        CharSequence subText = notification.extras.getCharSequence(EXTRA_SUB_TEXT);
        // TODO b/317408921: Validate that the ML model still works with this
        StringBuilder builder = new StringBuilder()
                .append(title != null ? title : "").append(" ")
                .append(text != null ? text : "").append(" ")
                .append(subText != null ? subText : "").append(" ");
        CharSequence[] textLines = extras.getCharSequenceArray(EXTRA_TEXT_LINES);
        if (textLines != null) {
            for (CharSequence line : textLines) {
                builder.append(line).append(" ");
            }
        }
        List<Message> messages = Message.getMessagesFromBundleArray(
                extras.getParcelableArray(EXTRA_MESSAGES, Parcelable.class));
        // Sort the newest messages (largest timestamp) first
        messages.sort((MessagingStyle.Message lhs, MessagingStyle.Message rhs) ->
                Math.toIntExact(rhs.getTimestamp() - lhs.getTimestamp()));
        for (MessagingStyle.Message message : messages) {
            builder.append(message.getText()).append(" ");
        }
        return builder.length() <= MAX_SENSITIVE_TEXT_LEN ? builder.toString()
                : builder.substring(0, MAX_SENSITIVE_TEXT_LEN);
    }

    /**
     * Determines if a notification should be checked for an OTP, based on category, style, and
     * possible otp content (as determined by a regular expression).
     * @param notification The notification whose content should be checked
     * @return true, if further checks for OTP codes should be performed, false otherwise
     */
    public static boolean shouldCheckForOtp(Notification notification) {
        if (notification == null || !SdkLevel.isAtLeastV()
                || !Flags.redactSensitiveNotificationsFromUntrustedListeners()) {
            return false;
        }
        return SENSITIVE_NOTIFICATION_CATEGORIES.contains(notification.category)
                || isStyle(notification, Notification.MessagingStyle.class)
                || isStyle(notification, Notification.InboxStyle.class)
                || matchesOtpRegex(notification, false)
                || shouldCheckForOtp(notification.publicVersion);
    }

    private static boolean isStyle(Notification notification,
            Class<? extends Notification.Style> styleClass) {
        if (notification.extras == null) {
            return false;
        }
        String templateClass = notification.extras.getString(Notification.EXTRA_TEMPLATE);
        return Objects.equals(templateClass, styleClass.getName());
    }

    private NotificationOtpDetectionHelper() { }
}
