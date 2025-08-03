@echo off
rem
rem ==========================================================================
rem Copyright 2001-2023 The Apache Software Foundation.
rem
rem Licensed to the Apache Software Foundation (ASF) under one or more
rem contributor license agreements.  See the NOTICE file distributed with
rem this work for additional information regarding copyright ownership.
rem The ASF licenses this file to You under the Apache License, Version 2.0
rem (the "License"); you may not use this file except in compliance with
rem the License.  You may obtain a copy of the License at
rem
rem     http://www.apache.org/licenses/LICENSE-2.0
rem
rem Unless required by applicable law or agreed to in writing, software
rem distributed under the License is distributed on an "AS IS" BASIS,
rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
rem See the License for the specific language governing permissions and
rem limitations under the License.
rem ==========================================================================

rem     Author: mukulg@apache.org

rem Set JAVA_HOME environment variable, for the local environment

if "%JAVA_HOME%"=="" goto noJavaHome

set XALAN_BUILD_DIR_PATH=..\..\..\xalan-java\build;..\..\..\build

set XERCES_ENDORSED_DIR_PATH=..\..\..\xalan-java\lib\endorsed;..\..\..\lib\endorsed

rem #Test 1 (Testing XalanJ integer truncation bug fix, with XalanJ XSLTC processor)
if exist "int_trunc.class" (
  rem delete the result XalanJ translet file, if that exists
  del int_trunc.class
)

%JAVA_HOME%\bin\java -Djava.endorsed.dirs=%XALAN_BUILD_DIR_PATH%;%XERCES_ENDORSED_DIR_PATH% org.apache.xalan.xslt.Process -XSLTC -IN int_trunc.xml -XSL int_trunc.xsl -SECURE -XX -XT 2>NUL

if exist "int_trunc.class" (
    echo Test failed. Please solve this, before checking in! 
) else (
    echo The xalanj integer truncation bug fix test passed!
)

rem #Test 2 (Testing bug fix of the jira issue XALANJ-2584, with XalanJ interpretive processor)
%JAVA_HOME%\bin\java -Djava.endorsed.dirs=%XALAN_BUILD_DIR_PATH%;%XERCES_ENDORSED_DIR_PATH% org.apache.xalan.xslt.Process -IN jira_xalanj_2584.xml -XSL jira_xalanj_2584.xsl > jira_xalanj_2584.out 

%JAVA_HOME%\bin\java -Djava.endorsed.dirs=%XERCES_ENDORSED_DIR_PATH% -classpath ..\..\java\build\testxsl.jar org.apache.qetest.XMLParserTestDriver jira_xalanj_2584.out xalan_interpretive

rem #Test 3 (Testing bug fix of the jira issue XALANJ-2584, with XalanJ XSLTC processor)
%JAVA_HOME%\bin\java -Djava.endorsed.dirs=%XALAN_BUILD_DIR_PATH%;%XERCES_ENDORSED_DIR_PATH% org.apache.xalan.xslt.Process -XSLTC -IN jira_xalanj_2584.xml -XSL jira_xalanj_2584.xsl > jira_xalanj_2584.out 

%JAVA_HOME%\bin\java -Djava.endorsed.dirs=%XERCES_ENDORSED_DIR_PATH% -classpath ..\..\java\build\testxsl.jar org.apache.qetest.XMLParserTestDriver jira_xalanj_2584.out xalan_xsltc

del jira_xalanj_2584.out

rem #Test 4 (Testing bug fix of the jira issue XALANJ-2623, with XalanJ interpretive processor)
%JAVA_HOME%\bin\java -Djava.endorsed.dirs=%XALAN_BUILD_DIR_PATH%;%XERCES_ENDORSED_DIR_PATH% org.apache.xalan.xslt.Process -IN jira_xalanj_2623.xml -XSL jira_xalanj_2623.xsl > jira_xalanj_2623.out 

%JAVA_HOME%\bin\java -Djava.endorsed.dirs=%XERCES_ENDORSED_DIR_PATH% -classpath ..\..\java\build\testxsl.jar org.apache.qetest.XSValidationTestDriver jira_xalanj_2623.out jira_xalanj_2623.xsd xalan_interpretive

rem #Test 5 (Testing bug fix of the jira issue XALANJ-2623, with XalanJ XSLTC processor)
%JAVA_HOME%\bin\java -Djava.endorsed.dirs=%XALAN_BUILD_DIR_PATH%;%XERCES_ENDORSED_DIR_PATH% org.apache.xalan.xslt.Process -XSLTC -IN jira_xalanj_2623.xml -XSL jira_xalanj_2623.xsl > jira_xalanj_2623.out 

%JAVA_HOME%\bin\java -Djava.endorsed.dirs=%XERCES_ENDORSED_DIR_PATH% -classpath ..\..\java\build\testxsl.jar org.apache.qetest.XSValidationTestDriver jira_xalanj_2623.out jira_xalanj_2623.xsd xalan_xsltc

del jira_xalanj_2623.out

goto end

:noJavaHome
echo Warning: JAVA_HOME environment variable is not set

:end
set XALAN_BUILD_DIR_PATH=
set XERCES_ENDORSED_DIR_PATH= 