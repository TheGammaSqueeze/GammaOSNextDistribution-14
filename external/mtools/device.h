#ifndef MTOOLS_DEVICE_H
#define MTOOLS_DEVICE_H
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

/* Functions needed to work with struct device */

#include "llong.h"

/* Stuff related to particular device definitions are in devices.c
   (note the plural) */

#define SCSI_FLAG		0x001u
#define PRIV_FLAG		0x002u
#define NOLOCK_FLAG		0x004u
#define USE_XDF_FLAG		0x008u
#define MFORMAT_ONLY_FLAG	0x010u
#define VOLD_FLAG		0x020u
#define FLOPPYD_FLAG		0x040u
#define FILTER_FLAG		0x080u
#define SWAP_FLAG		0x100u

#define IS_SCSI(x)  ((x) && ((x)->misc_flags & SCSI_FLAG))
#define IS_PRIVILEGED(x) ((x) && ((x)->misc_flags & PRIV_FLAG))
#define IS_NOLOCK(x) ((x) && ((x)->misc_flags & NOLOCK_FLAG))
#define IS_MFORMAT_ONLY(x) ((x) && ((x)->misc_flags & MFORMAT_ONLY_FLAG))
#define SHOULD_USE_VOLD(x) ((x)&& ((x)->misc_flags & VOLD_FLAG))
#define SHOULD_USE_XDF(x) ((x)&& ((x)->misc_flags & USE_XDF_FLAG))
#define DO_SWAP(x)  ((x) && ((x)->misc_flags & SWAP_FLAG))

typedef struct device {
	const char *name;       /* full path to device */

	char drive;	   	/* the drive letter */
	int fat_bits;		/* FAT encoding scheme */

	int mode;		/* any special open() flags */
	unsigned int tracks;	/* tracks */
	uint16_t heads;		/* heads */
	uint16_t sectors;	/* sectors */
	unsigned int hidden;	/* number of hidden sectors. Used for
				 * mformatting partitioned devices */

	off_t offset;	       	/* skip this many bytes */

	unsigned int partition;

	unsigned int misc_flags;

	/* Linux only stuff */
	uint8_t ssize;
	unsigned int use_2m;

	char *precmd;		/* command to be executed before opening
				 * the drive */

	/* internal variables */
	int file_nr;		/* used during parsing */
	unsigned int blocksize;	/* size of disk block in bytes */

	unsigned int codepage;		/* codepage for shortname encoding */

	const char *data_map;

	uint32_t tot_sectors;	/* Amount of total sectors, more
				 * precise than tracks (in case of
				 * partitions which may take up parts
				 * of a track) */

	uint16_t sector_size; /* Non-default sector size */

	const char *cfg_filename; /* used for debugging purposes */
} device_t;

extern struct device *devices;
extern struct device const_devices[];
extern const unsigned int nr_const_devices;

int lock_dev(int fd, int mode, struct device *dev);

void precmd(struct device *dev);

int check_if_sectors_fit(uint32_t tot_sectors, mt_off_t maxBytes,
			 uint32_t sectorSize, char *errmsg);
int chs_to_totsectors(struct device *dev, char *errmsg);

#endif
