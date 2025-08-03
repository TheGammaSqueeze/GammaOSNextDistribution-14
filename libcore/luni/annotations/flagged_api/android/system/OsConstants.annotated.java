/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package android.system;

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class OsConstants {

OsConstants() { throw new RuntimeException("Stub!"); }

public static boolean S_ISBLK(int mode) { throw new RuntimeException("Stub!"); }

public static boolean S_ISCHR(int mode) { throw new RuntimeException("Stub!"); }

public static boolean S_ISDIR(int mode) { throw new RuntimeException("Stub!"); }

public static boolean S_ISFIFO(int mode) { throw new RuntimeException("Stub!"); }

public static boolean S_ISREG(int mode) { throw new RuntimeException("Stub!"); }

public static boolean S_ISLNK(int mode) { throw new RuntimeException("Stub!"); }

public static boolean S_ISSOCK(int mode) { throw new RuntimeException("Stub!"); }

public static int WEXITSTATUS(int status) { throw new RuntimeException("Stub!"); }

public static boolean WCOREDUMP(int status) { throw new RuntimeException("Stub!"); }

public static int WTERMSIG(int status) { throw new RuntimeException("Stub!"); }

public static int WSTOPSIG(int status) { throw new RuntimeException("Stub!"); }

public static boolean WIFEXITED(int status) { throw new RuntimeException("Stub!"); }

public static boolean WIFSTOPPED(int status) { throw new RuntimeException("Stub!"); }

public static boolean WIFSIGNALED(int status) { throw new RuntimeException("Stub!"); }

public static String gaiName(int error) { throw new RuntimeException("Stub!"); }

public static String errnoName(int errno) { throw new RuntimeException("Stub!"); }

public static final int AF_INET;
static { AF_INET = 0; }

public static final int AF_INET6;
static { AF_INET6 = 0; }

public static final int AF_NETLINK;
static { AF_NETLINK = 0; }

public static final int AF_PACKET;
static { AF_PACKET = 0; }

public static final int AF_UNIX;
static { AF_UNIX = 0; }

public static final int AF_UNSPEC;
static { AF_UNSPEC = 0; }

public static final int AF_VSOCK;
static { AF_VSOCK = 0; }

public static final int AI_ADDRCONFIG;
static { AI_ADDRCONFIG = 0; }

public static final int AI_ALL;
static { AI_ALL = 0; }

public static final int AI_CANONNAME;
static { AI_CANONNAME = 0; }

public static final int AI_NUMERICHOST;
static { AI_NUMERICHOST = 0; }

public static final int AI_NUMERICSERV;
static { AI_NUMERICSERV = 0; }

public static final int AI_PASSIVE;
static { AI_PASSIVE = 0; }

public static final int AI_V4MAPPED;
static { AI_V4MAPPED = 0; }

public static final int ARPHRD_ETHER;
static { ARPHRD_ETHER = 0; }

public static final int CAP_AUDIT_CONTROL;
static { CAP_AUDIT_CONTROL = 0; }

public static final int CAP_AUDIT_WRITE;
static { CAP_AUDIT_WRITE = 0; }

public static final int CAP_BLOCK_SUSPEND;
static { CAP_BLOCK_SUSPEND = 0; }

public static final int CAP_CHOWN;
static { CAP_CHOWN = 0; }

public static final int CAP_DAC_OVERRIDE;
static { CAP_DAC_OVERRIDE = 0; }

public static final int CAP_DAC_READ_SEARCH;
static { CAP_DAC_READ_SEARCH = 0; }

public static final int CAP_FOWNER;
static { CAP_FOWNER = 0; }

public static final int CAP_FSETID;
static { CAP_FSETID = 0; }

public static final int CAP_IPC_LOCK;
static { CAP_IPC_LOCK = 0; }

public static final int CAP_IPC_OWNER;
static { CAP_IPC_OWNER = 0; }

public static final int CAP_KILL;
static { CAP_KILL = 0; }

public static final int CAP_LAST_CAP;
static { CAP_LAST_CAP = 0; }

public static final int CAP_LEASE;
static { CAP_LEASE = 0; }

public static final int CAP_LINUX_IMMUTABLE;
static { CAP_LINUX_IMMUTABLE = 0; }

public static final int CAP_MAC_ADMIN;
static { CAP_MAC_ADMIN = 0; }

public static final int CAP_MAC_OVERRIDE;
static { CAP_MAC_OVERRIDE = 0; }

public static final int CAP_MKNOD;
static { CAP_MKNOD = 0; }

public static final int CAP_NET_ADMIN;
static { CAP_NET_ADMIN = 0; }

public static final int CAP_NET_BIND_SERVICE;
static { CAP_NET_BIND_SERVICE = 0; }

public static final int CAP_NET_BROADCAST;
static { CAP_NET_BROADCAST = 0; }

public static final int CAP_NET_RAW;
static { CAP_NET_RAW = 0; }

public static final int CAP_SETFCAP;
static { CAP_SETFCAP = 0; }

public static final int CAP_SETGID;
static { CAP_SETGID = 0; }

public static final int CAP_SETPCAP;
static { CAP_SETPCAP = 0; }

public static final int CAP_SETUID;
static { CAP_SETUID = 0; }

public static final int CAP_SYSLOG;
static { CAP_SYSLOG = 0; }

public static final int CAP_SYS_ADMIN;
static { CAP_SYS_ADMIN = 0; }

public static final int CAP_SYS_BOOT;
static { CAP_SYS_BOOT = 0; }

public static final int CAP_SYS_CHROOT;
static { CAP_SYS_CHROOT = 0; }

public static final int CAP_SYS_MODULE;
static { CAP_SYS_MODULE = 0; }

public static final int CAP_SYS_NICE;
static { CAP_SYS_NICE = 0; }

public static final int CAP_SYS_PACCT;
static { CAP_SYS_PACCT = 0; }

public static final int CAP_SYS_PTRACE;
static { CAP_SYS_PTRACE = 0; }

public static final int CAP_SYS_RAWIO;
static { CAP_SYS_RAWIO = 0; }

public static final int CAP_SYS_RESOURCE;
static { CAP_SYS_RESOURCE = 0; }

public static final int CAP_SYS_TIME;
static { CAP_SYS_TIME = 0; }

public static final int CAP_SYS_TTY_CONFIG;
static { CAP_SYS_TTY_CONFIG = 0; }

public static final int CAP_WAKE_ALARM;
static { CAP_WAKE_ALARM = 0; }

public static final int E2BIG;
static { E2BIG = 0; }

public static final int EACCES;
static { EACCES = 0; }

public static final int EADDRINUSE;
static { EADDRINUSE = 0; }

public static final int EADDRNOTAVAIL;
static { EADDRNOTAVAIL = 0; }

public static final int EAFNOSUPPORT;
static { EAFNOSUPPORT = 0; }

public static final int EAGAIN;
static { EAGAIN = 0; }

public static final int EAI_AGAIN;
static { EAI_AGAIN = 0; }

public static final int EAI_BADFLAGS;
static { EAI_BADFLAGS = 0; }

public static final int EAI_FAIL;
static { EAI_FAIL = 0; }

public static final int EAI_FAMILY;
static { EAI_FAMILY = 0; }

public static final int EAI_MEMORY;
static { EAI_MEMORY = 0; }

public static final int EAI_NODATA;
static { EAI_NODATA = 0; }

public static final int EAI_NONAME;
static { EAI_NONAME = 0; }

public static final int EAI_OVERFLOW;
static { EAI_OVERFLOW = 0; }

public static final int EAI_SERVICE;
static { EAI_SERVICE = 0; }

public static final int EAI_SOCKTYPE;
static { EAI_SOCKTYPE = 0; }

public static final int EAI_SYSTEM;
static { EAI_SYSTEM = 0; }

public static final int EALREADY;
static { EALREADY = 0; }

public static final int EBADF;
static { EBADF = 0; }

public static final int EBADMSG;
static { EBADMSG = 0; }

public static final int EBUSY;
static { EBUSY = 0; }

public static final int ECANCELED;
static { ECANCELED = 0; }

public static final int ECHILD;
static { ECHILD = 0; }

public static final int ECONNABORTED;
static { ECONNABORTED = 0; }

public static final int ECONNREFUSED;
static { ECONNREFUSED = 0; }

public static final int ECONNRESET;
static { ECONNRESET = 0; }

public static final int EDEADLK;
static { EDEADLK = 0; }

public static final int EDESTADDRREQ;
static { EDESTADDRREQ = 0; }

public static final int EDOM;
static { EDOM = 0; }

public static final int EDQUOT;
static { EDQUOT = 0; }

public static final int EEXIST;
static { EEXIST = 0; }

public static final int EFAULT;
static { EFAULT = 0; }

public static final int EFBIG;
static { EFBIG = 0; }

public static final int EHOSTUNREACH;
static { EHOSTUNREACH = 0; }

public static final int EIDRM;
static { EIDRM = 0; }

public static final int EILSEQ;
static { EILSEQ = 0; }

public static final int EINPROGRESS;
static { EINPROGRESS = 0; }

public static final int EINTR;
static { EINTR = 0; }

public static final int EINVAL;
static { EINVAL = 0; }

public static final int EIO;
static { EIO = 0; }

public static final int EISCONN;
static { EISCONN = 0; }

public static final int EISDIR;
static { EISDIR = 0; }

public static final int ELOOP;
static { ELOOP = 0; }

public static final int EMFILE;
static { EMFILE = 0; }

public static final int EMLINK;
static { EMLINK = 0; }

public static final int EMSGSIZE;
static { EMSGSIZE = 0; }

public static final int EMULTIHOP;
static { EMULTIHOP = 0; }

public static final int ENAMETOOLONG;
static { ENAMETOOLONG = 0; }

public static final int ENETDOWN;
static { ENETDOWN = 0; }

public static final int ENETRESET;
static { ENETRESET = 0; }

public static final int ENETUNREACH;
static { ENETUNREACH = 0; }

public static final int ENFILE;
static { ENFILE = 0; }

public static final int ENOBUFS;
static { ENOBUFS = 0; }

public static final int ENODATA;
static { ENODATA = 0; }

public static final int ENODEV;
static { ENODEV = 0; }

public static final int ENOENT;
static { ENOENT = 0; }

public static final int ENOEXEC;
static { ENOEXEC = 0; }

public static final int ENOLCK;
static { ENOLCK = 0; }

public static final int ENOLINK;
static { ENOLINK = 0; }

public static final int ENOMEM;
static { ENOMEM = 0; }

public static final int ENOMSG;
static { ENOMSG = 0; }

public static final int ENONET;
static { ENONET = 0; }

public static final int ENOPROTOOPT;
static { ENOPROTOOPT = 0; }

public static final int ENOSPC;
static { ENOSPC = 0; }

public static final int ENOSR;
static { ENOSR = 0; }

public static final int ENOSTR;
static { ENOSTR = 0; }

public static final int ENOSYS;
static { ENOSYS = 0; }

public static final int ENOTCONN;
static { ENOTCONN = 0; }

public static final int ENOTDIR;
static { ENOTDIR = 0; }

public static final int ENOTEMPTY;
static { ENOTEMPTY = 0; }

public static final int ENOTSOCK;
static { ENOTSOCK = 0; }

public static final int ENOTSUP;
static { ENOTSUP = 0; }

public static final int ENOTTY;
static { ENOTTY = 0; }

public static final int ENXIO;
static { ENXIO = 0; }

public static final int EOPNOTSUPP;
static { EOPNOTSUPP = 0; }

public static final int EOVERFLOW;
static { EOVERFLOW = 0; }

public static final int EPERM;
static { EPERM = 0; }

public static final int EPIPE;
static { EPIPE = 0; }

public static final int EPROTO;
static { EPROTO = 0; }

public static final int EPROTONOSUPPORT;
static { EPROTONOSUPPORT = 0; }

public static final int EPROTOTYPE;
static { EPROTOTYPE = 0; }

public static final int ERANGE;
static { ERANGE = 0; }

public static final int EROFS;
static { EROFS = 0; }

public static final int ESPIPE;
static { ESPIPE = 0; }

public static final int ESRCH;
static { ESRCH = 0; }

public static final int ESTALE;
static { ESTALE = 0; }

public static final int ETH_P_ALL;
static { ETH_P_ALL = 0; }

public static final int ETH_P_ARP;
static { ETH_P_ARP = 0; }

public static final int ETH_P_IP;
static { ETH_P_IP = 0; }

public static final int ETH_P_IPV6;
static { ETH_P_IPV6 = 0; }

public static final int ETIME;
static { ETIME = 0; }

public static final int ETIMEDOUT;
static { ETIMEDOUT = 0; }

public static final int ETXTBSY;
static { ETXTBSY = 0; }

public static final int EXDEV;
static { EXDEV = 0; }

public static final int EXIT_FAILURE;
static { EXIT_FAILURE = 0; }

public static final int EXIT_SUCCESS;
static { EXIT_SUCCESS = 0; }

public static final int FD_CLOEXEC;
static { FD_CLOEXEC = 0; }

public static final int FIONREAD;
static { FIONREAD = 0; }

public static final int F_DUPFD;
static { F_DUPFD = 0; }

public static final int F_DUPFD_CLOEXEC;
static { F_DUPFD_CLOEXEC = 0; }

public static final int F_GETFD;
static { F_GETFD = 0; }

public static final int F_GETFL;
static { F_GETFL = 0; }

public static final int F_GETLK;
static { F_GETLK = 0; }

public static final int F_GETLK64;
static { F_GETLK64 = 0; }

public static final int F_GETOWN;
static { F_GETOWN = 0; }

public static final int F_OK;
static { F_OK = 0; }

public static final int F_RDLCK;
static { F_RDLCK = 0; }

public static final int F_SETFD;
static { F_SETFD = 0; }

public static final int F_SETFL;
static { F_SETFL = 0; }

public static final int F_SETLK;
static { F_SETLK = 0; }

public static final int F_SETLK64;
static { F_SETLK64 = 0; }

public static final int F_SETLKW;
static { F_SETLKW = 0; }

public static final int F_SETLKW64;
static { F_SETLKW64 = 0; }

public static final int F_SETOWN;
static { F_SETOWN = 0; }

public static final int F_UNLCK;
static { F_UNLCK = 0; }

public static final int F_WRLCK;
static { F_WRLCK = 0; }

public static final int ICMP6_ECHO_REPLY;
static { ICMP6_ECHO_REPLY = 0; }

public static final int ICMP6_ECHO_REQUEST;
static { ICMP6_ECHO_REQUEST = 0; }

public static final int ICMP_ECHO;
static { ICMP_ECHO = 0; }

public static final int ICMP_ECHOREPLY;
static { ICMP_ECHOREPLY = 0; }

public static final int IFA_F_DADFAILED;
static { IFA_F_DADFAILED = 0; }

public static final int IFA_F_DEPRECATED;
static { IFA_F_DEPRECATED = 0; }

public static final int IFA_F_HOMEADDRESS;
static { IFA_F_HOMEADDRESS = 0; }

public static final int IFA_F_MANAGETEMPADDR;
static { IFA_F_MANAGETEMPADDR = 0; }

public static final int IFA_F_NODAD;
static { IFA_F_NODAD = 0; }

public static final int IFA_F_NOPREFIXROUTE;
static { IFA_F_NOPREFIXROUTE = 0; }

public static final int IFA_F_OPTIMISTIC;
static { IFA_F_OPTIMISTIC = 0; }

public static final int IFA_F_PERMANENT;
static { IFA_F_PERMANENT = 0; }

public static final int IFA_F_SECONDARY;
static { IFA_F_SECONDARY = 0; }

public static final int IFA_F_TEMPORARY;
static { IFA_F_TEMPORARY = 0; }

public static final int IFA_F_TENTATIVE;
static { IFA_F_TENTATIVE = 0; }

public static final int IFF_ALLMULTI;
static { IFF_ALLMULTI = 0; }

public static final int IFF_AUTOMEDIA;
static { IFF_AUTOMEDIA = 0; }

public static final int IFF_BROADCAST;
static { IFF_BROADCAST = 0; }

public static final int IFF_DEBUG;
static { IFF_DEBUG = 0; }

public static final int IFF_DYNAMIC;
static { IFF_DYNAMIC = 0; }

public static final int IFF_LOOPBACK;
static { IFF_LOOPBACK = 0; }

public static final int IFF_MASTER;
static { IFF_MASTER = 0; }

public static final int IFF_MULTICAST;
static { IFF_MULTICAST = 0; }

public static final int IFF_NOARP;
static { IFF_NOARP = 0; }

public static final int IFF_NOTRAILERS;
static { IFF_NOTRAILERS = 0; }

public static final int IFF_POINTOPOINT;
static { IFF_POINTOPOINT = 0; }

public static final int IFF_PORTSEL;
static { IFF_PORTSEL = 0; }

public static final int IFF_PROMISC;
static { IFF_PROMISC = 0; }

public static final int IFF_RUNNING;
static { IFF_RUNNING = 0; }

public static final int IFF_SLAVE;
static { IFF_SLAVE = 0; }

public static final int IFF_UP;
static { IFF_UP = 0; }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static final int IPPROTO_ESP;
static { IPPROTO_ESP = 0; }

public static final int IPPROTO_ICMP;
static { IPPROTO_ICMP = 0; }

public static final int IPPROTO_ICMPV6;
static { IPPROTO_ICMPV6 = 0; }

public static final int IPPROTO_IP;
static { IPPROTO_IP = 0; }

public static final int IPPROTO_IPV6;
static { IPPROTO_IPV6 = 0; }

public static final int IPPROTO_RAW;
static { IPPROTO_RAW = 0; }

public static final int IPPROTO_TCP;
static { IPPROTO_TCP = 0; }

public static final int IPPROTO_UDP;
static { IPPROTO_UDP = 0; }

public static final int IPV6_CHECKSUM;
static { IPV6_CHECKSUM = 0; }

public static final int IPV6_MULTICAST_HOPS;
static { IPV6_MULTICAST_HOPS = 0; }

public static final int IPV6_MULTICAST_IF;
static { IPV6_MULTICAST_IF = 0; }

public static final int IPV6_MULTICAST_LOOP;
static { IPV6_MULTICAST_LOOP = 0; }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static final int IPV6_PKTINFO;
static { IPV6_PKTINFO = 0; }

public static final int IPV6_RECVDSTOPTS;
static { IPV6_RECVDSTOPTS = 0; }

public static final int IPV6_RECVHOPLIMIT;
static { IPV6_RECVHOPLIMIT = 0; }

public static final int IPV6_RECVHOPOPTS;
static { IPV6_RECVHOPOPTS = 0; }

public static final int IPV6_RECVPKTINFO;
static { IPV6_RECVPKTINFO = 0; }

public static final int IPV6_RECVRTHDR;
static { IPV6_RECVRTHDR = 0; }

public static final int IPV6_RECVTCLASS;
static { IPV6_RECVTCLASS = 0; }

public static final int IPV6_TCLASS;
static { IPV6_TCLASS = 0; }

public static final int IPV6_UNICAST_HOPS;
static { IPV6_UNICAST_HOPS = 0; }

public static final int IPV6_V6ONLY;
static { IPV6_V6ONLY = 0; }

public static final int IP_MULTICAST_IF;
static { IP_MULTICAST_IF = 0; }

public static final int IP_MULTICAST_LOOP;
static { IP_MULTICAST_LOOP = 0; }

public static final int IP_MULTICAST_TTL;
static { IP_MULTICAST_TTL = 0; }

public static final int IP_TOS;
static { IP_TOS = 0; }

public static final int IP_TTL;
static { IP_TTL = 0; }

public static final int MAP_ANONYMOUS;
static { MAP_ANONYMOUS = 0; }

public static final int MAP_FIXED;
static { MAP_FIXED = 0; }

public static final int MAP_PRIVATE;
static { MAP_PRIVATE = 0; }

public static final int MAP_SHARED;
static { MAP_SHARED = 0; }

public static final int MCAST_BLOCK_SOURCE;
static { MCAST_BLOCK_SOURCE = 0; }

public static final int MCAST_JOIN_GROUP;
static { MCAST_JOIN_GROUP = 0; }

public static final int MCAST_JOIN_SOURCE_GROUP;
static { MCAST_JOIN_SOURCE_GROUP = 0; }

public static final int MCAST_LEAVE_GROUP;
static { MCAST_LEAVE_GROUP = 0; }

public static final int MCAST_LEAVE_SOURCE_GROUP;
static { MCAST_LEAVE_SOURCE_GROUP = 0; }

public static final int MCAST_UNBLOCK_SOURCE;
static { MCAST_UNBLOCK_SOURCE = 0; }

public static final int MCL_CURRENT;
static { MCL_CURRENT = 0; }

public static final int MCL_FUTURE;
static { MCL_FUTURE = 0; }

public static final int MFD_CLOEXEC;
static { MFD_CLOEXEC = 0; }

public static final int MSG_CTRUNC;
static { MSG_CTRUNC = 0; }

public static final int MSG_DONTROUTE;
static { MSG_DONTROUTE = 0; }

public static final int MSG_EOR;
static { MSG_EOR = 0; }

public static final int MSG_OOB;
static { MSG_OOB = 0; }

public static final int MSG_PEEK;
static { MSG_PEEK = 0; }

public static final int MSG_TRUNC;
static { MSG_TRUNC = 0; }

public static final int MSG_WAITALL;
static { MSG_WAITALL = 0; }

public static final int MS_ASYNC;
static { MS_ASYNC = 0; }

public static final int MS_INVALIDATE;
static { MS_INVALIDATE = 0; }

public static final int MS_SYNC;
static { MS_SYNC = 0; }

public static final int NETLINK_INET_DIAG;
static { NETLINK_INET_DIAG = 0; }

public static final int NETLINK_NETFILTER;
static { NETLINK_NETFILTER = 0; }

public static final int NETLINK_ROUTE;
static { NETLINK_ROUTE = 0; }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static final int NETLINK_XFRM;
static { NETLINK_XFRM = 0; }

public static final int NI_DGRAM;
static { NI_DGRAM = 0; }

public static final int NI_NAMEREQD;
static { NI_NAMEREQD = 0; }

public static final int NI_NOFQDN;
static { NI_NOFQDN = 0; }

public static final int NI_NUMERICHOST;
static { NI_NUMERICHOST = 0; }

public static final int NI_NUMERICSERV;
static { NI_NUMERICSERV = 0; }

public static final int O_ACCMODE;
static { O_ACCMODE = 0; }

public static final int O_APPEND;
static { O_APPEND = 0; }

public static final int O_CLOEXEC;
static { O_CLOEXEC = 0; }

public static final int O_CREAT;
static { O_CREAT = 0; }

public static final int O_DSYNC;
static { O_DSYNC = 0; }

public static final int O_EXCL;
static { O_EXCL = 0; }

public static final int O_NOCTTY;
static { O_NOCTTY = 0; }

public static final int O_NOFOLLOW;
static { O_NOFOLLOW = 0; }

public static final int O_NONBLOCK;
static { O_NONBLOCK = 0; }

public static final int O_RDONLY;
static { O_RDONLY = 0; }

public static final int O_RDWR;
static { O_RDWR = 0; }

public static final int O_SYNC;
static { O_SYNC = 0; }

public static final int O_TRUNC;
static { O_TRUNC = 0; }

public static final int O_WRONLY;
static { O_WRONLY = 0; }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static final int PACKET_IGNORE_OUTGOING;
static { PACKET_IGNORE_OUTGOING = 0; }

public static final int POLLERR;
static { POLLERR = 0; }

public static final int POLLHUP;
static { POLLHUP = 0; }

public static final int POLLIN;
static { POLLIN = 0; }

public static final int POLLNVAL;
static { POLLNVAL = 0; }

public static final int POLLOUT;
static { POLLOUT = 0; }

public static final int POLLPRI;
static { POLLPRI = 0; }

public static final int POLLRDBAND;
static { POLLRDBAND = 0; }

public static final int POLLRDNORM;
static { POLLRDNORM = 0; }

public static final int POLLWRBAND;
static { POLLWRBAND = 0; }

public static final int POLLWRNORM;
static { POLLWRNORM = 0; }

public static final int PROT_EXEC;
static { PROT_EXEC = 0; }

public static final int PROT_NONE;
static { PROT_NONE = 0; }

public static final int PROT_READ;
static { PROT_READ = 0; }

public static final int PROT_WRITE;
static { PROT_WRITE = 0; }

public static final int PR_GET_DUMPABLE;
static { PR_GET_DUMPABLE = 0; }

public static final int PR_SET_DUMPABLE;
static { PR_SET_DUMPABLE = 0; }

public static final int PR_SET_NO_NEW_PRIVS;
static { PR_SET_NO_NEW_PRIVS = 0; }

public static final int RTMGRP_NEIGH;
static { RTMGRP_NEIGH = 0; }

public static final int RT_SCOPE_HOST;
static { RT_SCOPE_HOST = 0; }

public static final int RT_SCOPE_LINK;
static { RT_SCOPE_LINK = 0; }

public static final int RT_SCOPE_NOWHERE;
static { RT_SCOPE_NOWHERE = 0; }

public static final int RT_SCOPE_SITE;
static { RT_SCOPE_SITE = 0; }

public static final int RT_SCOPE_UNIVERSE;
static { RT_SCOPE_UNIVERSE = 0; }

public static final int R_OK;
static { R_OK = 0; }

public static final int SEEK_CUR;
static { SEEK_CUR = 0; }

public static final int SEEK_END;
static { SEEK_END = 0; }

public static final int SEEK_SET;
static { SEEK_SET = 0; }

public static final int SHUT_RD;
static { SHUT_RD = 0; }

public static final int SHUT_RDWR;
static { SHUT_RDWR = 0; }

public static final int SHUT_WR;
static { SHUT_WR = 0; }

public static final int SIGABRT;
static { SIGABRT = 0; }

public static final int SIGALRM;
static { SIGALRM = 0; }

public static final int SIGBUS;
static { SIGBUS = 0; }

public static final int SIGCHLD;
static { SIGCHLD = 0; }

public static final int SIGCONT;
static { SIGCONT = 0; }

public static final int SIGFPE;
static { SIGFPE = 0; }

public static final int SIGHUP;
static { SIGHUP = 0; }

public static final int SIGILL;
static { SIGILL = 0; }

public static final int SIGINT;
static { SIGINT = 0; }

public static final int SIGIO;
static { SIGIO = 0; }

public static final int SIGKILL;
static { SIGKILL = 0; }

public static final int SIGPIPE;
static { SIGPIPE = 0; }

public static final int SIGPROF;
static { SIGPROF = 0; }

public static final int SIGPWR;
static { SIGPWR = 0; }

public static final int SIGQUIT;
static { SIGQUIT = 0; }

public static final int SIGRTMAX;
static { SIGRTMAX = 0; }

public static final int SIGRTMIN;
static { SIGRTMIN = 0; }

public static final int SIGSEGV;
static { SIGSEGV = 0; }

public static final int SIGSTKFLT;
static { SIGSTKFLT = 0; }

public static final int SIGSTOP;
static { SIGSTOP = 0; }

public static final int SIGSYS;
static { SIGSYS = 0; }

public static final int SIGTERM;
static { SIGTERM = 0; }

public static final int SIGTRAP;
static { SIGTRAP = 0; }

public static final int SIGTSTP;
static { SIGTSTP = 0; }

public static final int SIGTTIN;
static { SIGTTIN = 0; }

public static final int SIGTTOU;
static { SIGTTOU = 0; }

public static final int SIGURG;
static { SIGURG = 0; }

public static final int SIGUSR1;
static { SIGUSR1 = 0; }

public static final int SIGUSR2;
static { SIGUSR2 = 0; }

public static final int SIGVTALRM;
static { SIGVTALRM = 0; }

public static final int SIGWINCH;
static { SIGWINCH = 0; }

public static final int SIGXCPU;
static { SIGXCPU = 0; }

public static final int SIGXFSZ;
static { SIGXFSZ = 0; }

public static final int SIOCGIFADDR;
static { SIOCGIFADDR = 0; }

public static final int SIOCGIFBRDADDR;
static { SIOCGIFBRDADDR = 0; }

public static final int SIOCGIFDSTADDR;
static { SIOCGIFDSTADDR = 0; }

public static final int SIOCGIFNETMASK;
static { SIOCGIFNETMASK = 0; }

public static final int SOCK_CLOEXEC;
static { SOCK_CLOEXEC = 0; }

public static final int SOCK_DGRAM;
static { SOCK_DGRAM = 0; }

public static final int SOCK_NONBLOCK;
static { SOCK_NONBLOCK = 0; }

public static final int SOCK_RAW;
static { SOCK_RAW = 0; }

public static final int SOCK_SEQPACKET;
static { SOCK_SEQPACKET = 0; }

public static final int SOCK_STREAM;
static { SOCK_STREAM = 0; }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static final int SOL_PACKET;
static { SOL_PACKET = 0; }

public static final int SOL_SOCKET;
static { SOL_SOCKET = 0; }

public static final int SOL_UDP;
static { SOL_UDP = 0; }

public static final int SO_BINDTODEVICE;
static { SO_BINDTODEVICE = 0; }

public static final int SO_BROADCAST;
static { SO_BROADCAST = 0; }

public static final int SO_DEBUG;
static { SO_DEBUG = 0; }

public static final int SO_DONTROUTE;
static { SO_DONTROUTE = 0; }

public static final int SO_ERROR;
static { SO_ERROR = 0; }

public static final int SO_KEEPALIVE;
static { SO_KEEPALIVE = 0; }

public static final int SO_LINGER;
static { SO_LINGER = 0; }

public static final int SO_OOBINLINE;
static { SO_OOBINLINE = 0; }

public static final int SO_PASSCRED;
static { SO_PASSCRED = 0; }

public static final int SO_PEERCRED;
static { SO_PEERCRED = 0; }

public static final int SO_RCVBUF;
static { SO_RCVBUF = 0; }

public static final int SO_RCVLOWAT;
static { SO_RCVLOWAT = 0; }

public static final int SO_RCVTIMEO;
static { SO_RCVTIMEO = 0; }

public static final int SO_REUSEADDR;
static { SO_REUSEADDR = 0; }

public static final int SO_SNDBUF;
static { SO_SNDBUF = 0; }

public static final int SO_SNDLOWAT;
static { SO_SNDLOWAT = 0; }

public static final int SO_SNDTIMEO;
static { SO_SNDTIMEO = 0; }

public static final int SO_TYPE;
static { SO_TYPE = 0; }

public static final int STDERR_FILENO;
static { STDERR_FILENO = 0; }

public static final int STDIN_FILENO;
static { STDIN_FILENO = 0; }

public static final int STDOUT_FILENO;
static { STDOUT_FILENO = 0; }

public static final int ST_MANDLOCK;
static { ST_MANDLOCK = 0; }

public static final int ST_NOATIME;
static { ST_NOATIME = 0; }

public static final int ST_NODEV;
static { ST_NODEV = 0; }

public static final int ST_NODIRATIME;
static { ST_NODIRATIME = 0; }

public static final int ST_NOEXEC;
static { ST_NOEXEC = 0; }

public static final int ST_NOSUID;
static { ST_NOSUID = 0; }

public static final int ST_RDONLY;
static { ST_RDONLY = 0; }

public static final int ST_RELATIME;
static { ST_RELATIME = 0; }

public static final int ST_SYNCHRONOUS;
static { ST_SYNCHRONOUS = 0; }

public static final int S_IFBLK;
static { S_IFBLK = 0; }

public static final int S_IFCHR;
static { S_IFCHR = 0; }

public static final int S_IFDIR;
static { S_IFDIR = 0; }

public static final int S_IFIFO;
static { S_IFIFO = 0; }

public static final int S_IFLNK;
static { S_IFLNK = 0; }

public static final int S_IFMT;
static { S_IFMT = 0; }

public static final int S_IFREG;
static { S_IFREG = 0; }

public static final int S_IFSOCK;
static { S_IFSOCK = 0; }

public static final int S_IRGRP;
static { S_IRGRP = 0; }

public static final int S_IROTH;
static { S_IROTH = 0; }

public static final int S_IRUSR;
static { S_IRUSR = 0; }

public static final int S_IRWXG;
static { S_IRWXG = 0; }

public static final int S_IRWXO;
static { S_IRWXO = 0; }

public static final int S_IRWXU;
static { S_IRWXU = 0; }

public static final int S_ISGID;
static { S_ISGID = 0; }

public static final int S_ISUID;
static { S_ISUID = 0; }

public static final int S_ISVTX;
static { S_ISVTX = 0; }

public static final int S_IWGRP;
static { S_IWGRP = 0; }

public static final int S_IWOTH;
static { S_IWOTH = 0; }

public static final int S_IWUSR;
static { S_IWUSR = 0; }

public static final int S_IXGRP;
static { S_IXGRP = 0; }

public static final int S_IXOTH;
static { S_IXOTH = 0; }

public static final int S_IXUSR;
static { S_IXUSR = 0; }

public static final int TCP_NODELAY;
static { TCP_NODELAY = 0; }

public static final int TCP_USER_TIMEOUT;
static { TCP_USER_TIMEOUT = 0; }

public static final int UDP_GRO;
static { UDP_GRO = 0; }

public static final int UDP_SEGMENT;
static { UDP_SEGMENT = 0; }

public static final int VMADDR_CID_ANY;
static { VMADDR_CID_ANY = 0; }

public static final int VMADDR_CID_HOST;
static { VMADDR_CID_HOST = 0; }

public static final int VMADDR_CID_LOCAL;
static { VMADDR_CID_LOCAL = 0; }

public static final int VMADDR_PORT_ANY;
static { VMADDR_PORT_ANY = 0; }

public static final int WCONTINUED;
static { WCONTINUED = 0; }

public static final int WEXITED;
static { WEXITED = 0; }

public static final int WNOHANG;
static { WNOHANG = 0; }

public static final int WNOWAIT;
static { WNOWAIT = 0; }

public static final int WSTOPPED;
static { WSTOPPED = 0; }

public static final int WUNTRACED;
static { WUNTRACED = 0; }

public static final int W_OK;
static { W_OK = 0; }

public static final int X_OK;
static { X_OK = 0; }

public static final int _SC_2_CHAR_TERM;
static { _SC_2_CHAR_TERM = 0; }

public static final int _SC_2_C_BIND;
static { _SC_2_C_BIND = 0; }

public static final int _SC_2_C_DEV;
static { _SC_2_C_DEV = 0; }

public static final int _SC_2_C_VERSION;
static { _SC_2_C_VERSION = 0; }

public static final int _SC_2_FORT_DEV;
static { _SC_2_FORT_DEV = 0; }

public static final int _SC_2_FORT_RUN;
static { _SC_2_FORT_RUN = 0; }

public static final int _SC_2_LOCALEDEF;
static { _SC_2_LOCALEDEF = 0; }

public static final int _SC_2_SW_DEV;
static { _SC_2_SW_DEV = 0; }

public static final int _SC_2_UPE;
static { _SC_2_UPE = 0; }

public static final int _SC_2_VERSION;
static { _SC_2_VERSION = 0; }

public static final int _SC_AIO_LISTIO_MAX;
static { _SC_AIO_LISTIO_MAX = 0; }

public static final int _SC_AIO_MAX;
static { _SC_AIO_MAX = 0; }

public static final int _SC_AIO_PRIO_DELTA_MAX;
static { _SC_AIO_PRIO_DELTA_MAX = 0; }

public static final int _SC_ARG_MAX;
static { _SC_ARG_MAX = 0; }

public static final int _SC_ASYNCHRONOUS_IO;
static { _SC_ASYNCHRONOUS_IO = 0; }

public static final int _SC_ATEXIT_MAX;
static { _SC_ATEXIT_MAX = 0; }

public static final int _SC_AVPHYS_PAGES;
static { _SC_AVPHYS_PAGES = 0; }

public static final int _SC_BC_BASE_MAX;
static { _SC_BC_BASE_MAX = 0; }

public static final int _SC_BC_DIM_MAX;
static { _SC_BC_DIM_MAX = 0; }

public static final int _SC_BC_SCALE_MAX;
static { _SC_BC_SCALE_MAX = 0; }

public static final int _SC_BC_STRING_MAX;
static { _SC_BC_STRING_MAX = 0; }

public static final int _SC_CHILD_MAX;
static { _SC_CHILD_MAX = 0; }

public static final int _SC_CLK_TCK;
static { _SC_CLK_TCK = 0; }

public static final int _SC_COLL_WEIGHTS_MAX;
static { _SC_COLL_WEIGHTS_MAX = 0; }

public static final int _SC_DELAYTIMER_MAX;
static { _SC_DELAYTIMER_MAX = 0; }

public static final int _SC_EXPR_NEST_MAX;
static { _SC_EXPR_NEST_MAX = 0; }

public static final int _SC_FSYNC;
static { _SC_FSYNC = 0; }

public static final int _SC_GETGR_R_SIZE_MAX;
static { _SC_GETGR_R_SIZE_MAX = 0; }

public static final int _SC_GETPW_R_SIZE_MAX;
static { _SC_GETPW_R_SIZE_MAX = 0; }

public static final int _SC_IOV_MAX;
static { _SC_IOV_MAX = 0; }

public static final int _SC_JOB_CONTROL;
static { _SC_JOB_CONTROL = 0; }

public static final int _SC_LINE_MAX;
static { _SC_LINE_MAX = 0; }

public static final int _SC_LOGIN_NAME_MAX;
static { _SC_LOGIN_NAME_MAX = 0; }

public static final int _SC_MAPPED_FILES;
static { _SC_MAPPED_FILES = 0; }

public static final int _SC_MEMLOCK;
static { _SC_MEMLOCK = 0; }

public static final int _SC_MEMLOCK_RANGE;
static { _SC_MEMLOCK_RANGE = 0; }

public static final int _SC_MEMORY_PROTECTION;
static { _SC_MEMORY_PROTECTION = 0; }

public static final int _SC_MESSAGE_PASSING;
static { _SC_MESSAGE_PASSING = 0; }

public static final int _SC_MQ_OPEN_MAX;
static { _SC_MQ_OPEN_MAX = 0; }

public static final int _SC_MQ_PRIO_MAX;
static { _SC_MQ_PRIO_MAX = 0; }

public static final int _SC_NGROUPS_MAX;
static { _SC_NGROUPS_MAX = 0; }

public static final int _SC_NPROCESSORS_CONF;
static { _SC_NPROCESSORS_CONF = 0; }

public static final int _SC_NPROCESSORS_ONLN;
static { _SC_NPROCESSORS_ONLN = 0; }

public static final int _SC_OPEN_MAX;
static { _SC_OPEN_MAX = 0; }

public static final int _SC_PAGESIZE;
static { _SC_PAGESIZE = 0; }

public static final int _SC_PAGE_SIZE;
static { _SC_PAGE_SIZE = 0; }

public static final int _SC_PASS_MAX;
static { _SC_PASS_MAX = 0; }

public static final int _SC_PHYS_PAGES;
static { _SC_PHYS_PAGES = 0; }

public static final int _SC_PRIORITIZED_IO;
static { _SC_PRIORITIZED_IO = 0; }

public static final int _SC_PRIORITY_SCHEDULING;
static { _SC_PRIORITY_SCHEDULING = 0; }

public static final int _SC_REALTIME_SIGNALS;
static { _SC_REALTIME_SIGNALS = 0; }

public static final int _SC_RE_DUP_MAX;
static { _SC_RE_DUP_MAX = 0; }

public static final int _SC_RTSIG_MAX;
static { _SC_RTSIG_MAX = 0; }

public static final int _SC_SAVED_IDS;
static { _SC_SAVED_IDS = 0; }

public static final int _SC_SEMAPHORES;
static { _SC_SEMAPHORES = 0; }

public static final int _SC_SEM_NSEMS_MAX;
static { _SC_SEM_NSEMS_MAX = 0; }

public static final int _SC_SEM_VALUE_MAX;
static { _SC_SEM_VALUE_MAX = 0; }

public static final int _SC_SHARED_MEMORY_OBJECTS;
static { _SC_SHARED_MEMORY_OBJECTS = 0; }

public static final int _SC_SIGQUEUE_MAX;
static { _SC_SIGQUEUE_MAX = 0; }

public static final int _SC_STREAM_MAX;
static { _SC_STREAM_MAX = 0; }

public static final int _SC_SYNCHRONIZED_IO;
static { _SC_SYNCHRONIZED_IO = 0; }

public static final int _SC_THREADS;
static { _SC_THREADS = 0; }

public static final int _SC_THREAD_ATTR_STACKADDR;
static { _SC_THREAD_ATTR_STACKADDR = 0; }

public static final int _SC_THREAD_ATTR_STACKSIZE;
static { _SC_THREAD_ATTR_STACKSIZE = 0; }

public static final int _SC_THREAD_DESTRUCTOR_ITERATIONS;
static { _SC_THREAD_DESTRUCTOR_ITERATIONS = 0; }

public static final int _SC_THREAD_KEYS_MAX;
static { _SC_THREAD_KEYS_MAX = 0; }

public static final int _SC_THREAD_PRIORITY_SCHEDULING;
static { _SC_THREAD_PRIORITY_SCHEDULING = 0; }

public static final int _SC_THREAD_PRIO_INHERIT;
static { _SC_THREAD_PRIO_INHERIT = 0; }

public static final int _SC_THREAD_PRIO_PROTECT;
static { _SC_THREAD_PRIO_PROTECT = 0; }

public static final int _SC_THREAD_SAFE_FUNCTIONS;
static { _SC_THREAD_SAFE_FUNCTIONS = 0; }

public static final int _SC_THREAD_STACK_MIN;
static { _SC_THREAD_STACK_MIN = 0; }

public static final int _SC_THREAD_THREADS_MAX;
static { _SC_THREAD_THREADS_MAX = 0; }

public static final int _SC_TIMERS;
static { _SC_TIMERS = 0; }

public static final int _SC_TIMER_MAX;
static { _SC_TIMER_MAX = 0; }

public static final int _SC_TTY_NAME_MAX;
static { _SC_TTY_NAME_MAX = 0; }

public static final int _SC_TZNAME_MAX;
static { _SC_TZNAME_MAX = 0; }

public static final int _SC_VERSION;
static { _SC_VERSION = 0; }

public static final int _SC_XBS5_ILP32_OFF32;
static { _SC_XBS5_ILP32_OFF32 = 0; }

public static final int _SC_XBS5_ILP32_OFFBIG;
static { _SC_XBS5_ILP32_OFFBIG = 0; }

public static final int _SC_XBS5_LP64_OFF64;
static { _SC_XBS5_LP64_OFF64 = 0; }

public static final int _SC_XBS5_LPBIG_OFFBIG;
static { _SC_XBS5_LPBIG_OFFBIG = 0; }

public static final int _SC_XOPEN_CRYPT;
static { _SC_XOPEN_CRYPT = 0; }

public static final int _SC_XOPEN_ENH_I18N;
static { _SC_XOPEN_ENH_I18N = 0; }

public static final int _SC_XOPEN_LEGACY;
static { _SC_XOPEN_LEGACY = 0; }

public static final int _SC_XOPEN_REALTIME;
static { _SC_XOPEN_REALTIME = 0; }

public static final int _SC_XOPEN_REALTIME_THREADS;
static { _SC_XOPEN_REALTIME_THREADS = 0; }

public static final int _SC_XOPEN_SHM;
static { _SC_XOPEN_SHM = 0; }

public static final int _SC_XOPEN_UNIX;
static { _SC_XOPEN_UNIX = 0; }

public static final int _SC_XOPEN_VERSION;
static { _SC_XOPEN_VERSION = 0; }

public static final int _SC_XOPEN_XCU_VERSION;
static { _SC_XOPEN_XCU_VERSION = 0; }
}

