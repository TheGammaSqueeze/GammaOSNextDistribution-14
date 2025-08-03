<xsl:stylesheet version='1.0' xmlns:xsl="http://www.w3.org/1999/XSL/Transform">



  <!-- Test FileName: mk062.xsl -->

  <!-- Source Document: XSLT Programmer's Reference by Michael Kay -->

  <!-- Example: othello.xml, play.dtd, play.xsl -->

  <!-- Chapter/Page:  -->

  <!-- Purpose: Format a Shakespearean play  -->

  

<xsl:output method="text"/>



<!-- This stylesheet outputs the book list as a CSV file -->



<xsl:template match="BOOKLIST">

        <xsl:apply-templates select="BOOKS"/>

</xsl:template>



<xsl:template match="BOOKS">

Title,Author,Category<xsl:text/>

<xsl:for-each select="ITEM">

"<xsl:value-of select="TITLE"/>","<xsl:value-of select="AUTHOR"/>","<xsl:value-of select="@CAT"/>(<xsl:text/>

        <xsl:choose>

        <xsl:when test='@CAT="F"'>Fiction</xsl:when>

        <xsl:when test='@CAT="S"'>Science</xsl:when>

        <xsl:when test='@CAT="C"'>Computing</xsl:when>

        <xsl:when test='@CAT="X"'>Crime</xsl:when>

        <xsl:otherwise>Unclassified</xsl:otherwise>

        </xsl:choose>)"<xsl:text/>

</xsl:for-each><xsl:text>

</xsl:text>

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
