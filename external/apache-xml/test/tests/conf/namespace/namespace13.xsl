<?xml version="1.0"?>
<ped:transform xmlns:ped="http://www.w3.org/1999/XSL/Transform" version="1.0">

<ped:import href="impnspc13.xsl"/>
<ped:include href="incnspc13.xsl"/>

<ped:output method="xml"/>

  <!-- FileName: namespace13 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.1 XSLT Namespace. -->
  <!-- Creator: Paul Dick -->
  <!-- Purpose: Stylesheets are free to use any prefix, provided there is a namespace
       declaration that binds the prefix to the URI of XSLT namespace. -->

<ped:template match="doc">
  <out>
	<ped:value-of select="'Testing '"/>
	<ped:for-each select="*">
		<ped:value-of select="."/><ped:text> </ped:text>		
	</ped:for-each>

	<ped:call-template name="ThatTemp">
		<ped:with-param name="sam">quos</ped:with-param>
	</ped:call-template>

	<ped:call-template name="ThatOtherTemp">
		<ped:with-param name="sam">quos</ped:with-param>
	</ped:call-template>

	<ped:call-template name="ThatForthTemp">
		<ped:with-param name="sam">quos</ped:with-param>
	</ped:call-template>
  </out>
</ped:template>

<ped:template name="ThatTemp">
  <ped:param name="sam">bo</ped:param>
	Orginal xmlns:ped <ped:copy-of select="$sam"/>
</ped:template>

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

</ped:transform>
