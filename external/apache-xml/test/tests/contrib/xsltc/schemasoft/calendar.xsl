<?xml version="1.0"?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">



<!-- Author:  Muhammad Athar Parvez -->

<!-- Reference: http://www.incrementaldevelopment.com/xsltrick/parvez/#calendar -->

<!-- Description: Print out an html calander of a month -->



<xsl:output method="xml" encoding="UTF-8"/>



<xsl:variable name="start" select="/month/@start"/>

<xsl:variable name="count" select="/month/@days"/>



<xsl:variable name="total" select="$start + $count - 1"/>

<xsl:variable name="overflow" select="$total mod 7"/>



<xsl:variable name="nelements">

<xsl:choose>

<xsl:when test="$overflow > 0"><xsl:value-of select="$total + 7 - $overflow"/></xsl:when>

<xsl:otherwise><xsl:value-of select="$total"/></xsl:otherwise>

</xsl:choose>

</xsl:variable>



<xsl:template match="/">



<html>

<head><title><xsl:value-of select="month/name"/></title></head>



<body bgcolor="lightyellow">



<h1 align="center"><xsl:value-of select="month/name"/></h1>



<table border="1" bgcolor="lightblue" noshade="yes" align="center">

<tr bgcolor="white">

<th>Sun</th><th>Mon</th><th>Tue</th><th>Wed</th><th>Thu</th><th>Fri</th><th>Sat</th>

</tr>



<xsl:call-template name="month"/>

</table>



</body></html>



</xsl:template>



<!-- Called only once for root -->

<!-- Uses recursion with index + 7 for each week -->

<xsl:template name="month">

<xsl:param name="index" select="1"/>



<xsl:if test="$index &lt; $nelements">

<xsl:call-template name="week">

<xsl:with-param name="index" select="$index"/>

</xsl:call-template>



<xsl:call-template name="month">

<xsl:with-param name="index" select="$index + 7"/>

</xsl:call-template>

</xsl:if>



</xsl:template>



<!-- Called repeatedly by month for each week -->

<xsl:template name="week">

<xsl:param name="index" select="1"/>

<tr>

<xsl:call-template name="days">

<xsl:with-param name="index" select="$index"/>

<xsl:with-param name="counter" select="$index + 6"/>

</xsl:call-template>

</tr>

</xsl:template>



<!-- Called by week -->

<!-- Uses recursion with index + 1 for each day-of-week -->

<xsl:template name="days">

<xsl:param name="index" select="1"/>

<xsl:param name="counter" select="1"/>



<xsl:choose>

<xsl:when test="$index &lt; $start">

<td>-</td>

</xsl:when>



<xsl:when test="$index - $start + 1 > $count">

<td>-</td>

</xsl:when>



<xsl:when test="$index > $start - 1">

<td><xsl:value-of select="$index - $start + 1"/></td>

</xsl:when>



</xsl:choose>



<xsl:if test="$counter > $index">

<xsl:call-template name="days">

<xsl:with-param name="index" select="$index + 1"/>

<xsl:with-param name="counter" select="$counter"/>

</xsl:call-template>

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
