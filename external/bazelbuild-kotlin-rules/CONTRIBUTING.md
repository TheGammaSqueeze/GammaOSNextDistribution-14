<!--
 Copyright 2022 Google LLC. All rights reserved.

 Licensed under the Apache License, Version 2.0 (the License);
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

Want to contribute? Great! First, read this page (including the small print at
the end).

### Before you contribute
**Before we can use your code, you must sign the
[Google Individual Contributor License Agreement](https://developers.google.com/open-source/cla/individual?csw=1)
(CLA)**, which you can do online.

The CLA is necessary mainly because you own the copyright to your changes,
even after your contribution becomes part of our codebase, so we need your
permission to use and distribute your code. We also need to be sure of
various other things — for instance that you'll tell us if you know that
your code infringes on other people's patents. You don't have to sign
the CLA until after you've submitted your code for review and a member has
approved it, but you must do it before we can put your code into our codebase.

### The small print
Contributions made by corporations are covered by a different agreement than
the one above, the
[Software Grant and Corporate Contributor License Agreement](https://cla.developers.google.com/about/google-corporate).

### Contribution process

1. Explain your idea and discuss your plan with members of the team. The best
   way to do this is to create
   an [issue](https://github.com/bazelbuild/rules_kotlin/issues) or comment on
   an existing issue.
1. Prepare a git commit with your change. Don't forget to
   add [tests](https://github.com/bazelbuild/rules_kotlin/tree/main/tests).
   Run the existing tests with `bazel test //...`. Update
   [README.md](https://github.com/bazelbuild/rules_kotlin/blob/main/README.md)
   if appropriate.
1. [Create a pull request](https://help.github.com/articles/creating-a-pull-request/).
   This will start the code review process. **All submissions, including
   submissions by project members, require review.**
1. You may be asked to make some changes. You'll also need to sign the CLA at
   this point, if you haven't done so already. Our continuous integration bots
   will test your change automatically on supported platforms. Once everything
   looks good, your change will be merged.
