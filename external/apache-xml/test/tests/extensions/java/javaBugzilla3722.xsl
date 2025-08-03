<?xml version="1.0"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:lxslt="http://xml.apache.org/xslt"
                xmlns:bug3722="javaBugzilla3722-namespace"
                extension-element-prefixes="bug3722"
                exclude-result-prefixes="lxslt"
                version="1.0">

  <lxslt:component prefix="bug3722"
                   functions="dumpConfig">
    <lxslt:script lang="javaclass" src="javaBugzilla3722"/>
  </lxslt:component>

  <xsl:template match="/doc">
    <out>
      <xsl:apply-templates/>
    </out>
  </xsl:template>

   <xsl:template match="matcher">
      <xsl:variable name="config"><xsl:copy-of select="."/></xsl:variable>
      <dumpConfig><xsl:value-of select="bug3722:dumpConfig($config)"/></dumpConfig>
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
