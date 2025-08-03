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
 * Remapping shim
 */

#include "sysincludes.h"
#include "msdos.h"
#include "mtools.h"
#include "remap.h"

enum map_type_t {
		 DATA,
		 ZERO,
		 SKIP,
		 POS
};

struct map {
	mt_off_t orig;
	mt_off_t remapped;
	enum map_type_t type;
};

typedef struct Remap_t {
	struct Stream_t head;

	struct map *map;
	int mapSize;

	mt_off_t net_offset;
} Remap_t;

static enum map_type_t remap(Remap_t *This, mt_off_t *start, size_t *len) {
	int i;
	for(i=0; i < This->mapSize - 1; i++)
		if(*start < This->map[i+1].remapped) {
			limitSizeToOffT(len, This->map[i+1].remapped - *start);
			break;
		}
	*start = *start - This->map[i].remapped + This->map[i].orig;
	return This->map[i].type;
}

static ssize_t remap_pread(Stream_t *Stream, char *buf,
			  mt_off_t start, size_t len)
{
	DeclareThis(Remap_t);
	if(remap(This, &start, &len)==DATA)
		return PREADS(This->head.Next, buf, start, len);
	else {
		memset(buf, 0, len);
		return (ssize_t) len;
	}
}

static ssize_t remap_pwrite(Stream_t *Stream, char *buf,
			   mt_off_t start, size_t len)
{
	DeclareThis(Remap_t);
	if(remap(This, &start, &len)==DATA)
		return PWRITES(This->head.Next, buf, start, len);
	else {
		unsigned int i;
		/* When writing to a "zero" sector, make sure that we
		   indeed only write zeroes back to there. Helps catch
		   putting filesystems with parameters unsuitable to
		   the particular mapping */
		for(i=0; i<len; i++) {
			if(buf[i]) {
				fprintf(stderr, "Bad data written to unmapped sectors\n");
				errno = EFAULT;
				return -1;
			}
		}
		return (ssize_t) len;
	}
}

static int remap_free(Stream_t *Stream)
{
	DeclareThis(Remap_t);
	if(This->map)
		free(This->map);
	return 0;
}

static Class_t RemapClass = {
	0,
	0,
	remap_pread,
	remap_pwrite,
	0, /* flush */
	remap_free, /* free */
	set_geom_pass_through, /* set_geom */
	0, /* get_data */
	0, /* pre-allocate */
	get_dosConvert_pass_through, /* dos convert */
	0, /* discard */
};

static int process_map(Remap_t *This, const char *ptr,
		       int countOnly, char *errmsg) {
	mt_off_t orig=0;
	mt_off_t remapped=0;
	int count=0;
	int atEnd=0;
	char *eptr;
	while(!atEnd) {
		mt_off_t len;
		enum map_type_t type;
		if(*ptr=='\0') {
			type=DATA;
			atEnd=1;
		} else if(!strncmp(ptr, "skip", 4)) {
			type=SKIP;
			ptr+=4;
		} else if(!strncmp(ptr, "zero", 4)) {
			type=ZERO;
			ptr+=4;
		} else if(!strncmp(ptr, "pos", 3)) {
			type=POS;
			ptr+=3;
		} else {
			type=DATA;
		}

		len=str_to_off_with_end(ptr,&eptr);
		ptr=eptr;
		switch(*ptr) {
		case '\0':
			/* End of string */
			break;
		case ',':
			/* Move on to next item */
			ptr++;
			break;
		default:
			sprintf(errmsg, "Bad number %s\n", ptr);
			return -1;
		}

		if(type == POS) {
			orig = len;
			continue;
		}
		if(type != SKIP) {
			if(!countOnly) {
				struct map *m = This->map+count;
				m->orig = orig;
				m->remapped = remapped;
				m->type = type;
			}
			remapped+=len;
			count++;
		}
		if(type != ZERO) {
			orig+=len;
		}

	}
	This->net_offset = orig-remapped;
	return count;
}


Stream_t *Remap(Stream_t *Next, struct device *dev, char *errmsg) {
	Remap_t *This;
	int nrItems=0;
	const char *map = dev->data_map;

	This = New(Remap_t);
	if (!This){
		printOom();
		return 0;
	}
	memset((void*)This, 0, sizeof(Remap_t));
	init_head(&This->head, &RemapClass, Next);

	/* First count number of items */
	nrItems=process_map(This, map, 1, errmsg);
	if(nrItems < 0) {
		free(This);
		return NULL;
	}

	This->map = calloc((size_t)nrItems, sizeof(struct map));
	if(!This->map) {
		printOom();
		goto exit_0;
	}

	process_map(This, map, 0, errmsg);

	if(adjust_tot_sectors(dev, This->net_offset, errmsg) < 0)
		goto exit_1;

	This->mapSize=nrItems;
	return &This->head;
 exit_1:
	free(This->map);
 exit_0:
	free(This);
	printOom();
	return NULL;
}
