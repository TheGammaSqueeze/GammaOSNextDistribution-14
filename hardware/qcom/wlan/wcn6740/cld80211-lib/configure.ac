# -*- Autoconf -*-
# configure.ac -- Autoconf script for qcacld-tools
#

AC_PREREQ(2.61)
AC_INIT([cld80211-lib], 1.0.0)
AM_INIT_AUTOMAKE([foreign])
AM_MAINTAINER_MODE
AC_CONFIG_HEADERS([config.h])
AC_CONFIG_MACRO_DIR([m4])
LT_INIT

# Checks for programs.
AC_PROG_CC
AM_PROG_CC_C_O
AC_PROG_LIBTOOL
AC_PROG_AWK
AC_PROG_CPP
AC_PROG_INSTALL
AC_PROG_LN_S
AC_PROG_MAKE_SET
PKG_PROG_PKG_CONFIG

AC_ARG_ENABLE([debug],
        [  --enable-debug    Turn on debugging],
        [case "${enableval}" in
            yes) debug=true ;;
            no)  debug=false ;;
            *) AC_MSG_ERROR([bad value ${enableval} for --enable-debug]) ;;
        esac],[debug=false])
AM_CONDITIONAL([DEBUG], [test x$debug = xtrue])

has_libnl_ver=0
# libnl-2 provides only libnl-2.0.pc file, so we check for separate libnl-genl-3.0.pc
# pkg-config file just for libnl-3.0 case.
#
PKG_CHECK_MODULES([LIBNL], [libnl-3.0 >= 3.0 libnl-genl-3.0 >= 3.0], [has_libnl_ver=3], [
   PKG_CHECK_MODULES([LIBNL], [libnl-2.0 >= 2.0], [has_libnl_ver=2], [
           PKG_CHECK_MODULES([LIBNL], [libnl-1], [has_libnl_ver=1], [has_libnl_ver=0])])])

if (test "$has_libnl_ver" -eq 0); then
        AC_MSG_ERROR(libnl and libnl-genl are required but were not found)
fi

if (test "$has_libnl_ver" -gt 1); then
        AC_DEFINE([HAVE_LIBNL20], [1], [Define if you have libnl-2.0 or higher])
fi

if (test "$has_libnl_ver" -gt 2); then
        AC_DEFINE([HAVE_LIBNL30], [1], [Define if you have libnl-3.0 or higher])
fi

AC_SUBST([LIBNL_CFLAGS])
AC_SUBST([LIBNL_LIBS])

# Checks for typedefs, structures, and compiler characteristics.
AC_HEADER_STDBOOL
AC_HEADER_STDC
AC_C_INLINE
AC_TYPE_INT64_T
AC_TYPE_PID_T
AC_TYPE_SIZE_T
AC_TYPE_SSIZE_T
AC_TYPE_UINT16_T
AC_TYPE_UINT32_T
AC_TYPE_UINT8_T

# Checks for library functions.
AC_FUNC_ERROR_AT_LINE
AC_FUNC_FORK
AC_FUNC_MALLOC

AC_CONFIG_FILES([ \
    Makefile \
    cld80211-lib.pc
])
AC_OUTPUT
