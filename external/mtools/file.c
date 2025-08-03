/*  Copyright 1996-1999,2001-2003,2007-2009,2011 Alain Knaff.
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
#include "mtools.h"
#include "fsP.h"
#include "file.h"
#include "htable.h"
#include "dirCache.h"
#include "buffer.h"

typedef struct File_t {
	struct Stream_t head;

	struct Stream_t *Buffer;
	
	int (*map)(struct File_t *this, uint32_t where, uint32_t *len, int mode,
			   mt_off_t *res);
	uint32_t FileSize;

	/* How many bytes do we project to need for this file
	   (includes those already in FileSize) */
	uint32_t preallocatedSize;

	/* How many clusters we have asked the lower layer to reserve
	   for us (only what we will need in the future, excluding already
	   allocated clusters in FileSize) */
	uint32_t preallocatedClusters;

	/* Absolute position of first cluster of file */
	unsigned int FirstAbsCluNr;

	/* Absolute position of previous cluster */
	unsigned int PreviousAbsCluNr;

	/* Relative position of previous cluster */
	unsigned int PreviousRelCluNr;
	direntry_t direntry;
	size_t hint;
	struct dirCache_t *dcp;

	unsigned int loopDetectRel;
	unsigned int loopDetectAbs;

	uint32_t where;
} File_t;

static Class_t FileClass;
static T_HashTable *filehash;

static File_t *getUnbufferedFile(Stream_t *Stream)
{
	while(Stream->Class != &FileClass)
		Stream = Stream->Next;
	return (File_t *) Stream;
}

static inline Fs_t *_getFs(File_t *File)
{
	return (Fs_t *) File->head.Next;
}

Fs_t *getFs(Stream_t *Stream)
{
	return (Fs_t *)getUnbufferedFile(Stream)->head.Next;
}

struct dirCache_t **getDirCacheP(Stream_t *Stream)
{
	return &getUnbufferedFile(Stream)->dcp;
}

direntry_t *getDirentry(Stream_t *Stream)
{
	return &getUnbufferedFile(Stream)->direntry;
}

/**
 * Overflow-safe conversion of bytes to cluster
 */
static uint32_t filebytesToClusters(uint32_t bytes, uint32_t clus_size) {
	uint32_t ret = bytes / clus_size;
	if(bytes % clus_size)
		ret++;
	return ret;
}

static int recalcPreallocSize(File_t *This)
{
	uint32_t currentClusters, neededClusters;
	unsigned int clus_size;
	uint32_t neededPrealloc;
	Fs_t *Fs = _getFs(This);

#if 0
	if(This->FileSize & 0xc0000000) {
		fprintf(stderr, "Bad filesize\n");
	}
	if(This->preallocatedSize & 0xc0000000) {
		fprintf(stderr, "Bad preallocated size %x\n",
				(int) This->preallocatedSize);
	}
#endif
	clus_size = Fs->cluster_size * Fs->sector_size;
	currentClusters = filebytesToClusters(This->FileSize, clus_size);
	neededClusters = filebytesToClusters(This->preallocatedSize, clus_size);
	if(neededClusters < currentClusters)
		neededPrealloc = 0;
	else
		neededPrealloc = neededClusters - currentClusters;
	if(neededPrealloc > This->preallocatedClusters) {
		int r = fsPreallocateClusters(Fs, neededPrealloc-
					      This->preallocatedClusters);
		if(r)
			return r;
	} else {
		fsReleasePreallocateClusters(Fs, This->preallocatedClusters -
					     neededPrealloc);
	}
	This->preallocatedClusters = neededPrealloc;
	return 0;
}

static int _loopDetect(unsigned int *oldrel, unsigned int rel,
		       unsigned int *oldabs, unsigned int absol)
{
	if(*oldrel && rel > *oldrel && absol == *oldabs) {
		fprintf(stderr, "loop detected! oldrel=%d newrel=%d abs=%d\n",
				*oldrel, rel, absol);
		return -1;
	}

	if(rel >= 2 * *oldrel + 1) {
		*oldrel = rel;
		*oldabs = absol;
	}
	return 0;
}


static int loopDetect(File_t *This, unsigned int rel, unsigned int absol)
{
	return _loopDetect(&This->loopDetectRel, rel, &This->loopDetectAbs, absol);
}

static unsigned int _countBlocks(Fs_t *This, unsigned int block)
{
	unsigned int blocks;
	unsigned int rel, oldabs, oldrel;

	blocks = 0;

	oldabs = oldrel = rel = 0;

	while (block <= This->last_fat && block != 1 && block) {
		blocks++;
		block = fatDecode(This, block);
		rel++;
		if(_loopDetect(&oldrel, rel, &oldabs, block) < 0)
			block = 1;
	}
	return blocks;
}

unsigned int countBlocks(Stream_t *Dir, unsigned int block)
{
	Stream_t *Stream = GetFs(Dir);
	DeclareThis(Fs_t);

	return _countBlocks(This, block);
}

/* returns number of bytes in a directory.  Represents a file size, and
 * can hence be not bigger than 2^32
 */
static uint32_t countBytes(Stream_t *Dir, unsigned int block)
{
	Stream_t *Stream = GetFs(Dir);
	DeclareThis(Fs_t);

	return _countBlocks(This, block) *
		This->sector_size * This->cluster_size;
}

void printFat(Stream_t *Stream)
{
	File_t *This = getUnbufferedFile(Stream);
	uint32_t n;
	unsigned int rel;
	unsigned long begin, end;
	int first;

	n = This->FirstAbsCluNr;
	if(!n) {
		printf("Root directory or empty file\n");
		return;
	}

	rel = 0;
	first = 1;
	begin = end = 0;
	do {
		if (first || n != end+1) {
			if (!first) {
				if (begin != end)
					printf("-%lu", end);
				printf("> ");
			}
			begin = end = n;
			printf("<%lu", begin);
		} else {
			end++;
		}
		first = 0;
		n = fatDecode(_getFs(This), n);
		rel++;
		if(loopDetect(This, rel, n) < 0)
			n = 1;
	} while (n <= _getFs(This)->last_fat && n != 1);
	if(!first) {
		if (begin != end)
			printf("-%lu", end);
		printf(">");
	}
}

void printFatWithOffset(Stream_t *Stream, off_t offset) {
	File_t *This = getUnbufferedFile(Stream);
	uint32_t n;
	unsigned int rel;
	off_t clusSize;

	n = This->FirstAbsCluNr;
	if(!n) {
		printf("Root directory or empty file\n");
		return;
	}

	clusSize = _getFs(This)->cluster_size * _getFs(This)->sector_size;

	rel = 0;
	while(offset >= clusSize) {
		n = fatDecode(_getFs(This), n);
		rel++;
		if(loopDetect(This, rel, n) < 0)
			return;
		if(n > _getFs(This)->last_fat)
			return;
		offset -= clusSize;
	}

	printf("%lu", (unsigned long) n);
}

static int normal_map(File_t *This, uint32_t where, uint32_t *len, int mode,
		      mt_off_t *res)
{
	unsigned int offset;
	size_t end;
	uint32_t NrClu; /* number of clusters to read */
	uint32_t RelCluNr;
	uint32_t CurCluNr;
	uint32_t NewCluNr;
	uint32_t AbsCluNr;
	uint32_t clus_size;
	Fs_t *Fs = _getFs(This);

	*res = 0;
	clus_size = Fs->cluster_size * Fs->sector_size;
	offset = where % clus_size;

	if (mode == MT_READ)
		maximize(*len, This->FileSize - where);
	if (*len == 0 )
		return 0;

	if (This->FirstAbsCluNr < 2){
		if( mode == MT_READ || *len == 0){
			*len = 0;
			return 0;
		}
		NewCluNr = get_next_free_cluster(_getFs(This), 1);
		if (NewCluNr == 1 ){
			errno = ENOSPC;
			return -2;
		}
		hash_remove(filehash, (void *) This, This->hint);
		This->FirstAbsCluNr = NewCluNr;
		hash_add(filehash, (void *) This, &This->hint);
		fatAllocate(_getFs(This), NewCluNr, Fs->end_fat);
	}

	RelCluNr = where / clus_size;

	if (RelCluNr >= This->PreviousRelCluNr){
		CurCluNr = This->PreviousRelCluNr;
		AbsCluNr = This->PreviousAbsCluNr;
	} else {
		CurCluNr = 0;
		AbsCluNr = This->FirstAbsCluNr;
	}


	NrClu = (offset + *len - 1) / clus_size;
	while (CurCluNr <= RelCluNr + NrClu){
		if (CurCluNr == RelCluNr){
			/* we have reached the beginning of our zone. Save
			 * coordinates */
			This->PreviousRelCluNr = RelCluNr;
			This->PreviousAbsCluNr = AbsCluNr;
		}
		NewCluNr = fatDecode(_getFs(This), AbsCluNr);
		if (NewCluNr == 1 || NewCluNr == 0){
			fprintf(stderr,"Fat problem while decoding %d %x\n",
				AbsCluNr, NewCluNr);
			exit(1);
		}
		if(CurCluNr == RelCluNr + NrClu)
			break;
		if (NewCluNr > Fs->last_fat && mode == MT_WRITE){
			/* if at end, and writing, extend it */
			NewCluNr = get_next_free_cluster(_getFs(This), AbsCluNr);
			if (NewCluNr == 1 ){ /* no more space */
				errno = ENOSPC;
				return -2;
			}
			fatAppend(_getFs(This), AbsCluNr, NewCluNr);
		}

		if (CurCluNr < RelCluNr && NewCluNr > Fs->last_fat){
			*len = 0;
			return 0;
		}

		if (CurCluNr >= RelCluNr && NewCluNr != AbsCluNr + 1)
			break;
		CurCluNr++;
		AbsCluNr = NewCluNr;
		if(loopDetect(This, CurCluNr, AbsCluNr)) {
			errno = EIO;
			return -2;
		}
	}

	maximize(*len, (1 + CurCluNr - RelCluNr) * clus_size - offset);

	end = where + *len;
	if(batchmode &&
	   mode == MT_WRITE &&
	   end >= This->FileSize) {
		/* In batch mode, when writing at end of file, "pad"
		 * to nearest cluster boundary so that we don't have
		 * to read that data back from disk. */
		*len += ROUND_UP(end, clus_size) - end;
	}

	if((*len + offset) / clus_size + This->PreviousAbsCluNr-2 >
		Fs->num_clus) {
		fprintf(stderr, "cluster too big\n");
		exit(1);
	}

	*res = sectorsToBytes(Fs,
			      (This->PreviousAbsCluNr-2) * Fs->cluster_size +
			      Fs->clus_start) + to_mt_off_t(offset);
	return 1;
}


static int root_map(File_t *This, uint32_t where, uint32_t *len,
		    int mode UNUSEDP,  mt_off_t *res)
{
	Fs_t *Fs = _getFs(This);

	if(Fs->dir_len * Fs->sector_size < where) {
		*len = 0;
		errno = ENOSPC;
		return -2;
	}

	maximize(*len, Fs->dir_len * Fs->sector_size - where);
        if (*len == 0)
            return 0;

	*res = sectorsToBytes(Fs, Fs->dir_start) +
		to_mt_off_t(where);
	return 1;
}

static ssize_t read_file(Stream_t *Stream, char *buf, size_t ilen)
{
	DeclareThis(File_t);
	mt_off_t pos;
	int err;
	uint32_t len = truncSizeTo32u(ilen);
	ssize_t ret;
	
	Stream_t *Disk = _getFs(This)->head.Next;

	err = This->map(This, This->where, &len, MT_READ, &pos);
	if(err <= 0)
		return err;
	ret = PREADS(Disk, buf, pos, len);
	if(ret < 0)
		return ret;
	This->where += (size_t) ret;
	return ret;
}

static ssize_t write_file(Stream_t *Stream, char *buf, size_t ilen)
{
	DeclareThis(File_t);
	mt_off_t pos;
	ssize_t ret;
	uint32_t requestedLen;
	uint32_t bytesWritten;
	Stream_t *Disk = _getFs(This)->head.Next;
	uint32_t maxLen = UINT32_MAX-This->where;
	uint32_t len;
	int err;

	if(ilen > maxLen) {
		len = maxLen;
	} else
		len = (uint32_t) ilen;
	requestedLen = len;
	err = This->map(This, This->where, &len, MT_WRITE, &pos);
	if( err <= 0)
		return err;
	if(batchmode)
		ret = force_pwrite(Disk, buf, pos, len);
	else
		ret = PWRITES(Disk, buf, pos, len);
	if(ret < 0)
		/* Error occured */
		return ret;
	if((uint32_t)ret > requestedLen)
		/* More data than requested may be written to lower
		 * levels if batch mode is active, in order to "pad"
		 * the last cluster of a file, so that we don't have
		 * to read that back from disk */
		bytesWritten = requestedLen;
	else
		bytesWritten = (uint32_t)ret;
	This->where += bytesWritten;
	if (This->where > This->FileSize )
		This->FileSize = This->where;
	recalcPreallocSize(This);
	return (ssize_t) bytesWritten;
}

static ssize_t pread_file(Stream_t *Stream, char *buf, mt_off_t where,
			  size_t ilen) {
	DeclareThis(File_t);
	This->where = truncMtOffTo32u(where);
	return read_file(Stream, buf, ilen);
}

static ssize_t pwrite_file(Stream_t *Stream, char *buf, mt_off_t where,
			  size_t ilen) {
	DeclareThis(File_t);
	This->where = truncMtOffTo32u(where);
	return write_file(Stream, buf, ilen);
}

/*
 * Convert an MSDOS time & date stamp to the Unix time() format
 */

static int month[] = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334,
					  0, 0, 0 };
static __inline__ time_t conv_stamp(struct directory *dir)
{
	struct tm *tmbuf;
	long tzone, dst;
	time_t accum, tmp;

	accum = DOS_YEAR(dir) - 1970; /* years past */

	/* days passed */
	accum = accum * 365L + month[DOS_MONTH(dir)-1] + DOS_DAY(dir);

	/* leap years */
	accum += (DOS_YEAR(dir) - 1972) / 4L;

	/* back off 1 day if before 29 Feb */
	if (!(DOS_YEAR(dir) % 4) && DOS_MONTH(dir) < 3)
	        accum--;
	accum = accum * 24L + DOS_HOUR(dir); /* hours passed */
	accum = accum * 60L + DOS_MINUTE(dir); /* minutes passed */
	accum = accum * 60L + DOS_SEC(dir); /* seconds passed */

	/* correct for Time Zone */
#ifdef HAVE_GETTIMEOFDAY
	{
		struct timeval tv;
		struct timezone tz;

		gettimeofday(&tv, &tz);
		tzone = tz.tz_minuteswest * 60L;
	}
#else
#if defined HAVE_TZSET && !defined OS_mingw32msvc
	{
#if !defined OS_ultrix && !defined OS_cygwin
		/* Ultrix defines this to be a different type */
		extern long timezone;
#endif
		tzset();
		tzone = (long) timezone;
	}
#else
	tzone = 0;
#endif /* HAVE_TZSET */
#endif /* HAVE_GETTIMEOFDAY */

	accum += tzone;

	/* correct for Daylight Saving Time */
	tmp = accum;
	tmbuf = localtime(&tmp);
	if(tmbuf) {
		dst = (tmbuf->tm_isdst) ? (-60L * 60L) : 0L;
		accum += dst;
	}
	return accum;
}


static int get_file_data(Stream_t *Stream, time_t *date, mt_off_t *size,
			 int *type, uint32_t *address)
{
	DeclareThis(File_t);

	if(date)
		*date = conv_stamp(& This->direntry.dir);
	if(size)
		*size = to_mt_off_t(This->FileSize);
	if(type)
		*type = This->direntry.dir.attr & ATTR_DIR;
	if(address)
		*address = This->FirstAbsCluNr;
	return 0;
}


static int free_file(Stream_t *Stream)
{
	DeclareThis(File_t);
	Fs_t *Fs = _getFs(This);
	fsReleasePreallocateClusters(Fs, This->preallocatedClusters);
	FREE(&This->direntry.Dir);
	freeDirCache(Stream);
	return hash_remove(filehash, (void *) Stream, This->hint);
}


static int flush_file(Stream_t *Stream)
{
	DeclareThis(File_t);
	direntry_t *entry = &This->direntry;

	if(isRootDir(Stream)) {
		return 0;
	}

	if(This->FirstAbsCluNr != getStart(entry->Dir, &entry->dir)) {
		set_word(entry->dir.start, This->FirstAbsCluNr & 0xffff);
		set_word(entry->dir.startHi, This->FirstAbsCluNr >> 16);
		dir_write(entry);
	}
	return 0;
}


static int pre_allocate_file(Stream_t *Stream, mt_off_t isize)
{
	DeclareThis(File_t);

	uint32_t size = truncMtOffTo32u(isize);

	if(size > This->FileSize &&
	   size > This->preallocatedSize) {
		This->preallocatedSize = size;
		return recalcPreallocSize(This);
	} else
		return 0;
}

static Class_t FileClass = {
	read_file,
	write_file,
	pread_file,
	pwrite_file,
	flush_file, /* flush */
	free_file, /* free */
	0, /* get_geom */
	get_file_data,
	pre_allocate_file,
	get_dosConvert_pass_through,
	0 /* discard */
};

static unsigned int getAbsCluNr(File_t *This)
{
	if(This->FirstAbsCluNr)
		return This->FirstAbsCluNr;
	if(isRootDir((Stream_t *) This))
		return 0;
	return 1;
}

static uint32_t func1(void *Stream)
{
	DeclareThis(File_t);

	return getAbsCluNr(This) ^ (uint32_t) (unsigned long) This->head.Next;
}

static uint32_t func2(void *Stream)
{
	DeclareThis(File_t);

	return getAbsCluNr(This);
}

static int comp(void *Stream, void *Stream2)
{
	DeclareThis(File_t);

	File_t *This2 = (File_t *) Stream2;

	return _getFs(This) != _getFs(This2) ||
		getAbsCluNr(This) != getAbsCluNr(This2);
}

static void init_hash(void)
{
	static int is_initialised=0;

	if(!is_initialised){
		make_ht(func1, func2, comp, 20, &filehash);
		is_initialised = 1;
	}
}


static Stream_t *_internalFileOpen(Stream_t *Dir, unsigned int first,
				   uint32_t size, direntry_t *entry)
{
	Stream_t *Stream = GetFs(Dir);
	DeclareThis(Fs_t);
	File_t Pattern;
	File_t *File;

	init_hash();
	This->head.refs++;

	if(first != 1){
		/* we use the illegal cluster 1 to mark newly created files.
		 * do not manage those by hashtable */
		init_head(&Pattern.head, &FileClass, &This->head);
		if(first || (entry && !IS_DIR(entry)))
			Pattern.map = normal_map;
		else
			Pattern.map = root_map;
		Pattern.FirstAbsCluNr = first;
		Pattern.loopDetectRel = 0;
		Pattern.loopDetectAbs = first;
		if(!hash_lookup(filehash, (T_HashTableEl) &Pattern,
				(T_HashTableEl **)&File, 0)){
			File->head.refs++;
			This->head.refs--;
			return (Stream_t *) File;
		}
	}

	File = New(File_t);
	if (!File)
		return NULL;
	init_head(&File->head, &FileClass, &This->head);
	File->Buffer = NULL;
	File->dcp = 0;
	File->preallocatedClusters = 0;
	File->preallocatedSize = 0;
	/* memorize dir for date and attrib */
	File->direntry = *entry;
	if(entry->entry == -3)
		File->direntry.Dir = (Stream_t *) File; /* root directory */
	else
		COPY(File->direntry.Dir);
	File->where = 0;
	if(first || (entry && !IS_DIR(entry)))
		File->map = normal_map;
	else
		File->map = root_map; /* FAT 12/16 root directory */
	if(first == 1)
		File->FirstAbsCluNr = 0;
	else
		File->FirstAbsCluNr = first;

	File->loopDetectRel = 0;
	File->loopDetectAbs = 0;

	File->PreviousRelCluNr = 0xffff;
	File->FileSize = size;
	hash_add(filehash, (void *) File, &File->hint);
	return (Stream_t *) File;
}

static void bufferize(Stream_t **Dir)
{
	Stream_t *BDir;
	File_t *file = (File_t *) *Dir;
	
	if(!*Dir)
		return;

	if(file->Buffer){
		(*Dir)->refs--;
		file->Buffer->refs++;
		*Dir = file->Buffer;
		return;
	}
	
	BDir = buf_init(*Dir, 64*16384, 512, MDIR_SIZE);
	if(!BDir){
		FREE(Dir);
		*Dir = NULL;
	} else {
		file->Buffer = BDir;
		*Dir = BDir;
	}
}


Stream_t *OpenRoot(Stream_t *Dir)
{
	unsigned int num;
	direntry_t entry;
	uint32_t size;
	Stream_t *file;

	memset(&entry, 0, sizeof(direntry_t));

	num = fat32RootCluster(Dir);

	/* make the directory entry */
	entry.entry = -3;
	entry.name[0] = '\0';
	mk_entry_from_base("/", ATTR_DIR, num, 0, 0, &entry.dir);

	if(num)
		size = countBytes(Dir, num);
	else {
		Fs_t *Fs = (Fs_t *) GetFs(Dir);
		size = Fs->dir_len * Fs->sector_size;
	}
	file = _internalFileOpen(Dir, num, size, &entry);
	bufferize(&file);
	return file;
}


Stream_t *OpenFileByDirentry(direntry_t *entry)
{
	Stream_t *file;
	unsigned int first;
	uint32_t size;

	first = getStart(entry->Dir, &entry->dir);

	if(!first && IS_DIR(entry))
		return OpenRoot(entry->Dir);
	if (IS_DIR(entry))
		size = countBytes(entry->Dir, first);
	else
		size = FILE_SIZE(&entry->dir);
	file = _internalFileOpen(entry->Dir, first, size, entry);
	if(IS_DIR(entry)) {
		bufferize(&file);
		if(first == 1)
			dir_grow(file, 0);
	}

	return file;
}


int isRootDir(Stream_t *Stream)
{
	File_t *This = getUnbufferedFile(Stream);

	return This->map == root_map;
}
