<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: boolean87 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.1 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test = and !=, comparing RTF to boolean. -->

<xsl:output method="xml" encoding="UTF-8"/>

<xsl:variable name="normalRTF">
  <xsl:value-of select="/doc/avj"/>
</xsl:variable>
<xsl:variable name="emptyRTF"></xsl:variable>

<xsl:template match="doc">
  <out>
    <nt><xsl:value-of select="$normalRTF=true()"/></nt>
    <nnt><xsl:value-of select="$normalRTF!=true()"/></nnt>
    <tn><xsl:value-of select="true()=$normalRTF"/></tn>
    <tnn><xsl:value-of select="true()!=$normalRTF"/></tnn><xsl:text>
</xsl:text>
    <et><xsl:value-of select="$emptyRTF=true()"/></et>
    <ent><xsl:value-of select="$emptyRTF!=true()"/></ent>
    <te><xsl:value-of select="true()=$emptyRTF"/></te>
    <tne><xsl:value-of select="true()!=$emptyRTF"/></tne>
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
