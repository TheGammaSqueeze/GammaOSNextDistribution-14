/**
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.qti.extphone;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class QosParametersResult implements Parcelable {

    private static final String TAG = "QosParametersResult";

    public static final int QOS_TYPE_EPS = Qos.QOS_TYPE_EPS;
    public static final int QOS_TYPE_NR = Qos.QOS_TYPE_NR;

    private final Qos mDefaultQos;
    private final List<QosBearerSession> mQosBearerSessions;

    public QosParametersResult() {
        mDefaultQos = null;
        mQosBearerSessions = new ArrayList<>();
    }


    public QosParametersResult(/* @Nullable */ Qos defaultQos,
                               /* @Nullable */  List<QosBearerSession> qosBearerSessions) {
        mDefaultQos = defaultQos;
        mQosBearerSessions = (qosBearerSessions == null)
                ? new ArrayList<>() : new ArrayList<>(qosBearerSessions);
    }

    public QosParametersResult(Parcel source) {
        mDefaultQos = source.readParcelable(Qos.class.getClassLoader());
        mQosBearerSessions = new ArrayList<>();
        source.readList(mQosBearerSessions, QosBearerSession.class.getClassLoader());
    }

    /**
     * @return default QOS of the data connection received from the network
     *
     * @hide
     * @Nullable
     */
    public Qos getDefaultQos() {
        return mDefaultQos;
    }

    /**
     * @return All the dedicated bearer QOS sessions of the data connection received from the
     * network.
     *
     * @hide
     * @NonNull
     */
    public List<QosBearerSession> getQosBearerSessions() {
        return mQosBearerSessions;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("QosParametersResult: {")
           .append(" defaultQos=").append(mDefaultQos)
           .append(" qosBearerSessions=").append(mQosBearerSessions)
           .append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(/* @Nullable */ Object o) {
        if (this == o) return true;

        if (!(o instanceof QosParametersResult)) {
            return false;
        }

        QosParametersResult other = (QosParametersResult) o;

        final boolean isQosSame = (mDefaultQos == null || other.mDefaultQos == null)
                ? mDefaultQos == other.mDefaultQos
                : mDefaultQos.equals(other.mDefaultQos);

        final boolean isQosBearerSessionsSame =
                (mQosBearerSessions == null || other.mQosBearerSessions == null)
                ? mQosBearerSessions == other.mQosBearerSessions
                : mQosBearerSessions.size() == other.mQosBearerSessions.size()
                && mQosBearerSessions.containsAll(other.mQosBearerSessions);

        return isQosSame && isQosBearerSessionsSame;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDefaultQos, mQosBearerSessions);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (mDefaultQos != null) {
            if (mDefaultQos.getType() == Qos.QOS_TYPE_EPS) {
                dest.writeParcelable((EpsQos) mDefaultQos, flags);
            } else {
                dest.writeParcelable((NrQos) mDefaultQos, flags);
            }
        } else {
            dest.writeParcelable(null, flags);
        }
        dest.writeList(mQosBearerSessions);
    }

    // @NonNull
    public static final Parcelable.Creator<QosParametersResult> CREATOR =
            new Parcelable.Creator<QosParametersResult>() {
                @Override
                public QosParametersResult createFromParcel(Parcel source) {
                    return new QosParametersResult(source);
                }

                @Override
                public QosParametersResult[] newArray(int size) {
                    return new QosParametersResult[size];
                }
            };

    /**
     * Provides a convenient way to set the fields of a {@link QosParametersResult} when
     * creating a new instance.
     *
     * <p>The example below shows how you might create a new {@code QosParametersResult}:
     *
     * <pre><code>
     *
     * QosParametersResult response = new QosParametersResult.Builder()
     *     .setAddresses(Arrays.asList("192.168.1.2"))
     *     .setProtocolType(ApnSetting.PROTOCOL_IPV4V6)
     *     .build();
     * </code></pre>
     */
    public static final class Builder {
        private Qos mDefaultQos;
        private List<QosBearerSession> mQosBearerSessions = new ArrayList<>();

        /**
         * Default constructor for Builder.
         */
        public Builder() {
        }

        /**
         * Set the default QOS for this data connection.
         *
         * @param defaultQos QOS (Quality Of Service) received from network.
         *
         * @return The same instance of the builder.
         *
         * @hide
         * @NonNull
         */
        public Builder setDefaultQos(/* Nullable */ Qos defaultQos) {
            mDefaultQos = defaultQos;
            return this;
        }

        /**
         * Set the dedicated bearer QOS sessions for this data connection.
         *
         * @param qosBearerSessions Dedicated bearer QOS (Quality Of Service) sessions received
         * from network.
         *
         * @return The same instance of the builder.
         *
         * @hide
         * @NonNull
         */
        public Builder setQosBearerSessions(
                /* @NonNull */ List<QosBearerSession> qosBearerSessions) {
            mQosBearerSessions = qosBearerSessions;
            return this;
        }

       /**
         * Build the QosParametersResult.
         *
         * @return the QosParametersResult object.
         * @NonNull
         */
        public QosParametersResult build() {
            return new QosParametersResult(mDefaultQos, mQosBearerSessions);
        }
    }
}