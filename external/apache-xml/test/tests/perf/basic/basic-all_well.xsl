<?xml version="1.0"?>

<!--<xsl:stylesheet xmlns:xsl="http://www.w3.org/XSL/Transform/1.0">-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:template match="/">
		<html>
			<head>
				<title><xsl:value-of select="PLAY/TITLE" /></title>
			</head>
			<body>
				<xsl:attribute name="style">
					background-color: #000000;
				</xsl:attribute>
				<xsl:apply-templates />
			</body>
		</html>
	</xsl:template>

	<xsl:template match="PLAY">
		<div>
	 		<xsl:attribute name="style">
	 			position: absolute;
				width: 100%;
	 			font-family: Arial;
	 			font-size: 10pt;
	 			color: #cc6600;
	 		</xsl:attribute>
	 		<xsl:apply-templates />
		</div>
	</xsl:template>

	<xsl:template match="SCNDESCR">
		<div>
			<xsl:attribute name="style">
				margin-bottom: 1em;
				padding: 1px;
				padding-left: 3em;
				background-color: #cc6600;
				color: #333333;
			</xsl:attribute>
			<xsl:value-of select="."/>
		</div>
	</xsl:template>

	<xsl:template match="PLAY/TITLE">
		<div>
			<xsl:attribute name="style">
				padding: 2px;
				padding-left: 1em;
				font-size: 12pt;
				background-color: #ff9900;
				color: #000000;
			</xsl:attribute>
			<xsl:value-of select="."/>
		</div>
	</xsl:template>

	<xsl:template match="ACT/TITLE">
		<div>
			<xsl:attribute name="style">
				text-align: right;
			</xsl:attribute>
			<span>
				<xsl:attribute name="style">
					width: 25%;
					padding: 1px;
					padding-right: 1em;
					color: #000000;
					background-color: #ff9900;
				</xsl:attribute>
				<xsl:value-of select="."/>
			</span>
		</div>
	</xsl:template>

	<xsl:template match="SCENE | PROLOGUE">
		<div>
			<!--
			<xsl:attribute name="id">act<xsl:eval>ancestorChildNumber('ACT',this)</xsl:eval>scene<xsl:eval>absoluteChildNumber(this)</xsl:eval></xsl:attribute>
			-->
			<xsl:apply-templates />
		</div>
	</xsl:template>

	<xsl:template match="SCENE/TITLE">
		<div>
			<xsl:attribute name="style">
				margin-bottom: 1em;
				padding-left: 1em;
				border-color: #ff9900;
				border-width: 1px;
				border-bottom-style: solid;
				border-top-style: solid;
				background-color: #cc6600;
				color: #000000;
			</xsl:attribute>
			<xsl:value-of select="."/>
		</div>
	</xsl:template>

	<xsl:template match="PROLOGUE/TITLE">
		<div>
			<xsl:attribute name="style">
				margin-bottom: 1em;
				padding-left: 1em;
				border-color: #ff9900;
				border-width: 1px;
				border-bottom-style: solid;
				border-top-style: solid;
				background-color: #cc6600;
				color: #000000;
			</xsl:attribute>
			<xsl:value-of select="."/>
		</div>
	</xsl:template>

	<xsl:template match="SPEAKER">
		<div>
			<xsl:attribute name="style">
				color: #ff9900;
			</xsl:attribute>
			<xsl:value-of select="."/>
		</div>
	</xsl:template>

	<xsl:template match="LINE">
		<div>
			<xsl:attribute name="style">
				margin-left: 3em;
				color: #ccccff;
			</xsl:attribute>
			<xsl:value-of select="."/>
		</div>
	</xsl:template>

	<xsl:template match="STAGEDIR">
		<div>
			<xsl:attribute name="style">
				position: relative;
				width: 40%;
				left: 50%;
				text-align: right;
				margin: 0.5em;
				padding-right: 1em;
				padding-left: 1em;
				border-color: #ff9900;
				border-width: 1px;
				border-bottom-style: solid;
				border-top-style: solid;
				font-size: 9pt;
				color: #9999cc;
			</xsl:attribute>
			<xsl:value-of select="."/>
		</div>
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
