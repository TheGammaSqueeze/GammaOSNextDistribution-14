#pragma once

#include <features.h>
#include <stdint.h>
#include <sys/types.h>

#if defined(_GNU_SOURCE) || defined(_BSD_SOURCE)

struct tcphdr {
#ifdef _GNU_SOURCE
#ifdef __GNUC__
	__extension__
#endif
	union { struct {

	uint16_t source;
	uint16_t dest;
	uint32_t seq;
	uint32_t ack_seq;
#if __BYTE_ORDER == __LITTLE_ENDIAN
	uint16_t res1:4;
	uint16_t doff:4;
	uint16_t fin:1;
	uint16_t syn:1;
	uint16_t rst:1;
	uint16_t psh:1;
	uint16_t ack:1;
	uint16_t urg:1;
	uint16_t res2:2;
#else
	uint16_t doff:4;
	uint16_t res1:4;
	uint16_t res2:2;
	uint16_t urg:1;
	uint16_t ack:1;
	uint16_t psh:1;
	uint16_t rst:1;
	uint16_t syn:1;
	uint16_t fin:1;
#endif
	uint16_t window;
	uint16_t check;
	uint16_t urg_ptr;

	}; struct {
#endif

	uint16_t th_sport;
	uint16_t th_dport;
	uint32_t th_seq;
	uint32_t th_ack;
#if __BYTE_ORDER == __LITTLE_ENDIAN
	uint8_t th_x2:4;
	uint8_t th_off:4;
#else
	uint8_t th_off:4;
	uint8_t th_x2:4;
#endif
	uint8_t th_flags;
	uint16_t th_win;
	uint16_t th_sum;
	uint16_t th_urp;

#ifdef _GNU_SOURCE
	}; };
#endif
};

#endif
