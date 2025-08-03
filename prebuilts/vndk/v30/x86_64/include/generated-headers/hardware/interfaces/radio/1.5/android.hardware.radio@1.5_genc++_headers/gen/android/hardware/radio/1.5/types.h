#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_5_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_5_TYPES_H

#include <android/hardware/radio/1.0/types.h>
#include <android/hardware/radio/1.1/types.h>
#include <android/hardware/radio/1.2/types.h>
#include <android/hardware/radio/1.4/types.h>
#include <android/hidl/safe_union/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace radio {
namespace V1_5 {

// Forward declaration for forward reference support:
enum class SignalMeasurementType : int32_t;
struct SignalThresholdInfo;
enum class AccessNetwork : int32_t;
enum class RadioAccessNetworks : int32_t;
struct RadioAccessSpecifier;
enum class NgranBands : int32_t;
enum class UtranBands : int32_t;
enum class EutranBands : int32_t;
struct NetworkScanRequest;
enum class ApnTypes : int32_t;
struct DataProfileInfo;
enum class AddressProperty : int32_t;
struct LinkAddress;
struct SetupDataCallResult;
enum class Domain : int32_t;
struct ClosedSubscriberGroupInfo;
struct OptionalCsgInfo;
struct CellIdentityGsm;
struct CellIdentityWcdma;
struct CellIdentityTdscdma;
struct CellIdentityLte;
struct CellIdentityNr;
struct CellInfoGsm;
struct CellInfoWcdma;
struct CellInfoTdscdma;
struct CellInfoLte;
struct CellInfoNr;
struct CellInfo;
struct CellIdentity;
struct BarringInfo;
enum class IndicationFilter : int32_t;
enum class RegistrationFailCause : int32_t;
enum class PrlIndicator : int32_t;
struct RegStateResult;
struct NetworkScanResult;
enum class PersoSubstate : int32_t;
struct AppStatus;
struct CardStatus;

/**
 * Defining signal strength type.
 */
enum class SignalMeasurementType : int32_t {
    /**
     * Received Signal Strength Indication.
     * Range: -113 dBm and -51 dBm
     * Used RAN: GERAN, CDMA2000
     * Reference: 3GPP TS 27.007 section 8.5.
     */
    RSSI = 1,
    /**
     * Received Signal Code Power.
     * Range: -120 dBm to -25 dBm;
     * Used RAN: UTRAN
     * Reference: 3GPP TS 25.123, section 9.1.1.1
     */
    RSCP = 2,
    /**
     * Reference Signal Received Power.
     * Range: -140 dBm to -44 dBm;
     * Used RAN: EUTRAN
     * Reference: 3GPP TS 36.133 9.1.4
     */
    RSRP = 3,
    /**
     * Reference Signal Received Quality
     * Range: -34 dB to 3 dB;
     * Used RAN: EUTRAN
     * Reference: 3GPP TS 36.133 v12.6.0 section 9.1.7
     */
    RSRQ = 4,
    /**
     * Reference Signal Signal to Noise Ratio
     * Range: -20 dB to 30 dB;
     * Used RAN: EUTRAN
     * Note: this field is optional; how to support it can be decided by the
     * corresponding vendor. Though the response code is not enforced,
     * vendor's implementation must ensure this interface not crashing.
     */
    RSSNR = 5,
    /**
     * 5G SS reference signal received power.
     * Range: -140 dBm to -44 dBm.
     * Used RAN: NGRAN
     * Reference: 3GPP TS 38.215.
     */
    SSRSRP = 6,
    /**
     * 5G SS reference signal received quality.
     * Range: -20 dB to -3 dB.
     * Used RAN: NGRAN
     * Reference: 3GPP TS 38.215.
     */
    SSRSRQ = 7,
    /**
     * 5G SS signal-to-noise and interference ratio.
     * Range: -23 dB to 40 dB
     * Used RAN: NGRAN
     * Reference: 3GPP TS 38.215 section 5.1.*, 3GPP TS 38.133 section 10.1.16.1.
     */
    SSSINR = 8,
};

/**
 * Contains the threshold values of each signal measurement type.
 */
struct SignalThresholdInfo final {
    /**
     * Signal Measurement Type
     */
    ::android::hardware::radio::V1_5::SignalMeasurementType signalMeasurement __attribute__ ((aligned(4)));
    /**
     * A hysteresis time in milliseconds to prevent flapping. A value of 0 disables hysteresis.
     */
    int32_t hysteresisMs __attribute__ ((aligned(4)));
    /**
     * An interval in dB defining the required magnitude change between reports.
     * hysteresisDb must be smaller than the smallest threshold delta.
     * An interval value of 0 disables hysteresis.
     */
    int32_t hysteresisDb __attribute__ ((aligned(4)));
    /**
     * List of threshold values.
     * Range and unit must reference specific @1.5::SignalMeasurementType.
     * The threshold values for which to apply criteria.
     * A vector size of 0 disables the use of thresholds for reporting.
     */
    ::android::hardware::hidl_vec<int32_t> thresholds __attribute__ ((aligned(8)));
    /**
     * Indicates whether the reporting criteria of the corresponding measurement is enabled
     * (isEnabled==true) or disabled (isEnabled==false).
     *
     * If enabled, modem must trigger the report based on the criteria.
     * If disabled, modem must not trigger the report based on the criteria.
     */
    bool isEnabled __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::SignalThresholdInfo, signalMeasurement) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::SignalThresholdInfo, hysteresisMs) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::SignalThresholdInfo, hysteresisDb) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::SignalThresholdInfo, thresholds) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::SignalThresholdInfo, isEnabled) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::SignalThresholdInfo) == 40, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::SignalThresholdInfo) == 8, "wrong alignment");

enum class AccessNetwork : int32_t {
    /**
     * GSM EDGE Radio Access Network
     */
    GERAN = 1,
    /**
     * Universal Terrestrial Radio Access Network
     */
    UTRAN = 2,
    /**
     * Evolved Universal Terrestrial Radio Access Network
     */
    EUTRAN = 3,
    /**
     * CDMA 2000 network
     */
    CDMA2000 = 4,
    /**
     * Interworking Wireless LAN
     */
    IWLAN = 5,
    /**
     * Unknown access network
     */
    UNKNOWN = 0,
    /**
     * Next-Generation Radio Access Network (NGRAN).
     * Note NGRAN is only for standalone mode. Non-standalone mode uses AccessNetwork EUTRAN.
     */
    NGRAN = 6,
};

enum class RadioAccessNetworks : int32_t {
    GERAN = 1,
    UTRAN = 2,
    EUTRAN = 3,
    UNKNOWN = 0,
    /**
     * Next Generation Radio Access Network
     */
    NGRAN = 4,
    /**
     * CDMA 2000 Network
     */
    CDMA2000 = 5,
};

/**
 * Overwritten from @1.1::RadioAccessSpecifier to add NGRAN and NgranBands.
 */
struct RadioAccessSpecifier final {
    // Forward declaration for forward reference support:
    struct Bands;

    struct Bands final {
        enum class hidl_discriminator : uint8_t {
            /**
             * Valid only if radioAccessNetwork = GERAN.
             */
            geranBands = 0,  // ::android::hardware::hidl_vec<::android::hardware::radio::V1_1::GeranBands>
            /**
             * Valid only if radioAccessNetwork = UTRAN.
             */
            utranBands = 1,  // ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::UtranBands>
            /**
             * Valid only if radioAccessNetwork = EUTRAN.
             */
            eutranBands = 2,  // ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::EutranBands>
            /**
             * Valid only if radioAccessNetwork = NGRAN.
             */
            ngranBands = 3,  // ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::NgranBands>
        };

        Bands();
        ~Bands();
        Bands(Bands&&);
        Bands(const Bands&);
        Bands& operator=(Bands&&);
        Bands& operator=(const Bands&);

        void geranBands(const ::android::hardware::hidl_vec<::android::hardware::radio::V1_1::GeranBands>&);
        void geranBands(::android::hardware::hidl_vec<::android::hardware::radio::V1_1::GeranBands>&&);
        ::android::hardware::hidl_vec<::android::hardware::radio::V1_1::GeranBands>& geranBands();
        const ::android::hardware::hidl_vec<::android::hardware::radio::V1_1::GeranBands>& geranBands() const;

        void utranBands(const ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::UtranBands>&);
        void utranBands(::android::hardware::hidl_vec<::android::hardware::radio::V1_5::UtranBands>&&);
        ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::UtranBands>& utranBands();
        const ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::UtranBands>& utranBands() const;

        void eutranBands(const ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::EutranBands>&);
        void eutranBands(::android::hardware::hidl_vec<::android::hardware::radio::V1_5::EutranBands>&&);
        ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::EutranBands>& eutranBands();
        const ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::EutranBands>& eutranBands() const;

        void ngranBands(const ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::NgranBands>&);
        void ngranBands(::android::hardware::hidl_vec<::android::hardware::radio::V1_5::NgranBands>&&);
        ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::NgranBands>& ngranBands();
        const ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::NgranBands>& ngranBands() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            ::android::hardware::hidl_vec<::android::hardware::radio::V1_1::GeranBands> geranBands __attribute__ ((aligned(8)));
            ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::UtranBands> utranBands __attribute__ ((aligned(8)));
            ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::EutranBands> eutranBands __attribute__ ((aligned(8)));
            ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::NgranBands> ngranBands __attribute__ ((aligned(8)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands::hidl_union) == 16, "wrong size");
        static_assert(__alignof(::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands::hidl_union) == 8, "wrong alignment");
        static_assert(sizeof(::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands) == 24, "wrong size");
    static_assert(__alignof(::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands) == 8, "wrong alignment");

    /**
     * The type of network to scan.
     */
    ::android::hardware::radio::V1_5::RadioAccessNetworks radioAccessNetwork __attribute__ ((aligned(4)));
    /**
     * The frequency bands to scan.
     * Maximum length of the vector is 8.
     */
    ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands bands __attribute__ ((aligned(8)));
    /**
     * The radio channels to scan as defined in 3GPP TS 25.101 and 36.101.
     * Maximum length of the vector is 32.
     */
    ::android::hardware::hidl_vec<int32_t> channels __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::RadioAccessSpecifier, radioAccessNetwork) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::RadioAccessSpecifier, bands) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::RadioAccessSpecifier, channels) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::RadioAccessSpecifier) == 48, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::RadioAccessSpecifier) == 8, "wrong alignment");

/**
 * IRadio 1.5 supports NGRAN bands up to V16.2.0
 */
enum class NgranBands : int32_t {
    /**
     * 3GPP TS 38.101-1, Table 5.2-1: FR1 bands
     */
    BAND_1 = 1,
    BAND_2 = 2,
    BAND_3 = 3,
    BAND_5 = 5,
    BAND_7 = 7,
    BAND_8 = 8,
    BAND_12 = 12,
    BAND_14 = 14,
    BAND_18 = 18,
    BAND_20 = 20,
    BAND_25 = 25,
    BAND_28 = 28,
    BAND_29 = 29,
    BAND_30 = 30,
    BAND_34 = 34,
    BAND_38 = 38,
    BAND_39 = 39,
    BAND_40 = 40,
    BAND_41 = 41,
    BAND_48 = 48,
    BAND_50 = 50,
    BAND_51 = 51,
    BAND_65 = 65,
    BAND_66 = 66,
    BAND_70 = 70,
    BAND_71 = 71,
    BAND_74 = 74,
    BAND_75 = 75,
    BAND_76 = 76,
    BAND_77 = 77,
    BAND_78 = 78,
    BAND_79 = 79,
    BAND_80 = 80,
    BAND_81 = 81,
    BAND_82 = 82,
    BAND_83 = 83,
    BAND_84 = 84,
    BAND_86 = 86,
    BAND_89 = 89,
    BAND_90 = 90,
    BAND_91 = 91,
    BAND_92 = 92,
    BAND_93 = 93,
    BAND_94 = 94,
    BAND_95 = 95,
    /**
     * 3GPP TS 38.101-2, Table 5.2-1: FR2 bands
     */
    BAND_257 = 257,
    BAND_258 = 258,
    BAND_260 = 260,
    BAND_261 = 261,
};

/**
 * Extended from @1.1 UtranBands to add TD-SCDMA bands
 * IRadio 1.5 supports UTRAN bands up to V15.0.0
 */
enum class UtranBands : int32_t {
    BAND_1 = 1,
    BAND_2 = 2,
    BAND_3 = 3,
    BAND_4 = 4,
    BAND_5 = 5,
    BAND_6 = 6,
    BAND_7 = 7,
    BAND_8 = 8,
    BAND_9 = 9,
    BAND_10 = 10,
    BAND_11 = 11,
    BAND_12 = 12,
    BAND_13 = 13,
    BAND_14 = 14,
    BAND_19 = 19,
    BAND_20 = 20,
    BAND_21 = 21,
    BAND_22 = 22,
    BAND_25 = 25,
    BAND_26 = 26,
    /**
     * TD-SCDMA bands. 3GPP TS 25.102, Table 5.2: Frequency bands
     */
    BAND_A = 101,
    BAND_B = 102,
    BAND_C = 103,
    BAND_D = 104,
    BAND_E = 105,
    BAND_F = 106,
};

/**
 * Extended from @1.1 EutranBands to add more bands from 3GPP TS 36.101, Table 5.5: Operating bands
 * IRadio 1.5 supports EUTRAN bands up to V16.4.0
 */
enum class EutranBands : int32_t {
    BAND_1 = 1,
    BAND_2 = 2,
    BAND_3 = 3,
    BAND_4 = 4,
    BAND_5 = 5,
    BAND_6 = 6,
    BAND_7 = 7,
    BAND_8 = 8,
    BAND_9 = 9,
    BAND_10 = 10,
    BAND_11 = 11,
    BAND_12 = 12,
    BAND_13 = 13,
    BAND_14 = 14,
    BAND_17 = 17,
    BAND_18 = 18,
    BAND_19 = 19,
    BAND_20 = 20,
    BAND_21 = 21,
    BAND_22 = 22,
    BAND_23 = 23,
    BAND_24 = 24,
    BAND_25 = 25,
    BAND_26 = 26,
    BAND_27 = 27,
    BAND_28 = 28,
    BAND_30 = 30,
    BAND_31 = 31,
    BAND_33 = 33,
    BAND_34 = 34,
    BAND_35 = 35,
    BAND_36 = 36,
    BAND_37 = 37,
    BAND_38 = 38,
    BAND_39 = 39,
    BAND_40 = 40,
    BAND_41 = 41,
    BAND_42 = 42,
    BAND_43 = 43,
    BAND_44 = 44,
    BAND_45 = 45,
    BAND_46 = 46,
    BAND_47 = 47,
    BAND_48 = 48,
    BAND_65 = 65,
    BAND_66 = 66,
    BAND_68 = 68,
    BAND_70 = 70,
    BAND_49 = 49,
    BAND_50 = 50,
    BAND_51 = 51,
    BAND_52 = 52,
    BAND_53 = 53,
    BAND_71 = 71,
    BAND_72 = 72,
    BAND_73 = 73,
    BAND_74 = 74,
    BAND_85 = 85,
    BAND_87 = 87,
    BAND_88 = 88,
};

/**
 * Overwritten from @1.2::NetworkScanRequest to update RadioAccessSpecifier to 1.5 version.
 */
struct NetworkScanRequest final {
    ::android::hardware::radio::V1_1::ScanType type __attribute__ ((aligned(4)));
    /**
     * Time interval in seconds between the completion of one scan and the start of
     * a subsequent scan.
     * Implementations may ignore this field unless the 'type' is 'PERIODIC'.
     * Range: ScanIntervalRange:MIN to ScanIntervalRange:MAX
     */
    int32_t interval __attribute__ ((aligned(4)));
    /**
     * Networks with bands/channels to scan
     * Maximum length of the vector is RadioConst:RADIO_ACCESS_SPECIFIER_MAX_SIZE
     */
    ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::RadioAccessSpecifier> specifiers __attribute__ ((aligned(8)));
    /**
     * Maximum duration of the periodic search (in seconds).
     * If the search lasts maxSearchTime, it must be terminated.
     * Range: MaxSearchTimeRange:MIN to MaxSearchTimeRange:MAX
     */
    int32_t maxSearchTime __attribute__ ((aligned(4)));
    /**
     * Indicates whether the modem must report incremental results of the network scan
     * to the client.
     * FALSE – Incremental results must not be reported.
     * TRUE  – Incremental must be reported.
     */
    bool incrementalResults __attribute__ ((aligned(1)));
    /**
     * Indicates the periodicity with which the modem must report incremental results to
     * the client (in seconds).
     * Implementations may ignore this value if the incremental results are not requested.
     * This value must be less than or equal to maxSearchTime.
     * Range: IncrementalResultsPeriodicityRange:MIN to IncrementalResultsPeriodicityRange:MAX
     */
    int32_t incrementalResultsPeriodicity __attribute__ ((aligned(4)));
    /**
     * Describes the List of PLMN ids (MCC-MNC)
     * If any PLMN of this list is found, search must end at that point and results with all
     * PLMN found until that point should be sent as response.
     * If the list is not sent, search to be completed until end and all PLMNs found to be
     * reported.
     */
    ::android::hardware::hidl_vec<::android::hardware::hidl_string> mccMncs __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::NetworkScanRequest, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::NetworkScanRequest, interval) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::NetworkScanRequest, specifiers) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::NetworkScanRequest, maxSearchTime) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::NetworkScanRequest, incrementalResults) == 28, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::NetworkScanRequest, incrementalResultsPeriodicity) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::NetworkScanRequest, mccMncs) == 40, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::NetworkScanRequest) == 56, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::NetworkScanRequest) == 8, "wrong alignment");

enum class ApnTypes : int32_t {
    NONE = 0,
    DEFAULT = 1 /* 1 << 0 */,
    MMS = 2 /* 1 << 1 */,
    SUPL = 4 /* 1 << 2 */,
    DUN = 8 /* 1 << 3 */,
    HIPRI = 16 /* 1 << 4 */,
    FOTA = 32 /* 1 << 5 */,
    IMS = 64 /* 1 << 6 */,
    CBS = 128 /* 1 << 7 */,
    IA = 256 /* 1 << 8 */,
    EMERGENCY = 512 /* 1 << 9 */,
    ALL = 1023 /* DEFAULT | MMS | SUPL | DUN | HIPRI | FOTA | IMS | CBS | IA | EMERGENCY */,
    /**
     * Due to the addition of this new value, the value ALL defined in 1.0::ApnTypes is now
     * deprecated and should not be used.
     */
    MCX = 1024 /* 1 << 10 */,
    /**
     * APN type for XCAP
     * NOTE: Due to the addition of this new value, the value ALL defined in
     * 1.0::ApnTypes is deprecated and should not be used.
     */
    XCAP = 2048 /* 1 << 11 */,
};

/**
 * Overwritten from @1.4::DataProfileInfo to update ApnTypes to 1.5 version and replace mtu with
 * mtuV4 and mtuV6. In the future, this must be extended instead of overwritten.
 */
struct DataProfileInfo final {
    /**
     * ID of the data profile.
     */
    ::android::hardware::radio::V1_0::DataProfileId profileId __attribute__ ((aligned(4)));
    /**
     * The APN name.
     */
    ::android::hardware::hidl_string apn __attribute__ ((aligned(8)));
    /**
     * PDP_type values.
     */
    ::android::hardware::radio::V1_4::PdpProtocolType protocol __attribute__ ((aligned(4)));
    /**
     * PDP_type values used on roaming network.
     */
    ::android::hardware::radio::V1_4::PdpProtocolType roamingProtocol __attribute__ ((aligned(4)));
    /**
     * APN authentication type.
     */
    ::android::hardware::radio::V1_0::ApnAuthType authType __attribute__ ((aligned(4)));
    /**
     * The username for APN, or empty string.
     */
    ::android::hardware::hidl_string user __attribute__ ((aligned(8)));
    /**
     * The password for APN, or empty string.
     */
    ::android::hardware::hidl_string password __attribute__ ((aligned(8)));
    /**
     * Data profile technology type.
     */
    ::android::hardware::radio::V1_0::DataProfileInfoType type __attribute__ ((aligned(4)));
    /**
     * The period in seconds to limit the maximum connections.
     */
    int32_t maxConnsTime __attribute__ ((aligned(4)));
    /**
     * The maximum connections during maxConnsTime.
     */
    int32_t maxConns __attribute__ ((aligned(4)));
    /**
     * The required wait time in seconds after a successful UE initiated disconnect of a given PDN
     * connection before the device can send a new PDN connection request for that given PDN.
     */
    int32_t waitTime __attribute__ ((aligned(4)));
    /**
     * True to enable the profile, false to disable.
     */
    bool enabled __attribute__ ((aligned(1)));
    /**
     * Supported APN types bitmap. See ApnTypes for the value of each bit.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::ApnTypes> supportedApnTypesBitmap __attribute__ ((aligned(4)));
    /**
     * The bearer bitmap. See RadioAccessFamily for the value of each bit.
     */
    ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_4::RadioAccessFamily> bearerBitmap __attribute__ ((aligned(4)));
    /**
     * Maximum transmission unit (MTU) size in bytes for IPv4.
     */
    int32_t mtuV4 __attribute__ ((aligned(4)));
    /**
     * Maximum transmission unit (MTU) size in bytes for IPv6.
     */
    int32_t mtuV6 __attribute__ ((aligned(4)));
    /**
     * True if this data profile was used to bring up the last default (i.e internet) data
     * connection successfully.
     */
    bool preferred __attribute__ ((aligned(1)));
    /**
     * If true, modem must persist this data profile and profileId must not be
     * set to DataProfileId.INVALID. If the same data profile exists, this data profile must
     * overwrite it.
     */
    bool persistent __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::DataProfileInfo, profileId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::DataProfileInfo, apn) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::DataProfileInfo, protocol) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::DataProfileInfo, roamingProtocol) == 28, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::DataProfileInfo, authType) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::DataProfileInfo, user) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::DataProfileInfo, password) == 56, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::DataProfileInfo, type) == 72, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::DataProfileInfo, maxConnsTime) == 76, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::DataProfileInfo, maxConns) == 80, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::DataProfileInfo, waitTime) == 84, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::DataProfileInfo, enabled) == 88, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::DataProfileInfo, supportedApnTypesBitmap) == 92, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::DataProfileInfo, bearerBitmap) == 96, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::DataProfileInfo, mtuV4) == 100, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::DataProfileInfo, mtuV6) == 104, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::DataProfileInfo, preferred) == 108, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::DataProfileInfo, persistent) == 109, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::DataProfileInfo) == 112, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::DataProfileInfo) == 8, "wrong alignment");

/**
 * The properties of the link address. This enum reflects the definition in
 * if_addr.h in Linux kernel.
 */
enum class AddressProperty : int32_t {
    NONE = 0,
    /**
     * Indicates this address is deprecated
     */
    DEPRECATED = 32 /* 0x20 */,
};

/**
 * Describes a data link address for mobile data connection.
 */
struct LinkAddress final {
    /**
     * The format is IP address with optional "/"
     * prefix length (The format is defined in RFC-4291 section 2.3). For example, "192.0.1.3",
     * "192.0.1.11/16", or "2001:db8::1/64". Typically one IPv4 or one IPv6 or one of each. If
     * the prefix length is absent, then the addresses are assumed to be point to point with
     * IPv4 with prefix length 32 or IPv6 with prefix length 128.
     */
    ::android::hardware::hidl_string address __attribute__ ((aligned(8)));
    /**
     * The properties of the link address
     */
    ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::AddressProperty> properties __attribute__ ((aligned(4)));
    /**
     * The time, as reported by SystemClock.elapsedRealtime(), when this link address will be or
     * was deprecated. -1 indicates this information is not available. At the time existing
     * connections can still use this address until it expires, but new connections should use the
     * new address. LONG_MAX(0x7FFFFFFFFFFFFFFF) indicates this link address will never be
     * deprecated.
     */
    uint64_t deprecationTime __attribute__ ((aligned(8)));
    /**
     * The time, as reported by SystemClock.elapsedRealtime(), when this link address will expire
     * and be removed from the interface. -1 indicates this information is not available.
     * LONG_MAX(0x7FFFFFFFFFFFFFFF) indicates this link address will never expire.
     */
    uint64_t expirationTime __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::LinkAddress, address) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::LinkAddress, properties) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::LinkAddress, deprecationTime) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::LinkAddress, expirationTime) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::LinkAddress) == 40, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::LinkAddress) == 8, "wrong alignment");

/**
 * Overwritten from @1.4::SetupDataCallResult in order to update the addresses to 1.5 version.
 * In 1.5 the type of addresses changes to vector of LinkAddress, and mtu is replaced by
 * mtuV4 and mtuV6.
 */
struct SetupDataCallResult final {
    /**
     * Data call fail cause. DataCallFailCause.NONE if no error.
     */
    ::android::hardware::radio::V1_4::DataCallFailCause cause __attribute__ ((aligned(4)));
    /**
     * If status != DataCallFailCause.NONE, this field indicates the suggested retry back-off timer
     * value RIL wants to override the one pre-configured in FW. The unit is milliseconds.
     * The value < 0 means no value is suggested.
     * The value 0 means retry must be done ASAP.
     * The value of INT_MAX(0x7fffffff) means no retry.
     */
    int32_t suggestedRetryTime __attribute__ ((aligned(4)));
    /**
     * Context ID, uniquely identifies this call.
     */
    int32_t cid __attribute__ ((aligned(4)));
    /**
     * Data connection active status.
     */
    ::android::hardware::radio::V1_4::DataConnActiveStatus active __attribute__ ((aligned(4)));
    /**
     * PDP_type values. If cause is DataCallFailCause.ONLY_SINGLE_BEARER_ALLOWED, this is the type
     * supported such as "IP" or "IPV6".
     */
    ::android::hardware::radio::V1_4::PdpProtocolType type __attribute__ ((aligned(4)));
    /**
     * The network interface name.
     */
    ::android::hardware::hidl_string ifname __attribute__ ((aligned(8)));
    /**
     * List of link address.
     */
    ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::LinkAddress> addresses __attribute__ ((aligned(8)));
    /**
     * List of DNS server addresses, e.g., "192.0.1.3" or "192.0.1.11 2001:db8::1". Empty if no dns
     * server addresses returned.
     */
    ::android::hardware::hidl_vec<::android::hardware::hidl_string> dnses __attribute__ ((aligned(8)));
    /**
     * List of default gateway addresses, e.g., "192.0.1.3" or "192.0.1.11 2001:db8::1".
     * When empty, the addresses represent point to point connections.
     */
    ::android::hardware::hidl_vec<::android::hardware::hidl_string> gateways __attribute__ ((aligned(8)));
    /**
     * List of P-CSCF(Proxy Call State Control Function) addresses via PCO(Protocol Configuration
     * Option), e.g., "2001:db8::1 2001:db8::2 2001:db8::3". Empty if not IMS client.
     */
    ::android::hardware::hidl_vec<::android::hardware::hidl_string> pcscf __attribute__ ((aligned(8)));
    /**
     * MTU received from network for IPv4.
     * Value <= 0 means network has either not sent a value or sent an invalid value.
     */
    int32_t mtuV4 __attribute__ ((aligned(4)));
    /**
     * MTU received from network for IPv6.
     * Value <= 0 means network has either not sent a value or sent an invalid value.
     */
    int32_t mtuV6 __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::SetupDataCallResult, cause) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::SetupDataCallResult, suggestedRetryTime) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::SetupDataCallResult, cid) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::SetupDataCallResult, active) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::SetupDataCallResult, type) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::SetupDataCallResult, ifname) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::SetupDataCallResult, addresses) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::SetupDataCallResult, dnses) == 56, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::SetupDataCallResult, gateways) == 72, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::SetupDataCallResult, pcscf) == 88, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::SetupDataCallResult, mtuV4) == 104, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::SetupDataCallResult, mtuV6) == 108, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::SetupDataCallResult) == 112, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::SetupDataCallResult) == 8, "wrong alignment");

enum class Domain : int32_t {
    /**
     * Circuit-switched
     */
    CS = 1 /* 1 << 0 */,
    /**
     * Packet-switched
     */
    PS = 2 /* 1 << 1 */,
};

struct ClosedSubscriberGroupInfo final {
    /**
     * Indicates whether the cell is restricted to only CSG members. A cell not broadcasting the
     * CSG Indication but reporting CSG information is considered a Hybrid Cell.
     * Refer to the "csg-Indication" field in 3GPP TS 36.331 section 6.2.2
     * SystemInformationBlockType1.
     * Also refer to "CSG Indicator" in 3GPP TS 25.331 section 10.2.48.8.1 and TS 25.304.
     */
    bool csgIndication __attribute__ ((aligned(1)));
    /**
     * The human-readable name of the closed subscriber group operating this cell.
     * Refer to "hnb-Name" in TS 36.331 section 6.2.2 SystemInformationBlockType9.
     * Also refer to "HNB Name" in 3GPP TS25.331 section 10.2.48.8.23 and TS 23.003 section 4.8.
     */
    ::android::hardware::hidl_string homeNodebName __attribute__ ((aligned(8)));
    /**
     * The identity of the closed subscriber group that the cell belongs to.
     * Refer to "CSG-Identity" in TS 36.336 section 6.3.4.
     * Also refer to "CSG Identity" in 3GPP TS 25.331 section 10.3.2.8 and TS 23.003 section 4.7.
     */
    int32_t csgIdentity __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo, csgIndication) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo, homeNodebName) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo, csgIdentity) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo) == 32, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo) == 8, "wrong alignment");

struct OptionalCsgInfo final {
    enum class hidl_discriminator : uint8_t {
        /**
         * If no CSG info is provided by the cell, then this structure shall be present.
         */
        noinit = 0,  // ::android::hidl::safe_union::V1_0::Monostate
        /**
         * If CSG info is provided by the cell, this structure shall be present.
         */
        csgInfo = 1,  // ::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo
    };

    OptionalCsgInfo();
    ~OptionalCsgInfo();
    OptionalCsgInfo(OptionalCsgInfo&&);
    OptionalCsgInfo(const OptionalCsgInfo&);
    OptionalCsgInfo& operator=(OptionalCsgInfo&&);
    OptionalCsgInfo& operator=(const OptionalCsgInfo&);

    void noinit(const ::android::hidl::safe_union::V1_0::Monostate&);
    void noinit(::android::hidl::safe_union::V1_0::Monostate&&);
    ::android::hidl::safe_union::V1_0::Monostate& noinit();
    const ::android::hidl::safe_union::V1_0::Monostate& noinit() const;

    void csgInfo(const ::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo&);
    void csgInfo(::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo&&);
    ::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo& csgInfo();
    const ::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo& csgInfo() const;

    // Utility methods
    hidl_discriminator getDiscriminator() const;

    constexpr size_t hidl_getUnionOffset() const {
        return offsetof(::android::hardware::radio::V1_5::OptionalCsgInfo, hidl_u);
    }

private:
    void hidl_destructUnion();

    hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
    union hidl_union final {
        ::android::hidl::safe_union::V1_0::Monostate noinit __attribute__ ((aligned(1)));
        ::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo csgInfo __attribute__ ((aligned(8)));

        hidl_union();
        ~hidl_union();
    } hidl_u;

    static_assert(sizeof(::android::hardware::radio::V1_5::OptionalCsgInfo::hidl_union) == 32, "wrong size");
    static_assert(__alignof(::android::hardware::radio::V1_5::OptionalCsgInfo::hidl_union) == 8, "wrong alignment");
    static_assert(sizeof(::android::hardware::radio::V1_5::OptionalCsgInfo::hidl_discriminator) == 1, "wrong size");
    static_assert(__alignof(::android::hardware::radio::V1_5::OptionalCsgInfo::hidl_discriminator) == 1, "wrong alignment");
};

static_assert(sizeof(::android::hardware::radio::V1_5::OptionalCsgInfo) == 40, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::OptionalCsgInfo) == 8, "wrong alignment");

struct CellIdentityGsm final {
    /**
     * The fields "mcc" and "mnc" must contain the PLMN-ID of the primary PLMN of this cell.
     */
    ::android::hardware::radio::V1_2::CellIdentityGsm base __attribute__ ((aligned(8)));
    /**
     * Additional PLMN-IDs beyond the primary PLMN broadcast for this cell
     */
    ::android::hardware::hidl_vec<::android::hardware::hidl_string> additionalPlmns __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::CellIdentityGsm, base) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CellIdentityGsm, additionalPlmns) == 80, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::CellIdentityGsm) == 96, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::CellIdentityGsm) == 8, "wrong alignment");

struct CellIdentityWcdma final {
    /**
     * The fields "mcc" and "mnc" must contain the PLMN-ID of the primary PLMN of this cell.
     */
    ::android::hardware::radio::V1_2::CellIdentityWcdma base __attribute__ ((aligned(8)));
    /**
     * Additional PLMN-IDs beyond the primary PLMN broadcast for this cell
     */
    ::android::hardware::hidl_vec<::android::hardware::hidl_string> additionalPlmns __attribute__ ((aligned(8)));
    /**
     * Information about any closed subscriber group ID for this cell
     */
    ::android::hardware::radio::V1_5::OptionalCsgInfo optionalCsgInfo __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::CellIdentityWcdma, base) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CellIdentityWcdma, additionalPlmns) == 80, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CellIdentityWcdma, optionalCsgInfo) == 96, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::CellIdentityWcdma) == 136, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::CellIdentityWcdma) == 8, "wrong alignment");

struct CellIdentityTdscdma final {
    /**
     * The fields "mcc" and "mnc" must contain the PLMN-ID of the primary PLMN of this cell.
     */
    ::android::hardware::radio::V1_2::CellIdentityTdscdma base __attribute__ ((aligned(8)));
    /**
     * Additional PLMN-IDs beyond the primary PLMN broadcast for this cell
     */
    ::android::hardware::hidl_vec<::android::hardware::hidl_string> additionalPlmns __attribute__ ((aligned(8)));
    /**
     * Information about any closed subscriber group ID for this cell
     */
    ::android::hardware::radio::V1_5::OptionalCsgInfo optionalCsgInfo __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::CellIdentityTdscdma, base) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CellIdentityTdscdma, additionalPlmns) == 88, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CellIdentityTdscdma, optionalCsgInfo) == 104, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::CellIdentityTdscdma) == 144, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::CellIdentityTdscdma) == 8, "wrong alignment");

struct CellIdentityLte final {
    /**
     * The fields "mcc" and "mnc" must contain the PLMN-ID of the primary PLMN of this cell.
     */
    ::android::hardware::radio::V1_2::CellIdentityLte base __attribute__ ((aligned(8)));
    /**
     * Additional PLMN-IDs beyond the primary PLMN broadcast for this cell
     */
    ::android::hardware::hidl_vec<::android::hardware::hidl_string> additionalPlmns __attribute__ ((aligned(8)));
    /**
     * Information about any closed subscriber group ID for this cell
     */
    ::android::hardware::radio::V1_5::OptionalCsgInfo optionalCsgInfo __attribute__ ((aligned(8)));
    /**
     * Bands used by the cell.
     */
    ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::EutranBands> bands __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::CellIdentityLte, base) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CellIdentityLte, additionalPlmns) == 88, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CellIdentityLte, optionalCsgInfo) == 104, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CellIdentityLte, bands) == 144, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::CellIdentityLte) == 160, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::CellIdentityLte) == 8, "wrong alignment");

/**
 * The CellIdentity structure should be reported once for each element of the PLMN-IdentityInfoList
 * broadcast in SIB1 CellAccessRelatedInfo as per 3GPP TS 38.331 Section 6.3.2.
 */
struct CellIdentityNr final {
    /**
     * The fields "mcc" and "mnc" must contain the PLMN-ID of the primary PLMN of this cell.
     */
    ::android::hardware::radio::V1_4::CellIdentityNr base __attribute__ ((aligned(8)));
    /**
     * Additional PLMN-IDs beyond the primary PLMN broadcast for this cell
     */
    ::android::hardware::hidl_vec<::android::hardware::hidl_string> additionalPlmns __attribute__ ((aligned(8)));
    /**
     * Bands used by the cell.
     */
    ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::NgranBands> bands __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::CellIdentityNr, base) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CellIdentityNr, additionalPlmns) == 88, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CellIdentityNr, bands) == 104, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::CellIdentityNr) == 120, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::CellIdentityNr) == 8, "wrong alignment");

struct CellInfoGsm final {
    ::android::hardware::radio::V1_5::CellIdentityGsm cellIdentityGsm __attribute__ ((aligned(8)));
    ::android::hardware::radio::V1_0::GsmSignalStrength signalStrengthGsm __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::CellInfoGsm, cellIdentityGsm) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CellInfoGsm, signalStrengthGsm) == 96, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::CellInfoGsm) == 112, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::CellInfoGsm) == 8, "wrong alignment");

struct CellInfoWcdma final {
    ::android::hardware::radio::V1_5::CellIdentityWcdma cellIdentityWcdma __attribute__ ((aligned(8)));
    ::android::hardware::radio::V1_2::WcdmaSignalStrength signalStrengthWcdma __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::CellInfoWcdma, cellIdentityWcdma) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CellInfoWcdma, signalStrengthWcdma) == 136, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::CellInfoWcdma) == 152, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::CellInfoWcdma) == 8, "wrong alignment");

struct CellInfoTdscdma final {
    ::android::hardware::radio::V1_5::CellIdentityTdscdma cellIdentityTdscdma __attribute__ ((aligned(8)));
    ::android::hardware::radio::V1_2::TdscdmaSignalStrength signalStrengthTdscdma __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::CellInfoTdscdma, cellIdentityTdscdma) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CellInfoTdscdma, signalStrengthTdscdma) == 144, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::CellInfoTdscdma) == 160, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::CellInfoTdscdma) == 8, "wrong alignment");

struct CellInfoLte final {
    ::android::hardware::radio::V1_5::CellIdentityLte cellIdentityLte __attribute__ ((aligned(8)));
    ::android::hardware::radio::V1_0::LteSignalStrength signalStrengthLte __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::CellInfoLte, cellIdentityLte) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CellInfoLte, signalStrengthLte) == 160, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::CellInfoLte) == 184, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::CellInfoLte) == 8, "wrong alignment");

struct CellInfoNr final {
    ::android::hardware::radio::V1_5::CellIdentityNr cellIdentityNr __attribute__ ((aligned(8)));
    ::android::hardware::radio::V1_4::NrSignalStrength signalStrengthNr __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::CellInfoNr, cellIdentityNr) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CellInfoNr, signalStrengthNr) == 120, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::CellInfoNr) == 144, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::CellInfoNr) == 8, "wrong alignment");

struct CellInfo final {
    // Forward declaration for forward reference support:
    struct CellInfoRatSpecificInfo;

    struct CellInfoRatSpecificInfo final {
        enum class hidl_discriminator : uint8_t {
            /**
             * 3gpp CellInfo types.
             */
            gsm = 0,  // ::android::hardware::radio::V1_5::CellInfoGsm
            wcdma = 1,  // ::android::hardware::radio::V1_5::CellInfoWcdma
            tdscdma = 2,  // ::android::hardware::radio::V1_5::CellInfoTdscdma
            lte = 3,  // ::android::hardware::radio::V1_5::CellInfoLte
            nr = 4,  // ::android::hardware::radio::V1_5::CellInfoNr
            /**
             * 3gpp2 CellInfo types;
             */
            cdma = 5,  // ::android::hardware::radio::V1_2::CellInfoCdma
        };

        CellInfoRatSpecificInfo();
        ~CellInfoRatSpecificInfo();
        CellInfoRatSpecificInfo(CellInfoRatSpecificInfo&&);
        CellInfoRatSpecificInfo(const CellInfoRatSpecificInfo&);
        CellInfoRatSpecificInfo& operator=(CellInfoRatSpecificInfo&&);
        CellInfoRatSpecificInfo& operator=(const CellInfoRatSpecificInfo&);

        void gsm(const ::android::hardware::radio::V1_5::CellInfoGsm&);
        void gsm(::android::hardware::radio::V1_5::CellInfoGsm&&);
        ::android::hardware::radio::V1_5::CellInfoGsm& gsm();
        const ::android::hardware::radio::V1_5::CellInfoGsm& gsm() const;

        void wcdma(const ::android::hardware::radio::V1_5::CellInfoWcdma&);
        void wcdma(::android::hardware::radio::V1_5::CellInfoWcdma&&);
        ::android::hardware::radio::V1_5::CellInfoWcdma& wcdma();
        const ::android::hardware::radio::V1_5::CellInfoWcdma& wcdma() const;

        void tdscdma(const ::android::hardware::radio::V1_5::CellInfoTdscdma&);
        void tdscdma(::android::hardware::radio::V1_5::CellInfoTdscdma&&);
        ::android::hardware::radio::V1_5::CellInfoTdscdma& tdscdma();
        const ::android::hardware::radio::V1_5::CellInfoTdscdma& tdscdma() const;

        void lte(const ::android::hardware::radio::V1_5::CellInfoLte&);
        void lte(::android::hardware::radio::V1_5::CellInfoLte&&);
        ::android::hardware::radio::V1_5::CellInfoLte& lte();
        const ::android::hardware::radio::V1_5::CellInfoLte& lte() const;

        void nr(const ::android::hardware::radio::V1_5::CellInfoNr&);
        void nr(::android::hardware::radio::V1_5::CellInfoNr&&);
        ::android::hardware::radio::V1_5::CellInfoNr& nr();
        const ::android::hardware::radio::V1_5::CellInfoNr& nr() const;

        void cdma(const ::android::hardware::radio::V1_2::CellInfoCdma&);
        void cdma(::android::hardware::radio::V1_2::CellInfoCdma&&);
        ::android::hardware::radio::V1_2::CellInfoCdma& cdma();
        const ::android::hardware::radio::V1_2::CellInfoCdma& cdma() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            ::android::hardware::radio::V1_5::CellInfoGsm gsm __attribute__ ((aligned(8)));
            ::android::hardware::radio::V1_5::CellInfoWcdma wcdma __attribute__ ((aligned(8)));
            ::android::hardware::radio::V1_5::CellInfoTdscdma tdscdma __attribute__ ((aligned(8)));
            ::android::hardware::radio::V1_5::CellInfoLte lte __attribute__ ((aligned(8)));
            ::android::hardware::radio::V1_5::CellInfoNr nr __attribute__ ((aligned(8)));
            ::android::hardware::radio::V1_2::CellInfoCdma cdma __attribute__ ((aligned(8)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo::hidl_union) == 184, "wrong size");
        static_assert(__alignof(::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo::hidl_union) == 8, "wrong alignment");
        static_assert(sizeof(::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo) == 192, "wrong size");
    static_assert(__alignof(::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo) == 8, "wrong alignment");

    /**
     * True if this cell is registered false if not registered.
     */
    bool registered __attribute__ ((aligned(1)));
    /**
     * Type of time stamp represented by timeStamp.
     */
    ::android::hardware::radio::V1_0::TimeStampType timeStampType __attribute__ ((aligned(4)));
    /**
     * Time in nanos as returned by ril_nano_time.
     */
    uint64_t timeStamp __attribute__ ((aligned(8)));
    /**
     * Connection status for the cell.
     */
    ::android::hardware::radio::V1_2::CellConnectionStatus connectionStatus __attribute__ ((aligned(4)));
    ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo ratSpecificInfo __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::CellInfo, registered) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CellInfo, timeStampType) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CellInfo, timeStamp) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CellInfo, connectionStatus) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CellInfo, ratSpecificInfo) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::CellInfo) == 216, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::CellInfo) == 8, "wrong alignment");

/**
 * A union representing the CellIdentity of a single cell.
 */
struct CellIdentity final {
    enum class hidl_discriminator : uint8_t {
        noinit = 0,  // ::android::hidl::safe_union::V1_0::Monostate
        gsm = 1,  // ::android::hardware::radio::V1_5::CellIdentityGsm
        wcdma = 2,  // ::android::hardware::radio::V1_5::CellIdentityWcdma
        tdscdma = 3,  // ::android::hardware::radio::V1_5::CellIdentityTdscdma
        cdma = 4,  // ::android::hardware::radio::V1_2::CellIdentityCdma
        lte = 5,  // ::android::hardware::radio::V1_5::CellIdentityLte
        nr = 6,  // ::android::hardware::radio::V1_5::CellIdentityNr
    };

    CellIdentity();
    ~CellIdentity();
    CellIdentity(CellIdentity&&);
    CellIdentity(const CellIdentity&);
    CellIdentity& operator=(CellIdentity&&);
    CellIdentity& operator=(const CellIdentity&);

    void noinit(const ::android::hidl::safe_union::V1_0::Monostate&);
    void noinit(::android::hidl::safe_union::V1_0::Monostate&&);
    ::android::hidl::safe_union::V1_0::Monostate& noinit();
    const ::android::hidl::safe_union::V1_0::Monostate& noinit() const;

    void gsm(const ::android::hardware::radio::V1_5::CellIdentityGsm&);
    void gsm(::android::hardware::radio::V1_5::CellIdentityGsm&&);
    ::android::hardware::radio::V1_5::CellIdentityGsm& gsm();
    const ::android::hardware::radio::V1_5::CellIdentityGsm& gsm() const;

    void wcdma(const ::android::hardware::radio::V1_5::CellIdentityWcdma&);
    void wcdma(::android::hardware::radio::V1_5::CellIdentityWcdma&&);
    ::android::hardware::radio::V1_5::CellIdentityWcdma& wcdma();
    const ::android::hardware::radio::V1_5::CellIdentityWcdma& wcdma() const;

    void tdscdma(const ::android::hardware::radio::V1_5::CellIdentityTdscdma&);
    void tdscdma(::android::hardware::radio::V1_5::CellIdentityTdscdma&&);
    ::android::hardware::radio::V1_5::CellIdentityTdscdma& tdscdma();
    const ::android::hardware::radio::V1_5::CellIdentityTdscdma& tdscdma() const;

    void cdma(const ::android::hardware::radio::V1_2::CellIdentityCdma&);
    void cdma(::android::hardware::radio::V1_2::CellIdentityCdma&&);
    ::android::hardware::radio::V1_2::CellIdentityCdma& cdma();
    const ::android::hardware::radio::V1_2::CellIdentityCdma& cdma() const;

    void lte(const ::android::hardware::radio::V1_5::CellIdentityLte&);
    void lte(::android::hardware::radio::V1_5::CellIdentityLte&&);
    ::android::hardware::radio::V1_5::CellIdentityLte& lte();
    const ::android::hardware::radio::V1_5::CellIdentityLte& lte() const;

    void nr(const ::android::hardware::radio::V1_5::CellIdentityNr&);
    void nr(::android::hardware::radio::V1_5::CellIdentityNr&&);
    ::android::hardware::radio::V1_5::CellIdentityNr& nr();
    const ::android::hardware::radio::V1_5::CellIdentityNr& nr() const;

    // Utility methods
    hidl_discriminator getDiscriminator() const;

    constexpr size_t hidl_getUnionOffset() const {
        return offsetof(::android::hardware::radio::V1_5::CellIdentity, hidl_u);
    }

private:
    void hidl_destructUnion();

    hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
    union hidl_union final {
        ::android::hidl::safe_union::V1_0::Monostate noinit __attribute__ ((aligned(1)));
        ::android::hardware::radio::V1_5::CellIdentityGsm gsm __attribute__ ((aligned(8)));
        ::android::hardware::radio::V1_5::CellIdentityWcdma wcdma __attribute__ ((aligned(8)));
        ::android::hardware::radio::V1_5::CellIdentityTdscdma tdscdma __attribute__ ((aligned(8)));
        ::android::hardware::radio::V1_2::CellIdentityCdma cdma __attribute__ ((aligned(8)));
        ::android::hardware::radio::V1_5::CellIdentityLte lte __attribute__ ((aligned(8)));
        ::android::hardware::radio::V1_5::CellIdentityNr nr __attribute__ ((aligned(8)));

        hidl_union();
        ~hidl_union();
    } hidl_u;

    static_assert(sizeof(::android::hardware::radio::V1_5::CellIdentity::hidl_union) == 160, "wrong size");
    static_assert(__alignof(::android::hardware::radio::V1_5::CellIdentity::hidl_union) == 8, "wrong alignment");
    static_assert(sizeof(::android::hardware::radio::V1_5::CellIdentity::hidl_discriminator) == 1, "wrong size");
    static_assert(__alignof(::android::hardware::radio::V1_5::CellIdentity::hidl_discriminator) == 1, "wrong alignment");
};

static_assert(sizeof(::android::hardware::radio::V1_5::CellIdentity) == 168, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::CellIdentity) == 8, "wrong alignment");

struct BarringInfo final {
    // Forward declaration for forward reference support:
    enum class ServiceType : int32_t;
    enum class BarringType : int32_t;
    struct BarringTypeSpecificInfo;

    enum class ServiceType : int32_t {
        /**
         * Applicable to UTRAN
         *
         *
         * Barring for all CS services, including registration
         */
        CS_SERVICE = 0,
        /**
         * Barring for all PS services, including registration
         */
        PS_SERVICE = 1 /* ::android::hardware::radio::V1_5::BarringInfo::ServiceType.CS_SERVICE implicitly + 1 */,
        /**
         * Barring for mobile-originated circuit-switched voice calls
         */
        CS_VOICE = 2 /* ::android::hardware::radio::V1_5::BarringInfo::ServiceType.PS_SERVICE implicitly + 1 */,
        /**
         * Applicable to EUTRAN, NGRAN
         *
         *
         * Barring for mobile-originated signalling for any purpose
         */
        MO_SIGNALLING = 3 /* ::android::hardware::radio::V1_5::BarringInfo::ServiceType.CS_VOICE implicitly + 1 */,
        /**
         * Barring for mobile-originated internet or other interactive data
         */
        MO_DATA = 4 /* ::android::hardware::radio::V1_5::BarringInfo::ServiceType.MO_SIGNALLING implicitly + 1 */,
        /**
         * Barring for circuit-switched fallback calling
         */
        CS_FALLBACK = 5 /* ::android::hardware::radio::V1_5::BarringInfo::ServiceType.MO_DATA implicitly + 1 */,
        /**
         * Barring for IMS voice calling
         */
        MMTEL_VOICE = 6 /* ::android::hardware::radio::V1_5::BarringInfo::ServiceType.CS_FALLBACK implicitly + 1 */,
        /**
         * Barring for IMS video calling
         */
        MMTEL_VIDEO = 7 /* ::android::hardware::radio::V1_5::BarringInfo::ServiceType.MMTEL_VOICE implicitly + 1 */,
        /**
         * Applicable to UTRAN, EUTRAN, NGRAN
         *
         *
         * Barring for emergency services, either CS or emergency MMTEL
         */
        EMERGENCY = 8 /* ::android::hardware::radio::V1_5::BarringInfo::ServiceType.MMTEL_VIDEO implicitly + 1 */,
        /**
         * Barring for short message services
         */
        SMS = 9 /* ::android::hardware::radio::V1_5::BarringInfo::ServiceType.EMERGENCY implicitly + 1 */,
        /**
         * Operator-specific barring codes; applicable to NGRAN
         */
        OPERATOR_1 = 1001,
        OPERATOR_2 = 1002,
        OPERATOR_3 = 1003,
        OPERATOR_4 = 1004,
        OPERATOR_5 = 1005,
        OPERATOR_6 = 1006,
        OPERATOR_7 = 1007,
        OPERATOR_8 = 1008,
        OPERATOR_9 = 1009,
        OPERATOR_10 = 1010,
        OPERATOR_11 = 1011,
        OPERATOR_12 = 1012,
        OPERATOR_13 = 1013,
        OPERATOR_14 = 1014,
        OPERATOR_15 = 1015,
        OPERATOR_16 = 1016,
        OPERATOR_17 = 1017,
        OPERATOR_18 = 1018,
        OPERATOR_19 = 1019,
        OPERATOR_20 = 1020,
        OPERATOR_21 = 1021,
        OPERATOR_22 = 1022,
        OPERATOR_23 = 1023,
        OPERATOR_24 = 1024,
        OPERATOR_25 = 1025,
        OPERATOR_26 = 1026,
        OPERATOR_27 = 1027,
        OPERATOR_28 = 1028,
        OPERATOR_29 = 1029,
        OPERATOR_30 = 1030,
        OPERATOR_31 = 1031,
        OPERATOR_32 = 1032,
    };

    enum class BarringType : int32_t {
        /**
         * Device is not barred for the given service
         */
        NONE = 0,
        /**
         * Device may be barred based on time and probability factors
         */
        CONDITIONAL = 1 /* ::android::hardware::radio::V1_5::BarringInfo::BarringType.NONE implicitly + 1 */,
        /*
         * Device is unconditionally barred
         */
        UNCONDITIONAL = 2 /* ::android::hardware::radio::V1_5::BarringInfo::BarringType.CONDITIONAL implicitly + 1 */,
    };

    struct BarringTypeSpecificInfo final {
        // Forward declaration for forward reference support:
        struct Conditional;

        struct Conditional final {
            /**
             * The barring factor as a percentage 0-100
             */
            int32_t factor __attribute__ ((aligned(4)));
            /**
             * The number of seconds between re-evaluations of barring
             */
            int32_t timeSeconds __attribute__ ((aligned(4)));
            /**
             * Indicates whether barring is currently being applied.
             *
             * <p>True if the UE applies barring to a conditionally barred
             * service based on the conditional barring parameters.
             *
             * <p>False if the service is conditionally barred but barring
             * is not currently applied, which could be due to either the
             * barring criteria not having been evaluated (if the UE has not
             * attempted to use the service) or due to the criteria being
             * evaluated and the UE being permitted to use the service
             * despite conditional barring.
             */
            bool isBarred __attribute__ ((aligned(1)));
        };

        static_assert(offsetof(::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional, factor) == 0, "wrong offset");
        static_assert(offsetof(::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional, timeSeconds) == 4, "wrong offset");
        static_assert(offsetof(::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional, isBarred) == 8, "wrong offset");
        static_assert(sizeof(::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional) == 12, "wrong size");
        static_assert(__alignof(::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional) == 4, "wrong alignment");

        enum class hidl_discriminator : uint8_t {
            /**
             * Barring type is either none or unconditional
             */
            noinit = 0,  // ::android::hidl::safe_union::V1_0::Monostate
            /**
             * Must be included if barring is conditional
             */
            conditional = 1,  // ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional
        };

        BarringTypeSpecificInfo();
        ~BarringTypeSpecificInfo();
        BarringTypeSpecificInfo(BarringTypeSpecificInfo&&);
        BarringTypeSpecificInfo(const BarringTypeSpecificInfo&);
        BarringTypeSpecificInfo& operator=(BarringTypeSpecificInfo&&);
        BarringTypeSpecificInfo& operator=(const BarringTypeSpecificInfo&);

        void noinit(const ::android::hidl::safe_union::V1_0::Monostate&);
        void noinit(::android::hidl::safe_union::V1_0::Monostate&&);
        ::android::hidl::safe_union::V1_0::Monostate& noinit();
        const ::android::hidl::safe_union::V1_0::Monostate& noinit() const;

        void conditional(const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional&);
        void conditional(::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional&&);
        ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional& conditional();
        const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional& conditional() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            ::android::hidl::safe_union::V1_0::Monostate noinit __attribute__ ((aligned(1)));
            ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional conditional __attribute__ ((aligned(4)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::hidl_union) == 12, "wrong size");
        static_assert(__alignof(::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::hidl_union) == 4, "wrong alignment");
        static_assert(sizeof(::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo) == 16, "wrong size");
    static_assert(__alignof(::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo) == 4, "wrong alignment");

    /**
     * Combined list of barring services for UTRAN, EUTRAN, and NGRAN.
     *
     * Barring information is defined in:
     * -UTRAN - 3gpp 25.331 Sec 10.2.48.8.6.
     * -EUTRAN - 3gpp 36.331 Sec 6.3.1 SystemInformationBlockType2
     * -NGRAN - 3gpp 38.331 Sec 6.3.2 UAC-BarringInfo and 22.261 Sec 6.22.2.[2-3]
     */
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType serviceType __attribute__ ((aligned(4)));
    /**
     * The type of barring applied to the service
     */
    ::android::hardware::radio::V1_5::BarringInfo::BarringType barringType __attribute__ ((aligned(4)));
    /**
     * Type-specific barring info if applicable
     */
    ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo barringTypeSpecificInfo __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::BarringInfo, serviceType) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::BarringInfo, barringType) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::BarringInfo, barringTypeSpecificInfo) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::BarringInfo) == 24, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::BarringInfo) == 4, "wrong alignment");

enum class IndicationFilter : int32_t {
    NONE = 0,
    ALL = -1 /* ~0 */,
    /**
     * When this bit is set, modem must send the signal strength update through
     * IRadioIndication.currentSignalStrength() when all criteria specified by
     * IRadio.setSignalStrengthReportingCriteria() are met.
     */
    SIGNAL_STRENGTH = 1 /* 1 << 0 */,
    /**
     * When this bit is set, modem must invoke IRadioIndication.networkStateChanged() when any field
     * in VoiceRegStateResult or DataRegStateResult changes. When this bit is not set, modem must
     * suppress IRadioIndication.networkStateChanged() when there are only changes from
     * insignificant fields. Modem must invoke IRadioIndication.networkStateChanged() when
     * significant fields are updated regardless of whether this bit is set.
     *
     * The following fields are considered significant: VoiceRegStateResult.regState,
     * VoiceRegStateResult.rat, DataRegStateResult.regState, DataRegStateResult.rat.
     */
    FULL_NETWORK_STATE = 2 /* 1 << 1 */,
    /**
     * When this bit is set, modem must send IRadioIndication.dataCallListChanged() whenever any
     * field in ITypes.SetupDataCallResult changes. When this bit is not set, modem must suppress
     * the indication when the only changed field is 'active' (for data dormancy). For all other
     * field changes, the modem must send IRadioIndication.dataCallListChanged() regardless of
     * whether this bit is set.
     */
    DATA_CALL_DORMANCY_CHANGED = 4 /* 1 << 2 */,
    /**
     * When this bit is set, modem must send the link capacity update through
     * IRadioIndication.currentLinkCapacityEstimate() when all criteria specified by
     * IRadio.setLinkCapacityReportingCriteria() are met.
     */
    LINK_CAPACITY_ESTIMATE = 8 /* 1 << 3 */,
    /**
     * When this bit is set, the modem must send the physical channel configuration update through
     * IRadioIndication.currentPhysicalChannelConfigs() when the configuration has changed. It is
     * recommended that this be reported whenever link capacity or signal strength is reported.
     */
    PHYSICAL_CHANNEL_CONFIG = 16 /* 1 << 4 */,
    /**
     * Control the unsolicited sending of registration failure reports via onRegistrationFailed
     */
    REGISTRATION_FAILURE = 32 /* 1 << 5 */,
    /**
     * Control the unsolicited sending of barring info updates via onBarringInfo
     */
    BARRING_INFO = 64 /* 1 << 6 */,
};

/**
 * Call fail causes for Circuit-switched service enumerated in 3GPP TS 24.008, 10.5.3.6 and
 * 10.5.147. Additional detail is available in 3GPP TS 24.008 Annex G.
 */
enum class RegistrationFailCause : int32_t {
    /**
     * 0 - None
     */
    NONE = 0,
    /**
     * 2 - IMSI unknown in HLR
     */
    IMSI_UNKNOWN_IN_HLR = 2,
    /**
     * 3 - Illegal MS
     */
    ILLEGAL_MS = 3,
    /**
     * 4 - Illegal ME
     */
    IMSI_UNKNOWN_IN_VLR = 4,
    /**
     * 5 - PLMN not allowed
     */
    IMEI_NOT_ACCEPTED = 5,
    /**
     * 6 - Location area not allowed
     */
    ILLEGAL_ME = 6,
    /**
     * 7 - Roaming not allowed
     */
    GPRS_SERVICES_NOT_ALLOWED = 7,
    /**
     * 8 - No Suitable Cells in this Location Area
     */
    GPRS_AND_NON_GPRS_SERVICES_NOT_ALLOWED = 8,
    /**
     * 9 - Network failure
     */
    MS_IDENTITY_CANNOT_BE_DERIVED_BY_NETWORK = 9,
    /**
     * 10 - Persistent location update reject
     */
    IMPLICITLY_DETACHED = 10,
    /**
     * 11 - PLMN not allowed
     */
    PLMN_NOT_ALLOWED = 11,
    /**
     * 12 - Location area not allowed
     */
    LOCATION_AREA_NOT_ALLOWED = 12,
    /**
     * 13 - Roaming not allowed in this Location Area
     */
    ROAMING_NOT_ALLOWED = 13,
    /**
     * 14 - GPRS Services not allowed in this PLMN
     */
    GPRS_SERVICES_NOT_ALLOWED_IN_PLMN = 14,
    /**
     * 15 - No Suitable Cells in this Location Area
     */
    NO_SUITABLE_CELLS = 15,
    /**
     * 16 - MSC temporarily not reachable
     */
    MSC_TEMPORARILY_NOT_REACHABLE = 15,
    /**
     * 17 - Network Failure
     */
    NETWORK_FAILURE = 17,
    /**
     * 20 - MAC Failure
     */
    MAC_FAILURE = 20,
    /**
     * 21 - Sync Failure
     */
    SYNC_FAILURE = 21,
    /**
     * 22 - Congestion
     */
    CONGESTION = 22,
    /**
     * 23 - GSM Authentication unacceptable
     */
    GSM_AUTHENTICATION_UNACCEPTABLE = 23,
    /**
     * 25 - Not Authorized for this CSG
     */
    NOT_AUTHORIZED_FOR_THIS_CSG = 25,
    /**
     * 28 SMS provided via GPRS in this routing area
     */
    SMS_PROVIDED_BY_GPRS_IN_ROUTING_AREA = 26 /* ::android::hardware::radio::V1_5::RegistrationFailCause.NOT_AUTHORIZED_FOR_THIS_CSG implicitly + 1 */,
    /**
     * 32 - Service option not supported
     */
    SERVICE_OPTION_NOT_SUPPORTED = 32,
    /**
     * 33 - Requested service option not subscribed
     */
    SERVICE_OPTION_NOT_SUBSCRIBED = 33,
    /**
     * 34 - Service option temporarily out of order
     */
    SERVICE_OPTION_TEMPORARILY_OUT_OF_ORDER = 34,
    /**
     * 38 - Call cannot be identified
     */
    CALL_CANNOT_BE_IDENTIFIED = 38,
    /**
     * 40 No PDP context activated
     */
    NO_PDP_CONTEXT_ACTIVATED = 40,
    /**
     * 48-63 - Retry upon entry into a new cell
     */
    RETRY_UPON_ENTRY_INTO_NEW_CELL_1 = 48,
    RETRY_UPON_ENTRY_INTO_NEW_CELL_2 = 49,
    RETRY_UPON_ENTRY_INTO_NEW_CELL_3 = 50,
    RETRY_UPON_ENTRY_INTO_NEW_CELL_4 = 51,
    RETRY_UPON_ENTRY_INTO_NEW_CELL_5 = 52,
    RETRY_UPON_ENTRY_INTO_NEW_CELL_6 = 53,
    RETRY_UPON_ENTRY_INTO_NEW_CELL_7 = 54,
    RETRY_UPON_ENTRY_INTO_NEW_CELL_8 = 55,
    RETRY_UPON_ENTRY_INTO_NEW_CELL_9 = 56,
    RETRY_UPON_ENTRY_INTO_NEW_CELL_10 = 57,
    RETRY_UPON_ENTRY_INTO_NEW_CELL_11 = 58,
    RETRY_UPON_ENTRY_INTO_NEW_CELL_12 = 59,
    RETRY_UPON_ENTRY_INTO_NEW_CELL_13 = 60,
    RETRY_UPON_ENTRY_INTO_NEW_CELL_14 = 61,
    RETRY_UPON_ENTRY_INTO_NEW_CELL_15 = 62,
    RETRY_UPON_ENTRY_INTO_NEW_CELL_16 = 63,
    /**
     * 95 - Semantically incorrect message
     */
    SEMANTICALLY_INCORRECT_MESSAGE = 95,
    /**
     * 96 - Invalid mandatory information
     */
    INVALID_MANDATORY_INFORMATION = 96,
    /**
     * 97 - Message type non-existent or not implemented
     */
    MESSAGE_TYPE_NON_EXISTENT_OR_NOT_IMPLEMENTED = 97,
    /**
     * 98 - Message type not compatible with protocol state
     */
    MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE = 98,
    /**
     * 99 - Information element non-existent or not implemented
     */
    INFORMATION_ELEMENT_NON_EXISTENT_OR_NOT_IMPLEMENTED = 99,
    /**
     * 100 - Conditional IE error
     */
    CONDITIONAL_IE_ERROR = 100,
    /**
     * 101 - Message not compatible with protocol state
     */
    MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE = 101,
    /**
     * 111 - Protocol error, unspecified
     */
    PROTOCOL_ERROR_UNSPECIFIED = 111,
};

enum class PrlIndicator : int32_t {
    NOT_REGISTERED = -1 /* -1 */,
    NOT_IN_PRL = 0,
    IN_PRL = 1,
};

struct RegStateResult final {
    // Forward declaration for forward reference support:
    struct AccessTechnologySpecificInfo;

    struct AccessTechnologySpecificInfo final {
        // Forward declaration for forward reference support:
        struct Cdma2000RegistrationInfo;
        struct EutranRegistrationInfo;

        struct Cdma2000RegistrationInfo final {
            /**
             * Concurrent services support indicator. if registered on a CDMA system.
             * false - Concurrent services not supported,
             * true - Concurrent services supported
             */
            bool cssSupported __attribute__ ((aligned(1)));
            /**
             * TSB-58 Roaming Indicator if registered on a CDMA or EVDO system or -1 if not.
             * Valid values are 0-255.
             */
            int32_t roamingIndicator __attribute__ ((aligned(4)));
            /**
             * Indicates whether the current system is in the PRL if registered on a CDMA or EVDO
             * system or -1 if not. 0=not in the PRL, 1=in the PRL.
             */
            ::android::hardware::radio::V1_5::PrlIndicator systemIsInPrl __attribute__ ((aligned(4)));
            /**
             * Default Roaming Indicator from the PRL if registered on a CDMA or EVDO system or -1
             * if not.
             * Valid values are 0-255.
             */
            int32_t defaultRoamingIndicator __attribute__ ((aligned(4)));
        };

        static_assert(offsetof(::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo, cssSupported) == 0, "wrong offset");
        static_assert(offsetof(::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo, roamingIndicator) == 4, "wrong offset");
        static_assert(offsetof(::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo, systemIsInPrl) == 8, "wrong offset");
        static_assert(offsetof(::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo, defaultRoamingIndicator) == 12, "wrong offset");
        static_assert(sizeof(::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo) == 16, "wrong size");
        static_assert(__alignof(::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo) == 4, "wrong alignment");

        struct EutranRegistrationInfo final {
            /**
             * Network capabilities for voice over PS services. This info is valid only on LTE
             * network and must be present when device is camped on LTE. VopsInfo must be empty when
             * device is camped only on 2G/3G.
             */
            ::android::hardware::radio::V1_4::LteVopsInfo lteVopsInfo __attribute__ ((aligned(1)));
            /**
             * The parameters of NR 5G Non-Standalone. This value is only valid on E-UTRAN,
             * otherwise must be empty.
             */
            ::android::hardware::radio::V1_4::NrIndicators nrIndicators __attribute__ ((aligned(1)));
        };

        static_assert(offsetof(::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo, lteVopsInfo) == 0, "wrong offset");
        static_assert(offsetof(::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo, nrIndicators) == 2, "wrong offset");
        static_assert(sizeof(::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo) == 5, "wrong size");
        static_assert(__alignof(::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo) == 1, "wrong alignment");

        enum class hidl_discriminator : uint8_t {
            noinit = 0,  // ::android::hidl::safe_union::V1_0::Monostate
            cdmaInfo = 1,  // ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo
            eutranInfo = 2,  // ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo
        };

        AccessTechnologySpecificInfo();
        ~AccessTechnologySpecificInfo();
        AccessTechnologySpecificInfo(AccessTechnologySpecificInfo&&);
        AccessTechnologySpecificInfo(const AccessTechnologySpecificInfo&);
        AccessTechnologySpecificInfo& operator=(AccessTechnologySpecificInfo&&);
        AccessTechnologySpecificInfo& operator=(const AccessTechnologySpecificInfo&);

        void noinit(const ::android::hidl::safe_union::V1_0::Monostate&);
        void noinit(::android::hidl::safe_union::V1_0::Monostate&&);
        ::android::hidl::safe_union::V1_0::Monostate& noinit();
        const ::android::hidl::safe_union::V1_0::Monostate& noinit() const;

        void cdmaInfo(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo&);
        void cdmaInfo(::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo&&);
        ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo& cdmaInfo();
        const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo& cdmaInfo() const;

        void eutranInfo(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo&);
        void eutranInfo(::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo&&);
        ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo& eutranInfo();
        const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo& eutranInfo() const;

        // Utility methods
        hidl_discriminator getDiscriminator() const;

        constexpr size_t hidl_getUnionOffset() const {
            return offsetof(::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo, hidl_u);
        }

    private:
        void hidl_destructUnion();

        hidl_discriminator hidl_d __attribute__ ((aligned(1))) ;
        union hidl_union final {
            ::android::hidl::safe_union::V1_0::Monostate noinit __attribute__ ((aligned(1)));
            ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo cdmaInfo __attribute__ ((aligned(4)));
            ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo eutranInfo __attribute__ ((aligned(1)));

            hidl_union();
            ~hidl_union();
        } hidl_u;

        static_assert(sizeof(::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::hidl_union) == 16, "wrong size");
        static_assert(__alignof(::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::hidl_union) == 4, "wrong alignment");
        static_assert(sizeof(::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::hidl_discriminator) == 1, "wrong size");
        static_assert(__alignof(::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::hidl_discriminator) == 1, "wrong alignment");
    };

    static_assert(sizeof(::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo) == 20, "wrong size");
    static_assert(__alignof(::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo) == 4, "wrong alignment");

    /**
     * Registration state
     *
     * If the RAT is indicated as a GERAN, UTRAN, or CDMA2000 technology, this value reports
     * registration in the Circuit-switched domain.
     * If the RAT is indicated as an EUTRAN, NGRAN, or another technology that does not support
     * circuit-switched services, this value reports registration in the Packet-switched domain.
     */
    ::android::hardware::radio::V1_0::RegState regState __attribute__ ((aligned(4)));
    /**
     * Indicates the available voice radio technology, valid values as
     * defined by RadioTechnology.
     */
    ::android::hardware::radio::V1_4::RadioTechnology rat __attribute__ ((aligned(4)));
    /**
     * Cause code reported by the network in case registration fails. This will be a mobility
     * management cause code defined for MM, GMM, MME or equivalent as appropriate for the RAT.
     */
    ::android::hardware::radio::V1_5::RegistrationFailCause reasonForDenial __attribute__ ((aligned(4)));
    /**
     * CellIdentity
     */
    ::android::hardware::radio::V1_5::CellIdentity cellIdentity __attribute__ ((aligned(8)));
    /**
     * The most-recent PLMN-ID upon which the UE registered (or attempted to register if a failure
     * is reported in the reasonForDenial field). This PLMN shall be in standard format consisting
     * of a 3 digit MCC concatenated with a 2 or 3 digit MNC.
     */
    ::android::hardware::hidl_string registeredPlmn __attribute__ ((aligned(8)));
    /**
     * Access-technology-specific registration information, such as for CDMA2000.
     */
    ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo accessTechnologySpecificInfo __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::RegStateResult, regState) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::RegStateResult, rat) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::RegStateResult, reasonForDenial) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::RegStateResult, cellIdentity) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::RegStateResult, registeredPlmn) == 184, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::RegStateResult, accessTechnologySpecificInfo) == 200, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::RegStateResult) == 224, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::RegStateResult) == 8, "wrong alignment");

/**
 * Overwritten from @1.4::NetworkScanResult in order to update the CellInfo to 1.5 version.
 */
struct NetworkScanResult final {
    /**
     * The status of the scan.
     */
    ::android::hardware::radio::V1_1::ScanStatus status __attribute__ ((aligned(4)));
    /**
     * The error code of the incremental result.
     */
    ::android::hardware::radio::V1_0::RadioError error __attribute__ ((aligned(4)));
    /**
     * List of network information as CellInfo.
     */
    ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::CellInfo> networkInfos __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::NetworkScanResult, status) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::NetworkScanResult, error) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::NetworkScanResult, networkInfos) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::NetworkScanResult) == 24, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::NetworkScanResult) == 8, "wrong alignment");

/**
 * Additional personalization categories in addition to those specified in 3GPP TS 22.022 and
 * 3GPP2 C.S0068-0.
 */
enum class PersoSubstate : int32_t {
    UNKNOWN = 0,
    IN_PROGRESS = 1 /* ::android::hardware::radio::V1_0::PersoSubstate.UNKNOWN implicitly + 1 */,
    READY = 2 /* ::android::hardware::radio::V1_0::PersoSubstate.IN_PROGRESS implicitly + 1 */,
    SIM_NETWORK = 3 /* ::android::hardware::radio::V1_0::PersoSubstate.READY implicitly + 1 */,
    SIM_NETWORK_SUBSET = 4 /* ::android::hardware::radio::V1_0::PersoSubstate.SIM_NETWORK implicitly + 1 */,
    SIM_CORPORATE = 5 /* ::android::hardware::radio::V1_0::PersoSubstate.SIM_NETWORK_SUBSET implicitly + 1 */,
    SIM_SERVICE_PROVIDER = 6 /* ::android::hardware::radio::V1_0::PersoSubstate.SIM_CORPORATE implicitly + 1 */,
    SIM_SIM = 7 /* ::android::hardware::radio::V1_0::PersoSubstate.SIM_SERVICE_PROVIDER implicitly + 1 */,
    SIM_NETWORK_PUK = 8 /* ::android::hardware::radio::V1_0::PersoSubstate.SIM_SIM implicitly + 1 */,
    SIM_NETWORK_SUBSET_PUK = 9 /* ::android::hardware::radio::V1_0::PersoSubstate.SIM_NETWORK_PUK implicitly + 1 */,
    SIM_CORPORATE_PUK = 10 /* ::android::hardware::radio::V1_0::PersoSubstate.SIM_NETWORK_SUBSET_PUK implicitly + 1 */,
    SIM_SERVICE_PROVIDER_PUK = 11 /* ::android::hardware::radio::V1_0::PersoSubstate.SIM_CORPORATE_PUK implicitly + 1 */,
    SIM_SIM_PUK = 12 /* ::android::hardware::radio::V1_0::PersoSubstate.SIM_SERVICE_PROVIDER_PUK implicitly + 1 */,
    RUIM_NETWORK1 = 13 /* ::android::hardware::radio::V1_0::PersoSubstate.SIM_SIM_PUK implicitly + 1 */,
    RUIM_NETWORK2 = 14 /* ::android::hardware::radio::V1_0::PersoSubstate.RUIM_NETWORK1 implicitly + 1 */,
    RUIM_HRPD = 15 /* ::android::hardware::radio::V1_0::PersoSubstate.RUIM_NETWORK2 implicitly + 1 */,
    RUIM_CORPORATE = 16 /* ::android::hardware::radio::V1_0::PersoSubstate.RUIM_HRPD implicitly + 1 */,
    RUIM_SERVICE_PROVIDER = 17 /* ::android::hardware::radio::V1_0::PersoSubstate.RUIM_CORPORATE implicitly + 1 */,
    RUIM_RUIM = 18 /* ::android::hardware::radio::V1_0::PersoSubstate.RUIM_SERVICE_PROVIDER implicitly + 1 */,
    RUIM_NETWORK1_PUK = 19 /* ::android::hardware::radio::V1_0::PersoSubstate.RUIM_RUIM implicitly + 1 */,
    RUIM_NETWORK2_PUK = 20 /* ::android::hardware::radio::V1_0::PersoSubstate.RUIM_NETWORK1_PUK implicitly + 1 */,
    RUIM_HRPD_PUK = 21 /* ::android::hardware::radio::V1_0::PersoSubstate.RUIM_NETWORK2_PUK implicitly + 1 */,
    RUIM_CORPORATE_PUK = 22 /* ::android::hardware::radio::V1_0::PersoSubstate.RUIM_HRPD_PUK implicitly + 1 */,
    RUIM_SERVICE_PROVIDER_PUK = 23 /* ::android::hardware::radio::V1_0::PersoSubstate.RUIM_CORPORATE_PUK implicitly + 1 */,
    RUIM_RUIM_PUK = 24 /* ::android::hardware::radio::V1_0::PersoSubstate.RUIM_SERVICE_PROVIDER_PUK implicitly + 1 */,
    /**
     * The device is personalized using the content of the Service Provider Name (SPN) in the SIM
     * card.
     */
    SIM_SPN = 25 /* ::android::hardware::radio::V1_0::PersoSubstate.RUIM_RUIM_PUK implicitly + 1 */,
    SIM_SPN_PUK = 26 /* ::android::hardware::radio::V1_5::PersoSubstate.SIM_SPN implicitly + 1 */,
    /**
     * Service Provider and Equivalent Home PLMN
     * The device is personalized using both the content of the GID1 (equivalent to service provider
     * personalization) and the content of the Equivalent Home PLMN (EHPLMN) in the SIM card.
     * If the GID1 in the SIM is absent, then just the content of the Equivalent Home PLMN
     * is matched.
     */
    SIM_SP_EHPLMN = 27 /* ::android::hardware::radio::V1_5::PersoSubstate.SIM_SPN_PUK implicitly + 1 */,
    SIM_SP_EHPLMN_PUK = 28 /* ::android::hardware::radio::V1_5::PersoSubstate.SIM_SP_EHPLMN implicitly + 1 */,
    /**
     * Device is personalized using the first digits of the ICCID of the SIM card.
     */
    SIM_ICCID = 29 /* ::android::hardware::radio::V1_5::PersoSubstate.SIM_SP_EHPLMN_PUK implicitly + 1 */,
    SIM_ICCID_PUK = 30 /* ::android::hardware::radio::V1_5::PersoSubstate.SIM_ICCID implicitly + 1 */,
    /**
     * Device is personalized using the content of the IMPI in the ISIM.
     */
    SIM_IMPI = 31 /* ::android::hardware::radio::V1_5::PersoSubstate.SIM_ICCID_PUK implicitly + 1 */,
    SIM_IMPI_PUK = 32 /* ::android::hardware::radio::V1_5::PersoSubstate.SIM_IMPI implicitly + 1 */,
    /**
     * Network Subset and Service Provider
     * Device is personalized using both the content of GID1 (equivalent to service provider
     * personalization) and the first digits of the IMSI (equivalent to network subset
     * personalization).
     */
    SIM_NS_SP = 33 /* ::android::hardware::radio::V1_5::PersoSubstate.SIM_IMPI_PUK implicitly + 1 */,
    SIM_NS_SP_PUK = 34 /* ::android::hardware::radio::V1_5::PersoSubstate.SIM_NS_SP implicitly + 1 */,
};

/**
 * Extended from @1.0::AppStatus to update PersoSubstate to 1.5 version.
 */
struct AppStatus final {
    ::android::hardware::radio::V1_0::AppStatus base __attribute__ ((aligned(8)));
    /**
     * Applicable only if appState == SUBSCRIPTION_PERSO
     */
    ::android::hardware::radio::V1_5::PersoSubstate persoSubstate __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::AppStatus, base) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::AppStatus, persoSubstate) == 64, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::AppStatus) == 72, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::AppStatus) == 8, "wrong alignment");

/**
 * Extended from @1.4::CardStatus to use 1.5 version of AppStatus.
 */
struct CardStatus final {
    ::android::hardware::radio::V1_4::CardStatus base __attribute__ ((aligned(8)));
    /**
     * size <= RadioConst::CARD_MAX_APPS
     */
    ::android::hardware::hidl_vec<::android::hardware::radio::V1_5::AppStatus> applications __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::radio::V1_5::CardStatus, base) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::radio::V1_5::CardStatus, applications) == 96, "wrong offset");
static_assert(sizeof(::android::hardware::radio::V1_5::CardStatus) == 112, "wrong size");
static_assert(__alignof(::android::hardware::radio::V1_5::CardStatus) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::radio::V1_5::SignalMeasurementType o);
static inline void PrintTo(::android::hardware::radio::V1_5::SignalMeasurementType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::SignalMeasurementType lhs, const ::android::hardware::radio::V1_5::SignalMeasurementType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::radio::V1_5::SignalMeasurementType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::SignalMeasurementType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::SignalMeasurementType lhs, const ::android::hardware::radio::V1_5::SignalMeasurementType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::radio::V1_5::SignalMeasurementType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::SignalMeasurementType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::radio::V1_5::SignalMeasurementType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::radio::V1_5::SignalMeasurementType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::radio::V1_5::SignalThresholdInfo& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::SignalThresholdInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::SignalThresholdInfo& lhs, const ::android::hardware::radio::V1_5::SignalThresholdInfo& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::SignalThresholdInfo& lhs, const ::android::hardware::radio::V1_5::SignalThresholdInfo& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::radio::V1_5::AccessNetwork o);
static inline void PrintTo(::android::hardware::radio::V1_5::AccessNetwork o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::AccessNetwork lhs, const ::android::hardware::radio::V1_5::AccessNetwork rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::radio::V1_5::AccessNetwork rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::AccessNetwork lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::AccessNetwork lhs, const ::android::hardware::radio::V1_5::AccessNetwork rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::radio::V1_5::AccessNetwork rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::AccessNetwork lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::radio::V1_5::AccessNetwork e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::radio::V1_5::AccessNetwork e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::radio::V1_5::RadioAccessNetworks o);
static inline void PrintTo(::android::hardware::radio::V1_5::RadioAccessNetworks o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::RadioAccessNetworks lhs, const ::android::hardware::radio::V1_5::RadioAccessNetworks rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::radio::V1_5::RadioAccessNetworks rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::RadioAccessNetworks lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::RadioAccessNetworks lhs, const ::android::hardware::radio::V1_5::RadioAccessNetworks rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::radio::V1_5::RadioAccessNetworks rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::RadioAccessNetworks lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::radio::V1_5::RadioAccessNetworks e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::radio::V1_5::RadioAccessNetworks e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands& lhs, const ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands& lhs, const ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::RadioAccessSpecifier& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::RadioAccessSpecifier& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::RadioAccessSpecifier& lhs, const ::android::hardware::radio::V1_5::RadioAccessSpecifier& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::RadioAccessSpecifier& lhs, const ::android::hardware::radio::V1_5::RadioAccessSpecifier& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::radio::V1_5::NgranBands o);
static inline void PrintTo(::android::hardware::radio::V1_5::NgranBands o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::NgranBands lhs, const ::android::hardware::radio::V1_5::NgranBands rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::radio::V1_5::NgranBands rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::NgranBands lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::NgranBands lhs, const ::android::hardware::radio::V1_5::NgranBands rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::radio::V1_5::NgranBands rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::NgranBands lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::radio::V1_5::NgranBands e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::radio::V1_5::NgranBands e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::radio::V1_5::UtranBands o);
static inline void PrintTo(::android::hardware::radio::V1_5::UtranBands o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::UtranBands lhs, const ::android::hardware::radio::V1_5::UtranBands rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::radio::V1_5::UtranBands rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::UtranBands lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::UtranBands lhs, const ::android::hardware::radio::V1_5::UtranBands rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::radio::V1_5::UtranBands rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::UtranBands lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::radio::V1_5::UtranBands e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::radio::V1_5::UtranBands e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::radio::V1_5::EutranBands o);
static inline void PrintTo(::android::hardware::radio::V1_5::EutranBands o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::EutranBands lhs, const ::android::hardware::radio::V1_5::EutranBands rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::radio::V1_5::EutranBands rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::EutranBands lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::EutranBands lhs, const ::android::hardware::radio::V1_5::EutranBands rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::radio::V1_5::EutranBands rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::EutranBands lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::radio::V1_5::EutranBands e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::radio::V1_5::EutranBands e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::radio::V1_5::NetworkScanRequest& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::NetworkScanRequest& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::NetworkScanRequest& lhs, const ::android::hardware::radio::V1_5::NetworkScanRequest& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::NetworkScanRequest& lhs, const ::android::hardware::radio::V1_5::NetworkScanRequest& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::radio::V1_5::ApnTypes o);
static inline void PrintTo(::android::hardware::radio::V1_5::ApnTypes o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::ApnTypes lhs, const ::android::hardware::radio::V1_5::ApnTypes rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::radio::V1_5::ApnTypes rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::ApnTypes lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::ApnTypes lhs, const ::android::hardware::radio::V1_5::ApnTypes rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::radio::V1_5::ApnTypes rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::ApnTypes lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::radio::V1_5::ApnTypes e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::radio::V1_5::ApnTypes e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::radio::V1_5::DataProfileInfo& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::DataProfileInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::DataProfileInfo& lhs, const ::android::hardware::radio::V1_5::DataProfileInfo& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::DataProfileInfo& lhs, const ::android::hardware::radio::V1_5::DataProfileInfo& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::radio::V1_5::AddressProperty o);
static inline void PrintTo(::android::hardware::radio::V1_5::AddressProperty o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::AddressProperty lhs, const ::android::hardware::radio::V1_5::AddressProperty rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::radio::V1_5::AddressProperty rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::AddressProperty lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::AddressProperty lhs, const ::android::hardware::radio::V1_5::AddressProperty rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::radio::V1_5::AddressProperty rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::AddressProperty lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::radio::V1_5::AddressProperty e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::radio::V1_5::AddressProperty e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::radio::V1_5::LinkAddress& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::LinkAddress& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::LinkAddress& lhs, const ::android::hardware::radio::V1_5::LinkAddress& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::LinkAddress& lhs, const ::android::hardware::radio::V1_5::LinkAddress& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::SetupDataCallResult& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::SetupDataCallResult& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::SetupDataCallResult& lhs, const ::android::hardware::radio::V1_5::SetupDataCallResult& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::SetupDataCallResult& lhs, const ::android::hardware::radio::V1_5::SetupDataCallResult& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::radio::V1_5::Domain o);
static inline void PrintTo(::android::hardware::radio::V1_5::Domain o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::Domain lhs, const ::android::hardware::radio::V1_5::Domain rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::radio::V1_5::Domain rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::Domain lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::Domain lhs, const ::android::hardware::radio::V1_5::Domain rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::radio::V1_5::Domain rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::Domain lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::radio::V1_5::Domain e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::radio::V1_5::Domain e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo& lhs, const ::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo& lhs, const ::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::OptionalCsgInfo& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::OptionalCsgInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::OptionalCsgInfo& lhs, const ::android::hardware::radio::V1_5::OptionalCsgInfo& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::OptionalCsgInfo& lhs, const ::android::hardware::radio::V1_5::OptionalCsgInfo& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::CellIdentityGsm& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::CellIdentityGsm& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::CellIdentityGsm& lhs, const ::android::hardware::radio::V1_5::CellIdentityGsm& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::CellIdentityGsm& lhs, const ::android::hardware::radio::V1_5::CellIdentityGsm& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::CellIdentityWcdma& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::CellIdentityWcdma& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::CellIdentityWcdma& lhs, const ::android::hardware::radio::V1_5::CellIdentityWcdma& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::CellIdentityWcdma& lhs, const ::android::hardware::radio::V1_5::CellIdentityWcdma& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::CellIdentityTdscdma& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::CellIdentityTdscdma& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::CellIdentityTdscdma& lhs, const ::android::hardware::radio::V1_5::CellIdentityTdscdma& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::CellIdentityTdscdma& lhs, const ::android::hardware::radio::V1_5::CellIdentityTdscdma& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::CellIdentityLte& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::CellIdentityLte& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::CellIdentityLte& lhs, const ::android::hardware::radio::V1_5::CellIdentityLte& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::CellIdentityLte& lhs, const ::android::hardware::radio::V1_5::CellIdentityLte& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::CellIdentityNr& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::CellIdentityNr& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::CellIdentityNr& lhs, const ::android::hardware::radio::V1_5::CellIdentityNr& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::CellIdentityNr& lhs, const ::android::hardware::radio::V1_5::CellIdentityNr& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::CellInfoGsm& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::CellInfoGsm& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::CellInfoGsm& lhs, const ::android::hardware::radio::V1_5::CellInfoGsm& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::CellInfoGsm& lhs, const ::android::hardware::radio::V1_5::CellInfoGsm& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::CellInfoWcdma& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::CellInfoWcdma& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::CellInfoWcdma& lhs, const ::android::hardware::radio::V1_5::CellInfoWcdma& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::CellInfoWcdma& lhs, const ::android::hardware::radio::V1_5::CellInfoWcdma& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::CellInfoTdscdma& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::CellInfoTdscdma& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::CellInfoTdscdma& lhs, const ::android::hardware::radio::V1_5::CellInfoTdscdma& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::CellInfoTdscdma& lhs, const ::android::hardware::radio::V1_5::CellInfoTdscdma& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::CellInfoLte& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::CellInfoLte& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::CellInfoLte& lhs, const ::android::hardware::radio::V1_5::CellInfoLte& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::CellInfoLte& lhs, const ::android::hardware::radio::V1_5::CellInfoLte& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::CellInfoNr& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::CellInfoNr& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::CellInfoNr& lhs, const ::android::hardware::radio::V1_5::CellInfoNr& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::CellInfoNr& lhs, const ::android::hardware::radio::V1_5::CellInfoNr& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo& lhs, const ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo& lhs, const ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::CellInfo& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::CellInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::CellInfo& lhs, const ::android::hardware::radio::V1_5::CellInfo& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::CellInfo& lhs, const ::android::hardware::radio::V1_5::CellInfo& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::CellIdentity& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::CellIdentity& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::CellIdentity& lhs, const ::android::hardware::radio::V1_5::CellIdentity& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::CellIdentity& lhs, const ::android::hardware::radio::V1_5::CellIdentity& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::radio::V1_5::BarringInfo::ServiceType o);
static inline void PrintTo(::android::hardware::radio::V1_5::BarringInfo::ServiceType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::BarringInfo::ServiceType lhs, const ::android::hardware::radio::V1_5::BarringInfo::ServiceType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::radio::V1_5::BarringInfo::ServiceType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::BarringInfo::ServiceType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::BarringInfo::ServiceType lhs, const ::android::hardware::radio::V1_5::BarringInfo::ServiceType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::radio::V1_5::BarringInfo::ServiceType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::BarringInfo::ServiceType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::radio::V1_5::BarringInfo::ServiceType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::radio::V1_5::BarringInfo::ServiceType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::radio::V1_5::BarringInfo::BarringType o);
static inline void PrintTo(::android::hardware::radio::V1_5::BarringInfo::BarringType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::BarringInfo::BarringType lhs, const ::android::hardware::radio::V1_5::BarringInfo::BarringType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::radio::V1_5::BarringInfo::BarringType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::BarringInfo::BarringType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::BarringInfo::BarringType lhs, const ::android::hardware::radio::V1_5::BarringInfo::BarringType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::radio::V1_5::BarringInfo::BarringType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::BarringInfo::BarringType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::radio::V1_5::BarringInfo::BarringType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::radio::V1_5::BarringInfo::BarringType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional& lhs, const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional& lhs, const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo& lhs, const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo& lhs, const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::BarringInfo& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::BarringInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::BarringInfo& lhs, const ::android::hardware::radio::V1_5::BarringInfo& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::BarringInfo& lhs, const ::android::hardware::radio::V1_5::BarringInfo& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::radio::V1_5::IndicationFilter o);
static inline void PrintTo(::android::hardware::radio::V1_5::IndicationFilter o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::IndicationFilter lhs, const ::android::hardware::radio::V1_5::IndicationFilter rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::radio::V1_5::IndicationFilter rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::IndicationFilter lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::IndicationFilter lhs, const ::android::hardware::radio::V1_5::IndicationFilter rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::radio::V1_5::IndicationFilter rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::IndicationFilter lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::radio::V1_5::IndicationFilter e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::radio::V1_5::IndicationFilter e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::radio::V1_5::RegistrationFailCause o);
static inline void PrintTo(::android::hardware::radio::V1_5::RegistrationFailCause o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::RegistrationFailCause lhs, const ::android::hardware::radio::V1_5::RegistrationFailCause rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::radio::V1_5::RegistrationFailCause rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::RegistrationFailCause lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::RegistrationFailCause lhs, const ::android::hardware::radio::V1_5::RegistrationFailCause rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::radio::V1_5::RegistrationFailCause rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::RegistrationFailCause lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::radio::V1_5::RegistrationFailCause e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::radio::V1_5::RegistrationFailCause e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::radio::V1_5::PrlIndicator o);
static inline void PrintTo(::android::hardware::radio::V1_5::PrlIndicator o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::PrlIndicator lhs, const ::android::hardware::radio::V1_5::PrlIndicator rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::radio::V1_5::PrlIndicator rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::PrlIndicator lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::PrlIndicator lhs, const ::android::hardware::radio::V1_5::PrlIndicator rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::radio::V1_5::PrlIndicator rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::PrlIndicator lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::radio::V1_5::PrlIndicator e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::radio::V1_5::PrlIndicator e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo& lhs, const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo& lhs, const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo& lhs, const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo& lhs, const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo& lhs, const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo& lhs, const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::RegStateResult& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::RegStateResult& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::RegStateResult& lhs, const ::android::hardware::radio::V1_5::RegStateResult& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::RegStateResult& lhs, const ::android::hardware::radio::V1_5::RegStateResult& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::NetworkScanResult& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::NetworkScanResult& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::NetworkScanResult& lhs, const ::android::hardware::radio::V1_5::NetworkScanResult& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::NetworkScanResult& lhs, const ::android::hardware::radio::V1_5::NetworkScanResult& rhs);

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::radio::V1_5::PersoSubstate o);
static inline void PrintTo(::android::hardware::radio::V1_5::PersoSubstate o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::PersoSubstate lhs, const ::android::hardware::radio::V1_5::PersoSubstate rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::radio::V1_5::PersoSubstate rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::radio::V1_5::PersoSubstate lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::PersoSubstate lhs, const ::android::hardware::radio::V1_5::PersoSubstate rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::radio::V1_5::PersoSubstate rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::radio::V1_5::PersoSubstate lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::radio::V1_5::PersoSubstate e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::radio::V1_5::PersoSubstate e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::radio::V1_5::AppStatus& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::AppStatus& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::AppStatus& lhs, const ::android::hardware::radio::V1_5::AppStatus& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::AppStatus& lhs, const ::android::hardware::radio::V1_5::AppStatus& rhs);

static inline std::string toString(const ::android::hardware::radio::V1_5::CardStatus& o);
static inline void PrintTo(const ::android::hardware::radio::V1_5::CardStatus& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::radio::V1_5::CardStatus& lhs, const ::android::hardware::radio::V1_5::CardStatus& rhs);
static inline bool operator!=(const ::android::hardware::radio::V1_5::CardStatus& lhs, const ::android::hardware::radio::V1_5::CardStatus& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::radio::V1_5::SignalMeasurementType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::SignalMeasurementType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::radio::V1_5::SignalMeasurementType::RSSI) == static_cast<int32_t>(::android::hardware::radio::V1_5::SignalMeasurementType::RSSI)) {
        os += (first ? "" : " | ");
        os += "RSSI";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::SignalMeasurementType::RSSI;
    }
    if ((o & ::android::hardware::radio::V1_5::SignalMeasurementType::RSCP) == static_cast<int32_t>(::android::hardware::radio::V1_5::SignalMeasurementType::RSCP)) {
        os += (first ? "" : " | ");
        os += "RSCP";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::SignalMeasurementType::RSCP;
    }
    if ((o & ::android::hardware::radio::V1_5::SignalMeasurementType::RSRP) == static_cast<int32_t>(::android::hardware::radio::V1_5::SignalMeasurementType::RSRP)) {
        os += (first ? "" : " | ");
        os += "RSRP";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::SignalMeasurementType::RSRP;
    }
    if ((o & ::android::hardware::radio::V1_5::SignalMeasurementType::RSRQ) == static_cast<int32_t>(::android::hardware::radio::V1_5::SignalMeasurementType::RSRQ)) {
        os += (first ? "" : " | ");
        os += "RSRQ";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::SignalMeasurementType::RSRQ;
    }
    if ((o & ::android::hardware::radio::V1_5::SignalMeasurementType::RSSNR) == static_cast<int32_t>(::android::hardware::radio::V1_5::SignalMeasurementType::RSSNR)) {
        os += (first ? "" : " | ");
        os += "RSSNR";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::SignalMeasurementType::RSSNR;
    }
    if ((o & ::android::hardware::radio::V1_5::SignalMeasurementType::SSRSRP) == static_cast<int32_t>(::android::hardware::radio::V1_5::SignalMeasurementType::SSRSRP)) {
        os += (first ? "" : " | ");
        os += "SSRSRP";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::SignalMeasurementType::SSRSRP;
    }
    if ((o & ::android::hardware::radio::V1_5::SignalMeasurementType::SSRSRQ) == static_cast<int32_t>(::android::hardware::radio::V1_5::SignalMeasurementType::SSRSRQ)) {
        os += (first ? "" : " | ");
        os += "SSRSRQ";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::SignalMeasurementType::SSRSRQ;
    }
    if ((o & ::android::hardware::radio::V1_5::SignalMeasurementType::SSSINR) == static_cast<int32_t>(::android::hardware::radio::V1_5::SignalMeasurementType::SSSINR)) {
        os += (first ? "" : " | ");
        os += "SSSINR";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::SignalMeasurementType::SSSINR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::radio::V1_5::SignalMeasurementType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::radio::V1_5::SignalMeasurementType::RSSI) {
        return "RSSI";
    }
    if (o == ::android::hardware::radio::V1_5::SignalMeasurementType::RSCP) {
        return "RSCP";
    }
    if (o == ::android::hardware::radio::V1_5::SignalMeasurementType::RSRP) {
        return "RSRP";
    }
    if (o == ::android::hardware::radio::V1_5::SignalMeasurementType::RSRQ) {
        return "RSRQ";
    }
    if (o == ::android::hardware::radio::V1_5::SignalMeasurementType::RSSNR) {
        return "RSSNR";
    }
    if (o == ::android::hardware::radio::V1_5::SignalMeasurementType::SSRSRP) {
        return "SSRSRP";
    }
    if (o == ::android::hardware::radio::V1_5::SignalMeasurementType::SSRSRQ) {
        return "SSRSRQ";
    }
    if (o == ::android::hardware::radio::V1_5::SignalMeasurementType::SSSINR) {
        return "SSSINR";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::radio::V1_5::SignalMeasurementType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::SignalThresholdInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".signalMeasurement = ";
    os += ::android::hardware::radio::V1_5::toString(o.signalMeasurement);
    os += ", .hysteresisMs = ";
    os += ::android::hardware::toString(o.hysteresisMs);
    os += ", .hysteresisDb = ";
    os += ::android::hardware::toString(o.hysteresisDb);
    os += ", .thresholds = ";
    os += ::android::hardware::toString(o.thresholds);
    os += ", .isEnabled = ";
    os += ::android::hardware::toString(o.isEnabled);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::SignalThresholdInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::SignalThresholdInfo& lhs, const ::android::hardware::radio::V1_5::SignalThresholdInfo& rhs) {
    if (lhs.signalMeasurement != rhs.signalMeasurement) {
        return false;
    }
    if (lhs.hysteresisMs != rhs.hysteresisMs) {
        return false;
    }
    if (lhs.hysteresisDb != rhs.hysteresisDb) {
        return false;
    }
    if (lhs.thresholds != rhs.thresholds) {
        return false;
    }
    if (lhs.isEnabled != rhs.isEnabled) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::SignalThresholdInfo& lhs, const ::android::hardware::radio::V1_5::SignalThresholdInfo& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::radio::V1_5::AccessNetwork>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::AccessNetwork> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::radio::V1_5::AccessNetwork::GERAN) == static_cast<int32_t>(::android::hardware::radio::V1_5::AccessNetwork::GERAN)) {
        os += (first ? "" : " | ");
        os += "GERAN";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::AccessNetwork::GERAN;
    }
    if ((o & ::android::hardware::radio::V1_5::AccessNetwork::UTRAN) == static_cast<int32_t>(::android::hardware::radio::V1_5::AccessNetwork::UTRAN)) {
        os += (first ? "" : " | ");
        os += "UTRAN";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::AccessNetwork::UTRAN;
    }
    if ((o & ::android::hardware::radio::V1_5::AccessNetwork::EUTRAN) == static_cast<int32_t>(::android::hardware::radio::V1_5::AccessNetwork::EUTRAN)) {
        os += (first ? "" : " | ");
        os += "EUTRAN";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::AccessNetwork::EUTRAN;
    }
    if ((o & ::android::hardware::radio::V1_5::AccessNetwork::CDMA2000) == static_cast<int32_t>(::android::hardware::radio::V1_5::AccessNetwork::CDMA2000)) {
        os += (first ? "" : " | ");
        os += "CDMA2000";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::AccessNetwork::CDMA2000;
    }
    if ((o & ::android::hardware::radio::V1_5::AccessNetwork::IWLAN) == static_cast<int32_t>(::android::hardware::radio::V1_5::AccessNetwork::IWLAN)) {
        os += (first ? "" : " | ");
        os += "IWLAN";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::AccessNetwork::IWLAN;
    }
    if ((o & ::android::hardware::radio::V1_5::AccessNetwork::UNKNOWN) == static_cast<int32_t>(::android::hardware::radio::V1_5::AccessNetwork::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::AccessNetwork::UNKNOWN;
    }
    if ((o & ::android::hardware::radio::V1_5::AccessNetwork::NGRAN) == static_cast<int32_t>(::android::hardware::radio::V1_5::AccessNetwork::NGRAN)) {
        os += (first ? "" : " | ");
        os += "NGRAN";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::AccessNetwork::NGRAN;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::radio::V1_5::AccessNetwork o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::radio::V1_5::AccessNetwork::GERAN) {
        return "GERAN";
    }
    if (o == ::android::hardware::radio::V1_5::AccessNetwork::UTRAN) {
        return "UTRAN";
    }
    if (o == ::android::hardware::radio::V1_5::AccessNetwork::EUTRAN) {
        return "EUTRAN";
    }
    if (o == ::android::hardware::radio::V1_5::AccessNetwork::CDMA2000) {
        return "CDMA2000";
    }
    if (o == ::android::hardware::radio::V1_5::AccessNetwork::IWLAN) {
        return "IWLAN";
    }
    if (o == ::android::hardware::radio::V1_5::AccessNetwork::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::radio::V1_5::AccessNetwork::NGRAN) {
        return "NGRAN";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::radio::V1_5::AccessNetwork o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::radio::V1_5::RadioAccessNetworks>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::RadioAccessNetworks> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::radio::V1_5::RadioAccessNetworks::GERAN) == static_cast<int32_t>(::android::hardware::radio::V1_5::RadioAccessNetworks::GERAN)) {
        os += (first ? "" : " | ");
        os += "GERAN";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RadioAccessNetworks::GERAN;
    }
    if ((o & ::android::hardware::radio::V1_5::RadioAccessNetworks::UTRAN) == static_cast<int32_t>(::android::hardware::radio::V1_5::RadioAccessNetworks::UTRAN)) {
        os += (first ? "" : " | ");
        os += "UTRAN";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RadioAccessNetworks::UTRAN;
    }
    if ((o & ::android::hardware::radio::V1_5::RadioAccessNetworks::EUTRAN) == static_cast<int32_t>(::android::hardware::radio::V1_5::RadioAccessNetworks::EUTRAN)) {
        os += (first ? "" : " | ");
        os += "EUTRAN";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RadioAccessNetworks::EUTRAN;
    }
    if ((o & ::android::hardware::radio::V1_5::RadioAccessNetworks::UNKNOWN) == static_cast<int32_t>(::android::hardware::radio::V1_5::RadioAccessNetworks::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RadioAccessNetworks::UNKNOWN;
    }
    if ((o & ::android::hardware::radio::V1_5::RadioAccessNetworks::NGRAN) == static_cast<int32_t>(::android::hardware::radio::V1_5::RadioAccessNetworks::NGRAN)) {
        os += (first ? "" : " | ");
        os += "NGRAN";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RadioAccessNetworks::NGRAN;
    }
    if ((o & ::android::hardware::radio::V1_5::RadioAccessNetworks::CDMA2000) == static_cast<int32_t>(::android::hardware::radio::V1_5::RadioAccessNetworks::CDMA2000)) {
        os += (first ? "" : " | ");
        os += "CDMA2000";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RadioAccessNetworks::CDMA2000;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::radio::V1_5::RadioAccessNetworks o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::radio::V1_5::RadioAccessNetworks::GERAN) {
        return "GERAN";
    }
    if (o == ::android::hardware::radio::V1_5::RadioAccessNetworks::UTRAN) {
        return "UTRAN";
    }
    if (o == ::android::hardware::radio::V1_5::RadioAccessNetworks::EUTRAN) {
        return "EUTRAN";
    }
    if (o == ::android::hardware::radio::V1_5::RadioAccessNetworks::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::radio::V1_5::RadioAccessNetworks::NGRAN) {
        return "NGRAN";
    }
    if (o == ::android::hardware::radio::V1_5::RadioAccessNetworks::CDMA2000) {
        return "CDMA2000";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::radio::V1_5::RadioAccessNetworks o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands::hidl_discriminator::geranBands: {
            os += ".geranBands = ";
            os += toString(o.geranBands());
            break;
        }
        case ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands::hidl_discriminator::utranBands: {
            os += ".utranBands = ";
            os += toString(o.utranBands());
            break;
        }
        case ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands::hidl_discriminator::eutranBands: {
            os += ".eutranBands = ";
            os += toString(o.eutranBands());
            break;
        }
        case ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands::hidl_discriminator::ngranBands: {
            os += ".ngranBands = ";
            os += toString(o.ngranBands());
            break;
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) o.getDiscriminator()) + ").").c_str());
        }
    }
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands& lhs, const ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands::hidl_discriminator::geranBands: {
            return (lhs.geranBands() == rhs.geranBands());
        }
        case ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands::hidl_discriminator::utranBands: {
            return (lhs.utranBands() == rhs.utranBands());
        }
        case ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands::hidl_discriminator::eutranBands: {
            return (lhs.eutranBands() == rhs.eutranBands());
        }
        case ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands::hidl_discriminator::ngranBands: {
            return (lhs.ngranBands() == rhs.ngranBands());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands& lhs, const ::android::hardware::radio::V1_5::RadioAccessSpecifier::Bands& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::RadioAccessSpecifier& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".radioAccessNetwork = ";
    os += ::android::hardware::radio::V1_5::toString(o.radioAccessNetwork);
    os += ", .bands = ";
    os += ::android::hardware::radio::V1_5::toString(o.bands);
    os += ", .channels = ";
    os += ::android::hardware::toString(o.channels);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::RadioAccessSpecifier& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::RadioAccessSpecifier& lhs, const ::android::hardware::radio::V1_5::RadioAccessSpecifier& rhs) {
    if (lhs.radioAccessNetwork != rhs.radioAccessNetwork) {
        return false;
    }
    if (lhs.bands != rhs.bands) {
        return false;
    }
    if (lhs.channels != rhs.channels) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::RadioAccessSpecifier& lhs, const ::android::hardware::radio::V1_5::RadioAccessSpecifier& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::radio::V1_5::NgranBands>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::NgranBands> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_1) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_1)) {
        os += (first ? "" : " | ");
        os += "BAND_1";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_1;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_2) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_2)) {
        os += (first ? "" : " | ");
        os += "BAND_2";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_2;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_3) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_3)) {
        os += (first ? "" : " | ");
        os += "BAND_3";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_3;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_5) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_5)) {
        os += (first ? "" : " | ");
        os += "BAND_5";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_5;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_7) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_7)) {
        os += (first ? "" : " | ");
        os += "BAND_7";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_7;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_8) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_8)) {
        os += (first ? "" : " | ");
        os += "BAND_8";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_8;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_12) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_12)) {
        os += (first ? "" : " | ");
        os += "BAND_12";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_12;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_14) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_14)) {
        os += (first ? "" : " | ");
        os += "BAND_14";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_14;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_18) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_18)) {
        os += (first ? "" : " | ");
        os += "BAND_18";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_18;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_20) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_20)) {
        os += (first ? "" : " | ");
        os += "BAND_20";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_20;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_25) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_25)) {
        os += (first ? "" : " | ");
        os += "BAND_25";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_25;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_28) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_28)) {
        os += (first ? "" : " | ");
        os += "BAND_28";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_28;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_29) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_29)) {
        os += (first ? "" : " | ");
        os += "BAND_29";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_29;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_30) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_30)) {
        os += (first ? "" : " | ");
        os += "BAND_30";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_30;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_34) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_34)) {
        os += (first ? "" : " | ");
        os += "BAND_34";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_34;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_38) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_38)) {
        os += (first ? "" : " | ");
        os += "BAND_38";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_38;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_39) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_39)) {
        os += (first ? "" : " | ");
        os += "BAND_39";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_39;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_40) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_40)) {
        os += (first ? "" : " | ");
        os += "BAND_40";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_40;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_41) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_41)) {
        os += (first ? "" : " | ");
        os += "BAND_41";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_41;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_48) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_48)) {
        os += (first ? "" : " | ");
        os += "BAND_48";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_48;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_50) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_50)) {
        os += (first ? "" : " | ");
        os += "BAND_50";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_50;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_51) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_51)) {
        os += (first ? "" : " | ");
        os += "BAND_51";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_51;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_65) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_65)) {
        os += (first ? "" : " | ");
        os += "BAND_65";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_65;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_66) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_66)) {
        os += (first ? "" : " | ");
        os += "BAND_66";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_66;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_70) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_70)) {
        os += (first ? "" : " | ");
        os += "BAND_70";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_70;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_71) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_71)) {
        os += (first ? "" : " | ");
        os += "BAND_71";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_71;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_74) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_74)) {
        os += (first ? "" : " | ");
        os += "BAND_74";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_74;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_75) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_75)) {
        os += (first ? "" : " | ");
        os += "BAND_75";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_75;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_76) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_76)) {
        os += (first ? "" : " | ");
        os += "BAND_76";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_76;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_77) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_77)) {
        os += (first ? "" : " | ");
        os += "BAND_77";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_77;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_78) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_78)) {
        os += (first ? "" : " | ");
        os += "BAND_78";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_78;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_79) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_79)) {
        os += (first ? "" : " | ");
        os += "BAND_79";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_79;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_80) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_80)) {
        os += (first ? "" : " | ");
        os += "BAND_80";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_80;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_81) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_81)) {
        os += (first ? "" : " | ");
        os += "BAND_81";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_81;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_82) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_82)) {
        os += (first ? "" : " | ");
        os += "BAND_82";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_82;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_83) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_83)) {
        os += (first ? "" : " | ");
        os += "BAND_83";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_83;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_84) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_84)) {
        os += (first ? "" : " | ");
        os += "BAND_84";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_84;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_86) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_86)) {
        os += (first ? "" : " | ");
        os += "BAND_86";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_86;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_89) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_89)) {
        os += (first ? "" : " | ");
        os += "BAND_89";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_89;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_90) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_90)) {
        os += (first ? "" : " | ");
        os += "BAND_90";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_90;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_91) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_91)) {
        os += (first ? "" : " | ");
        os += "BAND_91";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_91;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_92) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_92)) {
        os += (first ? "" : " | ");
        os += "BAND_92";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_92;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_93) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_93)) {
        os += (first ? "" : " | ");
        os += "BAND_93";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_93;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_94) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_94)) {
        os += (first ? "" : " | ");
        os += "BAND_94";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_94;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_95) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_95)) {
        os += (first ? "" : " | ");
        os += "BAND_95";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_95;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_257) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_257)) {
        os += (first ? "" : " | ");
        os += "BAND_257";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_257;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_258) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_258)) {
        os += (first ? "" : " | ");
        os += "BAND_258";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_258;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_260) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_260)) {
        os += (first ? "" : " | ");
        os += "BAND_260";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_260;
    }
    if ((o & ::android::hardware::radio::V1_5::NgranBands::BAND_261) == static_cast<int32_t>(::android::hardware::radio::V1_5::NgranBands::BAND_261)) {
        os += (first ? "" : " | ");
        os += "BAND_261";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::NgranBands::BAND_261;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::radio::V1_5::NgranBands o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_1) {
        return "BAND_1";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_2) {
        return "BAND_2";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_3) {
        return "BAND_3";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_5) {
        return "BAND_5";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_7) {
        return "BAND_7";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_8) {
        return "BAND_8";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_12) {
        return "BAND_12";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_14) {
        return "BAND_14";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_18) {
        return "BAND_18";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_20) {
        return "BAND_20";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_25) {
        return "BAND_25";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_28) {
        return "BAND_28";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_29) {
        return "BAND_29";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_30) {
        return "BAND_30";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_34) {
        return "BAND_34";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_38) {
        return "BAND_38";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_39) {
        return "BAND_39";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_40) {
        return "BAND_40";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_41) {
        return "BAND_41";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_48) {
        return "BAND_48";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_50) {
        return "BAND_50";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_51) {
        return "BAND_51";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_65) {
        return "BAND_65";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_66) {
        return "BAND_66";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_70) {
        return "BAND_70";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_71) {
        return "BAND_71";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_74) {
        return "BAND_74";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_75) {
        return "BAND_75";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_76) {
        return "BAND_76";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_77) {
        return "BAND_77";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_78) {
        return "BAND_78";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_79) {
        return "BAND_79";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_80) {
        return "BAND_80";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_81) {
        return "BAND_81";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_82) {
        return "BAND_82";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_83) {
        return "BAND_83";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_84) {
        return "BAND_84";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_86) {
        return "BAND_86";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_89) {
        return "BAND_89";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_90) {
        return "BAND_90";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_91) {
        return "BAND_91";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_92) {
        return "BAND_92";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_93) {
        return "BAND_93";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_94) {
        return "BAND_94";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_95) {
        return "BAND_95";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_257) {
        return "BAND_257";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_258) {
        return "BAND_258";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_260) {
        return "BAND_260";
    }
    if (o == ::android::hardware::radio::V1_5::NgranBands::BAND_261) {
        return "BAND_261";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::radio::V1_5::NgranBands o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::radio::V1_5::UtranBands>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::UtranBands> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_1) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_1)) {
        os += (first ? "" : " | ");
        os += "BAND_1";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_1;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_2) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_2)) {
        os += (first ? "" : " | ");
        os += "BAND_2";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_2;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_3) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_3)) {
        os += (first ? "" : " | ");
        os += "BAND_3";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_3;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_4) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_4)) {
        os += (first ? "" : " | ");
        os += "BAND_4";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_4;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_5) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_5)) {
        os += (first ? "" : " | ");
        os += "BAND_5";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_5;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_6) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_6)) {
        os += (first ? "" : " | ");
        os += "BAND_6";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_6;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_7) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_7)) {
        os += (first ? "" : " | ");
        os += "BAND_7";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_7;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_8) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_8)) {
        os += (first ? "" : " | ");
        os += "BAND_8";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_8;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_9) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_9)) {
        os += (first ? "" : " | ");
        os += "BAND_9";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_9;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_10) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_10)) {
        os += (first ? "" : " | ");
        os += "BAND_10";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_10;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_11) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_11)) {
        os += (first ? "" : " | ");
        os += "BAND_11";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_11;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_12) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_12)) {
        os += (first ? "" : " | ");
        os += "BAND_12";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_12;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_13) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_13)) {
        os += (first ? "" : " | ");
        os += "BAND_13";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_13;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_14) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_14)) {
        os += (first ? "" : " | ");
        os += "BAND_14";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_14;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_19) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_19)) {
        os += (first ? "" : " | ");
        os += "BAND_19";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_19;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_20) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_20)) {
        os += (first ? "" : " | ");
        os += "BAND_20";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_20;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_21) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_21)) {
        os += (first ? "" : " | ");
        os += "BAND_21";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_21;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_22) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_22)) {
        os += (first ? "" : " | ");
        os += "BAND_22";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_22;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_25) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_25)) {
        os += (first ? "" : " | ");
        os += "BAND_25";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_25;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_26) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_26)) {
        os += (first ? "" : " | ");
        os += "BAND_26";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_26;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_A) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_A)) {
        os += (first ? "" : " | ");
        os += "BAND_A";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_A;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_B) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_B)) {
        os += (first ? "" : " | ");
        os += "BAND_B";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_B;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_C) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_C)) {
        os += (first ? "" : " | ");
        os += "BAND_C";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_C;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_D) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_D)) {
        os += (first ? "" : " | ");
        os += "BAND_D";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_D;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_E) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_E)) {
        os += (first ? "" : " | ");
        os += "BAND_E";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_E;
    }
    if ((o & ::android::hardware::radio::V1_5::UtranBands::BAND_F) == static_cast<int32_t>(::android::hardware::radio::V1_5::UtranBands::BAND_F)) {
        os += (first ? "" : " | ");
        os += "BAND_F";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::UtranBands::BAND_F;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::radio::V1_5::UtranBands o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_1) {
        return "BAND_1";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_2) {
        return "BAND_2";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_3) {
        return "BAND_3";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_4) {
        return "BAND_4";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_5) {
        return "BAND_5";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_6) {
        return "BAND_6";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_7) {
        return "BAND_7";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_8) {
        return "BAND_8";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_9) {
        return "BAND_9";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_10) {
        return "BAND_10";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_11) {
        return "BAND_11";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_12) {
        return "BAND_12";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_13) {
        return "BAND_13";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_14) {
        return "BAND_14";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_19) {
        return "BAND_19";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_20) {
        return "BAND_20";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_21) {
        return "BAND_21";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_22) {
        return "BAND_22";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_25) {
        return "BAND_25";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_26) {
        return "BAND_26";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_A) {
        return "BAND_A";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_B) {
        return "BAND_B";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_C) {
        return "BAND_C";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_D) {
        return "BAND_D";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_E) {
        return "BAND_E";
    }
    if (o == ::android::hardware::radio::V1_5::UtranBands::BAND_F) {
        return "BAND_F";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::radio::V1_5::UtranBands o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::radio::V1_5::EutranBands>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::EutranBands> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_1) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_1)) {
        os += (first ? "" : " | ");
        os += "BAND_1";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_1;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_2) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_2)) {
        os += (first ? "" : " | ");
        os += "BAND_2";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_2;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_3) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_3)) {
        os += (first ? "" : " | ");
        os += "BAND_3";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_3;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_4) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_4)) {
        os += (first ? "" : " | ");
        os += "BAND_4";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_4;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_5) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_5)) {
        os += (first ? "" : " | ");
        os += "BAND_5";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_5;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_6) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_6)) {
        os += (first ? "" : " | ");
        os += "BAND_6";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_6;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_7) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_7)) {
        os += (first ? "" : " | ");
        os += "BAND_7";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_7;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_8) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_8)) {
        os += (first ? "" : " | ");
        os += "BAND_8";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_8;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_9) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_9)) {
        os += (first ? "" : " | ");
        os += "BAND_9";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_9;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_10) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_10)) {
        os += (first ? "" : " | ");
        os += "BAND_10";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_10;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_11) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_11)) {
        os += (first ? "" : " | ");
        os += "BAND_11";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_11;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_12) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_12)) {
        os += (first ? "" : " | ");
        os += "BAND_12";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_12;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_13) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_13)) {
        os += (first ? "" : " | ");
        os += "BAND_13";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_13;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_14) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_14)) {
        os += (first ? "" : " | ");
        os += "BAND_14";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_14;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_17) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_17)) {
        os += (first ? "" : " | ");
        os += "BAND_17";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_17;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_18) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_18)) {
        os += (first ? "" : " | ");
        os += "BAND_18";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_18;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_19) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_19)) {
        os += (first ? "" : " | ");
        os += "BAND_19";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_19;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_20) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_20)) {
        os += (first ? "" : " | ");
        os += "BAND_20";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_20;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_21) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_21)) {
        os += (first ? "" : " | ");
        os += "BAND_21";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_21;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_22) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_22)) {
        os += (first ? "" : " | ");
        os += "BAND_22";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_22;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_23) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_23)) {
        os += (first ? "" : " | ");
        os += "BAND_23";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_23;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_24) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_24)) {
        os += (first ? "" : " | ");
        os += "BAND_24";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_24;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_25) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_25)) {
        os += (first ? "" : " | ");
        os += "BAND_25";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_25;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_26) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_26)) {
        os += (first ? "" : " | ");
        os += "BAND_26";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_26;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_27) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_27)) {
        os += (first ? "" : " | ");
        os += "BAND_27";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_27;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_28) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_28)) {
        os += (first ? "" : " | ");
        os += "BAND_28";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_28;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_30) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_30)) {
        os += (first ? "" : " | ");
        os += "BAND_30";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_30;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_31) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_31)) {
        os += (first ? "" : " | ");
        os += "BAND_31";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_31;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_33) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_33)) {
        os += (first ? "" : " | ");
        os += "BAND_33";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_33;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_34) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_34)) {
        os += (first ? "" : " | ");
        os += "BAND_34";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_34;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_35) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_35)) {
        os += (first ? "" : " | ");
        os += "BAND_35";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_35;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_36) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_36)) {
        os += (first ? "" : " | ");
        os += "BAND_36";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_36;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_37) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_37)) {
        os += (first ? "" : " | ");
        os += "BAND_37";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_37;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_38) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_38)) {
        os += (first ? "" : " | ");
        os += "BAND_38";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_38;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_39) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_39)) {
        os += (first ? "" : " | ");
        os += "BAND_39";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_39;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_40) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_40)) {
        os += (first ? "" : " | ");
        os += "BAND_40";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_40;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_41) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_41)) {
        os += (first ? "" : " | ");
        os += "BAND_41";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_41;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_42) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_42)) {
        os += (first ? "" : " | ");
        os += "BAND_42";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_42;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_43) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_43)) {
        os += (first ? "" : " | ");
        os += "BAND_43";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_43;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_44) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_44)) {
        os += (first ? "" : " | ");
        os += "BAND_44";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_44;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_45) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_45)) {
        os += (first ? "" : " | ");
        os += "BAND_45";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_45;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_46) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_46)) {
        os += (first ? "" : " | ");
        os += "BAND_46";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_46;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_47) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_47)) {
        os += (first ? "" : " | ");
        os += "BAND_47";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_47;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_48) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_48)) {
        os += (first ? "" : " | ");
        os += "BAND_48";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_48;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_65) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_65)) {
        os += (first ? "" : " | ");
        os += "BAND_65";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_65;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_66) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_66)) {
        os += (first ? "" : " | ");
        os += "BAND_66";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_66;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_68) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_68)) {
        os += (first ? "" : " | ");
        os += "BAND_68";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_68;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_70) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_70)) {
        os += (first ? "" : " | ");
        os += "BAND_70";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_70;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_49) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_49)) {
        os += (first ? "" : " | ");
        os += "BAND_49";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_49;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_50) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_50)) {
        os += (first ? "" : " | ");
        os += "BAND_50";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_50;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_51) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_51)) {
        os += (first ? "" : " | ");
        os += "BAND_51";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_51;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_52) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_52)) {
        os += (first ? "" : " | ");
        os += "BAND_52";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_52;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_53) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_53)) {
        os += (first ? "" : " | ");
        os += "BAND_53";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_53;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_71) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_71)) {
        os += (first ? "" : " | ");
        os += "BAND_71";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_71;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_72) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_72)) {
        os += (first ? "" : " | ");
        os += "BAND_72";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_72;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_73) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_73)) {
        os += (first ? "" : " | ");
        os += "BAND_73";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_73;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_74) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_74)) {
        os += (first ? "" : " | ");
        os += "BAND_74";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_74;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_85) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_85)) {
        os += (first ? "" : " | ");
        os += "BAND_85";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_85;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_87) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_87)) {
        os += (first ? "" : " | ");
        os += "BAND_87";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_87;
    }
    if ((o & ::android::hardware::radio::V1_5::EutranBands::BAND_88) == static_cast<int32_t>(::android::hardware::radio::V1_5::EutranBands::BAND_88)) {
        os += (first ? "" : " | ");
        os += "BAND_88";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::EutranBands::BAND_88;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::radio::V1_5::EutranBands o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_1) {
        return "BAND_1";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_2) {
        return "BAND_2";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_3) {
        return "BAND_3";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_4) {
        return "BAND_4";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_5) {
        return "BAND_5";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_6) {
        return "BAND_6";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_7) {
        return "BAND_7";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_8) {
        return "BAND_8";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_9) {
        return "BAND_9";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_10) {
        return "BAND_10";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_11) {
        return "BAND_11";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_12) {
        return "BAND_12";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_13) {
        return "BAND_13";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_14) {
        return "BAND_14";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_17) {
        return "BAND_17";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_18) {
        return "BAND_18";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_19) {
        return "BAND_19";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_20) {
        return "BAND_20";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_21) {
        return "BAND_21";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_22) {
        return "BAND_22";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_23) {
        return "BAND_23";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_24) {
        return "BAND_24";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_25) {
        return "BAND_25";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_26) {
        return "BAND_26";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_27) {
        return "BAND_27";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_28) {
        return "BAND_28";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_30) {
        return "BAND_30";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_31) {
        return "BAND_31";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_33) {
        return "BAND_33";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_34) {
        return "BAND_34";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_35) {
        return "BAND_35";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_36) {
        return "BAND_36";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_37) {
        return "BAND_37";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_38) {
        return "BAND_38";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_39) {
        return "BAND_39";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_40) {
        return "BAND_40";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_41) {
        return "BAND_41";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_42) {
        return "BAND_42";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_43) {
        return "BAND_43";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_44) {
        return "BAND_44";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_45) {
        return "BAND_45";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_46) {
        return "BAND_46";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_47) {
        return "BAND_47";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_48) {
        return "BAND_48";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_65) {
        return "BAND_65";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_66) {
        return "BAND_66";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_68) {
        return "BAND_68";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_70) {
        return "BAND_70";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_49) {
        return "BAND_49";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_50) {
        return "BAND_50";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_51) {
        return "BAND_51";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_52) {
        return "BAND_52";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_53) {
        return "BAND_53";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_71) {
        return "BAND_71";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_72) {
        return "BAND_72";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_73) {
        return "BAND_73";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_74) {
        return "BAND_74";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_85) {
        return "BAND_85";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_87) {
        return "BAND_87";
    }
    if (o == ::android::hardware::radio::V1_5::EutranBands::BAND_88) {
        return "BAND_88";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::radio::V1_5::EutranBands o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::NetworkScanRequest& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::radio::V1_1::toString(o.type);
    os += ", .interval = ";
    os += ::android::hardware::toString(o.interval);
    os += ", .specifiers = ";
    os += ::android::hardware::toString(o.specifiers);
    os += ", .maxSearchTime = ";
    os += ::android::hardware::toString(o.maxSearchTime);
    os += ", .incrementalResults = ";
    os += ::android::hardware::toString(o.incrementalResults);
    os += ", .incrementalResultsPeriodicity = ";
    os += ::android::hardware::toString(o.incrementalResultsPeriodicity);
    os += ", .mccMncs = ";
    os += ::android::hardware::toString(o.mccMncs);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::NetworkScanRequest& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::NetworkScanRequest& lhs, const ::android::hardware::radio::V1_5::NetworkScanRequest& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.interval != rhs.interval) {
        return false;
    }
    if (lhs.specifiers != rhs.specifiers) {
        return false;
    }
    if (lhs.maxSearchTime != rhs.maxSearchTime) {
        return false;
    }
    if (lhs.incrementalResults != rhs.incrementalResults) {
        return false;
    }
    if (lhs.incrementalResultsPeriodicity != rhs.incrementalResultsPeriodicity) {
        return false;
    }
    if (lhs.mccMncs != rhs.mccMncs) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::NetworkScanRequest& lhs, const ::android::hardware::radio::V1_5::NetworkScanRequest& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::radio::V1_5::ApnTypes>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::ApnTypes> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::radio::V1_5::ApnTypes::NONE) == static_cast<int32_t>(::android::hardware::radio::V1_5::ApnTypes::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::ApnTypes::NONE;
    }
    if ((o & ::android::hardware::radio::V1_5::ApnTypes::DEFAULT) == static_cast<int32_t>(::android::hardware::radio::V1_5::ApnTypes::DEFAULT)) {
        os += (first ? "" : " | ");
        os += "DEFAULT";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::ApnTypes::DEFAULT;
    }
    if ((o & ::android::hardware::radio::V1_5::ApnTypes::MMS) == static_cast<int32_t>(::android::hardware::radio::V1_5::ApnTypes::MMS)) {
        os += (first ? "" : " | ");
        os += "MMS";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::ApnTypes::MMS;
    }
    if ((o & ::android::hardware::radio::V1_5::ApnTypes::SUPL) == static_cast<int32_t>(::android::hardware::radio::V1_5::ApnTypes::SUPL)) {
        os += (first ? "" : " | ");
        os += "SUPL";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::ApnTypes::SUPL;
    }
    if ((o & ::android::hardware::radio::V1_5::ApnTypes::DUN) == static_cast<int32_t>(::android::hardware::radio::V1_5::ApnTypes::DUN)) {
        os += (first ? "" : " | ");
        os += "DUN";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::ApnTypes::DUN;
    }
    if ((o & ::android::hardware::radio::V1_5::ApnTypes::HIPRI) == static_cast<int32_t>(::android::hardware::radio::V1_5::ApnTypes::HIPRI)) {
        os += (first ? "" : " | ");
        os += "HIPRI";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::ApnTypes::HIPRI;
    }
    if ((o & ::android::hardware::radio::V1_5::ApnTypes::FOTA) == static_cast<int32_t>(::android::hardware::radio::V1_5::ApnTypes::FOTA)) {
        os += (first ? "" : " | ");
        os += "FOTA";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::ApnTypes::FOTA;
    }
    if ((o & ::android::hardware::radio::V1_5::ApnTypes::IMS) == static_cast<int32_t>(::android::hardware::radio::V1_5::ApnTypes::IMS)) {
        os += (first ? "" : " | ");
        os += "IMS";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::ApnTypes::IMS;
    }
    if ((o & ::android::hardware::radio::V1_5::ApnTypes::CBS) == static_cast<int32_t>(::android::hardware::radio::V1_5::ApnTypes::CBS)) {
        os += (first ? "" : " | ");
        os += "CBS";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::ApnTypes::CBS;
    }
    if ((o & ::android::hardware::radio::V1_5::ApnTypes::IA) == static_cast<int32_t>(::android::hardware::radio::V1_5::ApnTypes::IA)) {
        os += (first ? "" : " | ");
        os += "IA";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::ApnTypes::IA;
    }
    if ((o & ::android::hardware::radio::V1_5::ApnTypes::EMERGENCY) == static_cast<int32_t>(::android::hardware::radio::V1_5::ApnTypes::EMERGENCY)) {
        os += (first ? "" : " | ");
        os += "EMERGENCY";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::ApnTypes::EMERGENCY;
    }
    if ((o & ::android::hardware::radio::V1_5::ApnTypes::ALL) == static_cast<int32_t>(::android::hardware::radio::V1_5::ApnTypes::ALL)) {
        os += (first ? "" : " | ");
        os += "ALL";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::ApnTypes::ALL;
    }
    if ((o & ::android::hardware::radio::V1_5::ApnTypes::MCX) == static_cast<int32_t>(::android::hardware::radio::V1_5::ApnTypes::MCX)) {
        os += (first ? "" : " | ");
        os += "MCX";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::ApnTypes::MCX;
    }
    if ((o & ::android::hardware::radio::V1_5::ApnTypes::XCAP) == static_cast<int32_t>(::android::hardware::radio::V1_5::ApnTypes::XCAP)) {
        os += (first ? "" : " | ");
        os += "XCAP";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::ApnTypes::XCAP;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::radio::V1_5::ApnTypes o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::radio::V1_5::ApnTypes::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::radio::V1_5::ApnTypes::DEFAULT) {
        return "DEFAULT";
    }
    if (o == ::android::hardware::radio::V1_5::ApnTypes::MMS) {
        return "MMS";
    }
    if (o == ::android::hardware::radio::V1_5::ApnTypes::SUPL) {
        return "SUPL";
    }
    if (o == ::android::hardware::radio::V1_5::ApnTypes::DUN) {
        return "DUN";
    }
    if (o == ::android::hardware::radio::V1_5::ApnTypes::HIPRI) {
        return "HIPRI";
    }
    if (o == ::android::hardware::radio::V1_5::ApnTypes::FOTA) {
        return "FOTA";
    }
    if (o == ::android::hardware::radio::V1_5::ApnTypes::IMS) {
        return "IMS";
    }
    if (o == ::android::hardware::radio::V1_5::ApnTypes::CBS) {
        return "CBS";
    }
    if (o == ::android::hardware::radio::V1_5::ApnTypes::IA) {
        return "IA";
    }
    if (o == ::android::hardware::radio::V1_5::ApnTypes::EMERGENCY) {
        return "EMERGENCY";
    }
    if (o == ::android::hardware::radio::V1_5::ApnTypes::ALL) {
        return "ALL";
    }
    if (o == ::android::hardware::radio::V1_5::ApnTypes::MCX) {
        return "MCX";
    }
    if (o == ::android::hardware::radio::V1_5::ApnTypes::XCAP) {
        return "XCAP";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::radio::V1_5::ApnTypes o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::DataProfileInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".profileId = ";
    os += ::android::hardware::radio::V1_0::toString(o.profileId);
    os += ", .apn = ";
    os += ::android::hardware::toString(o.apn);
    os += ", .protocol = ";
    os += ::android::hardware::radio::V1_4::toString(o.protocol);
    os += ", .roamingProtocol = ";
    os += ::android::hardware::radio::V1_4::toString(o.roamingProtocol);
    os += ", .authType = ";
    os += ::android::hardware::radio::V1_0::toString(o.authType);
    os += ", .user = ";
    os += ::android::hardware::toString(o.user);
    os += ", .password = ";
    os += ::android::hardware::toString(o.password);
    os += ", .type = ";
    os += ::android::hardware::radio::V1_0::toString(o.type);
    os += ", .maxConnsTime = ";
    os += ::android::hardware::toString(o.maxConnsTime);
    os += ", .maxConns = ";
    os += ::android::hardware::toString(o.maxConns);
    os += ", .waitTime = ";
    os += ::android::hardware::toString(o.waitTime);
    os += ", .enabled = ";
    os += ::android::hardware::toString(o.enabled);
    os += ", .supportedApnTypesBitmap = ";
    os += ::android::hardware::radio::V1_5::toString<::android::hardware::radio::V1_5::ApnTypes>(o.supportedApnTypesBitmap);
    os += ", .bearerBitmap = ";
    os += ::android::hardware::radio::V1_4::toString<::android::hardware::radio::V1_4::RadioAccessFamily>(o.bearerBitmap);
    os += ", .mtuV4 = ";
    os += ::android::hardware::toString(o.mtuV4);
    os += ", .mtuV6 = ";
    os += ::android::hardware::toString(o.mtuV6);
    os += ", .preferred = ";
    os += ::android::hardware::toString(o.preferred);
    os += ", .persistent = ";
    os += ::android::hardware::toString(o.persistent);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::DataProfileInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::DataProfileInfo& lhs, const ::android::hardware::radio::V1_5::DataProfileInfo& rhs) {
    if (lhs.profileId != rhs.profileId) {
        return false;
    }
    if (lhs.apn != rhs.apn) {
        return false;
    }
    if (lhs.protocol != rhs.protocol) {
        return false;
    }
    if (lhs.roamingProtocol != rhs.roamingProtocol) {
        return false;
    }
    if (lhs.authType != rhs.authType) {
        return false;
    }
    if (lhs.user != rhs.user) {
        return false;
    }
    if (lhs.password != rhs.password) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.maxConnsTime != rhs.maxConnsTime) {
        return false;
    }
    if (lhs.maxConns != rhs.maxConns) {
        return false;
    }
    if (lhs.waitTime != rhs.waitTime) {
        return false;
    }
    if (lhs.enabled != rhs.enabled) {
        return false;
    }
    if (lhs.supportedApnTypesBitmap != rhs.supportedApnTypesBitmap) {
        return false;
    }
    if (lhs.bearerBitmap != rhs.bearerBitmap) {
        return false;
    }
    if (lhs.mtuV4 != rhs.mtuV4) {
        return false;
    }
    if (lhs.mtuV6 != rhs.mtuV6) {
        return false;
    }
    if (lhs.preferred != rhs.preferred) {
        return false;
    }
    if (lhs.persistent != rhs.persistent) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::DataProfileInfo& lhs, const ::android::hardware::radio::V1_5::DataProfileInfo& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::radio::V1_5::AddressProperty>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::AddressProperty> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::radio::V1_5::AddressProperty::NONE) == static_cast<int32_t>(::android::hardware::radio::V1_5::AddressProperty::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::AddressProperty::NONE;
    }
    if ((o & ::android::hardware::radio::V1_5::AddressProperty::DEPRECATED) == static_cast<int32_t>(::android::hardware::radio::V1_5::AddressProperty::DEPRECATED)) {
        os += (first ? "" : " | ");
        os += "DEPRECATED";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::AddressProperty::DEPRECATED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::radio::V1_5::AddressProperty o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::radio::V1_5::AddressProperty::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::radio::V1_5::AddressProperty::DEPRECATED) {
        return "DEPRECATED";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::radio::V1_5::AddressProperty o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::LinkAddress& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".address = ";
    os += ::android::hardware::toString(o.address);
    os += ", .properties = ";
    os += ::android::hardware::radio::V1_5::toString<::android::hardware::radio::V1_5::AddressProperty>(o.properties);
    os += ", .deprecationTime = ";
    os += ::android::hardware::toString(o.deprecationTime);
    os += ", .expirationTime = ";
    os += ::android::hardware::toString(o.expirationTime);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::LinkAddress& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::LinkAddress& lhs, const ::android::hardware::radio::V1_5::LinkAddress& rhs) {
    if (lhs.address != rhs.address) {
        return false;
    }
    if (lhs.properties != rhs.properties) {
        return false;
    }
    if (lhs.deprecationTime != rhs.deprecationTime) {
        return false;
    }
    if (lhs.expirationTime != rhs.expirationTime) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::LinkAddress& lhs, const ::android::hardware::radio::V1_5::LinkAddress& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::SetupDataCallResult& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".cause = ";
    os += ::android::hardware::radio::V1_4::toString(o.cause);
    os += ", .suggestedRetryTime = ";
    os += ::android::hardware::toString(o.suggestedRetryTime);
    os += ", .cid = ";
    os += ::android::hardware::toString(o.cid);
    os += ", .active = ";
    os += ::android::hardware::radio::V1_4::toString(o.active);
    os += ", .type = ";
    os += ::android::hardware::radio::V1_4::toString(o.type);
    os += ", .ifname = ";
    os += ::android::hardware::toString(o.ifname);
    os += ", .addresses = ";
    os += ::android::hardware::toString(o.addresses);
    os += ", .dnses = ";
    os += ::android::hardware::toString(o.dnses);
    os += ", .gateways = ";
    os += ::android::hardware::toString(o.gateways);
    os += ", .pcscf = ";
    os += ::android::hardware::toString(o.pcscf);
    os += ", .mtuV4 = ";
    os += ::android::hardware::toString(o.mtuV4);
    os += ", .mtuV6 = ";
    os += ::android::hardware::toString(o.mtuV6);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::SetupDataCallResult& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::SetupDataCallResult& lhs, const ::android::hardware::radio::V1_5::SetupDataCallResult& rhs) {
    if (lhs.cause != rhs.cause) {
        return false;
    }
    if (lhs.suggestedRetryTime != rhs.suggestedRetryTime) {
        return false;
    }
    if (lhs.cid != rhs.cid) {
        return false;
    }
    if (lhs.active != rhs.active) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.ifname != rhs.ifname) {
        return false;
    }
    if (lhs.addresses != rhs.addresses) {
        return false;
    }
    if (lhs.dnses != rhs.dnses) {
        return false;
    }
    if (lhs.gateways != rhs.gateways) {
        return false;
    }
    if (lhs.pcscf != rhs.pcscf) {
        return false;
    }
    if (lhs.mtuV4 != rhs.mtuV4) {
        return false;
    }
    if (lhs.mtuV6 != rhs.mtuV6) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::SetupDataCallResult& lhs, const ::android::hardware::radio::V1_5::SetupDataCallResult& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::radio::V1_5::Domain>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::Domain> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::radio::V1_5::Domain::CS) == static_cast<int32_t>(::android::hardware::radio::V1_5::Domain::CS)) {
        os += (first ? "" : " | ");
        os += "CS";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::Domain::CS;
    }
    if ((o & ::android::hardware::radio::V1_5::Domain::PS) == static_cast<int32_t>(::android::hardware::radio::V1_5::Domain::PS)) {
        os += (first ? "" : " | ");
        os += "PS";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::Domain::PS;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::radio::V1_5::Domain o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::radio::V1_5::Domain::CS) {
        return "CS";
    }
    if (o == ::android::hardware::radio::V1_5::Domain::PS) {
        return "PS";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::radio::V1_5::Domain o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".csgIndication = ";
    os += ::android::hardware::toString(o.csgIndication);
    os += ", .homeNodebName = ";
    os += ::android::hardware::toString(o.homeNodebName);
    os += ", .csgIdentity = ";
    os += ::android::hardware::toString(o.csgIdentity);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo& lhs, const ::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo& rhs) {
    if (lhs.csgIndication != rhs.csgIndication) {
        return false;
    }
    if (lhs.homeNodebName != rhs.homeNodebName) {
        return false;
    }
    if (lhs.csgIdentity != rhs.csgIdentity) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo& lhs, const ::android::hardware::radio::V1_5::ClosedSubscriberGroupInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::OptionalCsgInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::radio::V1_5::OptionalCsgInfo::hidl_discriminator::noinit: {
            os += ".noinit = ";
            os += toString(o.noinit());
            break;
        }
        case ::android::hardware::radio::V1_5::OptionalCsgInfo::hidl_discriminator::csgInfo: {
            os += ".csgInfo = ";
            os += toString(o.csgInfo());
            break;
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) o.getDiscriminator()) + ").").c_str());
        }
    }
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::OptionalCsgInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::OptionalCsgInfo& lhs, const ::android::hardware::radio::V1_5::OptionalCsgInfo& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::radio::V1_5::OptionalCsgInfo::hidl_discriminator::noinit: {
            return (lhs.noinit() == rhs.noinit());
        }
        case ::android::hardware::radio::V1_5::OptionalCsgInfo::hidl_discriminator::csgInfo: {
            return (lhs.csgInfo() == rhs.csgInfo());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::OptionalCsgInfo& lhs, const ::android::hardware::radio::V1_5::OptionalCsgInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::CellIdentityGsm& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".base = ";
    os += ::android::hardware::radio::V1_2::toString(o.base);
    os += ", .additionalPlmns = ";
    os += ::android::hardware::toString(o.additionalPlmns);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::CellIdentityGsm& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::CellIdentityGsm& lhs, const ::android::hardware::radio::V1_5::CellIdentityGsm& rhs) {
    if (lhs.base != rhs.base) {
        return false;
    }
    if (lhs.additionalPlmns != rhs.additionalPlmns) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::CellIdentityGsm& lhs, const ::android::hardware::radio::V1_5::CellIdentityGsm& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::CellIdentityWcdma& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".base = ";
    os += ::android::hardware::radio::V1_2::toString(o.base);
    os += ", .additionalPlmns = ";
    os += ::android::hardware::toString(o.additionalPlmns);
    os += ", .optionalCsgInfo = ";
    os += ::android::hardware::radio::V1_5::toString(o.optionalCsgInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::CellIdentityWcdma& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::CellIdentityWcdma& lhs, const ::android::hardware::radio::V1_5::CellIdentityWcdma& rhs) {
    if (lhs.base != rhs.base) {
        return false;
    }
    if (lhs.additionalPlmns != rhs.additionalPlmns) {
        return false;
    }
    if (lhs.optionalCsgInfo != rhs.optionalCsgInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::CellIdentityWcdma& lhs, const ::android::hardware::radio::V1_5::CellIdentityWcdma& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::CellIdentityTdscdma& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".base = ";
    os += ::android::hardware::radio::V1_2::toString(o.base);
    os += ", .additionalPlmns = ";
    os += ::android::hardware::toString(o.additionalPlmns);
    os += ", .optionalCsgInfo = ";
    os += ::android::hardware::radio::V1_5::toString(o.optionalCsgInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::CellIdentityTdscdma& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::CellIdentityTdscdma& lhs, const ::android::hardware::radio::V1_5::CellIdentityTdscdma& rhs) {
    if (lhs.base != rhs.base) {
        return false;
    }
    if (lhs.additionalPlmns != rhs.additionalPlmns) {
        return false;
    }
    if (lhs.optionalCsgInfo != rhs.optionalCsgInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::CellIdentityTdscdma& lhs, const ::android::hardware::radio::V1_5::CellIdentityTdscdma& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::CellIdentityLte& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".base = ";
    os += ::android::hardware::radio::V1_2::toString(o.base);
    os += ", .additionalPlmns = ";
    os += ::android::hardware::toString(o.additionalPlmns);
    os += ", .optionalCsgInfo = ";
    os += ::android::hardware::radio::V1_5::toString(o.optionalCsgInfo);
    os += ", .bands = ";
    os += ::android::hardware::toString(o.bands);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::CellIdentityLte& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::CellIdentityLte& lhs, const ::android::hardware::radio::V1_5::CellIdentityLte& rhs) {
    if (lhs.base != rhs.base) {
        return false;
    }
    if (lhs.additionalPlmns != rhs.additionalPlmns) {
        return false;
    }
    if (lhs.optionalCsgInfo != rhs.optionalCsgInfo) {
        return false;
    }
    if (lhs.bands != rhs.bands) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::CellIdentityLte& lhs, const ::android::hardware::radio::V1_5::CellIdentityLte& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::CellIdentityNr& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".base = ";
    os += ::android::hardware::radio::V1_4::toString(o.base);
    os += ", .additionalPlmns = ";
    os += ::android::hardware::toString(o.additionalPlmns);
    os += ", .bands = ";
    os += ::android::hardware::toString(o.bands);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::CellIdentityNr& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::CellIdentityNr& lhs, const ::android::hardware::radio::V1_5::CellIdentityNr& rhs) {
    if (lhs.base != rhs.base) {
        return false;
    }
    if (lhs.additionalPlmns != rhs.additionalPlmns) {
        return false;
    }
    if (lhs.bands != rhs.bands) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::CellIdentityNr& lhs, const ::android::hardware::radio::V1_5::CellIdentityNr& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::CellInfoGsm& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".cellIdentityGsm = ";
    os += ::android::hardware::radio::V1_5::toString(o.cellIdentityGsm);
    os += ", .signalStrengthGsm = ";
    os += ::android::hardware::radio::V1_0::toString(o.signalStrengthGsm);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::CellInfoGsm& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::CellInfoGsm& lhs, const ::android::hardware::radio::V1_5::CellInfoGsm& rhs) {
    if (lhs.cellIdentityGsm != rhs.cellIdentityGsm) {
        return false;
    }
    if (lhs.signalStrengthGsm != rhs.signalStrengthGsm) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::CellInfoGsm& lhs, const ::android::hardware::radio::V1_5::CellInfoGsm& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::CellInfoWcdma& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".cellIdentityWcdma = ";
    os += ::android::hardware::radio::V1_5::toString(o.cellIdentityWcdma);
    os += ", .signalStrengthWcdma = ";
    os += ::android::hardware::radio::V1_2::toString(o.signalStrengthWcdma);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::CellInfoWcdma& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::CellInfoWcdma& lhs, const ::android::hardware::radio::V1_5::CellInfoWcdma& rhs) {
    if (lhs.cellIdentityWcdma != rhs.cellIdentityWcdma) {
        return false;
    }
    if (lhs.signalStrengthWcdma != rhs.signalStrengthWcdma) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::CellInfoWcdma& lhs, const ::android::hardware::radio::V1_5::CellInfoWcdma& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::CellInfoTdscdma& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".cellIdentityTdscdma = ";
    os += ::android::hardware::radio::V1_5::toString(o.cellIdentityTdscdma);
    os += ", .signalStrengthTdscdma = ";
    os += ::android::hardware::radio::V1_2::toString(o.signalStrengthTdscdma);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::CellInfoTdscdma& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::CellInfoTdscdma& lhs, const ::android::hardware::radio::V1_5::CellInfoTdscdma& rhs) {
    if (lhs.cellIdentityTdscdma != rhs.cellIdentityTdscdma) {
        return false;
    }
    if (lhs.signalStrengthTdscdma != rhs.signalStrengthTdscdma) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::CellInfoTdscdma& lhs, const ::android::hardware::radio::V1_5::CellInfoTdscdma& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::CellInfoLte& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".cellIdentityLte = ";
    os += ::android::hardware::radio::V1_5::toString(o.cellIdentityLte);
    os += ", .signalStrengthLte = ";
    os += ::android::hardware::radio::V1_0::toString(o.signalStrengthLte);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::CellInfoLte& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::CellInfoLte& lhs, const ::android::hardware::radio::V1_5::CellInfoLte& rhs) {
    if (lhs.cellIdentityLte != rhs.cellIdentityLte) {
        return false;
    }
    if (lhs.signalStrengthLte != rhs.signalStrengthLte) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::CellInfoLte& lhs, const ::android::hardware::radio::V1_5::CellInfoLte& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::CellInfoNr& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".cellIdentityNr = ";
    os += ::android::hardware::radio::V1_5::toString(o.cellIdentityNr);
    os += ", .signalStrengthNr = ";
    os += ::android::hardware::radio::V1_4::toString(o.signalStrengthNr);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::CellInfoNr& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::CellInfoNr& lhs, const ::android::hardware::radio::V1_5::CellInfoNr& rhs) {
    if (lhs.cellIdentityNr != rhs.cellIdentityNr) {
        return false;
    }
    if (lhs.signalStrengthNr != rhs.signalStrengthNr) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::CellInfoNr& lhs, const ::android::hardware::radio::V1_5::CellInfoNr& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo::hidl_discriminator::gsm: {
            os += ".gsm = ";
            os += toString(o.gsm());
            break;
        }
        case ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo::hidl_discriminator::wcdma: {
            os += ".wcdma = ";
            os += toString(o.wcdma());
            break;
        }
        case ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo::hidl_discriminator::tdscdma: {
            os += ".tdscdma = ";
            os += toString(o.tdscdma());
            break;
        }
        case ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo::hidl_discriminator::lte: {
            os += ".lte = ";
            os += toString(o.lte());
            break;
        }
        case ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo::hidl_discriminator::nr: {
            os += ".nr = ";
            os += toString(o.nr());
            break;
        }
        case ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo::hidl_discriminator::cdma: {
            os += ".cdma = ";
            os += toString(o.cdma());
            break;
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) o.getDiscriminator()) + ").").c_str());
        }
    }
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo& lhs, const ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo::hidl_discriminator::gsm: {
            return (lhs.gsm() == rhs.gsm());
        }
        case ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo::hidl_discriminator::wcdma: {
            return (lhs.wcdma() == rhs.wcdma());
        }
        case ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo::hidl_discriminator::tdscdma: {
            return (lhs.tdscdma() == rhs.tdscdma());
        }
        case ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo::hidl_discriminator::lte: {
            return (lhs.lte() == rhs.lte());
        }
        case ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo::hidl_discriminator::nr: {
            return (lhs.nr() == rhs.nr());
        }
        case ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo::hidl_discriminator::cdma: {
            return (lhs.cdma() == rhs.cdma());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo& lhs, const ::android::hardware::radio::V1_5::CellInfo::CellInfoRatSpecificInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::CellInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".registered = ";
    os += ::android::hardware::toString(o.registered);
    os += ", .timeStampType = ";
    os += ::android::hardware::radio::V1_0::toString(o.timeStampType);
    os += ", .timeStamp = ";
    os += ::android::hardware::toString(o.timeStamp);
    os += ", .connectionStatus = ";
    os += ::android::hardware::radio::V1_2::toString(o.connectionStatus);
    os += ", .ratSpecificInfo = ";
    os += ::android::hardware::radio::V1_5::toString(o.ratSpecificInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::CellInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::CellInfo& lhs, const ::android::hardware::radio::V1_5::CellInfo& rhs) {
    if (lhs.registered != rhs.registered) {
        return false;
    }
    if (lhs.timeStampType != rhs.timeStampType) {
        return false;
    }
    if (lhs.timeStamp != rhs.timeStamp) {
        return false;
    }
    if (lhs.connectionStatus != rhs.connectionStatus) {
        return false;
    }
    if (lhs.ratSpecificInfo != rhs.ratSpecificInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::CellInfo& lhs, const ::android::hardware::radio::V1_5::CellInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::CellIdentity& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::radio::V1_5::CellIdentity::hidl_discriminator::noinit: {
            os += ".noinit = ";
            os += toString(o.noinit());
            break;
        }
        case ::android::hardware::radio::V1_5::CellIdentity::hidl_discriminator::gsm: {
            os += ".gsm = ";
            os += toString(o.gsm());
            break;
        }
        case ::android::hardware::radio::V1_5::CellIdentity::hidl_discriminator::wcdma: {
            os += ".wcdma = ";
            os += toString(o.wcdma());
            break;
        }
        case ::android::hardware::radio::V1_5::CellIdentity::hidl_discriminator::tdscdma: {
            os += ".tdscdma = ";
            os += toString(o.tdscdma());
            break;
        }
        case ::android::hardware::radio::V1_5::CellIdentity::hidl_discriminator::cdma: {
            os += ".cdma = ";
            os += toString(o.cdma());
            break;
        }
        case ::android::hardware::radio::V1_5::CellIdentity::hidl_discriminator::lte: {
            os += ".lte = ";
            os += toString(o.lte());
            break;
        }
        case ::android::hardware::radio::V1_5::CellIdentity::hidl_discriminator::nr: {
            os += ".nr = ";
            os += toString(o.nr());
            break;
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) o.getDiscriminator()) + ").").c_str());
        }
    }
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::CellIdentity& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::CellIdentity& lhs, const ::android::hardware::radio::V1_5::CellIdentity& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::radio::V1_5::CellIdentity::hidl_discriminator::noinit: {
            return (lhs.noinit() == rhs.noinit());
        }
        case ::android::hardware::radio::V1_5::CellIdentity::hidl_discriminator::gsm: {
            return (lhs.gsm() == rhs.gsm());
        }
        case ::android::hardware::radio::V1_5::CellIdentity::hidl_discriminator::wcdma: {
            return (lhs.wcdma() == rhs.wcdma());
        }
        case ::android::hardware::radio::V1_5::CellIdentity::hidl_discriminator::tdscdma: {
            return (lhs.tdscdma() == rhs.tdscdma());
        }
        case ::android::hardware::radio::V1_5::CellIdentity::hidl_discriminator::cdma: {
            return (lhs.cdma() == rhs.cdma());
        }
        case ::android::hardware::radio::V1_5::CellIdentity::hidl_discriminator::lte: {
            return (lhs.lte() == rhs.lte());
        }
        case ::android::hardware::radio::V1_5::CellIdentity::hidl_discriminator::nr: {
            return (lhs.nr() == rhs.nr());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::CellIdentity& lhs, const ::android::hardware::radio::V1_5::CellIdentity& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::radio::V1_5::BarringInfo::ServiceType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::BarringInfo::ServiceType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::CS_SERVICE) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::CS_SERVICE)) {
        os += (first ? "" : " | ");
        os += "CS_SERVICE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::CS_SERVICE;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::PS_SERVICE) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::PS_SERVICE)) {
        os += (first ? "" : " | ");
        os += "PS_SERVICE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::PS_SERVICE;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::CS_VOICE) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::CS_VOICE)) {
        os += (first ? "" : " | ");
        os += "CS_VOICE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::CS_VOICE;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::MO_SIGNALLING) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::MO_SIGNALLING)) {
        os += (first ? "" : " | ");
        os += "MO_SIGNALLING";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::MO_SIGNALLING;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::MO_DATA) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::MO_DATA)) {
        os += (first ? "" : " | ");
        os += "MO_DATA";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::MO_DATA;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::CS_FALLBACK) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::CS_FALLBACK)) {
        os += (first ? "" : " | ");
        os += "CS_FALLBACK";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::CS_FALLBACK;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::MMTEL_VOICE) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::MMTEL_VOICE)) {
        os += (first ? "" : " | ");
        os += "MMTEL_VOICE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::MMTEL_VOICE;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::MMTEL_VIDEO) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::MMTEL_VIDEO)) {
        os += (first ? "" : " | ");
        os += "MMTEL_VIDEO";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::MMTEL_VIDEO;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::EMERGENCY) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::EMERGENCY)) {
        os += (first ? "" : " | ");
        os += "EMERGENCY";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::EMERGENCY;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::SMS) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::SMS)) {
        os += (first ? "" : " | ");
        os += "SMS";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::SMS;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_1) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_1)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_1";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_1;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_2) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_2)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_2";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_2;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_3) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_3)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_3";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_3;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_4) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_4)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_4";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_4;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_5) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_5)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_5";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_5;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_6) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_6)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_6";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_6;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_7) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_7)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_7";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_7;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_8) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_8)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_8";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_8;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_9) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_9)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_9";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_9;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_10) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_10)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_10";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_10;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_11) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_11)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_11";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_11;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_12) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_12)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_12";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_12;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_13) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_13)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_13";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_13;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_14) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_14)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_14";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_14;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_15) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_15)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_15";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_15;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_16) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_16)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_16";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_16;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_17) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_17)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_17";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_17;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_18) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_18)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_18";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_18;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_19) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_19)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_19";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_19;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_20) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_20)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_20";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_20;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_21) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_21)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_21";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_21;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_22) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_22)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_22";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_22;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_23) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_23)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_23";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_23;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_24) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_24)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_24";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_24;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_25) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_25)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_25";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_25;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_26) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_26)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_26";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_26;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_27) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_27)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_27";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_27;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_28) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_28)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_28";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_28;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_29) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_29)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_29";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_29;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_30) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_30)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_30";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_30;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_31) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_31)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_31";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_31;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_32) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_32)) {
        os += (first ? "" : " | ");
        os += "OPERATOR_32";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_32;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::radio::V1_5::BarringInfo::ServiceType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::CS_SERVICE) {
        return "CS_SERVICE";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::PS_SERVICE) {
        return "PS_SERVICE";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::CS_VOICE) {
        return "CS_VOICE";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::MO_SIGNALLING) {
        return "MO_SIGNALLING";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::MO_DATA) {
        return "MO_DATA";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::CS_FALLBACK) {
        return "CS_FALLBACK";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::MMTEL_VOICE) {
        return "MMTEL_VOICE";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::MMTEL_VIDEO) {
        return "MMTEL_VIDEO";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::EMERGENCY) {
        return "EMERGENCY";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::SMS) {
        return "SMS";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_1) {
        return "OPERATOR_1";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_2) {
        return "OPERATOR_2";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_3) {
        return "OPERATOR_3";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_4) {
        return "OPERATOR_4";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_5) {
        return "OPERATOR_5";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_6) {
        return "OPERATOR_6";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_7) {
        return "OPERATOR_7";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_8) {
        return "OPERATOR_8";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_9) {
        return "OPERATOR_9";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_10) {
        return "OPERATOR_10";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_11) {
        return "OPERATOR_11";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_12) {
        return "OPERATOR_12";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_13) {
        return "OPERATOR_13";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_14) {
        return "OPERATOR_14";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_15) {
        return "OPERATOR_15";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_16) {
        return "OPERATOR_16";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_17) {
        return "OPERATOR_17";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_18) {
        return "OPERATOR_18";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_19) {
        return "OPERATOR_19";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_20) {
        return "OPERATOR_20";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_21) {
        return "OPERATOR_21";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_22) {
        return "OPERATOR_22";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_23) {
        return "OPERATOR_23";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_24) {
        return "OPERATOR_24";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_25) {
        return "OPERATOR_25";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_26) {
        return "OPERATOR_26";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_27) {
        return "OPERATOR_27";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_28) {
        return "OPERATOR_28";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_29) {
        return "OPERATOR_29";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_30) {
        return "OPERATOR_30";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_31) {
        return "OPERATOR_31";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_32) {
        return "OPERATOR_32";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::radio::V1_5::BarringInfo::ServiceType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::radio::V1_5::BarringInfo::BarringType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::BarringInfo::BarringType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::BarringType::NONE) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::BarringType::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::BarringType::NONE;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::BarringType::CONDITIONAL) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::BarringType::CONDITIONAL)) {
        os += (first ? "" : " | ");
        os += "CONDITIONAL";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::BarringType::CONDITIONAL;
    }
    if ((o & ::android::hardware::radio::V1_5::BarringInfo::BarringType::UNCONDITIONAL) == static_cast<int32_t>(::android::hardware::radio::V1_5::BarringInfo::BarringType::UNCONDITIONAL)) {
        os += (first ? "" : " | ");
        os += "UNCONDITIONAL";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::BarringInfo::BarringType::UNCONDITIONAL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::radio::V1_5::BarringInfo::BarringType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::radio::V1_5::BarringInfo::BarringType::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::BarringType::CONDITIONAL) {
        return "CONDITIONAL";
    }
    if (o == ::android::hardware::radio::V1_5::BarringInfo::BarringType::UNCONDITIONAL) {
        return "UNCONDITIONAL";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::radio::V1_5::BarringInfo::BarringType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".factor = ";
    os += ::android::hardware::toString(o.factor);
    os += ", .timeSeconds = ";
    os += ::android::hardware::toString(o.timeSeconds);
    os += ", .isBarred = ";
    os += ::android::hardware::toString(o.isBarred);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional& lhs, const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional& rhs) {
    if (lhs.factor != rhs.factor) {
        return false;
    }
    if (lhs.timeSeconds != rhs.timeSeconds) {
        return false;
    }
    if (lhs.isBarred != rhs.isBarred) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional& lhs, const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::Conditional& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::hidl_discriminator::noinit: {
            os += ".noinit = ";
            os += toString(o.noinit());
            break;
        }
        case ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::hidl_discriminator::conditional: {
            os += ".conditional = ";
            os += toString(o.conditional());
            break;
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) o.getDiscriminator()) + ").").c_str());
        }
    }
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo& lhs, const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::hidl_discriminator::noinit: {
            return (lhs.noinit() == rhs.noinit());
        }
        case ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo::hidl_discriminator::conditional: {
            return (lhs.conditional() == rhs.conditional());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo& lhs, const ::android::hardware::radio::V1_5::BarringInfo::BarringTypeSpecificInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::BarringInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".serviceType = ";
    os += ::android::hardware::radio::V1_5::toString(o.serviceType);
    os += ", .barringType = ";
    os += ::android::hardware::radio::V1_5::toString(o.barringType);
    os += ", .barringTypeSpecificInfo = ";
    os += ::android::hardware::radio::V1_5::toString(o.barringTypeSpecificInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::BarringInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::BarringInfo& lhs, const ::android::hardware::radio::V1_5::BarringInfo& rhs) {
    if (lhs.serviceType != rhs.serviceType) {
        return false;
    }
    if (lhs.barringType != rhs.barringType) {
        return false;
    }
    if (lhs.barringTypeSpecificInfo != rhs.barringTypeSpecificInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::BarringInfo& lhs, const ::android::hardware::radio::V1_5::BarringInfo& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::radio::V1_5::IndicationFilter>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::IndicationFilter> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::radio::V1_5::IndicationFilter::NONE) == static_cast<int32_t>(::android::hardware::radio::V1_5::IndicationFilter::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::IndicationFilter::NONE;
    }
    if ((o & ::android::hardware::radio::V1_5::IndicationFilter::ALL) == static_cast<int32_t>(::android::hardware::radio::V1_5::IndicationFilter::ALL)) {
        os += (first ? "" : " | ");
        os += "ALL";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::IndicationFilter::ALL;
    }
    if ((o & ::android::hardware::radio::V1_5::IndicationFilter::SIGNAL_STRENGTH) == static_cast<int32_t>(::android::hardware::radio::V1_5::IndicationFilter::SIGNAL_STRENGTH)) {
        os += (first ? "" : " | ");
        os += "SIGNAL_STRENGTH";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::IndicationFilter::SIGNAL_STRENGTH;
    }
    if ((o & ::android::hardware::radio::V1_5::IndicationFilter::FULL_NETWORK_STATE) == static_cast<int32_t>(::android::hardware::radio::V1_5::IndicationFilter::FULL_NETWORK_STATE)) {
        os += (first ? "" : " | ");
        os += "FULL_NETWORK_STATE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::IndicationFilter::FULL_NETWORK_STATE;
    }
    if ((o & ::android::hardware::radio::V1_5::IndicationFilter::DATA_CALL_DORMANCY_CHANGED) == static_cast<int32_t>(::android::hardware::radio::V1_5::IndicationFilter::DATA_CALL_DORMANCY_CHANGED)) {
        os += (first ? "" : " | ");
        os += "DATA_CALL_DORMANCY_CHANGED";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::IndicationFilter::DATA_CALL_DORMANCY_CHANGED;
    }
    if ((o & ::android::hardware::radio::V1_5::IndicationFilter::LINK_CAPACITY_ESTIMATE) == static_cast<int32_t>(::android::hardware::radio::V1_5::IndicationFilter::LINK_CAPACITY_ESTIMATE)) {
        os += (first ? "" : " | ");
        os += "LINK_CAPACITY_ESTIMATE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::IndicationFilter::LINK_CAPACITY_ESTIMATE;
    }
    if ((o & ::android::hardware::radio::V1_5::IndicationFilter::PHYSICAL_CHANNEL_CONFIG) == static_cast<int32_t>(::android::hardware::radio::V1_5::IndicationFilter::PHYSICAL_CHANNEL_CONFIG)) {
        os += (first ? "" : " | ");
        os += "PHYSICAL_CHANNEL_CONFIG";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::IndicationFilter::PHYSICAL_CHANNEL_CONFIG;
    }
    if ((o & ::android::hardware::radio::V1_5::IndicationFilter::REGISTRATION_FAILURE) == static_cast<int32_t>(::android::hardware::radio::V1_5::IndicationFilter::REGISTRATION_FAILURE)) {
        os += (first ? "" : " | ");
        os += "REGISTRATION_FAILURE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::IndicationFilter::REGISTRATION_FAILURE;
    }
    if ((o & ::android::hardware::radio::V1_5::IndicationFilter::BARRING_INFO) == static_cast<int32_t>(::android::hardware::radio::V1_5::IndicationFilter::BARRING_INFO)) {
        os += (first ? "" : " | ");
        os += "BARRING_INFO";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::IndicationFilter::BARRING_INFO;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::radio::V1_5::IndicationFilter o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::radio::V1_5::IndicationFilter::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::radio::V1_5::IndicationFilter::ALL) {
        return "ALL";
    }
    if (o == ::android::hardware::radio::V1_5::IndicationFilter::SIGNAL_STRENGTH) {
        return "SIGNAL_STRENGTH";
    }
    if (o == ::android::hardware::radio::V1_5::IndicationFilter::FULL_NETWORK_STATE) {
        return "FULL_NETWORK_STATE";
    }
    if (o == ::android::hardware::radio::V1_5::IndicationFilter::DATA_CALL_DORMANCY_CHANGED) {
        return "DATA_CALL_DORMANCY_CHANGED";
    }
    if (o == ::android::hardware::radio::V1_5::IndicationFilter::LINK_CAPACITY_ESTIMATE) {
        return "LINK_CAPACITY_ESTIMATE";
    }
    if (o == ::android::hardware::radio::V1_5::IndicationFilter::PHYSICAL_CHANNEL_CONFIG) {
        return "PHYSICAL_CHANNEL_CONFIG";
    }
    if (o == ::android::hardware::radio::V1_5::IndicationFilter::REGISTRATION_FAILURE) {
        return "REGISTRATION_FAILURE";
    }
    if (o == ::android::hardware::radio::V1_5::IndicationFilter::BARRING_INFO) {
        return "BARRING_INFO";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::radio::V1_5::IndicationFilter o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::radio::V1_5::RegistrationFailCause>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::RegistrationFailCause> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::NONE) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::NONE;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::IMSI_UNKNOWN_IN_HLR) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::IMSI_UNKNOWN_IN_HLR)) {
        os += (first ? "" : " | ");
        os += "IMSI_UNKNOWN_IN_HLR";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::IMSI_UNKNOWN_IN_HLR;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::ILLEGAL_MS) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::ILLEGAL_MS)) {
        os += (first ? "" : " | ");
        os += "ILLEGAL_MS";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::ILLEGAL_MS;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::IMSI_UNKNOWN_IN_VLR) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::IMSI_UNKNOWN_IN_VLR)) {
        os += (first ? "" : " | ");
        os += "IMSI_UNKNOWN_IN_VLR";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::IMSI_UNKNOWN_IN_VLR;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::IMEI_NOT_ACCEPTED) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::IMEI_NOT_ACCEPTED)) {
        os += (first ? "" : " | ");
        os += "IMEI_NOT_ACCEPTED";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::IMEI_NOT_ACCEPTED;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::ILLEGAL_ME) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::ILLEGAL_ME)) {
        os += (first ? "" : " | ");
        os += "ILLEGAL_ME";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::ILLEGAL_ME;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::GPRS_SERVICES_NOT_ALLOWED) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::GPRS_SERVICES_NOT_ALLOWED)) {
        os += (first ? "" : " | ");
        os += "GPRS_SERVICES_NOT_ALLOWED";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::GPRS_SERVICES_NOT_ALLOWED;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::GPRS_AND_NON_GPRS_SERVICES_NOT_ALLOWED) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::GPRS_AND_NON_GPRS_SERVICES_NOT_ALLOWED)) {
        os += (first ? "" : " | ");
        os += "GPRS_AND_NON_GPRS_SERVICES_NOT_ALLOWED";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::GPRS_AND_NON_GPRS_SERVICES_NOT_ALLOWED;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::MS_IDENTITY_CANNOT_BE_DERIVED_BY_NETWORK) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::MS_IDENTITY_CANNOT_BE_DERIVED_BY_NETWORK)) {
        os += (first ? "" : " | ");
        os += "MS_IDENTITY_CANNOT_BE_DERIVED_BY_NETWORK";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::MS_IDENTITY_CANNOT_BE_DERIVED_BY_NETWORK;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::IMPLICITLY_DETACHED) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::IMPLICITLY_DETACHED)) {
        os += (first ? "" : " | ");
        os += "IMPLICITLY_DETACHED";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::IMPLICITLY_DETACHED;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::PLMN_NOT_ALLOWED) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::PLMN_NOT_ALLOWED)) {
        os += (first ? "" : " | ");
        os += "PLMN_NOT_ALLOWED";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::PLMN_NOT_ALLOWED;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::LOCATION_AREA_NOT_ALLOWED) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::LOCATION_AREA_NOT_ALLOWED)) {
        os += (first ? "" : " | ");
        os += "LOCATION_AREA_NOT_ALLOWED";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::LOCATION_AREA_NOT_ALLOWED;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::ROAMING_NOT_ALLOWED) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::ROAMING_NOT_ALLOWED)) {
        os += (first ? "" : " | ");
        os += "ROAMING_NOT_ALLOWED";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::ROAMING_NOT_ALLOWED;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::GPRS_SERVICES_NOT_ALLOWED_IN_PLMN) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::GPRS_SERVICES_NOT_ALLOWED_IN_PLMN)) {
        os += (first ? "" : " | ");
        os += "GPRS_SERVICES_NOT_ALLOWED_IN_PLMN";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::GPRS_SERVICES_NOT_ALLOWED_IN_PLMN;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::NO_SUITABLE_CELLS) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::NO_SUITABLE_CELLS)) {
        os += (first ? "" : " | ");
        os += "NO_SUITABLE_CELLS";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::NO_SUITABLE_CELLS;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::MSC_TEMPORARILY_NOT_REACHABLE) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::MSC_TEMPORARILY_NOT_REACHABLE)) {
        os += (first ? "" : " | ");
        os += "MSC_TEMPORARILY_NOT_REACHABLE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::MSC_TEMPORARILY_NOT_REACHABLE;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::NETWORK_FAILURE) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::NETWORK_FAILURE)) {
        os += (first ? "" : " | ");
        os += "NETWORK_FAILURE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::NETWORK_FAILURE;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::MAC_FAILURE) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::MAC_FAILURE)) {
        os += (first ? "" : " | ");
        os += "MAC_FAILURE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::MAC_FAILURE;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::SYNC_FAILURE) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::SYNC_FAILURE)) {
        os += (first ? "" : " | ");
        os += "SYNC_FAILURE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::SYNC_FAILURE;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::CONGESTION) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::CONGESTION)) {
        os += (first ? "" : " | ");
        os += "CONGESTION";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::CONGESTION;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::GSM_AUTHENTICATION_UNACCEPTABLE) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::GSM_AUTHENTICATION_UNACCEPTABLE)) {
        os += (first ? "" : " | ");
        os += "GSM_AUTHENTICATION_UNACCEPTABLE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::GSM_AUTHENTICATION_UNACCEPTABLE;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::NOT_AUTHORIZED_FOR_THIS_CSG) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::NOT_AUTHORIZED_FOR_THIS_CSG)) {
        os += (first ? "" : " | ");
        os += "NOT_AUTHORIZED_FOR_THIS_CSG";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::NOT_AUTHORIZED_FOR_THIS_CSG;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::SMS_PROVIDED_BY_GPRS_IN_ROUTING_AREA) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::SMS_PROVIDED_BY_GPRS_IN_ROUTING_AREA)) {
        os += (first ? "" : " | ");
        os += "SMS_PROVIDED_BY_GPRS_IN_ROUTING_AREA";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::SMS_PROVIDED_BY_GPRS_IN_ROUTING_AREA;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::SERVICE_OPTION_NOT_SUPPORTED) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::SERVICE_OPTION_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "SERVICE_OPTION_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::SERVICE_OPTION_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::SERVICE_OPTION_NOT_SUBSCRIBED) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::SERVICE_OPTION_NOT_SUBSCRIBED)) {
        os += (first ? "" : " | ");
        os += "SERVICE_OPTION_NOT_SUBSCRIBED";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::SERVICE_OPTION_NOT_SUBSCRIBED;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::SERVICE_OPTION_TEMPORARILY_OUT_OF_ORDER) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::SERVICE_OPTION_TEMPORARILY_OUT_OF_ORDER)) {
        os += (first ? "" : " | ");
        os += "SERVICE_OPTION_TEMPORARILY_OUT_OF_ORDER";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::SERVICE_OPTION_TEMPORARILY_OUT_OF_ORDER;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::CALL_CANNOT_BE_IDENTIFIED) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::CALL_CANNOT_BE_IDENTIFIED)) {
        os += (first ? "" : " | ");
        os += "CALL_CANNOT_BE_IDENTIFIED";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::CALL_CANNOT_BE_IDENTIFIED;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::NO_PDP_CONTEXT_ACTIVATED) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::NO_PDP_CONTEXT_ACTIVATED)) {
        os += (first ? "" : " | ");
        os += "NO_PDP_CONTEXT_ACTIVATED";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::NO_PDP_CONTEXT_ACTIVATED;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_1) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_1)) {
        os += (first ? "" : " | ");
        os += "RETRY_UPON_ENTRY_INTO_NEW_CELL_1";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_1;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_2) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_2)) {
        os += (first ? "" : " | ");
        os += "RETRY_UPON_ENTRY_INTO_NEW_CELL_2";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_2;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_3) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_3)) {
        os += (first ? "" : " | ");
        os += "RETRY_UPON_ENTRY_INTO_NEW_CELL_3";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_3;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_4) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_4)) {
        os += (first ? "" : " | ");
        os += "RETRY_UPON_ENTRY_INTO_NEW_CELL_4";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_4;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_5) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_5)) {
        os += (first ? "" : " | ");
        os += "RETRY_UPON_ENTRY_INTO_NEW_CELL_5";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_5;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_6) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_6)) {
        os += (first ? "" : " | ");
        os += "RETRY_UPON_ENTRY_INTO_NEW_CELL_6";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_6;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_7) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_7)) {
        os += (first ? "" : " | ");
        os += "RETRY_UPON_ENTRY_INTO_NEW_CELL_7";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_7;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_8) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_8)) {
        os += (first ? "" : " | ");
        os += "RETRY_UPON_ENTRY_INTO_NEW_CELL_8";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_8;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_9) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_9)) {
        os += (first ? "" : " | ");
        os += "RETRY_UPON_ENTRY_INTO_NEW_CELL_9";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_9;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_10) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_10)) {
        os += (first ? "" : " | ");
        os += "RETRY_UPON_ENTRY_INTO_NEW_CELL_10";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_10;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_11) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_11)) {
        os += (first ? "" : " | ");
        os += "RETRY_UPON_ENTRY_INTO_NEW_CELL_11";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_11;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_12) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_12)) {
        os += (first ? "" : " | ");
        os += "RETRY_UPON_ENTRY_INTO_NEW_CELL_12";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_12;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_13) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_13)) {
        os += (first ? "" : " | ");
        os += "RETRY_UPON_ENTRY_INTO_NEW_CELL_13";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_13;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_14) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_14)) {
        os += (first ? "" : " | ");
        os += "RETRY_UPON_ENTRY_INTO_NEW_CELL_14";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_14;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_15) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_15)) {
        os += (first ? "" : " | ");
        os += "RETRY_UPON_ENTRY_INTO_NEW_CELL_15";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_15;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_16) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_16)) {
        os += (first ? "" : " | ");
        os += "RETRY_UPON_ENTRY_INTO_NEW_CELL_16";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_16;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::SEMANTICALLY_INCORRECT_MESSAGE) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::SEMANTICALLY_INCORRECT_MESSAGE)) {
        os += (first ? "" : " | ");
        os += "SEMANTICALLY_INCORRECT_MESSAGE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::SEMANTICALLY_INCORRECT_MESSAGE;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::INVALID_MANDATORY_INFORMATION) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::INVALID_MANDATORY_INFORMATION)) {
        os += (first ? "" : " | ");
        os += "INVALID_MANDATORY_INFORMATION";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::INVALID_MANDATORY_INFORMATION;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::MESSAGE_TYPE_NON_EXISTENT_OR_NOT_IMPLEMENTED) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::MESSAGE_TYPE_NON_EXISTENT_OR_NOT_IMPLEMENTED)) {
        os += (first ? "" : " | ");
        os += "MESSAGE_TYPE_NON_EXISTENT_OR_NOT_IMPLEMENTED";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::MESSAGE_TYPE_NON_EXISTENT_OR_NOT_IMPLEMENTED;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE)) {
        os += (first ? "" : " | ");
        os += "MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::INFORMATION_ELEMENT_NON_EXISTENT_OR_NOT_IMPLEMENTED) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::INFORMATION_ELEMENT_NON_EXISTENT_OR_NOT_IMPLEMENTED)) {
        os += (first ? "" : " | ");
        os += "INFORMATION_ELEMENT_NON_EXISTENT_OR_NOT_IMPLEMENTED";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::INFORMATION_ELEMENT_NON_EXISTENT_OR_NOT_IMPLEMENTED;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::CONDITIONAL_IE_ERROR) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::CONDITIONAL_IE_ERROR)) {
        os += (first ? "" : " | ");
        os += "CONDITIONAL_IE_ERROR";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::CONDITIONAL_IE_ERROR;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE)) {
        os += (first ? "" : " | ");
        os += "MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE;
    }
    if ((o & ::android::hardware::radio::V1_5::RegistrationFailCause::PROTOCOL_ERROR_UNSPECIFIED) == static_cast<int32_t>(::android::hardware::radio::V1_5::RegistrationFailCause::PROTOCOL_ERROR_UNSPECIFIED)) {
        os += (first ? "" : " | ");
        os += "PROTOCOL_ERROR_UNSPECIFIED";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::RegistrationFailCause::PROTOCOL_ERROR_UNSPECIFIED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::radio::V1_5::RegistrationFailCause o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::IMSI_UNKNOWN_IN_HLR) {
        return "IMSI_UNKNOWN_IN_HLR";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::ILLEGAL_MS) {
        return "ILLEGAL_MS";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::IMSI_UNKNOWN_IN_VLR) {
        return "IMSI_UNKNOWN_IN_VLR";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::IMEI_NOT_ACCEPTED) {
        return "IMEI_NOT_ACCEPTED";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::ILLEGAL_ME) {
        return "ILLEGAL_ME";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::GPRS_SERVICES_NOT_ALLOWED) {
        return "GPRS_SERVICES_NOT_ALLOWED";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::GPRS_AND_NON_GPRS_SERVICES_NOT_ALLOWED) {
        return "GPRS_AND_NON_GPRS_SERVICES_NOT_ALLOWED";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::MS_IDENTITY_CANNOT_BE_DERIVED_BY_NETWORK) {
        return "MS_IDENTITY_CANNOT_BE_DERIVED_BY_NETWORK";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::IMPLICITLY_DETACHED) {
        return "IMPLICITLY_DETACHED";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::PLMN_NOT_ALLOWED) {
        return "PLMN_NOT_ALLOWED";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::LOCATION_AREA_NOT_ALLOWED) {
        return "LOCATION_AREA_NOT_ALLOWED";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::ROAMING_NOT_ALLOWED) {
        return "ROAMING_NOT_ALLOWED";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::GPRS_SERVICES_NOT_ALLOWED_IN_PLMN) {
        return "GPRS_SERVICES_NOT_ALLOWED_IN_PLMN";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::NO_SUITABLE_CELLS) {
        return "NO_SUITABLE_CELLS";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::MSC_TEMPORARILY_NOT_REACHABLE) {
        return "MSC_TEMPORARILY_NOT_REACHABLE";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::NETWORK_FAILURE) {
        return "NETWORK_FAILURE";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::MAC_FAILURE) {
        return "MAC_FAILURE";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::SYNC_FAILURE) {
        return "SYNC_FAILURE";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::CONGESTION) {
        return "CONGESTION";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::GSM_AUTHENTICATION_UNACCEPTABLE) {
        return "GSM_AUTHENTICATION_UNACCEPTABLE";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::NOT_AUTHORIZED_FOR_THIS_CSG) {
        return "NOT_AUTHORIZED_FOR_THIS_CSG";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::SMS_PROVIDED_BY_GPRS_IN_ROUTING_AREA) {
        return "SMS_PROVIDED_BY_GPRS_IN_ROUTING_AREA";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::SERVICE_OPTION_NOT_SUPPORTED) {
        return "SERVICE_OPTION_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::SERVICE_OPTION_NOT_SUBSCRIBED) {
        return "SERVICE_OPTION_NOT_SUBSCRIBED";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::SERVICE_OPTION_TEMPORARILY_OUT_OF_ORDER) {
        return "SERVICE_OPTION_TEMPORARILY_OUT_OF_ORDER";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::CALL_CANNOT_BE_IDENTIFIED) {
        return "CALL_CANNOT_BE_IDENTIFIED";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::NO_PDP_CONTEXT_ACTIVATED) {
        return "NO_PDP_CONTEXT_ACTIVATED";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_1) {
        return "RETRY_UPON_ENTRY_INTO_NEW_CELL_1";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_2) {
        return "RETRY_UPON_ENTRY_INTO_NEW_CELL_2";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_3) {
        return "RETRY_UPON_ENTRY_INTO_NEW_CELL_3";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_4) {
        return "RETRY_UPON_ENTRY_INTO_NEW_CELL_4";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_5) {
        return "RETRY_UPON_ENTRY_INTO_NEW_CELL_5";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_6) {
        return "RETRY_UPON_ENTRY_INTO_NEW_CELL_6";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_7) {
        return "RETRY_UPON_ENTRY_INTO_NEW_CELL_7";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_8) {
        return "RETRY_UPON_ENTRY_INTO_NEW_CELL_8";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_9) {
        return "RETRY_UPON_ENTRY_INTO_NEW_CELL_9";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_10) {
        return "RETRY_UPON_ENTRY_INTO_NEW_CELL_10";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_11) {
        return "RETRY_UPON_ENTRY_INTO_NEW_CELL_11";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_12) {
        return "RETRY_UPON_ENTRY_INTO_NEW_CELL_12";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_13) {
        return "RETRY_UPON_ENTRY_INTO_NEW_CELL_13";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_14) {
        return "RETRY_UPON_ENTRY_INTO_NEW_CELL_14";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_15) {
        return "RETRY_UPON_ENTRY_INTO_NEW_CELL_15";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_16) {
        return "RETRY_UPON_ENTRY_INTO_NEW_CELL_16";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::SEMANTICALLY_INCORRECT_MESSAGE) {
        return "SEMANTICALLY_INCORRECT_MESSAGE";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::INVALID_MANDATORY_INFORMATION) {
        return "INVALID_MANDATORY_INFORMATION";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::MESSAGE_TYPE_NON_EXISTENT_OR_NOT_IMPLEMENTED) {
        return "MESSAGE_TYPE_NON_EXISTENT_OR_NOT_IMPLEMENTED";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE) {
        return "MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::INFORMATION_ELEMENT_NON_EXISTENT_OR_NOT_IMPLEMENTED) {
        return "INFORMATION_ELEMENT_NON_EXISTENT_OR_NOT_IMPLEMENTED";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::CONDITIONAL_IE_ERROR) {
        return "CONDITIONAL_IE_ERROR";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE) {
        return "MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE";
    }
    if (o == ::android::hardware::radio::V1_5::RegistrationFailCause::PROTOCOL_ERROR_UNSPECIFIED) {
        return "PROTOCOL_ERROR_UNSPECIFIED";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::radio::V1_5::RegistrationFailCause o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::radio::V1_5::PrlIndicator>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::PrlIndicator> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::radio::V1_5::PrlIndicator::NOT_REGISTERED) == static_cast<int32_t>(::android::hardware::radio::V1_5::PrlIndicator::NOT_REGISTERED)) {
        os += (first ? "" : " | ");
        os += "NOT_REGISTERED";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PrlIndicator::NOT_REGISTERED;
    }
    if ((o & ::android::hardware::radio::V1_5::PrlIndicator::NOT_IN_PRL) == static_cast<int32_t>(::android::hardware::radio::V1_5::PrlIndicator::NOT_IN_PRL)) {
        os += (first ? "" : " | ");
        os += "NOT_IN_PRL";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PrlIndicator::NOT_IN_PRL;
    }
    if ((o & ::android::hardware::radio::V1_5::PrlIndicator::IN_PRL) == static_cast<int32_t>(::android::hardware::radio::V1_5::PrlIndicator::IN_PRL)) {
        os += (first ? "" : " | ");
        os += "IN_PRL";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PrlIndicator::IN_PRL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::radio::V1_5::PrlIndicator o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::radio::V1_5::PrlIndicator::NOT_REGISTERED) {
        return "NOT_REGISTERED";
    }
    if (o == ::android::hardware::radio::V1_5::PrlIndicator::NOT_IN_PRL) {
        return "NOT_IN_PRL";
    }
    if (o == ::android::hardware::radio::V1_5::PrlIndicator::IN_PRL) {
        return "IN_PRL";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::radio::V1_5::PrlIndicator o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".cssSupported = ";
    os += ::android::hardware::toString(o.cssSupported);
    os += ", .roamingIndicator = ";
    os += ::android::hardware::toString(o.roamingIndicator);
    os += ", .systemIsInPrl = ";
    os += ::android::hardware::radio::V1_5::toString(o.systemIsInPrl);
    os += ", .defaultRoamingIndicator = ";
    os += ::android::hardware::toString(o.defaultRoamingIndicator);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo& lhs, const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo& rhs) {
    if (lhs.cssSupported != rhs.cssSupported) {
        return false;
    }
    if (lhs.roamingIndicator != rhs.roamingIndicator) {
        return false;
    }
    if (lhs.systemIsInPrl != rhs.systemIsInPrl) {
        return false;
    }
    if (lhs.defaultRoamingIndicator != rhs.defaultRoamingIndicator) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo& lhs, const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::Cdma2000RegistrationInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".lteVopsInfo = ";
    os += ::android::hardware::radio::V1_4::toString(o.lteVopsInfo);
    os += ", .nrIndicators = ";
    os += ::android::hardware::radio::V1_4::toString(o.nrIndicators);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo& lhs, const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo& rhs) {
    if (lhs.lteVopsInfo != rhs.lteVopsInfo) {
        return false;
    }
    if (lhs.nrIndicators != rhs.nrIndicators) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo& lhs, const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::EutranRegistrationInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";

    switch (o.getDiscriminator()) {
        case ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::hidl_discriminator::noinit: {
            os += ".noinit = ";
            os += toString(o.noinit());
            break;
        }
        case ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::hidl_discriminator::cdmaInfo: {
            os += ".cdmaInfo = ";
            os += toString(o.cdmaInfo());
            break;
        }
        case ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::hidl_discriminator::eutranInfo: {
            os += ".eutranInfo = ";
            os += toString(o.eutranInfo());
            break;
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) o.getDiscriminator()) + ").").c_str());
        }
    }
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo& lhs, const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo& rhs) {
    if (lhs.getDiscriminator() != rhs.getDiscriminator()) {
        return false;
    }
    switch (lhs.getDiscriminator()) {
        case ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::hidl_discriminator::noinit: {
            return (lhs.noinit() == rhs.noinit());
        }
        case ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::hidl_discriminator::cdmaInfo: {
            return (lhs.cdmaInfo() == rhs.cdmaInfo());
        }
        case ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo::hidl_discriminator::eutranInfo: {
            return (lhs.eutranInfo() == rhs.eutranInfo());
        }
        default: {
            ::android::hardware::details::logAlwaysFatal((
                    "Unknown union discriminator (value: " +
                    std::to_string((uint8_t) lhs.getDiscriminator()) + ").").c_str());
        }
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo& lhs, const ::android::hardware::radio::V1_5::RegStateResult::AccessTechnologySpecificInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::RegStateResult& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".regState = ";
    os += ::android::hardware::radio::V1_0::toString(o.regState);
    os += ", .rat = ";
    os += ::android::hardware::radio::V1_4::toString(o.rat);
    os += ", .reasonForDenial = ";
    os += ::android::hardware::radio::V1_5::toString(o.reasonForDenial);
    os += ", .cellIdentity = ";
    os += ::android::hardware::radio::V1_5::toString(o.cellIdentity);
    os += ", .registeredPlmn = ";
    os += ::android::hardware::toString(o.registeredPlmn);
    os += ", .accessTechnologySpecificInfo = ";
    os += ::android::hardware::radio::V1_5::toString(o.accessTechnologySpecificInfo);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::RegStateResult& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::RegStateResult& lhs, const ::android::hardware::radio::V1_5::RegStateResult& rhs) {
    if (lhs.regState != rhs.regState) {
        return false;
    }
    if (lhs.rat != rhs.rat) {
        return false;
    }
    if (lhs.reasonForDenial != rhs.reasonForDenial) {
        return false;
    }
    if (lhs.cellIdentity != rhs.cellIdentity) {
        return false;
    }
    if (lhs.registeredPlmn != rhs.registeredPlmn) {
        return false;
    }
    if (lhs.accessTechnologySpecificInfo != rhs.accessTechnologySpecificInfo) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::RegStateResult& lhs, const ::android::hardware::radio::V1_5::RegStateResult& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::NetworkScanResult& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".status = ";
    os += ::android::hardware::radio::V1_1::toString(o.status);
    os += ", .error = ";
    os += ::android::hardware::radio::V1_0::toString(o.error);
    os += ", .networkInfos = ";
    os += ::android::hardware::toString(o.networkInfos);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::NetworkScanResult& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::NetworkScanResult& lhs, const ::android::hardware::radio::V1_5::NetworkScanResult& rhs) {
    if (lhs.status != rhs.status) {
        return false;
    }
    if (lhs.error != rhs.error) {
        return false;
    }
    if (lhs.networkInfos != rhs.networkInfos) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::NetworkScanResult& lhs, const ::android::hardware::radio::V1_5::NetworkScanResult& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::radio::V1_5::PersoSubstate>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::radio::V1_5::PersoSubstate> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::UNKNOWN) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "UNKNOWN";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::UNKNOWN;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::IN_PROGRESS) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::IN_PROGRESS)) {
        os += (first ? "" : " | ");
        os += "IN_PROGRESS";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::IN_PROGRESS;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::READY) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::READY)) {
        os += (first ? "" : " | ");
        os += "READY";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::READY;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK)) {
        os += (first ? "" : " | ");
        os += "SIM_NETWORK";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK_SUBSET) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK_SUBSET)) {
        os += (first ? "" : " | ");
        os += "SIM_NETWORK_SUBSET";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK_SUBSET;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_CORPORATE) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_CORPORATE)) {
        os += (first ? "" : " | ");
        os += "SIM_CORPORATE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_CORPORATE;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_SERVICE_PROVIDER) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_SERVICE_PROVIDER)) {
        os += (first ? "" : " | ");
        os += "SIM_SERVICE_PROVIDER";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_SERVICE_PROVIDER;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_SIM) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_SIM)) {
        os += (first ? "" : " | ");
        os += "SIM_SIM";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_SIM;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK_PUK) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK_PUK)) {
        os += (first ? "" : " | ");
        os += "SIM_NETWORK_PUK";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK_PUK;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK_SUBSET_PUK) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK_SUBSET_PUK)) {
        os += (first ? "" : " | ");
        os += "SIM_NETWORK_SUBSET_PUK";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK_SUBSET_PUK;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_CORPORATE_PUK) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_CORPORATE_PUK)) {
        os += (first ? "" : " | ");
        os += "SIM_CORPORATE_PUK";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_CORPORATE_PUK;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_SERVICE_PROVIDER_PUK) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_SERVICE_PROVIDER_PUK)) {
        os += (first ? "" : " | ");
        os += "SIM_SERVICE_PROVIDER_PUK";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_SERVICE_PROVIDER_PUK;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_SIM_PUK) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_SIM_PUK)) {
        os += (first ? "" : " | ");
        os += "SIM_SIM_PUK";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_SIM_PUK;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK1) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK1)) {
        os += (first ? "" : " | ");
        os += "RUIM_NETWORK1";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK1;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK2) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK2)) {
        os += (first ? "" : " | ");
        os += "RUIM_NETWORK2";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK2;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::RUIM_HRPD) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::RUIM_HRPD)) {
        os += (first ? "" : " | ");
        os += "RUIM_HRPD";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::RUIM_HRPD;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::RUIM_CORPORATE) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::RUIM_CORPORATE)) {
        os += (first ? "" : " | ");
        os += "RUIM_CORPORATE";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::RUIM_CORPORATE;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::RUIM_SERVICE_PROVIDER) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::RUIM_SERVICE_PROVIDER)) {
        os += (first ? "" : " | ");
        os += "RUIM_SERVICE_PROVIDER";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::RUIM_SERVICE_PROVIDER;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::RUIM_RUIM) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::RUIM_RUIM)) {
        os += (first ? "" : " | ");
        os += "RUIM_RUIM";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::RUIM_RUIM;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK1_PUK) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK1_PUK)) {
        os += (first ? "" : " | ");
        os += "RUIM_NETWORK1_PUK";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK1_PUK;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK2_PUK) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK2_PUK)) {
        os += (first ? "" : " | ");
        os += "RUIM_NETWORK2_PUK";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK2_PUK;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::RUIM_HRPD_PUK) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::RUIM_HRPD_PUK)) {
        os += (first ? "" : " | ");
        os += "RUIM_HRPD_PUK";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::RUIM_HRPD_PUK;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::RUIM_CORPORATE_PUK) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::RUIM_CORPORATE_PUK)) {
        os += (first ? "" : " | ");
        os += "RUIM_CORPORATE_PUK";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::RUIM_CORPORATE_PUK;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::RUIM_SERVICE_PROVIDER_PUK) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::RUIM_SERVICE_PROVIDER_PUK)) {
        os += (first ? "" : " | ");
        os += "RUIM_SERVICE_PROVIDER_PUK";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::RUIM_SERVICE_PROVIDER_PUK;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::RUIM_RUIM_PUK) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::RUIM_RUIM_PUK)) {
        os += (first ? "" : " | ");
        os += "RUIM_RUIM_PUK";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::RUIM_RUIM_PUK;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_SPN) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_SPN)) {
        os += (first ? "" : " | ");
        os += "SIM_SPN";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_SPN;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_SPN_PUK) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_SPN_PUK)) {
        os += (first ? "" : " | ");
        os += "SIM_SPN_PUK";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_SPN_PUK;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_SP_EHPLMN) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_SP_EHPLMN)) {
        os += (first ? "" : " | ");
        os += "SIM_SP_EHPLMN";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_SP_EHPLMN;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_SP_EHPLMN_PUK) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_SP_EHPLMN_PUK)) {
        os += (first ? "" : " | ");
        os += "SIM_SP_EHPLMN_PUK";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_SP_EHPLMN_PUK;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_ICCID) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_ICCID)) {
        os += (first ? "" : " | ");
        os += "SIM_ICCID";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_ICCID;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_ICCID_PUK) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_ICCID_PUK)) {
        os += (first ? "" : " | ");
        os += "SIM_ICCID_PUK";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_ICCID_PUK;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_IMPI) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_IMPI)) {
        os += (first ? "" : " | ");
        os += "SIM_IMPI";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_IMPI;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_IMPI_PUK) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_IMPI_PUK)) {
        os += (first ? "" : " | ");
        os += "SIM_IMPI_PUK";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_IMPI_PUK;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_NS_SP) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_NS_SP)) {
        os += (first ? "" : " | ");
        os += "SIM_NS_SP";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_NS_SP;
    }
    if ((o & ::android::hardware::radio::V1_5::PersoSubstate::SIM_NS_SP_PUK) == static_cast<int32_t>(::android::hardware::radio::V1_5::PersoSubstate::SIM_NS_SP_PUK)) {
        os += (first ? "" : " | ");
        os += "SIM_NS_SP_PUK";
        first = false;
        flipped |= ::android::hardware::radio::V1_5::PersoSubstate::SIM_NS_SP_PUK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::radio::V1_5::PersoSubstate o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::UNKNOWN) {
        return "UNKNOWN";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::IN_PROGRESS) {
        return "IN_PROGRESS";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::READY) {
        return "READY";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK) {
        return "SIM_NETWORK";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK_SUBSET) {
        return "SIM_NETWORK_SUBSET";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_CORPORATE) {
        return "SIM_CORPORATE";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_SERVICE_PROVIDER) {
        return "SIM_SERVICE_PROVIDER";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_SIM) {
        return "SIM_SIM";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK_PUK) {
        return "SIM_NETWORK_PUK";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK_SUBSET_PUK) {
        return "SIM_NETWORK_SUBSET_PUK";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_CORPORATE_PUK) {
        return "SIM_CORPORATE_PUK";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_SERVICE_PROVIDER_PUK) {
        return "SIM_SERVICE_PROVIDER_PUK";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_SIM_PUK) {
        return "SIM_SIM_PUK";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK1) {
        return "RUIM_NETWORK1";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK2) {
        return "RUIM_NETWORK2";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::RUIM_HRPD) {
        return "RUIM_HRPD";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::RUIM_CORPORATE) {
        return "RUIM_CORPORATE";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::RUIM_SERVICE_PROVIDER) {
        return "RUIM_SERVICE_PROVIDER";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::RUIM_RUIM) {
        return "RUIM_RUIM";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK1_PUK) {
        return "RUIM_NETWORK1_PUK";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK2_PUK) {
        return "RUIM_NETWORK2_PUK";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::RUIM_HRPD_PUK) {
        return "RUIM_HRPD_PUK";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::RUIM_CORPORATE_PUK) {
        return "RUIM_CORPORATE_PUK";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::RUIM_SERVICE_PROVIDER_PUK) {
        return "RUIM_SERVICE_PROVIDER_PUK";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::RUIM_RUIM_PUK) {
        return "RUIM_RUIM_PUK";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_SPN) {
        return "SIM_SPN";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_SPN_PUK) {
        return "SIM_SPN_PUK";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_SP_EHPLMN) {
        return "SIM_SP_EHPLMN";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_SP_EHPLMN_PUK) {
        return "SIM_SP_EHPLMN_PUK";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_ICCID) {
        return "SIM_ICCID";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_ICCID_PUK) {
        return "SIM_ICCID_PUK";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_IMPI) {
        return "SIM_IMPI";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_IMPI_PUK) {
        return "SIM_IMPI_PUK";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_NS_SP) {
        return "SIM_NS_SP";
    }
    if (o == ::android::hardware::radio::V1_5::PersoSubstate::SIM_NS_SP_PUK) {
        return "SIM_NS_SP_PUK";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::radio::V1_5::PersoSubstate o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::AppStatus& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".base = ";
    os += ::android::hardware::radio::V1_0::toString(o.base);
    os += ", .persoSubstate = ";
    os += ::android::hardware::radio::V1_5::toString(o.persoSubstate);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::AppStatus& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::AppStatus& lhs, const ::android::hardware::radio::V1_5::AppStatus& rhs) {
    if (lhs.base != rhs.base) {
        return false;
    }
    if (lhs.persoSubstate != rhs.persoSubstate) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::AppStatus& lhs, const ::android::hardware::radio::V1_5::AppStatus& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::radio::V1_5::CardStatus& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".base = ";
    os += ::android::hardware::radio::V1_4::toString(o.base);
    os += ", .applications = ";
    os += ::android::hardware::toString(o.applications);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::radio::V1_5::CardStatus& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::radio::V1_5::CardStatus& lhs, const ::android::hardware::radio::V1_5::CardStatus& rhs) {
    if (lhs.base != rhs.base) {
        return false;
    }
    if (lhs.applications != rhs.applications) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::radio::V1_5::CardStatus& lhs, const ::android::hardware::radio::V1_5::CardStatus& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_5
}  // namespace radio
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::radio::V1_5::SignalMeasurementType, 8> hidl_enum_values<::android::hardware::radio::V1_5::SignalMeasurementType> = {
    ::android::hardware::radio::V1_5::SignalMeasurementType::RSSI,
    ::android::hardware::radio::V1_5::SignalMeasurementType::RSCP,
    ::android::hardware::radio::V1_5::SignalMeasurementType::RSRP,
    ::android::hardware::radio::V1_5::SignalMeasurementType::RSRQ,
    ::android::hardware::radio::V1_5::SignalMeasurementType::RSSNR,
    ::android::hardware::radio::V1_5::SignalMeasurementType::SSRSRP,
    ::android::hardware::radio::V1_5::SignalMeasurementType::SSRSRQ,
    ::android::hardware::radio::V1_5::SignalMeasurementType::SSSINR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::radio::V1_5::AccessNetwork, 7> hidl_enum_values<::android::hardware::radio::V1_5::AccessNetwork> = {
    ::android::hardware::radio::V1_5::AccessNetwork::GERAN,
    ::android::hardware::radio::V1_5::AccessNetwork::UTRAN,
    ::android::hardware::radio::V1_5::AccessNetwork::EUTRAN,
    ::android::hardware::radio::V1_5::AccessNetwork::CDMA2000,
    ::android::hardware::radio::V1_5::AccessNetwork::IWLAN,
    ::android::hardware::radio::V1_5::AccessNetwork::UNKNOWN,
    ::android::hardware::radio::V1_5::AccessNetwork::NGRAN,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::radio::V1_5::RadioAccessNetworks, 6> hidl_enum_values<::android::hardware::radio::V1_5::RadioAccessNetworks> = {
    ::android::hardware::radio::V1_5::RadioAccessNetworks::GERAN,
    ::android::hardware::radio::V1_5::RadioAccessNetworks::UTRAN,
    ::android::hardware::radio::V1_5::RadioAccessNetworks::EUTRAN,
    ::android::hardware::radio::V1_5::RadioAccessNetworks::UNKNOWN,
    ::android::hardware::radio::V1_5::RadioAccessNetworks::NGRAN,
    ::android::hardware::radio::V1_5::RadioAccessNetworks::CDMA2000,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::radio::V1_5::NgranBands, 49> hidl_enum_values<::android::hardware::radio::V1_5::NgranBands> = {
    ::android::hardware::radio::V1_5::NgranBands::BAND_1,
    ::android::hardware::radio::V1_5::NgranBands::BAND_2,
    ::android::hardware::radio::V1_5::NgranBands::BAND_3,
    ::android::hardware::radio::V1_5::NgranBands::BAND_5,
    ::android::hardware::radio::V1_5::NgranBands::BAND_7,
    ::android::hardware::radio::V1_5::NgranBands::BAND_8,
    ::android::hardware::radio::V1_5::NgranBands::BAND_12,
    ::android::hardware::radio::V1_5::NgranBands::BAND_14,
    ::android::hardware::radio::V1_5::NgranBands::BAND_18,
    ::android::hardware::radio::V1_5::NgranBands::BAND_20,
    ::android::hardware::radio::V1_5::NgranBands::BAND_25,
    ::android::hardware::radio::V1_5::NgranBands::BAND_28,
    ::android::hardware::radio::V1_5::NgranBands::BAND_29,
    ::android::hardware::radio::V1_5::NgranBands::BAND_30,
    ::android::hardware::radio::V1_5::NgranBands::BAND_34,
    ::android::hardware::radio::V1_5::NgranBands::BAND_38,
    ::android::hardware::radio::V1_5::NgranBands::BAND_39,
    ::android::hardware::radio::V1_5::NgranBands::BAND_40,
    ::android::hardware::radio::V1_5::NgranBands::BAND_41,
    ::android::hardware::radio::V1_5::NgranBands::BAND_48,
    ::android::hardware::radio::V1_5::NgranBands::BAND_50,
    ::android::hardware::radio::V1_5::NgranBands::BAND_51,
    ::android::hardware::radio::V1_5::NgranBands::BAND_65,
    ::android::hardware::radio::V1_5::NgranBands::BAND_66,
    ::android::hardware::radio::V1_5::NgranBands::BAND_70,
    ::android::hardware::radio::V1_5::NgranBands::BAND_71,
    ::android::hardware::radio::V1_5::NgranBands::BAND_74,
    ::android::hardware::radio::V1_5::NgranBands::BAND_75,
    ::android::hardware::radio::V1_5::NgranBands::BAND_76,
    ::android::hardware::radio::V1_5::NgranBands::BAND_77,
    ::android::hardware::radio::V1_5::NgranBands::BAND_78,
    ::android::hardware::radio::V1_5::NgranBands::BAND_79,
    ::android::hardware::radio::V1_5::NgranBands::BAND_80,
    ::android::hardware::radio::V1_5::NgranBands::BAND_81,
    ::android::hardware::radio::V1_5::NgranBands::BAND_82,
    ::android::hardware::radio::V1_5::NgranBands::BAND_83,
    ::android::hardware::radio::V1_5::NgranBands::BAND_84,
    ::android::hardware::radio::V1_5::NgranBands::BAND_86,
    ::android::hardware::radio::V1_5::NgranBands::BAND_89,
    ::android::hardware::radio::V1_5::NgranBands::BAND_90,
    ::android::hardware::radio::V1_5::NgranBands::BAND_91,
    ::android::hardware::radio::V1_5::NgranBands::BAND_92,
    ::android::hardware::radio::V1_5::NgranBands::BAND_93,
    ::android::hardware::radio::V1_5::NgranBands::BAND_94,
    ::android::hardware::radio::V1_5::NgranBands::BAND_95,
    ::android::hardware::radio::V1_5::NgranBands::BAND_257,
    ::android::hardware::radio::V1_5::NgranBands::BAND_258,
    ::android::hardware::radio::V1_5::NgranBands::BAND_260,
    ::android::hardware::radio::V1_5::NgranBands::BAND_261,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::radio::V1_5::UtranBands, 26> hidl_enum_values<::android::hardware::radio::V1_5::UtranBands> = {
    ::android::hardware::radio::V1_5::UtranBands::BAND_1,
    ::android::hardware::radio::V1_5::UtranBands::BAND_2,
    ::android::hardware::radio::V1_5::UtranBands::BAND_3,
    ::android::hardware::radio::V1_5::UtranBands::BAND_4,
    ::android::hardware::radio::V1_5::UtranBands::BAND_5,
    ::android::hardware::radio::V1_5::UtranBands::BAND_6,
    ::android::hardware::radio::V1_5::UtranBands::BAND_7,
    ::android::hardware::radio::V1_5::UtranBands::BAND_8,
    ::android::hardware::radio::V1_5::UtranBands::BAND_9,
    ::android::hardware::radio::V1_5::UtranBands::BAND_10,
    ::android::hardware::radio::V1_5::UtranBands::BAND_11,
    ::android::hardware::radio::V1_5::UtranBands::BAND_12,
    ::android::hardware::radio::V1_5::UtranBands::BAND_13,
    ::android::hardware::radio::V1_5::UtranBands::BAND_14,
    ::android::hardware::radio::V1_5::UtranBands::BAND_19,
    ::android::hardware::radio::V1_5::UtranBands::BAND_20,
    ::android::hardware::radio::V1_5::UtranBands::BAND_21,
    ::android::hardware::radio::V1_5::UtranBands::BAND_22,
    ::android::hardware::radio::V1_5::UtranBands::BAND_25,
    ::android::hardware::radio::V1_5::UtranBands::BAND_26,
    ::android::hardware::radio::V1_5::UtranBands::BAND_A,
    ::android::hardware::radio::V1_5::UtranBands::BAND_B,
    ::android::hardware::radio::V1_5::UtranBands::BAND_C,
    ::android::hardware::radio::V1_5::UtranBands::BAND_D,
    ::android::hardware::radio::V1_5::UtranBands::BAND_E,
    ::android::hardware::radio::V1_5::UtranBands::BAND_F,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::radio::V1_5::EutranBands, 60> hidl_enum_values<::android::hardware::radio::V1_5::EutranBands> = {
    ::android::hardware::radio::V1_5::EutranBands::BAND_1,
    ::android::hardware::radio::V1_5::EutranBands::BAND_2,
    ::android::hardware::radio::V1_5::EutranBands::BAND_3,
    ::android::hardware::radio::V1_5::EutranBands::BAND_4,
    ::android::hardware::radio::V1_5::EutranBands::BAND_5,
    ::android::hardware::radio::V1_5::EutranBands::BAND_6,
    ::android::hardware::radio::V1_5::EutranBands::BAND_7,
    ::android::hardware::radio::V1_5::EutranBands::BAND_8,
    ::android::hardware::radio::V1_5::EutranBands::BAND_9,
    ::android::hardware::radio::V1_5::EutranBands::BAND_10,
    ::android::hardware::radio::V1_5::EutranBands::BAND_11,
    ::android::hardware::radio::V1_5::EutranBands::BAND_12,
    ::android::hardware::radio::V1_5::EutranBands::BAND_13,
    ::android::hardware::radio::V1_5::EutranBands::BAND_14,
    ::android::hardware::radio::V1_5::EutranBands::BAND_17,
    ::android::hardware::radio::V1_5::EutranBands::BAND_18,
    ::android::hardware::radio::V1_5::EutranBands::BAND_19,
    ::android::hardware::radio::V1_5::EutranBands::BAND_20,
    ::android::hardware::radio::V1_5::EutranBands::BAND_21,
    ::android::hardware::radio::V1_5::EutranBands::BAND_22,
    ::android::hardware::radio::V1_5::EutranBands::BAND_23,
    ::android::hardware::radio::V1_5::EutranBands::BAND_24,
    ::android::hardware::radio::V1_5::EutranBands::BAND_25,
    ::android::hardware::radio::V1_5::EutranBands::BAND_26,
    ::android::hardware::radio::V1_5::EutranBands::BAND_27,
    ::android::hardware::radio::V1_5::EutranBands::BAND_28,
    ::android::hardware::radio::V1_5::EutranBands::BAND_30,
    ::android::hardware::radio::V1_5::EutranBands::BAND_31,
    ::android::hardware::radio::V1_5::EutranBands::BAND_33,
    ::android::hardware::radio::V1_5::EutranBands::BAND_34,
    ::android::hardware::radio::V1_5::EutranBands::BAND_35,
    ::android::hardware::radio::V1_5::EutranBands::BAND_36,
    ::android::hardware::radio::V1_5::EutranBands::BAND_37,
    ::android::hardware::radio::V1_5::EutranBands::BAND_38,
    ::android::hardware::radio::V1_5::EutranBands::BAND_39,
    ::android::hardware::radio::V1_5::EutranBands::BAND_40,
    ::android::hardware::radio::V1_5::EutranBands::BAND_41,
    ::android::hardware::radio::V1_5::EutranBands::BAND_42,
    ::android::hardware::radio::V1_5::EutranBands::BAND_43,
    ::android::hardware::radio::V1_5::EutranBands::BAND_44,
    ::android::hardware::radio::V1_5::EutranBands::BAND_45,
    ::android::hardware::radio::V1_5::EutranBands::BAND_46,
    ::android::hardware::radio::V1_5::EutranBands::BAND_47,
    ::android::hardware::radio::V1_5::EutranBands::BAND_48,
    ::android::hardware::radio::V1_5::EutranBands::BAND_65,
    ::android::hardware::radio::V1_5::EutranBands::BAND_66,
    ::android::hardware::radio::V1_5::EutranBands::BAND_68,
    ::android::hardware::radio::V1_5::EutranBands::BAND_70,
    ::android::hardware::radio::V1_5::EutranBands::BAND_49,
    ::android::hardware::radio::V1_5::EutranBands::BAND_50,
    ::android::hardware::radio::V1_5::EutranBands::BAND_51,
    ::android::hardware::radio::V1_5::EutranBands::BAND_52,
    ::android::hardware::radio::V1_5::EutranBands::BAND_53,
    ::android::hardware::radio::V1_5::EutranBands::BAND_71,
    ::android::hardware::radio::V1_5::EutranBands::BAND_72,
    ::android::hardware::radio::V1_5::EutranBands::BAND_73,
    ::android::hardware::radio::V1_5::EutranBands::BAND_74,
    ::android::hardware::radio::V1_5::EutranBands::BAND_85,
    ::android::hardware::radio::V1_5::EutranBands::BAND_87,
    ::android::hardware::radio::V1_5::EutranBands::BAND_88,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::radio::V1_5::ApnTypes, 14> hidl_enum_values<::android::hardware::radio::V1_5::ApnTypes> = {
    ::android::hardware::radio::V1_5::ApnTypes::NONE,
    ::android::hardware::radio::V1_5::ApnTypes::DEFAULT,
    ::android::hardware::radio::V1_5::ApnTypes::MMS,
    ::android::hardware::radio::V1_5::ApnTypes::SUPL,
    ::android::hardware::radio::V1_5::ApnTypes::DUN,
    ::android::hardware::radio::V1_5::ApnTypes::HIPRI,
    ::android::hardware::radio::V1_5::ApnTypes::FOTA,
    ::android::hardware::radio::V1_5::ApnTypes::IMS,
    ::android::hardware::radio::V1_5::ApnTypes::CBS,
    ::android::hardware::radio::V1_5::ApnTypes::IA,
    ::android::hardware::radio::V1_5::ApnTypes::EMERGENCY,
    ::android::hardware::radio::V1_5::ApnTypes::ALL,
    ::android::hardware::radio::V1_5::ApnTypes::MCX,
    ::android::hardware::radio::V1_5::ApnTypes::XCAP,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::radio::V1_5::AddressProperty, 2> hidl_enum_values<::android::hardware::radio::V1_5::AddressProperty> = {
    ::android::hardware::radio::V1_5::AddressProperty::NONE,
    ::android::hardware::radio::V1_5::AddressProperty::DEPRECATED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::radio::V1_5::Domain, 2> hidl_enum_values<::android::hardware::radio::V1_5::Domain> = {
    ::android::hardware::radio::V1_5::Domain::CS,
    ::android::hardware::radio::V1_5::Domain::PS,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::radio::V1_5::BarringInfo::ServiceType, 42> hidl_enum_values<::android::hardware::radio::V1_5::BarringInfo::ServiceType> = {
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::CS_SERVICE,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::PS_SERVICE,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::CS_VOICE,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::MO_SIGNALLING,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::MO_DATA,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::CS_FALLBACK,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::MMTEL_VOICE,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::MMTEL_VIDEO,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::EMERGENCY,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::SMS,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_1,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_2,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_3,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_4,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_5,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_6,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_7,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_8,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_9,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_10,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_11,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_12,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_13,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_14,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_15,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_16,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_17,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_18,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_19,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_20,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_21,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_22,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_23,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_24,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_25,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_26,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_27,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_28,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_29,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_30,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_31,
    ::android::hardware::radio::V1_5::BarringInfo::ServiceType::OPERATOR_32,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::radio::V1_5::BarringInfo::BarringType, 3> hidl_enum_values<::android::hardware::radio::V1_5::BarringInfo::BarringType> = {
    ::android::hardware::radio::V1_5::BarringInfo::BarringType::NONE,
    ::android::hardware::radio::V1_5::BarringInfo::BarringType::CONDITIONAL,
    ::android::hardware::radio::V1_5::BarringInfo::BarringType::UNCONDITIONAL,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::radio::V1_5::IndicationFilter, 9> hidl_enum_values<::android::hardware::radio::V1_5::IndicationFilter> = {
    ::android::hardware::radio::V1_5::IndicationFilter::NONE,
    ::android::hardware::radio::V1_5::IndicationFilter::ALL,
    ::android::hardware::radio::V1_5::IndicationFilter::SIGNAL_STRENGTH,
    ::android::hardware::radio::V1_5::IndicationFilter::FULL_NETWORK_STATE,
    ::android::hardware::radio::V1_5::IndicationFilter::DATA_CALL_DORMANCY_CHANGED,
    ::android::hardware::radio::V1_5::IndicationFilter::LINK_CAPACITY_ESTIMATE,
    ::android::hardware::radio::V1_5::IndicationFilter::PHYSICAL_CHANNEL_CONFIG,
    ::android::hardware::radio::V1_5::IndicationFilter::REGISTRATION_FAILURE,
    ::android::hardware::radio::V1_5::IndicationFilter::BARRING_INFO,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::radio::V1_5::RegistrationFailCause, 52> hidl_enum_values<::android::hardware::radio::V1_5::RegistrationFailCause> = {
    ::android::hardware::radio::V1_5::RegistrationFailCause::NONE,
    ::android::hardware::radio::V1_5::RegistrationFailCause::IMSI_UNKNOWN_IN_HLR,
    ::android::hardware::radio::V1_5::RegistrationFailCause::ILLEGAL_MS,
    ::android::hardware::radio::V1_5::RegistrationFailCause::IMSI_UNKNOWN_IN_VLR,
    ::android::hardware::radio::V1_5::RegistrationFailCause::IMEI_NOT_ACCEPTED,
    ::android::hardware::radio::V1_5::RegistrationFailCause::ILLEGAL_ME,
    ::android::hardware::radio::V1_5::RegistrationFailCause::GPRS_SERVICES_NOT_ALLOWED,
    ::android::hardware::radio::V1_5::RegistrationFailCause::GPRS_AND_NON_GPRS_SERVICES_NOT_ALLOWED,
    ::android::hardware::radio::V1_5::RegistrationFailCause::MS_IDENTITY_CANNOT_BE_DERIVED_BY_NETWORK,
    ::android::hardware::radio::V1_5::RegistrationFailCause::IMPLICITLY_DETACHED,
    ::android::hardware::radio::V1_5::RegistrationFailCause::PLMN_NOT_ALLOWED,
    ::android::hardware::radio::V1_5::RegistrationFailCause::LOCATION_AREA_NOT_ALLOWED,
    ::android::hardware::radio::V1_5::RegistrationFailCause::ROAMING_NOT_ALLOWED,
    ::android::hardware::radio::V1_5::RegistrationFailCause::GPRS_SERVICES_NOT_ALLOWED_IN_PLMN,
    ::android::hardware::radio::V1_5::RegistrationFailCause::NO_SUITABLE_CELLS,
    ::android::hardware::radio::V1_5::RegistrationFailCause::MSC_TEMPORARILY_NOT_REACHABLE,
    ::android::hardware::radio::V1_5::RegistrationFailCause::NETWORK_FAILURE,
    ::android::hardware::radio::V1_5::RegistrationFailCause::MAC_FAILURE,
    ::android::hardware::radio::V1_5::RegistrationFailCause::SYNC_FAILURE,
    ::android::hardware::radio::V1_5::RegistrationFailCause::CONGESTION,
    ::android::hardware::radio::V1_5::RegistrationFailCause::GSM_AUTHENTICATION_UNACCEPTABLE,
    ::android::hardware::radio::V1_5::RegistrationFailCause::NOT_AUTHORIZED_FOR_THIS_CSG,
    ::android::hardware::radio::V1_5::RegistrationFailCause::SMS_PROVIDED_BY_GPRS_IN_ROUTING_AREA,
    ::android::hardware::radio::V1_5::RegistrationFailCause::SERVICE_OPTION_NOT_SUPPORTED,
    ::android::hardware::radio::V1_5::RegistrationFailCause::SERVICE_OPTION_NOT_SUBSCRIBED,
    ::android::hardware::radio::V1_5::RegistrationFailCause::SERVICE_OPTION_TEMPORARILY_OUT_OF_ORDER,
    ::android::hardware::radio::V1_5::RegistrationFailCause::CALL_CANNOT_BE_IDENTIFIED,
    ::android::hardware::radio::V1_5::RegistrationFailCause::NO_PDP_CONTEXT_ACTIVATED,
    ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_1,
    ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_2,
    ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_3,
    ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_4,
    ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_5,
    ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_6,
    ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_7,
    ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_8,
    ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_9,
    ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_10,
    ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_11,
    ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_12,
    ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_13,
    ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_14,
    ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_15,
    ::android::hardware::radio::V1_5::RegistrationFailCause::RETRY_UPON_ENTRY_INTO_NEW_CELL_16,
    ::android::hardware::radio::V1_5::RegistrationFailCause::SEMANTICALLY_INCORRECT_MESSAGE,
    ::android::hardware::radio::V1_5::RegistrationFailCause::INVALID_MANDATORY_INFORMATION,
    ::android::hardware::radio::V1_5::RegistrationFailCause::MESSAGE_TYPE_NON_EXISTENT_OR_NOT_IMPLEMENTED,
    ::android::hardware::radio::V1_5::RegistrationFailCause::MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE,
    ::android::hardware::radio::V1_5::RegistrationFailCause::INFORMATION_ELEMENT_NON_EXISTENT_OR_NOT_IMPLEMENTED,
    ::android::hardware::radio::V1_5::RegistrationFailCause::CONDITIONAL_IE_ERROR,
    ::android::hardware::radio::V1_5::RegistrationFailCause::MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE,
    ::android::hardware::radio::V1_5::RegistrationFailCause::PROTOCOL_ERROR_UNSPECIFIED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::radio::V1_5::PrlIndicator, 3> hidl_enum_values<::android::hardware::radio::V1_5::PrlIndicator> = {
    ::android::hardware::radio::V1_5::PrlIndicator::NOT_REGISTERED,
    ::android::hardware::radio::V1_5::PrlIndicator::NOT_IN_PRL,
    ::android::hardware::radio::V1_5::PrlIndicator::IN_PRL,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::radio::V1_5::PersoSubstate, 35> hidl_enum_values<::android::hardware::radio::V1_5::PersoSubstate> = {
    ::android::hardware::radio::V1_5::PersoSubstate::UNKNOWN,
    ::android::hardware::radio::V1_5::PersoSubstate::IN_PROGRESS,
    ::android::hardware::radio::V1_5::PersoSubstate::READY,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK_SUBSET,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_CORPORATE,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_SERVICE_PROVIDER,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_SIM,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK_PUK,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_NETWORK_SUBSET_PUK,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_CORPORATE_PUK,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_SERVICE_PROVIDER_PUK,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_SIM_PUK,
    ::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK1,
    ::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK2,
    ::android::hardware::radio::V1_5::PersoSubstate::RUIM_HRPD,
    ::android::hardware::radio::V1_5::PersoSubstate::RUIM_CORPORATE,
    ::android::hardware::radio::V1_5::PersoSubstate::RUIM_SERVICE_PROVIDER,
    ::android::hardware::radio::V1_5::PersoSubstate::RUIM_RUIM,
    ::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK1_PUK,
    ::android::hardware::radio::V1_5::PersoSubstate::RUIM_NETWORK2_PUK,
    ::android::hardware::radio::V1_5::PersoSubstate::RUIM_HRPD_PUK,
    ::android::hardware::radio::V1_5::PersoSubstate::RUIM_CORPORATE_PUK,
    ::android::hardware::radio::V1_5::PersoSubstate::RUIM_SERVICE_PROVIDER_PUK,
    ::android::hardware::radio::V1_5::PersoSubstate::RUIM_RUIM_PUK,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_SPN,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_SPN_PUK,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_SP_EHPLMN,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_SP_EHPLMN_PUK,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_ICCID,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_ICCID_PUK,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_IMPI,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_IMPI_PUK,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_NS_SP,
    ::android::hardware::radio::V1_5::PersoSubstate::SIM_NS_SP_PUK,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_5_TYPES_H
