<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  version="1.0">   
<!-- Reproduce Bugzilla#4218, apply this to identity.xml or any file -->  
<!--
The following stylesheet puts out <out>abcabc</out>, which is absolutely
incorrect.  If you uncomment the commented value-of and variable, it results
"Variable accessed before it is bound!" error message.  The bug seems to be
related to the inner call-template... it looks like something in the stack
frame is not being restored???
-->
<xsl:template match="/">
    <out>
      <xsl:variable name="v1" select="'abc-should-appear-once'"/>
      <xsl:variable name="v2" select="'def-should-appear-once'"/>
     
      <xsl:variable name="v3" select="'ghi-should-appear-once'"/>
     
      <xsl:call-template name="test-template">
        <xsl:with-param name="p1">
          <xsl:call-template name="xyz-template">
            <xsl:with-param name="p1" select="$v1"/>
          </xsl:call-template>
          <xsl:value-of select="$v2"/>
          <!-- Comment this in, to get error message: 
               Variable accessed before it is bound! 
               See Bugzilla4218a.xsl -->
          <!-- xsl:value-of select="$v3"/ -->
        </xsl:with-param>
      </xsl:call-template>
    </out>
  </xsl:template>
 
  <xsl:template name="test-template">
    <xsl:param name="p1" select="'error'"/>
    <test-template><xsl:value-of select="$p1"/></test-template>
  </xsl:template>
 
  <xsl:template name="xyz-template">
    <xsl:param name="p1" select="'error'"/>
    <xyz-template><xsl:value-of select="$p1"/></xyz-template>
  </xsl:template>

  <!--
   * Licensed to the Apache Software Foundation (ASF) under one
   * or more contributor license agreements. See the NOTICE file
   * distributed with this work for additional information
   * regarding copyright ownership. The ASF licenses this file
   * to you under the Apache License, Version 2.0 (the  "License");
   * you may not use this file except in compliance with the License.
   * You may obtain a copy of the License at
   *
   *     http://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing, software
   * distributed under the License is distributed on an "AS IS" BASIS,
   * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   * See the License for the specific language governing permissions and
   * limitations under the License.
  -->

</xsl:stylesheet>
