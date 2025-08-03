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

package android.car.vms;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.car.builtin.os.ParcelHelper;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.ArraySet;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.AnnotationValidations;

import java.util.Collections;
import java.util.Set;

/**
 * A Vehicle Map Service layer offering for a single publisher.
 *
 * Contains all layers the publisher can offer, and the layers that offering depends on.
 *
 * A layer will not be advertised to subscribers unless all of its dependencies are met.
 *
 * @deprecated Use {@link VmsClient#setProviderOfferings(int, Set)} instead
 *
 * @hide
 */
@Deprecated
@SystemApi
public final class VmsLayersOffering implements Parcelable {
    /**
     * Layers and dependencies in the offering
     */
    private @NonNull Set<VmsLayerDependency> mDependencies;

    /**
     * ID of the publisher making the offering
     */
    private final int mPublisherId;

    private void onConstructed() {
        mDependencies = Collections.unmodifiableSet(mDependencies);
    }

    private void parcelDependencies(Parcel dest) {
        ParcelHelper.writeArraySet(dest, new ArraySet<>(mDependencies));
    }

    @SuppressWarnings("unchecked")
    private Set<VmsLayerDependency> unparcelDependencies(Parcel in) {
        return (Set<VmsLayerDependency>) ParcelHelper.readArraySet(in,
                VmsLayerDependency.class.getClassLoader());
    }

    /**
     * Creates a new VmsLayersOffering.
     *
     * @param dependencies
     *   Layers and dependencies in the offering
     * @param publisherId
     *   ID of the publisher making the offering
     */
    public VmsLayersOffering(
            @NonNull Set<VmsLayerDependency> dependencies,
            int publisherId) {
        this.mDependencies = dependencies;
        AnnotationValidations.validate(
                NonNull.class, null, mDependencies);
        this.mPublisherId = publisherId;

        onConstructed();
    }

    /**
     * Layers and dependencies in the offering
     */
    public @NonNull Set<VmsLayerDependency> getDependencies() {
        return mDependencies;
    }

    /**
     * ID of the publisher making the offering
     */
    public int getPublisherId() {
        return mPublisherId;
    }

    @Override
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "VmsLayersOffering { " +
                "dependencies = " + mDependencies + ", " +
                "publisherId = " + mPublisherId +
        " }";
    }

    @Override
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(VmsLayersOffering other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        VmsLayersOffering that = (VmsLayersOffering) o;
        //noinspection PointlessBooleanExpression
        return true
                && java.util.Objects.equals(mDependencies, that.mDependencies)
                && mPublisherId == that.mPublisherId;
    }

    @Override
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mDependencies);
        _hash = 31 * _hash + mPublisherId;
        return _hash;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        parcelDependencies(dest);
        dest.writeInt(mPublisherId);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    /* package-private */ VmsLayersOffering(@NonNull Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        Set<VmsLayerDependency> dependencies = unparcelDependencies(in);
        int publisherId = in.readInt();

        this.mDependencies = dependencies;
        AnnotationValidations.validate(
                NonNull.class, null, mDependencies);
        this.mPublisherId = publisherId;

        onConstructed();
    }

    public static final @NonNull Parcelable.Creator<VmsLayersOffering> CREATOR
            = new Parcelable.Creator<VmsLayersOffering>() {
        @Override
        public VmsLayersOffering[] newArray(int size) {
            return new VmsLayersOffering[size];
        }

        @Override
        public VmsLayersOffering createFromParcel(@NonNull Parcel in) {
            return new VmsLayersOffering(in);
        }
    };
}
