#!/usr/bin/perl
##
## Copyright 2019 The Android Open Source Project
##
## Licensed under the Apache License, Version 2.0 (the "License");
## you may not use this file except in compliance with the License.
## You may obtain a copy of the License at
##
##      http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software
## distributed under the License is distributed on an "AS IS" BASIS,
## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
## See the License for the specific language governing permissions and
## limitations under the License.

package Mockcify;

use diagnostics;
use strict;
use warnings;

use lib "$ENV{ANDROID_BUILD_TOP}/packages/modules/Bluetooth/system/test/tool";
require 'mockcify_util.pl';

printf("MOCKCIFY unit test\n");

"void" eq comment_out_input_vars("void")
    || die("FAIL file:",  __FILE__, " line:", __LINE__, "\n");
"one /* two */" eq comment_out_input_vars("one two")
    || die("FAIL file:",  __FILE__, " line:", __LINE__, "\n");
"one /* two */, three /* four */, five /* six */" eq comment_out_input_vars("one two, three four, five six")
    || die("FAIL file:",  __FILE__, " line:", __LINE__, "\n");
"std::string /* s */, tSOME_STRUCT /* struct */" eq comment_out_input_vars("std::string   s  ,   tSOME_STRUCT  struct")
    || die("FAIL file:",  __FILE__, " line:", __LINE__, "\n");
"const std::string& /* s */, tSOME_STRUCT /* struct */" eq comment_out_input_vars("   const   std::string&   s  ,   tSOME_STRUCT  struct")
    || die("FAIL file:",  __FILE__, " line:", __LINE__, "\n");

printf("SUCCESS\n");
