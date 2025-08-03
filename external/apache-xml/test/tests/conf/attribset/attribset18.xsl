<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:ped="http://www.ped.com"
                xmlns:bdd="http://www.bdd.com">

  <!-- FileName: attribset18 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 7.1.3 Creating Attributes -->
  <!-- Creator: Paul Dick -->
  <!-- Purpose: Verify adding an attribute to an element replaces any
       existing attribute of that element with the same expanded name. -->

<xsl:template match="/">
 <root><xsl:text>&#10;</xsl:text>
   <Out>
      <xsl:attribute name="attr1">Wrong</xsl:attribute>
      <xsl:attribute name="ped:attr1">Wrong</xsl:attribute>
      <xsl:attribute name="bdd:attr1">Wrong</xsl:attribute>
      <xsl:attribute name="attr2">Wrong</xsl:attribute>
	  <xsl:attribute name="bdd:attr1">Test1-OK</xsl:attribute>
      <xsl:attribute name="ped:attr1">Test2-OK</xsl:attribute>
      <xsl:attribute name="attr1">Hello</xsl:attribute>
      <xsl:attribute name="attr2">Test2</xsl:attribute>
      <xsl:attribute name="attr2">Goodbye</xsl:attribute>
   </Out>
 </root>
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
