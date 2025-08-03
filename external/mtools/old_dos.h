#ifndef MTOOLS_OLDDOS_H
#define MTOOLS_OLDDOS_H
/*  Copyright 2021 Alain Knaff.
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
 */

#include "device.h"

struct OldDos_t {
	unsigned int tracks;
	uint16_t sectors;
	uint16_t  heads;

	uint16_t dir_len;
	uint8_t cluster_size;
	uint32_t fat_len;

	uint8_t media;
};

extern struct OldDos_t *getOldDosBySize(size_t size);
extern struct OldDos_t *getOldDosByMedia(int media);
extern struct OldDos_t *getOldDosByParams(unsigned int tracks,
					  unsigned int heads,
					  unsigned int sectors,
					  unsigned int dir_len,
					  unsigned int cluster_size);
int setDeviceFromOldDos(int media, struct device *dev);

#endif
