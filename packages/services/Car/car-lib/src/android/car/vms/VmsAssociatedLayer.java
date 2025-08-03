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
import java.util.Objects;
import java.util.Set;

/**
 * A Vehicle Map Service layer with a list of publisher IDs it is associated with.
 *
 * @hide
 */
@SystemApi
public final class VmsAssociatedLayer implements Parcelable {
    /**
     * Layer being offered
     */
    private final @NonNull VmsLayer mVmsLayer;

    /**
     * IDs of providers that publish the layer
     */
    private @NonNull Set<Integer> mProviderIds;

    private void onConstructed() {
        mProviderIds = Collections.unmodifiableSet(mProviderIds);
    }

    private void parcelProviderIds(Parcel dest) {
        ParcelHelper.writeArraySet(dest, new ArraySet<>(mProviderIds));
    }

    @SuppressWarnings("unchecked")
    private Set<Integer> unparcelProviderIds(Parcel in) {
        return (Set<Integer>) ParcelHelper.readArraySet(in, Integer.class.getClassLoader());
    }

    /**
     * IDs of publishers that publish the layer
     *
     * @deprecated Use {@link #getProviderIds()} instead
     */
    @Deprecated
    @NonNull
    public Set<Integer> getPublisherIds() {
        return mProviderIds;
    }

    /**
     * Creates a new VmsAssociatedLayer.
     *
     * @param vmsLayer
     *   Layer being offered
     * @param providerIds
     *   IDs of providers that publish the layer
     */
    public VmsAssociatedLayer(
            @NonNull VmsLayer vmsLayer,
            @NonNull Set<Integer> providerIds) {
        this.mVmsLayer = vmsLayer;
        AnnotationValidations.validate(
                NonNull.class, null, mVmsLayer);
        this.mProviderIds = providerIds;
        AnnotationValidations.validate(
                NonNull.class, null, mProviderIds);

        onConstructed();
    }

    /**
     * Layer being offered
     */
    public @NonNull VmsLayer getVmsLayer() {
        return mVmsLayer;
    }

    /**
     * IDs of providers that publish the layer
     */
    public @NonNull Set<Integer> getProviderIds() {
        return mProviderIds;
    }

    @Override
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "VmsAssociatedLayer { " +
                "vmsLayer = " + mVmsLayer + ", " +
                "providerIds = " + mProviderIds +
        " }";
    }

    @Override
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(VmsAssociatedLayer other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        VmsAssociatedLayer that = (VmsAssociatedLayer) o;
        //noinspection PointlessBooleanExpression
        return true
                && Objects.equals(mVmsLayer, that.mVmsLayer)
                && Objects.equals(mProviderIds, that.mProviderIds);
    }

    @Override
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + Objects.hashCode(mVmsLayer);
        _hash = 31 * _hash + Objects.hashCode(mProviderIds);
        return _hash;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeTypedObject(mVmsLayer, flags);
        parcelProviderIds(dest);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    /* package-private */ VmsAssociatedLayer(@NonNull Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        VmsLayer vmsLayer = (VmsLayer) in.readTypedObject(VmsLayer.CREATOR);
        Set<Integer> providerIds = unparcelProviderIds(in);

        this.mVmsLayer = vmsLayer;
        AnnotationValidations.validate(
                NonNull.class, null, mVmsLayer);
        this.mProviderIds = providerIds;
        AnnotationValidations.validate(
                NonNull.class, null, mProviderIds);

        onConstructed();
    }

    public static final @NonNull Parcelable.Creator<VmsAssociatedLayer> CREATOR
            = new Parcelable.Creator<VmsAssociatedLayer>() {
        @Override
        public VmsAssociatedLayer[] newArray(int size) {
            return new VmsAssociatedLayer[size];
        }

        @Override
        public VmsAssociatedLayer createFromParcel(@NonNull Parcel in) {
            return new VmsAssociatedLayer(in);
        }
    };
}
