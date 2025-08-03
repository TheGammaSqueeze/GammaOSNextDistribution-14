/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.settings.security;

import static com.android.internal.widget.PasswordValidationError.CONTAINS_INVALID_CHARACTERS;
import static com.android.internal.widget.PasswordValidationError.CONTAINS_SEQUENCE;
import static com.android.internal.widget.PasswordValidationError.NOT_ENOUGH_DIGITS;
import static com.android.internal.widget.PasswordValidationError.NOT_ENOUGH_LETTERS;
import static com.android.internal.widget.PasswordValidationError.NOT_ENOUGH_LOWER_CASE;
import static com.android.internal.widget.PasswordValidationError.NOT_ENOUGH_NON_DIGITS;
import static com.android.internal.widget.PasswordValidationError.NOT_ENOUGH_NON_LETTER;
import static com.android.internal.widget.PasswordValidationError.NOT_ENOUGH_SYMBOLS;
import static com.android.internal.widget.PasswordValidationError.NOT_ENOUGH_UPPER_CASE;
import static com.android.internal.widget.PasswordValidationError.RECENTLY_USED;
import static com.android.internal.widget.PasswordValidationError.TOO_LONG;
import static com.android.internal.widget.PasswordValidationError.TOO_SHORT;

import android.annotation.UserIdInt;
import android.app.admin.DevicePolicyManager;
import android.app.admin.PasswordMetrics;
import android.content.Context;

import androidx.annotation.VisibleForTesting;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.internal.widget.PasswordValidationError;
import com.android.settingslib.utils.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper used by ChooseLockPinPasswordFragment
 * Much of the logic is taken from {@link com.android.settings.password.ChooseLockPassword}
 */
public class PasswordHelper {
    public static final String EXTRA_CURRENT_SCREEN_LOCK = "extra_current_screen_lock";
    public static final String EXTRA_CURRENT_PASSWORD_QUALITY = "extra_current_password_quality";

    private static final Logger LOG = new Logger(PasswordHelper.class);

    private final Context mContext;
    private final LockPatternUtils mLockPatternUtils;
    private final PasswordMetrics mMinMetrics;
    private List<PasswordValidationError> mValidationErrors;
    private boolean mIsPin;
    private boolean mIsPattern;
    private byte[] mPasswordHistoryHashFactor;

    @UserIdInt
    private final int mUserId;

    @DevicePolicyManager.PasswordComplexity
    private final int mMinComplexity;

    public PasswordHelper(Context context, @UserIdInt int userId) {
        mContext = context;
        mUserId = userId;
        mLockPatternUtils = new LockPatternUtils(context);
        mMinMetrics = mLockPatternUtils.getRequestedPasswordMetrics(
                mUserId, /* deviceWideOnly= */ false);
        mMinComplexity = mLockPatternUtils.getRequestedPasswordComplexity(
                mUserId, /* deviceWideOnly= */ false);
    }

    @VisibleForTesting
    PasswordHelper(Context context, @UserIdInt int userId, LockPatternUtils lockPatternUtils,
            PasswordMetrics minMetrics, @DevicePolicyManager.PasswordComplexity int minComplexity) {
        mContext = context;
        mUserId = userId;
        mLockPatternUtils = lockPatternUtils;
        mMinMetrics = minMetrics;
        mMinComplexity = minComplexity;
    }

    /**
     * Validates a proposed new lockscreen credential. Does not check it against the password
     * history, but does all other types of validation such as length, allowed characters, etc.
     * {@link #getCredentialValidationErrorMessages()} can be called afterwards to retrieve the
     * error message(s).
     *
     * @param credential the proposed new lockscreen credential
     * @return whether the new credential is valid
     */
    public boolean validateCredential(LockscreenCredential credential) {
        mValidationErrors = PasswordMetrics.validateCredential(mMinMetrics, mMinComplexity,
                credential);
        mIsPin = credential.isPin();
        mIsPattern = credential.isPattern();
        return mValidationErrors.isEmpty();
    }

    /**
     * Validates a proposed new lockscreen credential. Does the full validation including checking
     * against the password history. {@link #getCredentialValidationErrorMessages()} can be called
     * afterwards to retrieve the error message(s).
     *
     * @param enteredCredential the proposed new lockscreen credential
     * @param existingCredential the current lockscreen credential
     * @return whether the new credential is valid
     */
    public boolean validateCredential(LockscreenCredential enteredCredential,
            LockscreenCredential existingCredential) {
        if (validateCredential(enteredCredential)
                && mLockPatternUtils.checkPasswordHistory(enteredCredential.getCredential(),
                    getPasswordHistoryHashFactor(existingCredential), mUserId)) {
            mValidationErrors =
                    Collections.singletonList(new PasswordValidationError(RECENTLY_USED));
        }
        return mValidationErrors.isEmpty();
    }

    /**
     * Lazily computes and returns the history hash factor of the user id of the current process
     * {@code mUserId}, used for password history check.
     */
    private byte[] getPasswordHistoryHashFactor(LockscreenCredential credential) {
        if (mPasswordHistoryHashFactor == null) {
            mPasswordHistoryHashFactor = mLockPatternUtils.getPasswordHistoryHashFactor(
                    credential != null ? credential : LockscreenCredential.createNone(), mUserId);
        }
        return mPasswordHistoryHashFactor;
    }

    /**
     * Returns a message describing any errors of the last call to {@link
     * #validateCredential(LockscreenCredential)} or {@link
     * #validateCredential(LockscreenCredential, LockscreenCredential)}.
     * Returns an empty string if there were no errors.
     */
    public String getCredentialValidationErrorMessages() {
        List<String> messages = new ArrayList<>();
        for (PasswordValidationError error : mValidationErrors) {
            switch (error.errorCode) {
                case CONTAINS_INVALID_CHARACTERS:
                    messages.add(mContext.getString(R.string.lockpassword_illegal_character));
                    break;
                case NOT_ENOUGH_UPPER_CASE:
                    messages.add(StringUtil.getIcuPluralsString(mContext, error.requirement,
                            R.string.lockpassword_password_requires_uppercase));
                    break;
                case NOT_ENOUGH_LOWER_CASE:
                    messages.add(StringUtil.getIcuPluralsString(mContext, error.requirement,
                            R.string.lockpassword_password_requires_lowercase));
                    break;
                case NOT_ENOUGH_LETTERS:
                    messages.add(StringUtil.getIcuPluralsString(mContext, error.requirement,
                            R.string.lockpassword_password_requires_letters));
                    break;
                case NOT_ENOUGH_DIGITS:
                    messages.add(StringUtil.getIcuPluralsString(mContext, error.requirement,
                            R.string.lockpassword_password_requires_numeric));
                    break;
                case NOT_ENOUGH_SYMBOLS:
                    messages.add(StringUtil.getIcuPluralsString(mContext, error.requirement,
                            R.string.lockpassword_password_requires_symbols));
                    break;
                case NOT_ENOUGH_NON_LETTER:
                    messages.add(StringUtil.getIcuPluralsString(mContext, error.requirement,
                            R.string.lockpassword_password_requires_nonletter));
                    break;
                case NOT_ENOUGH_NON_DIGITS:
                    messages.add(StringUtil.getIcuPluralsString(mContext, error.requirement,
                            R.string.lockpassword_password_requires_nonnumerical));
                    break;
                case TOO_SHORT:
                    messages.add(StringUtil.getIcuPluralsString(mContext, error.requirement,
                            mIsPin
                                    ? R.string.lockpassword_pin_too_short
                            : mIsPattern
                                    ? R.string.lockpattern_recording_incorrect_too_short
                                    : R.string.lockpassword_password_too_short));
                    break;
                case TOO_LONG:
                    messages.add(StringUtil.getIcuPluralsString(mContext, error.requirement + 1,
                            mIsPin
                                    ? R.string.lockpassword_pin_too_long
                                    : R.string.lockpassword_password_too_long));
                    break;
                case CONTAINS_SEQUENCE:
                    messages.add(mContext.getString(
                            R.string.lockpassword_pin_no_sequential_digits));
                    break;
                case RECENTLY_USED:
                    messages.add(mContext.getString(mIsPin
                            ? R.string.lockpassword_pin_recently_used
                            : R.string.lockpassword_password_recently_used));
                    break;
                default:
                    LOG.wtf("unknown error validating password: " + error);
            }
        }
        if (messages.isEmpty() && !mValidationErrors.isEmpty()) {
            // All errors were unknown, so fall back to the default message. If you see this message
            // in the UI, something needs to be added to the switch statement above!
            messages.add(mContext.getString(R.string.lockpassword_invalid_password));
        }
        return String.join("\n", messages);
    }

    /**
     * Zero out credentials and force garbage collection to remove any remnants of user password
     * shards from memory. Should be used in onDestroy for any LockscreenCredential fields.
     *
     * @param credentials the credentials to zero out, can be null
     **/
    public static void zeroizeCredentials(LockscreenCredential... credentials) {
        for (LockscreenCredential credential : credentials) {
            if (credential != null) {
                credential.zeroize();
            }
        }

        System.gc();
        System.runFinalization();
        System.gc();
    }
}
