<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: paramtest -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.4 Top-level Variables and Parameters  -->
  <!-- Purpose: Test of parameters to the stylesheet.  -->
  <!-- Comments: This test is within the embedded section because it requires
                 a special command line which is already developed for embed. -->

<xsl:param name="pv1" />
<xsl:param name="pv2" />
<xsl:param name="pv3" />
<xsl:param name="pv4">Oops, failed</xsl:param>
<xsl:param name="pv5">Oops, failed</xsl:param>
<xsl:param name="pv6">Oops, failed</xsl:param>

<xsl:template match="/">
 <out>
	<pv1Tag><xsl:value-of select="$pv1"/></pv1Tag>,
	<pv2Tag><xsl:value-of select="$pv2"/></pv2Tag>,
	<pv3Tag><xsl:value-of select="$pv3"/></pv3Tag>,
	<pv4Tag><xsl:value-of select="$pv4"/></pv4Tag>,
	<pv5Tag><xsl:value-of select="$pv5"/></pv5Tag>,
	<pv6Tag><xsl:value-of select="$pv6"/></pv6Tag>.
 </out>
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
