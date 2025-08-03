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
 * Subscription state of Vehicle Map Service layers.
 *
 * The subscription state is used by publishers to determine which layers to publish data for, as
 * any data published to a layer without subscribers will be dropped by the Vehicle Map Service.
 *
 * Sequence numbers are used to indicate the succession of subscription states, and increase
 * monotonically with each change in subscriptions. They must be used by clients to ignore states
 * that are received out-of-order.
 *
 * @hide
 */
@SystemApi
public final class VmsSubscriptionState implements Parcelable {
    /**
     * Sequence number of the subscription state
     */
    private final int mSequenceNumber;

    /**
     * Layers with subscriptions to all publishers
     */
    private @NonNull Set<VmsLayer> mLayers;

    /**
     * Layers with subscriptions to a subset of publishers
     */
    private @NonNull Set<VmsAssociatedLayer> mAssociatedLayers;

    private void onConstructed() {
        mLayers = Collections.unmodifiableSet(mLayers);
        mAssociatedLayers = Collections.unmodifiableSet(mAssociatedLayers);
    }

    private void parcelLayers(Parcel dest) {
        ParcelHelper.writeArraySet(dest, new ArraySet<>(mLayers));
    }

    @SuppressWarnings("unchecked")
    private Set<VmsLayer> unparcelLayers(Parcel in) {
        return (Set<VmsLayer>) ParcelHelper.readArraySet(in, VmsLayer.class.getClassLoader());
    }

    private void parcelAssociatedLayers(Parcel dest) {
        ParcelHelper.writeArraySet(dest, new ArraySet<>(mAssociatedLayers));
    }

    @SuppressWarnings("unchecked")
    private Set<VmsAssociatedLayer> unparcelAssociatedLayers(Parcel in) {
        return (Set<VmsAssociatedLayer>) ParcelHelper.readArraySet(in,
                VmsAssociatedLayer.class.getClassLoader());
    }

    /**
     * Creates a new VmsSubscriptionState.
     *
     * @param sequenceNumber
     *   Sequence number of the subscription state
     * @param layers
     *   Layers with subscriptions to all publishers
     * @param associatedLayers
     *   Layers with subscriptions to a subset of publishers
     */
    public VmsSubscriptionState(
            int sequenceNumber,
            @NonNull Set<VmsLayer> layers,
            @NonNull Set<VmsAssociatedLayer> associatedLayers) {
        this.mSequenceNumber = sequenceNumber;
        this.mLayers = layers;
        AnnotationValidations.validate(
                NonNull.class, null, mLayers);
        this.mAssociatedLayers = associatedLayers;
        AnnotationValidations.validate(
                NonNull.class, null, mAssociatedLayers);

        onConstructed();
    }

    /**
     * Sequence number of the subscription state
     */
    public int getSequenceNumber() {
        return mSequenceNumber;
    }

    /**
     * Layers with subscriptions to all publishers
     */
    public @NonNull Set<VmsLayer> getLayers() {
        return mLayers;
    }

    /**
     * Layers with subscriptions to a subset of publishers
     */
    public @NonNull Set<VmsAssociatedLayer> getAssociatedLayers() {
        return mAssociatedLayers;
    }

    @Override
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "VmsSubscriptionState { " +
                "sequenceNumber = " + mSequenceNumber + ", " +
                "layers = " + mLayers + ", " +
                "associatedLayers = " + mAssociatedLayers +
        " }";
    }

    @Override
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(VmsSubscriptionState other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        VmsSubscriptionState that = (VmsSubscriptionState) o;
        //noinspection PointlessBooleanExpression
        return true
                && mSequenceNumber == that.mSequenceNumber
                && Objects.equals(mLayers, that.mLayers)
                && Objects.equals(mAssociatedLayers, that.mAssociatedLayers);
    }

    @Override
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + mSequenceNumber;
        _hash = 31 * _hash + Objects.hashCode(mLayers);
        _hash = 31 * _hash + Objects.hashCode(mAssociatedLayers);
        return _hash;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeInt(mSequenceNumber);
        parcelLayers(dest);
        parcelAssociatedLayers(dest);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    /* package-private */ VmsSubscriptionState(@NonNull Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        int sequenceNumber = in.readInt();
        Set<VmsLayer> layers = unparcelLayers(in);
        Set<VmsAssociatedLayer> associatedLayers = unparcelAssociatedLayers(in);

        this.mSequenceNumber = sequenceNumber;
        this.mLayers = layers;
        AnnotationValidations.validate(
                NonNull.class, null, mLayers);
        this.mAssociatedLayers = associatedLayers;
        AnnotationValidations.validate(
                NonNull.class, null, mAssociatedLayers);

        onConstructed();
    }

    public static final @NonNull Parcelable.Creator<VmsSubscriptionState> CREATOR
            = new Parcelable.Creator<VmsSubscriptionState>() {
        @Override
        public VmsSubscriptionState[] newArray(int size) {
            return new VmsSubscriptionState[size];
        }

        @Override
        public VmsSubscriptionState createFromParcel(@NonNull Parcel in) {
            return new VmsSubscriptionState(in);
        }
    };
}
