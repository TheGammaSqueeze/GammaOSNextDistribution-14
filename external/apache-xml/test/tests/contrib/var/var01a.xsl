<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  version="1.0">   
<!-- var01.xsl: variations on a theme:Bugzilla#4218; input .xml is ignored-->

<!-- Theme: passing with-param, somehow variable stack frame gets messed up -->
<xsl:template match="/">
<!-- Theme-change: variables declared outside <out> -->
    <xsl:variable name="v1" select="'abc-should-appear-once'"/>
    <xsl:variable name="v2" select="'def-should-appear-once'"/>
    <out>
      <xsl:call-template name="template1">
        <xsl:with-param name="param1">
          <xsl:call-template name="template2">
            <!-- Theme-change: with-param uses different names -->
            <xsl:with-param name="param2" select="$v1"/>
          </xsl:call-template>
          <xsl:value-of select="$v2"/>
        </xsl:with-param>
      </xsl:call-template>
    </out>
  </xsl:template>
 
  <xsl:template name="template1">
    <xsl:param name="param1" select="'error'"/>
    <template1><xsl:value-of select="$param1"/></template1>
  </xsl:template>
 
  <xsl:template name="template2">
    <xsl:param name="param2" select="'error'"/>
    <template2><xsl:value-of select="$param2"/></template2>
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
