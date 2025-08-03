/*
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
 */
/*
 * $Id$
 */

/*
 *
 * LoggingEntityResolver.java
 *
 */
package org.apache.qetest.xsl;

import java.io.IOException;

import org.apache.qetest.Reporter;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

//-------------------------------------------------------------------------

/**
 * Implementation of EntityResolver that logs all calls.
 * Currently just provides default service; returns null.
 * @author shane_curcuru@lotus.com
 * @version $Id$
 */
public class LoggingEntityResolver implements EntityResolver
{

    /** No-op ctor since it's often useful to have one. */
    public LoggingEntityResolver(){}

    /**
     * Ctor that calls setReporter automatically.  
     *
     * NEEDSDOC @param r
     */
    public LoggingEntityResolver(Reporter r)
    {
        setReporter(r);
    }

    /** Our Reporter, who we tell all our secrets to. */
    private Reporter reporter;

    /**
     * Accesor methods for our Reporter.  
     *
     * NEEDSDOC @param r
     */
    public void setReporter(Reporter r)
    {
        if (r != null)
            reporter = r;
    }

    /**
     * Accesor methods for our Reporter.  
     *
     * NEEDSDOC ($objectName$) @return
     */
    public Reporter getReporter()
    {
        return (reporter);
    }

    /** Prefixed to all reporter msg output. */
    private String prefix = "ER:";

    /** Counters for how many entities we've 'resolved'. */
    private int entityCtr = 0;

    /**
     * Accesor methods for entity counter.  
     *
     * NEEDSDOC ($objectName$) @return
     */
    public int getEntityCtr()
    {
        return entityCtr;
    }

    /**
     * Cheap-o string representation of our state.  
     *
     * NEEDSDOC ($objectName$) @return
     */
    public String getCounterString()
    {
        return (prefix + "Entities: " + getEntityCtr());
    }

    /** Cheap-o string representation of last entity we resolved. */
    private String lastEntity = null;

    /**
     * NEEDSDOC Method setLastEntity 
     *
     *
     * NEEDSDOC @param s
     */
    protected void setLastEntity(String s)
    {
        lastEntity = s;
    }

    /**
     * Accessor for string representation of last entity we resolved.  
     *
     * NEEDSDOC ($objectName$) @return
     */
    public String getLastEntity()
    {
        return lastEntity;
    }

    /** What loggingLevel to use for reporter.logMsg(). */
    private int level = Reporter.DEFAULT_LOGGINGLEVEL;

    /**
     * Accesor methods; don't think it needs to be synchronized.  
     *
     * NEEDSDOC @param l
     */
    public void setLoggingLevel(int l)
    {
        level = l;
    }

    /**
     * Accesor methods; don't think it needs to be synchronized.  
     *
     * NEEDSDOC ($objectName$) @return
     */
    public int getLoggingLevel()
    {
        return level;
    }

    /**
     * Implement this method: just returns null for now.
     * Also saves the last entity for later retrieval, and counts
     * how many entities we've 'resolved' overall.
     * @todo have a settable property to actually return as the InputSource
     *
     * NEEDSDOC @param publicId
     * NEEDSDOC @param systemId
     *
     * NEEDSDOC ($objectName$) @return
     * @exception SAXException never thrown
     * @exception IOException never thrown
     */
    public InputSource resolveEntity(String publicId, String systemId)
            throws SAXException, IOException
    {

        entityCtr++;

        setLastEntity(publicId + ";" + systemId);

        if (reporter != null)
        {
            reporter.logMsg(level,
                            prefix + getLastEntity() + " "
                            + getCounterString());
        }

        return null;
    }
}
