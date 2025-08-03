/*  Copyright 2018 Alain Knaff.
 *  This file is part of mtools.
 *
 *  Mtools is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Mtools is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Mtools.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
#include "sysincludes.h"
#include "mtools.h"

static long strtol_with_range(const char *nptr, char **endptr, int base,
			      long min, long max) {
    long l = strtol(nptr, endptr, base);
    if(l > max) {
	errno = ERANGE;
	return max;
    }
    if(l < min) {
	errno = ERANGE;
	return min;
    }
    return l;
}

static unsigned long strtoul_with_range(const char *nptr, char **endptr,
					int base, unsigned long max) {
    unsigned long l = strtoul(nptr, endptr, base);
    if(l > max) {
	errno = ERANGE;
	return max;
    }
    return l;
}

#ifndef HAVE_STRTOUI
unsigned int strtoui(const char *nptr, char **endptr, int base) {
    return (unsigned int) strtoul_with_range(nptr, endptr, base, UINT_MAX);
}
#endif

unsigned int atoui(const char *str) {
    return strtoui(str, 0, 0);
}

#ifndef HAVE_STRTOI
int strtoi(const char *nptr, char **endptr, int base) {
    return (int) strtol_with_range(nptr, endptr, base, INT_MIN, INT_MAX);
}
#endif

unsigned long atoul(const char *str) {
    return strtoul(str, 0, 0);
}

uint8_t strtou8(const char *nptr, char **endptr, int base) {
    return (uint8_t) strtoul_with_range(nptr, endptr, base, UINT8_MAX);
}

uint8_t atou8(const char *str) {
    return strtou8(str, 0, 0);
}

uint16_t strtou16(const char *nptr, char **endptr, int base) {
    return (uint16_t) strtoul_with_range(nptr, endptr, base, UINT16_MAX);
}

uint16_t atou16(const char *str) {
    return strtou16(str, 0, 0);
}

uint32_t strtou32(const char *nptr, char **endptr, int base) {
    return (uint32_t) strtoul_with_range(nptr, endptr, base, UINT32_MAX);
}

uint32_t atou32(const char *str) {
    return strtou32(str, 0, 0);
}

static void checkOverflow(uint32_t tot_sectors, int bits) {
	if(tot_sectors > UINT32_MAX >> bits) {
		fprintf(stderr, "Too many sectors\n");
		exit(1);
	}
}

uint32_t parseSize(char *sizeStr) {
	char *eptr;
	uint32_t tot_sectors = strtou32(sizeStr, &eptr, 10);
	if(eptr == sizeStr) {
		fprintf(stderr, "Bad size %s\n", sizeStr);
		exit(1);
	}
	switch(toupper(*eptr)) {
	case 'T':
		checkOverflow(tot_sectors, 10);
		tot_sectors *= 1024;
		/* FALL THROUGH */
	case 'G':
		checkOverflow(tot_sectors, 10);
		tot_sectors *= 1024;
		/* FALL THROUGH */
	case 'M':
		checkOverflow(tot_sectors, 10);
		tot_sectors *= 1024;
		/* FALL THROUGH */
	case 'K':
		checkOverflow(tot_sectors, 1);
		tot_sectors *= 2;
		eptr++;
		break;
	case '\0':
		/* By default, assume sectors */
		break;
	}
	if(*eptr) {
		fprintf(stderr, "Bad suffix %s\n", eptr);
		exit(1);
	}
	return tot_sectors;
}
