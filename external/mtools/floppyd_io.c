/*  Copyright 1999 Peter Schlaile.
 *  Copyright 1999-2002,2005-2007,2009 Alain Knaff.
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
 * IO to the floppyd daemon running on the local X-Server Host
 *
 * written by:
 *
 * Peter Schlaile
 *
 * udbz@rz.uni-karlsruhe.de
 *
 */

#include "sysincludes.h"
#include "stream.h"
#include "mtools.h"
#include "msdos.h"
#include "scsi.h"
#include "floppyd_io.h"

/* ######################################################################## */


static const char* AuthErrors[] = {
	"Auth success",
	"Auth failed: Packet oversized",
	"Auth failed: X-Cookie doesn't match",
	"Auth failed: Wrong transmission protocol version",
	"Auth failed: Device locked",
	"Auth failed: Bad packet",
	"Auth failed: I/O Error"
};


typedef struct RemoteFile_t {
	struct Stream_t head;

	int fd;
	mt_off_t offset;
	mt_off_t lastwhere;
	mt_off_t size;
	unsigned int version;
	unsigned int capabilities;
	int drive;
} RemoteFile_t;


#include "byte_dword.h"
#include "read_dword.h"


/* ######################################################################## */

static unsigned int authenticate_to_floppyd(RemoteFile_t *floppyd,
					    int sock, char *display)
{
	size_t cookielen;
	uint16_t filelen;
	ssize_t newlen;
	Byte buf[16];
	const char *command[] = { "xauth", "xauth", "extract", "-", 0, 0 };
	char *xcookie;
	Dword errcode;
	int l;

	command[4] = display;

	cookielen=strlen(display);
	cookielen += 100;

	xcookie = (char *) safe_malloc(cookielen+4);
	newlen = safePopenOut(command, xcookie+4, cookielen);
	if(newlen < 1 || newlen > UINT16_MAX)
		return AUTH_AUTHFAILED;
	filelen = (uint16_t) newlen;

	/* Version negotiation */
	dword2byte(4,buf);
	dword2byte(floppyd->version,buf+4);
	if(write(sock, buf, 8) < 8)
		return AUTH_IO_ERROR;

	if ( (l = (int) read_dword(sock)) < 4) {
		return AUTH_WRONGVERSION;
	}

	errcode = read_dword(sock);

	if (errcode != AUTH_SUCCESS) {
		return errcode;
	}

	if(l >= 8)
		floppyd->version = read_dword(sock);
	if(l >= 12)
		floppyd->capabilities = read_dword(sock);

	dword2byte(filelen, (Byte *)xcookie);
	if(write(sock, xcookie, filelen+4) < ((ssize_t) (filelen + 4)))
		return AUTH_IO_ERROR;

	if (read_dword(sock) != 4) {
		return AUTH_PACKETOVERSIZE;
	}

	errcode = read_dword(sock);

	return errcode;
}


static ssize_t floppyd_reader(int fd, char* buffer, uint32_t len)
{
	Dword errcode;
	Dword gotlen;
	Byte buf[16];

	dword2byte(1, buf);
	buf[4] = OP_READ;
	dword2byte(4, buf+5);
	dword2byte(len, buf+9);
	if(write(fd, buf, 13) < 13)
		return AUTH_IO_ERROR;

	if (read_dword(fd) != 8) {
		errno = EIO;
		return -1;
	}

	gotlen = read_dword(fd);
	errcode = read_dword(fd);

	if (gotlen != (Dword) -1) {
		size_t l;
		unsigned int start;
		if (read_dword(fd) != gotlen) {
			errno = EIO;
			return -1;
		}
		for (start = 0, l = 0; start < gotlen; start += l) {
			ssize_t ret = read(fd, buffer+start, gotlen-start);
			if( ret < 0)
				return -1;
			if (ret == 0) {
				errno = EIO;
				return -1;
			}
			l = (size_t) ret;
		}
	} else {
		errno = (int) errcode;
	}
	return (ssize_t) gotlen;
}

static ssize_t floppyd_writer(int fd, char* buffer, uint32_t len)
{
	int errcode;
	int32_t gotlen;
	Byte buf[16];
	ssize_t ret;

	dword2byte(1, buf);
	buf[4] = OP_WRITE;
	dword2byte(len, buf+5);

	cork(fd, 1);
	if(write(fd, buf, 9) < 9)
		return AUTH_IO_ERROR;
	ret = write(fd, buffer, len);
	if(ret == -1 || (size_t) ret < len)
		return AUTH_IO_ERROR;
	cork(fd, 0);

	if (read_dword(fd) != 8) {
		errno = EIO;
		return -1;
	}

	gotlen = read_sdword(fd);
	errcode = read_sdword(fd);

	errno = errcode;
	if(errno != 0 && gotlen == 0) {
	    if (errno == EBADF)
		errno = EROFS;
	    gotlen = -1;
	}

	return gotlen;
}

static int floppyd_lseek(int fd, int32_t offset, int whence)
{
	int errcode;
	int gotlen;
	Byte buf[32];

	dword2byte(1, buf);
	buf[4] = OP_SEEK;

	dword2byte(8, buf+5);
	sdword2byte(offset, buf+9);
	sdword2byte(whence, buf+13);

	if(write(fd, buf, 17) < 17)
		return AUTH_IO_ERROR;

	if (read_dword(fd) != 8) {
		errno = EIO;
		return -1;
	}

	gotlen = read_sdword(fd);
	errcode = read_sdword(fd);

	errno = errcode;

	return gotlen;
}

#if SIZEOF_OFF_T >= 8
static mt_off_t floppyd_lseek64(int fd, mt_off_t offset, int whence)
{
	int errcode;
	struct SQwordRet gotlen;
	Byte buf[32];

	dword2byte(1, buf);
	buf[4] = OP_SEEK64;

	dword2byte(12, buf+5);
	qword2byte((uint32_t)offset, buf+9);
	sdword2byte(whence, buf+17);

	if(write(fd, buf, 21) < 21)
		return AUTH_IO_ERROR;

	if (read_dword(fd) != 12) {
		errno = EIO;
		return -1;
	}

	gotlen = read_sqword(fd);
	errcode = read_sdword(fd);

	errno = errcode;

	return gotlen.v;
}
#endif

static int floppyd_open(RemoteFile_t *This, int mode)
{
	int errcode;
	int gotlen;
	Byte buf[16];

	if(! (This->capabilities & FLOPPYD_CAP_EXPLICIT_OPEN) ) {
		/* floppyd has no "explicit seek" capabilities */
		return 0;
	}

	dword2byte(1, buf);
	if((mode & O_ACCMODE) == O_RDONLY)
		buf[4] = OP_OPRO;
	else
		buf[4] = OP_OPRW;
	dword2byte(4, buf+5);
	sdword2byte(This->drive, buf+9);

	if(write(This->fd, buf, 13) < 13)
		return AUTH_IO_ERROR;

	if (read_dword(This->fd) != 8) {
		errno = EIO;
		return -1;
	}

	gotlen = read_sdword(This->fd);
	errcode = read_sdword(This->fd);

	errno = errcode;

	return gotlen;
}


/* ######################################################################## */

typedef ssize_t (*iofn) (int, char *, uint32_t);

static ssize_t floppyd_io(Stream_t *Stream, char *buf, mt_off_t where,
			  size_t len, iofn io)
{
	DeclareThis(RemoteFile_t);
	ssize_t ret;

	where += This->offset;

	if (where != This->lastwhere ){
#if SIZEOF_OFF_T >= 8
		if(This->capabilities & FLOPPYD_CAP_LARGE_SEEK) {
			if(floppyd_lseek64( This->fd, where, SEEK_SET) < 0 ){
				perror("floppyd_lseek64");
				This->lastwhere = -1;
				return -1;
			}
		} else
#endif
			{
			if(where > INT32_MAX  || where < INT32_MIN) {
				fprintf(stderr, "Seek position out of range\n");
				return -1;
			}
			if(floppyd_lseek(This->fd, (int32_t) where, SEEK_SET) < 0 ){
				perror("floppyd_lseek");
				This->lastwhere = -1;
				return -1;
			}
		}
	}
	ret = io(This->fd, buf,
		 (len > INT32_MAX) ? (uint32_t)INT32_MAX+1 : (uint32_t) len);
	if ( ret == -1 ){
		perror("floppyd_io");
		This->lastwhere = -1;
		return -1;
	}
	This->lastwhere = where + ret;
	return ret;
}

static ssize_t floppyd_pread(Stream_t *Stream, char *buf,
			     mt_off_t where, size_t len)
{
	return floppyd_io(Stream, buf, where, len, floppyd_reader);
}

static ssize_t floppyd_pwrite(Stream_t *Stream, char *buf,
			      mt_off_t where, size_t len)
{
	return floppyd_io(Stream, buf, where, len, floppyd_writer);
}

static int floppyd_flush(Stream_t *Stream)
{
	Byte buf[16];

	DeclareThis(RemoteFile_t);

	dword2byte(1, buf);
	buf[4] = OP_FLUSH;
	dword2byte(1, buf+5);
	buf[9] = '\0';

	if(write(This->fd, buf, 10) < 10)
		return AUTH_IO_ERROR;

	if (read_dword(This->fd) != 8) {
		errno = EIO;
		return -1;
	}

	read_dword(This->fd);
	read_dword(This->fd);
	return 0;
}

static int floppyd_free(Stream_t *Stream)
{
	Byte buf[16];
	int gotlen;
	int errcode;
	DeclareThis(RemoteFile_t);

	if (This->fd > 2) {
		dword2byte(1, buf);
		buf[4] = OP_CLOSE;
		if(write(This->fd, buf, 5) < 5)
			return AUTH_IO_ERROR;
		shutdown(This->fd, 1);
		if (read_dword(This->fd) != 8) {
		    errno = EIO;
		    return -1;
		}

		gotlen = read_sdword(This->fd);
		errcode = read_sdword(This->fd);

		errno = errcode;

		close(This->fd);
		return gotlen;
	} else {
		return 0;
	}
}



static int floppyd_data(Stream_t *Stream, time_t *date, mt_off_t *size,
			int *type, uint32_t *address)
{
	DeclareThis(RemoteFile_t);

	if(date)
		/* unknown, and irrelevant anyways */
		*date = 0;
	if(size)
		/* the size derived from the geometry */
		*size = This->size;
	if(type)
		*type = 0; /* not a directory */
	if(address)
		*address = 0;
	return 0;
}

/* ######################################################################## */

static Class_t FloppydFileClass = {
	0,
	0,
	floppyd_pread,
	floppyd_pwrite,
	floppyd_flush,
	floppyd_free,
	set_geom_noop,
	floppyd_data,
	0, /* pre_allocate */
	0, /* get_dosConvert */
	0  /* discard */
};

/* ######################################################################## */

static int get_host_and_port_and_drive(const char* name, char** hostname,
				       char **display, uint16_t* port,
				       int *drive)
{
	char* newname = strdup(name);
	char* p;
	char* p2;

	p = newname;
	while (*p != '/' && *p) p++;
	p2 = p;
	if (*p) p++;
	*p2 = 0;

	*port = FLOPPYD_DEFAULT_PORT;
	if(*p >= '0' && *p <= '9')
	  *port = strtou16(p, &p, 0);
	if(*p == '/')
	  p++;
	*drive = 0;
	if(*p >= '0' && *p <= '9')
	  *drive = strtoi(p, &p, 0);

	*display = strdup(newname);

	p = newname;
	while (*p != ':' && *p) p++;
	p2 = p;
	if (*p) p++;
	*p2 = 0;

	*port += atoi(p);  /* add display number to the port */

	if (!*newname || strcmp(newname, "unix") == 0) {
		free(newname);
		newname = strdup("localhost");
	}

	*hostname = newname;
	return 1;
}

/*
 *  * Return the IP address of the specified host.
 *  */
static in_addr_t getipaddress(char *ipaddr)
{
	struct hostent  *host;
	in_addr_t        ip;

	if (((ip = inet_addr(ipaddr)) == INADDR_NONE) &&
	    (strcmp(ipaddr, "255.255.255.255") != 0)) {

		if ((host = gethostbyname(ipaddr)) != NULL) {
			memcpy(&ip, host->h_addr, sizeof(ip));
		}

		endhostent();
	}

#ifdef DEBUG
	fprintf(stderr, "IP lookup %s -> 0x%08lx\n", ipaddr, ip);
#endif

	return (ip);
}

/*
 *  * Connect to the floppyd server.
 *  */
static int connect_to_server(in_addr_t ip, uint16_t port)
{

	struct sockaddr_in      addr;
	int                     sock;

	/*
	 * Allocate a socket.
	 */
	if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
		return (-1);
	}

	/*
	 * Set the address to connect to.
	 */

	addr.sin_family = AF_INET;
	addr.sin_port = htons(port);
	addr.sin_addr.s_addr = ip;

        /*
	 * Connect our socket to the above address.
	 */
	if (connect(sock, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
		return (-1);
	}

        /*
	 * Set the keepalive socket option to on.
	 */
	{
		int             on = 1;
		setsockopt(sock, SOL_SOCKET, SO_KEEPALIVE,
			   (char *)&on, sizeof(on));
	}

	return (sock);
}

static int ConnectToFloppyd(RemoteFile_t *floppyd, const char* name,
			    char *errmsg);

Stream_t *FloppydOpen(struct device *dev,
		      const char *name, int mode, char *errmsg,
		      mt_off_t *maxSize)
{
	RemoteFile_t *This;

	if (!dev ||  !(dev->misc_flags & FLOPPYD_FLAG))
		return NULL;

	This = New(RemoteFile_t);
	if (!This){
		printOom();
		return NULL;
	}
	init_head(&This->head, &FloppydFileClass, NULL);

	This->offset = 0;
	This->lastwhere = 0;

	This->fd = ConnectToFloppyd(This, name, errmsg);
	if (This->fd == -1) {
		Free(This);
		return NULL;
	}

	if(floppyd_open(This, mode) < 0) {
		sprintf(errmsg,
			"Can't open remote drive: %s", strerror(errno));
		close(This->fd);
		Free(This);
		return NULL;
	}

	if(maxSize) {
		*maxSize =
			((This->capabilities & FLOPPYD_CAP_LARGE_SEEK) ?
			 max_off_t_seek : max_off_t_31);
	}
	return &This->head;
}

static int ConnectToFloppyd(RemoteFile_t *floppyd, const char* name,
			    char *errmsg)
{
	char* hostname;
	char* display;
	uint16_t port;
	int rval = get_host_and_port_and_drive(name, &hostname, &display,
					       &port, &floppyd->drive);
	int sock;
	unsigned int reply;

	if (!rval) return -1;

	floppyd->version = FLOPPYD_PROTOCOL_VERSION;
	floppyd->capabilities = 0;
	while(1) {
		sock = connect_to_server(getipaddress(hostname), port);

		if (sock == -1) {
#ifdef HAVE_SNPRINTF
			snprintf(errmsg, 200,
				 "Can't connect to floppyd server on %s, port %i (%s)!",
				 hostname, port, strerror(errno));
#else
			sprintf(errmsg,
				 "Can't connect to floppyd server on %s, port %i!",
				 hostname, port);
#endif
			return -1;
		}

		reply = authenticate_to_floppyd(floppyd, sock, display);
		if(floppyd->version == FLOPPYD_PROTOCOL_VERSION_OLD)
			break;
		if(reply == AUTH_WRONGVERSION) {
			/* fall back on old version */
			floppyd->version = FLOPPYD_PROTOCOL_VERSION_OLD;
			continue;
		}
		break;
	}

	if (reply != 0) {
		fprintf(stderr,
			"Permission denied, authentication failed!\n"
			"%s\n", AuthErrors[reply]);
		return -1;
	}

	free(hostname);
	free(display);

	return sock;
}
