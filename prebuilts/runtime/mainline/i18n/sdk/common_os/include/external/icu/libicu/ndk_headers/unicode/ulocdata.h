// © 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
******************************************************************************
*                                                                            *
* Copyright (C) 2003-2015, International Business Machines                   *
*                Corporation and others. All Rights Reserved.                *
*                                                                            *
******************************************************************************
*   file name:  ulocdata.h
*   encoding:   UTF-8
*   tab size:   8 (not used)
*   indentation:4
*
*   created on: 2003Oct21
*   created by: Ram Viswanadha
*/

#ifndef __ULOCDATA_H__
#define __ULOCDATA_H__

#include "unicode/uloc.h"




















/**
 * Return the current CLDR version used by the library.
 * @param versionArray fillin that will recieve the version number
 * @param status error code - could be U_MISSING_RESOURCE_ERROR if the version was not found.
 * @stable ICU 4.2
 */
U_CAPI void U_EXPORT2
ulocdata_getCLDRVersion(UVersionInfo versionArray, UErrorCode *status) __INTRODUCED_IN(31);







#endif
