<?xml version="1.0"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:java="http://xml.apache.org/xslt/java"
                version="1.0">

<!-- Copied from: java/samples/extensions/3-java-namespace.xsl -->
<xsl:output method="xml" indent="yes"/>
                
  <xsl:template match="/">
    <out>
      <xsl:apply-templates select="/doc/date"/> 
    </out>
  </xsl:template>
 
  <xsl:template match="date">
    <xsl:variable name="year" select="string(@year)"/>
    <xsl:variable name="month" select="string(@month)"/> 
    <xsl:variable name="day" select="string(@day)"/>          
    <xsl:variable name="format" select="string(@format)"/>
    <xsl:variable name="formatter"       
         select="java:java.text.SimpleDateFormat.new($format)"/>
    <xsl:variable name="date" 
         select="java:javaSample3.getDate($year,$month,$day)"/>
    <p>Format:   <xsl:value-of select="$format"/></p>
    <p>Date-xml: y:<xsl:value-of select="$year"/> m:<xsl:value-of select="$month"/> d:<xsl:value-of select="$day"/></p>
    <p>Date-ext: <xsl:value-of select="java:format($formatter, $date)"/></p>
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
