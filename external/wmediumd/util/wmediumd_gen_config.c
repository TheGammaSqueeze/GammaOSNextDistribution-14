#include <ctype.h>
#include <errno.h>
#include <getopt.h>
#include <libconfig.h>
#include <limits.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define MAC_ADDR_LEN 6
#define STR_MAC_ADDR_LEN 17

#define OPENWRT_MAC_ADDR_1 "42:00:00:00:00:00"
#define OPENWRT_MAC_ADDR_2 "42:00:00:00:01:00"

#define TX_POWER_DEFAULT 10

#define APPEND_LAST -1

#define DEFAULT_RADIO_COUNT 2
#define DEFAULT_CUTTLEFISH_INSTANCE_COUNT 64
#define DEFAULT_MAC_PREFIX 5554

#define PREVENT_MULTIPLE_OPTION(var, zero_val)                             \
  do {                                                                     \
    if ((var) != (zero_val)) {                                             \
      fprintf(stderr, "Error - cannot use option '%c' multiple times\n\n", \
              opt);                                                        \
      print_help(-1);                                                      \
    }                                                                      \
  } while (0)

// Adds MAC addresses for cuttlefish. Addresses will be 02:XX:XX:YY:YY:00
// where
//  - XX:XX prefix. enumerated from `mac_prefix`(default: 5554) to
//          `mac_prefix` + `instance_count`(default: 16) - 1
//  - YY:YY radio index. enumerated from 0 to `radios`(default: 2) - 1
int add_cuttlefish_mac_addresses(config_setting_t *ids, int mac_prefix,
                                 int instance_count, int radios) {
  for (int instance_num = 0; instance_num < instance_count; ++instance_num) {
    char iface_id[STR_MAC_ADDR_LEN + 1] = {
        0,
    };
    uint8_t mac[MAC_ADDR_LEN] = {
        0,
    };
    uint32_t instance_mac_prefix = mac_prefix + instance_num;

    mac[0] = 0x02;
    mac[1] = (instance_mac_prefix >> 8) & 0xff;
    mac[2] = instance_mac_prefix & 0xff;

    for (int radio_num = 0; radio_num < radios; ++radio_num) {
      mac[3] = (radio_num >> 8) & 0xff;
      mac[4] = radio_num;

      snprintf(iface_id, sizeof(iface_id), "%02x:%02x:%02x:%02x:%02x:%02x",
               mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);

      config_setting_set_string_elem(ids, APPEND_LAST, iface_id);
    }
  }

  return 0;
}

int add_cuttlefish_path_loss_model(config_setting_t *model,
                                   int instance_count) {
  config_setting_t *type =
      config_setting_add(model, "type", CONFIG_TYPE_STRING);
  config_setting_set_string(type, "path_loss");

  config_setting_t *model_name =
      config_setting_add(model, "model_name", CONFIG_TYPE_STRING);
  config_setting_set_string(model_name, "free_space");

  config_setting_t *positions =
      config_setting_add(model, "positions", CONFIG_TYPE_LIST);
  config_setting_t *tx_powers =
      config_setting_add(model, "tx_powers", CONFIG_TYPE_ARRAY);

  for (int idx = 0; idx < instance_count; ++idx) {
    config_setting_t *position =
        config_setting_add(positions, NULL, CONFIG_TYPE_LIST);
    config_setting_set_float_elem(position, APPEND_LAST, 0.0);
    config_setting_set_float_elem(position, APPEND_LAST, 0.0);

    config_setting_set_float_elem(tx_powers, APPEND_LAST, TX_POWER_DEFAULT);
  }

  return 0;
}

bool valid_mac_addr(const char *mac_addr) {
  if (strlen(mac_addr) != STR_MAC_ADDR_LEN) return false;

  if (mac_addr[2] != ':' || mac_addr[5] != ':' || mac_addr[8] != ':' ||
      mac_addr[11] != ':' || mac_addr[14] != ':') {
    return false;
  }

  for (int i = 0; i < STR_MAC_ADDR_LEN; ++i) {
    if ((i - 2) % 3 == 0) continue;
    char c = mac_addr[i];

    if (isupper(c)) {
      c = tolower(c);
    }

    if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) return false;
  }

  return true;
}

void print_help(int exit_code) {
  printf("wmediumd_gen_config - wmediumd config generator\n");
  printf(
      "wmediumd_gen_config [-h] [-n count] [-r count] [-p prefix] [-m "
      "MAC_ADDR] [-o "
      "PATH]\n");
  printf("  -h              print help and exit\n");
  printf(
      "  -n count        cuttlefish instance count for adding pre-defined mac "
      "address\n");
  printf(
      "  -r count        radio count of each cuttlefish instance (default: "
      "2)\n");
  printf(
      "  -p prefix       set prefix for cuttlefish mac address (default: "
      "5554)\n");
  printf(
      "                  second and third byte of mac address will be set to "
      "prefix\n");
  printf("                    ex) -p 5554    ex) -p 0x15b2\n");
  printf("  -m MAC_ADDR     add mac address as pre-defined mac address\n");
  printf("                    ex) -m 02:15:b2:00:00:00\n");
  printf(
      "  -o PATH         if specified, output result to file (default: "
      "stdout)\n");
  printf("\n");

  exit(exit_code);
}

int parse_count_option(const char *value, int opt) {
  char *parse_end_token;

  int result = strtol(value, &parse_end_token, 10);

  if ((result == LONG_MAX && errno == ERANGE) || optarg == parse_end_token ||
      result <= 0) {
    fprintf(stderr, "Error - Invalid count value '%s' at option '%c'\n\n",
            value, opt);
    return -1;
  }

  return result;
}

int parse_prefix_option(const char *value, int opt) {
  char *parse_end_token;
  int base = 10;

  if (strlen(value) >= 2 && value[0] == '0' && value[1] == 'x') {
    value += 2;
    base = 16;
  }

  int result = strtol(value, &parse_end_token, base);

  if ((result == LONG_MAX && errno == ERANGE) || optarg == parse_end_token ||
      result < 0) {
    fprintf(stderr, "Error - Invalid prefix value '%s' at option '%c'\n\n",
            value, opt);
    return -1;
  }

  if (result > 0xffff) {
    fprintf(
        stderr,
        "Error - Prefix value should not be greater than 0xffff(65535) \n\n");
    return -1;
  }

  return result;
}

int main(int argc, char **argv) {
  config_t cfg;

  config_init(&cfg);

  config_setting_t *root = config_root_setting(&cfg);
  config_setting_t *ifaces =
      config_setting_add(root, "ifaces", CONFIG_TYPE_GROUP);

  config_setting_t *count =
      config_setting_add(ifaces, "count", CONFIG_TYPE_INT);
  config_setting_t *ids = config_setting_add(ifaces, "ids", CONFIG_TYPE_ARRAY);

  config_setting_set_string_elem(ids, APPEND_LAST, OPENWRT_MAC_ADDR_1);
  config_setting_set_string_elem(ids, APPEND_LAST, OPENWRT_MAC_ADDR_2);

  FILE *output = stdout;
  char *out_path = NULL;
  int opt;
  int cuttlefish_instance_count = -1;
  int radio_count = -1;
  int mac_prefix = -1;

  while ((opt = getopt(argc, argv, "hn:p:r:m:o:")) != -1) {
    switch (opt) {
      case ':':
        fprintf(stderr, "Error - Option '%c' needs a value\n\n", optopt);
        print_help(-1);
        break;
      case 'h':
        print_help(0);
        break;
      case 'n':
        PREVENT_MULTIPLE_OPTION(cuttlefish_instance_count, -1);

        cuttlefish_instance_count = parse_count_option(optarg, opt);

        if (cuttlefish_instance_count < 0) {
          print_help(-1);
        }
        break;
      case 'p':
        PREVENT_MULTIPLE_OPTION(mac_prefix, -1);

        mac_prefix = parse_prefix_option(optarg, opt);

        if (mac_prefix < 0) {
          print_help(-1);
        }
        break;
      case 'r':
        PREVENT_MULTIPLE_OPTION(radio_count, -1);

        radio_count = parse_count_option(optarg, opt);

        if (radio_count < 0) {
          print_help(-1);
        }
        break;
      case 'm':
        if (!valid_mac_addr(optarg)) {
          fprintf(stderr, "Error - '%s' is not a valid mac address\n\n",
                  optarg);
          print_help(-1);
        }

        config_setting_set_string_elem(ids, APPEND_LAST, optarg);
        break;
      case 'o':
        PREVENT_MULTIPLE_OPTION(out_path, NULL);

        out_path = strdup(optarg);
        break;
      case '?':
        fprintf(stderr, "Error - Unknown option '%c'\n\n", optopt);
        print_help(-1);
        break;
    }
  }

  /* Use default values if not specified */

  if (radio_count == -1) {
    radio_count = DEFAULT_RADIO_COUNT;
  }

  if (cuttlefish_instance_count == -1) {
    cuttlefish_instance_count = DEFAULT_CUTTLEFISH_INSTANCE_COUNT;
  }

  if (mac_prefix == -1) {
    mac_prefix = DEFAULT_MAC_PREFIX;
  }

  if (add_cuttlefish_mac_addresses(ids, mac_prefix, cuttlefish_instance_count,
                                   radio_count) < 0) {
    fprintf(stderr, "Error - Failed to add cuttlefish mac address\n\n");
    print_help(-1);
  }

  config_setting_t *model =
      config_setting_add(root, "model", CONFIG_TYPE_GROUP);
  add_cuttlefish_path_loss_model(model, config_setting_length(ids));

  config_setting_set_int(count, config_setting_length(ids));

  if (out_path != NULL) {
    FILE *out_file = fopen(out_path, "w");

    if (out_file == NULL) {
      perror("fopen");
      fprintf(stderr, "Error - Cannot open '%s'\n\n", out_path);
      return -1;
    }

    output = out_file;
  }

  config_write(&cfg, output);

  if (out_path != NULL) {
    free(out_path);
  }

  config_destroy(&cfg);

  return 0;
}
