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

package com.android.adservices.data.adselection;

import android.adservices.common.AdTechIdentifier;
import android.annotation.Nullable;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

import java.util.Objects;

/**
 * A pair of (buyer, package name) that gives the buyer permission to run app install filters on the
 * package.
 *
 * @hide
 */
@Entity(
        tableName = "app_install",
        primaryKeys = {"buyer", "package_name"})
public final class DBAppInstallPermissions {
    @ColumnInfo(name = "buyer")
    @NonNull
    private final AdTechIdentifier mBuyer;

    @ColumnInfo(name = "package_name", index = true)
    @NonNull
    private final String mPackageName;

    public DBAppInstallPermissions(@NonNull AdTechIdentifier buyer, @NonNull String packageName) {
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(packageName);
        mBuyer = buyer;
        mPackageName = packageName;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o instanceof DBAppInstallPermissions) {
            DBAppInstallPermissions otherAppInstall = (DBAppInstallPermissions) o;

            return mBuyer.equals(otherAppInstall.mBuyer)
                    && mPackageName.equals(otherAppInstall.mPackageName);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mBuyer, mPackageName);
    }

    @Override
    public String toString() {
        return "DBAppInstallPermissions{"
                + "mBuyer="
                + mBuyer
                + ", mPackageName='"
                + mPackageName
                + "'}";
    }

    /** @return The buyer who can filter on the package */
    @NonNull
    public AdTechIdentifier getBuyer() {
        return mBuyer;
    }

    /** @return The name of the package */
    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    /** Builder for {@link DBAppInstallPermissions} object. */
    public static final class Builder {
        @Nullable private AdTechIdentifier mBuyer;

        @Nullable private String mPackageName;

        public Builder() {}

        /** Sets the buyer */
        @NonNull
        public DBAppInstallPermissions.Builder setBuyer(@NonNull AdTechIdentifier buyer) {
            Objects.requireNonNull(buyer);
            mBuyer = buyer;
            return this;
        }

        /** Sets the package name */
        @NonNull
        public DBAppInstallPermissions.Builder setPackageName(@NonNull String packageName) {
            Objects.requireNonNull(packageName);
            mPackageName = packageName;
            return this;
        }

        /**
         * Builds an {@link DBAppInstallPermissions} instance.
         *
         * @throws NullPointerException if any non-null params are null.
         */
        @NonNull
        public DBAppInstallPermissions build() {
            return new DBAppInstallPermissions(mBuyer, mPackageName);
        }
    }
}
