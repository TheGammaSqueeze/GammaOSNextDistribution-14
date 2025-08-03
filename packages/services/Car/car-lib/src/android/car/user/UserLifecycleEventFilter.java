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

package android.car.user;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DEBUGGING_CODE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.ActivityManager;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;
import android.util.ArraySet;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.common.CommonConstants;
import com.android.car.internal.util.ArrayUtils;
import com.android.car.internal.util.DataClass;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/**
 * Filter for user lifecycle event receivers to selectively receive events.
 *
 * @hide
 */
@DataClass(
        genParcelable = true,
        genGetters = false,
        genConstructor = false,
        genEqualsHashCode = true)
@SystemApi
public final class UserLifecycleEventFilter implements Parcelable {

    private static final int USER_CURRENT = UserHandle.CURRENT.getIdentifier();

    private final @Nullable int[] mEventTypes;
    private final @Nullable int[] mUserIds;

    // TODO(b/216850516): Manually implemented these getters, as codegen does not auto-generate
    //         the @VisibleForTesting annotation.
    /** @hide */
    @VisibleForTesting
    public @Nullable int[] getEventTypes() {
        return mEventTypes;
    }

    /** @hide */
    @VisibleForTesting
    public @Nullable int[] getUserIds() {
        return mUserIds;
    }

    /**
     * Checks if the {@code event} passes this filter.
     *
     * @param event user lifecycle event to check.
     * @return {@code true} if the event passes this filter.
     */
    public boolean apply(@NonNull UserLifecycleEvent event) {
        Objects.requireNonNull(event, "event cannot be null");

        return matchUserId(event) && matchEventType(event);
    }

    private boolean matchUserId(UserLifecycleEvent event) {
        // Filter by the user id.
        if (mUserIds == null) {
            return true;
        }
        for (int userId : mUserIds) {
            if (userId == USER_CURRENT) {
                userId = ActivityManager.getCurrentUser();
            }
            if (userId == event.getUserId() || userId == event.getPreviousUserId()) {
                return true;
            }
        }
        return false;
    }

    private boolean matchEventType(UserLifecycleEvent event) {
        // Filter by the event type.
        if (mEventTypes == null) {
            return true;
        }
        for (int eventType : mEventTypes) {
            if (eventType == event.getEventType()) {
                return true;
            }
        }
        return false;
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DEBUGGING_CODE)
    public String toString() {
        StringBuilder builder = new StringBuilder("UserLifecycleEventFilter{eventTypes=");
        if (mEventTypes == null) {
            builder.append("ANY");
        } else {
            builder.append('[');
            for (int eventType : mEventTypes) {
                builder.append(CarUserManager.lifecycleEventTypeToString(eventType));
                builder.append(',');
            }
            builder.setCharAt(builder.length() - 1, ']');
        }
        builder.append(",userIds=");
        if (mUserIds == null) {
            builder.append("ANY");
        } else {
            builder.append('[');
            for (int userId : mUserIds) {
                if (userId == USER_CURRENT) {
                    builder.append("CURRENT");
                } else {
                    builder.append(userId);
                }
                builder.append(',');
            }
            builder.setCharAt(builder.length() - 1, ']');
        }

        return builder.append('}').toString();
    }

    /**
     * Builder for {@link UserLifecycleEventFilter}.
     *
     */
    public static final class Builder {
        private final ArraySet<Integer> mEventTypes = new ArraySet<>();
        private final ArraySet<Integer> mUserIds = new ArraySet<>();

        /** Adds an event type that this filter passes. */
        @SuppressWarnings("[MissingGetterMatchingBuilder]")
        @NonNull
        public Builder addEventType(@CommonConstants.UserLifecycleEventType int eventType) {
            mEventTypes.add(eventType);
            return this;
        }

        /**
         * Adds a user that this filter passes.
         * *
         * @param userHandle a user handle. {@code UserHandle.CURRENT} is supported but no other
         *                   special value is supported. When {@code UserHandle.CURRENT} is used,
         *                   the filter will use the current user at the time of the event, not the
         *                   current user at the time of the filter creation.
         * @throws IllegalArgumentException if the specified userHandle is not supported.
         */
        @SuppressWarnings({"[MissingGetterMatchingBuilder]", "[UserHandleName]"})
        @NonNull
        public Builder addUser(@NonNull UserHandle userHandle) {
            int userId = userHandle.getIdentifier();
            if (userId < 0 && userId != USER_CURRENT) {
                throw new IllegalArgumentException("Unsupported user handle: " + userHandle);
            }
            mUserIds.add(userHandle.getIdentifier());
            return this;
        }

        /** Builds and returns a {@link UserLifecycleEventFilter}. */
        @NonNull
        public UserLifecycleEventFilter build() {
            if (mEventTypes.isEmpty() && mUserIds.isEmpty()) {
                throw new IllegalStateException("Cannot build an empty filter.");
            }
            return new UserLifecycleEventFilter(this);
        }
    }

    private UserLifecycleEventFilter(Builder builder) {
        // Keep the arrays in sorted order so that equals() and hashCode() work as intended.
        mEventTypes = toSortedArray(builder.mEventTypes);
        mUserIds = toSortedArray(builder.mUserIds);
    }

    private @Nullable int[] toSortedArray(ArraySet<Integer> arraySet) {
        if (arraySet.isEmpty()) {
            return null;
        }
        int[] result = ArrayUtils.convertToIntArray(new ArrayList<>(arraySet));
        Arrays.sort(result);
        return result;
    }



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car/user/UserLifecycleEventFilter.java
    // Added AddedInOrBefore or ApiRequirement Annotation manually
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @Override
    @DataClass.Generated.Member
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(UserLifecycleEventFilter other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        UserLifecycleEventFilter that = (UserLifecycleEventFilter) o;
        //noinspection PointlessBooleanExpression
        return true
                && Arrays.equals(mEventTypes, that.mEventTypes)
                && Arrays.equals(mUserIds, that.mUserIds);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + Arrays.hashCode(mEventTypes);
        _hash = 31 * _hash + Arrays.hashCode(mUserIds);
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mEventTypes != null) flg |= 0x1;
        if (mUserIds != null) flg |= 0x2;
        dest.writeByte(flg);
        if (mEventTypes != null) dest.writeIntArray(mEventTypes);
        if (mUserIds != null) dest.writeIntArray(mUserIds);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ UserLifecycleEventFilter(@NonNull Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        int[] eventTypes = (flg & 0x1) == 0 ? null : in.createIntArray();
        int[] userIds = (flg & 0x2) == 0 ? null : in.createIntArray();

        this.mEventTypes = eventTypes;
        this.mUserIds = userIds;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<UserLifecycleEventFilter> CREATOR
            = new Parcelable.Creator<UserLifecycleEventFilter>() {
        @Override
        public UserLifecycleEventFilter[] newArray(int size) {
            return new UserLifecycleEventFilter[size];
        }

        @Override
        public UserLifecycleEventFilter createFromParcel(@NonNull Parcel in) {
            return new UserLifecycleEventFilter(in);
        }
    };

    @DataClass.Generated(
            time = 1643409624655L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/services/Car/car-lib/src/android/car/user/UserLifecycleEventFilter.java",
            inputSignatures = "private static final  int USER_CURRENT\nprivate final @android.annotation.Nullable int[] mEventTypes\nprivate final @android.annotation.Nullable int[] mUserIds\npublic @com.android.internal.annotations.VisibleForTesting @android.annotation.Nullable int[] getEventTypes()\npublic @com.android.internal.annotations.VisibleForTesting @android.annotation.Nullable int[] getUserIds()\npublic  boolean apply(android.car.user.CarUserManager.UserLifecycleEvent)\nprivate  boolean matchUserId(android.car.user.CarUserManager.UserLifecycleEvent)\nprivate  boolean matchEventType(android.car.user.CarUserManager.UserLifecycleEvent)\npublic @java.lang.Override @com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport java.lang.String toString()\nprivate @android.annotation.Nullable int[] toSortedArray(android.util.ArraySet<java.lang.Integer>)\nclass UserLifecycleEventFilter extends java.lang.Object implements [android.os.Parcelable]\nprivate final  android.util.ArraySet<java.lang.Integer> mEventTypes\nprivate final  android.util.ArraySet<java.lang.Integer> mUserIds\npublic  android.car.user.UserLifecycleEventFilter.Builder addEventType(int)\npublic  android.car.user.UserLifecycleEventFilter.Builder addUser(android.os.UserHandle)\npublic  android.car.user.UserLifecycleEventFilter build()\nclass Builder extends java.lang.Object implements []\n@com.android.car.internal.util.DataClass(genParcelable=true, genGetters=false, genConstructor=false, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
