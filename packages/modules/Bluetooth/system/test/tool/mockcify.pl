#! /usr/bin/perl
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

use File::Basename;

## mockcify version
##
## 0.7.0 Comment out unused mock variables
##
## 0.6.3 Streamline inclusion for headers and source
##
## 0.6.2 Add tBTA_STATUS default value, Cpp type failure log
##
## 0.6.1 Add tBTA_SDP_STATUS default value
##
## 0.6.0 Replace `extern` with `include` for mock_function_count_map
##
## 0.5.0 Add compilation check
##
## 0.4.0 Second re-write
##
## 0.3.2 Remove pragma from source file
##
## 0.3.1 Statically link return value to prevent 'this' pointer in function
##
## 0.3.0 Re-write parser.
##
## 0.2.1 Compilation units only include types and a single related header file
##       Alphabetically sort functions by return value
##       Add non-primative return data values in structure
##
## 0.2.0 First version
##
my $VERSION = "0.7.0";

use diagnostics;
use strict;
use warnings;

use lib "$ENV{ANDROID_BUILD_TOP}/packages/modules/Bluetooth/system/test/tool";
require 'mockcify_util.pl';

my $YEAR = "2023";
my $TOKEN = "MOCKCIFY_TOKEN";
my $MOCKCIFY_BRACKET_GROUP = "MOCKCIFY_BRACKET_GROUP";
my $CLANG_FORMAT = "/usr/bin/clang-format-13";
my $CC = "g++";
my $LIBCHROME = "../../../../external/libchrome/";
my $COMPILE_SCREEN_ENABLED = 0;

my @structs;

my %function_signature;
my %function_return_types;
my @function_names;
my %function_params;
my %function_param_names;
my %function_param_types;

sub clang_format {
    return `$CLANG_FORMAT --style="{ColumnLimit: 10000, PointerAlignment: Left, PointerBindsToType: true, FixNamespaceComments: true }"`;
}

## Create a temp directory for any cruft
my $TMPDIR="/tmp/mockcify";
system("mkdir -p $TMPDIR");
my $OUTDIR = "$TMPDIR/out/";
system("mkdir -p $OUTDIR");
my $INCDIR = "$TMPDIR/include/";
system("mkdir -p $INCDIR");

if (scalar(@ARGV == 0)) {
  printf(STDERR "ERROR Must supply at least one argument\n");
  exit 1;
}

my $arg = shift @ARGV;
## Check only argument for debug vector
if ($arg =~ /--cla[ng]/) {
    exit print clang_format();
} elsif ($arg =~ /--f[ilter]/) {
    exit print read_stdin_and_filter_file();
} elsif ($arg =~ /--l[ines]/) {
    exit print filter_lines(read_stdin_and_filter_file());
} elsif ($arg =~ /--i[nfo]/) {
    my ($incs, $types, $funcs) = parse_info(filter_lines(read_stdin_and_filter_file()));
    exit print @{$incs}, @{$types}, @{$funcs};
} elsif ($arg =~ /--co[mpile]/) {
    exit compilation_screen("mock_" . shift @ARGV);
} elsif ($arg =~ /--cle[an]/) {
    exit system("mv $TMPDIR $TMPDIR.deleted");
} elsif ($arg =~ /--u[nittest]/) {
    print(STDERR "unit testing device");
}

sub help {
    print <<EOF
 Usage:
  Specify a namespace on the command line for the shared structure data.
  Then pipe the C file on stdin and one source and one header file will
  be created based upon the namespace convention

 mockcify.pl stack_l2cap_api < stack/l2cap/l2c_api.cc

 Output files:
  mock_stack_l2cap_api.cc
  mock_stack_l2cap_api.h

  The tool is not capable of parsing C++ and a workaround is to remove
  C++ in the source prior to mock-C-fying the source.

EOF
}

## Only single arg is taken
my $namespace = $arg;

if ($namespace =~ /^--/) {
    print(STDERR "ERROR Halting due to ill-formed namespace expression \'$namespace\'\n");
    exit -1;
}

###
### Phase 0: Prepare input and output file streams
###

## Default to stdout
my $FH_SRC;
my $FH_HDR;

my $src_filename;
my $hdr_filename;
## If namepace specified then write to that source and header
if ($namespace eq "TESTING") {
  $FH_SRC = *STDOUT;
  $FH_HDR = *STDOUT;
} else {
  $src_filename="mock_" . $namespace . ".cc";
  $hdr_filename="mock_" . $namespace . ".h";

  open($FH_SRC, ">", $OUTDIR .$src_filename)
    or die $!;

  open($FH_HDR, ">", $OUTDIR .$hdr_filename)
    or die $!;
}

###
### Phase 1: Read input file and apply single line filtering
###
my $text = read_stdin_and_filter_file();

###
### Phase 2: Apply Multiline filters
###
$text = filter_lines($text);

##
## Phase 3: Extract required mock information
##
my ($includes_ref, $typedefs_ref, $functions_ref, $usings_ref,  $namespaces_ref) = parse_info($text);
my @includes = @{$includes_ref};
my @typedefs = @{$typedefs_ref};
my @functions = @{$functions_ref};
my @namespaces = @{$namespaces_ref};
my @usings = @{$usings_ref};

@includes = reject_include_list(@includes);

@functions = grep { parse_function_into_components ($_) } @functions;

##
## Phase 4: Output the mocks source and header
##
print_source($FH_SRC);
print_header($FH_HDR);

close ($FH_SRC);
close ($FH_HDR);

## Format the final source code files
if (defined $src_filename) {
  system("clang-format", "-i", $OUTDIR . $src_filename);
  system("clang-format", "-i", $OUTDIR . $hdr_filename);
}

print(STDERR "Generated files:", $OUTDIR . $src_filename, " ", $OUTDIR . $hdr_filename, "\n");

if ($COMPILE_SCREEN_ENABLED) {
  my $rc = compilation_screen("mock_" . $namespace);
  exit ($rc == 256) ?1 : 0;
}

sub reject_include_list {
    my @incs = ();
    foreach (@_) {
      next if (/init_flags/);
      push(@incs, $_);
    }
    return @incs;
}

sub compile_screen_failed {
    my $src = shift @_;
    print STDERR <<EOF
    MOCK Compilation is EXPERIMENTAL ONLY

    ERROR Failed to compile \'$src\' NOTE: This does not mean
    the mock is unusable as the tool only screens the compilation.

    There could be one of 3 problems:
    1. Undeclared external surface or dependency
    2. C++ code or namespaces mixed in with C code
    3. An issue with proper mock'ing with mockcify.
EOF
}

sub compilation_screen {
    my $base= shift @_;
    my $src=$base . ".cc";
    my $hdr=$base . ".h";

    ## Verious external or generated header not needed for mocks
    foreach((
            "test/mock/mock.h",
            "src/init_flags.rs.h",
            "src/message_loop_thread.rs.h",
            "android/hardware/bluetooth/audio/2.2/IBluetoothAudioProvidersFactory.h",
            "android/hardware/bluetooth/audio/2.2/types.h",
        )) {
        system("mkdir -p $INCDIR". dirname($_));
        system("touch $INCDIR/$_");
    }
    my @incs = (
        $INCDIR,
        $LIBCHROME,
        ".",
        "audio_hal_interface/",
        "include/",
        "stack/include/",
        "btif/include/",
        "internal_include",
        "osi/include/",
        "test/mock/",
        "types/",
    );
    my @defs = (
        "HAS_NO_BDROID_BUILDCFG",
    );

    my $link="test/mock/$hdr";
    unlink "$INCDIR/$link";
    symlink "$OUTDIR/$hdr", "$INCDIR/$link";
    system("$CC -c -std=c++20 -o /dev/null -D" . join(" -D", @defs) . " -I" . join(" -I", @incs) . " $OUTDIR/$src");
    my $rc = $?;
         ($? == 0)
         ? printf(STDERR "SUCCESS Compiled unit \'$src\'\n")
         : compile_screen_failed($src);
    return $rc;
}

###
### Phase 4.1: Print the source compilation unit and the associated structues
###
sub print_source {
  my $FH = shift @_;
  print_copyright($FH);
  print_generated_note($FH);

  print_mock_header_include($FH);
  print_mock_decl_src($FH);
  print_usings($FH);
  print_internal_structs($FH);
  print_source_namespace_structs($FH);
  print_static_return_values($FH);
  print_mocked_functions($FH);

  print $FH "// END mockcify generation\n";
}

###
### Phase 4.2 Print the header unit to be included with the test
###
sub print_header {
  my $FH = shift @_;
  print_copyright($FH);
  print_pragma($FH);
  print_generated_note($FH);
  print_mock_decl_hdr($FH);

  print_includes($FH);
  print_usings($FH);
  print_defs($FH);
  print_header_test_mock_namespace_structs($FH);
  print $FH "// END mockcify generation";
}

sub get_function_param_names {
    my $name = shift @_;
    my @param_names;
    foreach (0..$#{$function_param_names{$name}}) {
        my $param_name = $function_param_names{$name}[$_];
        my $param_type = $function_param_types{$name}[$_];

        if (!defined($param_type)) {
          printf(STDERR "Unable to find param type def for $name\n");
          next;
        }
        if ($param_type =~ /unique_ptr/) {
            ## Wrap name in a move operation
            push(@param_names, "std::move($param_name)");
        } else {
            push(@param_names, $param_name);
        }
    }
    return join(',', @param_names);
}

##
## Parse a function signature into 4 basic components and insert into
## the global hashes and arrays.
##  1. @function return type
##  2. @function name
##  3. %param types
##  4. %param names
##
sub parse_function_into_components {
  my $function = shift @_;
  ## Ensure this is really a function string
  assert(substr $function, -1 eq ')');

  ## Split on first occurrence of open paren to get return
  ## type and name of function
  my ($return_type_and_name, $params) = split '\(', $function, 2;
  if (!defined($params)) {
      printf(STDERR "WARNING \'params\' is undefined \"$params\" function:\'$function\'\n");
      return 0;
  }
  ## Remove input params closing paren
  $params=~ s/\).*$//;

  ## Parse the return type and function name
  my ($return_type, $name) = $return_type_and_name =~ /(.*)\s(.*)/;

  if (!defined($name)) {
      printf(STDERR "WARNING \'name\' is undefined \"$return_type_and_name\" a [con|des]tructor ?\n");
      return 0;
  }
  if ($name =~ /::/) {
      printf(STDERR "WARNING \'name\' is unhandled class method \'$name\'\n");
      return 0;
  }

  ## Store away complete function signature
  $function_signature{$name} = $function;

  ## Store away the parameter type and names
  chomp($params);
  $function_params{$name} = $params;

  ## Parse the parameter types and names
  my @param_types;
  my @param_names;

  ## Skip when void keyword used for no parameters
  if ($params ne "void") {
    ## TODO Replace all comma types within angle brackets before split
    foreach (split ',', $params) {
      s/^\s+//;
      if (/\(/) {
        ## TODO Parameter is a C style function
        my @vars;
        my @f = split /[\(\)]/;
        push(@vars, substr $f[1], 1);
      } else {
        ## Store the type and name
        my ($type, $name) = /(.*)\s(.*)/;
        push(@param_names, $name);
        push(@param_types, $type);
      }
    }
  }
  push(@function_names, $name);
  $function_return_types{$name} = $return_type;
  $function_param_types{$name} = \@param_types;
  $function_param_names{$name} = \@param_names;
  return 1;
}

##
## Read a file from stdin and does a first pass simple
## filtering that removes single lines.
##
sub read_stdin_and_filter_file {
  my @filtered_lines;
  my @clang_format=clang_format();
  foreach (@clang_format) {
    ## Update header guards with compiler #pragma for proper
    ## decision processing of header or source
    s/^#ifndef [A-Z_0-9]+_H/#pragma once/;

    unless (/^extern/
        or /^#define /
        or / = \{/
        or /^#if /
        or /^constexpr/
        or /^#ifdef/
        or /^#ifndef/
        or /^#else/
        or /^enum/
        or /^static.*;$/
        or /^#endif/) {
        ## Remove any single line C style comments
        s:/\*.*\*/::;
        push(@filtered_lines, $_);
      }
  }
  return join('', @filtered_lines);
}

sub filter_lines {
  $_ = shift @_;
  ## Remove anonymous namespaces
  ## $text =~ s/namespace \{.*\n\} \/\/ namespace/\n/sg;
  s/namespace \{.*\n\} \/\/ namespace?/\n/sg;
  s/namespace \{.?\n\}/\n/g;
  ## Remove C style comments
  s/\s*\/\*(?:(?!\*\/).)*\*\/\n?/\n/sg;
  ## Remove Cpp style comments
  s/\s*\/\/.*//g;
  ## Remove unnecessary bluetooth osi specific modifier
  s/UNUSED_ATTR//g;
  ## Modify internally defined structure typedefs
  s/typedef struct \{.*?\n\} (\w+);/typedef struct $MOCKCIFY_BRACKET_GROUP $1;/sg;
  ## Modify internally defined structure typedefs
  s/typedef struct (\w+) \{.*?\n\} (\w+);/struct $1 $MOCKCIFY_BRACKET_GROUP;/sg;
  ## Modify internally defined structures
  s/struct (\w+) \{.*?\n\};/struct $1 $MOCKCIFY_BRACKET_GROUP;/sg;
  ## Remove lines only with spaces
  s/^\s+$//sg;
  return $_;
}

sub parse_info {
    if (/\n#pragma once\n/) {
        return parse_info_header(shift @_);
    } else {
        return parse_info_source(shift @_);
    }
}

sub parse_info_header {
  my (@includes, @typedefs, @functions, @usings, @namespaces);
  foreach (split('\n')) {
      chomp();
      if (/^ /) {
      } elsif (/^#include /) {
          push(@includes, $_);
      } elsif (/^typedef /) {
          push @typedefs, $_;
      } elsif ($_ =~ /^ *$/) {
          # Skip function body indicated by indentation
      } elsif ($_ =~ /^}/) {
          # Skip function curly bracket closure
      } elsif (/^namespace/) {
          push @namespaces, $_;
      } elsif (/\(/) {
          # Add function signature
          chomp();
          ## Remove all function body after signature
          s/{.*$//;
          ## Remove whitespace on both ends
          s/^\s+|\s+$//g;
          ## Ignore locally linked functions
          next if (/^static/);
          ## Reduce all remaining whitespace to a single space
          s/\s+/ /g;
          ## Remove any semi colons
          s/;//g;
          push(@functions, "$_\n");
      } else {
          # Not a function. skip
      }
  }
  printf(STDERR "Parsed HEADER lines includes:%d typedefs:%d functions:%d\n",
      scalar(@includes), scalar(@typedefs), scalar(@functions));
  return (\@includes, \@typedefs, \@functions, \@usings, \@namespaces);
}

sub parse_info_source{
  my @s = split('\n', $_);
  my (@includes, @typedefs, @functions, @usings, @namespaces);
  foreach (@s) {
      chomp();
      if (/^ /) {
      } elsif (/^#include /) {
          push @includes, $_;
      } elsif (/^typedef /) {
          push @typedefs, $_;
      } elsif (/^using /) {
          push @usings, $_;
      } elsif (/^namespace/) {
          push @namespaces, $_;
      } elsif ($_ =~ /^ *$/) {
          # Skip function body indicated by indentation
      } elsif ($_ =~ /^}/) {
          # Skip function curly bracket closure
        } elsif (/\{/) {
          # Add function signature
          chomp();
          ## Remove all function body after signature
          s/{.*$//;
          ## Remove whitespace on both ends
          s/^\s+|\s+$//g;
          ## Ignore locally linked functions
          next if (/^static/);
          ## Reduce all remaining whitespace to a single space
          s/\s+/ /g;
          push(@functions, "$_\n");
      } else {
          # Not a function. skip
      }
  }
  printf(STDERR "Parsed SOURCE lines includes:%d typedefs:%d functions:%d\n",
      scalar(@includes), scalar(@typedefs), scalar(@functions));
  return (\@includes, \@typedefs, \@functions, \@usings, \@namespaces);
}

## Returns the default type specified by the function return type.
## These are processed in priority order.
sub get_default_return_value_from_type {
  $_ = shift @_;
  assert($_ ne '');
  if (/^bool/) {
    return "false";
  } elsif (/\*$/ or /^std::unique_ptr/ or /^std::shared_ptr/) {  ## Pointer return val
    return "nullptr";
  } elsif (/^void/) {
    return "";
  } elsif (/^std::string/) {
    return "std::string()";
  } elsif (/^std::list\<entry_t\>::iterator/) {
    return "static std::list<entry_t> v";
  } elsif (/^std::list\<section_t\>::iterator/) {
    return "std::list<section_t>";
  } elsif (/reactor_status_t/) {
    return "REACTOR_STATUS_DONE";
  } elsif (/tL2CAP_LE_RESULT_CODE/) {
    return "L2CAP_LE_RESULT_CONN_OK";
  } elsif (/std::vector/) {
    return "retval";
  } elsif (/tBT_TRANSPORT/) {
    return "BT_TRANSPORT_BR_EDR";
  } elsif (/tSDP_STATUS/) {
    return "SDP_SUCCESS";
  } elsif (/tGATT_STATUS/) {
    return "GATT_SUCCESS";
  } elsif (/tHID_STATUS/) {
    return "HID_SUCCESS";
  } elsif (/future_t\*/) {
    return "FUTURE_FAIL";
  } elsif(/bt_status_t/) {
    return "BT_STATUS_SUCCESS";
  } elsif(/.*module_t\*/) {
    return "nullptr";
  } elsif(/btav_a2dp_codec_index_t/) {
    return "BTAV_A2DP_CODEC_INDEX_SOURCE_MIN";
  } elsif(/tBTA_SDP_STATUS/) {
    return "BTA_SDP_SUCCESS";
  } elsif(/tBTA_STATUS/) {
    return "BTA_SUCCESS";
  } else {
    ## Decay to int type
    return "0";
  }
}

##
## Various print output boilerplate
###
sub print_copyright {
  my $FH = shift @_;
print $FH <<EOF
/*
 * Copyright $YEAR The Android Open Source Project
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
EOF
}

## Print body of each function
sub print_mocked_functions {
  my $FH = shift @_;
  print $FH <<EOF;
// Mocked functions, if any
EOF
  foreach my $name (sort @function_names) {
      my $return_type = $function_return_types{$name};
      assert($return_type ne '');

      my $return_keyword = $return_type eq "void" ? "" : "return";
      my $function_param_names = get_function_param_names($name);

      print $FH <<EOF;
$function_signature{$name} {
    inc_func_call_count(__func__);
    ${return_keyword} test::mock::${namespace}::${name}($function_param_names);
}
EOF
  }
  print $FH <<EOF;
// Mocked functions complete
EOF
}

sub print_static_return_values {
  my $FH = shift @_;
  print $FH <<EOF;
// Mocked function return values, if any
namespace test {
namespace mock {
namespace $namespace {

EOF
  foreach my $name (sort @function_names) {
      $name =~ s/\s+$//;
      my $return_type = $function_return_types{$name};
      assert($return_type ne '');

      next if ($return_type eq "void");
      my $default_return_value = get_default_return_value_from_type($return_type);
      print $FH "${return_type} ${name}::return_value = ${default_return_value};\n";
  }
  print $FH <<EOF;

} // namespace $namespace
} // namespace mock
} // namespace test

EOF
}

##
## Collection of mocked functions
sub print_source_namespace_structs {
  my $FH = shift @_;
  print $FH <<EOF;
namespace test {
namespace mock {
namespace $namespace {

// Function state capture and return values, if needed
EOF
    foreach my $name (sort @function_names) {
      print $FH "struct $name $name;\n";
    }
    print $FH <<EOF;

} // namespace $namespace
} // namespace mock
} // namespace test

EOF
}

##
##  Print the definitions of the various structures for the header files
##
sub print_header_test_mock_namespace_structs {
  my $FH = shift @_;
  print $FH <<EOF;
namespace test {
namespace mock {
namespace $namespace {

// Shared state between mocked functions and tests
EOF
  foreach my $name (sort @function_names) {
      my $input_params = $function_params{$name};
      my $vars_commented_out_input_params = comment_out_input_vars($input_params);
      my $return_type = $function_return_types{$name};
      my @param_names = $function_param_names{$name};
      assert($return_type ne '');

      my $function_param_names = get_function_param_names($name);
      my $return_keyword = $return_type eq "void" ? "" : "return";
      my $return_statement = $return_type eq "void" ? "" : "return return_value;";
      my $return_definition = $return_type eq "void" ? "" : "static $return_type return_value;";

print $FH <<EOF;
// Name: $name
// Params: $input_params
// Return: $return_type
struct $name {
EOF
       if ($return_definition ne "") {
           print $FH "$return_definition\n";
       }
print $FH <<EOF;
    std::function<$return_type($input_params)> body{[]($vars_commented_out_input_params){$return_statement}};
    $return_type operator()($input_params) { ${return_keyword} body($function_param_names);};
};
extern struct $name $name;

EOF
    }
print $FH <<EOF;
} // namespace $namespace
} // namespace mock
} // namespace test

EOF
}

sub print_pragma {
  my $FH = shift @_;
print $FH <<EOF
#pragma once

EOF
}

sub print_generated_note {
  my $FH = shift @_;
  my $gen = scalar(@functions);
print $FH <<EOF;
/*
 * Generated mock file from original source file
 *   Functions generated:$gen
 *
 *  mockcify.pl ver $VERSION
 */

EOF
}

sub print_usings {
  my $FH = shift @_;
print $FH <<EOF;
// Original usings
EOF
  foreach (sort @usings) {
    print $FH $_, "\n";
  }
  print($FH "\n");;
}

sub print_includes {
  my $FH = shift @_;
  print $FH <<EOF;
// Original included files, if any
// NOTE: Since this is a mock file with mock definitions some number of
//       include files may not be required.  The include-what-you-use
//       still applies, but crafting proper inclusion is out of scope
//       for this effort.  This compilation unit may compile as-is, or
//       may need attention to prune from (or add to ) the inclusion set.
EOF
  foreach (sort @includes) {
    print $FH $_, "\n";
  }
  print($FH "\n");;
}

sub print_mock_header_include {
  my $FH = shift @_;
  print $FH <<EOF;
// Mock include file to share data between tests and mock
#include "test/mock/mock_${namespace}.h"

EOF
}

sub print_mock_decl_hdr {
  my $FH = shift @_;
print $FH <<EOF;
#include <cstdint>
#include <functional>

EOF
}

sub print_mock_decl_src {
  my $FH = shift @_;
print $FH <<EOF;
#include <cstdint>

#include "test/common/mock_functions.h"

EOF
}

sub print_defs {
  my $FH = shift @_;
  print $FH <<EOF;
// Mocked compile conditionals, if any

EOF
}

sub print_internal_structs {
  my $FH = shift @_;
  print $FH <<EOF;
// Mocked internal structures, if any
EOF

  foreach (sort @structs) {
    print $FH $_,"\n"};
  print $FH "\n";
}

sub assert {
    my ($condition, $msg) = @_;
    return if $condition;
    if (!$msg) {
        my ($pkg, $file, $line) = caller(0);
        open my $fh, "<", $file;
        my @lines = <$fh>;
        close $fh;
        $msg = "$file:$line: " . $lines[$line - 1];
    }
    die "Assertion failed: $msg";
}
