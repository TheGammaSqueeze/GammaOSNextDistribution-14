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
package org.apache.qetest.xsl;

import org.apache.qetest.FileBasedTest;
import org.apache.qetest.Logger;
import org.apache.qetest.QetestUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Commandline;
import org.apache.tools.ant.types.CommandlineJava;
import org.apache.tools.ant.types.Environment;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Reference;
import org.apache.tools.ant.taskdefs.Execute;
import org.apache.tools.ant.taskdefs.ExecuteJava;
import org.apache.tools.ant.taskdefs.LogStreamHandler;
import org.apache.tools.ant.taskdefs.PumpStreamHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;

/**
 * Execute an instance of an org.apache.qetest.xsl.XSLProcessorTestBase.
 *
 * Cheap-o (for now) way to run qetest or Xalan tests directly
 * from an Ant build file.  Current usage:
 * <code>
 * &lt;taskdef name="QetestTask" classname="org.apache.qetest.xsl.XSLTestAntTask"/>
 *  &lt;target name="test">
 *      &lt;QetestTask
 *          test="Minitest"
 *          loggingLevel="50"
 *          consoleLoggingLevel="40"
 *          inputDir="../tests/api"
 *          goldDir="../tests/api-gold"
 *          outputDir="../tests/minitest"
 *          logFile="../tests/minitest/log.xml"
 *          flavor="trax"
 *       />
 * </code>
 * To be improved: I'd like to basically convert XSLTestHarness
 * into an Ant task, so you can run multiple tests at once.
 * Other obvious improvements include an AntLogger implementation
 * of Logger and better integration with the Project and
 * the various ways build scripts use properties.
 * Also, various properties should really have default values.
 *
 * Blatantly ripped off from org.apache.tools.ant.taskdefs.Java Ant 1.3
 *
 * @author <a href="mailto:shane_curcuru@lotus.com">Shane Curcuru</a>
 * @author Stefano Mazzocchi <a href="mailto:stefano@apache.org">stefano@apache.org</a>
 * @author <a href="mailto:stefan.bodewig@epost.de">Stefan Bodewig</a>
 * @version $Id$
 */
public class XSLTestAntTask extends Task
{

    /** Representation of command line to run for our test.  */
    protected CommandlineJava commandLineJava = new CommandlineJava();

    /** If we should fork another JVM to execute the test.  */
    protected boolean fork = false;

    /** If forking, current dir to set new JVM in.  */
    protected File dir = null;

    /** Alternate Ant output file to use.  */
    protected File out;

    /** 
     * If Ant errors/problems should throw a BuildException.  
     * Note: This does not fail if the test fails, only on a 
     * serious error or problem running the test.
     */
    protected boolean failOnError = false;

    /** Normal constants for testType: API tests.  */
    public static final String TESTTYPE_API = "api.";

    /** Normal constants for testType: API tests.  */
    public static final String TESTTYPE_CONF = "conf.";

    /** Normal constants for testType: API tests.  */
    public static final String TESTTYPE_PERF = "perf.";

    /** Normal constants for testType: API tests.  */
    public static final String TESTTYPE_CONTRIB = "contrib.";

    /** 
     * Type of test we're executing.
     * Used by passThruProps to determine which kind of prefixed 
     * properties from the Ant file to pass thru to the test.
     * Normally one of api|conf|perf|contrib, etc.
     */
    protected String testType = TESTTYPE_API;

    /** 
     * Name of the class to execute as the test.  
     */
    protected String testClass = null;

    /** 
     * Cheap-o way to pass properties to the underlying test.  
     * This task simply writes out needed properties to this file, 
     * then tells the test to -load them when executing.
     */
    protected String passThruProps = "XSLTestAntTask.properties";

    //-----------------------------------------------------
    //-------- Implementations for test-related parameters --------
    //-----------------------------------------------------

    /**
     * Test parameter: Set the type of this test.
     *
     * @param ll loggingLevel passed to test for all
     * non-console output; 0=very little output, 99=lots
     * @see org.apache.qetest.Reporter#setLoggingLevel(int)
     */
    public void setTestType(String type)
    {
        log("setTestType(" + type + ")", Project.MSG_VERBOSE);
        testType = type;
    }


    /**
     * Test parameter: Name of test class to execute.
     *
     * Replacement for Java's setClassname property; accepts the
     * name of a specific Test subclass.  Note that we use
     * {@link org.apache.qetest.QetestUtils.testClassForName(String, String[], String) QetestUtils.testClassForName}
     * to actually get the FQCN of the class to run; this allows
     * users to just specify the name of the class itself
     * (e.g. SystemIdTest) and have it work properly.
     * We search a number of default packages in order if needed: 
     * as seen in QetestUtils.testClassForName().
     * Note! Due to Ant's interesting handling of classpaths, we 
     * cannot actually resolve the testname here - we simply store 
     * the string, and then let QetestUtils resolve it later 
     * when we're actually executing.  That's because any classpaths 
     * that were set in the build.xml file aren't valid here, when 
     * the task is setting properties - they're only valid when the 
     * task actually executes later on.
     *
     * @param testClassname FQCN or just bare classname
     * of test to run
     */
    public void setTest(String testClassname)
    {
        log("setTest(" + testClassname + ")", Project.MSG_VERBOSE);
        testClass = testClassname;

        // Force the actual class being executed to be a 'launcher' class
        commandLineJava.setClassname("org.apache.qetest.QetestUtils");
        // Note this needs to be the first argument in the line,
        //  thus this should be the first property set
        //@todo fix this so users can actually use other properties 
        //  first without the ordering problem
        commandLineJava.createArgument().setLine(testClass);
    }


    /**
     * Test parameter: Set the loggingLevel used in this test.
     * //@todo deprecate: should use passThruProps instead
     *
     * @param ll loggingLevel passed to test for all
     * non-console output; 0=very little output, 99=lots
     * @see org.apache.qetest.Reporter#setLoggingLevel(int)
     */
    public void setLoggingLevel(int ll)
    {

        // Is this really the simplest way to stuff data into 
        //  objects in the 'proper Ant way'?
        commandLineJava.createArgument().setLine("-"
                                                 + Logger.OPT_LOGGINGLEVEL
                                                 + " "
                                                 + Integer.toString(ll));
    }


    /**
     * Test parameter: Set the consoleLoggingLevel used in this test.
     * //@todo deprecate: should use passThruProps instead
     *
     * @param ll loggingLevel used just for console output; here,
     * the default log going to Ant's console
     * @see org.apache.qetest.ConsoleLogger
     */
    public void setConsoleLoggingLevel(int ll)
    {
        commandLineJava.createArgument().setLine(
            "-ConsoleLogger.loggingLevel " + Integer.toString(ll));
    }


    /**
     * Test parameter: inputDir, root of input files tree (required).
     * //@todo deprecate: should use passThruProps instead
     *
     * //@todo this should have a default, since without a valid
     * value most tests will just return an error
     * @param d Path to look for input files in: should be the
     * root of the applicable tests/api, tests/conf, etc. tree
     * @see org.apache.qetest.FileBasedTest#OPT_INPUTDIR
     */
    public void setInputDir(Path d)
    {

        commandLineJava.createArgument().setValue(
            "-" + FileBasedTest.OPT_INPUTDIR);
        commandLineJava.createArgument().setPath(d);
    }


    /**
     * Test parameter: outputDir, dir to put outputs in.
     * //@todo deprecate: should use passThruProps instead
     * @param d where the test will put it's output files
     * @see org.apache.qetest.FileBasedTest#OPT_OUTPUTDIR
     */
    public void setOutputDir(Path d)
    {

        commandLineJava.createArgument().setValue(
            "-" + FileBasedTest.OPT_OUTPUTDIR);
        commandLineJava.createArgument().setPath(d);
    }


    /**
     * Test parameter: goldDir, root of gold files tree.
     * //@todo deprecate: should use passThruProps instead
     * @param d Path to look for gold files in: should be the
     * root of the applicable tests/api-gold, tests/conf-gold, etc. tree
     * @see org.apache.qetest.FileBasedTest#OPT_GOLDDIR
     */
    public void setGoldDir(Path d)
    {

        commandLineJava.createArgument().setValue(
            "-" + FileBasedTest.OPT_GOLDDIR);
        commandLineJava.createArgument().setPath(d);
    }


    /**
     * Test parameter: logFile, where to put XMLFileLogger output.
     * //@todo deprecate: should use passThruProps instead
     * @param f File(name) to send our 'official' results to via
     * an {@link org.apache.qetest.XMLFileLogger XMLFileLogger}
     */
    public void setLogFile(File f)
    {
        commandLineJava.createArgument().setValue("-" + Logger.OPT_LOGFILE);
        commandLineJava.createArgument().setFile(f);  // Check if this is what the test is expecting
    }

    
    /** 
     * Default prefix of Ant properties to passThru to the test.  
     * Note that testType is also a dynamic prefix that's also used.
     */
    public static final String ANT_PASSTHRU_PREFIX = "qetest.";
    
    
    /**
     * Worker method to write out properties file for test.  
     * Simply translates any properties in your Ant build file that 
     * begin with the prefix, and puts them in a Properties block.
     * This block is then written out to disk, so that the test can 
     * later read them in via -load.
     * //@todo NEEDS IMPROVEMENT: make more robust; check for write 
     * access to local dir; support dir-switching attribute 
     * when forking from Ant task; etc.
     * @param altPrefix alternate prefix of Ant properties to also 
     * pass thru in addition to ANT_PASSTHRU_PREFIX; these will 
     * override any of the default prefix ones
     */
    protected void writePassThruProps(String altPrefix)
    {
        Hashtable antProps = this.getProject().getProperties();

        Properties passThru = new Properties();
        // Passthru any of the default prefixed properties..
        for (Enumeration keys = antProps.keys();
                keys.hasMoreElements(); 
                /* no increment portion */ )
        {
            String key = keys.nextElement().toString();
            if (key.startsWith(ANT_PASSTHRU_PREFIX))
            {
                // Move any of these properties into the test; 
                //  rip off the prefix first
                passThru.put(key.substring(ANT_PASSTHRU_PREFIX.length()), antProps.get(key));
            }
        }
        //.. Then also passthru any alternate prefix properties
        //  this ensures alternate prefixes will overwrite default ones
        for (Enumeration keys = antProps.keys();
                keys.hasMoreElements(); 
                /* no increment portion */ )
        {
            String key = keys.nextElement().toString();
            if (key.startsWith(altPrefix))
            {
                // Also move alternate prefixed properties too
                passThru.put(key.substring(altPrefix.length()), antProps.get(key));
            }
        }
        // Make sure to write to the basedir of the project!
        File baseDir = this.getProject().getBaseDir();
        String propFileName = baseDir.getPath() + File.separator + passThruProps;
        log("writePassThruProps attempting to write to " + propFileName, Project.MSG_VERBOSE);
        try
        {
            // If we can write the props out to disk...
            passThru.save(new FileOutputStream(propFileName), 
                    "XSLTestAntTask.writePassThruProps() generated for use by test " + testClass);
            
            // ... then also force -load of this file into test's command line
            commandLineJava.createArgument().setLine("-load " + passThruProps);
        }
        catch (IOException ioe)
        {
            throw new BuildException("writePassThruProps could not write to " + propFileName + ", threw: "
                                     + ioe.toString(), location);
        }
    }


    //-----------------------------------------------------
    //-------- Implementations from Java task --------
    //-----------------------------------------------------

    /**
     * Execute this task.
     *
     * Basically just calls the
     * {@link #executeJava() executeJava() worker method} to do
     * all the work of executing the task.  Then checks the
     * failOnError member to see if we should throw an exception.
     *
     * @throws BuildException
     */
    public void execute() throws BuildException
    {
        // Log out our version info: useful for debugging, since 
        //  the wrong version of this class can easily get loaded
        log("XSLTestAntTask: $Id$", Project.MSG_VERBOSE);

        // Call worker method to create and write prop file
        // This passes thru both default 'qetest.' properties as 
        //  well as properties associated with testType
        writePassThruProps(testType);
        
        int err = -1;

        if ((err = executeJava()) != 0)
        {
            if (failOnError)
            {
                throw new BuildException("XSLTestAntTask execution returned: "
                                         + err, location);
            }
            else
            {
                log("XSLTestAntTask Result: " + err, Project.MSG_ERR);
            }
        }
    }


    /**
     * Worker method to do the execution and return a return code.
     *
     * @return the return code from the execute java class if it
     * was executed in a separate VM (fork = "yes").
     *
     * @throws BuildException
     */
    public int executeJava() throws BuildException
    {

        String classname = commandLineJava.getClassname();


        if (classname == null)
        {
            throw new BuildException("Classname must not be null.");
        }

        if (fork)
        {
            log("Forking " + commandLineJava.toString(), Project.MSG_VERBOSE);

            return run(commandLineJava.getCommandline());
        }
        else
        {
            if (commandLineJava.getVmCommand().size() > 1)
            {
                log("JVM args ignored when same JVM is used.",
                    Project.MSG_WARN);
            }

            if (dir != null)
            {
                log("Working directory ignored when same JVM is used.",
                    Project.MSG_WARN);
            }

            log("Running in same VM "
                + commandLineJava.getJavaCommand().toString(), Project.MSG_VERBOSE);
            run(commandLineJava);

            return 0;
        }
    }


    /**
     * Set the classpath to be used for this test.
     *
     * @param s classpath used for running the test
     */
    public void setClasspath(Path s)
    {
        createClasspath().append(s);
    }


    /**
     * Creates a nested classpath element
     *
     * @return classpath element to set for this test
     */
    public Path createClasspath()
    {
        return commandLineJava.createClasspath(project).createPath();
    }


    /**
     * Adds a reference to a CLASSPATH defined elsewhere.
     *
     * @param r reference to the CLASSPATH
     */
    public void setClasspathRef(Reference r)
    {
        createClasspath().setRefid(r);
    }


    /**
     * Creates a nested arg element.
     *
     * @return Argument to send to our test
     */
    public Commandline.Argument createArg()
    {
        return commandLineJava.createArgument();
    }


    /**
     * Set the forking flag.
     *
     * @param s true if we should fork; false otherwise
     */
    public void setFork(boolean s)
    {
        this.fork = s;
    }


    /**
     * Creates a nested jvmarg element.
     *
     * @return Argument to send to our JVM if forking
     */
    public Commandline.Argument createJvmarg()
    {
        return commandLineJava.createVmArgument();
    }


    /**
     * Set the command used to start the VM (only if fork==false).
     *
     * @param s vm command used
     */
    public void setJvm(String s)
    {
        commandLineJava.setVm(s);
    }


    /**
     * Add a nested sysproperty element.
     *
     * @param sysp to send to our test/JVM
     */
    public void addSysproperty(Environment.Variable sysp)
    {
        commandLineJava.addSysproperty(sysp);
    }


    /**
     * Throw a BuildException if process returns non 0.
     *
     * @param fail if we should fail on serious errors
     */
    public void setFailonerror(boolean fail)
    {
        failOnError = fail;
    }


    /**
     * The working directory of the process, if forked.
     *
     * @param d current directory for test, if forked
     */
    public void setDir(File d)
    {
        this.dir = d;
    }


    /**
     * File the output of the process is redirected to.
     *
     * @param out output file for Ant output (not just test output)
     */
    public void setOutput(File out)
    {
        this.out = out;
    }


    /**
     * -mx or -Xmx depending on VM version
     *
     * @param max max Java memory to use for test execution
     */
    public void setMaxmemory(String max)
    {

        if (Project.getJavaVersion().startsWith("1.1"))
        {
            createJvmarg().setValue("-mx" + max);
        }
        else
        {
            createJvmarg().setValue("-Xmx" + max);
        }
    }


    /**
     * Executes the given classname with the given arguments as if
     * it was a command line application.
     * Explicitly adds test-specific args from our members.
     *
     * @param command object to execute
     *
     * @throws BuildException thrown if IOException thrown internally
     */
    private void run(CommandlineJava command) throws BuildException
    {

        ExecuteJava exe = new ExecuteJava();


        exe.setJavaCommand(command.getJavaCommand());
        exe.setClasspath(command.getClasspath());
        exe.setSystemProperties(command.getSystemProperties());

        if (out != null)
        {
            try
            {
                exe.setOutput(new PrintStream(new FileOutputStream(out)));
            }
            catch (IOException io)
            {
                throw new BuildException(io, location);
            }
        }

        exe.execute(project);
    }


    /**
     * Executes the given classname with the given arguments in a separate VM.
     *
     * @param command line args to execute
     *
     * @return status from VM execution
     *
     * @throws BuildException thrown if IOException thrown internally
     */
    private int run(String[] command) throws BuildException
    {

        FileOutputStream fos = null;


        try
        {
            Execute exe = null;


            if (out == null)
            {
                exe = new Execute(
                    new LogStreamHandler(
                    this, Project.MSG_INFO, Project.MSG_WARN), null);
            }
            else
            {
                fos = new FileOutputStream(out);
                exe = new Execute(new PumpStreamHandler(fos), null);
            }

            exe.setAntRun(project);

            if (dir == null)
            {
                dir = project.getBaseDir();
            }
            else if (!dir.exists() ||!dir.isDirectory())
            {
                throw new BuildException(
                    dir.getAbsolutePath() + " is not a valid directory",
                    location);
            }

            exe.setWorkingDirectory(dir);
            exe.setCommandline(command);

            try
            {
                return exe.execute();
            }
            catch (IOException e)
            {
                throw new BuildException(e, location);
            }
        }
        catch (IOException io)
        {
            throw new BuildException(io, location);
        }
        finally
        {
            if (fos != null)
            {
                try
                {
                    fos.close();
                }
                catch (IOException io){}
            }
        }
    }


    /**
     * Executes the given classname with the given arguments as if it
     * was a command line application.
     *
     * @param classname of Java class to execute
     * @param args for Java class
     *
     * @throws BuildException not thrown
     */
    protected void run(String classname, Vector args) throws BuildException
    {

        CommandlineJava cmdj = new CommandlineJava();


        cmdj.setClassname(classname);

        for (int i = 0; i < args.size(); i++)
        {
            cmdj.createArgument().setValue((String) args.elementAt(i));
        }

        run(cmdj);
    }


    /**
     * Clear out the arguments to this java task.
     */
    public void clearArgs()
    {
        commandLineJava.clearJavaArgs();
    }
    
   /**
     * Set the bootclasspathref to be used for this test.
     *
     * @param s bootclasspathref used for running the test
     */
    public void setBootclasspathref(Reference r)
    {
        // This is a hack.
        // On JDK 1.4.x or later we need to override bootclasspath
        // the Xalan/Xerces in rt.jar.
        String jdkRelease =
                   System.getProperty("java.version", "0.0").substring(0,3);
        if (!jdkRelease.equals("1.1")
                && !jdkRelease.equals("1.2")
                && !jdkRelease.equals("1.3")) {
            Path p = (Path)r.getReferencedObject(this.getProject());
            log("Bootclasspath: " + p);
            createJvmarg().setValue("-Xbootclasspath/p:" + p);
        }
    }    
}
