<?xml version="1.0"?>



<!-- xt sampleXMLSchema.xml XMLSchema2c++.xslt -->



<xsl:stylesheet

 xmlns:xsl='http://www.w3.org/1999/XSL/Transform'

 xmlns:xsch="http://www.w3.org/1999/XMLSchema" 

                 version="1.0" >



<xsl:output method="text"/>



<xsl:template match  = '/' >

typedef int integer;

   <xsl:apply-templates/>

</xsl:template>

<xsl:template match  = 'text()' >

</xsl:template>



<xsl:template match="@*|*">

    <xsl:apply-templates />

</xsl:template>



<xsl:template match = 'xsch:complexType' >

class <xsl:value-of select="@name"/> 

  <xsl:if test="@derivedBy='extension'" > : public <xsl:value-of select="@source"/>

  </xsl:if>

{

  <xsl:if test="@type" >

    <xsl:value-of select="@type"/> content;

  </xsl:if>

<xsl:apply-templates/>

};

</xsl:template>



<xsl:template match = 'xsch:element' >

<xsl:variable name="type">

<xsl:value-of select="@type"/>

</xsl:variable>

<xsl:choose>

 <xsl:when test="$type != ''">

  <xsl:value-of select="$type"/>

 </xsl:when>

 <xsl:otherwise>

  no_type_specified 

 </xsl:otherwise>

</xsl:choose>

<xsl:text> </xsl:text><xsl:value-of select="@name"/> ;

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
