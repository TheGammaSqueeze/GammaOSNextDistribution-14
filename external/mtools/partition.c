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
 * Buffer read/write module
 */

#include "sysincludes.h"
#include "msdos.h"
#include "mtools.h"
#include "partition.h"

typedef struct Partition_t {
	struct Stream_t head;

	mt_off_t offset; /* Offset, in bytes */
	mt_off_t size; /* size, in bytes */
	uint32_t nbSect; /* size, in sectors */
	
	uint8_t pos;

	uint8_t sectors;
	uint8_t heads;
	uint16_t cyclinders;
} Partition_t;

static __inline__ void print_hsc(hsc *h)
{
	printf(" h=%d s=%d c=%d\n",
	       head(*h), sector(*h), cyl(*h));
}

/*
 * Make sure range [ start, end ] does not overlap with partition i
 */
static int overlapCheck(struct partition *partTable, unsigned int i,
			uint32_t start, uint32_t end) {
	struct partition *partition = &partTable[i];
	if(!partition->sys_ind)
		return 0; /* Partition not allocated => ok */
	if(end > BEGIN(partition) &&
	   (start < END(partition) || END(partition) < BEGIN(partition)))
		/* overlap */
		return -1;
	return 0;
}

unsigned int findOverlap(struct partition *partTable, unsigned int until,
			 uint32_t start, uint32_t end)
{
	unsigned int i;
	for(i=1; i <= until; i++)
		if(overlapCheck(partTable, i, start, end))
			return i;
	return 0;
}


int consistencyCheck(struct partition *partTable, int doprint,
		     int verbose,
		     int *has_activated, uint32_t tot_sectors,
		     struct device *used_dev UNUSEDP,
		     unsigned int target_partition)
{
	unsigned int i;
	bool inconsistency;

	/* quick consistency check */
	inconsistency = 0;
	*has_activated = 0;
	for(i=1; i<=4; i++){
		unsigned int j;
		struct partition *partition = &partTable[i];
		if(!partition->sys_ind)
			continue;
		if(partition->boot_ind)
			(*has_activated)++;

		if(END(partition) < BEGIN(partition)) {
			fprintf(stderr,
				"End of partition %d before its begin\n",
				i);
		}

		if((j = findOverlap(partTable, i-1,
				    BEGIN(partition), END(partition)))) {
			fprintf(stderr,
				"Partitions %d and %d overlap\n",
				j, i);
			inconsistency=1;
		}

		if(tot_sectors && END(partition) >tot_sectors) {
			fprintf(stderr,
				"Partition %d extends beyond end of disk\n", i);
		}

		if(doprint && verbose) {
			if(i==target_partition)
				putchar('*');
			else
				putchar(' ');
			printf("Partition %d\n",i);

			printf("  active=%x\n", partition->boot_ind);
			printf("  start:");
			print_hsc(&partition->start);
			printf("  type=0x%x\n", partition->sys_ind);
			printf("  end:");
			print_hsc(&partition->end);
			printf("  start=%d\n", BEGIN(partition));
			printf("  nr=%d\n", _DWORD(partition->nr_sects));
			printf("\n");
		}
	}
	return inconsistency;
}


static int limit_size(Partition_t *This, mt_off_t start, size_t *len)
{
	if(start > This->size)
		return -1;
	limitSizeToOffT(len, This->size - start);
	return 0;
}

static ssize_t partition_pread(Stream_t *Stream, char *buf,
			       mt_off_t start, size_t len)
{
	DeclareThis(Partition_t);
	if(limit_size(This, start, &len) < 0)
		return -1;
	return PREADS(This->head.Next, buf, start+This->offset, len);
}

static ssize_t partition_pwrite(Stream_t *Stream, char *buf,
				mt_off_t start, size_t len)
{
	DeclareThis(Partition_t);
	if(limit_size(This, start, &len) < 0)
		return -1;
	return PWRITES(This->head.Next, buf, start+This->offset, len);
}

static int partition_data(Stream_t *Stream, time_t *date, mt_off_t *size,
			  int *type, uint32_t *address)
{
	DeclareThis(Partition_t);

	if(date || type || address) {
		int ret = GET_DATA(This->head.Next, date, NULL, type, address);
		if(ret < 0)
			return ret;
	}
	if(size)
		*size = This->size * 512;
	return 0;
}


static int partition_geom(Stream_t *Stream, struct device *dev,
			  UNUSEDP struct device *orig_dev)
{
	DeclareThis(Partition_t);

	if(!dev->tot_sectors)
		dev->tot_sectors = This->nbSect;

	return 0;
}

static Class_t PartitionClass = {
	0,
	0,
	partition_pread,
	partition_pwrite,
	0, /* flush */
	0, /* free */
	partition_geom, /* set_geom */
	partition_data, /* get_data */
	0, /* pre-allocate */
	get_dosConvert_pass_through, /* dos convert */
	0, /* discard */
};

Stream_t *OpenPartition(Stream_t *Next, struct device *dev,
			char *errmsg, mt_off_t *maxSize) {
	Partition_t *This;
	int has_activated;
	unsigned char buf[2048];
	struct partition *partTable=(struct partition *)(buf+ 0x1ae);
	uint32_t partOff;
	struct partition *partition;

	if(!dev || (dev->partition > 4) || (dev->partition <= 0)) {
	    fprintf(stderr,
		    "Invalid partition %d (must be between 1 and 4), ignoring it\n",
		    dev->partition);
	    return NULL;
	}

	This = New(Partition_t);
	if (!This){
		printOom();
		return 0;
	}
	memset((void*)This, 0, sizeof(Partition_t));
	init_head(&This->head, &PartitionClass, Next);


	/* read the first sector, or part of it */
	if (force_pread(This->head.Next, (char*) buf, 0, 512) != 512)
		goto exit_0;
	if( _WORD(buf+510) != 0xaa55) {
		/* Not a partition table */
		if(errmsg)
			sprintf(errmsg,
				"Device does not have a BIOS partition table\n");
		goto exit_0;
	}
	partition = &partTable[dev->partition];
	if(!partition->sys_ind) {
		if(errmsg)
			sprintf(errmsg,
				"Partition %d does not exist\n",
				dev->partition);
		goto exit_0;
	}

	partOff = BEGIN(partition);
	if (maxSize) {
		if (partOff > (smt_off_t)(*maxSize >> 9)) {
			if(errmsg)
				sprintf(errmsg,"init: Big disks not supported");
			goto exit_0;
		}
		*maxSize -= partOff << 9;
		maximize(*maxSize, ((mt_off_t)PART_SIZE(partition)) << 9);
	}

	This->offset = (mt_off_t) partOff << 9;

	if(!mtools_skip_check &&
	   consistencyCheck((struct partition *)(buf+0x1ae), 0, 0,
			    &has_activated, dev->tot_sectors, dev, 0)) {
		fprintf(stderr,
			"Warning: inconsistent partition table\n");
		fprintf(stderr,
			"Possibly unpartitioned device\n");
		fprintf(stderr,
			"\n*** Maybe try without partition=%d in "
			"device definition ***\n\n",
			dev->partition);
		fprintf(stderr,
			"If this is a PCMCIA card, or a disk "
			"partitioned on another computer, this "
			"message may be in error: add "
			"mtools_skip_check=1 to your .mtoolsrc "
			"file to suppress this warning\n");
	}
	dev->tot_sectors = This->nbSect = PART_SIZE(partition);
	This->size = (mt_off_t) This->nbSect << 9;
	return &This->head;
 exit_0:
	Free(This);
	return NULL;
}

