/*  Copyright 1997,1998,2001-2003,2006,2009 Alain Knaff.
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

typedef struct hsc {
	unsigned char byte0;
	unsigned char head;		/* starting head */
	unsigned char sector;		/* starting sector */
	unsigned char cyl;		/* starting cylinder */
} hsc;

#define head(x) ((uint8_t)((x).head))
#define sector(x) ((uint8_t)((x).sector & 0x3f))
#define cyl(x) ((uint16_t)((x).cyl | (((x).sector & 0xc0)<<2)))

#define BEGIN(p) _DWORD((p)->start_sect)
#define END(p) (_DWORD((p)->start_sect)+(_DWORD((p)->nr_sects)))
#define PART_SIZE(p) (_DWORD((p)->nr_sects))


struct partition {
	hsc start;
	hsc end;
	unsigned char start_sect[4];	/* starting sector counting from 0 */
	unsigned char nr_sects[4];     	/* nr of sectors in partition */
};

#define boot_ind start.byte0
#define sys_ind end.byte0

int consistencyCheck(struct partition *partTable, int doprint, int verbose,
		     int *has_activated, uint32_t tot_sectors,
		     struct device *used_dev, unsigned int target_partition);

void setBeginEnd(struct partition *partTable,
		 uint32_t begin, uint32_t end,
		 uint16_t iheads, uint16_t isectors,
		 int activate, uint8_t type, unsigned int fat_bits);

Stream_t *OpenPartition(Stream_t *Next, struct device *dev,
			char *errmsg, mt_off_t *maxSize);

unsigned int findOverlap(struct partition *partTable, unsigned int until,
			 uint32_t start, uint32_t end);
