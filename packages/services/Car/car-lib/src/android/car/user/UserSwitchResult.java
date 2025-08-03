/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.car.user;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Parcelable;
import android.os.UserManager;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.DataClass;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * User switch results.
 *
 * @hide
 */
@DataClass(
        genToString = true,
        genHiddenConstructor = true,
        genHiddenConstDefs = true)
@SystemApi
public final class UserSwitchResult implements Parcelable, OperationResult {

    /**
     * When user switch is successful for both HAL and Android.
     */
    @Status
    public static final int STATUS_SUCCESSFUL = CommonResults.STATUS_SUCCESSFUL;

    /**
     * When user switch is only successful for Hal but not for Android. Hal user switch rollover
     * message have been sent.
     */
    @Status
    public static final int STATUS_ANDROID_FAILURE = CommonResults.STATUS_ANDROID_FAILURE;

    /**
     * When user switch fails for HAL. User switch for Android is not called.
     */
    @Status
    public static final int STATUS_HAL_FAILURE = CommonResults.STATUS_HAL_FAILURE;

    /**
     * When user switch fails for HAL for some internal error. User switch for Android is not
     * called.
     */
    @Status
    public static final int STATUS_HAL_INTERNAL_FAILURE = CommonResults.STATUS_HAL_INTERNAL_FAILURE;

    /**
     * When given parameters or environment states are invalid for switching user. HAL or Android
     * user switch is not requested.
     */
    @Status
    public static final int STATUS_INVALID_REQUEST = CommonResults.STATUS_INVALID_REQUEST;

    /**
     * When user switch fails because of driving safety UX restrictions.
     */
    @Status
    public static final int STATUS_UX_RESTRICTION_FAILURE =
            CommonResults.STATUS_UX_RESTRICTION_FAILURE;

    /**
     * When target user is same as current user.
     */
    @Status
    public static final int STATUS_OK_USER_ALREADY_IN_FOREGROUND =
            CommonResults.LAST_COMMON_STATUS + 1;

    /**
     * When another user switch request for the same target user is in process.
     */
    @Status
    public static final int STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO =
            CommonResults.LAST_COMMON_STATUS + 2;

    /**
     * When another user switch request for a new different target user is received. Previous
     * request is abandoned.
     */
    @Status
    public static final int STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST =
            CommonResults.LAST_COMMON_STATUS + 3;

    /**
     * When switching users is currently not allowed for the user this process is running under.
     */
    @Status
    public static final int STATUS_NOT_SWITCHABLE =
            CommonResults.LAST_COMMON_STATUS + 4;

    /**
     * When logout was called but the current user was not switched by a device admin.
     */
    @Status
    public static final int STATUS_NOT_LOGGED_IN =
            CommonResults.LAST_COMMON_STATUS + 5;

    /**
     * Gets the user switch result status.
     *
     * @return either {@link UserSwitchResult#STATUS_SUCCESSFUL},
     *         {@link UserSwitchResult#STATUS_ANDROID_FAILURE},
     *         {@link UserSwitchResult#STATUS_HAL_FAILURE},
     *         {@link UserSwitchResult#STATUS_HAL_INTERNAL_FAILURE},
     *         {@link UserSwitchResult#STATUS_INVALID_REQUEST},
     *         {@link UserSwitchResult#STATUS_UX_RESTRICTION_FAILURE},
     *         {@link UserSwitchResult#STATUS_OK_USER_ALREADY_IN_FOREGROUND},
     *         {@link UserSwitchResult#STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO},
     *         {@link UserSwitchResult#STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST},
     *         {@link UserSwitchResult#STATUS_NOT_SWITCHABLE}, or
     *         {@link UserSwitchResult#STATUS_NOT_LOGGED_IN}.
     */
    private final @Status int mStatus;

    // TODO(b/214443810): codegen generates call to writeInteger() / readInteger(), we need to
    // manually change to writeInt() / readInt()
    /**
     * Gets the failure status returned by {@link UserManager} when the {@link #getStatus() status}
     * is {@link #STATUS_ANDROID_FAILURE}.
     *
     * @return {@code USER_OPERATION_ERROR_} constants defined by {@link UserManager}, or
     * {@code null} when the {@link #getStatus() status} is not {@link #STATUS_ANDROID_FAILURE}.
     */
    @Nullable
    private final Integer mAndroidFailureStatus;

    /**
     * Gets the error message, if any.
     */
    @Nullable
    private final String mErrorMessage;

    @Override
    public boolean isSuccess() {
        return mStatus == STATUS_SUCCESSFUL || mStatus == STATUS_OK_USER_ALREADY_IN_FOREGROUND;
    }

    /** @hide */
    public UserSwitchResult(@Status int status, @Nullable String errorMessage) {
        this(status, /* androidFailureStatus= */ null, errorMessage);
    }

    // NOTE: codegen generates this method, but without @ExcludeFromCodeCoverageGeneratedReport
    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public int describeContents() {
        return 0;
    }



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car/user/UserSwitchResult.java
    // Added AddedInOrBefore or ApiRequirement Annotation manually
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    /** @hide */
    @IntDef(prefix = "STATUS_", value = {
        STATUS_SUCCESSFUL,
        STATUS_ANDROID_FAILURE,
        STATUS_HAL_FAILURE,
        STATUS_HAL_INTERNAL_FAILURE,
        STATUS_INVALID_REQUEST,
        STATUS_UX_RESTRICTION_FAILURE,
        STATUS_OK_USER_ALREADY_IN_FOREGROUND,
        STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO,
        STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST,
        STATUS_NOT_SWITCHABLE,
        STATUS_NOT_LOGGED_IN
    })
    @Retention(RetentionPolicy.SOURCE)
    @DataClass.Generated.Member
    public @interface Status {}

    @DataClass.Generated.Member
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @NonNull
    public static String statusToString(@Status int value) {
        switch (value) {
            case STATUS_SUCCESSFUL:
                    return "STATUS_SUCCESSFUL";
            case STATUS_ANDROID_FAILURE:
                    return "STATUS_ANDROID_FAILURE";
            case STATUS_HAL_FAILURE:
                    return "STATUS_HAL_FAILURE";
            case STATUS_HAL_INTERNAL_FAILURE:
                    return "STATUS_HAL_INTERNAL_FAILURE";
            case STATUS_INVALID_REQUEST:
                    return "STATUS_INVALID_REQUEST";
            case STATUS_UX_RESTRICTION_FAILURE:
                    return "STATUS_UX_RESTRICTION_FAILURE";
            case STATUS_OK_USER_ALREADY_IN_FOREGROUND:
                    return "STATUS_OK_USER_ALREADY_IN_FOREGROUND";
            case STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO:
                    return "STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO";
            case STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST:
                    return "STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST";
            case STATUS_NOT_SWITCHABLE:
                    return "STATUS_NOT_SWITCHABLE";
            case STATUS_NOT_LOGGED_IN:
                    return "STATUS_NOT_LOGGED_IN";
            default: return Integer.toHexString(value);
        }
    }

    /**
     * Creates a new UserSwitchResult.
     *
     * @param status
     *   Gets the user switch result status.
     *
     *   @return either {@link UserSwitchResult#STATUS_SUCCESSFUL},
     *           {@link UserSwitchResult#STATUS_ANDROID_FAILURE},
     *           {@link UserSwitchResult#STATUS_HAL_FAILURE},
     *           {@link UserSwitchResult#STATUS_HAL_INTERNAL_FAILURE},
     *           {@link UserSwitchResult#STATUS_INVALID_REQUEST},
     *           {@link UserSwitchResult#STATUS_UX_RESTRICTION_FAILURE},
     *           {@link UserSwitchResult#STATUS_OK_USER_ALREADY_IN_FOREGROUND},
     *           {@link UserSwitchResult#STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO},
     *           {@link UserSwitchResult#STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST},
     *           {@link UserSwitchResult#STATUS_NOT_SWITCHABLE}, or
     *           {@link UserSwitchResult#STATUS_NOT_LOGGED_IN}.
     * @param androidFailureStatus
     *   Gets the failure status returned by {@link UserManager} when the {@link #getStatus() status}
     *   is {@link #STATUS_ANDROID_FAILURE}.
     *
     *   @return {@code USER_OPERATION_ERROR_} constants defined by {@link UserManager}, or
     *   {@code null} when the {@link #getStatus() status} is not {@link #STATUS_ANDROID_FAILURE}.
     * @param errorMessage
     *   Gets the error message, if any.
     * @hide
     */
    @DataClass.Generated.Member
    public UserSwitchResult(
            @Status int status,
            @Nullable Integer androidFailureStatus,
            @Nullable String errorMessage) {
        this.mStatus = status;

        if (!(mStatus == STATUS_SUCCESSFUL)
                && !(mStatus == STATUS_ANDROID_FAILURE)
                && !(mStatus == STATUS_HAL_FAILURE)
                && !(mStatus == STATUS_HAL_INTERNAL_FAILURE)
                && !(mStatus == STATUS_INVALID_REQUEST)
                && !(mStatus == STATUS_UX_RESTRICTION_FAILURE)
                && !(mStatus == STATUS_OK_USER_ALREADY_IN_FOREGROUND)
                && !(mStatus == STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO)
                && !(mStatus == STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST)
                && !(mStatus == STATUS_NOT_SWITCHABLE)
                && !(mStatus == STATUS_NOT_LOGGED_IN)) {
            throw new java.lang.IllegalArgumentException(
                    "status was " + mStatus + " but must be one of: "
                            + "STATUS_SUCCESSFUL(" + STATUS_SUCCESSFUL + "), "
                            + "STATUS_ANDROID_FAILURE(" + STATUS_ANDROID_FAILURE + "), "
                            + "STATUS_HAL_FAILURE(" + STATUS_HAL_FAILURE + "), "
                            + "STATUS_HAL_INTERNAL_FAILURE(" + STATUS_HAL_INTERNAL_FAILURE + "), "
                            + "STATUS_INVALID_REQUEST(" + STATUS_INVALID_REQUEST + "), "
                            + "STATUS_UX_RESTRICTION_FAILURE(" + STATUS_UX_RESTRICTION_FAILURE + "), "
                            + "STATUS_OK_USER_ALREADY_IN_FOREGROUND(" + STATUS_OK_USER_ALREADY_IN_FOREGROUND + "), "
                            + "STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO(" + STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO + "), "
                            + "STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST(" + STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST + "), "
                            + "STATUS_NOT_SWITCHABLE(" + STATUS_NOT_SWITCHABLE + "), "
                            + "STATUS_NOT_LOGGED_IN(" + STATUS_NOT_LOGGED_IN + ")");
        }

        this.mAndroidFailureStatus = androidFailureStatus;
        this.mErrorMessage = errorMessage;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Gets the user switch result status.
     *
     * @return either {@link UserSwitchResult#STATUS_SUCCESSFUL},
     *         {@link UserSwitchResult#STATUS_ANDROID_FAILURE},
     *         {@link UserSwitchResult#STATUS_HAL_FAILURE},
     *         {@link UserSwitchResult#STATUS_HAL_INTERNAL_FAILURE},
     *         {@link UserSwitchResult#STATUS_INVALID_REQUEST},
     *         {@link UserSwitchResult#STATUS_UX_RESTRICTION_FAILURE},
     *         {@link UserSwitchResult#STATUS_OK_USER_ALREADY_IN_FOREGROUND},
     *         {@link UserSwitchResult#STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO},
     *         {@link UserSwitchResult#STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST},
     *         {@link UserSwitchResult#STATUS_NOT_SWITCHABLE}, or
     *         {@link UserSwitchResult#STATUS_NOT_LOGGED_IN}.
     */
    @DataClass.Generated.Member
    public @Status int getStatus() {
        return mStatus;
    }

    /**
     * Gets the failure status returned by {@link UserManager} when the {@link #getStatus() status}
     * is {@link #STATUS_ANDROID_FAILURE}.
     *
     * @return {@code USER_OPERATION_ERROR_} constants defined by {@link UserManager}, or
     * {@code null} when the {@link #getStatus() status} is not {@link #STATUS_ANDROID_FAILURE}.
     */
    @DataClass.Generated.Member
    public @Nullable Integer getAndroidFailureStatus() {
        return mAndroidFailureStatus;
    }

    /**
     * Gets the error message, if any.
     */
    @DataClass.Generated.Member
    public @Nullable String getErrorMessage() {
        return mErrorMessage;
    }

    @Override
    @DataClass.Generated.Member
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "UserSwitchResult { " +
                "status = " + statusToString(mStatus) + ", " +
                "androidFailureStatus = " + mAndroidFailureStatus + ", " +
                "errorMessage = " + mErrorMessage +
        " }";
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@android.annotation.NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mAndroidFailureStatus != null) flg |= 0x2;
        if (mErrorMessage != null) flg |= 0x4;
        dest.writeByte(flg);
        dest.writeInt(mStatus);
        if (mAndroidFailureStatus != null) dest.writeInt(mAndroidFailureStatus);
        if (mErrorMessage != null) dest.writeString(mErrorMessage);
    }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ UserSwitchResult(@android.annotation.NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        int status = in.readInt();
        Integer androidFailureStatus = (flg & 0x2) == 0 ? null : (Integer) in.readInt();
        String errorMessage = (flg & 0x4) == 0 ? null : in.readString();

        this.mStatus = status;

        if (!(mStatus == STATUS_SUCCESSFUL)
                && !(mStatus == STATUS_ANDROID_FAILURE)
                && !(mStatus == STATUS_HAL_FAILURE)
                && !(mStatus == STATUS_HAL_INTERNAL_FAILURE)
                && !(mStatus == STATUS_INVALID_REQUEST)
                && !(mStatus == STATUS_UX_RESTRICTION_FAILURE)
                && !(mStatus == STATUS_OK_USER_ALREADY_IN_FOREGROUND)
                && !(mStatus == STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO)
                && !(mStatus == STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST)
                && !(mStatus == STATUS_NOT_SWITCHABLE)
                && !(mStatus == STATUS_NOT_LOGGED_IN)) {
            throw new java.lang.IllegalArgumentException(
                    "status was " + mStatus + " but must be one of: "
                            + "STATUS_SUCCESSFUL(" + STATUS_SUCCESSFUL + "), "
                            + "STATUS_ANDROID_FAILURE(" + STATUS_ANDROID_FAILURE + "), "
                            + "STATUS_HAL_FAILURE(" + STATUS_HAL_FAILURE + "), "
                            + "STATUS_HAL_INTERNAL_FAILURE(" + STATUS_HAL_INTERNAL_FAILURE + "), "
                            + "STATUS_INVALID_REQUEST(" + STATUS_INVALID_REQUEST + "), "
                            + "STATUS_UX_RESTRICTION_FAILURE(" + STATUS_UX_RESTRICTION_FAILURE + "), "
                            + "STATUS_OK_USER_ALREADY_IN_FOREGROUND(" + STATUS_OK_USER_ALREADY_IN_FOREGROUND + "), "
                            + "STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO(" + STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO + "), "
                            + "STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST(" + STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST + "), "
                            + "STATUS_NOT_SWITCHABLE(" + STATUS_NOT_SWITCHABLE + "), "
                            + "STATUS_NOT_LOGGED_IN(" + STATUS_NOT_LOGGED_IN + ")");
        }

        this.mAndroidFailureStatus = androidFailureStatus;
        this.mErrorMessage = errorMessage;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @android.annotation.NonNull Parcelable.Creator<UserSwitchResult> CREATOR
            = new Parcelable.Creator<UserSwitchResult>() {
        @Override
        public UserSwitchResult[] newArray(int size) {
            return new UserSwitchResult[size];
        }

        @Override
        public UserSwitchResult createFromParcel(@android.annotation.NonNull android.os.Parcel in) {
            return new UserSwitchResult(in);
        }
    };

    @DataClass.Generated(
            time = 1673057752058L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/services/Car/car-lib/src/android/car/user/UserSwitchResult.java",
            inputSignatures = "public static final @android.car.user.UserSwitchResult.Status @android.car.annotation.AddedInOrBefore int STATUS_SUCCESSFUL\npublic static final @android.car.user.UserSwitchResult.Status @android.car.annotation.AddedInOrBefore int STATUS_ANDROID_FAILURE\npublic static final @android.car.user.UserSwitchResult.Status @android.car.annotation.AddedInOrBefore int STATUS_HAL_FAILURE\npublic static final @android.car.user.UserSwitchResult.Status @android.car.annotation.AddedInOrBefore int STATUS_HAL_INTERNAL_FAILURE\npublic static final @android.car.user.UserSwitchResult.Status @android.car.annotation.AddedInOrBefore int STATUS_INVALID_REQUEST\npublic static final @android.car.user.UserSwitchResult.Status @android.car.annotation.AddedInOrBefore int STATUS_UX_RESTRICTION_FAILURE\npublic static final @android.car.user.UserSwitchResult.Status @android.car.annotation.AddedInOrBefore int STATUS_OK_USER_ALREADY_IN_FOREGROUND\npublic static final @android.car.user.UserSwitchResult.Status @android.car.annotation.AddedInOrBefore int STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO\npublic static final @android.car.user.UserSwitchResult.Status @android.car.annotation.AddedInOrBefore int STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST\npublic static final @android.car.user.UserSwitchResult.Status @android.car.annotation.AddedInOrBefore int STATUS_NOT_SWITCHABLE\npublic static final @android.car.user.UserSwitchResult.Status @android.car.annotation.AddedInOrBefore int STATUS_NOT_LOGGED_IN\nprivate final @android.car.user.UserSwitchResult.Status int mStatus\nprivate final @android.annotation.Nullable java.lang.Integer mAndroidFailureStatus\nprivate final @android.annotation.Nullable java.lang.String mErrorMessage\npublic @java.lang.Override @android.car.annotation.AddedInOrBefore boolean isSuccess()\npublic @java.lang.Override @com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport @android.car.annotation.AddedInOrBefore int describeContents()\nclass UserSwitchResult extends java.lang.Object implements [android.os.Parcelable, android.car.user.OperationResult]\n@com.android.car.internal.util.DataClass(genToString=true, genHiddenConstructor=true, genHiddenConstDefs=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
