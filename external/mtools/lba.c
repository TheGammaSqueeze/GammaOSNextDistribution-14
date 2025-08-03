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
 *
 * "LBA Assist" geometry
 */

#include "sysincludes.h"
#include "mtools.h"
#include "lba.h"

int compute_lba_geom_from_tot_sectors(struct device *dev)
{
	unsigned int sect_per_track;
	uint32_t tracks;

	/* If already fully specified, nothing to do */
	if(dev->heads && dev->sectors && dev->tracks)
		return 0;

	/* If tot_sectors missing, return. Hopefully size still
	 * specified somewhere that will be read at a later stage
	 * (such as mformat command line) */
	if(dev->tot_sectors == 0) {
		return 0;
	}

	/* Floppy sizes, allowing for non-standard sizes with slightly
	   more sectors per track than the default */
	if(dev->tot_sectors &&
	   dev->tot_sectors <= 8640 && dev->tot_sectors % 40 == 0) {
		if(dev->tot_sectors <= 540) {
			/* double density 48tpi single sided */
			dev->tracks = 40;
			dev->heads = 1;
		} else if(dev->tot_sectors <= 1080) {
			/* double density 48tpi double sided or
			   96 tpi single sided   */

			if(dev->heads == 1)
				dev->tracks = 80;
			else {
				dev->tracks = 40;
				dev->heads = 2;
			}
		} else {
			/* double density 96tpi double sided,
			 * high density, extra density */
			dev->tracks = 80;
			dev->heads = 2;
		}
		dev->sectors =
			(uint16_t)(dev->tot_sectors / dev->heads / dev->tracks);
	}


	/* Heads or sectors not known => fill them in both... */
	if(!dev->sectors || !dev->heads) {
		dev->sectors = 63;

		if (dev->tot_sectors < 16u*dev->sectors*1024)
			dev->heads = 16;
		else if (dev->tot_sectors < 32u*dev->sectors*1024)
			dev->heads = 32;
		else if (dev->tot_sectors < 64u*dev->sectors*1024)
			dev->heads = 64;
		else if (dev->tot_sectors < 128u*dev->sectors*1024)
			dev->heads = 128;
		else
			dev->heads = 255;
	}

	/* ... and calculate corresponding tracks */
	if(!dev->tracks) {
		sect_per_track = dev->heads * dev->sectors;
		tracks = (dev->tot_sectors + sect_per_track - 1) /
			sect_per_track;
		dev->tracks = tracks;
	}

	return 0;
}
