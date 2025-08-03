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

package com.android.server.remoteauth.connectivity;

import android.annotation.NonNull;
import android.annotation.TargetApi;
import android.companion.AssociationInfo;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Encapsulates the connection information for companion device manager connections.
 *
 * <p>Connection information captures the details of underlying connection such as connection id,
 * type of connection and peer device mac address.
 */
// TODO(b/295407748): Change to use @DataClass.
// TODO(b/296625303): Change to VANILLA_ICE_CREAM when AssociationInfo is available in V.
@TargetApi(Build.VERSION_CODES.TIRAMISU)
public final class CdmConnectionInfo extends ConnectionInfo<AssociationInfo> {
    @NonNull private final AssociationInfo mAssociationInfo;

    public CdmConnectionInfo(int connectionId, @NonNull AssociationInfo associationInfo) {
        super(connectionId);
        mAssociationInfo = associationInfo;
    }

    private CdmConnectionInfo(@NonNull Parcel in) {
        super(in);
        mAssociationInfo = in.readTypedObject(AssociationInfo.CREATOR);
    }

    /** Used to read CdmConnectionInfo from a Parcel */
    @NonNull
    public static final Parcelable.Creator<CdmConnectionInfo> CREATOR =
            new Parcelable.Creator<CdmConnectionInfo>() {
                public CdmConnectionInfo createFromParcel(@NonNull Parcel in) {
                    return new CdmConnectionInfo(in);
                }

                public CdmConnectionInfo[] newArray(int size) {
                    return new CdmConnectionInfo[size];
                }
            };

    /** No special parcel contents. */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Flatten this CdmConnectionInfo in to a Parcel.
     *
     * @param out The Parcel in which the object should be written.
     * @param flags Additional flags about how the object should be written.
     */
    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        super.writeToParcel(out, flags);
        out.writeTypedObject(mAssociationInfo, 0);
    }

    /** Returns a string representation of ConnectionInfo. */
    @Override
    public String toString() {
        return super.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof CdmConnectionInfo)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        CdmConnectionInfo other = (CdmConnectionInfo) o;
        return super.equals(o) && mAssociationInfo.equals(other.getConnectionParams());
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAssociationInfo);
    }

    @Override
    public AssociationInfo getConnectionParams() {
        return mAssociationInfo;
    }
}
