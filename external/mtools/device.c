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

#include "sysincludes.h"
#include "llong.h"
#include "device.h"

int check_if_sectors_fit(uint32_t tot_sectors,
			 mt_off_t maxBytes,
			 uint32_t sectorSize,
			 char *errmsg)
{
	if(!maxBytes)
		return 0; /* Maxbytes = 0 => no checking */
	if(tot_sectors > (smt_off_t) maxBytes / (smt_off_t) sectorSize) {
		sprintf(errmsg,
			"%d sectors too large for this platform\n",
			tot_sectors);
		return -1;
	}
	return 0;
}

/*
 * Calculate number of total sectors on device if needed, and check that
 * they fit into 
 */
int chs_to_totsectors(struct device *dev, char *errmsg)
{
	uint32_t sect_per_track, tot_sectors;
	
	if(dev->tot_sectors)
		return 0;
		
	if(!dev->heads || !dev->sectors || !dev->tracks)
		return 0; /* not fully specified => we cannot do
			     anything anyways */
		
	/* Cannot overflow as both dev->heads and dev->sectors are 16
	 * bit quantities, whose product will be put into a 32 bit
	 * field */
	sect_per_track = dev->heads * dev->sectors;

	if(dev->tracks > UINT32_MAX / sect_per_track) {
		/* Would not fit in 32 bits */

		if(errmsg)
			sprintf(errmsg,
				"Number of sectors larger than 2^32\n");
		return -1;
	}

	tot_sectors = dev->tracks * sect_per_track;
	if(tot_sectors > dev->hidden % sect_per_track)
		tot_sectors -= dev->hidden % sect_per_track;
	dev->tot_sectors = tot_sectors;
	return 0;
}
