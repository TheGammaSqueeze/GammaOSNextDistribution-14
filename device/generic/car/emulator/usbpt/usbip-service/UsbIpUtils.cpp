#include "UsbIpUtils.h"

#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <log/log.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

constexpr uint16_t kIpVersion = 0x0111;

constexpr uint16_t kOpRequest = 0x8003;
constexpr uint16_t kOpResponse = 0x0003;

//  Fields associated with all USBIP packets.
typedef struct op_req_common {
    uint16_t ip_version;
    uint16_t command_code;
    uint32_t status;
} op_req_common;

typedef struct op_req_import {
    op_req_common common;
    uint32_t busid[8];
} op_req_import;

typedef struct op_rep_import {
    op_req_common common;
} op_rep_import;

// Metadata fields only valid for successful status flag.
typedef struct op_rep_import_metadata {
    uint32_t path[64];
    uint32_t bus_id[8];
    uint32_t bus_num;
    uint32_t dev_num;
    uint32_t speed;
    uint16_t id_vendor;
    uint16_t id_product;
    uint16_t bcd_device;
    uint8_t device_class;
    uint8_t device_subclass;
    uint8_t device_protocol;
    uint8_t configuration_value;
    uint8_t num_configurations;
    uint8_t num_interfaces;
} op_rep_import_success;

bool get_usbip_connection(const char *server, const char *port,
                          const char *dev_id, usbip_conn_info *info) {
    struct sockaddr_in address;

    // Setup socket to the server.
    if ((info->sock_fd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)) == 0) {
        ALOGE("Opening socket failed: %s\n", strerror(errno));
        return false;
    }

    int flags = 1;
    if (setsockopt(info->sock_fd, SOL_SOCKET, SO_KEEPALIVE, &flags,
                   sizeof(flags)) == -1) {
        ALOGE("Failed to enable keep alive: %s\n", strerror(errno));
        close(info->sock_fd);
        return false;
    }
    if (setsockopt(info->sock_fd, SOL_TCP, TCP_NODELAY, &flags,
                   sizeof(flags)) == -1) {
        ALOGE("Failed to enable no delay: %s\n", strerror(errno));
        close(info->sock_fd);
        return false;
    }

    address.sin_family = AF_INET;
    if (inet_aton(server, &address.sin_addr) == 0) {
        ALOGE("Failure to convert ip address %s\n", server);
        close(info->sock_fd);
        return false;
    }

    errno = 0;
    char *error;
    address.sin_port = strtol(port, &error, 10);
    if (address.sin_port == 0) {
        ALOGE("Port is invalid %s\n", port);
        close(info->sock_fd);
        return false;
    }
    address.sin_port = htons(address.sin_port);
    if (connect(info->sock_fd, (struct sockaddr *)&address, sizeof(address)) ==
        -1) {
        ALOGE("Connection failed: %s\n", strerror(errno));
        close(info->sock_fd);
        return false;
    }

    // Fill in op request
    op_req_import op_req;
    op_req.common.ip_version = htons(kIpVersion);
    op_req.common.command_code = htons(kOpRequest);
    op_req.common.status = 0x0000;
    strncpy((char *)op_req.busid, dev_id, sizeof(op_req.busid));

    if (send(info->sock_fd, &op_req, sizeof(op_req), 0) == -1) {
        ALOGE("Error sending op_req: %s\n", strerror(errno));
        close(info->sock_fd);
        return false;
    }

    // Read in op response.
    op_rep_import op_rep;
    if (recv(info->sock_fd, &op_rep, sizeof(op_rep), MSG_WAITALL) == -1) {
        ALOGE("Error receiving op_rep: %s\n", strerror(errno));
        close(info->sock_fd);
        return false;
    }

    if (op_rep.common.status != 0) {
        ALOGE("op_rep status is invalid.\n");
        close(info->sock_fd);
        return false;
    }

    uint16_t command = ntohs(op_rep.common.command_code);
    if (command != kOpResponse) {
        ALOGE("Invalid command expected: %d received: %d", command, kOpResponse);
        close(info->sock_fd);
        return false;
    }

    op_rep_import_success data;
    if (recv(info->sock_fd, &data, sizeof(data), MSG_WAITALL) == -1) {
        ALOGE("Error receiving op_rep_data: %s\n", strerror(errno));
        close(info->sock_fd);
        return false;
    }

    int temp_bus_num = ntohl(data.bus_num);
    int temp_dev_num = ntohl(data.dev_num);
    info->speed = ntohl(data.speed);
    info->dev_id = (temp_bus_num << 16) | temp_dev_num;
    return true;
}

int get_free_vhci_port(FILE *file, int speed) {
    // Throw away the header line.
    char *buf = NULL;
    size_t length = 0;
    if (getline(&buf, &length, file) == -1) {
        ALOGE("Couldn't get the header line: %s\n", strerror(errno));
        free(buf);
        return -1;
    }
    free(buf);

    char busid[32];
    char hub[3];
    int port = 0;
    int status = 0;
    int spd = 0;
    int dev = 0;
    int sockfd = 0;
    const char *expected_hub = (speed == USBIP_SPEED_SUPER) ? "ss" : "hs";

    // Scan status lines for a free USB port.
    while (fscanf(file, "%2s %d %d %d %x %u %31s\n", hub, &port, &status, &spd,
                  &dev, &sockfd, busid) != EOF) {
        if (strcmp(expected_hub, hub) == 0 && status == USBIP_VDEV_NULL) {
            return port;
        }
    }

    return -1;
}
