import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.DataStore
import com.google.api.client.util.store.DataStoreFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.androidbuildinternal.v3.Androidbuildinternal
import com.google.api.services.androidbuildinternal.v3.AndroidbuildinternalScopes
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.utils.IOUtils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.xml.sax.SAXException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.Path
import java.security.GeneralSecurityException
import java.util.Collections
import java.util.logging.Logger
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * Task that grabs the most recent build from the branch under development (git_main, etc)
 * This task uses the androidbuildinternal api jar to access build servers.
 *
 * The jar is found here:
 * vendor/unbundled_google/libraries/androidbuildinternal/
 *
 * Their are several assumptions baked in to this code.
 *
 *
 * * "sdk-trunk_staging" is a build target
 * * "sdk-trunk_staging" containing a test artifact called: "android-all-robolectric.jar"
 *
 * To see builds and artifacts look here:
 * https://android-build.corp.google.com/build_explorer/branch/git_main/?gridSize=20&activeTarget=sdk-trunk_staging&selectionType=START_BUILD_WINDOW&numBuilds=20
 *
 * The API called to find the latest build can be played with here:
 * https://apis-explorer-internal.corp.google.com/?discoveryUrl=https:%2F%2Fwww.googleapis.com%2Fdiscovery%2Fv1%2Fapis%2Fandroidbuildinternal%2Fv3%2Frest&creds=public&methodId=androidbuildinternal.build.list
 *
 * If we want to try to be more precise a pull the current checkout's build the best option today
 * is to guess which git project is the latest and use the API behind: go/wimcl to find the buildId
 * that most closely matches the buildId the user is on.   Unclear if this is necessary.
 *
 */
abstract class RoboJarFetcherTask : DefaultTask() {

    @get:Input
    abstract var rootPath: String

    @get:Input
    abstract var outPath: String

    @get:Input
    abstract var suggestedGitBranch: String

    @get:Input
    abstract var buildId: Long

    @TaskAction
    fun taskAction() {
        println("Fetching android_all jar")
        // Setting this property is needed in the gradle jvm to allow
        // this task to start a web browser on its own rather than
        // begging the user to do so in standard out.
        val originalSysProp = System.getProperty("java.awt.headless")
        System.setProperty("java.awt.headless", "false")
        val generator = RoboJarFetcher(rootPath, outPath, suggestedGitBranch, buildId)
        val path = generator.downloadAndroidJarFromServer()
        System.setProperty("java.awt.headless", originalSysProp)
        println("Jar downloaded at $path")
    }
}

private class RoboJarFetcher(
        private val rootPath: String,
        private val outPath: String,
        private val suggestedGitBranch: String,
        private val buildId: Long
) {

    private val dataStoreFactory: DataStoreFactory
    private val localProps: DataStore<Long>
    private var client: Androidbuildinternal? = null
    private var lastFetchedBuildId: Long = -1

    init {
        val dataDir = File(outPath, "gapi")
        dataDir.mkdirs()
        dataStoreFactory = FileDataStoreFactory(dataDir)
        localProps = dataStoreFactory.getDataStore(LOCAL_PROPS)
    }

    /**
     * Downloads and returns the jar for latest robolectric system image
     */
    @Throws(IOException::class)
    fun downloadAndroidJarFromServer(): Path {
        val jar = cachedJar()
        if (jar != null) {
            return jar
        }

        // Download from server
        val buildId = latestBuildId()
        LOGGER.info("Downloading jar for buildId $buildId")
        val downloadUrl = buildClient().buildartifact()
                .getdownloadurl(buildId.toString(), TARGET, "latest", "android-all-robolectric.jar")
                .set("redirect", false)
                .execute()
                .signedUrl
        LOGGER.info("Download url $downloadUrl")
        val out = getFileForBuildNumber(buildId)
        val tempFile = File(out.parentFile, out.name + ".tmp")
        FileOutputStream(tempFile).use { o -> IOUtils.copy(URL(downloadUrl).openStream(), o) }
        tempFile.renameTo(out)
        localProps[KEY_BUILD_NUMBER] = buildId
        localProps[KEY_EXPIRY_TIME] = System.currentTimeMillis() + EXPIRY_TIMEOUT

        // Cleanup, delete all other files.
        for (f in out.parentFile.listFiles()) {
            if (f != out) {
                f.delete()
            }
        }
        return out.toPath()
    }

    @Throws(IOException::class)
    private fun cachedJar(): Path? {
        val buildNumber = localProps[KEY_BUILD_NUMBER] ?: return null
        val targetFile = getFileForBuildNumber(buildNumber)
        if (!targetFile.exists()) {
            return null
        }
        if (buildId != -1L) {
            // If we want a fixed build number, ignore expiry check
            return if (buildNumber == buildId) targetFile.toPath() else null
        }

        // Verify if this is still valid
        var expiryTime = localProps[KEY_EXPIRY_TIME] ?: return null
        if (expiryTime < System.currentTimeMillis()) {
            // Check if we are still valid.
            val latestBuildId = try {
                latestBuildId()
            } catch (e: Exception) {
                LOGGER.warning("Error fetching buildId from build server, using existing jar")
                return targetFile.toPath()
            }
            if (buildNumber != latestBuildId) {
                // New build available, download and return that
                return null
            }
            // Since we just verified, update the expiry
            expiryTime = System.currentTimeMillis() + EXPIRY_TIMEOUT
            localProps[KEY_EXPIRY_TIME] = expiryTime
        }
        return targetFile.toPath()
    }

    @Throws(IOException::class)
    private fun latestBuildId() : Long {
        if (buildId >= 0) {
            return buildId
        }
        if (lastFetchedBuildId > -1) {
            return lastFetchedBuildId
        }
        val gitBranch = currentGitBranch()
        val result = buildClient()
                .build()
                .list()
                .setSortingType("buildId")
                .setBuildType("submitted")
                .setBranch("git_$gitBranch")
                .setTarget(TARGET)
                .setBuildAttemptStatus("complete")
                .setSuccessful(true)
                .setMaxResults(1L)
                .execute()
        val buildId = result.builds[0].buildId
        LOGGER.info("Latest build id: $buildId")
        lastFetchedBuildId = buildId.toLong()
        return lastFetchedBuildId
    }

    @Throws(IOException::class)
    private fun buildClient() : Androidbuildinternal {
        if (client != null) {
            return client as Androidbuildinternal
        }
        try {
            val transport: HttpTransport = GoogleNetHttpTransport.newTrustedTransport()
            val flow = GoogleAuthorizationCodeFlow.Builder(
                    transport,
                    GsonFactory.getDefaultInstance(),
                    CLIENT_ID, CLIENT_SECRET,
                    AndroidbuildinternalScopes.all())
                    .setDataStoreFactory(dataStoreFactory)
                    .setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build()
            val credential = AuthorizationCodeInstalledApp(flow,
                    LocalServerReceiver())
                    .authorize("user")
            return Androidbuildinternal.Builder(
                    transport, GsonFactory.getDefaultInstance(), credential)
                    .build().also { client = it }
        } catch (gse: GeneralSecurityException) {
            throw IOException(gse)
        }
    }

    @Throws(IOException::class)
    fun currentGitBranch(): String {
        if (suggestedGitBranch.isNotEmpty()) {
            return suggestedGitBranch
        }

        // Try to find from repo manifest
        val manifest = File("$rootPath/.repo/manifests/default.xml")
        return try {
            DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(manifest)
                    .getElementsByTagName("default")
                    .item(0)
                    .attributes
                    .getNamedItem("revision")
                    .nodeValue
        } catch (ex: ParserConfigurationException) {
            throw IOException(ex)
        } catch (ex: SAXException) {
            throw IOException(ex)
        }
    }

    private fun getFileForBuildNumber(buildNumber: Long): File {
        val dir = File(outPath, "android_all")
        dir.mkdirs()
        return File(dir, "android-all-robolectric-$buildNumber.jar")
    }

    companion object {
        private val LOGGER = Logger.getLogger("RoboJarFetcher")
        private const val CLIENT_ID =
                "547163898880-gm920odpvl47ba6cpehjsna4ef978739.apps.googleusercontent.com"
        private const val CLIENT_SECRET = "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"
        private const val TARGET = "sdk-trunk_staging"
        private const val LOCAL_PROPS = "local_props"
        private const val KEY_EXPIRY_TIME = "expiry_time"
        private const val KEY_BUILD_NUMBER = "build_number"
        private const val EXPIRY_TIMEOUT = (60 * 60 * 1000 * 48).toLong() // 2 days
    }
}

private class RemoteByteChannel(urlString: String?) : SeekableByteChannel {
    private val url: URL
    private val size: Long
    private var requestedPos: Long? = null
    private var currentPos: Long = 0
    private var currentConn: HttpURLConnection? = null
    private var currentChannel: ReadableByteChannel? = null

    init {
        url = URL(urlString)
        val conn = newConn()
        conn.requestMethod = "HEAD"
        conn.connect()
        conn.responseCode
        size = conn.getHeaderField("content-length").toLong()
        conn.disconnect()
    }

    private fun closeCurrentConn() {
        currentChannel?.let (IOUtils::closeQuietly)
        currentChannel = null

        currentConn?.let(HttpURLConnection::disconnect)
        currentConn = null
    }

    @Throws(IOException::class)
    private fun newConn(): HttpURLConnection {
        closeCurrentConn()
        return (url.openConnection() as HttpURLConnection).also { currentConn = it }
    }

    @Throws(IOException::class)
    override fun read(byteBuffer: ByteBuffer): Int {
        requestedPos?.let {
            if (it != currentPos) {
                currentPos = it
                closeCurrentConn()
            }
        }
        requestedPos = null

        if (currentChannel == null) {
            val conn = newConn()
            conn.setRequestProperty("Range", "bytes=$currentPos-")
            conn.connect()
            currentChannel = Channels.newChannel(conn.inputStream)
        }
        val expected = byteBuffer.remaining()
        IOUtils.readFully(currentChannel, byteBuffer)
        val remaining = byteBuffer.remaining()
        currentPos += (expected - remaining).toLong()
        return expected - remaining
    }

    @Throws(IOException::class)
    override fun write(byteBuffer: ByteBuffer): Int {
        throw IOException("Not supported")
    }

    override fun position(): Long {
        return requestedPos ?: currentPos
    }

    override fun position(l: Long): SeekableByteChannel {
        requestedPos = l
        return this
    }

    override fun size(): Long {
        return size
    }

    @Throws(IOException::class)
    override fun truncate(l: Long): SeekableByteChannel {
        throw IOException("Not supported")
    }

    override fun isOpen(): Boolean {
        return currentChannel != null
    }

    override fun close() {
        closeCurrentConn()
    }
}
