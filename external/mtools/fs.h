#ifndef MTOOLS_FS_H
#define MTOOLS_FS_H

/*  Copyright 1996,1997,2001,2002,2007,2009 Alain Knaff.
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

#include "stream.h"


Stream_t *fs_init(char drive, int mode, int *isRop);
int fat_free(Stream_t *Dir, unsigned int fat);
int fatFreeWithDir(Stream_t *Dir, struct directory *dir);
int fat_error(Stream_t *Dir);
uint32_t fat32RootCluster(Stream_t *Dir);
char getDrive(Stream_t *Stream);

typedef struct Fs_t Fs_t;
bool getSerialized(Fs_t *File);
unsigned long getSerialNumber(Fs_t *File);
uint32_t getClusterBytes(Fs_t *File);

#endif
