<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: bugzilla6284 -->
  <!-- Creator: David Marston, from Daniel Gilder's bug report -->

<xsl:output method="xml" indent="no" encoding="UTF-8"/>

<xsl:variable name="lastMove">
  <xsl:for-each select="/Nodes/Black">
    <xsl:message>Position: <xsl:value-of select="position()"/></xsl:message>
    <xsl:if test="position()=1">
      <xsl:value-of select="@number"/>
    </xsl:if>
  </xsl:for-each>
</xsl:variable>

<xsl:template match="/">
  <out>
    <title>
      <xsl:text>Reproducing Bugzilla#6284: predicate/global variable/position()</xsl:text>
    </title>
    <xsl:text>
</xsl:text>
    <xsl:for-each select="/Nodes/Black[@number &lt;= $lastMove]">
      <duplicate><!-- Should get one of these -->
        <xsl:text>found a duplicate at </xsl:text>
        <xsl:value-of select="position()"/>
      </duplicate>
    </xsl:for-each>
    <xsl:text>
</xsl:text>
    <last><!-- Should be 1 -->
      <xsl:value-of select="$lastMove"/>
    </last>
  </out>
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
