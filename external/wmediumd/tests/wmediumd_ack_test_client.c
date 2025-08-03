#include <errno.h>
#include <getopt.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#include "wmediumd/api.h"

void print_help(int exit_code) {
  printf(
      "wmediumd_ack_test_client - test client for wmediumd crash that is "
      "related with ack\n\n");
  printf("Usage: wmediumd_ack_test_client -s PATH\n");
  printf("  Options:\n");
  printf("     - h : Print help\n");
  printf("     - s : Path for unix socket of wmediumd api server\n");

  exit(exit_code);
}

int write_fixed(int sock, void *data, int len) {
  int remain = len;
  int pos = 0;

  while (remain > 0) {
    int actual_written = write(sock, ((char *)data) + pos, remain);

    if (actual_written <= 0) {
      return actual_written;
    }

    remain -= actual_written;
    pos += actual_written;
  }

  return pos;
}

int read_fixed(int sock, void *data, int len) {
  int remain = len;
  int pos = 0;

  while (remain > 0) {
    int actual_read = read(sock, ((char *)data) + pos, remain);

    if (actual_read <= 0) {
      return actual_read;
    }

    remain -= actual_read;
    pos += actual_read;
  }

  return pos;
}

int wmediumd_send_packet(int sock, uint32_t type, void *data, uint32_t len) {
  struct wmediumd_message_header header;

  header.type = type;
  header.data_len = len;

  write_fixed(sock, &header, sizeof(uint32_t) * 2);

  if (len != 0) {
    write_fixed(sock, data, len);
  }

  return 0;
}

int wmediumd_read_packet(int sock) {
  struct wmediumd_message_header header;

  read_fixed(sock, &header, sizeof(uint32_t) * 2);

  if (header.data_len != 0) {
    char buf[4096];

    read_fixed(sock, buf, header.data_len);
  }

  return 0;
}

int main(int argc, char **argv) {
  int opt;
  char *wmediumd_api_server_path = NULL;

  while ((opt = getopt(argc, argv, "hs:")) != -1) {
    switch (opt) {
      case ':':
        fprintf(stderr,
                "error: Option `%c' "
                "needs a value\n\n",
                optopt);
        break;
      case 'h':
        print_help(0);
        break;
      case 's':
        if (wmediumd_api_server_path != NULL) {
          fprintf(stderr,
                  "error: You must provide just one option for `%c`\n\n",
                  optopt);
        }

        wmediumd_api_server_path = strdup(optarg);
        break;
      default:
        break;
    }
  }

  if (wmediumd_api_server_path == NULL) {
    fprintf(stderr, "error: must specify wmediumd api server path\n\n");
    print_help(-1);
  }

  int sock = socket(AF_UNIX, SOCK_STREAM, 0);

  struct sockaddr_un addr;

  addr.sun_family = AF_UNIX;

  if (strlen(wmediumd_api_server_path) >= sizeof(addr.sun_path)) {
    fprintf(stderr, "error: unix socket path is too long(maximum %zu)\n",
            sizeof(addr.sun_path) - 1);
    print_help(-1);
  }

  strncpy(addr.sun_path, wmediumd_api_server_path,
          strlen(wmediumd_api_server_path));

  if (connect(sock, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
    fprintf(stderr, "Cannot connect to %s\n", wmediumd_api_server_path);
    return -1;
  }

  struct wmediumd_message_control control_message;

  control_message.flags = WMEDIUMD_CTL_RX_ALL_FRAMES;

  wmediumd_send_packet(sock, WMEDIUMD_MSG_REGISTER, NULL, 0);
  wmediumd_read_packet(sock); /* Ack */
  wmediumd_send_packet(sock, WMEDIUMD_MSG_SET_CONTROL, &control_message,
                       sizeof(control_message));
  wmediumd_read_packet(sock); /* Ack */

  wmediumd_read_packet(sock);

  /* Send packet while receiving packet from wmediumd */
  wmediumd_send_packet(sock, WMEDIUMD_MSG_SET_CONTROL, &control_message,
                       sizeof(control_message));
  wmediumd_read_packet(sock);

  wmediumd_send_packet(sock, WMEDIUMD_MSG_ACK, NULL, 0);

  close(sock);

  free(wmediumd_api_server_path);

  return 0;
}
