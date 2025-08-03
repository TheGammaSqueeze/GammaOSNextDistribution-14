#ifndef READ_DWORD
#define READ_DWORD

/*  Copyright 2007,2009 Alain Knaff.
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

static Dword read_dword(int handle)
{
	Byte val[4];

	if(read(handle, (char *)val, 4) < 4)
		return (Dword) -1;

	return byte2dword(val);
}

UNUSED(static int32_t read_sdword(int handle))
{
	Byte val[4];

	if(read(handle, (char *)val, 4) < 4)
		return (int32_t) -1;

	return byte2sdword(val);
}


struct SQwordRet { int64_t v; int err; };
UNUSED(static struct SQwordRet read_sqword(int handle) )
{
	Byte val[8];
	struct SQwordRet ret;

	if(read(handle, (char *)val, 8) < 8) {
		ret.err=-1;
	} else {
		ret.v = (int64_t) byte2qword(val);
		ret.err = 0;
	}
	return ret;
}

#endif
