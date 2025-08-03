/*  Copyright 1997-2003,2005-2007,2009 Alain Knaff.
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
 * mformat.c
 */
#define DONT_NEED_WAIT

#include "sysincludes.h"
#include "msdos.h"
#include "mtools.h"
#include "mainloop.h"
#include "fsP.h"
#include "file.h"
#include "plain_io.h"
#include "nameclash.h"
#include "buffer.h"
#include "partition.h"
#include "open_image.h"
#include "lba.h"

#ifdef OS_linux
#include "linux/hdreg.h"
#include "linux/fs.h"
#endif

static void set_offset(hsc *h, unsigned long offset,
		       uint16_t heads, uint16_t sectors)
{
	uint16_t head, sector;
	unsigned int cyl;

	if(! heads || !sectors)
		head = sector = cyl = 0; /* linear mode */
	else {
		sector = offset % sectors;
		offset = offset / sectors;

		head = offset % heads;
		offset = offset / heads;
		if(offset > 1023)
			cyl = 1023;
		else
			cyl = (uint16_t) offset;
	}
	if(head > UINT8_MAX) {
		/* sector or head out of range => linear mode */
		head = sector = cyl = 0;
	}
	h->head = (uint8_t) head;
	h->sector = ((sector+1) & 0x3f) | ((cyl & 0x300)>>2);
	h->cyl = cyl & 0xff;
}

void setBeginEnd(struct partition *partTable,
		 uint32_t begin, uint32_t end,
		 uint16_t iheads, uint16_t isectors,
		 int activate, uint8_t type, unsigned int fat_bits)
{
	uint8_t heads, sectors;

	if(iheads > UINT8_MAX) {
		fprintf(stderr,
			"Too many heads for partition: %d\n",
			iheads);
		exit(1);
	}
	heads=(uint8_t) iheads;
	if(isectors > UINT8_MAX) {
		fprintf(stderr,
			"Too many sectors for partition: %d\n",
			isectors);
		exit(1);
	}
	sectors=(uint8_t) isectors;

	set_offset(&partTable->start, begin, heads, sectors);
	set_offset(&partTable->end, end-1, heads, sectors);
	set_dword(partTable->start_sect, begin);
	set_dword(partTable->nr_sects, end-begin);
	if(activate)
		partTable->boot_ind = 0x80;
	else
		partTable->boot_ind = 0;
	if(!type) {
		if (fat_bits == 0) {
			/**
			 * Fat bits unknown / not specified. We look
			 * at size to get a rough estimate what FAT
			 * bits are used.  Note: this is only an
			 * estimate, the precise calculation would
			 * involve the number of clusters, which is
			 * not necessarily known here.
			 */
			/* cc977219 would have a cutoff number of 32680,
			 * corresponding to a FAT12 partition with 4K
			 * clusters, however other information hints that
			 * only partitions with less than 4096 sectors are
			 * considered */
			if(end-begin < 4096)
				fat_bits = 12;
			else
				fat_bits = 16;
		}

		/* Description of various partition types in
		 * https://en.wikipedia.org/wiki/Partition_type#List_of_partition_IDs
		 * and
		 * https://docs.microsoft.com/en-us/previous-versions/windows/it-pro/windows-2000-server/cc977219(v=technet.10)
		 */
		if (fat_bits == 32)
			/* FAT 32 partition. For now, we disregard the
			 * possibility of FAT 32 CHS partitions */
			type = 0x0C; /* Win95 FAT32, LBA */
		else if (end < 65536) {
			/* FAT 12 or FAT 16 partitions which fit entirely below
			   the 32M mark */
			/* The 32M restriction doesn't apply to logical
			   partitions within an extended partition, but for the
			   moment mpartition only makes primary partitions */
			if(fat_bits == 12)
				/* FAT 12 partition */
				type = 0x01; /* DOS FAT12, CHS */
			else if (fat_bits == 16)
				/* FAT 16 partition */
				type = 0x04; /* DOS FAT16, CHS */
		} else if (end <  sectors * heads * 1024u)
			/* FAT 12 or FAT16 partition above the 32M
			 * mark but below the 1024 cylinder mark.
			 * Indeed, there can be no CHS partition
			 * beyond 1024 cylinders */
			type = 0x06; /* DOS BIG FAT16 or FAT12, CHS */
		else
			type = 0x0E; /* Win95 BIG FAT16, LBA */
	}
	partTable->sys_ind = type;
}


/* setsize function.  Determines scsicam mapping if this cannot be inferred from
 * any existing partitions. Shamelessly snarfed from the Linux kernel ;-) */

/*
 * Function : static int setsize(unsigned long capacity,unsigned int *cyls,
 *	unsigned int *hds, unsigned int *secs);
 *
 * Purpose : to determine a near-optimal int 0x13 mapping for a
 *	SCSI disk in terms of lost space of size capacity, storing
 *	the results in *cyls, *hds, and *secs.
 *
 * Returns : -1 on failure, 0 on success.
 *
 * Extracted from
 *
 * WORKING                                                    X3T9.2
 * DRAFT                                                        792D
 *
 *
 *                                                        Revision 6
 *                                                         10-MAR-94
 * Information technology -
 * SCSI-2 Common access method
 * transport and SCSI interface module
 *
 * ANNEX A :
 *
 * setsize() converts a read capacity value to int 13h
 * head-cylinder-sector requirements. It minimizes the value for
 * number of heads and maximizes the number of cylinders. This
 * will support rather large disks before the number of heads
 * will not fit in 4 bits (or 6 bits). This algorithm also
 * minimizes the number of sectors that will be unused at the end
 * of the disk while allowing for very large disks to be
 * accommodated. This algorithm does not use physical geometry.
 */

static int setsize(unsigned long capacity,unsigned int *cyls,
		   uint16_t *hds,  uint16_t *secs) {
    int rv = 0;
    unsigned long heads, sectors, cylinders, temp;

    cylinders = 1024L;			/* Set number of cylinders to max */
    sectors = 62L;      		/* Maximize sectors per track */

    temp = cylinders * sectors;		/* Compute divisor for heads */
    heads = capacity / temp;		/* Compute value for number of heads */
    if (capacity % temp) {		/* If no remainder, done! */
    	heads++;                	/* Else, increment number of heads */
    	temp = cylinders * heads;	/* Compute divisor for sectors */
    	sectors = capacity / temp;	/* Compute value for sectors per
					       track */
    	if (capacity % temp) {		/* If no remainder, done! */
      	    sectors++;                  /* Else, increment number of sectors */
      	    temp = heads * sectors;	/* Compute divisor for cylinders */
      	    cylinders = capacity / temp;/* Compute number of cylinders */
      	}
    }
    if (cylinders == 0) rv=-1;/* Give error if 0 cylinders */

    *cyls = (unsigned int) cylinders;	/* Stuff return values */
    *secs = (uint16_t) sectors;
    *hds  = (uint16_t) heads;
    return(rv);
}

static void setsize0(uint32_t capacity,unsigned int *cyls,
		     uint16_t *hds, uint16_t *secs)
{
	int r;

	/* 1. First try "Megabyte" sizes */
	if(capacity < 1024 * 2048 && !(capacity % 1024)) {
		*cyls = capacity >> 11;
		*hds  = 64;
		*secs = 32;
		return;
	}

	/* then try scsicam's size */
	r = setsize(capacity,cyls,hds,secs);
	if(r || *hds > 255 || *secs > 63) {
		/* scsicam failed. Do megabytes anyways */
		*cyls = capacity >> 11;
		*hds  = 64;
		*secs = 32;
		return;
	}
}


static void usage(int ret) NORETURN;
static void usage(int ret)
{
	fprintf(stderr,
		"Mtools version %s, dated %s\n", mversion, mdate);
	fprintf(stderr,
		"Usage: %s [-pradcv] [-I] [-B bootsect-template] [-s sectors] "
			"[-t cylinders] "
		"[-h heads] [-T type] [-b begin] [-l length] "
		"drive\n", progname);
	exit(ret);
}

void mpartition(int argc, char **argv, int dummy UNUSEDP) NORETURN;
void mpartition(int argc, char **argv, int dummy UNUSEDP)
{
	Stream_t *Stream;
	unsigned int dummy2;

	unsigned int i;

	uint16_t sec_per_cyl;
	int doprint = 0;
	int verbose = 0;
	int create = 0;
	int force = 0;
	unsigned int length = 0;
	int do_remove = 0;
	int initialize = 0;

	uint32_t tot_sectors=0;
	/* Needs to be long due to BLKGETSIZE ioctl */

	uint8_t type = 0;
	int begin_set = 0;
	int size_set = 0;
	int end_set = 0;
	int activate = 0;
	int has_activated = 0;
	int inconsistency=0;
	unsigned int begin=0;
	unsigned int end=0;
	int dirty = 0;
	int open2flags = 0;

	int c;
	struct device used_dev;
	unsigned int argtracks;
	uint16_t argheads, argsectors;

	char drive, name[EXPAND_BUF];
	unsigned char buf[512];
	struct partition *partTable=(struct partition *)(buf+ 0x1ae);
	struct device *dev;
	char errmsg[2100];
	char *bootSector=0;
	struct partition *tpartition;

	argtracks = 0;
	argheads = 0;
	argsectors = 0;

	/* get command line options */
	if(helpFlag(argc, argv))
		usage(0);
	while ((c = getopt(argc, argv, "i:adprcIT:t:h:s:fvpb:l:S:B:")) != EOF) {
		char *endptr=NULL;
		errno=0;
		switch (c) {
			case 'i':
				set_cmd_line_image(optarg);
				break;
			case 'B':
				bootSector = optarg;
				break;
			case 'a':
				/* no privs, as it could be abused to
				 * make other partitions unbootable, or
				 * to boot a rogue kernel from this one */
				open2flags |= NO_PRIV;
				activate = 1;
				dirty = 1;
				break;
			case 'd':
				activate = -1;
				dirty = 1;
				break;
			case 'p':
				doprint = 1;
				break;
			case 'r':
				do_remove = 1;
				dirty = 1;
				break;
			case 'I':
				/* could be abused to nuke all other
				 * partitions */
				open2flags |= NO_PRIV;
				initialize = 1;
				dirty = 1;
				break;
			case 'c':
				create = 1;
				dirty = 1;
				break;

			case 'T':
				/* could be abused to "manually" create
				 * extended partitions */
				open2flags |= NO_PRIV;
				type = strtou8(optarg, &endptr, 0);
				break;

			case 't':
				argtracks = atoui(optarg);
				break;
			case 'h':
				argheads = atou16(optarg);
				break;
			case 's':
				argsectors = atou16(optarg);
				break;

			case 'f':
				/* could be abused by creating overlapping
				 * partitions and other such Snafu */
				open2flags |= NO_PRIV;
				force = 1;
				break;

			case 'v':
				verbose++;
				break;
			case 'b':
				begin_set = 1;
				begin = strtoui(optarg, &endptr, 0);
				break;
			case 'l':
				size_set = 1;
				length = parseSize(optarg);
				break;

			default:
				usage(1);
		}
		check_number_parse_errno((char)c, optarg, endptr);
	}

	if (argc - optind != 1 ||
	    !argv[optind][0] || argv[optind][1] != ':')
		usage(1);

	drive = ch_toupper(argv[optind][0]);

	/* check out a drive whose letter and parameters match */
	sprintf(errmsg, "Drive '%c:' not supported", drive);
	Stream = 0;
	for(dev=devices;dev->drive;dev++) {
		int mode ;

		FREE(&(Stream));
		/* drive letter */
		if (dev->drive != drive)
			continue;
		if (dev->partition < 1 || dev->partition > 4) {
			sprintf(errmsg,
				"Drive '%c:' is not a partition",
				drive);
			continue;
		}
		used_dev = *dev;

		SET_INT(used_dev.tracks, argtracks);
		SET_INT(used_dev.heads, argheads);
		SET_INT(used_dev.sectors, argsectors);

		expand(dev->name, name);

		mode = dirty ? O_RDWR : O_RDONLY;
		if(initialize)
 			mode |= O_CREAT;

#ifdef USING_NEW_VOLD
		strcpy(name, getVoldName(dev, name));
#endif
		Stream = OpenImage(&used_dev, dev, name, mode, errmsg,
				   open2flags | SKIP_PARTITION | ALWAYS_GET_GEOMETRY,
				   mode, NULL, NULL, NULL);

		if (!Stream) {
#ifdef HAVE_SNPRINTF
			snprintf(errmsg,sizeof(errmsg)-1,
				 "init: open: %s", strerror(errno));
#else
			sprintf(errmsg,"init: open: %s", strerror(errno));
#endif
			continue;
		}

		tot_sectors = used_dev.tot_sectors;

		/* read the partition table */
		if (PREADS(Stream, (char *) buf, 0, 512) != 512 && !initialize){
#ifdef HAVE_SNPRINTF
			snprintf(errmsg, sizeof(errmsg)-1,
				"Error reading from '%s', wrong parameters?",
				name);
#else
			sprintf(errmsg,
				"Error reading from '%s', wrong parameters?",
				name);
#endif
			continue;
		}
		if(verbose>=2)
			print_sector("Read sector", buf, 512);
		break;
	}

	/* print error msg if needed */
	if ( dev->drive == 0 ){
		FREE(&Stream);
		fprintf(stderr,"%s: %s\n", argv[0],errmsg);
		exit(1);
	}

	if((used_dev.sectors || used_dev.heads) &&
	   (!used_dev.sectors || !used_dev.heads)) {
		fprintf(stderr,"You should either indicate both the number of sectors and the number of heads,\n");
		fprintf(stderr," or none of them\n");
		exit(1);
	}

	if(initialize) {
		if (bootSector) {
			int fd;
			fd = open(bootSector, O_RDONLY | O_BINARY | O_LARGEFILE);
			if (fd < 0) {
				perror("open MBR");
				exit(1);
			}
			if(read(fd, (char *) buf, 512) < 512) {
				perror("read MBR");
				exit(1);
			}
		}
		memset((char *)(partTable+1), 0, 4*sizeof(*partTable));
		set_word(((unsigned char*)buf)+510, 0xaa55);
	}

	/* check for boot signature, and place it if needed */
	if((buf[510] != 0x55) || (buf[511] != 0xaa)) {
		fprintf(stderr,"Boot signature not set\n");
		fprintf(stderr,
			"Use the -I flag to initialize the partition table, and set the boot signature\n");
		inconsistency = 1;
	}

	tpartition=&partTable[dev->partition];
	if(do_remove){
		if(!tpartition->sys_ind)
			fprintf(stderr,
				"Partition for drive %c: does not exist\n",
				drive);
		if((tpartition->sys_ind & 0x3f) == 5) {
			fprintf(stderr,
				"Partition for drive %c: may be an extended partition\n",
				drive);
			fprintf(stderr,
				"Use the -f flag to remove it anyways\n");
			inconsistency = 1;
		}
		memset(tpartition, 0, sizeof(*tpartition));
	}

	if(create && tpartition->sys_ind) {
		fprintf(stderr,
			"Partition for drive %c: already exists\n", drive);
		fprintf(stderr,
			"Use the -r flag to remove it before attempting to recreate it\n");
	}

	/* if number of heads and sectors not known yet, set "reasonable"
	 * defaults */
	compute_lba_geom_from_tot_sectors(&used_dev);

	/* find out whether there is any activated partition. Moreover
	 * if no offset of a partition to be created have been
	 * specificed, find out whether it may be placed between the
	 * preceding and following partition already existing */
	has_activated = 0;
	for(i=1; i<5; i++){
		struct partition *partition=&partTable[i];
		if(!partition->sys_ind)
			continue;

		if(partition->boot_ind)
			has_activated++;

		if(i<dev->partition && !begin_set)
			begin = END(partition);
		if(i>dev->partition && !end_set && !size_set) {
			end = BEGIN(partition);
			end_set = 1;
		}
	}

	if(!used_dev.sectors && !used_dev.heads) {
		if(tot_sectors) {
			setsize0((uint32_t)tot_sectors,&dummy2,&used_dev.heads,
				 &used_dev.sectors);
		} else {
			used_dev.heads = 64;
			used_dev.sectors = 32;
		}
	}

	if(verbose)
		fprintf(stderr,"sectors: %d heads: %d %u\n",
			used_dev.sectors, used_dev.heads, tot_sectors);

	sec_per_cyl = used_dev.sectors * used_dev.heads;
	if(create) {
		unsigned int overlap;
		if(!end_set && !size_set && tot_sectors) {
			end = tot_sectors - tot_sectors % sec_per_cyl;
			end_set = 1;
		}

		/* if the partition starts right at the beginning of
		 * the disk, keep one track unused to allow place for
		 * the master boot record */
		if(!begin && !begin_set)
			begin = used_dev.sectors ? used_dev.sectors : 2048;

		/* Do not try to align  partitions (other than first) on track
		 * boundaries here: apparently this was a thing of the past */

		if(size_set) {
			end = begin + length;
		} else if(!end_set) {
			fprintf(stderr,"Unknown size\n");
			exit(1);
		}

		/* Make sure partition boundaries are correctly ordered
		 * (end > begin) */
		if(begin >= end) {
			fprintf(stderr, "Begin larger than end\n");
			exit(1);
		}

		/* Check whether new partition doesn't overlap with
		 * any of those already in place */
		if((overlap=findOverlap(partTable, 4, begin, end))) {
			fprintf(stderr,
				"Partition would overlap with partition %d\n",
				overlap);
			exit(1);
		}

		setBeginEnd(tpartition, begin, end,
			    used_dev.heads, used_dev.sectors,
			    !has_activated, type,
			    abs(dev->fat_bits));
	}

	if(activate) {
		if(!tpartition->sys_ind) {
			fprintf(stderr,
				"Partition for drive %c: does not exist\n",
				drive);
		} else {
			switch(activate) {
				case 1:
					tpartition->boot_ind=0x80;
					break;
				case -1:
					tpartition->boot_ind=0x00;
					break;
			}
		}
	}

	inconsistency |= consistencyCheck(partTable, doprint, verbose,
					  &has_activated, tot_sectors,
					  &used_dev, dev->partition);

	switch(has_activated) {
		case 0:
			fprintf(stderr,
				"Warning: no active (bootable) partition present\n");
			break;
		case 1:
			break;
		default:
			fprintf(stderr,
				"Warning: %d active (bootable) partitions present\n",
				has_activated);
			fprintf(stderr,
				"Usually, a disk should have exactly one active partition\n");
			break;
	}

	if(inconsistency && !force) {
		fprintf(stderr,
			"inconsistency detected!\n" );
		if(dirty) {
			fprintf(stderr,
				"Retry with the -f switch to go ahead anyways\n");
			exit(1);
		}
	}

	if(doprint && tpartition->sys_ind) {
		printf("The following command will recreate the partition for drive %c:\n",
		       drive);
		used_dev.tracks =
			(_DWORD(tpartition->nr_sects) +
			 (BEGIN(tpartition) % sec_per_cyl)) /
			sec_per_cyl;
		printf("mpartition -c -b %d -l %d -t %d -h %d -s %d -b %u %c:\n",
		       BEGIN(tpartition), PART_SIZE(tpartition),
		       used_dev.tracks, used_dev.heads, used_dev.sectors,
		       BEGIN(tpartition), drive);
	}

	if(dirty) {
		/* write data back to the disk */
		if(verbose>=2)
			print_sector("Writing sector", buf, 512);
		if (PWRITES(Stream, (char *) buf, 0, 512) != 512) {
			fprintf(stderr,"Error writing partition table");
			exit(1);
		}
		if(verbose>=3)
			print_sector("Sector written", buf, 512);
	}
	FREE(&Stream);
	exit(0);
}
