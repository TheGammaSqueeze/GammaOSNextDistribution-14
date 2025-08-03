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

package org.apache.qetest;

import java.util.Properties;

/**
 * Factory constructor for various qetest-related objects.  
 *
 * Currently only supports finding an appropriate instance 
 * of a file-based CheckService.
 *
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public abstract class QetestFactory
{

    /** Constant denoting a default CheckService for Files.  */
    public static final String TYPE_FILES = "QetestFactory.FILECHECK";

    /** Default TYPE_FILES implementation class.  */
    public static final String DEFAULT_TYPE_FILES = "org.apache.qetest.xsl.XHTFileCheckService";

    /**
     * Get a new CheckService of a specified type.
     * 
     * Currently only supports the TYPE_FILES or your own FQCN.  
     *
     * @param type FQCN or constant type of CheckService needed
     * @return CheckService of the appropriate type; or a fallback 
     * type if not found and a fallback is available.
     */
    public static CheckService newCheckService(Logger logger, String type)
    {
        CheckService service = null;
        if (null == type)
        {
            logger.logMsg(Logger.ERRORMSG, "Warning: no type specified for newCheckService!");
            return null;
        }
        else if (TYPE_FILES.equals(type))
        {
            // Return our default impl
            Class fClazz = QetestUtils.testClassForName(DEFAULT_TYPE_FILES, null, null);
            if (null == fClazz)
            {
                logger.logMsg(Logger.ERRORMSG, "Warning: no default fileChecker is available: " + DEFAULT_TYPE_FILES);
                return null; //@todo should we throw exception instead?
            }

            try
            {
                service = (CheckService)fClazz.newInstance();
                //logger.logMsg(Logger.TRACEMSG, TYPE_FILES + " is " + service);
            } 
            catch (Exception e)
            {
                logger.logThrowable(Logger.ERRORMSG, e, "newCheckService(" + TYPE_FILES + ") threw");
            }
            return service;
        }
        else // Assume a classname of your impl
        {
            // Return our default impl
            Class fClazz = QetestUtils.testClassForName(type, QetestUtils.defaultPackages, null);
            if (null == fClazz)
            {
                logger.logMsg(Logger.ERRORMSG, "Warning: no fileChecker is available of type: " + type);
                return null; //@todo should we throw exception instead?
            }

            try
            {
                service = (CheckService)fClazz.newInstance();
                logger.logMsg(Logger.TRACEMSG, TYPE_FILES + " is " + service);
            } 
            catch (Exception e)
            {
                logger.logThrowable(Logger.ERRORMSG, e, "newCheckService(" + TYPE_FILES + ") threw");
            }
            return service;
        }
    }


    /**
     * Get a new Reporter with some defaults.
     * 
     * Will attempt to initialize the appropriate Reporter
     * depending on the options passed in; if all else fails, will 
     * return at least a ConsoleLogger.  
     *
     * @param options to create Reporter from
     * @return appropriate Reporter instance, or a default one.
     */
    public static Reporter newReporter(Properties options)
    {
        Reporter reporter = null;
        if (null == options)
        {
            // Return a default Reporter
            reporter = new Reporter(null);
            reporter.addDefaultLogger();  // add default logger automatically
            return reporter;
        }

        // Setup appropriate defaults for the Reporter
        // Ensure we have an XMLFileLogger if we have a logName
        String logF = options.getProperty(Logger.OPT_LOGFILE);

        if ((logF != null) && (!logF.equals("")))
        {

            // We should ensure there's an XMLFileReporter
            String r = options.getProperty(Reporter.OPT_LOGGERS);

            if (r == null)
            {
                // Create the property if needed...
                options.put(Reporter.OPT_LOGGERS,
                              "org.apache.qetest.XMLFileLogger");
            }
            else if (r.indexOf("XMLFileLogger") <= 0)
            {
                // ...otherwise append to existing list
                options.put(Reporter.OPT_LOGGERS,
                              r + Reporter.LOGGER_SEPARATOR
                              + "org.apache.qetest.XMLFileLogger");
            }
        }

        // Ensure we have a ConsoleLogger unless asked not to
        // @todo improve and document this feature
        String noDefault = options.getProperty("noDefaultReporter");

        if (noDefault == null)
        {

            // We should ensure there's an XMLFileReporter
            String r = options.getProperty(Reporter.OPT_LOGGERS);

            if (r == null)
            {
                options.put(Reporter.OPT_LOGGERS,
                              "org.apache.qetest.ConsoleLogger");
            }
            else if (r.indexOf("ConsoleLogger") <= 0)
            {
                options.put(Reporter.OPT_LOGGERS,
                              r + Reporter.LOGGER_SEPARATOR
                              + "org.apache.qetest.ConsoleLogger");
            }
        }

        // Pass our options directly to the reporter
        //  so it can use the same values in initialization
        // A Reporter will auto-initialize from the values
        //  in the properties block
        reporter = new Reporter(options);
        return reporter;
    }


    /**
     * Get a new Logger with some defaults.
     * 
     * Currently a redirect to call (Logger)newReporter(options).  
     *
     * @param options to create Logger from
     * @return appropriate Logger instance, or a default one.
     */
    public static Logger newLogger(Properties options)
    {
        return (Logger)newReporter(options);
    }


    /** Prevent instantiation - private constructor.  */
    private QetestFactory() { /* no-op */ }

}  // end of class CheckService

