package com.android.server.wifi.hotspot2;

import android.net.MacAddress;
import android.net.wifi.MloLink;
import android.net.wifi.ScanResult;
import android.util.Log;

import com.android.server.wifi.hotspot2.anqp.ANQPElement;
import com.android.server.wifi.hotspot2.anqp.Constants;
import com.android.server.wifi.hotspot2.anqp.RawByteElement;
import com.android.server.wifi.util.InformationElementUtil;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class NetworkDetail {

    private static final boolean DBG = false;

    private static final String TAG = "NetworkDetail";

    public enum Ant {
        Private,
        PrivateWithGuest,
        ChargeablePublic,
        FreePublic,
        Personal,
        EmergencyOnly,
        Resvd6,
        Resvd7,
        Resvd8,
        Resvd9,
        Resvd10,
        Resvd11,
        Resvd12,
        Resvd13,
        TestOrExperimental,
        Wildcard
    }

    public enum HSRelease {
        R1,
        R2,
        R3,
        Unknown
    }

    // General identifiers:
    private final String mSSID;
    private final long mHESSID;
    private final long mBSSID;
    // True if the SSID is potentially from a hidden network
    private final boolean mIsHiddenSsid;

    // BSS Load element:
    private final int mStationCount;
    private final int mChannelUtilization;
    private final int mCapacity;

    //channel detailed information
   /*
    * 0 -- 20 MHz
    * 1 -- 40 MHz
    * 2 -- 80 MHz
    * 3 -- 160 MHz
    * 4 -- 80 + 80 MHz
    */
    private final int mChannelWidth;
    private final int mPrimaryFreq;
    private final int mCenterfreq0;
    private final int mCenterfreq1;

    /*
     * 802.11 Standard (calculated from Capabilities and Supported Rates)
     * 0 -- Unknown
     * 1 -- 802.11a
     * 2 -- 802.11b
     * 3 -- 802.11g
     * 4 -- 802.11n
     * 7 -- 802.11ac
     */
    private final int mWifiMode;
    private final int mMaxRate;
    private final int mMaxNumberSpatialStreams;

    /*
     * From Interworking element:
     * mAnt non null indicates the presence of Interworking, i.e. 802.11u
     */
    private final Ant mAnt;
    private final boolean mInternet;

    /*
     * From HS20 Indication element:
     * mHSRelease is null only if the HS20 Indication element was not present.
     * mAnqpDomainID is set to -1 if not present in the element.
     */
    private final HSRelease mHSRelease;
    private final int mAnqpDomainID;

    /*
     * From beacon:
     * mAnqpOICount is how many additional OIs are available through ANQP.
     * mRoamingConsortiums is either null, if the element was not present, or is an array of
     * 1, 2 or 3 longs in which the roaming consortium values occupy the LSBs.
     */
    private final int mAnqpOICount;
    private final long[] mRoamingConsortiums;
    private int mDtimInterval = -1;
    private String mCountryCode;

    private final InformationElementUtil.ExtendedCapabilities mExtendedCapabilities;

    private final Map<Constants.ANQPElementType, ANQPElement> mANQPElements;

    /*
     * From Wi-Fi Alliance MBO-OCE Information element.
     * mMboAssociationDisallowedReasonCode is the reason code for AP not accepting new connections
     * and is set to -1 if association disallowed attribute is not present in the element.
     */
    private final int mMboAssociationDisallowedReasonCode;
    private final boolean mMboSupported;
    private final boolean mMboCellularDataAware;
    private final boolean mOceSupported;

    // Target wake time (TWT) allows an AP to manage activity in the BSS in order to minimize
    // contention between STAs and to reduce the required amount of time that a STA utilizing a
    // power management mode needs to be awake.

    // The HE AP requests that STAs participate in TWT by setting the TWT Required subfield to 1
    // in HE Operation elements. STAs that support TWT and receive an HE Operation element with
    // the TWT Required subfield set to 1 must either negotiate individual TWT agreements or
    // participate in broadcast TWT operation.
    private final boolean mTwtRequired;
    // With Individual TWT operation, a STA negotiate a wake schedule with an access point, allowing
    // it to wake up only when required.
    private final boolean mIndividualTwtSupported;
    // In Broadcast TWT operation, an AP can set up a shared TWT session for a group of stations
    // and specify the TWT parameters periodically in Beacon frames.
    private final boolean mBroadcastTwtSupported;
    // Restricted Target Wake Time (TWT) is a feature that allows an access point to allocate
    // exclusive access to a medium at specified times.
    private final boolean mRestrictedTwtSupported;

    // EPCS priority access is a mechanism that provides prioritized access to the wireless
    // medium for authorized users to increase their probability of successful communication
    // during periods of network congestion.
    private final boolean mEpcsPriorityAccessSupported;

    // Fast Initial Link Setup (FILS)
    private final boolean mFilsCapable;

    // 6 GHz Access Point Type
    private final InformationElementUtil.ApType6GHz mApType6GHz;

    // IEEE 802.11az
    private final boolean mIs11azSupported;

    // MLO Attributes
    private MacAddress mMldMacAddress = null;
    private int mMloLinkId = MloLink.INVALID_MLO_LINK_ID;
    private List<MloLink> mAffiliatedMloLinks = Collections.emptyList();
    private byte[] mDisabledSubchannelBitmap;

    public NetworkDetail(String bssid, ScanResult.InformationElement[] infoElements,
            List<String> anqpLines, int freq) {
        if (infoElements == null) {
            infoElements = new ScanResult.InformationElement[0];
        }

        mBSSID = Utils.parseMac(bssid);

        String ssid = null;
        boolean isHiddenSsid = false;
        byte[] ssidOctets = null;

        InformationElementUtil.BssLoad bssLoad = new InformationElementUtil.BssLoad();

        InformationElementUtil.Interworking interworking =
                new InformationElementUtil.Interworking();

        InformationElementUtil.RoamingConsortium roamingConsortium =
                new InformationElementUtil.RoamingConsortium();

        InformationElementUtil.Vsa vsa = new InformationElementUtil.Vsa();

        InformationElementUtil.HtOperation htOperation = new InformationElementUtil.HtOperation();
        InformationElementUtil.VhtOperation vhtOperation =
                new InformationElementUtil.VhtOperation();
        InformationElementUtil.HeOperation heOperation = new InformationElementUtil.HeOperation();
        InformationElementUtil.EhtOperation ehtOperation =
                new InformationElementUtil.EhtOperation();

        InformationElementUtil.HtCapabilities htCapabilities =
                new InformationElementUtil.HtCapabilities();
        InformationElementUtil.VhtCapabilities vhtCapabilities =
                new InformationElementUtil.VhtCapabilities();
        InformationElementUtil.HeCapabilities heCapabilities =
                new InformationElementUtil.HeCapabilities();
        InformationElementUtil.EhtCapabilities ehtCapabilities =
                new InformationElementUtil.EhtCapabilities();
        InformationElementUtil.Rnr rnr =
                new InformationElementUtil.Rnr();
        InformationElementUtil.MultiLink multiLink =
                new InformationElementUtil.MultiLink();
        InformationElementUtil.ExtendedCapabilities extendedCapabilities =
                new InformationElementUtil.ExtendedCapabilities();

        InformationElementUtil.Country country =
                new InformationElementUtil.Country();

        InformationElementUtil.TrafficIndicationMap trafficIndicationMap =
                new InformationElementUtil.TrafficIndicationMap();

        InformationElementUtil.SupportedRates supportedRates =
                new InformationElementUtil.SupportedRates();
        InformationElementUtil.SupportedRates extendedSupportedRates =
                new InformationElementUtil.SupportedRates();

        RuntimeException exception = null;

        ArrayList<Integer> iesFound = new ArrayList<Integer>();
        try {
            for (ScanResult.InformationElement ie : infoElements) {
                iesFound.add(ie.id);
                switch (ie.id) {
                    case ScanResult.InformationElement.EID_SSID:
                        ssidOctets = ie.bytes;
                        break;
                    case ScanResult.InformationElement.EID_BSS_LOAD:
                        bssLoad.from(ie);
                        break;
                    case ScanResult.InformationElement.EID_HT_OPERATION:
                        htOperation.from(ie);
                        break;
                    case ScanResult.InformationElement.EID_VHT_OPERATION:
                        vhtOperation.from(ie);
                        break;
                    case ScanResult.InformationElement.EID_HT_CAPABILITIES:
                        htCapabilities.from(ie);
                        break;
                    case ScanResult.InformationElement.EID_VHT_CAPABILITIES:
                        vhtCapabilities.from(ie);
                        break;
                    case ScanResult.InformationElement.EID_INTERWORKING:
                        interworking.from(ie);
                        break;
                    case ScanResult.InformationElement.EID_ROAMING_CONSORTIUM:
                        roamingConsortium.from(ie);
                        break;
                    case ScanResult.InformationElement.EID_VSA:
                        vsa.from(ie);
                        break;
                    case ScanResult.InformationElement.EID_EXTENDED_CAPS:
                        extendedCapabilities.from(ie);
                        break;
                    case ScanResult.InformationElement.EID_COUNTRY:
                        country.from(ie);
                        break;
                    case ScanResult.InformationElement.EID_TIM:
                        trafficIndicationMap.from(ie);
                        break;
                    case ScanResult.InformationElement.EID_SUPPORTED_RATES:
                        supportedRates.from(ie);
                        break;
                    case ScanResult.InformationElement.EID_EXTENDED_SUPPORTED_RATES:
                        extendedSupportedRates.from(ie);
                        break;
                    case ScanResult.InformationElement.EID_RNR:
                        rnr.from(ie);
                        break;
                    case ScanResult.InformationElement.EID_EXTENSION_PRESENT:
                        switch(ie.idExt) {
                            case ScanResult.InformationElement.EID_EXT_HE_OPERATION:
                                heOperation.from(ie);
                                break;
                            case ScanResult.InformationElement.EID_EXT_HE_CAPABILITIES:
                                heCapabilities.from(ie);
                                break;
                            case ScanResult.InformationElement.EID_EXT_EHT_OPERATION:
                                ehtOperation.from(ie);
                                break;
                            case ScanResult.InformationElement.EID_EXT_EHT_CAPABILITIES:
                                ehtCapabilities.from(ie);
                                break;
                            case ScanResult.InformationElement.EID_EXT_MULTI_LINK:
                                multiLink.from(ie);
                                break;
                            default:
                                break;
                        }
                        break;
                    default:
                        break;
                }
            }
        }
        catch (IllegalArgumentException | BufferUnderflowException | ArrayIndexOutOfBoundsException e) {
            Log.d(TAG, "Caught " + e);
            if (ssidOctets == null) {
                throw new IllegalArgumentException("Malformed IE string (no SSID)", e);
            }
            exception = e;
        }
        if (ssidOctets != null) {
            /*
             * Strict use of the "UTF-8 SSID" bit by APs appears to be spotty at best even if the
             * encoding truly is in UTF-8. An unconditional attempt to decode the SSID as UTF-8 is
             * therefore always made with a fall back to 8859-1 under normal circumstances.
             * If, however, a previous exception was detected and the UTF-8 bit is set, failure to
             * decode the SSID will be used as an indication that the whole frame is malformed and
             * an exception will be triggered.
             */
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            try {
                CharBuffer decoded = decoder.decode(ByteBuffer.wrap(ssidOctets));
                ssid = decoded.toString();
            }
            catch (CharacterCodingException cce) {
                ssid = null;
            }

            if (ssid == null) {
                if (extendedCapabilities.isStrictUtf8() && exception != null) {
                    throw new IllegalArgumentException("Failed to decode SSID in dubious IE string");
                }
                else {
                    ssid = new String(ssidOctets, StandardCharsets.ISO_8859_1);
                }
            }
            isHiddenSsid = true;
            for (byte byteVal : ssidOctets) {
                if (byteVal != 0) {
                    isHiddenSsid = false;
                    break;
                }
            }
        }

        mSSID = ssid;
        mHESSID = interworking.hessid;
        mIsHiddenSsid = isHiddenSsid;
        mStationCount = bssLoad.stationCount;
        mChannelUtilization = bssLoad.channelUtilization;
        mCapacity = bssLoad.capacity;
        mAnt = interworking.ant;
        mInternet = interworking.internet;
        mHSRelease = vsa.hsRelease;
        mAnqpDomainID = vsa.anqpDomainID;
        mMboSupported = vsa.IsMboCapable;
        mMboCellularDataAware = vsa.IsMboApCellularDataAware;
        mOceSupported = vsa.IsOceCapable;
        mMboAssociationDisallowedReasonCode = vsa.mboAssociationDisallowedReasonCode;
        mAnqpOICount = roamingConsortium.anqpOICount;
        mRoamingConsortiums = roamingConsortium.getRoamingConsortiums();
        mExtendedCapabilities = extendedCapabilities;
        mANQPElements = null;
        //set up channel info
        mPrimaryFreq = freq;
        mTwtRequired = heOperation.isTwtRequired();
        mIndividualTwtSupported = heCapabilities.isTwtResponderSupported();
        mBroadcastTwtSupported = heCapabilities.isBroadcastTwtSupported();
        mRestrictedTwtSupported = ehtCapabilities.isRestrictedTwtSupported();
        mEpcsPriorityAccessSupported = ehtCapabilities.isEpcsPriorityAccessSupported();
        mFilsCapable = extendedCapabilities.isFilsCapable();
        mApType6GHz = heOperation.getApType6GHz();
        mIs11azSupported = extendedCapabilities.isTriggerBasedRangingRespSupported()
                || extendedCapabilities.isNonTriggerBasedRangingRespSupported();
        int channelWidth = ScanResult.UNSPECIFIED;
        int centerFreq0 = mPrimaryFreq;
        int centerFreq1 = 0;

        // Check if EHT Operation Info is present in EHT operation IE.
        if (ehtOperation.isEhtOperationInfoPresent()) {
            int operatingBand = ScanResult.toBand(mPrimaryFreq);
            channelWidth = ehtOperation.getChannelWidth();
            centerFreq0 = ehtOperation.getCenterFreq0(operatingBand);
            centerFreq1 = ehtOperation.getCenterFreq1(operatingBand);
            mDisabledSubchannelBitmap = ehtOperation.getDisabledSubchannelBitmap();
        }

        // Proceed to HE Operation IE if channel width and center frequencies were not obtained
        // from EHT Operation IE
        if (channelWidth == ScanResult.UNSPECIFIED) {
            // Check if HE Operation IE is present
            if (heOperation.isPresent()) {
                // If 6GHz info is present, then parameters should be acquired from HE Operation IE
                if (heOperation.is6GhzInfoPresent()) {
                    channelWidth = heOperation.getChannelWidth();
                    centerFreq0 = heOperation.getCenterFreq0();
                    centerFreq1 = heOperation.getCenterFreq1();
                } else if (heOperation.isVhtInfoPresent()) {
                    // VHT Operation Info could be included inside the HE Operation IE
                    vhtOperation.from(heOperation.getVhtInfoElement());
                }
            }
        }

        // Proceed to VHT Operation IE if parameters were not obtained from HE Operation IE
        // Not operating in 6GHz
        if (channelWidth == ScanResult.UNSPECIFIED) {
            if (vhtOperation.isPresent()) {
                channelWidth = vhtOperation.getChannelWidth();
                if (channelWidth != ScanResult.UNSPECIFIED) {
                    centerFreq0 = vhtOperation.getCenterFreq0();
                    centerFreq1 = vhtOperation.getCenterFreq1();
                }
            }
        }

        // Proceed to HT Operation IE if parameters were not obtained from VHT/HE Operation IEs
        // Apply to operating in 2.4/5GHz with 20/40MHz channels
        if (channelWidth == ScanResult.UNSPECIFIED) {
            //Either no vht, or vht shows BW is 40/20 MHz
            if (htOperation.isPresent()) {
                channelWidth = htOperation.getChannelWidth();
                centerFreq0 = htOperation.getCenterFreq0(mPrimaryFreq);
            }
        }

        if (channelWidth == ScanResult.UNSPECIFIED) {
            // Failed to obtain channel info from HE, VHT, HT IEs (possibly a 802.11a/b/g legacy AP)
            channelWidth = ScanResult.CHANNEL_WIDTH_20MHZ;
        }

        mChannelWidth = channelWidth;
        mCenterfreq0 = centerFreq0;
        mCenterfreq1 = centerFreq1;

        if (country.isValid()) {
            mCountryCode = country.getCountryCode();
        }

        // If trafficIndicationMap is not valid, mDtimPeriod will be negative
        if (trafficIndicationMap.isValid()) {
            mDtimInterval = trafficIndicationMap.mDtimPeriod;
        }

        mMaxNumberSpatialStreams = Math.max(heCapabilities.getMaxNumberSpatialStreams(),
                Math.max(vhtCapabilities.getMaxNumberSpatialStreams(),
                htCapabilities.getMaxNumberSpatialStreams()));

        int maxRateA = 0;
        int maxRateB = 0;
        // If we got some Extended supported rates, consider them, if not default to 0
        if (extendedSupportedRates.isValid()) {
            // rates are sorted from smallest to largest in InformationElement
            maxRateB = extendedSupportedRates.mRates.get(extendedSupportedRates.mRates.size() - 1);
        }
        // Only process the determination logic if we got a 'SupportedRates'
        if (supportedRates.isValid()) {
            maxRateA = supportedRates.mRates.get(supportedRates.mRates.size() - 1);
            mMaxRate = maxRateA > maxRateB ? maxRateA : maxRateB;
            mWifiMode = InformationElementUtil.WifiMode.determineMode(mPrimaryFreq, mMaxRate,
                    ehtOperation.isPresent(), heOperation.isPresent(), vhtOperation.isPresent(),
                    htOperation.isPresent(),
                    iesFound.contains(ScanResult.InformationElement.EID_ERP));
        } else {
            mWifiMode = 0;
            mMaxRate = 0;
        }

        if (multiLink.isPresent()) {
            mMldMacAddress = multiLink.getMldMacAddress();
            mMloLinkId = multiLink.getLinkId();
            if (rnr.isPresent()) {
                if (!rnr.getAffiliatedMloLinks().isEmpty()) {
                    mAffiliatedMloLinks = new ArrayList<>(rnr.getAffiliatedMloLinks());
                } else if (!multiLink.getAffiliatedLinks().isEmpty()) {
                    mAffiliatedMloLinks = new ArrayList<>(multiLink.getAffiliatedLinks());
                }
            }

            // Add the current link to the list of links if not empty
            if (!mAffiliatedMloLinks.isEmpty()) {
                MloLink link = new MloLink();
                link.setApMacAddress(MacAddress.fromString(bssid));
                link.setChannel(ScanResult.convertFrequencyMhzToChannelIfSupported(mPrimaryFreq));
                link.setBand(ScanResult.toBand(mPrimaryFreq));
                link.setLinkId(mMloLinkId);
                mAffiliatedMloLinks.add(link);
            }
        }

        if (DBG) {
            Log.d(TAG, mSSID + "ChannelWidth is: " + mChannelWidth + " PrimaryFreq: "
                    + mPrimaryFreq + " Centerfreq0: " + mCenterfreq0 + " Centerfreq1: "
                    + mCenterfreq1 + (extendedCapabilities.is80211McRTTResponder()
                    ? " Support RTT responder" : " Do not support RTT responder")
                    + " MaxNumberSpatialStreams: " + mMaxNumberSpatialStreams
                    + " MboAssociationDisallowedReasonCode: "
                    + mMboAssociationDisallowedReasonCode);
            Log.v("WifiMode", mSSID
                    + ", WifiMode: " + InformationElementUtil.WifiMode.toString(mWifiMode)
                    + ", Freq: " + mPrimaryFreq
                    + ", MaxRate: " + mMaxRate
                    + ", EHT: " + String.valueOf(ehtOperation.isPresent())
                    + ", HE: " + String.valueOf(heOperation.isPresent())
                    + ", VHT: " + String.valueOf(vhtOperation.isPresent())
                    + ", HT: " + String.valueOf(htOperation.isPresent())
                    + ", ERP: " + String.valueOf(
                    iesFound.contains(ScanResult.InformationElement.EID_ERP))
                    + ", SupportedRates: " + supportedRates.toString()
                    + " ExtendedSupportedRates: " + extendedSupportedRates.toString());
        }
    }

    /**
     * Copy constructor
     */
    public NetworkDetail(NetworkDetail networkDetail) {
        this(networkDetail, networkDetail.mANQPElements);
    }

    private static ByteBuffer getAndAdvancePayload(ByteBuffer data, int plLength) {
        ByteBuffer payload = data.duplicate().order(data.order());
        payload.limit(payload.position() + plLength);
        data.position(data.position() + plLength);
        return payload;
    }

    private NetworkDetail(NetworkDetail base, Map<Constants.ANQPElementType, ANQPElement> anqpElements) {
        mSSID = base.mSSID;
        mIsHiddenSsid = base.mIsHiddenSsid;
        mBSSID = base.mBSSID;
        mHESSID = base.mHESSID;
        mStationCount = base.mStationCount;
        mChannelUtilization = base.mChannelUtilization;
        mCapacity = base.mCapacity;
        mAnt = base.mAnt;
        mInternet = base.mInternet;
        mHSRelease = base.mHSRelease;
        mAnqpDomainID = base.mAnqpDomainID;
        mAnqpOICount = base.mAnqpOICount;
        mRoamingConsortiums = base.mRoamingConsortiums;
        mExtendedCapabilities =
                new InformationElementUtil.ExtendedCapabilities(base.mExtendedCapabilities);
        mANQPElements = anqpElements;
        mChannelWidth = base.mChannelWidth;
        mPrimaryFreq = base.mPrimaryFreq;
        mCenterfreq0 = base.mCenterfreq0;
        mCenterfreq1 = base.mCenterfreq1;
        mDtimInterval = base.mDtimInterval;
        mCountryCode = base.mCountryCode;
        mWifiMode = base.mWifiMode;
        mMaxRate = base.mMaxRate;
        mMaxNumberSpatialStreams = base.mMaxNumberSpatialStreams;
        mMboSupported = base.mMboSupported;
        mMboCellularDataAware = base.mMboCellularDataAware;
        mOceSupported = base.mOceSupported;
        mMboAssociationDisallowedReasonCode = base.mMboAssociationDisallowedReasonCode;
        mTwtRequired = base.mTwtRequired;
        mIndividualTwtSupported = base.mIndividualTwtSupported;
        mBroadcastTwtSupported = base.mBroadcastTwtSupported;
        mRestrictedTwtSupported = base.mRestrictedTwtSupported;
        mEpcsPriorityAccessSupported = base.mEpcsPriorityAccessSupported;
        mFilsCapable = base.mFilsCapable;
        mApType6GHz = base.mApType6GHz;
        mIs11azSupported = base.mIs11azSupported;
    }

    public NetworkDetail complete(Map<Constants.ANQPElementType, ANQPElement> anqpElements) {
        return new NetworkDetail(this, anqpElements);
    }

    public boolean queriable(List<Constants.ANQPElementType> queryElements) {
        return mAnt != null &&
                (Constants.hasBaseANQPElements(queryElements) ||
                 Constants.hasR2Elements(queryElements) && mHSRelease == HSRelease.R2);
    }

    public boolean has80211uInfo() {
        return mAnt != null || mRoamingConsortiums != null || mHSRelease != null;
    }

    public boolean hasInterworking() {
        return mAnt != null;
    }

    public String getSSID() {
        return mSSID;
    }

    public String getTrimmedSSID() {
        if (mSSID != null) {
            for (int n = 0; n < mSSID.length(); n++) {
                if (mSSID.charAt(n) != 0) {
                    return mSSID;
                }
            }
        }
        return "";
    }

    public long getHESSID() {
        return mHESSID;
    }

    public long getBSSID() {
        return mBSSID;
    }

    public int getStationCount() {
        return mStationCount;
    }

    public int getChannelUtilization() {
        return mChannelUtilization;
    }

    public int getCapacity() {
        return mCapacity;
    }

    public boolean isInterworking() {
        return mAnt != null;
    }

    public Ant getAnt() {
        return mAnt;
    }

    public boolean isInternet() {
        return mInternet;
    }

    public HSRelease getHSRelease() {
        return mHSRelease;
    }

    public int getAnqpDomainID() {
        return mAnqpDomainID;
    }

    public byte[] getOsuProviders() {
        if (mANQPElements == null) {
            return null;
        }
        ANQPElement osuProviders = mANQPElements.get(Constants.ANQPElementType.HSOSUProviders);
        return osuProviders != null ? ((RawByteElement) osuProviders).getPayload() : null;
    }

    public int getAnqpOICount() {
        return mAnqpOICount;
    }

    public long[] getRoamingConsortiums() {
        return mRoamingConsortiums;
    }

    public Map<Constants.ANQPElementType, ANQPElement> getANQPElements() {
        return mANQPElements;
    }

    public int getChannelWidth() {
        return mChannelWidth;
    }

    public int getCenterfreq0() {
        return mCenterfreq0;
    }

    public int getCenterfreq1() {
        return mCenterfreq1;
    }

    public int getWifiMode() {
        return mWifiMode;
    }

    public int getMaxNumberSpatialStreams() {
        return mMaxNumberSpatialStreams;
    }

    public int getDtimInterval() {
        return mDtimInterval;
    }

    public String getCountryCode() {
        return mCountryCode;
    }

    public boolean is80211McResponderSupport() {
        return mExtendedCapabilities.is80211McRTTResponder();
    }

    public boolean isSSID_UTF8() {
        return mExtendedCapabilities.isStrictUtf8();
    }

    public MacAddress getMldMacAddress() {
        return mMldMacAddress;
    }

    public int getMloLinkId() {
        return mMloLinkId;
    }

    public List<MloLink> getAffiliatedMloLinks() {
        return mAffiliatedMloLinks;
    }

    public byte[] getDisabledSubchannelBitmap() {
        return mDisabledSubchannelBitmap;
    }

    @Override
    public boolean equals(Object thatObject) {
        if (this == thatObject) {
            return true;
        }
        if (thatObject == null || getClass() != thatObject.getClass()) {
            return false;
        }

        NetworkDetail that = (NetworkDetail)thatObject;

        return getSSID().equals(that.getSSID()) && getBSSID() == that.getBSSID();
    }

    @Override
    public int hashCode() {
        return ((mSSID.hashCode() * 31) + (int)(mBSSID >>> 32)) * 31 + (int)mBSSID;
    }

    @Override
    public String toString() {
        return "NetworkInfo{SSID='" + mSSID
                + "', HESSID=" + Utils.macToString(mHESSID)
                + ", BSSID=" + Utils.macToString(mBSSID)
                + ", StationCount=" + mStationCount
                + ", ChannelUtilization=" + mChannelUtilization
                + ", Capacity=" + mCapacity
                + ", Ant=" + mAnt + ", Internet=" + mInternet + ", HSRelease="
                + mHSRelease + ", AnqpDomainID" + mAnqpDomainID + ", AnqpOICount" + mAnqpOICount
                + ", RoamingConsortiums=" + Utils.roamingConsortiumsToString(mRoamingConsortiums)
                + "}";
    }

    public String toKeyString() {
        return mHESSID != 0 ?
                "'" + mSSID + "':" + Utils.macToString(mBSSID) + " ("
                        + Utils.macToString(mHESSID) + ")"
                : "'" + mSSID + "':" + Utils.macToString(mBSSID);
    }

    public String getBSSIDString() {
        return Utils.macToString(mBSSID);
    }

    /**
     * Evaluates the ScanResult this NetworkDetail is built from
     * returns true if built from a Beacon Frame
     * returns false if built from a Probe Response
     */
    public boolean isBeaconFrame() {
        // Beacon frames have a 'Traffic Indication Map' Information element
        // Probe Responses do not. This is indicated by a DTIM period > 0
        return mDtimInterval > 0;
    }

    /**
     * Evaluates the ScanResult this NetworkDetail is built from
     * returns true if built from a hidden Beacon Frame
     * returns false if not hidden or not a Beacon
     */
    public boolean isHiddenBeaconFrame() {
        // Hidden networks are not 80211 standard, but it is common for a hidden network beacon
        // frame to either send zero-value bytes as the SSID, or to send no bytes at all.
        return isBeaconFrame() && mIsHiddenSsid;
    }

    public int getMboAssociationDisallowedReasonCode() {
        return mMboAssociationDisallowedReasonCode;
    }

    public boolean isMboSupported() {
        return mMboSupported;
    }

    public boolean isMboCellularDataAware() {
        return mMboCellularDataAware;
    }

    public boolean isOceSupported() {
        return mOceSupported;
    }

    /** Return whether the AP supports IEEE 802.11az **/
    public boolean is11azSupported() {
        return mIs11azSupported;
    }
    /**
     * Return whether the AP requires HE stations to participate either in individual TWT
     * agreements or Broadcast TWT operation.
     **/
    public boolean isTwtRequired() {
        return mTwtRequired;
    }

    /** Return whether individual TWT is supported. */
    public boolean isIndividualTwtSupported() {
        return mIndividualTwtSupported;
    }

    /** Return whether broadcast TWT is supported */
    public boolean isBroadcastTwtSupported() {
        return mBroadcastTwtSupported;
    }

    /**
     * Returns whether restricted TWT is supported or not. It enables enhanced medium access
     * protection and resource reservation mechanisms for delivery of latency sensitive
     * traffic.
     */
    public boolean isRestrictedTwtSupported() {
        return mRestrictedTwtSupported;
    }

    /**
     * Returns whether EPCS priority access supported or not. EPCS priority access is a
     * mechanism that provides prioritized access to the wireless medium for authorized users to
     * increase their probability of successful communication during periods of network
     * congestion.
     */
    public boolean isEpcsPriorityAccessSupported() {
        return mEpcsPriorityAccessSupported;
    }

    /**
     * @return true if Fast Initial Link Setup (FILS) capable
     */
    public boolean isFilsCapable() {
        return mFilsCapable;
    }

    /**
     * Return 6Ghz AP type as defined in {@link InformationElementUtil.ApType6GHz}
     **/
    public InformationElementUtil.ApType6GHz getApType6GHz() {
        return mApType6GHz;
    }
}
