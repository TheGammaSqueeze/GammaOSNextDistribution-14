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

:: Expected arguments:
:: %1 = python_src
:: %2 = dest_dir

setlocal
set PYTHON_SRC=%1
set DEST=%2

cd %PYTHON_SRC%
rmdir /s/q %DEST% 2>NUL
md %DEST%
if %ERRORLEVEL% neq 0 goto :end

:: Deletes Android.bp or it will be packaged.
del Lib\Android.bp
if %ERRORLEVEL% neq 0 goto :end

echo ## Running prepare_windows_externals.py to configure XML files and build libffi...
py -3 %PYTHON_SRC%\kokoro\prepare_windows_externals.py
if %ERRORLEVEL% neq 0 goto :end

:: Find msbuild.exe. (The batch file sets %MSBUILD%.)
call %PYTHON_SRC%\PCbuild\find_msbuild.bat
if %ERRORLEVEL% neq 0 goto :end

:: Avoid calling PCbuild\build.bat directly because that script either downloads externals or
:: disables them, and we need to specify their locations. The batch file mostly reduces to this one
:: msbuild invocation.
echo ## Building python...
%MSBUILD% "%PYTHON_SRC%\PCbuild\pcbuild.proj" ^
  -target:Build -maxCpuCount -nologo -verbosity:minimal -clp:summary ^
  -property:Configuration=Release ^
  -property:Platform=x64 ^
  -property:IncludeExternals=true ^
  -property:IncludeCTypes=true ^
  -property:IncludeSSL=false ^
  -property:IncludeTkinter=false ^
  -property:UseTestMarker= ^
  -property:GIT=git.exe ^
  -property:PythonForBuild=python.exe
if %ERRORLEVEL% neq 0 goto :end

echo ## Packaging python...
set OUT_ARCHIVE=%DEST%\python3-windows-%KOKORO_BUILD_ID%.zip
PCBuild\amd64\python.exe PC\layout --zip %OUT_ARCHIVE% --include-dev
if %ERRORLEVEL% neq 0 goto :end

:: Linux/Darwin Python puts the LICENSE.txt file in lib/pythonX.Y with the Python libraries. For
:: consistency, move LICENSE.txt into Lib, which has the Python libraries on Windows.
echo ## Moving LICENSE.txt to Lib\LICENSE.txt...
7z rn %OUT_ARCHIVE% LICENSE.txt Lib\LICENSE.txt

:end
exit /b %ERRORLEVEL%
