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
 * Layer dependencies for single Vehicle Map Service layer.
 *
 * Dependencies are treated as <b>hard</b> dependencies, meaning that an offered layer will not be
 * reported as available until all dependent layers are also available.
 *
 * @hide
 */
@SystemApi
public final class VmsLayerDependency implements Parcelable {
    /**
     * Layer that has dependencies
     */
    private final @NonNull VmsLayer mLayer;

    /**
     * Layers that the given layer depends on
     */
    private @NonNull Set<VmsLayer> mDependencies;

    private void onConstructed() {
        mDependencies = Collections.unmodifiableSet(mDependencies);
    }

    private void parcelDependencies(Parcel dest) {
        ParcelHelper.writeArraySet(dest, new ArraySet<>(mDependencies));
    }

    @SuppressWarnings("unchecked")
    private Set<VmsLayer> unparcelDependencies(Parcel in) {
        return (Set<VmsLayer>) ParcelHelper.readArraySet(in, VmsLayer.class.getClassLoader());
    }

    /**
     * Creates a new VmsLayerDependency without dependencies.
     *
     * @param layer
     *   Layer that has no dependencies
     */
    public VmsLayerDependency(@NonNull VmsLayer layer) {
        this(layer, Collections.emptySet());
    }

    /**
     * Creates a new VmsLayerDependency.
     *
     * @param layer
     *   Layer that has dependencies
     * @param dependencies
     *   Layers that the given layer depends on
     */
    public VmsLayerDependency(
            @NonNull VmsLayer layer,
            @NonNull Set<VmsLayer> dependencies) {
        this.mLayer = layer;
        AnnotationValidations.validate(
                NonNull.class, null, mLayer);
        this.mDependencies = dependencies;
        AnnotationValidations.validate(
                NonNull.class, null, mDependencies);

        onConstructed();
    }

    /**
     * Layer that has dependencies
     */
    public @NonNull VmsLayer getLayer() {
        return mLayer;
    }

    /**
     * Layers that the given layer depends on
     */
    public @NonNull Set<VmsLayer> getDependencies() {
        return mDependencies;
    }

    @Override
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "VmsLayerDependency { " +
                "layer = " + mLayer + ", " +
                "dependencies = " + mDependencies +
        " }";
    }

    @Override
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(VmsLayerDependency other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        VmsLayerDependency that = (VmsLayerDependency) o;
        //noinspection PointlessBooleanExpression
        return true
                && Objects.equals(mLayer, that.mLayer)
                && Objects.equals(mDependencies, that.mDependencies);
    }

    @Override
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + Objects.hashCode(mLayer);
        _hash = 31 * _hash + Objects.hashCode(mDependencies);
        return _hash;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeTypedObject(mLayer, flags);
        parcelDependencies(dest);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    /* package-private */ VmsLayerDependency(@NonNull Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        VmsLayer layer = (VmsLayer) in.readTypedObject(VmsLayer.CREATOR);
        Set<VmsLayer> dependencies = unparcelDependencies(in);

        this.mLayer = layer;
        AnnotationValidations.validate(
                NonNull.class, null, mLayer);
        this.mDependencies = dependencies;
        AnnotationValidations.validate(
                NonNull.class, null, mDependencies);

        onConstructed();
    }

    public static final @NonNull Parcelable.Creator<VmsLayerDependency> CREATOR
            = new Parcelable.Creator<VmsLayerDependency>() {
        @Override
        public VmsLayerDependency[] newArray(int size) {
            return new VmsLayerDependency[size];
        }

        @Override
        public VmsLayerDependency createFromParcel(@NonNull Parcel in) {
            return new VmsLayerDependency(in);
        }
    };
}
