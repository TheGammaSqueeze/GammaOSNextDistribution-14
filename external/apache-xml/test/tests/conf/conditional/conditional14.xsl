<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: conditional14 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 9.1 -->
  <!-- Purpose: Test xsl:if with test of subelement value. -->

<xsl:template match="Family">
  <out>
    <xsl:apply-templates select="Child"/>
  </out>
</xsl:template>

<xsl:template match="Child">
  <xsl:if test='Personal_Information/Sex[.="Male"]' >
    <xsl:value-of select="./Name" />, he is <xsl:value-of select="Personal_Information/Age"/><xsl:text> years old.&#10;</xsl:text>
  </xsl:if>
  <xsl:if test='Personal_Information/Sex[.="Female"]' >
    <xsl:value-of select="./Name" />, she is <xsl:value-of select="Personal_Information/Age"/><xsl:text> years old.&#10;</xsl:text>
  </xsl:if>
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
