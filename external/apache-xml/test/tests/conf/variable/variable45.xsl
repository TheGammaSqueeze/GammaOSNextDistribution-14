<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:zoo="http://www.host.com/zoo"
                exclude-result-prefixes="zoo">

  <!-- FileName: variable45 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.6 -->
  <!-- Purpose: Use param whose name is a QName in with-param passing. -->
  <!-- Author: David Marston -->

<xsl:template match="/">
  <out>
    <xsl:call-template name="a">
      <xsl:with-param name="zoo:bear" select="'200'"/>
    </xsl:call-template>
  </out>
</xsl:template>

<xsl:template name="a">
  <xsl:param name="zoo:bear">Default mammal</xsl:param>
  <xsl:param name="zoo:snake">Default reptile</xsl:param>
  <xsl:value-of select="$zoo:bear"/>, <xsl:value-of select="$zoo:snake"/>
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
