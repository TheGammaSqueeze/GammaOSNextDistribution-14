#pragma once

#include <features.h>

#define __NEED_sa_family_t
#include <bits/alltypes.h>

struct sockaddr_storage {
	sa_family_t ss_family;
	char __ss_padding[128-sizeof(long)-sizeof(sa_family_t)];
	unsigned long __ss_align;
};
