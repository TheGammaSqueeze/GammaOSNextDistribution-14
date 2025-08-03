package com.example.imsmediatestingapp;

import android.telephony.imsmedia.AmrParams;
import android.telephony.imsmedia.AudioConfig;
import android.telephony.imsmedia.EvsParams;
import android.telephony.imsmedia.VideoConfig;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

/**
 * The DeviceInfo class stores the information about a device's connection
 * details, so it can be
 * quickly and easily sent through DatagramPackets between devices. Uses the
 * Builder pattern to
 * more easily create and change variables.
 */
public class DeviceInfo implements Serializable {
    private final InetAddress mInetAddress;
    private final Set<Integer> mAudioCodecs;
    private final Set<Integer> mAmrModes;
    private final Set<Integer> mEvsBandwidths;
    private final Set<Integer> mEvsModes;
    private final int mVideoCodec;
    private final int mHandshakePort;
    private final int mAudioRtpPort;
    private final int mVideoRtpPort;
    private final int mTextRtpPort;
    private final int mVideoResolutionWidth;
    private final int mVideoResolutionHeight;
    private final int mVideoCvoValue;
    private final Set<Integer> mRtcpFbTypes;

    private DeviceInfo(InetAddress inetSocketAddress,
            Set<Integer> audioCodecs, Set<Integer> amrModes, Set<Integer> evsBandwidths,
            Set<Integer> evsModes, int videoCodec, int handshakePort, int audioRtpPort,
            int videoRtpPort, int textRtpPort, int videoResolutionWidth, int videoResolutionHeight,
            int videoCvoValue, Set<Integer> rtcpFbTypes) {
        this.mInetAddress = inetSocketAddress;
        this.mAudioCodecs = audioCodecs;
        this.mAmrModes = amrModes;
        this.mEvsBandwidths = evsBandwidths;
        this.mEvsModes = evsModes;
        this.mVideoCodec = videoCodec;
        this.mHandshakePort = handshakePort;
        this.mAudioRtpPort = audioRtpPort;
        this.mVideoRtpPort = videoRtpPort;
        this.mTextRtpPort = textRtpPort;
        this.mVideoResolutionWidth = videoResolutionWidth;
        this.mVideoResolutionHeight = videoResolutionHeight;
        this.mVideoCvoValue = videoCvoValue;
        this.mRtcpFbTypes = rtcpFbTypes;
    }

    @NonNull
    public String toString() {
        return String.format(Locale.US,
                "IP Address: %s\nHandshake Port: %d\nAudioRTP Port: %d\nVideoRTP Port: %d\n"
                        + "Selected Audio Codecs: %s\nSelected AMR Modes: %s\nSelected "
                        + "EVS Bandwidths: %s\nSelected EVS Modes: %s\nSelected Video Codec: %d"
                        + "VideoWidth: %s\n VideoHeight: %s\n Cvo: %d\n rtcpFbTypes: %s\n",
                mInetAddress.getHostName(), getHandshakePort(),
                getAudioRtpPort(), getVideoRtpPort(),
                getAudioCodecsToString(), getAmrModesToString(),
                getEvsBandwidthsToString(), getEvsModesToString(),
                getVideoCodec(), getVideoResolutionWidth(), getVideoResolutionHeight(),
                getVideoCvoValue(), getRtcpFbTypesToString());
    }

    public int getHandshakePort() {
        return mHandshakePort;
    }

    public int getAudioRtpPort() {
        return mAudioRtpPort;
    }

    public int getVideoRtpPort() {
        return mVideoRtpPort;
    }

    public int getTextRtpPort() {
        return mTextRtpPort;
    }

    public Set<Integer> getAudioCodecs() {
        return mAudioCodecs;
    }

    public Set<Integer> getEvsBandwidths() {
        return mEvsBandwidths;
    }

    public int getVideoCodec() {
        return mVideoCodec;
    }

    public Set<Integer> getEvsModes() {
        return mEvsModes;
    }

    public Set<Integer> getAmrModes() {
        return mAmrModes;
    }

    public InetAddress getInetAddress() {
        return mInetAddress;
    }

    private String getAudioCodecsToString() {
        StringJoiner joiner = new StringJoiner(",");
        mAudioCodecs.forEach(item -> joiner.add(item.toString()));
        return joiner.toString();
    }

    private String getAmrModesToString() {
        StringJoiner joiner = new StringJoiner(",");
        mAmrModes.forEach(item -> joiner.add(item.toString()));
        return joiner.toString();
    }

    private String getEvsBandwidthsToString() {
        StringJoiner joiner = new StringJoiner(",");
        mEvsBandwidths.forEach(item -> joiner.add(item.toString()));
        return joiner.toString();
    }

    private String getEvsModesToString() {
        StringJoiner joiner = new StringJoiner(",");
        mEvsModes.forEach(item -> joiner.add(item.toString()));
        return joiner.toString();
    }

    public int getVideoResolutionWidth() {
        return mVideoResolutionWidth;
    }

    public int getVideoResolutionHeight() {
        return mVideoResolutionHeight;
    }

    public int getVideoCvoValue() {
        return mVideoCvoValue;
    }

    public Set<Integer> getRtcpFbTypes() {
        return mRtcpFbTypes;
    }

    private String getRtcpFbTypesToString() {
        StringJoiner joiner = new StringJoiner(",");
        mRtcpFbTypes.forEach(item -> joiner.add(item.toString()));
        return joiner.toString();
    }

    public static final class Builder {
        private InetAddress mInetAddress;
        private Set<Integer> mAudioCodecs = new HashSet<>(Arrays.asList(AudioConfig.CODEC_AMR,
                AudioConfig.CODEC_AMR_WB, AudioConfig.CODEC_EVS, AudioConfig.CODEC_PCMA,
                AudioConfig.CODEC_PCMU));
        private Set<Integer> mAmrModes = new HashSet<>(Arrays.asList(AmrParams.AMR_MODE_0,
                AmrParams.AMR_MODE_1, AmrParams.AMR_MODE_2, AmrParams.AMR_MODE_3,
                AmrParams.AMR_MODE_4, AmrParams.AMR_MODE_5, AmrParams.AMR_MODE_6,
                AmrParams.AMR_MODE_7, AmrParams.AMR_MODE_8));
        private Set<Integer> mEvsBandwidths = new HashSet<>(Arrays.asList(EvsParams.EVS_BAND_NONE,
                EvsParams.EVS_NARROW_BAND, EvsParams.EVS_WIDE_BAND, EvsParams.EVS_SUPER_WIDE_BAND,
                EvsParams.EVS_FULL_BAND));
        private Set<Integer> mEvsModes = new HashSet<>(Arrays.asList(EvsParams.EVS_MODE_0,
                EvsParams.EVS_MODE_1, EvsParams.EVS_MODE_2, EvsParams.EVS_MODE_3,
                EvsParams.EVS_MODE_4, EvsParams.EVS_MODE_5, EvsParams.EVS_MODE_6,
                EvsParams.EVS_MODE_7, EvsParams.EVS_MODE_8, EvsParams.EVS_MODE_9,
                EvsParams.EVS_MODE_10, EvsParams.EVS_MODE_11, EvsParams.EVS_MODE_12,
                EvsParams.EVS_MODE_13, EvsParams.EVS_MODE_14, EvsParams.EVS_MODE_15,
                EvsParams.EVS_MODE_16, EvsParams.EVS_MODE_17, EvsParams.EVS_MODE_18,
                EvsParams.EVS_MODE_19, EvsParams.EVS_MODE_20));
        private int mVideoCodec;
        private int mHandshakePort;
        private int mAudioRtpPort;
        private int mVideoRtpPort;
        private int mTextRtpPort;
        private int mVideoResolutionWidth;
        private int mVideoResolutionHeight;
        private int mVideoCvoValue;
        private Set<Integer> mRtcpFbTypes = new HashSet<>(Arrays.asList(VideoConfig.RTP_FB_NONE,
                VideoConfig.RTPFB_NACK, VideoConfig.RTPFB_TMMBR, VideoConfig.RTPFB_TMMBN,
                VideoConfig.PSFB_PLI, VideoConfig.PSFB_FIR));

        public Builder() {
        }

        @NonNull
        public DeviceInfo.Builder setInetAddress(InetAddress inetAddress) {
            this.mInetAddress = inetAddress;
            return this;
        }

        @NonNull
        public DeviceInfo.Builder setAudioCodecs(Set<Integer> audioCodecs) {
            if (!audioCodecs.isEmpty()) {
                this.mAudioCodecs = audioCodecs;
            }
            return this;
        }

        public DeviceInfo.Builder setAmrModes(Set<Integer> amrModes) {
            if (!amrModes.isEmpty()) {
                this.mAmrModes = amrModes;
            }
            return this;
        }

        public DeviceInfo.Builder setEvsBandwidths(Set<Integer> evsBandwidths) {
            if (!evsBandwidths.isEmpty()) {
                this.mEvsBandwidths = evsBandwidths;
            }
            return this;
        }

        public DeviceInfo.Builder setEvsModes(Set<Integer> evsModes) {
            if (!evsModes.isEmpty()) {
                this.mEvsModes = evsModes;
            }
            return this;
        }

        public DeviceInfo.Builder setVideoCodec(int videoCodec) {
            this.mVideoCodec = videoCodec;
            return this;
        }

        @NonNull
        public DeviceInfo.Builder setHandshakePort(int handshakePort) {
            this.mHandshakePort = handshakePort;
            return this;
        }

        @NonNull
        public DeviceInfo.Builder setAudioRtpPort(int audioRtpPort) {
            this.mAudioRtpPort = audioRtpPort;
            return this;
        }

        @NonNull
        public DeviceInfo.Builder setVideoRtpPort(int videoRtpPort) {
            this.mVideoRtpPort = videoRtpPort;
            return this;
        }

        @NonNull
        public DeviceInfo.Builder setTextRtpPort(int textRtpPort) {
            this.mTextRtpPort = textRtpPort;
            return this;
        }

        public DeviceInfo.Builder setVideoResolutionWidth(int width) {
            this.mVideoResolutionWidth = width;
            return this;
        }

        public DeviceInfo.Builder setVideoResolutionHeight(int height) {
            this.mVideoResolutionHeight = height;
            return this;
        }

        public DeviceInfo.Builder setVideoCvoValue(int cvo) {
            this.mVideoCvoValue = cvo;
            return this;
        }

        public DeviceInfo.Builder setRtcpFbTypes(Set<Integer> rtcpFb) {
            if (!rtcpFb.isEmpty()) {
                this.mRtcpFbTypes = rtcpFb;
            }
            return this;
        }

        @NonNull
        public DeviceInfo build() {
            return new DeviceInfo(mInetAddress, mAudioCodecs, mAmrModes, mEvsBandwidths, mEvsModes,
                    mVideoCodec, mHandshakePort, mAudioRtpPort, mVideoRtpPort, mTextRtpPort,
                    mVideoResolutionWidth, mVideoResolutionHeight, mVideoCvoValue, mRtcpFbTypes);
        }
    }
}
