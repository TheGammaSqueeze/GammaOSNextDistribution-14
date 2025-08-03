/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#pragma once

#include <stdint.h>
#include <virtio.h>

/*
 * VirtIO Console Feature Flags
 */
#define VIRTIO_CONSOLE_F_SIZE (1U << 0)
#define VIRTIO_CONSOLE_F_MULTIPORT (1U << 1)
#define VIRTIO_CONSOLE_F_EMERG_WRITE (1U << 2)

/**
 * enum virtio_console_event - Message types for VirtIO console control events.
 * @VIRTIO_CONSOLE_DEVICE_READY:  Guest->Host Acknowledge driver is ready to
 *                                speak control protocol
 * @VIRTIO_CONSOLE_DEVICE_ADD:    Host->Guest Sent when the host connects a
 *                                port
 * @VIRTIO_CONSOLE_DEVICE_REMOVE: Host->Guest Sent when the host disconnects a
 *                                port
 * @VIRTIO_CONSOLE_PORT_READY:    Guest->Host Acknowledge a DEVICE_ADD has been
 *                                processed by the driver
 * @VIRTIO_CONSOLE_CONSOLE_PORT:  Host->Guest label a port as being the primary
 *                                console (as opposed to a serial port)
 * @VIRTIO_CONSOLE_RESIZE:        Host->Guest one of the consoles has resized
 * @VIRTIO_CONSOLE_PORT_OPEN:     Bidirectional, whether the port is available
 *                                in "software". In theory, this is application
 *                                defined, but QEMU requires that we open the
 *                                port to begin receiving messages on it.
 * @VIRTIO_CONSOLE_PORT_NAME:     Host->Guest to indicate the name of a port.
 *                                The name is contained in the rest of the
 *                                virtio buffer after the event header.
 */
enum virtio_console_event {
    VIRTIO_CONSOLE_DEVICE_READY = 0,
    VIRTIO_CONSOLE_DEVICE_ADD = 1,
    VIRTIO_CONSOLE_DEVICE_REMOVE = 2,
    VIRTIO_CONSOLE_PORT_READY = 3,
    VIRTIO_CONSOLE_CONSOLE_PORT = 4,
    VIRTIO_CONSOLE_RESIZE = 5,
    VIRTIO_CONSOLE_PORT_OPEN = 6,
    VIRTIO_CONSOLE_PORT_NAME = 7,
};

/* Queue IDs for the control queues */
#define VIRTIO_CONSOLE_CTRL_RX 2
#define VIRTIO_CONSOLE_CTRL_TX 3

/* Offset from the base of a port for the RX/TX queues */
#define VIRTIO_CONSOLE_RX_OFFSET 0
#define VIRTIO_CONSOLE_TX_OFFSET 1

/* Maximum number of ports we support in multiport */
#define MAX_PORTS 16

/* Maximum name size we support for a port */
#define MAX_PORT_NAME_SIZE 64

/**
 * struct port - Bookkeeping for port state
 * @host_connected:  Whether the host enabled this port
 * @guest_connected: Whether we enabled this port
 * @name:            Port name (provided by host), optional. Will be an empty
 *                   string if unspecified.
 */
struct port {
    bool host_connected;
    bool guest_connected;
    char name[MAX_PORT_NAME_SIZE];
};

/**
 * struct virtio_console - A virtio serial/console bus
 * @vio:   VirtIO device being used
 * @ports: Current port state
 */
struct virtio_console {
    struct virtio_config* vio;
    struct port ports[MAX_PORTS];
};

/**
 * init_virtio_console() - Initializes a single VirtIO console device
 * Returns: The initialized console, or NULL in the event of a failure.
 *
 * We currently support a single legacy VirtIO console, in multiport
 * configuration. Any additional consoles will be ignored.
 *
 * Note that at VirtIO console device in multiport configuration may have
 * multiple attached serial ports, some of which are consoles.
 * We support driving any number of these, but they must stem from the same
 * VirtIO console.
 */
struct virtio_console* init_virtio_console(void);

/**
 * virtio_console_connect_port() - Hook up queues to a specific port.
 * @console: The console bus to hook up to.
 * @port_id: Which port to hook up to.
 * @vq_in:   Host->Guest virtq
 * @vq_out:  Guest->Host virtq
 */
void virtio_console_connect_port(struct virtio_console* console,
                                 size_t port_id,
                                 struct virtq* vq_in,
                                 struct virtq* vq_out);
