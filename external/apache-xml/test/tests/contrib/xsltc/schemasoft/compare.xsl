<?xml version='1.0' encoding='utf-8' ?>

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:output method="text" indent="no"/>



	<!-- FileName: compare.xsl -->

	<!-- Creator: John Li, Schemasoft -->

	<!-- Purpose: Compare values of numerical parameters that are set using "select" and by child -->

	

	<xsl:template match="/">

		<xsl:call-template name="compare">

			<xsl:with-param name="a" select="3"/>

			<xsl:with-param name="b">1</xsl:with-param>

			<xsl:with-param name="x">3</xsl:with-param>

		</xsl:call-template>

	</xsl:template>

	

	<xsl:template name="compare">

		<xsl:param name="a"/>

		<xsl:param name="b"/>

		<xsl:param name="x"/>

a = <xsl:value-of select="$a"/>

b = <xsl:value-of select="$b"/>

x = <xsl:value-of select="$x"/>

a &gt; b<xsl:if test="not($a &gt; $b)"> fail</xsl:if>

b &lt; a<xsl:if test="not($b &lt; $a)"> fail</xsl:if>

a == x<xsl:if test="not($a = $x)"> fail</xsl:if>

a &lt;= x<xsl:if test="not($a &lt;= $x)"> fail</xsl:if>

a &gt;= x<xsl:if test="not($a &gt;= $x)"> fail</xsl:if>

b &lt;= a<xsl:if test="not($b &lt;= $a)"> fail</xsl:if>

a &gt;= b<xsl:if test="not($a &gt;= $b)"> fail</xsl:if>

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
