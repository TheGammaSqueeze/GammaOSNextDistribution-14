#!/usr/bin/perl
# Copyright (C) 2018 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

use strict;
use Getopt::Long;
use Pod::Usage;

=pod

=head1 DESCRIPTION

This script parses API violations from C<adb logcat>. Output is in CSV format
with columns C<package>, C<symbol>, C<list>.

The package name is mapped from a PID, parsed from the same log. To ensure you
get all packages names, you should process the logcat from device boot time.

=head1 SYNOPSIS

  adb logcat | perl find_api_violations.pl > violations.csv
  cat bugreport.txt | perl find_api_violations.pl --bugreport > violations.csv

=head1 OPTIONS

=over

=item --[no]unsupported

(Don't) show unsupported API accesses (default true)

=item --[no]max-target

(Don't) show APIs blocked by apps target SDK (default true)

=item --[no]blocked

(Don't) show blocked list accesses (default true)

=item --bugreport|-b

Process a bugreport, rather than raw logcat

=item --short|-s

Output API signatures only, don't include CSV header/package names/list name.

=item --help

=back

=cut

my $unsupported = 1;
my $maxtarget = 1;
my $blocked = 1;
my $bugreport = 0;
my $short = 0;
my $help = 0;

GetOptions("unsupported!" => \$unsupported,
           "max-target!"  => \$maxtarget,
           "blocked!"     => \$blocked,
           "bugreport|b"  => \$bugreport,
           "short|s"      => \$short,
           "help"         => \$help)
  or pod2usage(q(-verbose) => 1);

pod2usage(q(-verbose) => 2) if ($help);

my $in;

if ($bugreport) {
  my $found_main = 0;
  while (my $line = <>) {
    chomp $line;
    if ($line =~ m/^------ SYSTEM LOG /) {
      $found_main = 1;
      last;
    }
  }
  if (!$found_main) {
    die "Couldn't find main log in bugreport\n";
  }
}

my $procmap = {};
print "package,symbol,list\n" unless $short;
while (my $line = <>) {
  chomp $line;
  last if $bugreport and $line =~ m/^------ \d+\.\d+s was the duration of 'SYSTEM LOG' ------/;
  next if $line =~ m/^--------- beginning of/;
  unless ($line =~ m/^\d{2}-\d{2} \d{2}:\d{2}:\d{2}.\d{3}\s+(?:\w+\s+)?(\d+)\s+(\d+)\s+([VWIDE])\s+(.*?): (.*)$/) {
    die "Cannot match line: $line\n";
    next;
  }
  my ($pid, $tid, $class, $tag, $msg) = ($1, $2, $3, $4, $5);
  if ($tag eq "ActivityManager" && $msg =~ m/^Start proc (\d+):(.*?) for /) {
    my ($new_pid, $proc_name) = ($1, $2);
    my $package;
    if ($proc_name =~ m/^(.*?)(:.*?)?\/(.*)$/) {
      $package = $1;
    } else {
      $package = $proc_name;
    }
    $procmap->{$new_pid} = $package;
  }
  if ($msg =~ m/Accessing hidden (\w+) (L.*?) \((.*list), (.*?)\)/) {
    my ($member_type, $symbol, $list, $access_type) = ($1, $2, $3, $4);
    my $package = $procmap->{$pid} || "unknown($pid)";
    if (($list =~ m/unsupported/ && $unsupported)
      || ($list =~ m/max-target/ && $maxtarget)
      || ($list =~ m/blocked/ && $blocked)) {
      if ($short) {
        print "$symbol\n";
      } else {
        print "$package,$symbol,$list\n"
      }
    }
  }
}

