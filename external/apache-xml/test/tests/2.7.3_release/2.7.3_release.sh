#!/bin/sh
#
#=========================================================================
# Copyright 2001-2023 The Apache Software Foundation.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#=========================================================================
#
#	Author: mukulg@apache.org

#	Setup:
#         You must set JAVA_HOME, for example,
#         $ export JAVA_HOME=/etc/alternatives/java_sdk

if [ "$JAVA_HOME" = "" ]; then 
    echo Warning: JAVA_HOME environment variable is not exported
    echo You may have meant to set it to /etc/alternatives/java_sdk
    exit 1
fi

JAVACMD=$JAVA_HOME/bin/java

CLASSPATH=../../java/build/testxsl.jar

XALAN_BUILD_DIR_PATH=../../../xalan-java/build:../../../build

XERCES_ENDORSED_DIR_PATH=../../../xalan-java/lib/endorsed:../../../lib/endorsed

TEST_XSL_DIR=../../java/build:../../../java/build

#Test 1 (Testing XalanJ integer truncation bug fix, with XalanJ XSLTC processor)
if [ -f "int_trunc.class" ]; then
  # delete the result XalanJ translet file, if that exists
  rm int_trunc.class
fi

$JAVACMD -Djava.endorsed.dirs=$XALAN_BUILD_DIR_PATH:$XERCES_ENDORSED_DIR_PATH org.apache.xalan.xslt.Process -XSLTC -IN int_trunc.xml -XSL int_trunc.xsl -SECURE -XX -XT 2>NUL

if [ -f "int_trunc.class" ]; then
    echo Test failed. Please solve this, before checking in! 
else
    echo The xalanj integer truncation bug fix test passed!
fi

#Test 2 (Testing bug fix of the jira issue XALANJ-2584, with XalanJ interpretive processor)
$JAVACMD -Djava.endorsed.dirs=$XALAN_BUILD_DIR_PATH:$XERCES_ENDORSED_DIR_PATH org.apache.xalan.xslt.Process -IN jira_xalanj_2584.xml -XSL jira_xalanj_2584.xsl > jira_xalanj_2584.out 

$JAVACMD -Djava.endorsed.dirs=$XERCES_ENDORSED_DIR_PATH -classpath $TEST_XSL_DIR/testxsl.jar org.apache.qetest.XMLParserTestDriver jira_xalanj_2584.out xalan_interpretive

#Test 3 (Testing bug fix of the jira issue XALANJ-2584, with XalanJ XSLTC processor)
$JAVACMD -Djava.endorsed.dirs=$XALAN_BUILD_DIR_PATH:$XERCES_ENDORSED_DIR_PATH org.apache.xalan.xslt.Process -XSLTC -IN jira_xalanj_2584.xml -XSL jira_xalanj_2584.xsl > jira_xalanj_2584.out 

$JAVACMD -Djava.endorsed.dirs=$XERCES_ENDORSED_DIR_PATH -classpath $TEST_XSL_DIR/testxsl.jar org.apache.qetest.XMLParserTestDriver jira_xalanj_2584.out xalan_xsltc

rm jira_xalanj_2584.out

#Test 4 (Testing bug fix of the jira issue XALANJ-2623, with XalanJ interpretive processor)
$JAVACMD -Djava.endorsed.dirs=$XALAN_BUILD_DIR_PATH:$XERCES_ENDORSED_DIR_PATH org.apache.xalan.xslt.Process -IN jira_xalanj_2623.xml -XSL jira_xalanj_2623.xsl > jira_xalanj_2623.out 

$JAVACMD -Djava.endorsed.dirs=$XERCES_ENDORSED_DIR_PATH -classpath $TEST_XSL_DIR/testxsl.jar org.apache.qetest.XSValidationTestDriver jira_xalanj_2623.out jira_xalanj_2623.xsd xalan_interpretive

#Test 5 (Testing bug fix of the jira issue XALANJ-2623, with XalanJ XSLTC processor)
$JAVACMD -Djava.endorsed.dirs=$XALAN_BUILD_DIR_PATH:$XERCES_ENDORSED_DIR_PATH org.apache.xalan.xslt.Process -XSLTC -IN jira_xalanj_2623.xml -XSL jira_xalanj_2623.xsl > jira_xalanj_2623.out 

$JAVACMD -Djava.endorsed.dirs=$XERCES_ENDORSED_DIR_PATH -classpath $TEST_XSL_DIR/testxsl.jar org.apache.qetest.XSValidationTestDriver jira_xalanj_2623.out jira_xalanj_2623.xsd xalan_xsltc

rm jira_xalanj_2623.out
