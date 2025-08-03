#include <stdio.h>

// Speed constants
inline constexpr int USBIP_SPEED_HIGH = 3;
inline constexpr int USBIP_SPEED_SUPER = 4;

// Status Constants
inline constexpr int USBIP_VDEV_NULL = 4;

// Connection information
typedef struct usbip_conn_info {
    int sock_fd;
    int speed;
    int dev_id;
} usbip_conn_info;

/*
 * Connects to server and retrieves required info for connection.
 * NOTE: User must close the sock_fd when they are done with it.
 */
bool get_usbip_connection(const char *server, const char *port,
                          const char *dev_id, usbip_conn_info *info);

/* Returns free port number from vhci, -1 on failure. */
int get_free_vhci_port(FILE *file, int speed);
