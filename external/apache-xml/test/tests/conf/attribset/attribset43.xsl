<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: attribset43 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 7.1.4 Named Attribute Sets -->
  <!-- Creator: David Marston, based on a test by Morten Jorgensen -->
  <!-- Purpose: Test inheritance of attribute sets. A xsl:element instruction
                is referring an attribute set that is defined by two separate
                xsl:attribute-set elements with the same name. -->

<xsl:output indent="yes"/>

<xsl:template match="/">
  <xsl:element name="out" use-attribute-sets="child">
    <xsl:attribute name="location">Wonderland</xsl:attribute>
  </xsl:element>
</xsl:template>

<xsl:attribute-set name="child">
  <xsl:attribute name="follow">yellowbrickroad</xsl:attribute>
  <xsl:attribute name="rabbithole">shallow</xsl:attribute>
</xsl:attribute-set>

<xsl:attribute-set name="child">
  <xsl:attribute name="follow">theleader</xsl:attribute>
  <xsl:attribute name="alice">intoxicated</xsl:attribute>
</xsl:attribute-set>


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
