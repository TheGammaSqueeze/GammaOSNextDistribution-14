#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_IGNSSMEASUREMENTCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_IGNSSMEASUREMENTCALLBACK_H

#include <android/hardware/gnss/1.0/IGnssMeasurementCallback.h>
#include <android/hardware/gnss/1.1/IGnssMeasurementCallback.h>
#include <android/hardware/gnss/2.0/types.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V2_0 {

/**
 * The callback interface to report measurements from the HAL.
 */
struct IGnssMeasurementCallback : public ::android::hardware::gnss::V1_1::IGnssMeasurementCallback {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.gnss@2.0::IGnssMeasurementCallback"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class GnssMeasurementState : uint32_t;
    struct GnssMeasurement;
    struct GnssData;

    /**
     * Flags indicating the GNSS measurement state.
     *
     * <p>The expected behavior here is for GNSS HAL to set all the flags that apply. For example,
     * if the state for a satellite is only C/A code locked and bit synchronized, and there is still
     * millisecond ambiguity, the state must be set as:
     *
     * STATE_CODE_LOCK | STATE_BIT_SYNC |  STATE_MSEC_AMBIGUOUS
     *
     * <p>If GNSS is still searching for a satellite, the corresponding state must be set to
     * STATE_UNKNOWN(0).
     *
     * <p>In @2.0::IGnssMeasurementCallback.GnssMeasurement, v1_1.v1_0.receivedSvTimeInNs, the
     * received satellite time, is relative to the beginning of the system week for all
     * constellations except for Glonass where it is relative to the beginning of the Glonass system
     * day.
     *
     * <p>The table below indicates the valid range of the received GNSS satellite time.  These
     * ranges depend on the constellation and code being tracked and the state of the tracking
     * algorithms given by the getState method. If the state flag is set, then the valid measurement
     * range is zero to the value in the table. The state flag with the widest range indicates the
     * range of the received GNSS satellite time value.
     *
     * <table>
     *   <thead>
     *     <tr>
     *       <td />
     *       <td colspan="3"><strong>GPS/QZSS</strong></td>
     *       <td><strong>GLNS</strong></td>
     *       <td colspan="2"><strong>BDS</strong></td>
     *       <td colspan="3"><strong>GAL</strong></td>
     *       <td><strong>SBAS</strong></td>
     *     </tr>
     *     <tr>
     *       <td><strong>State Flag</strong></td>
     *       <td><strong>L1 C/A</strong></td>
     *       <td><strong>L5I</strong></td>
     *       <td><strong>L5Q</strong></td>
     *       <td><strong>L1OF</strong></td>
     *       <td><strong>B1I (D1)</strong></td>
     *       <td><strong>B1I &nbsp;(D2)</strong></td>
     *       <td><strong>E1B</strong></td>
     *       <td><strong>E1C</strong></td>
     *       <td><strong>E5AQ</strong></td>
     *       <td><strong>L1 C/A</strong></td>
     *     </tr>
     *   </thead>
     *   <tbody>
     *     <tr>
     *       <td>
     *         <strong>STATE_UNKNOWN</strong>
     *       </td>
     *       <td>0</td>
     *       <td>0</td>
     *       <td>0</td>
     *       <td>0</td>
     *       <td>0</td>
     *       <td>0</td>
     *       <td>0</td>
     *       <td>0</td>
     *       <td>0</td>
     *       <td>0</td>
     *     </tr>
     *     <tr>
     *       <td>
     *         <strong>STATE_CODE_LOCK</strong>
     *       </td>
     *       <td>1 ms</td>
     *       <td>1 ms</td>
     *       <td>1 ms</td>
     *       <td>1 ms</td>
     *       <td>1 ms</td>
     *       <td>1 ms</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>1 ms</td>
     *       <td>1 ms</td>
     *     </tr>
     *     <tr>
     *       <td>
     *         <strong>STATE_SYMBOL_SYNC</strong>
     *       </td>
     *       <td>20 ms (optional)</td>
     *       <td>10 ms</td>
     *       <td>1 ms (optional)</td>
     *       <td>10 ms</td>
     *       <td>20 ms (optional)</td>
     *       <td>2 ms</td>
     *       <td>4 ms (optional)</td>
     *       <td>4 ms (optional)</td>
     *       <td>1 ms (optional)</td>
     *       <td>2 ms</td>
     *     </tr>
     *     <tr>
     *       <td>
     *         <strong>STATE_BIT_SYNC</strong>
     *       </td>
     *       <td>20 ms</td>
     *       <td>20 ms</td>
     *       <td>1 ms (optional)</td>
     *       <td>20 ms</td>
     *       <td>20 ms</td>
     *       <td>-</td>
     *       <td>8 ms</td>
     *       <td>-</td>
     *       <td>1 ms (optional)</td>
     *       <td>4 ms</td>
     *     </tr>
     *     <tr>
     *       <td>
     *         <strong>STATE_SUBFRAME_SYNC</strong>
     *       </td>
     *       <td>6s</td>
     *       <td>6s</td>
     *       <td>-</td>
     *       <td>2 s</td>
     *       <td>6 s</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>100 ms</td>
     *       <td>-</td>
     *     </tr>
     *     <tr>
     *       <td>
     *         <strong>STATE_TOW_DECODED</strong>
     *       </td>
     *       <td colspan="2">1 week</td>
     *       <td>-</td>
     *       <td>1 day</td>
     *       <td colspan="2">1 week</td>
     *       <td colspan="2">1 week</td>
     *       <td>-</td>
     *       <td>1 week</td>
     *     </tr>
     *     <tr>
     *       <td>
     *         <strong>STATE_TOW_KNOWN</strong>
     *       </td>
     *       <td colspan="3">1 week</td>
     *       <td>1 day</td>
     *       <td colspan="2">1 week</td>
     *       <td colspan="3">1 week</td>
     *       <td>1 week</td>
     *     </tr>
     *     <tr>
     *       <td>
     *         <strong>STATE_GLO_STRING_SYNC</strong>
     *       </td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>2 s</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *     </tr>
     *     <tr>
     *       <td>
     *         <strong>STATE_GLO_TOD_DECODED</strong>
     *       </td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>1 day</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *     </tr>
     *     <tr>
     *       <td>
     *         <strong>STATE_GLO_TOD_KNOWN</strong>
     *       </td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>1 day</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *     </tr>
     *     <tr>
     *       <td>
     *         <strong>STATE_BDS_D2_BIT_SYNC</strong>
     *       </td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>2 ms</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *     </tr>
     *     <tr>
     *       <td>
     *         <strong>STATE_BDS_D2_SUBFRAME_SYNC</strong>
     *       </td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>600 ms</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *     </tr>
     *     <tr>
     *       <td>
     *         <strong>STATE_GAL_E1BC_CODE_LOCK</strong>
     *       </td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>4 ms</td>
     *       <td>4 ms</td>
     *       <td>-</td>
     *       <td>-</td>
     *     </tr>
     *     <tr>
     *       <td>
     *         <strong>STATE_GAL_E1C_2ND_CODE_LOCK</strong>
     *       </td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>100 ms</td>
     *       <td>-</td>
     *       <td>-</td>
     *     </tr>
     *     <tr>
     *       <td>
     *         <strong>STATE_2ND_CODE_LOCK</strong>
     *       </td>
     *       <td>-</td>
     *       <td>10 ms (optional)</td>
     *       <td>20 ms</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>100 ms (optional)</td>
     *       <td>100 ms</td>
     *       <td>-</td>
     *     </tr>
     *     <tr>
     *       <td>
     *         <strong>STATE_GAL_E1B_PAGE_SYNC</strong>
     *       </td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>2 s</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *     </tr>
     *     <tr>
     *       <td>
     *         <strong>STATE_SBAS_SYNC</strong>
     *       </td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>-</td>
     *       <td>1 s</td>
     *     </tr>
     *   </tbody>
     * </table>
     *
     * <p>Note: TOW Known refers to the case where TOW is possibly not decoded over the air but has
     * been determined from other sources. If TOW decoded is set then TOW Known must also be set.
     *
     * <p>Note well: if there is any ambiguity in integer millisecond, STATE_MSEC_AMBIGUOUS must be
     * set accordingly, in the 'state' field.  This value must be populated if 'state' !=
     * STATE_UNKNOWN.
     *
     * <p>Note on optional flags:
     * <ul>
     *     <li> For L1 C/A and B1I, STATE_SYMBOL_SYNC is optional since the symbol length is the
     *     same as the bit length.
     *     <li> For L5Q and E5aQ, STATE_BIT_SYNC and STATE_SYMBOL_SYNC are optional since they are
     *     implied by STATE_CODE_LOCK.
     *     <li> STATE_2ND_CODE_LOCK for L5I is optional since it is implied by STATE_SYMBOL_SYNC.
     *     <li> STATE_2ND_CODE_LOCK for E1C is optional since it is implied by
     *     STATE_GAL_E1C_2ND_CODE_LOCK.
     *     <li> For E1B and E1C, STATE_SYMBOL_SYNC is optional, because it is implied by
     *     STATE_GAL_E1BC_CODE_LOCK.
     * </ul>
     *
     */
    enum class GnssMeasurementState : uint32_t {
        STATE_UNKNOWN = 0u,
        STATE_CODE_LOCK = 1u /* 1 << 0 */,
        STATE_BIT_SYNC = 2u /* 1 << 1 */,
        STATE_SUBFRAME_SYNC = 4u /* 1 << 2 */,
        STATE_TOW_DECODED = 8u /* 1 << 3 */,
        STATE_MSEC_AMBIGUOUS = 16u /* 1 << 4 */,
        STATE_SYMBOL_SYNC = 32u /* 1 << 5 */,
        STATE_GLO_STRING_SYNC = 64u /* 1 << 6 */,
        STATE_GLO_TOD_DECODED = 128u /* 1 << 7 */,
        STATE_BDS_D2_BIT_SYNC = 256u /* 1 << 8 */,
        STATE_BDS_D2_SUBFRAME_SYNC = 512u /* 1 << 9 */,
        STATE_GAL_E1BC_CODE_LOCK = 1024u /* 1 << 10 */,
        STATE_GAL_E1C_2ND_CODE_LOCK = 2048u /* 1 << 11 */,
        STATE_GAL_E1B_PAGE_SYNC = 4096u /* 1 << 12 */,
        STATE_SBAS_SYNC = 8192u /* 1 << 13 */,
        STATE_TOW_KNOWN = 16384u /* 1 << 14 */,
        STATE_GLO_TOD_KNOWN = 32768u /* 1 << 15 */,
        STATE_2ND_CODE_LOCK = 65536u /* 1 << 16 */,
    };

    /**
     * Extends a GNSS Measurement, adding a GnssMeasurementCodeType, a GnssMeasurementState, and
     * replacing the GnssConstellationType.
     */
    struct GnssMeasurement final {
        /**
         * GNSS measurement information for a single satellite and frequency, as in the 1.1
         * version of the HAL with further clarification of the value reported in the
         * accumulatedDeltaRangeM field, i.e., the alignment of the phase measurement will not be
         * adjusted by the receiver so the in-phase and quadrature phase components will have a
         * quarter cycle offset as they do when transmitted from the satellites. If the measurement
         * is from a combination of the in-phase and quadrature phase components, then the alignment
         * of the phase measurement will be aligned to the in-phase component.
         *
         * In this version of the HAL, the field 'state' in the v1_1.v1_0 struct is deprecated, and
         * is no longer used by the framework. The satellite sync state is instead reported in
         * @2.0::IGnssMeasurementCallback.GnssMeasurement.state.
         *
         * In this version of the HAL, the field 'constellation' in the v1_1.v1_0 struct is
         * deprecated, and is no longer used by the framework. The constellation type is instead
         * reported in @2.0::IGnssMeasurementCallback.GnssMeasurement.constellation.
         */
        ::android::hardware::gnss::V1_1::IGnssMeasurementCallback::GnssMeasurement v1_1 __attribute__ ((aligned(8)));
        /**
         * The type of code that is currently being tracked in the GNSS measurement.
         *
         * For high precision applications the type of code being tracked needs to be considered
         * in-order to properly apply code specific corrections to the pseudorange measurements.
         *
         * Value "A" represents GALILEO E1A, GALILEO E6A, IRNSS L5A, IRNSS SA.
         *
         * Value "B" represents GALILEO E1B, GALILEO E6B, IRNSS L5B, IRNSS SB.
         *
         * Value "C" represents GPS L1 C/A,  GPS L2 C/A, GLONASS G1 C/A, GLONASS G2 C/A, GALILEO E1C,
         * GALILEO E6C, SBAS L1 C/A, QZSS L1 C/A, IRNSS L5C.
         *
         * value "D" represents BDS B1C D.
         *
         * Value "I" represents GPS L5 I, GLONASS G3 I, GALILEO E5a I, GALILEO E5b I, GALILEO E5a+b I,
         * SBAS L5 I, QZSS L5 I, BDS B1 I, BDS B2 I, BDS B3 I.
         *
         * Value "L" represents GPS L1C (P), GPS L2C (L), QZSS L1C (P), QZSS L2C (L), LEX(6) L.
         *
         * Value "M" represents GPS L1M, GPS L2M.
         *
         * Value "N" represents GPS L1 codeless, GPS L2 codeless.
         *
         * Value "P" represents GPS L1P, GPS L2P, GLONASS G1P, GLONASS G2P, BDS B1C P.
         *
         * Value "Q" represents GPS L5 Q, GLONASS G3 Q, GALILEO E5a Q, GALILEO E5b Q, GALILEO E5a+b Q,
         * SBAS L5 Q, QZSS L5 Q, BDS B1 Q, BDS B2 Q, BDS B3 Q.
         *
         * Value "S" represents GPS L1C (D), GPS L2C (M), QZSS L1C (D), QZSS L2C (M), LEX(6) S.
         *
         * Value "W" represents GPS L1 Z-tracking, GPS L2 Z-tracking.
         *
         * Value "X" represents GPS L1C (D+P), GPS L2C (M+L), GPS L5 (I+Q), GLONASS G3 (I+Q),
         * GALILEO E1 (B+C), GALILEO E5a (I+Q), GALILEO E5b (I+Q), GALILEO E5a+b(I+Q),
         * GALILEO E6 (B+C), SBAS L5 (I+Q), QZSS L1C (D+P), QZSS L2C (M+L), QZSS L5 (I+Q),
         * LEX(6) (S+L), BDS B1 (I+Q), BDS B1C (D+P), BDS B2 (I+Q), BDS B3 (I+Q), IRNSS L5 (B+C).
         *
         * Value "Y" represents GPS L1Y, GPS L2Y.
         *
         * Value "Z" represents GALILEO E1 (A+B+C), GALILEO E6 (A+B+C), QZSS L1-SAIF.
         *
         * Value "UNKNOWN" represents the GNSS Measurement's code type is unknown.
         *
         * This is used to specify the observation descriptor defined in GNSS Observation Data File
         * Header Section Description in the RINEX standard (Version 3.XX). In RINEX Version 3.03,
         * in Appendix Table A2 Attributes are listed as uppercase letters (for instance, "A" for
         * "A channel"). In the future, if for instance a code "G" was added in the official RINEX
         * standard, "G" could be specified here.
         */
        ::android::hardware::hidl_string codeType __attribute__ ((aligned(8)));
        /**
         * Per satellite sync state. It represents the current sync state for the associated
         * satellite.
         *
         * Based on the sync state, the receivedSvTimeInNs field must be interpreted accordingly.
         *
         * This value is mandatory.
         */
        ::android::hardware::hidl_bitfield<::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState> state __attribute__ ((aligned(4)));
        /**
         * The constellation type of the GNSS measurement.
         */
        ::android::hardware::gnss::V2_0::GnssConstellationType constellation __attribute__ ((aligned(1)));
    };

    static_assert(offsetof(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement, v1_1) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement, codeType) == 152, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement, state) == 168, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement, constellation) == 172, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement) == 176, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement) == 8, "wrong alignment");

    /**
     * Complete set of GNSS Measurement data, same as 1.1 with additional enum in measurements.
     */
    struct GnssData final {
        /**
         * The full set of satellite measurement observations.
         */
        ::android::hardware::hidl_vec<::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement> measurements __attribute__ ((aligned(8)));
        /**
         * The GNSS clock time reading.
         */
        ::android::hardware::gnss::V1_0::IGnssMeasurementCallback::GnssClock clock __attribute__ ((aligned(8)));
        /**
         * Timing information of the GNSS data synchronized with SystemClock.elapsedRealtimeNanos()
         * clock.
         */
        ::android::hardware::gnss::V2_0::ElapsedRealtime elapsedRealtime __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssData, measurements) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssData, clock) == 16, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssData, elapsedRealtime) == 88, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssData) == 112, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssData) == 8, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Callback for the hal to pass a GnssData structure back to the client.
     *
     * @param data Contains a reading of GNSS measurements.
     */
    virtual ::android::hardware::Return<void> GnssMeasurementCb(const ::android::hardware::gnss::V1_0::IGnssMeasurementCallback::GnssData& data) = 0;

    /**
     * Callback for the hal to pass a GnssData structure back to the client.
     *
     * @param data Contains a reading of GNSS measurements.
     */
    virtual ::android::hardware::Return<void> gnssMeasurementCb(const ::android::hardware::gnss::V1_1::IGnssMeasurementCallback::GnssData& data) = 0;

    /**
     * Callback for the hal to pass a GnssData structure back to the client.
     *
     * @param data Contains a reading of GNSS measurements.
     */
    virtual ::android::hardware::Return<void> gnssMeasurementCb_2_0(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssData& data) = 0;

    /**
     * Return callback for interfaceChain
     */
    using interfaceChain_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& descriptors)>;
    /*
     * Provides run-time type information for this object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceChain on an IChild object must yield the following:
     *     ["android.hardware.foo@1.0::IChild",
     *      "android.hardware.foo@1.0::IParent"
     *      "android.hidl.base@1.0::IBase"]
     *
     * @return descriptors a vector of descriptors of the run-time type of the
     *         object.
     */
    virtual ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override;

    /*
     * Emit diagnostic information to the given file.
     *
     * Optionally overriden.
     *
     * @param fd      File descriptor to dump data to.
     *                Must only be used for the duration of this call.
     * @param options Arguments for debugging.
     *                Must support empty for default debug information.
     */
    virtual ::android::hardware::Return<void> debug(const ::android::hardware::hidl_handle& fd, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& options) override;

    /**
     * Return callback for interfaceDescriptor
     */
    using interfaceDescriptor_cb = std::function<void(const ::android::hardware::hidl_string& descriptor)>;
    /*
     * Provides run-time type information for this object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceDescriptor on an IChild object must yield
     *     "android.hardware.foo@1.0::IChild"
     *
     * @return descriptor a descriptor of the run-time type of the
     *         object (the first element of the vector returned by
     *         interfaceChain())
     */
    virtual ::android::hardware::Return<void> interfaceDescriptor(interfaceDescriptor_cb _hidl_cb) override;

    /**
     * Return callback for getHashChain
     */
    using getHashChain_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::hidl_array<uint8_t, 32>>& hashchain)>;
    /*
     * Returns hashes of the source HAL files that define the interfaces of the
     * runtime type information on the object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceChain on an IChild object must yield the following:
     *     [(hash of IChild.hal),
     *      (hash of IParent.hal)
     *      (hash of IBase.hal)].
     *
     * SHA-256 is used as the hashing algorithm. Each hash has 32 bytes
     * according to SHA-256 standard.
     *
     * @return hashchain a vector of SHA-1 digests
     */
    virtual ::android::hardware::Return<void> getHashChain(getHashChain_cb _hidl_cb) override;

    /*
     * This method trigger the interface to enable/disable instrumentation based
     * on system property hal.instrumentation.enable.
     */
    virtual ::android::hardware::Return<void> setHALInstrumentation() override;

    /*
     * Registers a death recipient, to be called when the process hosting this
     * interface dies.
     *
     * @param recipient a hidl_death_recipient callback object
     * @param cookie a cookie that must be returned with the callback
     * @return success whether the death recipient was registered successfully.
     */
    virtual ::android::hardware::Return<bool> linkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient, uint64_t cookie) override;

    /*
     * Provides way to determine if interface is running without requesting
     * any functionality.
     */
    virtual ::android::hardware::Return<void> ping() override;

    /**
     * Return callback for getDebugInfo
     */
    using getDebugInfo_cb = std::function<void(const ::android::hidl::base::V1_0::DebugInfo& info)>;
    /*
     * Get debug information on references on this interface.
     * @return info debugging information. See comments of DebugInfo.
     */
    virtual ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb) override;

    /*
     * This method notifies the interface that one or more system properties
     * have changed. The default implementation calls
     * (C++)  report_sysprop_change() in libcutils or
     * (Java) android.os.SystemProperties.reportSyspropChanged,
     * which in turn calls a set of registered callbacks (eg to update trace
     * tags).
     */
    virtual ::android::hardware::Return<void> notifySyspropsChanged() override;

    /*
     * Unregisters the registered death recipient. If this service was registered
     * multiple times with the same exact death recipient, this unlinks the most
     * recently registered one.
     *
     * @param recipient a previously registered hidl_death_recipient callback
     * @return success whether the death recipient was unregistered successfully.
     */
    virtual ::android::hardware::Return<bool> unlinkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient) override;

    // cast static functions
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V2_0::IGnssMeasurementCallback>> castFrom(const ::android::sp<::android::hardware::gnss::V2_0::IGnssMeasurementCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V2_0::IGnssMeasurementCallback>> castFrom(const ::android::sp<::android::hardware::gnss::V1_1::IGnssMeasurementCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V2_0::IGnssMeasurementCallback>> castFrom(const ::android::sp<::android::hardware::gnss::V1_0::IGnssMeasurementCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V2_0::IGnssMeasurementCallback>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

    // helper methods for interactions with the hwservicemanager
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is currently not available or not in the VINTF manifest on a Trebilized
     * device, this will return nullptr. This is useful when you don't want to block
     * during device boot. If getStub is true, this will try to return an unwrapped
     * passthrough implementation in the same process. This is useful when getting an
     * implementation from the same partition/compilation group.
     *
     * In general, prefer getService(std::string,bool)
     */
    static ::android::sp<IGnssMeasurementCallback> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnssMeasurementCallback> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnssMeasurementCallback> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnssMeasurementCallback> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IGnssMeasurementCallback> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnssMeasurementCallback> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnssMeasurementCallback> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnssMeasurementCallback> getService(bool getStub) { return getService("default", getStub); }
    /**
     * Registers a service with the service manager. For Trebilized devices, the service
     * must also be in the VINTF manifest.
     */
    __attribute__ ((warn_unused_result))::android::status_t registerAsService(const std::string &serviceName="default");
    /**
     * Registers for notifications for when a service is registered.
     */
    static bool registerForNotifications(
            const std::string &serviceName,
            const ::android::sp<::android::hidl::manager::V1_0::IServiceNotification> &notification);
};

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState o);
static inline void PrintTo(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState lhs, const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState lhs, const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement& o);
static inline void PrintTo(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement& lhs, const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement& lhs, const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement& rhs);

static inline std::string toString(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssData& o);
static inline void PrintTo(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssData& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssData& lhs, const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssData& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssData& lhs, const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssData& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V2_0::IGnssMeasurementCallback>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_UNKNOWN) == static_cast<uint32_t>(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_UNKNOWN)) {
        os += (first ? "" : " | ");
        os += "STATE_UNKNOWN";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_UNKNOWN;
    }
    if ((o & ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_CODE_LOCK) == static_cast<uint32_t>(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_CODE_LOCK)) {
        os += (first ? "" : " | ");
        os += "STATE_CODE_LOCK";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_CODE_LOCK;
    }
    if ((o & ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_BIT_SYNC) == static_cast<uint32_t>(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_BIT_SYNC)) {
        os += (first ? "" : " | ");
        os += "STATE_BIT_SYNC";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_BIT_SYNC;
    }
    if ((o & ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_SUBFRAME_SYNC) == static_cast<uint32_t>(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_SUBFRAME_SYNC)) {
        os += (first ? "" : " | ");
        os += "STATE_SUBFRAME_SYNC";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_SUBFRAME_SYNC;
    }
    if ((o & ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_TOW_DECODED) == static_cast<uint32_t>(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_TOW_DECODED)) {
        os += (first ? "" : " | ");
        os += "STATE_TOW_DECODED";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_TOW_DECODED;
    }
    if ((o & ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_MSEC_AMBIGUOUS) == static_cast<uint32_t>(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_MSEC_AMBIGUOUS)) {
        os += (first ? "" : " | ");
        os += "STATE_MSEC_AMBIGUOUS";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_MSEC_AMBIGUOUS;
    }
    if ((o & ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_SYMBOL_SYNC) == static_cast<uint32_t>(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_SYMBOL_SYNC)) {
        os += (first ? "" : " | ");
        os += "STATE_SYMBOL_SYNC";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_SYMBOL_SYNC;
    }
    if ((o & ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GLO_STRING_SYNC) == static_cast<uint32_t>(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GLO_STRING_SYNC)) {
        os += (first ? "" : " | ");
        os += "STATE_GLO_STRING_SYNC";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GLO_STRING_SYNC;
    }
    if ((o & ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GLO_TOD_DECODED) == static_cast<uint32_t>(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GLO_TOD_DECODED)) {
        os += (first ? "" : " | ");
        os += "STATE_GLO_TOD_DECODED";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GLO_TOD_DECODED;
    }
    if ((o & ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_BDS_D2_BIT_SYNC) == static_cast<uint32_t>(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_BDS_D2_BIT_SYNC)) {
        os += (first ? "" : " | ");
        os += "STATE_BDS_D2_BIT_SYNC";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_BDS_D2_BIT_SYNC;
    }
    if ((o & ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_BDS_D2_SUBFRAME_SYNC) == static_cast<uint32_t>(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_BDS_D2_SUBFRAME_SYNC)) {
        os += (first ? "" : " | ");
        os += "STATE_BDS_D2_SUBFRAME_SYNC";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_BDS_D2_SUBFRAME_SYNC;
    }
    if ((o & ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GAL_E1BC_CODE_LOCK) == static_cast<uint32_t>(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GAL_E1BC_CODE_LOCK)) {
        os += (first ? "" : " | ");
        os += "STATE_GAL_E1BC_CODE_LOCK";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GAL_E1BC_CODE_LOCK;
    }
    if ((o & ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GAL_E1C_2ND_CODE_LOCK) == static_cast<uint32_t>(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GAL_E1C_2ND_CODE_LOCK)) {
        os += (first ? "" : " | ");
        os += "STATE_GAL_E1C_2ND_CODE_LOCK";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GAL_E1C_2ND_CODE_LOCK;
    }
    if ((o & ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GAL_E1B_PAGE_SYNC) == static_cast<uint32_t>(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GAL_E1B_PAGE_SYNC)) {
        os += (first ? "" : " | ");
        os += "STATE_GAL_E1B_PAGE_SYNC";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GAL_E1B_PAGE_SYNC;
    }
    if ((o & ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_SBAS_SYNC) == static_cast<uint32_t>(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_SBAS_SYNC)) {
        os += (first ? "" : " | ");
        os += "STATE_SBAS_SYNC";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_SBAS_SYNC;
    }
    if ((o & ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_TOW_KNOWN) == static_cast<uint32_t>(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_TOW_KNOWN)) {
        os += (first ? "" : " | ");
        os += "STATE_TOW_KNOWN";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_TOW_KNOWN;
    }
    if ((o & ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GLO_TOD_KNOWN) == static_cast<uint32_t>(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GLO_TOD_KNOWN)) {
        os += (first ? "" : " | ");
        os += "STATE_GLO_TOD_KNOWN";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GLO_TOD_KNOWN;
    }
    if ((o & ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_2ND_CODE_LOCK) == static_cast<uint32_t>(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_2ND_CODE_LOCK)) {
        os += (first ? "" : " | ");
        os += "STATE_2ND_CODE_LOCK";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_2ND_CODE_LOCK;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_UNKNOWN) {
        return "STATE_UNKNOWN";
    }
    if (o == ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_CODE_LOCK) {
        return "STATE_CODE_LOCK";
    }
    if (o == ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_BIT_SYNC) {
        return "STATE_BIT_SYNC";
    }
    if (o == ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_SUBFRAME_SYNC) {
        return "STATE_SUBFRAME_SYNC";
    }
    if (o == ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_TOW_DECODED) {
        return "STATE_TOW_DECODED";
    }
    if (o == ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_MSEC_AMBIGUOUS) {
        return "STATE_MSEC_AMBIGUOUS";
    }
    if (o == ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_SYMBOL_SYNC) {
        return "STATE_SYMBOL_SYNC";
    }
    if (o == ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GLO_STRING_SYNC) {
        return "STATE_GLO_STRING_SYNC";
    }
    if (o == ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GLO_TOD_DECODED) {
        return "STATE_GLO_TOD_DECODED";
    }
    if (o == ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_BDS_D2_BIT_SYNC) {
        return "STATE_BDS_D2_BIT_SYNC";
    }
    if (o == ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_BDS_D2_SUBFRAME_SYNC) {
        return "STATE_BDS_D2_SUBFRAME_SYNC";
    }
    if (o == ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GAL_E1BC_CODE_LOCK) {
        return "STATE_GAL_E1BC_CODE_LOCK";
    }
    if (o == ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GAL_E1C_2ND_CODE_LOCK) {
        return "STATE_GAL_E1C_2ND_CODE_LOCK";
    }
    if (o == ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GAL_E1B_PAGE_SYNC) {
        return "STATE_GAL_E1B_PAGE_SYNC";
    }
    if (o == ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_SBAS_SYNC) {
        return "STATE_SBAS_SYNC";
    }
    if (o == ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_TOW_KNOWN) {
        return "STATE_TOW_KNOWN";
    }
    if (o == ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GLO_TOD_KNOWN) {
        return "STATE_GLO_TOD_KNOWN";
    }
    if (o == ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_2ND_CODE_LOCK) {
        return "STATE_2ND_CODE_LOCK";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".v1_1 = ";
    os += ::android::hardware::gnss::V1_1::toString(o.v1_1);
    os += ", .codeType = ";
    os += ::android::hardware::toString(o.codeType);
    os += ", .state = ";
    os += ::android::hardware::gnss::V2_0::toString<::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState>(o.state);
    os += ", .constellation = ";
    os += ::android::hardware::gnss::V2_0::toString(o.constellation);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement& lhs, const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement& rhs) {
    if (lhs.v1_1 != rhs.v1_1) {
        return false;
    }
    if (lhs.codeType != rhs.codeType) {
        return false;
    }
    if (lhs.state != rhs.state) {
        return false;
    }
    if (lhs.constellation != rhs.constellation) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement& lhs, const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurement& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssData& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".measurements = ";
    os += ::android::hardware::toString(o.measurements);
    os += ", .clock = ";
    os += ::android::hardware::gnss::V1_0::toString(o.clock);
    os += ", .elapsedRealtime = ";
    os += ::android::hardware::gnss::V2_0::toString(o.elapsedRealtime);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssData& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssData& lhs, const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssData& rhs) {
    if (lhs.measurements != rhs.measurements) {
        return false;
    }
    if (lhs.clock != rhs.clock) {
        return false;
    }
    if (lhs.elapsedRealtime != rhs.elapsedRealtime) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssData& lhs, const ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssData& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V2_0::IGnssMeasurementCallback>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V2_0
}  // namespace gnss
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
template<> inline constexpr std::array<::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState, 18> hidl_enum_values<::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState> = {
    ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_UNKNOWN,
    ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_CODE_LOCK,
    ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_BIT_SYNC,
    ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_SUBFRAME_SYNC,
    ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_TOW_DECODED,
    ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_MSEC_AMBIGUOUS,
    ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_SYMBOL_SYNC,
    ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GLO_STRING_SYNC,
    ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GLO_TOD_DECODED,
    ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_BDS_D2_BIT_SYNC,
    ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_BDS_D2_SUBFRAME_SYNC,
    ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GAL_E1BC_CODE_LOCK,
    ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GAL_E1C_2ND_CODE_LOCK,
    ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GAL_E1B_PAGE_SYNC,
    ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_SBAS_SYNC,
    ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_TOW_KNOWN,
    ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_GLO_TOD_KNOWN,
    ::android::hardware::gnss::V2_0::IGnssMeasurementCallback::GnssMeasurementState::STATE_2ND_CODE_LOCK,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_IGNSSMEASUREMENTCALLBACK_H
