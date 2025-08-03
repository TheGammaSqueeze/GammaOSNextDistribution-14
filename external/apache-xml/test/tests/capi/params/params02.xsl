<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: variableman02 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.2 Values of Variables and Parameters -->
  <!-- Purpose:  Test setting several parameters externally. Run from
       special bat file which contains the following additional options
       -param in1 'A' -param in2 'B' -param in3 'C' etc.
       Suggest setting 1-5, so you see default on 6. -->
  <!-- Author: David Marston -->

<xsl:param name="in1" select="'default1'"/>
<xsl:param name="in2" select="'default2'"/>
<xsl:param name="in3" select="'default3'"/>
<xsl:param name="in4" select="'default4'"/>
<xsl:param name="in5" select="'default5'"/>
<xsl:param name="in6" select="'default6'"/>

<xsl:template match="doc">
  <out>
    <xsl:value-of select="$in1"/>
    <xsl:value-of select="$in2"/>
    <xsl:value-of select="$in3"/>
    <xsl:value-of select="$in4"/>
    <xsl:value-of select="$in5"/>
    <xsl:value-of select="$in6"/>
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
