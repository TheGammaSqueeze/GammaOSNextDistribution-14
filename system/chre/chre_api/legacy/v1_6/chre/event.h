/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef _CHRE_EVENT_H_
#define _CHRE_EVENT_H_

/**
 * @file
 * Context Hub Runtime Environment API dealing with events and messages.
 */

#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

#include <chre/toolchain.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * The CHRE implementation is required to provide the following preprocessor
 * defines via the build system.
 *
 * CHRE_MESSAGE_TO_HOST_MAX_SIZE: The maximum size, in bytes, allowed for
 *     a message sent to chreSendMessageToHostEndpoint().  This must be at least
 *     CHRE_MESSAGE_TO_HOST_MINIMUM_MAX_SIZE.
 */

#ifndef CHRE_MESSAGE_TO_HOST_MAX_SIZE
#error CHRE_MESSAGE_TO_HOST_MAX_SIZE must be defined by the CHRE implementation
#endif

/**
 * The minimum size, in bytes, any CHRE implementation will use for
 * CHRE_MESSAGE_TO_HOST_MAX_SIZE is set to 1000 for v1.5+ CHRE implementations,
 * and 128 for v1.0-v1.4 implementations (previously kept in
 * CHRE_MESSAGE_TO_HOST_MINIMUM_MAX_SIZE, which has been removed).
 *
 * All CHRE implementations supporting v1.5+ must support the raised limit of
 * 1000 bytes, however a nanoapp compiled against v1.5 cannot assume this
 * limit if there is a possibility their binary will run on a v1.4 or earlier
 * implementation that had a lower limit. To allow for nanoapp compilation in
 * these situations, CHRE_MESSAGE_TO_HOST_MAX_SIZE must be set to the minimum
 * value the nanoapp may encounter, and CHRE_NANOAPP_SUPPORTS_PRE_V1_5 can be
 * defined to skip the compile-time check.
 */
#if (!defined(CHRE_NANOAPP_SUPPORTS_PRE_V1_5) && \
     CHRE_MESSAGE_TO_HOST_MAX_SIZE < 1000) ||    \
    (defined(CHRE_NANOAPP_SUPPORTS_PRE_V1_5) &&  \
     CHRE_MESSAGE_TO_HOST_MAX_SIZE < 128)
#error CHRE_MESSAGE_TO_HOST_MAX_SIZE is too small.
#endif

/**
 * The lowest numerical value legal for a user-defined event.
 *
 * The system reserves all event values from 0 to 0x7FFF, inclusive.
 * User events may use any value in the range 0x8000 to 0xFFFF, inclusive.
 *
 * Note that the same event values might be used by different nanoapps
 * for different meanings.  This is not a concern, as these values only
 * have meaning when paired with the originating nanoapp.
 */
#define CHRE_EVENT_FIRST_USER_VALUE  UINT16_C(0x8000)

/**
 * nanoappHandleEvent argument: struct chreMessageFromHostData
 *
 * The format of the 'message' part of this structure is left undefined,
 * and it's up to the nanoapp and host to have an established protocol
 * beforehand.
 */
#define CHRE_EVENT_MESSAGE_FROM_HOST  UINT16_C(0x0001)

/**
 * nanoappHandleEvent argument: 'cookie' given to chreTimerSet() method.
 *
 * Indicates that a timer has elapsed, in accordance with how chreTimerSet() was
 * invoked.
 */
#define CHRE_EVENT_TIMER  UINT16_C(0x0002)

/**
 * nanoappHandleEvent argument: struct chreNanoappInfo
 *
 * Indicates that a nanoapp has successfully started (its nanoappStart()
 * function has been called, and it returned true) and is able to receive events
 * sent via chreSendEvent().  Note that this event is not sent for nanoapps that
 * were started prior to the current nanoapp - use chreGetNanoappInfo() to
 * determine if another nanoapp is already running.
 *
 * @see chreConfigureNanoappInfoEvents
 * @since v1.1
 */
#define CHRE_EVENT_NANOAPP_STARTED  UINT16_C(0x0003)

/**
 * nanoappHandleEvent argument: struct chreNanoappInfo
 *
 * Indicates that a nanoapp has stopped executing and is no longer able to
 * receive events sent via chreSendEvent().  Any events sent prior to receiving
 * this event are not guaranteed to have been delivered.
 *
 * @see chreConfigureNanoappInfoEvents
 * @since v1.1
 */
#define CHRE_EVENT_NANOAPP_STOPPED  UINT16_C(0x0004)

/**
 * nanoappHandleEvent argument: NULL
 *
 * Indicates that CHRE has observed the host wake from low-power sleep state.
 *
 * @see chreConfigureHostSleepStateEvents
 * @since v1.2
 */
#define CHRE_EVENT_HOST_AWAKE  UINT16_C(0x0005)

/**
 * nanoappHandleEvent argument: NULL
 *
 * Indicates that CHRE has observed the host enter low-power sleep state.
 *
 * @see chreConfigureHostSleepStateEvents
 * @since v1.2
 */
#define CHRE_EVENT_HOST_ASLEEP  UINT16_C(0x0006)

/**
 * nanoappHandleEvent argument: NULL
 *
 * Indicates that CHRE is collecting debug dumps. Nanoapps can call
 * chreDebugDumpLog() to log their debug data while handling this event.
 *
 * @see chreConfigureDebugDumpEvent
 * @see chreDebugDumpLog
 * @since v1.4
 */
#define CHRE_EVENT_DEBUG_DUMP  UINT16_C(0x0007)

/**
 * nanoappHandleEvent argument: struct chreHostEndpointNotification
 *
 * Notifications event regarding a host endpoint.
 *
 * @see chreConfigureHostEndpointNotifications
 * @since v1.6
 */
#define CHRE_EVENT_HOST_ENDPOINT_NOTIFICATION UINT16_C(0x0008)

/**
 * First possible value for CHRE_EVENT_SENSOR events.
 *
 * This allows us to separately define our CHRE_EVENT_SENSOR_* events in
 * chre/sensor.h, without fear of collision with other event values.
 */
#define CHRE_EVENT_SENSOR_FIRST_EVENT  UINT16_C(0x0100)

/**
 * Last possible value for CHRE_EVENT_SENSOR events.
 *
 * This allows us to separately define our CHRE_EVENT_SENSOR_* events in
 * chre/sensor.h, without fear of collision with other event values.
 */
#define CHRE_EVENT_SENSOR_LAST_EVENT  UINT16_C(0x02FF)

/**
 * First event in the block reserved for GNSS. These events are defined in
 * chre/gnss.h.
 */
#define CHRE_EVENT_GNSS_FIRST_EVENT  UINT16_C(0x0300)
#define CHRE_EVENT_GNSS_LAST_EVENT   UINT16_C(0x030F)

/**
 * First event in the block reserved for WiFi. These events are defined in
 * chre/wifi.h.
 */
#define CHRE_EVENT_WIFI_FIRST_EVENT  UINT16_C(0x0310)
#define CHRE_EVENT_WIFI_LAST_EVENT   UINT16_C(0x031F)

/**
 * First event in the block reserved for WWAN. These events are defined in
 * chre/wwan.h.
 */
#define CHRE_EVENT_WWAN_FIRST_EVENT  UINT16_C(0x0320)
#define CHRE_EVENT_WWAN_LAST_EVENT   UINT16_C(0x032F)

/**
 * First event in the block reserved for audio. These events are defined in
 * chre/audio.h.
 */
#define CHRE_EVENT_AUDIO_FIRST_EVENT UINT16_C(0x0330)
#define CHRE_EVENT_AUDIO_LAST_EVENT  UINT16_C(0x033F)

/**
 * First event in the block reserved for settings changed notifications.
 * These events are defined in chre/user_settings.h
 *
 * @since v1.5
 */
#define CHRE_EVENT_SETTING_CHANGED_FIRST_EVENT UINT16_C(0x340)
#define CHRE_EVENT_SETTING_CHANGED_LAST_EVENT  UINT16_C(0x34F)

/**
 * First event in the block reserved for Bluetooth LE. These events are defined
 * in chre/ble.h.
 */
#define CHRE_EVENT_BLE_FIRST_EVENT UINT16_C(0x0350)
#define CHRE_EVENT_BLE_LAST_EVENT  UINT16_C(0x035F)

/**
 * First in the extended range of values dedicated for internal CHRE
 * implementation usage.
 *
 * This range is semantically the same as the internal event range defined
 * below, but has been extended to allow for more implementation-specific events
 * to be used.
 *
 * @since v1.1
 */
#define CHRE_EVENT_INTERNAL_EXTENDED_FIRST_EVENT  UINT16_C(0x7000)

/**
 * First in a range of values dedicated for internal CHRE implementation usage.
 *
 * If a CHRE wishes to use events internally, any values within this range
 * are assured not to be taken by future CHRE API additions.
 */
#define CHRE_EVENT_INTERNAL_FIRST_EVENT  UINT16_C(0x7E00)

/**
 * Last in a range of values dedicated for internal CHRE implementation usage.
 *
 * If a CHRE wishes to use events internally, any values within this range
 * are assured not to be taken by future CHRE API additions.
 */
#define CHRE_EVENT_INTERNAL_LAST_EVENT  UINT16_C(0x7FFF)

/**
 * A special value for the hostEndpoint argument in
 * chreSendMessageToHostEndpoint() that indicates that the message should be
 * delivered to all host endpoints.  This value will not be used in the
 * hostEndpoint field of struct chreMessageFromHostData supplied with
 * CHRE_EVENT_MESSAGE_FROM_HOST.
 *
 * @since v1.1
 */
#define CHRE_HOST_ENDPOINT_BROADCAST  UINT16_C(0xFFFF)

/**
 * A special value for hostEndpoint in struct chreMessageFromHostData that
 * indicates that a host endpoint is unknown or otherwise unspecified.  This
 * value may be received in CHRE_EVENT_MESSAGE_FROM_HOST, but it is not valid to
 * provide it to chreSendMessageToHostEndpoint().
 *
 * @since v1.1
 */
#define CHRE_HOST_ENDPOINT_UNSPECIFIED  UINT16_C(0xFFFE)

/**
 * Bitmask values that can be given as input to the messagePermissions parameter
 * of chreSendMessageWithPermissions(). These values are typically used by
 * nanoapps when they used data from the corresponding CHRE APIs to produce the
 * message contents being sent and is used to attribute permissions usage on
 * the Android side. See chreSendMessageWithPermissions() for more details on
 * how these values are used when sending a message.
 *
 * Values in the range
 * [CHRE_MESSAGE_PERMISSION_VENDOR_START, CHRE_MESSAGE_PERMISSION_VENDOR_END]
 * are reserved for vendors to use when adding support for permission-gated APIs
 * in their implementations.
 *
 * On the Android side, CHRE permissions are mapped as follows:
 * - CHRE_MESSAGE_PERMISSION_AUDIO: android.permission.RECORD_AUDIO
 * - CHRE_MESSAGE_PERMISSION_GNSS, CHRE_MESSAGE_PERMISSION_WIFI, and
 *   CHRE_MESSAGE_PERMISSION_WWAN: android.permission.ACCESS_FINE_LOCATION, and
 *   android.permissions.ACCESS_BACKGROUND_LOCATION
 *
 * @since v1.5
 *
 * @defgroup CHRE_MESSAGE_PERMISSION
 * @{
 */

#define CHRE_MESSAGE_PERMISSION_NONE UINT32_C(0)
#define CHRE_MESSAGE_PERMISSION_AUDIO UINT32_C(1)
#define CHRE_MESSAGE_PERMISSION_GNSS (UINT32_C(1) << 1)
#define CHRE_MESSAGE_PERMISSION_WIFI (UINT32_C(1) << 2)
#define CHRE_MESSAGE_PERMISSION_WWAN (UINT32_C(1) << 3)
#define CHRE_MESSAGE_PERMISSION_BLE (UINT32_C(1) << 4)
#define CHRE_MESSAGE_PERMISSION_VENDOR_START (UINT32_C(1) << 24)
#define CHRE_MESSAGE_PERMISSION_VENDOR_END (UINT32_C(1) << 31)

/** @} */

/**
 * Data provided with CHRE_EVENT_MESSAGE_FROM_HOST.
 */
struct chreMessageFromHostData {
    /**
     * Message type supplied by the host.
     *
     * @note In CHRE API v1.0, support for forwarding this field from the host
     * was not strictly required, and some implementations did not support it.
     * However, its support is mandatory as of v1.1.
     */
    union {
        /**
         * The preferred name to use when referencing this field.
         *
         * @since v1.1
         */
        uint32_t messageType;

        /**
         * @deprecated This is the name for the messageType field used in v1.0.
         * Left to allow code to compile against both v1.0 and v1.1 of the API
         * definition without needing to use #ifdefs. This will be removed in a
         * future API update - use messageType instead.
         */
        uint32_t reservedMessageType;
    };

    /**
     * The size, in bytes of the following 'message'.
     *
     * This can be 0.
     */
    uint32_t messageSize;

    /**
     * The message from the host.
     *
     * These contents are of a format that the host and nanoapp must have
     * established beforehand.
     *
     * This data is 'messageSize' bytes in length.  Note that if 'messageSize'
     * is 0, this might be NULL.
     */
    const void *message;

    /**
     * An identifier for the host-side entity that sent this message.  Unless
     * this is set to CHRE_HOST_ENDPOINT_UNSPECIFIED, it can be used in
     * chreSendMessageToHostEndpoint() to send a directed reply that will only
     * be received by the given entity on the host.  Endpoint identifiers are
     * opaque values assigned at runtime, so they cannot be assumed to always
     * describe a specific entity across restarts.
     *
     * If running on a CHRE API v1.0 implementation, this field will always be
     * set to CHRE_HOST_ENDPOINT_UNSPECIFIED.
     *
     * @since v1.1
     */
    uint16_t hostEndpoint;
};

/**
 * Provides metadata for a nanoapp in the system.
 */
struct chreNanoappInfo {
    /**
     * Nanoapp identifier. The convention for populating this value is to set
     * the most significant 5 bytes to a value that uniquely identifies the
     * vendor, and the lower 3 bytes identify the nanoapp.
     */
    uint64_t appId;

    /**
     * Nanoapp version.  The semantics of this field are defined by the nanoapp,
     * however nanoapps are recommended to follow the same scheme used for the
     * CHRE version exposed in chreGetVersion().  That is, the most significant
     * byte represents the major version, the next byte the minor version, and
     * the lower two bytes the patch version.
     */
    uint32_t version;

    /**
     * The instance ID of this nanoapp, which can be used in chreSendEvent() to
     * address an event specifically to this nanoapp.  This identifier is
     * guaranteed to be unique among all nanoapps in the system.
     *
     * @since v1.6
     * Instance ID is guaranteed to never go beyond INT16_MAX. This helps the
     * instance ID be packed into other information inside an int (useful for
     * RPC routing).
     */
    uint32_t instanceId;
};

/**
 * The types of notification events that can be included in struct
 * chreHostEndpointNotification.
 *
 * @defgroup HOST_ENDPOINT_NOTIFICATION_TYPE
 * @{
 */
#define HOST_ENDPOINT_NOTIFICATION_TYPE_DISCONNECT UINT8_C(0)
/** @} */

/**
 * Data provided in CHRE_EVENT_HOST_ENDPOINT_NOTIFICATION.
 */
struct chreHostEndpointNotification {
    /**
     * The ID of the host endpoint that this notification is for.
     */
    uint16_t hostEndpointId;

    /**
     * The type of notification this event represents, which should be
     * one of the HOST_ENDPOINT_NOTIFICATION_TYPE_* values.
     */
    uint8_t notificationType;

    /**
     * Reserved for future use, must be zero.
     */
    uint8_t reserved;
};

//! The maximum length of a host endpoint's name.
#define CHRE_MAX_ENDPOINT_NAME_LEN (51)

//! The maximum length of a host endpoint's tag.
#define CHRE_MAX_ENDPOINT_TAG_LEN (51)

/**
 * The type of host endpoint that can be used in the hostEndpointType field
 * of chreHostEndpointInfo.
 *
 * @since v1.6
 *
 * @defgroup CHRE_HOST_ENDPOINT_TYPE_
 * @{
 */

//! The host endpoint is part of the Android system framework.
#define CHRE_HOST_ENDPOINT_TYPE_FRAMEWORK UINT8_C(0)

//! The host endpoint is an Android app.
#define CHRE_HOST_ENDPOINT_TYPE_APP UINT8_C(1)

//! Values in the range [CHRE_HOST_ENDPOINT_TYPE_VENDOR_START,
//! CHRE_HOST_ENDPOINT_TYPE_VENDOR_END] can be a custom defined host endpoint
//! type for platform-specific vendor use.
#define CHRE_HOST_ENDPOINT_TYPE_VENDOR_START UINT8_C(128)
#define CHRE_HOST_ENDPOINT_TYPE_VENDOR_END UINT8_C(255)

/** @} */

/**
 * Provides metadata for a host endpoint.
 *
 * @since v1.6
 */
struct chreHostEndpointInfo {
    //! The endpoint ID of this host.
    uint16_t hostEndpointId;

    //! The type of host endpoint, which must be set to one of the
    //! CHRE_HOST_ENDPOINT_TYPE_* values or a value in the vendor-reserved
    //! range.
    uint8_t hostEndpointType;

    //! Flag indicating if the packageName/endpointName field is valid.
    uint8_t isNameValid : 1;

    //! Flag indicating if the attributionTag/endpointTag field is valid.
    uint8_t isTagValid : 1;

    //! A union of null-terminated host name strings.
    union {
        //! The Android package name associated with this host, valid if the
        //! hostEndpointType is CHRE_HOST_ENDPOINT_TYPE_APP or
        //! CHRE_HOST_ENDPOINT_TYPE_FRAMEWORK. Refer to the Android documentation
        //! for the package attribute in the app manifest.
        char packageName[CHRE_MAX_ENDPOINT_NAME_LEN];

        //! A generic endpoint name that can be used for endpoints that
        //! may not have a package name.
        char endpointName[CHRE_MAX_ENDPOINT_NAME_LEN];
    };

    //! A union of null-terminated host tag strings for further identification.
    union {
        //! The attribution tag associated with this host that is used to audit
        //! access to data, which can be valid if the hostEndpointType is
        //! CHRE_HOST_ENDPOINT_TYPE_APP. Refer to the Android documentation
        //! regarding data audit using attribution tags.
        char attributionTag[CHRE_MAX_ENDPOINT_TAG_LEN];

        //! A generic endpoint tag that can be used for endpoints that
        //! may not have an attribution tag.
        char endpointTag[CHRE_MAX_ENDPOINT_TAG_LEN];
    };
};

/**
 * An RPC service exposed by a nanoapp.
 *
 * The implementation of the RPC interface is not defined by the HAL, and is written
 * at the messaging endpoint layers (Android app and/or CHRE nanoapp). NanoappRpcService
 * contains the informational metadata to be consumed by the RPC interface layer.
 */
struct chreNanoappRpcService {
    /**
     * The unique 64-bit ID of an RPC service exposed by a nanoapp. Note that
     * the uniqueness is only required within the nanoapp's domain (i.e. the
     * combination of the nanoapp ID and service id must be unique).
     */
    uint64_t id;

    /**
     * The software version of this service, which follows the sematic
     * versioning scheme (see semver.org). It follows the format
     * major.minor.patch, where major and minor versions take up one byte
     * each, and the patch version takes up the final 2 bytes.
     */
    uint32_t version;
};

/**
 * Callback which frees data associated with an event.
 *
 * This callback is (optionally) provided to the chreSendEvent() method as
 * a means for freeing the event data and performing any other cleanup
 * necessary when the event is completed.  When this callback is invoked,
 * 'eventData' is no longer needed and can be released.
 *
 * @param eventType  The 'eventType' argument from chreSendEvent().
 * @param eventData  The 'eventData' argument from chreSendEvent().
 *
 * @see chreSendEvent
 */
typedef void (chreEventCompleteFunction)(uint16_t eventType, void *eventData);

/**
 * Callback which frees a message.
 *
 * This callback is (optionally) provided to the chreSendMessageToHostEndpoint()
 * method as a means for freeing the message.  When this callback is invoked,
 * 'message' is no longer needed and can be released.  Note that this in
 * no way assures that said message did or did not make it to the host, simply
 * that this memory is no longer needed.
 *
 * @param message  The 'message' argument from chreSendMessageToHostEndpoint().
 * @param messageSize  The 'messageSize' argument from
 *     chreSendMessageToHostEndpoint().
 *
 * @see chreSendMessageToHostEndpoint
 */
typedef void (chreMessageFreeFunction)(void *message, size_t messageSize);


/**
 * Enqueue an event to be sent to another nanoapp.
 *
 * @param eventType  This is a user-defined event type, of at least the
 *     value CHRE_EVENT_FIRST_USER_VALUE.  It is illegal to attempt to use any
 *     of the CHRE_EVENT_* values reserved for the CHRE.
 * @param eventData  A pointer value that will be understood by the receiving
 *     app.  Note that NULL is perfectly acceptable.  It also is not required
 *     that this be a valid pointer, although if this nanoapp is intended to
 *     work on arbitrary CHRE implementations, then the size of a
 *     pointer cannot be assumed to be a certain size.  Note that the caller
 *     no longer owns this memory after the call.
 * @param freeCallback  A pointer to a callback function.  After the lifetime
 *     of 'eventData' is over (either through successful delivery or the event
 *     being dropped), this callback will be invoked.  This argument is allowed
 *     to be NULL, in which case no callback will be invoked.
 * @param targetInstanceId  The ID of the instance we're delivering this event
 *     to.  Note that this is allowed to be our own instance.  The instance ID
 *     of a nanoapp can be retrieved by using chreGetNanoappInfoByInstanceId().
 * @return true if the event was enqueued, false otherwise.  Note that even
 *     if this method returns 'false', the 'freeCallback' will be invoked,
 *     if non-NULL.  Note in the 'false' case, the 'freeCallback' may be
 *     invoked directly from within chreSendEvent(), so it's necessary
 *     for nanoapp authors to avoid possible recursion with this.
 *
 * @see chreEventDataFreeFunction
 */
bool chreSendEvent(uint16_t eventType, void *eventData,
                   chreEventCompleteFunction *freeCallback,
                   uint32_t targetInstanceId);

/**
 * Send a message to the host, using the broadcast endpoint
 * CHRE_HOST_ENDPOINT_BROADCAST.  Refer to chreSendMessageToHostEndpoint() for
 * further details.
 *
 * @see chreSendMessageToHostEndpoint
 *
 * @deprecated New code should use chreSendMessageToHostEndpoint() instead of
 * this function.  A future update to the API may cause references to this
 * function to produce a compiler warning.
 */
bool chreSendMessageToHost(void *message, uint32_t messageSize,
                           uint32_t messageType,
                           chreMessageFreeFunction *freeCallback)
    CHRE_DEPRECATED("Use chreSendMessageToHostEndpoint instead");

/**
 * Send a message to the host, using CHRE_MESSAGE_PERMISSION_NONE for the
 * associated message permissions. This method must only be used if no data
 * provided by CHRE's audio, GNSS, WiFi, and WWAN APIs was used to produce the
 * contents of the message being sent. Refer to chreSendMessageWithPermissions()
 * for further details.
 *
 * @see chreSendMessageWithPermissions
 *
 * @since v1.1
 */
bool chreSendMessageToHostEndpoint(void *message, size_t messageSize,
                                   uint32_t messageType, uint16_t hostEndpoint,
                                   chreMessageFreeFunction *freeCallback);

/**
 * Send a message to the host, waking it up if it is currently asleep.
 *
 * This message is by definition arbitrarily defined.  Since we're not
 * just a passing a pointer to memory around the system, but need to copy
 * this into various buffers to send it to the host, the CHRE
 * implementation cannot be asked to support an arbitrarily large message
 * size.  As a result, we have the CHRE implementation define
 * CHRE_MESSAGE_TO_HOST_MAX_SIZE.
 *
 * CHRE_MESSAGE_TO_HOST_MAX_SIZE is not given a value by the Platform API.  The
 * Platform API does define CHRE_MESSAGE_TO_HOST_MINIMUM_MAX_SIZE, and requires
 * that CHRE_MESSAGE_TO_HOST_MAX_SIZE is at least that value.
 *
 * As a result, if your message sizes are all less than
 * CHRE_MESSAGE_TO_HOST_MINIMUM_MAX_SIZE, then you have no concerns on any
 * CHRE implementation.  If your message sizes are larger, you'll need to
 * come up with a strategy for splitting your message across several calls
 * to this method.  As long as that strategy works for
 * CHRE_MESSAGE_TO_HOST_MINIMUM_MAX_SIZE, it will work across all CHRE
 * implementations (although on some implementations less calls to this
 * method may be necessary).
 *
 * When sending a message to the host, the ContextHub service will enforce
 * the host client has been granted Android-level permissions corresponding to
 * the ones the nanoapp declares it uses through CHRE_NANOAPP_USES_AUDIO, etc.
 * In addition to this, the permissions bitmask provided as input to this method
 * results in the Android framework using app-ops to verify and log access upon
 * message delivery to an application. This is primarily useful for ensuring
 * accurate attribution for messages generated using permission-controlled data.
 * The bitmask declared by the nanoapp for this message must be a
 * subset of the permissions it declared it would use at build time or the
 * message will be rejected.
 *
 * Nanoapps must use this method if the data they are sending contains or was
 * derived from any data sampled through CHRE's audio, GNSS, WiFi, or WWAN APIs.
 * Additionally, if vendors add APIs to expose data that would be guarded by a
 * permission in Android, vendors must support declaring a message permission
 * through this method.
 *
 * @param message  Pointer to a block of memory to send to the host.
 *     NULL is acceptable only if messageSize is 0.  If non-NULL, this
 *     must be a legitimate pointer (that is, unlike chreSendEvent(), a small
 *     integral value cannot be cast to a pointer for this).  Note that the
 *     caller no longer owns this memory after the call.
 * @param messageSize  The size, in bytes, of the given message. If this exceeds
 *     CHRE_MESSAGE_TO_HOST_MAX_SIZE, the message will be rejected.
 * @param messageType  Message type sent to the app on the host.
 *     NOTE: In CHRE API v1.0, support for forwarding this field to the host was
 *     not strictly required, and some implementations did not support it.
 *     However, its support is mandatory as of v1.1.
 * @param hostEndpoint  An identifier for the intended recipient of the message,
 *     or CHRE_HOST_ENDPOINT_BROADCAST if all registered endpoints on the host
 *     should receive the message.  Endpoint identifiers are assigned on the
 *     host side, and nanoapps may learn of the host endpoint ID of an intended
 *     recipient via an initial message sent by the host.  This parameter is
 *     always treated as CHRE_HOST_ENDPOINT_BROADCAST if running on a CHRE API
 *     v1.0 implementation. CHRE_HOST_ENDPOINT_BROADCAST isn't allowed to be
 *     specified if anything other than CHRE_MESSAGE_PERMISSION_NONE is given
 *     as messagePermissions since doing so would potentially attribute
 *     permissions usage to host clients that don't intend to consume the data.
 * @param messagePermissions Bitmasked CHRE_MESSAGE_PERMISSION_ values that will
 *     be converted to corresponding Android-level permissions and attributed
 *     the host endpoint upon consumption of the message.
 * @param freeCallback  A pointer to a callback function.  After the lifetime
 *     of 'message' is over (which does not assure that 'message' made it to
 *     the host, just that the transport layer no longer needs this memory),
 *     this callback will be invoked.  This argument is allowed
 *     to be NULL, in which case no callback will be invoked.
 * @return true if the message was accepted for transmission, false otherwise.
 *     Note that even if this method returns 'false', the 'freeCallback' will
 *     be invoked, if non-NULL.  In either case, the 'freeCallback' may be
 *     invoked directly from within chreSendMessageToHostEndpoint(), so it's
 *     necessary for nanoapp authors to avoid possible recursion with this.
 *
 * @see chreMessageFreeFunction
 *
 * @since v1.5
 */
bool chreSendMessageWithPermissions(void *message, size_t messageSize,
                                    uint32_t messageType, uint16_t hostEndpoint,
                                    uint32_t messagePermissions,
                                    chreMessageFreeFunction *freeCallback);

/**
 * Queries for information about a nanoapp running in the system.
 *
 * In the current API, appId is required to be unique, i.e. there cannot be two
 * nanoapps running concurrently with the same appId.  If this restriction is
 * removed in a future API version and multiple instances of the same appId are
 * present, this function must always return the first app to start.
 *
 * @param appId Identifier for the nanoapp that the caller is requesting
 *     information about.
 * @param info Output parameter.  If this function returns true, this structure
 *     will be populated with details of the specified nanoapp.
 * @return true if a nanoapp with the given ID is currently running, and the
 *     supplied info parameter was populated with its information.
 *
 * @since v1.1
 */
bool chreGetNanoappInfoByAppId(uint64_t appId, struct chreNanoappInfo *info);

/**
 * Queries for information about a nanoapp running in the system, using the
 * runtime unique identifier.  This method can be used to get information about
 * the sender of an event.
 *
 * @param instanceId
 * @param info Output parameter.  If this function returns true, this structure
 *     will be populated with details of the specified nanoapp.
 * @return true if a nanoapp with the given instance ID is currently running,
 *     and the supplied info parameter was populated with its information.
 *
 * @since v1.1
 */
bool chreGetNanoappInfoByInstanceId(uint32_t instanceId,
                                    struct chreNanoappInfo *info);

/**
 * Configures whether this nanoapp will be notified when other nanoapps in the
 * system start and stop, via CHRE_EVENT_NANOAPP_STARTED and
 * CHRE_EVENT_NANOAPP_STOPPED.  These events are disabled by default, and if a
 * nanoapp is not interested in interacting with other nanoapps, then it does
 * not need to register for them.  However, if inter-nanoapp communication is
 * desired, nanoapps are recommended to call this function from nanoappStart().
 *
 * If running on a CHRE platform that only supports v1.0 of the CHRE API, this
 * function has no effect.
 *
 * @param enable true to enable these events, false to disable
 *
 * @see CHRE_EVENT_NANOAPP_STARTED
 * @see CHRE_EVENT_NANOAPP_STOPPED
 *
 * @since v1.1
 */
void chreConfigureNanoappInfoEvents(bool enable);

/**
 * Configures whether this nanoapp will be notified when the host (applications
 * processor) transitions between wake and sleep, via CHRE_EVENT_HOST_AWAKE and
 * CHRE_EVENT_HOST_ASLEEP.  As chreSendMessageToHostEndpoint() wakes the host if
 * it is asleep, these events can be used to opportunistically send data to the
 * host only when it wakes up for some other reason.  Note that this event is
 * not instantaneous - there is an inherent delay in CHRE observing power state
 * changes of the host processor, which may be significant depending on the
 * implementation, especially in the wake to sleep direction.  Therefore,
 * nanoapps are not guaranteed that messages sent to the host between AWAKE and
 * ASLEEP events will not trigger a host wakeup.  However, implementations must
 * ensure that the nominal wake-up notification latency is strictly less than
 * the minimum wake-sleep time of the host processor.  Implementations are also
 * encouraged to minimize this and related latencies where possible, to avoid
 * unnecessary host wake-ups.
 *
 * These events are only sent on transitions, so the initial state will not be
 * sent to the nanoapp as an event - use chreIsHostAwake().
 *
 * @param enable true to enable these events, false to disable
 *
 * @see CHRE_EVENT_HOST_AWAKE
 * @see CHRE_EVENT_HOST_ASLEEP
 *
 * @since v1.2
 */
void chreConfigureHostSleepStateEvents(bool enable);

/**
 * Retrieves the current sleep/wake state of the host (applications processor).
 * Note that, as with the CHRE_EVENT_HOST_AWAKE and CHRE_EVENT_HOST_ASLEEP
 * events, there is no guarantee that CHRE's view of the host processor's sleep
 * state is instantaneous, and it may also change between querying the state and
 * performing a host-waking action like sending a message to the host.
 *
 * @return true if by CHRE's own estimation the host is currently awake,
 *     false otherwise
 *
 * @since v1.2
 */
bool chreIsHostAwake(void);

/**
 * Configures whether this nanoapp will be notified when CHRE is collecting
 * debug dumps, via CHRE_EVENT_DEBUG_DUMP. This event is disabled by default,
 * and if a nanoapp is not interested in logging its debug data, then it does
 * not need to register for it.
 *
 * @param enable true to enable receipt of this event, false to disable.
 *
 * @see CHRE_EVENT_DEBUG_DUMP
 * @see chreDebugDumpLog
 *
 * @since v1.4
 */
void chreConfigureDebugDumpEvent(bool enable);

/**
 * Configures whether this nanoapp will receive updates regarding a host
 * endpoint that is connected with the Context Hub.
 *
 * If this API succeeds, the nanoapp will receive disconnection notifications,
 * via the CHRE_EVENT_HOST_ENDPOINT_NOTIFICATION event with type
 * HOST_ENDPOINT_NOTIFICATION_TYPE_DISCONNECT, which can be invoked if the host
 * has disconnected from the Context Hub either explicitly or implicitly (e.g.
 * crashes). Nanoapps can use this notifications to clean up any resources
 * associated with this host endpoint.
 *
 * @param hostEndpointId The host endpoint ID to configure notifications for.
 * @param enable true to enable notifications.
 *
 * @return true on success
 *
 * @see chreMessageFromHostData
 * @see chreHostEndpointNotification
 * @see CHRE_EVENT_HOST_ENDPOINT_NOTIFICATION
 *
 * @since v1.6
 */
bool chreConfigureHostEndpointNotifications(uint16_t hostEndpointId,
                                            bool enable);

/**
 * Publishes an RPC service from this nanoapp.
 *
 * When this API is invoked, the list of RPC services will be provided to
 * host applications interacting with the nanoapp.
 *
 * This function must be invoked from nanoappStart(), to guarantee stable output
 * of the list of RPC services supported by the nanoapp.
 *
 * @param services A non-null pointer to the list of RPC services to publish.
 * @param numServices The number of services to publish, i.e. the length of the
 *   services array.
 *
 * @return true if the publishing is successful.
 *
 * @since v1.6
 */
bool chrePublishRpcServices(struct chreNanoappRpcService *services,
                            size_t numServices);

/**
 * Retrieves metadata for a given host endpoint ID.
 *
 * This API will provide metadata regarding an endpoint associated with a
 * host endpoint ID. The nanoapp should use this API to determine more
 * information about a host endpoint that has sent a message to the nanoapp,
 * after receiving a chreMessageFromHostData (which includes the endpoint ID).
 *
 * If the given host endpoint ID is not associated with a valid host (or if the
 * client has disconnected from the Android or CHRE framework, i.e. no longer
 * able to send messages to CHRE), this method will return false and info will
 * not be populated.
 *
 * @param hostEndpointId The endpoint ID of the host to get info for.
 * @param info The non-null pointer to where the metadata will be stored.
 *
 * @return true if info has been successfully populated.
 *
 * @since v1.6
 */
bool chreGetHostEndpointInfo(uint16_t hostEndpointId,
                             struct chreHostEndpointInfo *info);

#ifdef __cplusplus
}
#endif

#endif  /* _CHRE_EVENT_H_ */

