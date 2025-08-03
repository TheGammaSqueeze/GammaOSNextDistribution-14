/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.queryable.queries;

import android.app.Notification;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;

import com.android.queryable.Queryable;
import com.android.queryable.QueryableBaseWithMatch;

import java.io.Serializable;
import java.util.Objects;

/**
 * Implementation of {@link NotificationQuery}.
 *
 * @param <E> The root of the query
 */
public final class NotificationQueryHelper<E extends Queryable> implements NotificationQuery<E>,
        Serializable {

    private static final long serialVersionUID = 1;

    private final transient E mQuery;
    private final StringQueryHelper<E> mChannelId;
    private final BundleQueryHelper<E> mExtras;
    private final StringQueryHelper<E> mTag;

    public static final class NotificationQueryBase extends
            QueryableBaseWithMatch<StatusBarNotification, NotificationQueryHelper<NotificationQueryBase>> {
        NotificationQueryBase() {
            super();
            setQuery(new NotificationQueryHelper<>(this));
        }

        NotificationQueryBase(Parcel in) {
            super(in);
        }

        public static final Parcelable.Creator<NotificationQueryHelper.NotificationQueryBase> CREATOR =
                new Parcelable.Creator<>() {
                    public NotificationQueryHelper.NotificationQueryBase createFromParcel(
                            Parcel in) {
                        return new NotificationQueryHelper.NotificationQueryBase(in);
                    }

                    public NotificationQueryHelper.NotificationQueryBase[] newArray(int size) {
                        return new NotificationQueryHelper.NotificationQueryBase[size];
                    }
                };
    }

    public NotificationQueryHelper(E query) {
        mQuery = query;
        mChannelId = new StringQueryHelper<>(query);
        mExtras = new BundleQueryHelper<>(query);
        mTag = new StringQueryHelper<>(query);
    }

    private NotificationQueryHelper(Parcel in) {
        mQuery = null;
        mChannelId = in.readParcelable(NotificationQueryHelper.class.getClassLoader());
        mExtras = in.readParcelable(NotificationQueryHelper.class.getClassLoader());
        mTag = in.readParcelable(NotificationQueryHelper.class.getClassLoader());
    }

    @Override
    public StringQuery<E> channelId() {
        return mChannelId;
    }

    @Override
    public BundleQuery<E> extras() {
        return mExtras;
    }

    @Override
    public StringQuery<E> tag() {
        return mTag;
    }

    @Override
    public boolean isEmptyQuery() {
        return Queryable.isEmptyQuery(mChannelId) && Queryable.isEmptyQuery(mExtras) && Queryable.isEmptyQuery(mTag);
    }

    /** {@code true} if all filters are met by {@code value}. */
    @Override
    public boolean matches(StatusBarNotification value) {
        // TODO: Consider adding another .notification() level here and make this a statusbarnotificationquery?
        if (!mChannelId.matches(value.getNotification().getChannelId())) {
            return false;
        }

        if (!mExtras.matches(value.getNotification().extras)) {
            return false;
        }

        if (!mTag.matches(value.getTag())) {
            return false;
        }

        return true;
    }

    /** See {@link #matches(StatusBarNotification)}. */
    public static boolean matches(NotificationQueryHelper<?> query, StatusBarNotification value) {
        return query.matches(value);
    }

    @Override
    public String describeQuery(String fieldName) {
        return Queryable.joinQueryStrings(
                mChannelId.describeQuery(fieldName + ".channelId"),
                mExtras.describeQuery(fieldName + ".extras"),
                mTag.describeQuery(fieldName + ".tag")
        );
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(mChannelId, flags);
        out.writeParcelable(mExtras, flags);
        out.writeParcelable(mTag, flags);
    }

    public static final Parcelable.Creator<NotificationQueryHelper> CREATOR =
            new Parcelable.Creator<NotificationQueryHelper>() {
                public NotificationQueryHelper createFromParcel(Parcel in) {
                    return new NotificationQueryHelper(in);
                }

                public NotificationQueryHelper[] newArray(int size) {
                    return new NotificationQueryHelper[size];
                }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NotificationQueryHelper)) return false;
        NotificationQueryHelper<?> that = (NotificationQueryHelper<?>) o;
        return Objects.equals(mChannelId, that.mChannelId)
                && Objects.equals(mExtras, that.mExtras)
                && Objects.equals(mTag, that.mTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mChannelId, mExtras, mTag);
    }
}
