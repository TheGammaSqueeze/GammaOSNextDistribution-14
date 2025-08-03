<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:math="http://exslt.org/math"
                exclude-result-prefixes="math"
                version="1.0">
<!-- Using math:max() within a xsl:template name -->
                
<xsl:output method="html" encoding="ISO-8859-1" indent="no" doctype-public="-//W3C//DTD HTML 4.01 Transitional//EN"  />
 
<xsl:template match="/">
   <xsl:call-template name = "calcul" >
      <xsl:with-param name="list" select="items/item" />
   </xsl:call-template>
</xsl:template>		  
		  
<xsl:template name="calcul">
   <xsl:param name="list"/>
   <xsl:choose>
      <xsl:when test="$list"><xsl:value-of select="math:max($list)" /></xsl:when>		 
      <xsl:otherwise>0</xsl:otherwise>
   </xsl:choose>		 
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
