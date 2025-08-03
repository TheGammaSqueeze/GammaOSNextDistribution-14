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
 * I/O to a SCSI device
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
#include "llong.h"

#include "open_image.h"

#include "scsi.h"
#include "plain_io.h"
#include "scsi_io.h"

typedef struct ScsiDevice_t {
	struct Stream_t head;

	int fd;
	int privileged;

	uint32_t scsi_sector_size;
	mt_off_t device_size;
	uint32_t tot_sectors;
	void *extra_data; /* extra system dependent information for scsi.
			     On some platforms, filled in by scsi_open, and to
			     be supplied to scsi_cmd */
} ScsiDevice_t;

/* ZIP or other scsi device on Solaris or SunOS system.
   Since Sun won't accept a non-Sun label on a scsi disk, we must
   bypass Sun's disk interface and use low-level SCSI commands to read
   or write the ZIP drive.  We thus replace the file_read and file_write
   routines with our own scsi_read and scsi_write routines, that use the
   uscsi ioctl interface.  By James Dugal, jpd@usl.edu, 11-96.  Tested
   under Solaris 2.5 and SunOS 4.3.1_u1 using GCC.

   Note: the mtools.conf entry for a ZIP drive would look like this:
(solaris) drive C: file="/dev/rdsk/c0t5d0s2" partition=4  FAT=16 nodelay  exclusive scsi=1
(sunos) drive C: file="/dev/rsd5c" partition=4  FAT=16 nodelay  exclusive scsi=1

   Note 2: Sol 2.5 wants mtools to be suid-root, to use the ioctl.  SunOS is
   happy if we just have access to the device, so making mtools sgid to a
   group called, say, "ziprw" which has rw permission on /dev/rsd5c, is fine.
 */

static int scsi_init(ScsiDevice_t *This)
{
   int fd = This->fd;
   unsigned char cdb[10],buf[8];

   memset(cdb, 0, sizeof cdb);
   memset(buf,0, sizeof(buf));
   cdb[0]=SCSI_READ_CAPACITY;
   if (scsi_cmd(fd, (unsigned char *)cdb,
		sizeof(cdb), SCSI_IO_READ, buf,
		sizeof(buf), This->extra_data)==0)
   {
	   This->tot_sectors=
		   ((unsigned)buf[0]<<24)|
		   ((unsigned)buf[1]<<16)|
		   ((unsigned)buf[2]<<8)|
		   (unsigned)buf[3];
	   if(This->tot_sectors < UINT32_MAX)
		   This->tot_sectors++;

	   This->scsi_sector_size=
		   ((unsigned)buf[5]<<16)|
		   ((unsigned)buf[6]<<8)|
		   (unsigned)buf[7];
	   if (This->scsi_sector_size != 512)
		   fprintf(stderr,"  (scsi_sector_size=%d)\n",This->scsi_sector_size);
	   return 0;
   } else
	   return -1;
}


/**
 * Overflow-safe conversion of bytes to sectors
 */
static uint32_t bytesToSectors(size_t bytes, uint32_t sector_size) {
	size_t sectors = bytes / sector_size;
	if(bytes % sector_size)
		sectors++;
	if(sectors > UINT32_MAX)
		return UINT32_MAX;
	else
		return (uint32_t) sectors;
}

static ssize_t scsi_io(Stream_t *Stream, char *buf,
		       mt_off_t where, size_t len, scsi_io_mode_t rwcmd)
{
	unsigned int firstblock, nsect;
	uint8_t clen;
	int r;
	unsigned int max;
	uint32_t offset;
	unsigned char cdb[10];
	DeclareThis(ScsiDevice_t);

	firstblock=truncMtOffTo32u(where/(mt_off_t)This->scsi_sector_size);
	/* 512,1024,2048,... bytes/sector supported */
	offset=(smt_off_t) where % This->scsi_sector_size;
	nsect=bytesToSectors(offset+len, This->scsi_sector_size);
#if defined(OS_sun) && defined(OS_i386)
	if (This->scsi_sector_size>512)
		firstblock*=This->scsi_sector_size/512; /* work around a uscsi bug */
#endif /* sun && i386 */

	if (len>512) {
		/* avoid buffer overruns. The transfer MUST be smaller or
		* equal to the requested size! */
		while (nsect*This->scsi_sector_size>len)
			--nsect;
		if(!nsect) {
			fprintf(stderr,"Scsi buffer too small\n");
			exit(1);
		}
		if(rwcmd == SCSI_IO_WRITE && offset) {
			/* there seems to be no memmove before a write */
			fprintf(stderr,"Unaligned write\n");
			exit(1);
		}
		/* a better implementation should use bounce buffers.
		 * However, in normal operation no buffer overruns or
		 * unaligned writes should happen anyways, as the logical
		 * sector size is (hopefully!) equal to the physical one
		 */
	}


	max = scsi_max_length();

	if (nsect > max)
		nsect=max;

	/* set up SCSI READ/WRITE command */
	memset(cdb, 0, sizeof cdb);

	switch(rwcmd) {
		case SCSI_IO_READ:
			cdb[0] = SCSI_READ;
			break;
		case SCSI_IO_WRITE:
			cdb[0] = SCSI_WRITE;
			break;
	}

	cdb[1] = 0;

	if (firstblock > 0x1fffff || nsect > 0xff) {
		/* I suspect that the ZIP drive also understands Group 1
		 * commands. If that is indeed true, we may chose Group 1
		 * more aggressively in the future */

		cdb[0] |= SCSI_GROUP1;
		clen=10; /* SCSI Group 1 cmd */

		/* this is one of the rare case where explicit coding is
		 * more portable than macros... The meaning of scsi command
		 * bytes is standardised, whereas the preprocessor macros
		 * handling it might be not... */

		cdb[2] = (unsigned char) (firstblock >> 24) & 0xff;
		cdb[3] = (unsigned char) (firstblock >> 16) & 0xff;
		cdb[4] = (unsigned char) (firstblock >> 8) & 0xff;
		cdb[5] = (unsigned char) firstblock & 0xff;
		cdb[6] = 0;
		cdb[7] = (unsigned char) (nsect >> 8) & 0xff;
		cdb[8] = (unsigned char) nsect & 0xff;
		cdb[9] = 0;
	} else {
		clen = 6; /* SCSI Group 0 cmd */
		cdb[1] |= (unsigned char) ((firstblock >> 16) & 0x1f);
		cdb[2] = (unsigned char) ((firstblock >> 8) & 0xff);
		cdb[3] = (unsigned char) firstblock & 0xff;
		cdb[4] = (unsigned char) nsect;
		cdb[5] = 0;
	}

	if(This->privileged)
		reclaim_privs();

	r=scsi_cmd(This->fd, (unsigned char *)cdb, clen, rwcmd, buf,
		   nsect*This->scsi_sector_size, This->extra_data);

	if(This->privileged)
		drop_privs();

	if(r) {
		perror(rwcmd == SCSI_IO_READ ? "SCMD_READ" : "SCMD_WRITE");
		return -1;
	}
#ifdef JPD
	printf("finished %u for %u\n", firstblock, nsect);
#endif

#ifdef JPD
	printf("zip: read or write OK\n");
#endif
	if (offset>0)
		memmove(buf,buf+offset,	nsect*This->scsi_sector_size-offset);
	if (len==256) return 256;
	else if (len==512) return 512;
	else return (ssize_t)(nsect*This->scsi_sector_size-offset);
}

static ssize_t scsi_pread(Stream_t *Stream, char *buf,
			  mt_off_t where, size_t len)
{
#ifdef JPD
	printf("zip: to read %d bytes at %d\n", len, where);
#endif
	return scsi_io(Stream, buf, where, len, SCSI_IO_READ);
}

static ssize_t scsi_pwrite(Stream_t *Stream, char *buf,
			   mt_off_t where, size_t len)
{
#ifdef JPD
	Printf("zip: to write %d bytes at %d\n", len, where);
#endif
	return scsi_io(Stream, buf, where, len, SCSI_IO_WRITE);
}

static int scsi_get_data(Stream_t *Stream, time_t *date, mt_off_t *size,
			 int *type, uint32_t *address)
{
	DeclareThis(ScsiDevice_t);

	if(date || type || address)
		fprintf(stderr, "Get_data call not supported\n");
	if(size)
		*size = This->device_size;
	return 0;
}



static Class_t ScsiDeviceClass = {
	0,
	0,
	scsi_pread,
	scsi_pwrite,
	0,
	0,
	set_geom_noop,
	scsi_get_data, /* get_data */
	0, /* pre-allocate */
	0, /* dos-convert */
	0 /* discard */
};

Stream_t *OpenScsi(struct device *dev,
		   const char *name, int mode, char *errmsg,
		   int mode2, int locked, int lockMode,
		   mt_off_t *maxSize)
{
	int ret;
	ScsiDevice_t *This;
	if (!IS_SCSI(dev))
		return NULL;

	This = New(ScsiDevice_t);
	if (!This){
		printOom();
		return 0;
	}
	memset((void*)This, 0, sizeof(ScsiDevice_t));
	init_head(&This->head, &ScsiDeviceClass, NULL);
	This->scsi_sector_size = 512;

	if(dev) {
		if(!(mode2 & NO_PRIV))
			This->privileged = IS_PRIVILEGED(dev);
		mode |= dev->mode;
	}

	precmd(dev);
	if(IS_PRIVILEGED(dev) && !(mode2 & NO_PRIV))
		reclaim_privs();

	/* End of stuff copied from top of plain_io.c before actual open */

	This->fd = scsi_open(name, mode, IS_NOLOCK(dev)?0444:0666,
			     &This->extra_data);

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

	if(LockDevice(This->fd, dev, locked, lockMode, errmsg) < 0)
		goto exit_0;

	if(maxSize)
		*maxSize = MAX_OFF_T_B(31+log_2(This->scsi_sector_size));
	if(This->privileged)
		reclaim_privs();
	ret=scsi_init(This);
	if(This->privileged)
		drop_privs();
	if(ret < 0)
		goto exit_0;
	dev->tot_sectors = This->tot_sectors;
	return &This->head;
 exit_0:
	close(This->fd);
 exit_1:
	Free(This);
	return NULL;
}
