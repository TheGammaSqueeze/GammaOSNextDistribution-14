/**
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

package android.telephony.imsmedia;

import android.annotation.IntDef;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * The class represents EVS (Enhanced Voice Services) codec parameters.
 *
 * @hide
 */
public final class EvsParams implements Parcelable {
    /** EVS band not specified */
    public static final int EVS_BAND_NONE = 0;
    /** EVS narrow band */
    public static final int EVS_NARROW_BAND = 1 << 0;
    /** EVS wide band */
    public static final int EVS_WIDE_BAND = 1 << 1;
    /** EVS super wide band */
    public static final int EVS_SUPER_WIDE_BAND = 1 << 2;
    /** EVS full band */
    public static final int EVS_FULL_BAND = 1 << 3;

    /** @hide */
    @IntDef(
        value = {
           EVS_BAND_NONE,
           EVS_NARROW_BAND,
           EVS_WIDE_BAND,
           EVS_SUPER_WIDE_BAND,
           EVS_FULL_BAND,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface EvsBandwidth {}

    /** 6.6 kbps for EVS AMR-WB IO */
    public static final int EVS_MODE_0 = 1 << 0;
    /** 8.855 kbps for AMR-WB IO */
    public static final int EVS_MODE_1 = 1 << 1;
    /** 12.65 kbps for AMR-WB IO */
    public static final int EVS_MODE_2 = 1 << 2;
    /** 14.25 kbps for AMR-WB IO */
    public static final int EVS_MODE_3 = 1 << 3;
    /** 15.85 kbps for AMR-WB IO */
    public static final int EVS_MODE_4 = 1 << 4;
    /** 18.25 kbps for AMR-WB IO */
    public static final int EVS_MODE_5 = 1 << 5;
    /** 19.85 kbps for AMR-WB IO */
    public static final int EVS_MODE_6 = 1 << 6;
    /** 23.05 kbps for AMR-WB IO */
    public static final int EVS_MODE_7 = 1 << 7;
    /** 23.85 kbps for AMR-WB IO */
    public static final int EVS_MODE_8 = 1 << 8;
    /** 5.9 kbps for EVS primary */
    public static final int EVS_MODE_9 = 1 << 9;
    /** 7.2 kbps for EVS primary */
    public static final int EVS_MODE_10 = 1 << 10;
    /** 8.0 kbps for EVS primary */
    public static final int EVS_MODE_11 = 1 << 11;
    /** 9.6 kbps for EVS primary */
    public static final int EVS_MODE_12 = 1 << 12;
    /** 13.2 kbps for EVS primary */
    public static final int EVS_MODE_13 = 1 << 13;
    /** 16.4 kbps for EVS primary */
    public static final int EVS_MODE_14 = 1 << 14;
    /** 24.4 kbps for EVS primary */
    public static final int EVS_MODE_15 = 1 << 15;
    /** 32.0 kbps for EVS primary */
    public static final int EVS_MODE_16 = 1 << 16;
    /** 48.0 kbps for EVS primary */
    public static final int EVS_MODE_17 = 1 << 17;
    /** 64.0 kbps for EVS primary */
    public static final int EVS_MODE_18 = 1 << 18;
    /** 96.0 kbps for EVS primary */
    public static final int EVS_MODE_19 = 1 << 19;
    /** 128.0 kbps for EVS primary */
    public static final int EVS_MODE_20 = 1 << 20;

    /** @hide */
    @IntDef(
        value = {
           EVS_MODE_0,
           EVS_MODE_1,
           EVS_MODE_2,
           EVS_MODE_3,
           EVS_MODE_4,
           EVS_MODE_5,
           EVS_MODE_6,
           EVS_MODE_7,
           EVS_MODE_8,
           EVS_MODE_9,
           EVS_MODE_10,
           EVS_MODE_11,
           EVS_MODE_12,
           EVS_MODE_13,
           EVS_MODE_14,
           EVS_MODE_15,
           EVS_MODE_16,
           EVS_MODE_17,
           EVS_MODE_18,
           EVS_MODE_19,
           EVS_MODE_20,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface EvsMode {}

    private final @EvsBandwidth int evsBandwidth;
    private final @EvsMode int evsMode;
    private final byte channelAwareMode;
    private final boolean mUseHeaderFullOnly;
    private final byte mCodecModeRequest;

    /** @hide **/
    public EvsParams(Parcel in) {
        evsBandwidth = in.readInt();
        evsMode = in.readInt();
        channelAwareMode = in.readByte();
        mUseHeaderFullOnly = in.readBoolean();
        mCodecModeRequest = in.readByte();
    }

    private EvsParams(final @EvsBandwidth int evsBandwidth, final @EvsMode int evsMode,
            final byte channelAwareMode, final boolean mUseHeaderFullOnly,
            final byte codecModeRequest) {
        this.evsBandwidth = evsBandwidth;
        this.evsMode = evsMode;
        this.channelAwareMode = channelAwareMode;
        this.mUseHeaderFullOnly = mUseHeaderFullOnly;
        this.mCodecModeRequest = codecModeRequest;
    }

    /** @hide **/
    public @EvsBandwidth int getEvsBandwidth() {
        return evsBandwidth;
    }

    /** @hide **/
    public @EvsMode int getEvsMode() {
        return evsMode;
    }

    /** @hide **/
    public byte getChannelAwareMode() {
        return channelAwareMode;
    }

    /** @hide **/
    public boolean getUseHeaderFullOnly() {
        return mUseHeaderFullOnly;
    }

    /** @hide **/
    public byte getCodecModeRequest() {
        return mCodecModeRequest;
    }

    @NonNull
    @Override
    public String toString() {
        return "EvsParams: {evsBandwidth=" + evsBandwidth
                + ", evsMode=" + evsMode
                + ", channelAwareMode=" + channelAwareMode
                + ", mUseHeaderFullOnly=" + mUseHeaderFullOnly
                + ", mCodecModeRequest=" + mCodecModeRequest
                + " }";
    }

    @Override
    public int hashCode() {
        return Objects.hash(evsBandwidth, evsMode, channelAwareMode, mUseHeaderFullOnly,
            mCodecModeRequest);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null || !(o instanceof EvsParams) || hashCode() != o.hashCode()) {
            return false;
        }

        if (this == o) {
            return true;
        }

        EvsParams s = (EvsParams) o;

        return (evsBandwidth == s.evsBandwidth
                && evsMode == s.evsMode
                && channelAwareMode == s.channelAwareMode
                && mUseHeaderFullOnly == s.mUseHeaderFullOnly
                && mCodecModeRequest == s.mCodecModeRequest);
    }

    /**
     * {@link Parcelable#describeContents}
     */
    public int describeContents() {
        return 0;
    }

    /**
     * {@link Parcelable#writeToParcel}
     */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(evsBandwidth);
        dest.writeInt(evsMode);
        dest.writeByte(channelAwareMode);
        dest.writeBoolean(mUseHeaderFullOnly);
        dest.writeByte(mCodecModeRequest);
    }

    public static final @NonNull Parcelable.Creator<EvsParams>
        CREATOR = new Parcelable.Creator() {
        public EvsParams createFromParcel(Parcel in) {
            // TODO use builder class so it will validate
            return new EvsParams(in);
        }

        public EvsParams[] newArray(int size) {
            return new EvsParams[size];
        }
    };

    /**
     * Provides a convenient way to set the fields of a {@link EvsParams}
     * when creating a new instance.
     */
    public static final class Builder {
        private @EvsBandwidth int evsBandwidth;
        private @EvsMode int evsMode;
        private byte channelAwareMode;
        private boolean mUseHeaderFullOnly;
        private byte mCodecModeRequest;

        /**
         * Default constructor for Builder.
         */
        public Builder() {
        }

        /**
         * Set the EVS speech codec bandwidth, See 3gpp spec 26.441 Table 1
         *
         * @param evsBandwidth EVS codec bandwidth
         * @return The same instance of the builder
         */
        public @NonNull Builder setEvsbandwidth(final @EvsBandwidth int evsBandwidth) {
            this.evsBandwidth = evsBandwidth;
            return this;
        }

        /**
         * Set the EVS codec mode to represent the bit rate
         *
         * @param evsMode EVS codec mode
         * @return The same instance of the builder
         */
        public @NonNull Builder setEvsMode(final @EvsMode int evsMode) {
            this.evsMode = evsMode;
            return this;
        }

        /**
         * Set the channel aware mode for the receive direction
         *
         * Permissible values are -1, 0, 2, 3, 5, and 7. If -1, channel-aware mode
         * is disabled in the session for the receive direction. If 0 or not present,
         * partial redundancy (channel-aware mode) is not used at the start of the
         * session for the receive  direction. If positive (2, 3, 5, or 7), partial
         * redundancy (channel-aware  mode) is used at the start of the session for
         * the receive direction using the value as the offset, See 3GPP TS 26.445
         * section 4.4.5
         *
         * @param channelAwareMode channel aware mode
         * @return The same instance of the builder
         */
        public @NonNull Builder setChannelAwareMode(final byte channelAwareMode) {
            this.channelAwareMode = channelAwareMode;
            return this;
        }

        /**
         * Set header full only mode the outgoing packets
         *
         * hf-only: Header full only is used for the outgoing/incoming packets. If it's true
         * then the session shall support header full format only else the session
         * could support both header full format and compact format.
         *
         * @param mUseHeaderFullOnly {@code true} if header full only needs to enabled
         * @return The same instance of the builder.
         */
        public @NonNull Builder setHeaderFullOnly(final boolean mUseHeaderFullOnly) {
            this.mUseHeaderFullOnly = mUseHeaderFullOnly;
            return this;
        }

        /**
         * cmr: Codec mode request is used to request the speech codec encoder of the
         * other party to set the frame type index of speech mode via RTP header, See
         * 3GPP TS 26.445 section A.3. Allowed values are -1, 0 and 1.
         *
         * @param codecModeRequest codec mode request
         * @return The same instance of the builder
         */
        public Builder setCodecModeRequest(final byte codecModeRequest) {
            this.mCodecModeRequest = codecModeRequest;
            return this;
        }

        /**
         * Build the EvsParams.
         *
         * @return the EvsParams object.
         */
        public @NonNull EvsParams build() {
            // TODO validation
            return new EvsParams(evsBandwidth, evsMode, channelAwareMode, mUseHeaderFullOnly,
                mCodecModeRequest);
        }
    }
}
