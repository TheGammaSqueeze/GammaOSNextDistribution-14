##
## Copyright 2023 The Android Open Source Project
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

use diagnostics;
use strict;
use warnings;

##
## Take a string of parameters in and return the parameter name commented out
##
## e.g.
## int a, char b, std::string c => int /* a */, char /* b */, std::string /* c */
##
sub comment_out_input_vars {
    my $input_param_string = shift @_;
    my @return_param_string;
    my @params = split /,/, $input_param_string;
    foreach (@params) {
        ## Trim leading and trailing space
        s/^\s+|\s+$//g;
        ## Reduce multiple internal spaces to single space
        s/\s\+/ /g;
        my @w = split /\s+/, $_;
        my $s;
        if ($#w != 0) {
            chomp($w[$#w]);
            $w[$#w] = "/* $w[$#w] */";
        }
        $s .= join " ", @w;
        push(@return_param_string, $s);
      }
      return join(', ', @return_param_string);
}

1;
