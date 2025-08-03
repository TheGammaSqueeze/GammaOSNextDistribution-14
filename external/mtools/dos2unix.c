/*  Copyright 1996,1997,1999,2001-2003,2008,2009,2021 Alain Knaff.
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
#include "mtools.h"
#include "codepage.h"

typedef struct Filter_t {
	struct Stream_t head;

	int mode;
	/* int convertCharset; */
} Filter_t;

/* read filter filters out messy dos' bizarre end of lines and final 0x1a's */

static ssize_t read_filter(Stream_t *Stream, char *buf, size_t len)
{
	DeclareThis(Filter_t);
	size_t i,j;
	ssize_t ret;
	char newchar;

	ret = READS(This->head.Next, buf, len);
	if ( ret < 0 )
		return ret;

	j = 0;
	for (i=0; i< (size_t) ret; i++){
		if ( buf[i] == '\r' )
			continue;
		if (buf[i] == 0x1a)
			break;
		newchar = buf[i];
		/*
		if (This->convertCharset) newchar = contents_to_unix(newchar);
		*/
		buf[j++] = newchar;
	}

	return (ssize_t) j;
}

static Class_t FilterClass = {
	read_filter,
	0,
	0,
	0,
	0, /* flush */
	0,
	0, /* set geometry */
	get_data_pass_through,
	0,
	0, /* get_dosconvert */
	0  /* discard */
};

Stream_t *open_dos2unix(Stream_t *Next, int convertCharset UNUSEDP)
{
	Filter_t *This;

	This = New(Filter_t);
	if (!This)
		return NULL;
	init_head(&This->head, &FilterClass, Next);
	/*
	  This->convertCharset = convertCharset;
	*/

	return &This->head;
}
