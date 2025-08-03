/*  Copyright 1995-2007,2009,2011 Alain Knaff.
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
 * Io to a plain file or device
 *
 * written by:
 *
 * Alain L. Knaff
 * alain@knaff.lu
 *
 */

#include "sysincludes.h"
#include "stream.h"
#include "mtools.h"
#include "msdos.h"
#include "open_image.h"
#include "devices.h"
#include "plain_io.h"
#include "llong.h"

#ifdef HAVE_LINUX_FS_H
# include <linux/fs.h>
#endif

typedef struct SimpleFile_t {
    struct Stream_t head;

    struct MT_STAT statbuf;
    int fd;
    mt_off_t lastwhere;
    int seekable;
    int privileged;
#ifdef OS_hpux
    int size_limited;
#endif
} SimpleFile_t;


#include "lockdev.h"

typedef ssize_t (*iofn) (int, void *, size_t);

static ssize_t file_io(SimpleFile_t *This, char *buf,
		       mt_off_t where, size_t len,
		       iofn io)
{
	ssize_t ret;

	if (This->seekable && where != This->lastwhere ){
		if(mt_lseek( This->fd, where, SEEK_SET) < 0 ){
			perror("seek");
			return -1; /* If seek failed, lastwhere did
				      not change */
		}
		This->lastwhere = where;
	}

#ifdef OS_hpux
	/*
	 * On HP/UX, we can not write more than MAX_LEN bytes in one go.
	 * If more are written, the write fails with EINVAL
	 */
	#define MAX_SCSI_LEN (127*1024)
	if(This->size_limited && len > MAX_SCSI_LEN)
		len = MAX_SCSI_LEN;
#endif
	ret = io(This->fd, buf, len);

#ifdef OS_hpux
	if (ret == -1 &&
		errno == EINVAL && /* if we got EINVAL */
		len > MAX_SCSI_LEN) {
		This->size_limited = 1;
		len = MAX_SCSI_LEN;
		ret = io(This->fd, buf, len);
	}
#endif

	if ( ret == -1 ){
		perror("plain_io");
		return -1;
	}
	This->lastwhere = where + ret;
	return ret;
}

static ssize_t file_read(Stream_t *Stream, char *buf, size_t len)
{
	DeclareThis(SimpleFile_t);
	return file_io(This, buf, This->lastwhere, len, read);
}

static ssize_t file_write(Stream_t *Stream, char *buf, size_t len)
{
	DeclareThis(SimpleFile_t);
	return file_io(This, buf, This->lastwhere, len, (iofn) write);
}

static ssize_t file_pread(Stream_t *Stream, char *buf,
			  mt_off_t where, size_t len)
{
	DeclareThis(SimpleFile_t);
	return file_io(This, buf, where, len, read);
}

static ssize_t file_pwrite(Stream_t *Stream, char *buf,
			   mt_off_t where, size_t len)
{
	DeclareThis(SimpleFile_t);
	return file_io(This, buf, where, len, (iofn) write);
}

static int file_flush(Stream_t *Stream UNUSEDP)
{
#if 0
	DeclareThis(SimpleFile_t);

	return fsync(This->fd);
#endif
	return 0;
}

static int file_free(Stream_t *Stream)
{
	DeclareThis(SimpleFile_t);

	if (This->fd > 2)
		return close(This->fd);
	else
		return 0;
}

static int init_geom_with_reg(int fd, struct device *dev,
			      struct device *orig_dev,
			      struct MT_STAT *statbuf) {
	if(S_ISREG(statbuf->st_mode)) {
		/* Regular file (image file) */
		mt_off_t sectors;
		if(statbuf->st_size == 0) {
			/* zero sized image => newly created.
			   Size not actually known...
			*/
			return 0;
		}
		sectors = statbuf->st_size /
			(mt_off_t)(dev->sector_size ? dev->sector_size : 512);
		dev->tot_sectors =
			((smt_off_t) sectors > UINT32_MAX)
			? UINT32_MAX
			: (uint32_t) sectors;
		return 0;
	} else {
		/* All the rest (devices, etc.) */
		return init_geom(fd, dev, orig_dev, statbuf);
	}
}

static int file_geom(Stream_t *Stream, struct device *dev,
		     struct device *orig_dev)
{
	int ret;
	DeclareThis(SimpleFile_t);

	if(dev->sector_size && dev->sector_size != 512) {
		dev->sectors =
			(uint16_t) (dev->sectors * dev->sector_size / 512);
	}

#ifdef JPD
	printf("file_geom:media=%0X=>cyl=%d,heads=%d,sects=%d,ssize=%d,use2m=%X\n",
	       media, dev->tracks, dev->heads, dev->sectors, dev->ssize,
	       dev->use_2m);
#endif
	ret = init_geom_with_reg(This->fd,dev, orig_dev, &This->statbuf);
	if(dev->sector_size && dev->sector_size != 512) {
		dev->sectors =
			(uint16_t) (dev->sectors * 512 / dev->sector_size);
	}
#ifdef JPD
	printf("f_geom: after init_geom(), sects=%d\n", dev->sectors);
#endif
	return ret;
}


static int file_data(Stream_t *Stream, time_t *date, mt_off_t *size,
		     int *type, uint32_t *address)
{
	DeclareThis(SimpleFile_t);

	if(date)
		*date = This->statbuf.st_mtime;
	if(size)
		*size = This->statbuf.st_size;
	if(type)
		*type = S_ISDIR(This->statbuf.st_mode);
	if(address)
		*address = 0;
	return 0;
}

static int file_discard(Stream_t *Stream UNUSEDP)
{
#ifdef BLKFLSBUF
	int ret;
	DeclareThis(SimpleFile_t);
	ret= ioctl(This->fd, BLKFLSBUF);
	if(ret < 0)
		perror("BLKFLSBUF");
	return ret;
#else
	return 0;
#endif
}

static Class_t SimpleFileClass = {
	file_read,
	file_write,
	file_pread,
	file_pwrite,
	file_flush,
	file_free,
	file_geom,
	file_data,
	0, /* pre_allocate */
	0, /* dos-convert */
	file_discard
};


int LockDevice(int fd, struct device *dev,
	       int locked, int lockMode,
	       char *errmsg)
{
#ifndef __EMX__
#ifndef __CYGWIN__
#ifndef OS_mingw32msvc
	/* lock the device on writes */
	if (locked && lock_dev(fd, (lockMode&O_ACCMODE) == O_RDWR, dev)) {
		if(errmsg)
#ifdef HAVE_SNPRINTF
			snprintf(errmsg,199,
				"plain floppy: device \"%s\" busy (%s):",
				dev ? dev->name : "unknown", strerror(errno));
#else
			sprintf(errmsg,
				"plain floppy: device \"%s\" busy (%s):",
				(dev && strlen(dev->name) < 50) ?
				 dev->name : "unknown", strerror(errno));
#endif

		if(errno != EOPNOTSUPP || (lockMode&O_ACCMODE) == O_RDWR) {
			/* If error is "not supported", and we're only
			 * reading from the device anyways, then ignore. Some
			 * OS'es don't support locks on read-only devices, even
			 * if they are shared (read-only) locks */
			return -1;
		}
	}
#endif
#endif
#endif
	return 0;
}

Stream_t *SimpleFileOpen(struct device *dev, struct device *orig_dev,
			 const char *name, int mode, char *errmsg,
			 int mode2, int locked, mt_off_t *maxSize) {
	return SimpleFileOpenWithLm(dev, orig_dev, name, mode,
				    errmsg, mode2, locked, mode, maxSize,
				    NULL);
}

Stream_t *SimpleFileOpenWithLm(struct device *dev, struct device *orig_dev,
			       const char *name, int mode, char *errmsg,
			       int mode2, int locked, int lockMode,
			       mt_off_t *maxSize, int *geomFailure)
{
	SimpleFile_t *This;
#ifdef __EMX__
HFILE FileHandle;
ULONG Action;
APIRET rc;
#endif
	if (IS_SCSI(dev))
		return NULL;
	This = New(SimpleFile_t);
	if (!This){
		printOom();
		return 0;
	}
	memset((void*)This, 0, sizeof(SimpleFile_t));
	This->seekable = 1;
#ifdef OS_hpux
	This->size_limited = 0;
#endif
	init_head(&This->head, &SimpleFileClass, NULL);
	if (!name || strcmp(name,"-") == 0 ){
		if (mode == O_RDONLY)
			This->fd = 0;
		else
			This->fd = 1;
		This->seekable = 0;
		if (MT_FSTAT(This->fd, &This->statbuf) < 0) {
		    Free(This);
		    if(errmsg)
#ifdef HAVE_SNPRINTF
			snprintf(errmsg,199,"Can't stat -: %s",
				strerror(errno));
#else
			sprintf(errmsg,"Can't stat -: %s",
				strerror(errno));
#endif
		    return NULL;
		}

		return &This->head;
	}


	if(dev) {
		if(!(mode2 & NO_PRIV))
			This->privileged = IS_PRIVILEGED(dev);
		mode |= dev->mode;
	}

	precmd(dev);
	if(IS_PRIVILEGED(dev) && !(mode2 & NO_PRIV))
		reclaim_privs();

#ifdef __EMX__
#define DOSOPEN_FLAGS	(OPEN_FLAGS_DASD | OPEN_FLAGS_WRITE_THROUGH | \
			OPEN_FLAGS_NOINHERIT | OPEN_FLAGS_RANDOM | \
			OPEN_FLAGS_NO_CACHE)
#define DOSOPEN_FD_ACCESS (OPEN_SHARE_DENYREADWRITE | OPEN_ACCESS_READWRITE)
#define DOSOPEN_HD_ACCESS (OPEN_SHARE_DENYNONE | OPEN_ACCESS_READONLY)

	if (isalpha(*name) && (*(name+1) == ':')) {
		rc = DosOpen(
			name, &FileHandle, &Action, 0L, FILE_NORMAL,
			OPEN_ACTION_OPEN_IF_EXISTS, DOSOPEN_FLAGS |
			(IS_NOLOCK(dev)?DOSOPEN_HD_ACCESS:DOSOPEN_FD_ACCESS),
			0L);
#if DEBUG
		if (rc != NO_ERROR) fprintf (stderr, "DosOpen() returned %d\n", rc);
#endif
		if (!IS_NOLOCK(dev)) {
			rc = DosDevIOCtl(
			FileHandle, 0x08L, DSK_LOCKDRIVE, 0, 0, 0, 0, 0, 0);
#if DEBUG
			if (rc != NO_ERROR) fprintf (stderr, "DosDevIOCtl() returned %d\n", rc);
#endif
		}
		if (rc == NO_ERROR)
			This->fd = _imphandle(FileHandle); else This->fd = -1;
	} else
#endif
	    {
		    This->fd = open(name, mode | O_LARGEFILE | O_BINARY,
				    IS_NOLOCK(dev)?0444:0666);
	    }

	if(IS_PRIVILEGED(dev) && !(mode2 & NO_PRIV))
		drop_privs();

	if (This->fd < 0) {
		if(errmsg) {
#ifdef HAVE_SNPRINTF
			snprintf(errmsg, 199, "Can't open %s: %s",
				name, strerror(errno));
#else
			sprintf(errmsg, "Can't open %s: %s",
				name, strerror(errno));
#endif
		}
		goto exit_1;
	}

	if(IS_PRIVILEGED(dev) && !(mode2 & NO_PRIV))
		closeExec(This->fd);

#ifdef __EMX__
	if (*(name+1) != ':')
#endif
	if (MT_FSTAT(This->fd, &This->statbuf) < 0
#ifdef OS_mingw32msvc
	    && strncmp(name, "\\\\.\\", 4) != 0
#endif
	   ) {
		if(errmsg) {
#ifdef HAVE_SNPRINTF
			snprintf(errmsg,199,"Can't stat %s: %s",
				name, strerror(errno));
#else
			if(strlen(name) > 50) {
			    sprintf(errmsg,"Can't stat file: %s",
				    strerror(errno));
			} else {
			    sprintf(errmsg,"Can't stat %s: %s",
				name, strerror(errno));
			}
#endif
		}
		goto exit_0;
	}

	if(LockDevice(This->fd, dev, locked, lockMode, errmsg) < 0)
		goto exit_0;

	/* set default parameters, if needed */
	if (dev){
		errno=0;
		if (((!IS_MFORMAT_ONLY(dev) && dev->tracks) ||
		     mode2 & ALWAYS_GET_GEOMETRY) &&
		    init_geom_with_reg(This->fd, dev, orig_dev,
				       &This->statbuf)){
			if(geomFailure && (errno==EBADF || errno==EPERM)) {
				*geomFailure=1;
				return NULL;
			} else if(errmsg)
				sprintf(errmsg,"init: set default params");
			goto exit_0;
		}
	}

	if(maxSize)
		*maxSize = max_off_t_seek;

	This->lastwhere = 0;

	return &This->head;
 exit_0:
	close(This->fd);
 exit_1:
	Free(This);
	return NULL;
}

int get_fd(Stream_t *Stream)
{
	Class_t *clazz;
	DeclareThis(SimpleFile_t);
	clazz = This->head.Class;
	if(clazz != &SimpleFileClass)
	  return -1;
	else
	  return This->fd;
}
