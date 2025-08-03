:: Copyright 2021 The Android Open Source Project
::
:: Licensed under the Apache License, Version 2.0 (the "License");
:: you may not use this file except in compliance with the License.
:: You may obtain a copy of the License at
::
::      http://www.apache.org/licenses/LICENSE-2.0
::
:: Unless required by applicable law or agreed to in writing, software
:: distributed under the License is distributed on an "AS IS" BASIS,
:: WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
:: See the License for the specific language governing permissions and
:: limitations under the License.

setlocal
set TOP=%~dp0..\..\..\..
set PYTHON_SRC=%~dp0..

:: Remove Cygwin from the PATH so we use native tools (e.g. native Git).
:: (It could leave Cygwin at the very end, but that's less of a problem.)
set PATH=%PATH:C:\cygwin64\bin;=%

:: The Kokoro image has two copies of MSBuild installed. Prefer the one in
:: C:\VS\MSBuild over the one in
:: "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\MSBuild"
:: See https://yaqs.corp.google.com/eng/q/8657098259782696960#a1.
if exist C:\VS\MSBuild\Current\Bin\amd64\MSBuild.exe (set "PATH=C:\VS\MSBuild\Current\Bin\amd64;%PATH%")

:: When we're actually running in the Kokoro environment, the git checkouts are
:: not owned by the current user, so when the Python build scripts query info
:: about them, git fails. Fix this by disabling git's safe directory checking.
IF DEFINED KOKORO_JOB_NAME (git config --global --add safe.directory *)

IF NOT DEFINED KOKORO_BUILD_ID (set KOKORO_BUILD_ID=dev)

:: Create the parent directories of the destination directory. Ordinarily, the
:: "md" invocation in build.cmd would automatically create these parent
:: directories, but it doesn't seem to work inside Kokoro's Docker-based Windows
:: VM, so create them manually instead. See http://b/278137784#comment2.
md "%TOP%\out" 2>NUL
md "%TOP%\out\python3" 2>NUL

call %~dp0build.cmd "%PYTHON_SRC%" "%TOP%\out\python3\artifact"
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

py -3 %TOP%\toolchain\ndk-kokoro\gen_manifest.py --root %TOP% ^
    -o %TOP%\out\python3\artifact\manifest-%KOKORO_BUILD_ID%.xml

exit /b %ERRORLEVEL%
