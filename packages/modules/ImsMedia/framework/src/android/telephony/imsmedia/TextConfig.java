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
 * The class represents RTP (Real Time Control) configuration for text stream.
 *
 * @hide
 */
public final class TextConfig extends RtpConfig {

    public static final int TEXT_CODEC_NONE = 0;
    // text codec - T140 - RFC 4103
    public static final int TEXT_T140 = 1;
    // text codec - T140 - RFC 4103 with redundancy
    public static final int TEXT_T140_RED = 2;

       /** @hide */
    @IntDef(
        flag = true,
        value = {
            TEXT_CODEC_NONE,
            TEXT_T140,
            TEXT_T140_RED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CodecType {}

    private final @CodecType int mCodecType;
    private final int mBitrate;
    private final byte mRedundantPayload;
    private final byte mRedundantLevel;
    private final boolean mKeepRedundantLevel;

    /** @hide */
    TextConfig(Parcel in) {
        super(RtpConfig.TYPE_TEXT, in);
        mCodecType = in.readInt();
        mBitrate = in.readInt();
        mRedundantPayload = in.readByte();
        mRedundantLevel = in.readByte();
        mKeepRedundantLevel = in.readBoolean();
    }

    /** @hide */
    TextConfig(Builder builder) {
        super(RtpConfig.TYPE_TEXT, builder);
        mCodecType = builder.mCodecType;
        mBitrate = builder.mBitrate;
        mRedundantPayload = builder.mRedundantPayload;
        mRedundantLevel = builder.mRedundantLevel;
        mKeepRedundantLevel = builder.mKeepRedundantLevel;
    }

    /** @hide **/
    public int getCodecType() {
        return this.mCodecType;
    }

    /** @hide **/
    public int getBitrate() {
        return this.mBitrate;
    }

    /** @hide **/
    public byte getRedundantPayload() {
        return this.mRedundantPayload;
    }

    /** @hide **/
    public byte getRedundantLevel() {
        return this.mRedundantLevel;
    }

    /** @hide **/
    public boolean getKeepRedundantLevel() {
        return this.mKeepRedundantLevel;
    }

    @NonNull
    @Override
    public String toString() {
        return super.toString() + " TextConfig: {mCodecType=" + mCodecType
            + ", mBitrate=" + mBitrate
            + ", mRedundantPayload =" + mRedundantPayload
            + ", mRedundantLevel =" + mRedundantLevel
            + ", mKeepRedundantLevel =" + mKeepRedundantLevel
            + " }";
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
            mCodecType,
            mBitrate,
            mRedundantPayload,
            mRedundantLevel,
            mKeepRedundantLevel);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null || !(o instanceof TextConfig) || hashCode() != o.hashCode()) {
            return false;
        }

        if (this == o) {
            return true;
        }

        TextConfig s = (TextConfig) o;

        if (!super.equals(s)) {
            return false;
        }

        return (mCodecType == s.mCodecType
                && mBitrate == s.mBitrate
                && mRedundantPayload == s.mRedundantPayload
                && mRedundantLevel == s.mRedundantLevel
                && mKeepRedundantLevel == s.mKeepRedundantLevel);
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
        super.writeToParcel(dest, RtpConfig.TYPE_TEXT);
        dest.writeInt(mCodecType);
        dest.writeInt(mBitrate);
        dest.writeByte(mRedundantPayload);
        dest.writeByte(mRedundantLevel);
        dest.writeBoolean(mKeepRedundantLevel);
    }

    public static final @NonNull Parcelable.Creator<TextConfig>
            CREATOR = new Parcelable.Creator() {
                public TextConfig createFromParcel(Parcel in) {
                    in.readInt();   //skip
                    return new TextConfig(in);
                }

                public TextConfig[] newArray(int size) {
                    return new TextConfig[size];
                }
            };

    /**
     * Provides a convenient way to set the fields of a {@link TextConfig}
     * when creating a new instance.
     */
    public static final class Builder extends RtpConfig.AbstractBuilder<Builder> {
        private @CodecType int mCodecType;
        private int mBitrate;
        private byte mRedundantPayload;
        private byte mRedundantLevel;
        private boolean mKeepRedundantLevel;

        /**
         * Default constructor for Builder.
         */
        public Builder() {
        }

        @Override
        Builder self() {
            return this;
        }

        /**
         * Sets text Codec type. It can be T.140 and RED.
         * @param mCodecType codec type, see {@link CodecType}
         */
        public Builder setCodecType(final @CodecType int mCodecType) {
            this.mCodecType = mCodecType;
            return this;
        }

        /**
         * Sets text bitrate of encoding streaming
         * @param bitrate mBitrate per second
         */
        public Builder setBitrate(final int bitrate) {
            this.mBitrate = bitrate;
            return this;
        }

        /**
         * Set negotiated text redundancy payload number for RED payload
         * @param payload text redundancy payload number
         */
        public Builder setRedundantPayload(final byte payload) {
            this.mRedundantPayload = payload;
            return this;
        }

        /**
         * Set text redundancy level of the T.140 payload
         * @param level text redundancy level
         */
        public Builder setRedundantLevel(final byte level) {
            this.mRedundantLevel = level;
            return this;
        }

        /**
         * The option for sending empty redundant payload when the codec type is sending T.140 and
         * RED payload
         * @param enable {@code true} enables sending empty redundant payload
         * {@code false} otherwise.
         */
        public Builder setKeepRedundantLevel(final boolean enable) {
            this.mKeepRedundantLevel = enable;
            return this;
        }

        /**
         * Build the TextConfig.
         *
         * @return the TextConfig object.
         */
        public @NonNull TextConfig build() {
            // TODO validation
            return new TextConfig(this);
        }
    }
}

