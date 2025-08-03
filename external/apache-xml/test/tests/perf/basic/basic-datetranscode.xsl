<?xml version="1.0"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

<xsl:output method="html"/>
<xsl:template match="doc">
  <doc>
    <xsl:for-each select="date">
	    <xsl:variable name="Day" select="substring-before(.,' ')"/>
    	<xsl:variable name="Month" select="substring-before(normalize-space(substring-after(., $Day)),' ')"/>
	    <xsl:variable name="Year" select="normalize-space(substring-after(., $Month))"/>
		<date orginal="{.}">
		<day><xsl:value-of select="$Day"/></day>
		<xsl:choose>
		<xsl:when test="$Month='January'"><month>1</month></xsl:when>
		<xsl:when test="$Month='February'"><month>2</month></xsl:when>
		<xsl:when test="$Month='March'"><month>3</month></xsl:when>
		<xsl:when test="$Month='April'"><month>4</month></xsl:when>
		<xsl:when test="$Month='May'"><month>5</month></xsl:when>
		<xsl:when test="$Month='June'"><month>6</month></xsl:when>
		<xsl:when test="$Month='July'"><month>7</month></xsl:when>
		<xsl:when test="$Month='August'"><month>8</month></xsl:when>
		<xsl:when test="$Month='September'"><month>9</month></xsl:when>
		<xsl:when test="$Month='October'"><month>10</month></xsl:when>
		<xsl:when test="$Month='November'"><month>11</month></xsl:when>
		<xsl:when test="$Month='December'"><month>12</month></xsl:when>
		</xsl:choose>
		<year><xsl:value-of select="$Year"/></year>
		</date>
    </xsl:for-each>
  </doc>
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
