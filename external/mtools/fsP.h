#ifndef MTOOLS_FSP_H
#define MTOOLS_FSP_H

/*  Copyright 1996-1999,2001-2003,2008,2009 Alain Knaff.
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
#include "stream.h"
#include "msdos.h"
#include "fs.h"

typedef enum fatAccessMode_t {
	FAT_ACCESS_READ,
	FAT_ACCESS_WRITE
} fatAccessMode_t;

typedef struct Fs_t {
	struct Stream_t head;

	int serialized;
	unsigned long serial_number;
	uint8_t cluster_size;
	uint16_t sector_size;
	
	int fat_error;

	unsigned int (*fat_decode)(struct Fs_t *This, unsigned int num);
	void (*fat_encode)(struct Fs_t *This, unsigned int num,
			   unsigned int code);

	int fat_dirty;
	uint16_t fat_start;
	uint32_t fat_len;

	uint8_t num_fat;
	uint32_t end_fat;
	uint32_t last_fat;
	unsigned int fat_bits; /* When it ends up here, all negative
				  special values have been
				  eliminated */

	struct FatMap_t *FatMap;

	uint32_t dir_start;
	uint16_t dir_len;
	uint32_t clus_start;

	uint32_t num_clus;
	char drive; /* for error messages */

	/* fat 32 */
	uint32_t primaryFat;
	uint32_t writeAllFats;
	uint32_t rootCluster;
	uint32_t infoSectorLoc;
	uint16_t backupBoot;
	uint32_t last; /* last sector allocated, or MAX32 if unknown */
	uint32_t freeSpace; /* free space, or MAX32 if unknown */
	unsigned int preallocatedClusters;

	uint32_t lastFatSectorNr;
	unsigned char *lastFatSectorData;
	fatAccessMode_t lastFatAccessMode;
	unsigned int sectorMask;
	unsigned int sectorShift;

	doscp_t *cp;
} Fs_t;

#ifndef abs
#define abs(x) ((unsigned int)((x)>0?(x):-(x)))
#endif

mt_off_t sectorsToBytes(Fs_t *This, uint32_t off);
int fs_free(Stream_t *Stream);

void set_fat(Fs_t *This);
unsigned int get_next_free_cluster(Fs_t *Fs, unsigned int last);
unsigned int fatDecode(Fs_t *This, unsigned int pos);
void fatAppend(Fs_t *This, unsigned int pos, unsigned int newpos);
void fatDeallocate(Fs_t *This, unsigned int pos);
void fatAllocate(Fs_t *This, unsigned int pos, unsigned int value);
void fatEncode(Fs_t *This, unsigned int pos, unsigned int value);

int fat_read(Fs_t *This, union bootsector *boot, int nodups);
void fat_write(Fs_t *This);
int zero_fat(Fs_t *Fs, uint8_t media_descriptor);
extern Class_t FsClass;
int fsPreallocateClusters(Fs_t *Fs, uint32_t);
void fsReleasePreallocateClusters(Fs_t *Fs, uint32_t);
Fs_t *getFs(Stream_t *Stream);

int calc_fs_parameters(struct device *dev, bool fat32, uint32_t tot_sectors,
		       struct Fs_t *Fs, uint8_t *descr);

/* Fs_t *makeFsForFormat(void); */
void initFsForFormat(Fs_t *Fs);
void setFsSectorSize(Fs_t *Fs, struct device *dev, uint16_t msize);

uint32_t parseFsParams(	Fs_t *This,
			union bootsector *boot,
			int media,
			unsigned int cylinder_size);
uint32_t calc_clus_start(Fs_t *Fs);
int calc_num_clus(Fs_t *Fs, uint32_t tot_sectors);

#endif
