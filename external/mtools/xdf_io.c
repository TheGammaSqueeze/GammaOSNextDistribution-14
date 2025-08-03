/*  Copyright 1994,1996-2003,2005,2007,2009 Alain Knaff.
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
 * Io to an xdf disk
 *
 * written by:
 *
 * Alain L. Knaff
 * alain@knaff.lu
 *
 */


#include "sysincludes.h"
#ifdef OS_linux
#include "msdos.h"
#include "mtools.h"
#include "devices.h"
#include "xdf_io.h"

/* Algorithms can't be patented */

typedef struct sector_map {
	unsigned int head:1;
	unsigned int size:7;
} sector_map_t;


static struct {
  unsigned char track_size;
  unsigned int track0_size:7;
  unsigned int rootskip:1;
  unsigned char rate;
  sector_map_t map[9];
} xdf_table[]= {
  {
    19, 16, 0, 0,
    {	{0,3},	{0,6},	{1,2},	{0,2},	{1,6},	{1,3},	{0,0} }
  },
  {
    23, 19, 0, 0,
    {	{0,3},	{0,4},	{1,6},	{0,2},	{1,2},	{0,6},	{1,4},	{1,3},	{0,0} }
  },
  {
    46, 37, 1, 0x43,
    {	{0,3},	{0,4},	{0,5},	{0,7},	{1,3},	{1,4},	{1,5},	{1,7},	{0,0} }
  },
  {
    24, 20, 1, 0,
    {	{0,5},	{1,6},	{0,6},	{1, 5} }
  },
  {
    48, 41, 1, 0,
    {	{0,6},	{1,7},	{0,7},	{1, 6} }
  }
};

#define NUMBER(x) (sizeof(x)/sizeof(x[0]))

typedef struct {
	unsigned char begin; /* where it begins */
	unsigned char end;
	unsigned char sector;
	unsigned char sizecode;

	unsigned int dirty:1;
	unsigned int phantom:2;
	unsigned int valid:1;
	unsigned int head:1;
} TrackMap_t;



typedef struct Xdf_t {
	struct Stream_t head;

	int fd;
	char *buffer;

	bool track_valid;
	uint8_t current_track;

	sector_map_t *map;

	uint32_t track_size;
	int track0_size;
	uint16_t sector_size;
	uint8_t FatSize;
	uint16_t RootDirSize;
	TrackMap_t *track_map;

	unsigned char last_sector;
	unsigned char rate;

	unsigned int stretch:1;
	unsigned int rootskip:1;
	signed  int drive:4;
} Xdf_t;

typedef struct {
	unsigned char head;
	unsigned char sector;
	unsigned char ptr;
} Compactify_t;


static int analyze_reply(RawRequest_t *raw_cmd, int do_print)
{
	int ret, bytes, newbytes;

	bytes = 0;
	while(1) {
		ret = analyze_one_reply(raw_cmd, &newbytes, do_print);
		bytes += newbytes;
		switch(ret) {
			case 0:
				return bytes;
			case 1:
				raw_cmd++;
				break;
			case -1:
				if(bytes)
					return bytes;
				else
					return 0;
		}
	}
}



static int send_cmd(int fd, RawRequest_t *raw_cmd, int nr,
		    const char *message, int retries)
{
	int j;
	int ret=-1;

	if(!nr)
		return 0;
	for (j=0; j< retries; j++){
		switch(send_one_cmd(fd, raw_cmd, message)) {
			case -1:
				return -1;
			case 1:
				j++;
				continue;
			case 0:
				break;
		}
		if((ret=analyze_reply(raw_cmd, j)) > 0)
			return ret; /* ok */
	}
	if(j > 1 && j == retries) {
		fprintf(stderr,"Too many errors, giving up\n");
		return 0;
	}
	return -1;
}



#define REC (This->track_map[ptr])
#define END(x) (This->track_map[(x)].end)
#define BEGIN(x) (This->track_map[(x)].begin)

static int add_to_request(Xdf_t *This, unsigned char ptr,
			  RawRequest_t *request, int *nr,
			  int direction, Compactify_t *compactify)
{
#if 0
	if(direction == MT_WRITE) {
		printf("writing %d: %u %d %d %d [%02x]\n",
		       ptr, This->current_track,
		       REC.head, REC.sector, REC.sizecode,
		       *(This->buffer + ptr * This->sector_size));
	} else
			printf(" load %d.%u\n", This->current_track, ptr);
#endif
	if(REC.phantom) {
		if(direction== MT_READ)
			memset(This->buffer + ptr * This->sector_size, 0,
			       128u << REC.sizecode);
		return 0;
	}

	if(*nr &&
	   RR_SIZECODE(request+(*nr)-1) == REC.sizecode &&
	   compactify->head == REC.head &&
	   compactify->ptr + 1 == ptr &&
	   compactify->sector +1 == REC.sector) {
		RR_SETSIZECODE(request+(*nr)-1, REC.sizecode);
	} else {
		if(*nr)
			RR_SETCONT(request+(*nr)-1);
		RR_INIT(request+(*nr));
		RR_SETDRIVE(request+(*nr), This->drive);
		RR_SETRATE(request+(*nr), This->rate);
		RR_SETTRACK(request+(*nr), This->current_track);
		RR_SETPTRACK(request+(*nr),
			     This->current_track << This->stretch);
		RR_SETHEAD(request+(*nr), REC.head);
		RR_SETSECTOR(request+(*nr), REC.sector);
		RR_SETSIZECODE(request+(*nr), REC.sizecode);
		RR_SETDIRECTION(request+(*nr), direction);
		RR_SETDATA(request+(*nr),
			   (caddr_t) This->buffer + ptr * This->sector_size);
		(*nr)++;
	}
	compactify->ptr = ptr;
	compactify->head = REC.head;
	compactify->sector = REC.sector;
	return 0;
}


static void add_to_request_if_invalid(Xdf_t *This, unsigned char ptr,
				     RawRequest_t *request, int *nr,
				     Compactify_t *compactify)
{
	if(!REC.valid)
		add_to_request(This, ptr, request, nr, MT_READ, compactify);

}


static void adjust_bounds(Xdf_t *This, uint32_t ibegin, uint32_t iend,
			  uint8_t *begin, uint8_t *end)
{
	/* translates begin and end from byte to sectors */
	*begin = (uint8_t) (ibegin / This->sector_size);
	*end = (uint8_t) ((iend + This->sector_size - 1) / This->sector_size);
}


static __inline__ int try_flush_dirty(Xdf_t *This)
{
	unsigned char ptr;
	int nr, bytes;
	RawRequest_t requests[100];
	Compactify_t compactify;

	if(!This->track_valid)
		return 0;

	nr = 0;
	for(ptr=0; ptr < This->last_sector; ptr=REC.end)
		if(REC.dirty)
			add_to_request(This, ptr,
				       requests, &nr,
				       MT_WRITE, &compactify);
#if 1
	bytes = send_cmd(This->fd,requests, nr, "writing", 4);
	if(bytes < 0)
		return bytes;
#else
	bytes = 0xffffff;
#endif
	for(ptr=0; ptr < This->last_sector; ptr=REC.end)
		if(REC.dirty) {
			if(bytes >= REC.end - REC.begin) {
				bytes -= REC.end - REC.begin;
				REC.dirty = 0;
			} else
				return 1;
		}
	return 0;
}



static int flush_dirty(Xdf_t *This)
{
	int ret;

	while((ret = try_flush_dirty(This))) {
		if(ret < 0)
			return ret;
	}
	return 0;
}


static ssize_t load_data(Xdf_t *This, uint32_t ibegin, uint32_t iend,
			 int retries)
{
	unsigned char ptr;
	int nr, bytes;
	RawRequest_t requests[100];
	Compactify_t compactify;
	unsigned char begin, end;
	adjust_bounds(This, ibegin, iend, &begin, &end);

	ptr = begin;
	nr = 0;
	for(ptr=REC.begin; ptr < end ; ptr = REC.end)
		add_to_request_if_invalid(This, ptr, requests, &nr,
					  &compactify);
	bytes = send_cmd(This->fd,requests, nr, "reading", retries);
	if(bytes < 0)
		return bytes;
	ptr = begin;
	for(ptr=REC.begin; ptr < end ; ptr = REC.end) {
		if(!REC.valid) {
			if(bytes >= REC.end - REC.begin) {
				bytes -= REC.end - REC.begin;
				REC.valid = 1;
			} else if(ptr > begin)
				return ptr * This->sector_size;
			else
				return -1;
		}
	}
	return end * This->sector_size;
}

static void mark_dirty(Xdf_t *This, uint32_t ibegin, uint32_t iend)
{
	int ptr;
	unsigned char begin, end;

	adjust_bounds(This, ibegin, iend, &begin, &end);

	ptr = begin;
	for(ptr=REC.begin; ptr < end ; ptr = REC.end) {
		REC.valid = 1;
		if(!REC.phantom)
			REC.dirty = 1;
	}
}


static ssize_t load_bounds(Xdf_t *This, uint32_t begin, uint32_t end)
{
	unsigned char lbegin, lend;

	adjust_bounds(This, begin, end, &lbegin, &lend);

	if(begin != BEGIN(lbegin) * This->sector_size &&
	   end != BEGIN(lend) * This->sector_size &&
	   lend < END(END(lbegin)))
		/* contiguous end & begin, load them in one go */
		return load_data(This, begin, end, 4);

	if(begin != BEGIN(lbegin) * This->sector_size) {
		ssize_t ret = load_data(This, begin, begin, 4);
		if(ret < 0)
			return ret;
	}

	if(end != BEGIN(lend) * This->sector_size) {
		ssize_t ret = load_data(This, end, end, 4);
		if(ret < 0)
			return BEGIN(lend) * This->sector_size;
	}
	return lend * This->sector_size;
}

/* Fill out a map that is just sufficient to read boot sector */
static void fill_boot(Xdf_t *This)
{
	uint8_t ptr=0;

	REC.head = 0;
	REC.sector = 129;
	REC.phantom = 0;

	REC.begin = ptr;
	REC.end = ptr+1;

	REC.sizecode = 2;

	REC.valid = 0;
	REC.dirty = 0;
	This->last_sector=1;
	This->current_track=0;
}


static uint8_t fill_t0(Xdf_t *This, uint8_t ptr, unsigned int size,
		       uint8_t *sector, uint8_t *head)
{
	unsigned int n;

	for(n = 0; n < size; ptr++,n++) {
		REC.head = *head;
		REC.sector = *sector + 129;
		REC.phantom = 0;
		(*sector)++;
		if(!*head && *sector >= This->track0_size - 8) {
			*sector = 0;
			*head = 1;
		}
	}
	return ptr;
}


static uint8_t fill_phantoms(Xdf_t *This, uint8_t ptr, uint8_t size)
{
	unsigned int n;

	for(n = 0; n < size; ptr++,n++)
		REC.phantom = 1;
	return ptr;
}

static int decompose(Xdf_t *This, mt_off_t iwhere, size_t len,
		     uint32_t *begin, uint32_t *end, uint8_t boot)
{
	uint8_t ptr;
	sector_map_t *map;
	uint8_t lbegin, lend;
	uint32_t track_size = This->track_size * 1024;

	smt_off_t track = (smt_off_t) iwhere / track_size;
	uint32_t where = (smt_off_t) iwhere % track_size;

	*begin = where;
	if(where + len > track_size)
		*end = track_size;
	else
		*end = (uint32_t) (where + len);

	if(This->current_track == track && !boot)
		/* already OK, return immediately */
		return 0;
	if(!boot)
		flush_dirty(This);
	if(track >= 80)
		return -1;
	This->current_track = (uint8_t) track;
	This->track_valid = true;

	if(track) {
		for(ptr=0, map=This->map; map->size; map++) {
			/* iterate through all sectors */
			lbegin = ptr;
			lend = ptr +
				(uint8_t) ((128u<<map->size)/This->sector_size);
			for( ; ptr < lend ; ptr++) {
				REC.begin = lbegin;
				REC.end = lend;

				REC.head = map->head;
				REC.sector = map->size + 128;
				REC.sizecode = map->size;

				REC.valid = 0;
				REC.dirty = 0;
				REC.phantom = 0;
			}
		}
		REC.begin = REC.end = ptr;
	} else {
		uint8_t sector, head;

		head = 0;
		sector = 0;

		for(ptr=boot; ptr < 2 * This->track_size; ptr++) {
			REC.begin = ptr;
			REC.end = ptr+1;

			REC.sizecode = 2;

			REC.valid = 0;
			REC.dirty = 0;
		}

		/* boot & 1st fat */
		ptr=fill_t0(This, 0, 1 + This->FatSize, &sector, &head);

		/* second fat */
		ptr=fill_phantoms(This, ptr, This->FatSize);

		/* root dir */
		ptr=fill_t0(This, ptr, This->RootDirSize, &sector, &head);

		/* "bad sectors" at the beginning of the fs */
		ptr=fill_phantoms(This, ptr, 5);

		if(This->rootskip)
			sector++;

		/* beginning of the file system */
		ptr = fill_t0(This, ptr,
			      (This->track_size - This->FatSize) * 2 -
			      This->RootDirSize - 6,
			      &sector, &head);
	}
	This->last_sector = ptr;
	return 0;
}


static ssize_t xdf_pread(Stream_t *Stream, char *buf,
			 mt_off_t where, size_t len)
{
	uint32_t begin, end;
	ssize_t ret;
	DeclareThis(Xdf_t);

	if(decompose(This, truncBytes32(where), len, &begin, &end, 0) < 0)
		/* Read beyond end of device */
		return 0;
	ret = load_data(This, begin, end, 4);
	if(ret < 0 || (size_t) ret < begin)
		return -1;
	maximize(len, (size_t) ret - begin);
	memcpy(buf, This->buffer + begin, len);
	return (ssize_t) (end - begin);
}

static ssize_t xdf_pwrite(Stream_t *Stream, char *buf,
			  mt_off_t where, size_t len)
{
	uint32_t begin, end;
	ssize_t len2;
	DeclareThis(Xdf_t);

	if(decompose(This, truncBytes32(where), len, &begin, &end, 0) < 0) {
		/* Write beyond end of device */
		errno = EFBIG;
		return -1;
	}

	len2 = load_bounds(This, begin, end);
	if(len2 < 0)
		return -1;
	maximize(end, (uint32_t)len2);
	len2 -= begin;
	maximize(len, (size_t) len2);
	memcpy(This->buffer + begin, buf, len);
	mark_dirty(This, begin, end);
	return (ssize_t) (end - begin);
}

static int xdf_flush(Stream_t *Stream)
{
	DeclareThis(Xdf_t);

	return flush_dirty(This);
}

static int xdf_free(Stream_t *Stream)
{
	DeclareThis(Xdf_t);
	Free(This->track_map);
	Free(This->buffer);
	return close(This->fd);
}


static int check_geom(Xdf_t *This, struct device *dev)
{
	unsigned int sect;

	if (!IS_MFORMAT_ONLY(dev)) {
	    if(compare(dev->sectors, 19) &&
	       compare(dev->sectors, 23) &&
	       compare(dev->sectors, 24) &&
	       compare(dev->sectors, 46) &&
	       compare(dev->sectors, 48))
		return 1;

	    /* check against contradictory info from configuration file */
	    if(compare(dev->heads, 2))
		return 1;
	}

	/* check against info from boot */
	if(This) {
		sect = This->track_size;
		if((sect != 19 && sect != 23 && sect != 24 &&
		    sect != 46 && sect != 48) ||
		   (!IS_MFORMAT_ONLY(dev) && compare(dev->sectors, sect)))
			return 1;
	}
	return 0;
}

static void set_geom(Xdf_t *This, struct device *dev)
{
	/* fill in config info to be returned to user */
	dev->heads = 2;
	dev->use_2m = 0xff;
	dev->sectors = (uint16_t) This->track_size;
	dev->tracks = 80;
}

static int config_geom(Stream_t *Stream UNUSEDP, struct device *dev,
		       struct device *orig_dev UNUSEDP)
{
	DeclareThis(Xdf_t);
	if(check_geom(This, dev))
		return 1;
	set_geom(This, dev);
	return 0;
}

static Class_t XdfClass = {
	0,
	0,
	xdf_pread,
	xdf_pwrite,
	xdf_flush,
	xdf_free,
	config_geom,
	0, /* get_data */
	0, /* pre-allocate */
	0, /* get_dosConvert */
	0 /* discard */
};

Stream_t *XdfOpen(struct device *dev, const char *name,
		  int mode, char *errmsg, struct xdf_info *info)
{
	Xdf_t *This;
	uint32_t begin, end;
	union bootsector *boot;
	unsigned int type;
	uint16_t fatSize;

	if(dev && ((!SHOULD_USE_XDF(dev) && !getenv("MTOOLS_USE_XDF")) ||
		   check_geom(NULL, dev)))
		return NULL;

	This = New(Xdf_t);
	if (!This)
		return NULL;
	init_head(&This->head, &XdfClass, NULL);

	This->sector_size = 512;
	This->stretch = 0;

	precmd(dev);
	This->fd = open(name,
			((mode | dev->mode) & ~O_ACCMODE) |
			O_EXCL | O_NDELAY | O_RDWR);
	if(This->fd < 0) {
#ifdef HAVE_SNPRINTF
		snprintf(errmsg,199,"xdf floppy: open: \"%s\"", strerror(errno));
#else
		sprintf(errmsg,"xdf floppy: open: \"%s\"", strerror(errno));
#endif
		goto exit_0;
	}
	closeExec(This->fd);

	This->drive = GET_DRIVE(This->fd);
	if(This->drive < 0)
		goto exit_1;

	/* allocate buffer */
	This->buffer = (char *) malloc(96 * 512);
	if (!This->buffer)
		goto exit_1;

	This->track_valid = false;
	This->track_map = (TrackMap_t *)
		calloc(96, sizeof(TrackMap_t));
	if(!This->track_map)
		goto exit_2;

	/* lock the device on writes */
	if (lock_dev(This->fd, mode == O_RDWR, dev)) {
#ifdef HAVE_SNPRINTF
		snprintf(errmsg,199,"xdf floppy: device \"%s\" busy:",
			dev->name);
#else
		sprintf(errmsg,"xdf floppy: device \"%s\" busy:",
			dev->name);
#endif
		goto exit_3;
	}

	/* Before reading the boot sector, assume dummy values suitable
	 * for reading just the boot sector */
	fill_boot(This);
	This->rate = 0;
	if (load_data(This, 0, 1, 4) < 0 ) {
		This->rate = 0x43;
		if(load_data(This, 0, 1, 4) < 0)
			goto exit_3;
	}

	boot = (union bootsector *) This->buffer;

	fatSize = WORD(fatlen);
	if(fatSize > UINT8_MAX) {
		fprintf(stderr, "Fat size %d too large\n", fatSize);
		exit(1);
	}
	This->FatSize = (uint8_t) fatSize;
	This->RootDirSize = WORD(dirents)/16;
	This->track_size = WORD(nsect);
	for(type=0; type < NUMBER(xdf_table); type++) {
		if(xdf_table[type].track_size == This->track_size) {
			This->map = xdf_table[type].map;
			This->track0_size = xdf_table[type].track0_size;
			This->rootskip = xdf_table[type].rootskip;
			This->rate = xdf_table[type].rate;
			break;
		}
	}
	if(type == NUMBER(xdf_table))
		goto exit_3;

	if(info) {
		info->RootDirSize = This->RootDirSize;
		info->FatSize = This->FatSize;
		info->BadSectors = 5;
	}
	decompose(This, 0, 512, &begin, &end, 1);

	if(dev)
		set_geom(This, dev);
	return &This->head;

exit_3:
	Free(This->track_map);
exit_2:
	Free(This->buffer);
exit_1:
	close(This->fd);
exit_0:
	Free(This);
	return NULL;
}

#endif

/* Algorithms can't be patented */
