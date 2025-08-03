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

#define U2D_BUFSIZE 4096

typedef struct Filter_t {
	struct Stream_t head;

	char buffer[U2D_BUFSIZE];
	
	size_t readBytes; /* how many bytes read into buffer */
	size_t bufPos; /* position in buffer */

	bool pendingNl;
	bool eof;
} Filter_t;

/* Add CR before NL, and 0x1a at end of file */
static ssize_t read_filter(Stream_t *Stream, char *output, size_t len)
{
	DeclareThis(Filter_t);
	size_t i;
	
	if(This->eof)
		return 0;
	
	for(i=0; i < len && !This->eof; i++) {
		char c;
		if(This->pendingNl) {
			c='\n';
			This->pendingNl=false;
		} else {
			if(This->bufPos == This->readBytes) {
				ssize_t ret = READS(This->head.Next,
						    This->buffer,
						    U2D_BUFSIZE);
				if(ret < 0) {
					/* an error */
					/* If we already have read some data,
					 * first return count of that data
					 * before returning error */
					if(i == 0)
						return -1;
					else
						break;
				}
				This->readBytes = (size_t) ret;
				This->bufPos = 0;
			}

			if(This->bufPos == This->readBytes) {
				/* Still at end of buffer, must be end
				   of file */
				c='\032';
				This->eof=true;
			} else {
				c = This->buffer[This->bufPos++];
				if(c == '\n') {
					This->pendingNl=true;
					c = '\r';
				}
			}
		}
		output[i]=c;
	}

	return (ssize_t) i;
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

Stream_t *open_unix2dos(Stream_t *Next, int convertCharset UNUSEDP)
{
	Filter_t *This;

	This = New(Filter_t);
	if (!This)
		return NULL;
	init_head(&This->head, &FilterClass, Next);

	This->readBytes = This->bufPos = 0;
	This->pendingNl = false;
	This->eof = false;

	return &This->head;
}
