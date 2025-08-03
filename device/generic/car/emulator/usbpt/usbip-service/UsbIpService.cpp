#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <log/log.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

#include "UsbIpUtils.h"

int main(int argc, char *argv[]) {
    if (argc != 4) {
        ALOGE("usage: 'usbip_service <ip_addr> <port> <dev_id>'\n");
        return -1;
    }
    ALOGD("IP: %s\nPort: %s\nDevId: %s\n", argv[1], argv[2], argv[3]);

    usbip_conn_info info;
    if (!get_usbip_connection(argv[1], argv[2], argv[3], &info)) {
        ALOGE("Couldn't retrieve socket connection\n");
        return -1;
    }

    // Get free port.
    FILE *file = fopen("/sys/devices/platform/vhci_hcd.0/status", "r");
    if (file == NULL) {
        ALOGE("Couldn't open sysfs status file: %s\n", strerror(errno));
        close(info.sock_fd);
        return -1;
    }
    int port_num = get_free_vhci_port(file, info.speed);

    // Pass socket to the kernel driver.
    int fd =
        openat(AT_FDCWD, "/sys/devices/platform/vhci_hcd.0/attach", O_WRONLY);
    if (fd == -1) {
        ALOGE("Couldn't open sysfs attach file: %s\n", strerror(errno));
        close(info.sock_fd);
        return -1;
    }
    if (dprintf(fd, "%d %d %d %d", port_num, info.sock_fd, info.dev_id,
                info.speed) < 0) {
        ALOGE("Failed to attach socket to VHCI.\n");
        close(info.sock_fd);
        close(fd);
        return -1;
    }

    close(info.sock_fd);
    close(fd);
    return 0;
}
