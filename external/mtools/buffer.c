/*  Copyright 1997,2001-2003 Alain Knaff.
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
 * Buffer read/write module
 */

#include "sysincludes.h"
#include "msdos.h"
#include "mtools.h"
#include "buffer.h"

typedef struct Buffer_t {
	struct Stream_t head;

	size_t size;     	/* size of read/write buffer */
	int dirty;	       	/* is the buffer dirty? */

	size_t sectorSize;	/* sector size: all operations happen
				 * in multiples of this */
	size_t cylinderSize;	/* cylinder size: preferred alignment,
				 * but for efficiency, less data may be read */
	int ever_dirty;	       	/* was the buffer ever dirty? */
	size_t dirty_pos;
	size_t dirty_end;
	mt_off_t current;	/* first sector in buffer */
	size_t cur_size;	/* the current size */
	char *buf;		/* disk read/write buffer */
} Buffer_t;

/* Convert position relative to buffer to absolute position */
static mt_off_t abs_pos(Buffer_t *Buffer, size_t rel) {
	return Buffer->current + (mt_off_t) rel;
}

/* End of currently valid buffer */
static mt_off_t cur_end(Buffer_t *Buffer) {
	return abs_pos(Buffer, Buffer->cur_size);
}

/* distance from absolute position until next full cylinder. If position already
 * *is* on a full cylinder boundary, return size of full cylinder */
static size_t pos_to_next_full_cyl(Buffer_t *Buffer, mt_off_t pos) {
	return Buffer->cylinderSize -
		(size_t) (pos % (mt_off_t) Buffer->cylinderSize);
}

/*
 * Flush a dirty buffer to disk.  Resets Buffer->dirty to zero.
 * All errors are fatal.
 */

static int _buf_flush(Buffer_t *Buffer)
{
	ssize_t ret;

#ifdef HAVE_ASSERT_H
	assert(Buffer->head.Next != NULL);
#endif
	
	if (!Buffer->dirty)
		return 0;
#ifdef DEBUG
	fprintf(stderr, "write %08x -- %02x %08x %08x\n",
		Buffer,
		(unsigned char) Buffer->buf[0],
		Buffer->current + Buffer->dirty_pos,
		Buffer->dirty_end - Buffer->dirty_pos);
#endif

	ret = force_pwrite(Buffer->head.Next,
			   Buffer->buf + Buffer->dirty_pos,
			   Buffer->current + (mt_off_t) Buffer->dirty_pos,
			   Buffer->dirty_end - Buffer->dirty_pos);
	if(ret < 0) {
		perror("buffer_flush: write");
		return -1;
	}
	
	if((size_t) ret != Buffer->dirty_end - Buffer->dirty_pos) {
		fprintf(stderr,"buffer_flush: short write\n");
		return -1;
	}
	Buffer->dirty = 0;
	Buffer->dirty_end = 0;
	Buffer->dirty_pos = 0;
	return 0;
}

static int invalidate_buffer(Buffer_t *Buffer, mt_off_t start)
{
	if(_buf_flush(Buffer) < 0)
		return -1;

	/* start reading at the beginning of start's sector
	 * don't start reading too early, or we might not even reach
	 * start */
	Buffer->current = ROUND_DOWN(start, (mt_off_t) Buffer->sectorSize);
	Buffer->cur_size = 0;
	return 0;
}

#undef OFFSET
#define OFFSET ((size_t)(start - This->current))

typedef enum position_t {
	OUTSIDE,
	APPEND,
	INSIDE,
	ERROR
} position_t;

static position_t isInBuffer(Buffer_t *This, mt_off_t start, size_t *len)
{
	if(start >= This->current && start < cur_end(This)) {
		maximize(*len, This->cur_size - OFFSET);
		return INSIDE;
	} else if(start == cur_end(This) &&
		  This->cur_size < This->size &&
		  *len >= This->sectorSize) {
		/* append to the buffer for this, three conditions have to
		 * be met:
		 *  1. The start falls exactly at the end of the currently
		 *     loaded data
		 *  2. There is still space
		 *  3. We append at least one sector
		 */
		maximize(*len, This->size - This->cur_size);
		*len = ROUND_DOWN(*len, This->sectorSize);
		return APPEND;
	} else {
		if(invalidate_buffer(This, start) < 0)
			return ERROR;
		maximize(*len, This->cylinderSize - OFFSET);
		maximize(*len, pos_to_next_full_cyl(This, This->current));
		return OUTSIDE;
	}
}

static ssize_t buf_pread(Stream_t *Stream, char *buf,
			 mt_off_t start, size_t len)
{
	size_t length;
	size_t offset;
	char *disk_ptr;
	ssize_t ret;
	DeclareThis(Buffer_t);

	if(!len)
		return 0;

	/*fprintf(stderr, "buf read %x   %x %x\n", Stream, start, len);*/
	switch(isInBuffer(This, start, &len)) {
		case OUTSIDE:
		case APPEND:
			/* always load until the end of the cylinder */
			length = pos_to_next_full_cyl(This, cur_end(This));
			maximize(length, This->size - This->cur_size);

			/* read it! */
			ret=PREADS(This->head.Next,
				   This->buf + This->cur_size,
				   This->current + (mt_off_t) This->cur_size,
				   length);
			if ( ret < 0 )
				return ret;
			This->cur_size += (size_t) ret;
			if (This->current+(mt_off_t)This->cur_size < start) {
				fprintf(stderr, "Short buffer fill\n");
				exit(1);
			}
			break;
		case INSIDE:
			/* nothing to do */
			break;
		case ERROR:
			return -1;
	}

	offset = OFFSET;
	disk_ptr = This->buf + offset;
	maximize(len, This->cur_size - offset);
	memcpy(buf, disk_ptr, len);
	return (ssize_t) len;
}

static ssize_t buf_pwrite(Stream_t *Stream, char *buf,
			  mt_off_t start, size_t len)
{
	char *disk_ptr;
	DeclareThis(Buffer_t);
	size_t offset=0;

	if(!len)
		return 0;

	This->ever_dirty = 1;

#ifdef DEBUG
	fprintf(stderr, "buf write %x   %02x %08x %08x -- %08x %08x -- %08x\n",
		Stream, (unsigned char) This->buf[0],
		start, len, This->current, This->cur_size, This->size);
	fprintf(stderr, "%d %d %d %x %x\n",
		start == This->current + This->cur_size,
		This->cur_size < This->size,
		len >= This->sectorSize, len, This->sectorSize);
#endif
	switch(isInBuffer(This, start, &len)) {
		case OUTSIDE:
#ifdef DEBUG
			fprintf(stderr, "outside\n");
#endif
			if(start % (mt_off_t) This->cylinderSize ||
			   len < This->sectorSize) {
				size_t readSize;
				ssize_t ret;
				size_t bytes_read;

				readSize = This->cylinderSize -
					(size_t)(This->current % (mt_off_t) This->cylinderSize);

				ret=PREADS(This->head.Next, This->buf,
					   (mt_off_t)This->current, readSize);
				/* read it! */
				if ( ret < 0 )
					return ret;
				bytes_read = (size_t) ret;
				if(bytes_read % This->sectorSize) {
				  fprintf(stderr, "Weird: read size (%zd) not a multiple of sector size (%d)\n", bytes_read, (int) This->sectorSize);
				    bytes_read -= bytes_read % This->sectorSize;
				    if(bytes_read == 0) {
					fprintf(stderr, "Nothing left\n");
					exit(1);
				    }
				}
				This->cur_size = bytes_read;
				/* for dosemu. Autoextend size */
				if(!This->cur_size) {
					memset(This->buf,0,readSize);
					This->cur_size = readSize;
				}
				offset = OFFSET;
				break;
			}
			/* FALL THROUGH */
		case APPEND:
#ifdef DEBUG
			fprintf(stderr, "append\n");
#endif
			len = ROUND_DOWN(len, This->sectorSize);
			offset = OFFSET;
			maximize(len, This->size - offset);
			This->cur_size += len;
			if(This->head.Next->Class->pre_allocate)
				PRE_ALLOCATE(This->head.Next, cur_end(This));
			break;
		case INSIDE:
			/* nothing to do */
#ifdef DEBUG
			fprintf(stderr, "inside\n");
#endif
			offset = OFFSET;
			maximize(len, This->cur_size - offset);
			break;
		case ERROR:
			return -1;
#ifdef DEBUG
		default:
			fprintf(stderr, "Should not happen\n");
			exit(1);
#endif
	}

	disk_ptr = This->buf + offset;

	/* extend if we write beyond end */
	if(offset + len > This->cur_size) {
		len -= (offset + len) % This->sectorSize;
		This->cur_size = len + offset;
	}

	memcpy(disk_ptr, buf, len);
	if(!This->dirty || offset < This->dirty_pos)
		This->dirty_pos = ROUND_DOWN(offset, This->sectorSize);
	if(!This->dirty || offset + len > This->dirty_end)
		This->dirty_end = ROUND_UP(offset + len, This->sectorSize);

	if(This->dirty_end > This->cur_size) {
		fprintf(stderr,
			"Internal error, dirty end too big dirty_end=%x cur_size=%x len=%x offset=%d sectorSize=%x\n",
			(unsigned int) This->dirty_end,
			(unsigned int) This->cur_size,
			(unsigned int) len,
			(int) offset, (int) This->sectorSize);
		fprintf(stderr, "offset + len + grain - 1 = %x\n",
				(int) (offset + len + This->sectorSize - 1));
		fprintf(stderr, "ROUNDOWN(offset + len + grain - 1) = %x\n",
				(int)ROUND_DOWN(offset + len + This->sectorSize - 1,
								This->sectorSize));
		fprintf(stderr, "This->dirty = %d\n", This->dirty);
		exit(1);
	}

	This->dirty = 1;
	return (ssize_t) len;
}

static int buf_flush(Stream_t *Stream)
{
	int ret;
	DeclareThis(Buffer_t);

	if (!This->ever_dirty)
		return 0;
	ret = _buf_flush(This);
	if(ret == 0)
		This->ever_dirty = 0;
	return ret;
}


static int buf_free(Stream_t *Stream)
{
	DeclareThis(Buffer_t);

	if(This->buf)
		free(This->buf);
	This->buf = 0;
	return 0;
}

static Class_t BufferClass = {
	0,
	0,
	buf_pread,
	buf_pwrite,
	buf_flush,
	buf_free,
	0, /* set_geom */
	get_data_pass_through, /* get_data */
	0, /* pre-allocate */
	get_dosConvert_pass_through, /* dos convert */
	0, /* discard */
};

Stream_t *buf_init(Stream_t *Next, size_t size,
		   size_t cylinderSize,
		   size_t sectorSize)
{
	Buffer_t *Buffer;

#ifdef HAVE_ASSERT_H
	assert(size != 0);
	assert(cylinderSize != 0);
	assert(sectorSize != 0);
	assert(Next != NULL);
#endif

	if(size % cylinderSize != 0) {
		fprintf(stderr, "size not multiple of cylinder size\n");
		exit(1);
	}
	if(cylinderSize % sectorSize != 0) {
		fprintf(stderr, "cylinder size not multiple of sector size\n");
		exit(1);
	}

	Buffer = New(Buffer_t);
	if(!Buffer)
		return 0;
	init_head(&Buffer->head, &BufferClass, Next);
	Buffer->buf = malloc(size);
	if ( !Buffer->buf){
		Free(Buffer);
		return 0;
	}
	Buffer->size = size;
	Buffer->dirty = 0;
	Buffer->cylinderSize = cylinderSize;
	Buffer->sectorSize = sectorSize;

	Buffer->ever_dirty = 0;
	Buffer->dirty_pos = 0;
	Buffer->dirty_end = 0;
	Buffer->current = 0L;
	Buffer->cur_size = 0; /* buffer currently empty */

	return &Buffer->head;
}

