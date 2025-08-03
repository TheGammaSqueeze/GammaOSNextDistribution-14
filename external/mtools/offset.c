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
 * filter to support filesystems stored at an offset into their image
 */

#include "sysincludes.h"
#include "msdos.h"
#include "mtools.h"
#include "offset.h"

typedef struct Offset_t {
	struct Stream_t head;

	mt_off_t offset;
} Offset_t;

static ssize_t offset_pread(Stream_t *Stream, char *buf,
			    mt_off_t start, size_t len)
{
	DeclareThis(Offset_t);
	return PREADS(This->head.Next, buf, start+This->offset, len);
}

static ssize_t offset_pwrite(Stream_t *Stream, char *buf,
			     mt_off_t start, size_t len)
{
	DeclareThis(Offset_t);
	return PWRITES(This->head.Next, buf, start+This->offset, len);
}

static Class_t OffsetClass = {
	0,
	0,
	offset_pread,
	offset_pwrite,
	0, /* flush */
	0, /* free */
	set_geom_pass_through, /* set_geom */
	0, /* get_data */
	0, /* pre-allocate */
	get_dosConvert_pass_through, /* dos convert */
	0, /* discard */
};

Stream_t *OpenOffset(Stream_t *Next, struct device *dev, off_t offset,
		     char *errmsg, mt_off_t *maxSize) {
	Offset_t *This;

	This = New(Offset_t);
	if (!This){
		printOom();
		return 0;
	}
	memset((void*)This, 0, sizeof(Offset_t));
	init_head(&This->head, &OffsetClass, Next);

	This->offset = offset;

	if(maxSize) {
		if(This->offset > *maxSize) {
			if(errmsg)
				sprintf(errmsg,"init: Big disks not supported");
			goto exit_0;
		}

		*maxSize -= This->offset;
	}

	if(adjust_tot_sectors(dev, This->offset, errmsg) < 0)
		goto exit_0;

	return &This->head;
 exit_0:
	Free(This);
	return NULL;
}
