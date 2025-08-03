/*  Copyright 1996,1997,1999,2001,2002,2008,2009 Alain Knaff.
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
#include "msdos.h"
#include "stream.h"

int batchmode = 0;

void limitSizeToOffT(size_t *len, mt_off_t maxLen)
{
#if SIZEOF_SIZE_T >= SIZEOF_MT_OFF_T
	if(*len > (size_t) maxLen)
#else
	if(*len > maxLen)
#endif
		*len = (size_t) maxLen;
}

void init_head(Stream_t *Stream, struct Class_t *Class, Stream_t *Next)
{
	Stream->Class = Class;
	Stream->refs = 1;
	Stream->Next = Next;
}

int flush_stream(Stream_t *Stream)
{
	int ret=0;
	if(!batchmode) {
		if(Stream->Class->flush)
			ret |= Stream->Class->flush(Stream);
		if(Stream->Next)
			ret |= flush_stream(Stream->Next);
	}
	return ret;
}

Stream_t *copy_stream(Stream_t *Stream)
{
	if(Stream)
		Stream->refs++;
	return Stream;
}

int free_stream(Stream_t **Stream)
{
	int ret=0;

	if(!*Stream)
		return -1;
	if(! --(*Stream)->refs){
		if((*Stream)->Class->flush)
			ret |= (*Stream)->Class->flush(*Stream);
		if((*Stream)->Class->freeFunc)
			ret |= (*Stream)->Class->freeFunc(*Stream);
		if((*Stream)->Next)
			ret |= free_stream(&(*Stream)->Next);
		Free(*Stream);
	}
	*Stream = NULL;
	return ret;
}


#define GET_DATA(stream, date, size, type, address) \
(stream)->Class->get_data( (stream), (date), (size), (type), (address) )

int set_geom_pass_through(Stream_t *Stream, device_t *dev, device_t *orig_dev)
{
	return SET_GEOM(Stream->Next, dev, orig_dev);
}

int set_geom_noop(Stream_t *Stream UNUSEDP,
		  device_t *dev UNUSEDP,
		  device_t *orig_dev UNUSEDP)
{
	return 0;
}

int get_data_pass_through(Stream_t *Stream, time_t *date, mt_off_t *size,
			  int *type, uint32_t *address)
{
       return GET_DATA(Stream->Next, date, size, type, address);
}

ssize_t pread_pass_through(Stream_t *Stream, char *buf,
			   mt_off_t start, size_t len)
{
	return PREADS(Stream->Next, buf, start, len);
}

ssize_t pwrite_pass_through(Stream_t *Stream, char *buf,
			    mt_off_t start, size_t len)
{
	return PWRITES(Stream->Next, buf, start, len);
}

doscp_t *get_dosConvert_pass_through(Stream_t *Stream)
{
	return GET_DOSCONVERT(Stream->Next);
}

/*
 * Adjust number of total sectors by given offset in bytes
 */
int adjust_tot_sectors(struct device *dev, mt_off_t offset, char *errmsg)
{
	if(!dev->tot_sectors)
		/* tot_sectors not set, do nothing */
		return 0;

	mt_off_t offs_sectors = offset /
		(dev->sector_size ? dev->sector_size : 512);
	if(offs_sectors > 0 && dev->tot_sectors < (smt_off_t) offs_sectors) {
		if(errmsg)
			sprintf(errmsg,"init: Offset bigger than base image");
		return -1;
	}
	dev->tot_sectors -= (uint32_t) offs_sectors;
	return 0;
}
