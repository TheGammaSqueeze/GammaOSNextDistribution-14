# Context Hub Peripheral Protocol (CHPP)

The Context Hub Runtime Environment (CHRE) is a feature built into Android which can run background applications on a lower power, always-on processor, with lower energy cost than what is possible when using the main applications processor (AP).

CHPP is designed to run over a serial link (e.g. UART, SPI) and enables an Android Context Hub (CHRE) running on a dedicated low-power processor to connect to peripherals (e.g. GNSS, WiFi). Using CHPP, CHRE can pull together various sources of information, including sensors, location, and audio, to enable a new generation of context-aware applications.

CHPP is a communication protocol that spans from the Transport to the Application layer. The CHPP Transport layer is responsible for correct handling of ARQ, fragmentation, and ordering of datagrams, while the CHPP Application Layer is responsible for the services, including client-service messaging and service discovery.

CHPP is designed to be flexible and future-proof while meeting the memory and processing constraints of both the CHRE and the peripherals that are often even-lower-resourced.

CHPP provides several basic services that are necessary for debugging and service discovery, defines a number of services for the most commonly used sensors and peripherals, and also allows vendor-specific services as necessary. CHPP allows these services to coexist on a single link with very small overhead.

## Integration Quick-Start

A quick-start guide for integration is provided in QUICKSTART.md

## Overview

Within the CHPP transport layer, each endpoint is considered a peer, meaning there are no role differences. However, within the application layer, one endpoint exposes a service to the other endpoint, which is considered to be a client of said service. CHPP is point-to-point, therefore multiple clients to the same service over the same link are not supported. In a typical configuration, the endpoint where CHRE runs will be a client of one or more peripherals that each provide one or more services. For example, CHRE may be running on a microcontroller which has a serial connection to a discrete GNSS (Global Navigation Satellite System) chip that exposes the GNSS CHPP service. However, the protocol is designed such that endpoints can both provide services and serve as clients of remote services simultaneously.

## Error Detection and Correction

CHPP supports checksum based ARQ. CHPP supports implicit NACKs, and optionally supports explicit NACKs. The timeout is implementation dependent with a recommended value of 50 msec.

## Conventions

- Unless otherwise specified, CHPP follows the Little Endian convention.
- Bit-level diagrams are presented with the most-significant bit occupying the leftmost cell
- Bits are numbered with 0 being the least significant bit and 7 being the most significant bit in a byte.

# Transport Layer Packetization

CHPP packets consist of a 2-byte preamble (packet start delimiter) and 8 bytes of header information. The header is followed by zero or more bytes of application-layer payload, typically starting with a 6-byte app header, and ends with a 4-byte packet footer. Defined as a structure (ChppTransportHeader in transport.h), the CHPP transport header is as follows:

- Preamble

  - uint16_t preamble;

- Transport Header

  - uint8_t flags;
  - uint8_t packetCode;
  - uint8_t ackSeq;
  - uint8_t seq;
  - uint16_t length;
  - uint16_t reserved;

## Preamble

CHPP packets use a two-byte preamble as the packet start delimiter. If the receiving side is not actively within a packet, it assumes a packet starts whenever it sees the preamble. For the current version of CHPP, the preamble is 0x6843 (“Ch” in ASCII as a little endian 16-bit integer). For future versions, e.g. for a non-point-to-point version of CHPP, we expect only the LSB to change.

## Packet Flags

Up to 8 flags are supported as a bitmap as follows

### 0x01: Fragmentation

The fragmentation flag allows the transfer of data messages larger than the MTU. In such cases, the data message would be split into multiple fragments. A fragmentation flag of (0) indicates the last/only packet in a datagram. A fragmentation flag set to (1) indicates that the packet is part of an unfinished, fragmented datagram

### 0x02 - 0x80: Reserved

Reserved for future use

## Packet Codes

The packet code field consists of two nibbles. The lower nibble is used for optional error reporting. The higher nibble is used for transport layer configuration.

### Error Reporting (lower nibble)

Error reporting is a recommended feature and can help with debugging and potentially help improve performance. A non-zero explicit NACK code indicates an explicit NACK. The code provides the reason as follows:

- 0x0: No explicit-NACK
- 0x1: Checksum failure
- 0x2: Out of memory
- 0x3: Busy
- 0x4: Invalid header
- 0x5: Out of order packet

### Attributes (higher nibble)

- 0x0: Regular Packet
- 0x1: Reset. The reset code is used at bootup to indicate that readiness, as well as to reset the state to post-bootup state in case of irrecoverable errors. If set, this indicates that the sending endpoint is requesting initialization of the CHPP protocol. The first packet sent after bootup always has this flag set, and endpoints may send a packet with this flag to attempt to recover from protocol failures. Upon receipt, the endpoint resets all its state, for example dropping any packets awaiting transmission, and resetting its service state. It then responds with a reset-ack.
  A reset packet may optionally populate the Error Reporting entry with the reason behind the reset.
  A reset packet has an optional configuration payload [TBD].
- 0x2 Reset-ack. Similar to reset, but sent as a response to reset, as described above.

## ACK Sequence Number

The ack sequence number provides the next expected packets, effectively acknowledging all packets up to (n-1). The 1-byte ack allows for (optional, future) support of group ACKs (up to a window size of 127 packets). Note that fragmented messages have multiple sequence numbers, one for each fragment.
The ack may be sent as part of a packet with or without a payload. In the latter case, the payload length would be set to zero.
If an ACK is not received after a predetermined timeout, or an implicit NACK is received (through an ACK of a lower sequence number), the unacknowledged packet(s) shall be retransmitted.

## Sequence Number

Provides the 8-bit sequence number of this packet. Note that fragmented messages have multiple sequence numbers, one for each fragment.

## Length

Provides the payload length. Devices may have MTU sizes smaller than this limit. Data messages larger than the MTU size can be fragmented according to the Packet Flags: Fragmentation section above.
A value of 0 indicates no payload, which is useful in cases where only transport layer data is relevant, for example when transmitting a bare acknowledgement message.

## Payload

The optional payload immediately follows a non-zero payload length. Its contents are described in the Application Layer section. It typically begins with an app layer header.

## Checksum

Provides an error detection code that is run over of the entire CHPP packet, i.e. anything between the preamble and the checksum fields.
The checksum algorithm is IEEE CRC-32 initialized to 0xFFFFFFFF.

# CHPP Transport - Link Layer API

To allow for a clean C implementation with clear separation of layers while supporting multiple CHPP instances per device, each CHPP layer state is stored in its own context struct.
To tie corresponding transport and application layers together, each layer’s context struct includes a pointer to the context struct of the other corresponding layer (i.e. the transport layer’s context has a pointer to the corresponding app layer’s context struct and vice versa).
Initializing CHPP involves:

1. Allocating the structs for the application, transport, and link layers (in any order).
2. Calling the application and transport layers’ initialization functions (in any order)

## void chppTransportInit(\*transportContext, \*appContext, \*linkContext, \*linkApi)

The CHPP Transport Layer state is stored in the ChppTransportState struct transportContext, and passed around between various functions. It is necessary to initialize the transport layer state for each transport layer instance on every platform.
Each transport layer instance is associated with a single application layer instance. appContext points to the application layer status struct associated with this transport layer instance.
After calling chppTransportInit, it is also necessary to separately initialize the linkContext - note that this can be done in the link layer init function.

## void chppAppInit(\*transportContext, \*appContext)

The CHPP Application Layer state is stored in the ChppAppState struct appContext, and passed around between various functions. It is necessary to initialize the application layer state for each application layer instance on every platform.
Each application layer instance is associated with a single transport layer instance. transportContext points to the transport layer status struct associated with this application layer instance.

## void chppAppInitWithClientServiceSet(\*transportContext, \*appContext, clientServiceSet)

It is also possible to specify the client/service endpoints to be enabled at runtime.

## bool chppRxDataCb(\*transportContext, \*buf, len)

This function is the interface between the CHPP Transport layer and the communications link’s Rx path (e.g. from the UART driver). This function is called when any data is received at the serial interface. The data is provided through a pointer to *buf, with its length specified as len.
The return value from chppRxData(*buf, len) can optionally be used at the communications link driver to improve performance. When the return value is true, the driver may stop sending all-zero payloads (e.g. as might happen when a serial link is idle).

## bool chppRxPacketCompleteCb(\*transportContext)

This is an optional function that enables the link layer to indicate the end of a packet. For packets with a corrupt length field, this function can enable the link layer to explicitly NACK the bad packet earlier.
This function is designed exclusively for link layers that can identify the end of individual packets. The availability of this information depends on the link layer implementation.

## [Link API] enum ChppLinkErrorCode send(\*linkContext, \*buf, len)

This function is the interface between the CHPP Transport layer and the communications link’s Tx path (e.g. to the UART driver). This function is called when any data should be sent to the serial interface. The data is stored in the link TxBuffer (see getTxBuffer), with its length specified as len. The linkContext should include link details and parameters as initialized by the implementation.
Both synchronous and asynchronous implementations of this function are supported. A synchronous implementation refers to one where send() is done with buf and len when it returns (i.e. the caller can free or reuse buf and len). An asynchronous implementation refers to one where send() returns before completely consuming buf and len (e.g. the send is completed at a later time). In this case, it is up to the platform implementation to call chppLinkSendDoneCb() after processing the contents of buf and len.
This function returns CHPP_LINK_ERROR_NONE_SENT if the platform implementation for this function is synchronous and CHPP_LINK_ERROR_NONE_QUEUED if it is implemented asynchronously. It can also return an error code from enum ChppLinkErrorCode.

## void chppLinkSendDoneCb(\*transportContext)

Notifies the transport layer that the link layer is done sending the previous payload (as provided to send()) and can accept more data.
On systems that implement the link layer Tx asynchronously, where send() returns False before consuming the payload provided to it, the platform implementation must call this function after send() is done with the payload (i.e. buf and len).

## void chppWorkThreadStart(\*transportContext)

Starts the main thread for CHPP's Transport Layer. This thread needs to be started after the Transport Layer is initialized through chppTransportInit() and provided with the transport layer context struct.

## void chppWorkThreadStop(\*transportContext)

Stops the main thread for CHPP's Transport Layer that has been started by calling chppWorkThreadStart(). Stopping this thread may be necessary for testing and debugging purposes. The parameter context provides the transport layer context struct.

# Application Layer Messaging

CHPP Application Layer datagrams typically have a 6-byte header. This is followed by zero or more bytes of application-layer payload. Both the header and payload are counted in the transport layer payload length. The transport layer 4-byte packet footer follows. Defined as a structure, the CHPP transport header looks like this:

- uint8_t handle;
- uint8_t type;
- uint8_t transaction;
- uint8_t reserved
- uint16_t command;

## Service Handle

The Service Handle provides the capability of running multiple simultaneous clients / services over a single CHPP transport (physical) link. This allows differentiation between traffic destined for different services / applications / sensors on a single link. It also allows both sides to have clients and services simultaneously if necessary. Handles include reserved ones (0x00 - 0x0f) and assignable ones. Assigning a handle is through a handshaking phase on the reserved handle number 0x01. The handshake allows for a wide range of manufacturer IDs, devices, and protocol types/versions to be mapped into an 8-bit handle number to achieve efficiency as well as flexibility.

### Handle

- 0x00: Non-handle based P2P communication
- 0x01: Loopback testing
- 0x02 - 0x0e: reserved
- 0x0f: CHPP service discovery handshake
- 0x10 - 0xff: Handles for services negotiated through handshake

## Message Type

The message type clarifies the direction of the messages (client <-> service) and whether a response is expected

- 0b00 (0): Request from client. Requires response with same command from service
- 0b01 (1): Response from service (with the same command / transaction ID as the request)
- 0b10 (2): Notification from client. Service shall not respond
- 0b11 (3): Notification from service. Client shall not respond

## Transaction ID

CHPP has an 8-bit transaction ID for out-of-order responses which is optional for clients, and mandatory for services. The Transaction ID allows CHPP to have multiple requests outstanding for not just different commands, but for a single command. To allow multiple outstanding requests, the service shall relay back the Transaction ID of a request in its response. The client shall not issue the same command unless it has either received the response or has changed / incremented the Transaction ID.

## Commands

The CHPP messaging structure allocates 16 bits for commands. CHPP Service Discovery uses the same messaging structure. Note that each vendor and service (as indicated by the Vendor ID and Service ID) can define their own commands and therefore command codes for different services and vendors can overlap.

# CHPP Basic Services

CHPP’s built-in basic services take advantage of reserved handle numbers, ranging from 0x00 - 0x0f to support debugging and development as well as discovery of other services. These basic services currently are as follows.

## Handleless Communication Service (0x00)

The handle 0x00 is reserved for use cases that do not need the flexibility provided by handles and avoid the need for discovery.

## Loopback Testing Service (0x01)

Loopback testing is a mandatory service where the loopback service relays the entire received datagram back to the client.
The client requesting the loopback must set the handle to 0x01, and the message type to 0 (i.e. request from client). The server responding with the loopback will respond on the same handle but with the message type to 1 (service response). Any (application layer) data following the handle and message type fields will be identically relayed back to the client.

## Discovery Service (0x0f)

Service Discovery is mapps a 8-bit handle to a number of coexisting services. In response to a discovery command, the discovery service responds with a list of available services (UUID + name + version) on the device. The first service in the list corresponds to handle 0x10, and so on.

# CHPP Predefined Service Protocols

CHPP supports the following services using the CHRE PAL. The standard UUIDs for these services are defined in common/standard_uuids.h.

- WWAN
- WiFi
- GNSS
