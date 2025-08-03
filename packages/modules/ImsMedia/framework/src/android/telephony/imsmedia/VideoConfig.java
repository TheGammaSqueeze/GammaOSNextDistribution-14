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
 * The class represents RTP (Real Time Control) configuration for video stream.
 *
 * @hide
 */
public final class VideoConfig extends RtpConfig {

    /** The mode that camera preview is displaying in the preview surface and the video frame does
    * not go out to the network. It is set in the case of the Ring-Go, Ring-back and in the
    * direction is RECEIVE_ONLY.
    */
    public static final int VIDEO_MODE_PREVIEW = 0;
    /** The camera is in recording mode and camera frame is displaying in preview surface and
    * frames are going out to the network in cases the MediaDirection is set as SEND_ONLY or
    * SEND_RECEIVE.
    */
    public static final int VIDEO_MODE_RECORDING = 1;
    /** An image is displayed in the preview surface and streaming to the network. The image is set
    * by the VideoConfig.
    */
    public static final int VIDEO_MODE_PAUSE_IMAGE = 2;

    /** @hide */
    @IntDef(
        flag = true,
        value = {
            VIDEO_MODE_PREVIEW,
            VIDEO_MODE_RECORDING,
            VIDEO_MODE_PAUSE_IMAGE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface VideoMode {}

    /** AVC video codec */
    public static final int VIDEO_CODEC_AVC = 1 << 5;
    /** HEVC video codec */
    public static final int VIDEO_CODEC_HEVC = 1 << 6;

    /** @hide */
    @IntDef(
        flag = true,
        value = {
            VIDEO_CODEC_AVC,
            VIDEO_CODEC_HEVC,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CodecType {}

    /** Codec profile is not specified */
    public static final int CODEC_PROFILE_NONE = 0;
    /** AVC Codec Baseline profile */
    public static final int AVC_PROFILE_BASELINE = 1;
    /** AVC Codec Constrained Baseline profile */
    public static final int AVC_PROFILE_CONSTRAINED_BASELINE = 0x00010000;
    /** AVC Codec Constrained High profile */
    public static final int AVC_PROFILE_CONSTRAINED_HIGH = 0x00080000;
    /** AVC Codec High profile */
    public static final int AVC_PROFILE_HIGH = 0x00000008;
    /** AVC Codec Main profile */
    public static final int AVC_PROFILE_MAIN = 0x00000002;
    /** HEVC Codec Main profile */
    public static final int HEVC_PROFILE_MAIN = 0x00000001;
    /** HEVC Codec Main 10 profile */
    public static final int HEVC_PROFILE_MAIN10 = 0x00000002;

    /** @hide */
    @IntDef(
        flag = true,
        value = {
            CODEC_PROFILE_NONE,
            AVC_PROFILE_BASELINE,
            AVC_PROFILE_CONSTRAINED_BASELINE,
            AVC_PROFILE_CONSTRAINED_HIGH,
            AVC_PROFILE_HIGH,
            AVC_PROFILE_MAIN,
            HEVC_PROFILE_MAIN,
            HEVC_PROFILE_MAIN10,
        })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CodecProfile {}

    /** Video codec level is not specified */
    public static final int CODEC_LEVEL_NONE = 0;
    /** AVC Codec level 1 : 176x144, 64kbps, 15.0fps for QCIF */
    public static final int AVC_LEVEL_1 = 1;
    /** AVC Codec level 1b : 176x144, 128kbps, 15.0fps for QCIF */
    public static final int AVC_LEVEL_1B = 0x00000002;
    /** AVC Codec level 1.1 : 352x288, 192kbps, 10.0fps for QVGA, 7.5fps for CIF */
    public static final int AVC_LEVEL_11 = 0x00000004;
    /** AVC Codec level 1.2 : 352x288, 384kbps, 20.0fps for QVGA, 15.1fps for CIF */
    public static final int AVC_LEVEL_12 = 0x00000008;
    /** AVC Codec level 1.3 : 352x288, 768kbps, 39.6fps for QVGA, 30.0fps for CIF */
    public static final int AVC_LEVEL_13 = 0x00000010;
    /** AVC Codec level 2.0 : 352x288, 2Mbps */
    public static final int AVC_LEVEL_2 = 0x00000020;
    /** AVC Codec level 2.1 : 704x288, 352x576, 4Mbps */
    public static final int AVC_LEVEL_21 = 0x00000040;
    /** AVC Codec level 2.2 : 720x576, 4Mbps */
    public static final int AVC_LEVEL_22 = 0x00000080;
    /** AVC Codec level 3.0 : 720x576, 10Mbps */
    public static final int AVC_LEVEL_3 = 0x00000100;
    /** AVC Codec level 3.1 : 1280x720, 14Mbps */
    public static final int AVC_LEVEL_31 = 0x00000200;
    /** HEVC Codec high tier level 1 */
    public static final int HEVC_HIGHTIER_LEVEL_1 = 0x00000002;
    /** HEVC Codec high tier level 2 */
    public static final int HEVC_HIGHTIER_LEVEL_2 = 0x00000008;
    /** HEVC Codec high tier level 2.1 */
    public static final int HEVC_HIGHTIER_LEVEL_21 = 0x00000020;
    /** HEVC Codec high tier level 3 */
    public static final int HEVC_HIGHTIER_LEVEL_3 = 0x00000080;
    /** HEVC Codec high tier level 3.1 */
    public static final int HEVC_HIGHTIER_LEVEL_31 = 0x00000200;
    /** HEVC Codec high tier level 4 */
    public static final int HEVC_HIGHTIER_LEVEL_4 = 0x00000800;
    /** HEVC Codec high tier level 4.1 */
    public static final int HEVC_HIGHTIER_LEVEL_41 = 0x00002000;
    /** HEVC Codec main tier level 1 */
    public static final int HEVC_MAINTIER_LEVEL_1 = 0x00000001;
    /** HEVC Codec main tier level 2 */
    public static final int HEVC_MAINTIER_LEVEL_2 = 0x00000004;
    /** HEVC Codec main tier level 2.1 */
    public static final int HEVC_MAINTIER_LEVEL_21 = 0x00000010;
    /** HEVC Codec main tier level 3 */
    public static final int HEVC_MAINTIER_LEVEL_3 = 0x00000040;
    /** HEVC Codec main tier level 3.1 */
    public static final int HEVC_MAINTIER_LEVEL_31 = 0x00000100;
    /** HEVC Codec main tier level 4 */
    public static final int HEVC_MAINTIER_LEVEL_4 = 0x00000400;
    /** HEVC Codec main tier level 4.1 */
    public static final int HEVC_MAINTIER_LEVEL_41 = 0x00001000;

    /** @hide */
    @IntDef(
        flag = true,
        value = {
            CODEC_LEVEL_NONE,
            AVC_LEVEL_1,
            AVC_LEVEL_1B,
            AVC_LEVEL_11,
            AVC_LEVEL_12,
            AVC_LEVEL_13,
            AVC_LEVEL_2,
            AVC_LEVEL_21,
            AVC_LEVEL_22,
            AVC_LEVEL_3,
            AVC_LEVEL_31,
            HEVC_HIGHTIER_LEVEL_1,
            HEVC_HIGHTIER_LEVEL_2,
            HEVC_HIGHTIER_LEVEL_21,
            HEVC_HIGHTIER_LEVEL_3,
            HEVC_HIGHTIER_LEVEL_31,
            HEVC_HIGHTIER_LEVEL_4,
            HEVC_HIGHTIER_LEVEL_41,
            HEVC_MAINTIER_LEVEL_1,
            HEVC_MAINTIER_LEVEL_2,
            HEVC_MAINTIER_LEVEL_21,
            HEVC_MAINTIER_LEVEL_3,
            HEVC_MAINTIER_LEVEL_31,
            HEVC_MAINTIER_LEVEL_4,
            HEVC_MAINTIER_LEVEL_41,
        })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CodecLevel {}

    /** Only single NAL unit packets MAY be used in this mode.
     * STAPs, MTAPs, and FUs MUST NOT be used. Check RFC 6184 */
    public static final int MODE_SINGLE_NAL_UNIT = 0;
    /** Only single NAL unit packets, STAP-As, and FU-As MAY be used in this mode.
     * STAP-Bs, MTAPs, and FU-Bs MUST NOT be used.  The transmission order of NAL units
     * MUST comply with the NAL unit decoding order. Check RFC 6184*/
    public static final int MODE_NON_INTERLEAVED = 1;
    /** STAP-Bs, MTAPs, FU-As, and FU-Bs MAY be used.  STAP-As and single NAL unit packets
     * MUST NOT be used.  The transmission order of packets and NAL units is constrained in
     * certain rule, check RFC 6184. */
    public static final int MODE_INTERLEAVED = 2;

    /** @hide */
    @IntDef(
        flag = true,
        value = {
            MODE_SINGLE_NAL_UNIT,
            MODE_NON_INTERLEAVED,
            MODE_INTERLEAVED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PacketizationMode {}

    public static final int RTP_FB_NONE = 0;
    /**
     * The Generic NACK(Negative Acknowledgement) message identified by RTCP packet type
     * value PT=RTPFB and FMT=1. RFC 4585.
     */
    public static final int RTPFB_NACK = 1 << 0;
    /**
     * The Temporary Maximum Media Stream Bit Rate Request is identified by
     * RTCP packet type value PT=RTPFB and FMT=3. RFC 5104.
     */
    public static final int RTPFB_TMMBR = 1 << 1;
    /**
     * The Temporary Maximum Media Stream Bit Rate Notification is identified
     * by RTCP packet type value PT=RTPFB and FMT=4. RFC 5104.
     */
    public static final int RTPFB_TMMBN = 1 << 2;
    /**
     * Picture Loss Indication. The PLI FB message is identified
     * by RTCP packet type value PT=PSFB and FMT=1. RFC 4585.
     */
    public static final int PSFB_PLI = 1 << 3;
    /**
    * Full Intra Request. The FIR message is identified by RTCP packet type
    * value PT=PSFB and FMT=4. RFC 5104.
    */
    public static final int PSFB_FIR = 1 << 4;

    /** @hide */
    @IntDef(
        flag = true,
        value = {
            RTP_FB_NONE,
            RTPFB_NACK,
            RTPFB_TMMBR,
            RTPFB_TMMBN,
            PSFB_PLI,
            PSFB_FIR,
        })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RtcpFbTypes {}

    private final @VideoMode int mVideoMode;
    private final @CodecType int mCodecType;
    private final int mFramerate;
    private final int mBitrate;
    private final @CodecProfile int mCodecProfile;
    private final @CodecLevel int mCodecLevel;
    private final int mIntraFrameIntervalSec;
    private final int mPacketizationMode;
    private final int mCameraId;
    private final int mCameraZoom;
    private final int mResolutionWidth;
    private final int mResolutionHeight;
    @Nullable
    private final String mPauseImagePath;
    private final int mDeviceOrientationDegree;
    private final int mCvoValue;
    private final int mMaxMtuBytes;
    private final @RtcpFbTypes int mRtcpFbTypes;

    /** @hide */
    VideoConfig(Parcel in) {
        super(RtpConfig.TYPE_VIDEO, in);
        mVideoMode = in.readInt();
        mCodecType = in.readInt();
        mFramerate = in.readInt();
        mBitrate = in.readInt();
        mMaxMtuBytes = in.readInt();
        mCodecProfile = in.readInt();
        mCodecLevel = in.readInt();
        mIntraFrameIntervalSec = in.readInt();
        mPacketizationMode = in.readInt();
        mCameraId = in.readInt();
        mCameraZoom = in.readInt();
        mResolutionWidth = in.readInt();
        mResolutionHeight = in.readInt();
        mPauseImagePath = in.readString();
        mDeviceOrientationDegree = in.readInt();
        mCvoValue = in.readInt();
        mRtcpFbTypes = in.readInt();
    }

    /** @hide */
    VideoConfig(Builder builder) {
        super(RtpConfig.TYPE_VIDEO, builder);
        mVideoMode = builder.mVideoMode;
        mCodecType = builder.mCodecType;
        mFramerate = builder.mFramerate;
        mBitrate = builder.mBitrate;
        mMaxMtuBytes = builder.mMaxMtuBytes;
        mCodecProfile = builder.mCodecProfile;
        mCodecLevel = builder.mCodecLevel;
        mIntraFrameIntervalSec = builder.mIntraFrameIntervalSec;
        mPacketizationMode = builder.mPacketizationMode;
        mCameraId = builder.mCameraId;
        mCameraZoom = builder.mCameraZoom;
        mResolutionWidth = builder.mResolutionWidth;
        mResolutionHeight = builder.mResolutionHeight;
        mPauseImagePath = builder.mPauseImagePath;
        mDeviceOrientationDegree = builder.mDeviceOrientationDegree;
        mCvoValue = builder.mCvoValue;
        mRtcpFbTypes = builder.mRtcpFbTypes;
    }

    /** @hide **/
    public int getVideoMode() {
        return this.mVideoMode;
    }

    /** @hide **/
    public int getCodecType() {
        return this.mCodecType;
    }

    /** @hide **/
    public int getFramerate() {
        return this.mFramerate;
    }

    /** @hide **/
    public int getBitrate() {
        return this.mBitrate;
    }

    /** @hide **/
    public int getCodecProfile() {
        return this.mCodecProfile;
    }

    /** @hide **/
    public int getCodecLevel() {
        return this.mCodecLevel;
    }

    /** @hide **/
    public int getIntraFrameIntervalSec() {
        return this.mIntraFrameIntervalSec;
    }

    /** @hide **/
    public int getPacketizationMode() {
        return this.mPacketizationMode;
    }

    /** @hide **/
    public int getCameraId() {
        return this.mCameraId;
    }

    /** @hide **/
    public int getCameraZoom() {
        return this.mCameraZoom;
    }

    /** @hide **/
    public int getResolutionWidth() {
        return this.mResolutionWidth;
    }

    /** @hide **/
    public int getResolutionHeight() {
        return this.mResolutionHeight;
    }

    /** @hide **/
    public String getPauseImagePath() {
        return this.mPauseImagePath;
    }

    /** @hide **/
    public int getDeviceOrientationDegree() {
        return this.mDeviceOrientationDegree;
    }

    /** @hide **/
    public int getCvoValue() {
        return this.mCvoValue;
    }

    /** @hide **/
    public int getRtcpFbTypes() {
        return this.mRtcpFbTypes;
    }

    /** @hide **/
    public int getMaxMtuBytes() {
        return mMaxMtuBytes;
    }

    @NonNull
    @Override
    public String toString() {
        return super.toString() + " VideoConfig: {mVideoMode=" + mVideoMode
            + ", mCodecType=" + mCodecType
            + ", mFramerate=" + mFramerate
            + ", mBitrate=" + mBitrate
            + ", mMaxMtuBytes=" + mMaxMtuBytes
            + ", mCodecProfile=" + mCodecProfile
            + ", mCodecLevel=" + mCodecLevel
            + ", mIntraFrameIntervalSec=" + mIntraFrameIntervalSec
            + ", mPacketizationMode=" + mPacketizationMode
            + ", mCameraId=" + mCameraId
            + ", mCameraZoom=" + mCameraZoom
            + ", mResolutionWidth=" + mResolutionWidth
            + ", mResolutionHeight=" + mResolutionHeight
            + ", mPauseImagePath=" + mPauseImagePath
            + ", mDeviceOrientationDegree=" + mDeviceOrientationDegree
            + ", mCvoValue=" + mCvoValue
            + ", rtcpFb=" + mRtcpFbTypes
            + " }";
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mVideoMode, mCodecType, mFramerate, mBitrate,
            mMaxMtuBytes, mCodecProfile, mCodecLevel, mIntraFrameIntervalSec,
            mPacketizationMode, mCameraId, mCameraZoom, mResolutionWidth, mResolutionHeight,
            mPauseImagePath, mDeviceOrientationDegree, mCvoValue, mRtcpFbTypes);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null || !(o instanceof VideoConfig) || hashCode() != o.hashCode()) {
            return false;
        }

        if (this == o) {
            return true;
        }

        VideoConfig s = (VideoConfig) o;

        if (!super.equals(s)) {
            return false;
        }

        return (mVideoMode == s.mVideoMode
            && mCodecType == s.mCodecType
            && mFramerate == s.mFramerate
            && mBitrate == s.mBitrate
            && mMaxMtuBytes == s.mMaxMtuBytes
            && mCodecProfile == s.mCodecProfile
            && mCodecLevel == s.mCodecLevel
            && mIntraFrameIntervalSec == s.mIntraFrameIntervalSec
            && mPacketizationMode == s.mPacketizationMode
            && mCameraId == s.mCameraId
            && mCameraZoom == s.mCameraZoom
            && mResolutionWidth == s.mResolutionWidth
            && mResolutionHeight == s.mResolutionHeight
            && Objects.equals(mPauseImagePath, s.mPauseImagePath)
            && mDeviceOrientationDegree == s.mDeviceOrientationDegree
            && mCvoValue == s.mCvoValue
            && mRtcpFbTypes == s.mRtcpFbTypes);
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
        super.writeToParcel(dest, RtpConfig.TYPE_VIDEO);
        dest.writeInt(mVideoMode);
        dest.writeInt(mCodecType);
        dest.writeInt(mFramerate);
        dest.writeInt(mBitrate);
        dest.writeInt(mMaxMtuBytes);
        dest.writeInt(mCodecProfile);
        dest.writeInt(mCodecLevel);
        dest.writeInt(mIntraFrameIntervalSec);
        dest.writeInt(mPacketizationMode);
        dest.writeInt(mCameraId);
        dest.writeInt(mCameraZoom);
        dest.writeInt(mResolutionWidth);
        dest.writeInt(mResolutionHeight);
        dest.writeString(mPauseImagePath);
        dest.writeInt(mDeviceOrientationDegree);
        dest.writeInt(mCvoValue);
        dest.writeInt(mRtcpFbTypes);
    }

    public static final @NonNull Parcelable.Creator<VideoConfig>
            CREATOR = new Parcelable.Creator() {
                public VideoConfig createFromParcel(Parcel in) {
                    in.readInt();   //skip
                    return new VideoConfig(in);
                }

                public VideoConfig[] newArray(int size) {
                    return new VideoConfig[size];
                }
            };

    /**
     * Provides a convenient way to set the fields of a {@link VideoConfig}
     * when creating a new instance.
     */
    public static final class Builder extends RtpConfig.AbstractBuilder<Builder> {
        private int mVideoMode;
        private int mCodecType;
        private int mFramerate;
        private int mBitrate;
        private int mMaxMtuBytes;
        private int mCodecProfile;
        private int mCodecLevel;
        private int mIntraFrameIntervalSec;
        private int mPacketizationMode;
        private int mCameraId;
        private int mCameraZoom;
        private int mResolutionWidth;
        private int mResolutionHeight;
        @Nullable
        private String mPauseImagePath;
        private int mDeviceOrientationDegree;
        private int mCvoValue;
        private int mRtcpFbTypes;

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
         * Sets video mode
         * @param videoMode video mode for preview, recording and puse image streaming
         */
        public Builder setVideoMode(final @VideoMode int videoMode) {
            this.mVideoMode = videoMode;
            return this;
        }

        /**
         * Sets video Codec type. It can be H.264, HEVC codec.
         * @param codecType codec type, see {@link CodecType}
         */
        public Builder setCodecType(final @CodecType int codecType) {
            this.mCodecType = codecType;
            return this;
        }

        /**
         * Sets video frame rate in encoding streaming
         * @param framerate frame rate per second
         */
        public Builder setFramerate(final int framerate) {
            this.mFramerate = framerate;
            return this;
        }

        /**
         * Sets video mBitrate of encoding streaming
         * @param bitrate mBitrate per second
         */
        public Builder setBitrate(final int bitrate) {
            this.mBitrate = bitrate;
            return this;
        }

        /**
         * Sets maximum Rtp transfer unit in bytes
         * @param maxMtuBytes bytes
         */
        public Builder setMaxMtuBytes(final int maxMtuBytes) {
            this.mMaxMtuBytes = maxMtuBytes;
            return self();
        }

        /**
         * Sets video codec encoder profile
         * @param codecProfile codec profile, see {@link CodecProfile}
         */
        public Builder setCodecProfile(final @CodecProfile int codecProfile) {
            this.mCodecProfile = codecProfile;
            return this;
        }

        /**
         * Sets video codec encoder level
         * @param codecLevel codec level, see {@link CodecLevel}
         */
        public Builder setCodecLevel(final @CodecLevel int codecLevel) {
            this.mCodecLevel = codecLevel;
            return this;
        }

        /**
         * Sets video codec encoder interval of intra-frames in seconds
         * @param intraFrameIntervalSec interval of frame in seconds unit
         */
        public Builder setIntraFrameIntervalSec(final int intraFrameIntervalSec) {
            this.mIntraFrameIntervalSec = intraFrameIntervalSec;
            return this;
        }

        /**
         * Sets video Rtp packetization mode.
         * @param packetizationMode it supports 0 and 1. 0 means Single NAL unit mode,
         * 1 means non-interleaved mode. And Interleaved mode is not supported. Check RFC 6184.
         */
        public Builder setPacketizationMode(final int packetizationMode) {
            this.mPacketizationMode = packetizationMode;
            return this;
        }

        /**
         * Sets an identification of camera device to use for video source
         * @param cameraId camera device identification
         */
        public Builder setCameraId(final int cameraId) {
            this.mCameraId = cameraId;
            return this;
        }

        /**
         * Sets a level of zoom of camera device.
         * @param cameraZoom zoom level, it can be 0 to 10.
         */
        public Builder setCameraZoom(final int cameraZoom) {
            this.mCameraZoom = cameraZoom;
            return this;
        }

        /**
         * Sets width of resolution in transmit streaming.
         * @param resolutionWidth width of video resolution
         */
        public Builder setResolutionWidth(final int resolutionWidth) {
            this.mResolutionWidth = resolutionWidth;
            return this;
        }

        /**
         * Sets height of resolution in transmit streaming.
         * @param resolutionHeight height of video resolution
         */
        public Builder setResolutionHeight(final int resolutionHeight) {
            this.mResolutionHeight = resolutionHeight;
            return this;
        }

        /**
         * Sets path of jpg image file for video mode VIDEO_MODE_PAUSE_IMAGE.
         * @param pauseImagePath image file path
         */
        public Builder setPauseImagePath(final String pauseImagePath) {
            this.mPauseImagePath = pauseImagePath;
            return this;
        }

        /**
         * Sets a device orientation in degree unit captured from device sensor.
         * @param deviceOrientationDegree degree of device orientation.
         */
        public Builder setDeviceOrientationDegree(final int deviceOrientationDegree) {
            this.mDeviceOrientationDegree = deviceOrientationDegree;
            return this;
        }

        /**
         * Sets a value to identify CVO RTP header extension id defined by the SDP negotiation.
         * When the flag is set, MediaStack sends CVO RTP extension byte in the RTP header when the
         * sendHeaderExtension is invoked and the Video IDR frame is sent. if this value is -1,
         * CVO is disabled, and non zero means CVO enabled with specified offset. Check RFC 5285
         * @param cvoValue It is the local identifier of extension. valid range is 1-14.
         */
        public Builder setCvoValue(final int cvoValue) {
            this.mCvoValue = cvoValue;
            return this;
        }

        /**
         * Sets RTPFB, PSFB configuration with RTCP Protocol.
         * @param rtcpFbTypes type of rtcp feedback protocols. see {@link RtcpFbTypes}
         */
        public Builder setRtcpFbTypes(final @RtcpFbTypes int rtcpFbTypes) {
            this.mRtcpFbTypes = rtcpFbTypes;
            return this;
        }

        /**
         * Build the VideoConfig.
         *
         * @return the VideoConfig object.
         */
        public @NonNull VideoConfig build() {
            return new VideoConfig(this);
        }
    }
}

