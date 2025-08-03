/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Simple program to try running an APF program against a packet.

#include <errno.h>
#include <getopt.h>
#include <inttypes.h>
#include <libgen.h>
#include <limits.h>
#include <pcap.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "disassembler.h"
#include "apf_interpreter.h"
#include "v5/apf_interpreter.h"
#include "v5/test_buf_allocator.h"

#define __unused __attribute__((unused))

// The following list must be in sync with
// https://cs.android.com/android/platform/superproject/main/+/main:packages/modules/NetworkStack/src/android/net/apf/ApfFilter.java;l=125
static const char* counter_name [] = {
    "RESERVED_OOB",
    "TOTAL_PACKETS",
    "PASSED_ARP",
    "PASSED_DHCP",
    "PASSED_IPV4",
    "PASSED_IPV6_NON_ICMP",
    "PASSED_IPV4_UNICAST",
    "PASSED_IPV6_ICMP",
    "PASSED_IPV6_UNICAST_NON_ICMP",
    "PASSED_ARP_NON_IPV4",
    "PASSED_ARP_UNKNOWN",
    "PASSED_ARP_UNICAST_REPLY",
    "PASSED_NON_IP_UNICAST",
    "PASSED_MDNS",
    "DROPPED_ETH_BROADCAST",
    "DROPPED_RA",
    "DROPPED_GARP_REPLY",
    "DROPPED_ARP_OTHER_HOST",
    "DROPPED_IPV4_L2_BROADCAST",
    "DROPPED_IPV4_BROADCAST_ADDR",
    "DROPPED_IPV4_BROADCAST_NET",
    "DROPPED_IPV4_MULTICAST",
    "DROPPED_IPV6_ROUTER_SOLICITATION",
    "DROPPED_IPV6_MULTICAST_NA",
    "DROPPED_IPV6_MULTICAST",
    "DROPPED_IPV6_MULTICAST_PING",
    "DROPPED_IPV6_NON_ICMP_MULTICAST",
    "DROPPED_802_3_FRAME",
    "DROPPED_ETHERTYPE_BLACKLISTED",
    "DROPPED_ARP_REPLY_SPA_NO_HOST",
    "DROPPED_IPV4_KEEPALIVE_ACK",
    "DROPPED_IPV6_KEEPALIVE_ACK",
    "DROPPED_IPV4_NATT_KEEPALIVE",
    "DROPPED_MDNS"
};

enum {
    OPT_PROGRAM,
    OPT_PACKET,
    OPT_PCAP,
    OPT_DATA,
    OPT_AGE,
    OPT_TRACE,
    OPT_V6,
};

const struct option long_options[] = {{"program", 1, NULL, OPT_PROGRAM},
                                      {"packet", 1, NULL, OPT_PACKET},
                                      {"pcap", 1, NULL, OPT_PCAP},
                                      {"data", 1, NULL, OPT_DATA},
                                      {"age", 1, NULL, OPT_AGE},
                                      {"trace", 0, NULL, OPT_TRACE},
                                      {"v6", 0, NULL, OPT_V6},
                                      {"help", 0, NULL, 'h'},
                                      {"cnt", 0, NULL, 'c'},
                                      {NULL, 0, NULL, 0}};

const int COUNTER_SIZE = 4;

// Parses hex in "input". Allocates and fills "*output" with parsed bytes.
// Returns length in bytes of "*output".
size_t parse_hex(const char* input, uint8_t** output) {
    int length = strlen(input);
    if (length & 1) {
        fprintf(stderr, "Argument not even number of characters: %s\n", input);
        exit(1);
    }
    length >>= 1;
    *output = malloc(length);
    if (*output == NULL) {
        fprintf(stderr, "Out of memory, tried to allocate %d\n", length);
        exit(1);
    }
    for (int i = 0; i < length; i++) {
        char byte[3] = { input[i*2], input[i*2+1], 0 };
        char* end_ptr;
        (*output)[i] = strtol(byte, &end_ptr, 16);
        if (end_ptr != byte + 2) {
            fprintf(stderr, "Failed to parse hex %s\n", byte);
            exit(1);
        }
    }
    return length;
}

void print_hex(const uint8_t* input, int len) {
    for (int i = 0; i < len; ++i) {
        printf("%02x", input[i]);
    }
}

uint32_t get_counter_value(const uint8_t* data, int data_len, int neg_offset) {
    if (neg_offset > -COUNTER_SIZE || neg_offset + data_len < 0) {
        return 0;
    }
    uint32_t value = 0;
    for (int i = 0; i < 4; ++i) {
        value = value << 8 | data[data_len + neg_offset];
        neg_offset++;
    }
    return value;
}

void print_counter(const uint8_t* data, int data_len) {
    int counter_len = sizeof(counter_name) / sizeof(counter_name[0]);
    for (int i = 0; i < counter_len; ++i) {
        uint32_t value = get_counter_value(data, data_len, -COUNTER_SIZE * i);
        if (value != 0) {
            printf("%s : %d \n", counter_name[i], value);
        }
    }
}

int tracing_enabled = 0;

void maybe_print_tracing_header() {
    if (!tracing_enabled) return;

    printf("      R0       R1       PC  Instruction\n");
    printf("-------------------------------------------------\n");

}

void print_transmitted_packet() {
    printf("transmitted packet: ");
    print_hex(apf_test_buffer, (int) apf_test_tx_packet_len);
    printf("\n");
}

// Process packet through APF filter
void packet_handler(int use_apf_v6_interpreter, uint8_t* program,
                    uint32_t program_len, uint32_t ram_len, const char* pkt, uint32_t filter_age) {
    uint8_t* packet;
    uint32_t packet_len = parse_hex(pkt, &packet);

    maybe_print_tracing_header();

    int ret;
    if (use_apf_v6_interpreter) {
        ret = apf_run(NULL, (uint32_t*)program, program_len, ram_len, packet, packet_len,
                            filter_age);
    } else {
        ret = accept_packet(program, program_len, ram_len, packet, packet_len,
                        filter_age);
    }
    printf("Packet %sed\n", ret ? "pass" : "dropp");

    free(packet);
}


void apf_trace_hook(uint32_t pc, const uint32_t* regs, const uint8_t* program, uint32_t program_len,
                    const uint8_t* packet __unused, uint32_t packet_len __unused,
                    const uint32_t* memory __unused, uint32_t memory_len __unused) {
    if (!tracing_enabled) return;

    printf("%8" PRIx32 " %8" PRIx32 " ", regs[0], regs[1]);
    printf("%s\n", apf_disassemble(program, program_len, &pc));
}

// Process pcap file through APF filter and generate output files
void file_handler(int use_apf_v6_interpreter, uint8_t* program,
                  uint32_t program_len, uint32_t ram_len, const char* filename,
                  uint32_t filter_age) {
    char errbuf[PCAP_ERRBUF_SIZE];
    pcap_t *pcap;
    struct pcap_pkthdr apf_header;
    const uint8_t* apf_packet;
    pcap_dumper_t *passed_dumper, *dropped_dumper;
    const char passed_file[] = "passed.pcap";
    const char dropped_file[] = "dropped.pcap";
    int pass = 0;
    int drop = 0;

    pcap = pcap_open_offline(filename, errbuf);
    if (pcap == NULL) {
        printf("Open pcap file failed.\n");
        exit(1);
    }

    passed_dumper = pcap_dump_open(pcap, passed_file);
    dropped_dumper = pcap_dump_open(pcap, dropped_file);

    if (!passed_dumper || !dropped_dumper) {
        printf("pcap_dump_open(): open output file failed.\n");
        pcap_close(pcap);
        exit(1);
    }

    while ((apf_packet = pcap_next(pcap, &apf_header)) != NULL) {
        maybe_print_tracing_header();

        int result;
        if (use_apf_v6_interpreter) {
            result = apf_run(NULL, (uint32_t*)program, program_len, ram_len, apf_packet,
                             apf_header.len, filter_age);
        } else {
            result = accept_packet(program, program_len, ram_len, apf_packet,
                                   apf_header.len, filter_age);
        }

        if (!result){
            drop++;
            pcap_dump((u_char*)dropped_dumper, &apf_header, apf_packet);
        } else {
            pass++;
            pcap_dump((u_char*)passed_dumper, &apf_header, apf_packet);
        }
    }

    printf("%d packets dropped\n", drop);
    printf("%d packets passed\n", pass);
    pcap_dump_close(passed_dumper);
    pcap_dump_close(dropped_dumper);
    pcap_close(pcap);
}

void print_usage(char* cmd) {
    fprintf(stderr,
            "Usage: %s --program <program> --pcap <file>|--packet <packet> "
            "[--data <content>] [--age <number>] [--trace]\n"
            "  --program    APF program, in hex.\n"
            "  --pcap       Pcap file to run through program.\n"
            "  --packet     Packet to run through program.\n"
            "  --data       Data memory contents, in hex.\n"
            "  --age        Age of program in seconds (default: 0).\n"
            "  --trace      Enable APF interpreter debug tracing\n"
            "  --v6         Use APF v6\n"
            "  -c, --cnt    Print the APF counters\n"
            "  -h, --help   Show this message.\n",
            basename(cmd));
}

int main(int argc, char* argv[]) {
    uint8_t* program = NULL;
    uint32_t program_len;
    const char* filename = NULL;
    char* packet = NULL;
    uint8_t* data = NULL;
    uint32_t data_len = 0;
    uint32_t filter_age = 0;
    int print_counter_enabled = 0;
    int use_apf_v6_interpreter = 0;

    int opt;
    char *endptr;

    while ((opt = getopt_long_only(argc, argv, "ch", long_options, NULL)) != -1) {
        switch (opt) {
            case OPT_PROGRAM:
                program_len = parse_hex(optarg, &program);
                break;
            case OPT_PACKET:
                if (!program) {
                    printf("<packet> requires <program> first\n\'%s -h or --help\' "
                           "for more information\n", basename(argv[0]));
                    exit(1);
                }
                if (filename) {
                    printf("Cannot use <file> with <packet> \n\'%s -h or --help\' "
                           "for more information\n", basename(argv[0]));

                    exit(1);
                }
                packet = optarg;
                break;
            case OPT_PCAP:
                if (!program) {
                    printf("<file> requires <program> first\n\'%s -h or --help\' "
                           "for more information\n", basename(argv[0]));

                    exit(1);
                }
                if (packet) {
                    printf("Cannot use <packet> with <file>\n\'%s -h or --help\' "
                           "for more information\n", basename(argv[0]));

                    exit(1);
                }
                filename = optarg;
                break;
            case OPT_DATA:
                data_len = parse_hex(optarg, &data);
                break;
            case OPT_AGE:
                errno = 0;
                filter_age = strtoul(optarg, &endptr, 10);
                if ((errno == ERANGE && filter_age == UINT32_MAX) ||
                    (errno != 0 && filter_age == 0)) {
                    perror("Error on age option: strtoul");
                    exit(1);
                }
                if (endptr == optarg) {
                    printf("No digit found in age.\n");
                    exit(1);
                }
                break;
            case OPT_TRACE:
                tracing_enabled = 1;
                break;
            case OPT_V6:
                use_apf_v6_interpreter = 1;
                break;
            case 'h':
                print_usage(argv[0]);
                exit(0);
                break;
            case 'c':
                print_counter_enabled = 1;
                break;
            default:
                print_usage(argv[0]);
                exit(1);
                break;
        }
    }

    if (!program) {
        printf("Must have APF program in option.\n");
        exit(1);
    }

    if (!filename && !packet) {
        printf("Missing file or packet after program.\n");
        exit(1);
    }

    // Combine the program and data into the unified APF buffer.
    if (data) {
        program = realloc(program, program_len + data_len);
        memcpy(program + program_len, data, data_len);
        free(data);
    }

    uint32_t ram_len = program_len + data_len;

    if (filename)
        file_handler(use_apf_v6_interpreter, program, program_len, ram_len,
                     filename, filter_age);
    else
        packet_handler(use_apf_v6_interpreter, program, program_len, ram_len,
                       packet, filter_age);

    if (data_len) {
        printf("Data: ");
        print_hex(program + program_len, data_len);
        printf("\n");
        if (print_counter_enabled) {
          printf("APF packet counters: \n");
          print_counter(program + program_len, data_len);
        }
    }

    if (use_apf_v6_interpreter && apf_test_tx_packet_len != 0) {
        print_transmitted_packet();
    }

    free(program);
    return 0;
}
