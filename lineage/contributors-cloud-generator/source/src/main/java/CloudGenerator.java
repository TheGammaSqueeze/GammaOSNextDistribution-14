import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import wordcloud.CollisionMode;
import wordcloud.WordCloud;
import wordcloud.WordFrequency;
import wordcloud.bg.PixelBoundryBackground;
import wordcloud.font.CloudFont;
import wordcloud.font.scale.FontScalar;
import wordcloud.font.scale.SqrtFontScalar;
import wordcloud.palette.ColorPalette;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.NullPointerException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;


public class CloudGenerator {

    private static final Logger LOGGER = LogManager.getLogger(CloudGenerator.class);

    private static final String ROOT_DIR = "./";
    private static final String DB_NAME = ROOT_DIR + "db/cloud.db";
    private static final String ACCOUNTS_DIR = ROOT_DIR + "db/accounts";
    private static final String CHANGES_DIR = ROOT_DIR + "db/changes";
    private static final String STATS_DIR = ROOT_DIR + "db/stats/";
    private static final String WELL_KNOWN_ACCOUNTS = ROOT_DIR + "resources/well-known-accounts.txt";

    private static final String ALL_STATS_FILENAME = "all_stats.dat";
    private static final String TRANSLATIONS_STATS_FILENAME = "translations_stats.dat";

    private static final String CLOUD_DATA = ROOT_DIR + "db/cloud.data";
    private static final String CLOUD_IMAGE = ROOT_DIR + "out/cloud.png";
    private static final String CLOUD_SVG = ROOT_DIR + "out/cloud.svg";
    private static final String CLOUD_DB_FILE = ROOT_DIR + "out/cloud.db";
    private static final String CLOUD_DATA_OUTPUT = ROOT_DIR + "out/cloud.data";
    private static final String CLOUD_DIST_FILE = ROOT_DIR + "out/cloud.zip";

    private static final String CLOUD_BG_SVG = ROOT_DIR + "resources/lineageos.svg";
    private static final String CLOUD_BG_PNG = ROOT_DIR + "resources/lineageos.png";
    private static final String CLOUD_FONT = ROOT_DIR + "resources/Roboto-Bold.ttf";
    private static final String LAST_CLOUD_SIZE = ROOT_DIR + "db/last_cloud_size.txt";
    private static final int DEFAULT_CLOUD_SIZE = 2024;
    private static final int DEFAULT_CLOUD_INCREMENT = 16;

    private static int wellKnownAccounts;

    private static class Account {
        public int id;
        public int accountId = -1;
        public List<String> usernames = new ArrayList<>();
        public List<String> names = new ArrayList<>();
        public List<String> emails = new ArrayList<>();
    }

    private static void initializeSSL() {
        TrustManager[] trustAllCerts = new TrustManager[] { 
            new X509TrustManager() {     
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { 
                    return new X509Certificate[0];
                } 
                public void checkClientTrusted( 
                    java.security.cert.X509Certificate[] certs, String authType) {
                    } 
                public void checkServerTrusted( 
                    java.security.cert.X509Certificate[] certs, String authType) {
                }
            } 
        }; 

        try {
            SSLContext sc = SSLContext.getInstance("SSL"); 
            sc.init(null, trustAllCerts, new java.security.SecureRandom()); 
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
        } catch (GeneralSecurityException e) {
        } 
    }

    public static void main(String[] args) throws Exception {
        boolean FROM_GERRIT = true;
        boolean closed = false;

        initializeSSL();

        new File(DB_NAME).delete();
        new File(CLOUD_DB_FILE).delete();
        Connection conn = createDb();
        Connection connMetadata = createMetadataDb();
        conn.setAutoCommit(false);
        connMetadata.setAutoCommit(false);
        try {
            LOGGER.info("Collecting data");
            processAccounts(conn);
            processStats(conn); // Still use the github data to map emails
            if (FROM_GERRIT) {
                fetchAllGerritCommits();
                processAllGerritCommits(conn);
            }
            create_indexes(conn);
            extractEmailsFromCommitNames(conn);

            if (FROM_GERRIT) {
                generateCloudDataFromGerrit(conn);
                writeGerritCloudData(conn);
            } else {
                computeCommitsPerProject(conn);
                generateCloudDataFromGithub(conn);
                filterDataByUserWithCommits(conn);
            }

            LOGGER.info("Generating cloud");
            generateCloud(connMetadata);
            generateMetadata(conn, connMetadata);

            // Close the database prior to compress it
            connMetadata.commit();
            connMetadata.close();
            generateCloudZip();

        } finally {
            conn.commit();
            conn.close();
            if (!connMetadata.isClosed()) {
	            connMetadata.commit();
	            connMetadata.close();
            }
        }
    }

    private static Connection createDb() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_NAME);
        Statement st = conn.createStatement();
        st.execute("PRAGMA synchronous = OFF;");
        st.execute("PRAGMA journal_mode = MEMORY;");
        st.execute("drop table if exists accounts;");
        st.execute("drop table if exists gerrit_accounts;");
        st.execute("drop table if exists usernames;");
        st.execute("drop table if exists names;");
        st.execute("drop table if exists emails;");
        st.execute("drop table if exists all_commits;");
        st.execute("drop table if exists translations_commits;");
        st.execute("drop table if exists stats;");
        st.execute("drop table if exists raw_cloud_data;");
        st.execute("drop table if exists all_accounts_with_commits;");
        st.execute("drop table if exists cloud_data;");
        st.execute("drop table if exists gerrit_commits;");
        st.execute("create table accounts (id NUMBER, accountId NUMBER, username TEXT, name TEXT, email TEXT);");
        st.execute("create table gerrit_accounts (accountId NUMBER, username TEXT, name TEXT, email TEXT);");
        st.execute("create table usernames (id NUMBER, username TEXT);");
        st.execute("create table names (id NUMBER, name TEXT);");
        st.execute("create table emails (id NUMBER, email TEXT);");
        st.execute("create table all_commits (project TEXT, commits NUMBER, name TEXT, email TEXT);");
        st.execute("create table translations_commits (project TEXT, commits NUMBER, name TEXT, email TEXT);");
        st.execute("create table stats (project TEXT, id NUMBER, commits NUMBER);");
        st.execute("create table raw_cloud_data (commits NUMBER, id NUMBER, name TEXT, username TEXT, filter TEXT);");
        st.execute("create table all_accounts_with_commits (accountId NUMBER);");
        st.execute("create table cloud_data (commits NUMBER, id NUMBER, name TEXT, username TEXT, filter TEXT);");
        st.execute("create table gerrit_commits (id NUMBER, changeId TEXT, project TEXT, branch TEXT, subject TEXT, author_name TEXT, author_email TEXT, owner_name, owner_email);");
        st.close();
        return conn;
    }

    private static Connection createMetadataDb() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + CLOUD_DB_FILE);
        Statement st = conn.createStatement();
        st.execute("PRAGMA synchronous = OFF;");
        st.execute("PRAGMA journal_mode = MEMORY;");
        st.execute("drop table if exists android_metadata;");
        st.execute("drop table if exists metadata;");
        st.execute("drop table if exists info;");
        st.execute("create table android_metadata (locale TEXT);");
        st.execute("insert into android_metadata (locale) values ('us_US')");
        st.execute("create table metadata (id NUMBER, name TEXT, username TEXT, filter TEXT, commits NUMBER, x NUMBER, y NUMBER, w NUMBER, h NUMBER, r NUMBER, fs REAL);");
        st.execute("create index metadata_idx_1 on metadata (name);");
        st.execute("create index metadata_idx_2 on metadata (filter);");
        st.execute("create index metadata_idx_3 on metadata (x, y, w, h);");
        st.execute("create index metadata_idx_4 on metadata (commits);");
        st.execute("create table info (key TEXT, value TEXT);");
        st.close();
        return conn;
    }

    private static void create_indexes(Connection conn) throws Exception {
        Statement st = conn.createStatement();
        st.execute("create index accounts_idx_1 on accounts (id);");
        st.execute("create index usernames_idx_1 on usernames (username);");
        st.execute("create index names_idx_1 on names (name);");
        st.execute("create index emails_idx_1 on emails (email);");
        st.execute("create index all_commits_idx_1 on all_commits (name);");
        st.execute("create index all_commits_idx_2 on all_commits (email);");
        st.execute("create index translations_commits_idx_1 on translations_commits (name);");
        st.execute("create index translations_commits_idx_2 on translations_commits (email);");
        st.execute("create index gerrit_commits_idx_1 on gerrit_commits (author_email);");
        st.execute("create index gerrit_commits_idx_2 on gerrit_commits (owner_email);");
        st.execute("create index gerrit_commits_idx_3 on gerrit_commits (changeId);");
        st.close();
    }

    private static void extractEmailsFromCommitNames(Connection conn) throws Exception {
        Statement st = conn.createStatement();
        st.execute("drop table if exists temp_emails;");
        st.execute("create table temp_emails (id NUMBER, email TEXT);");
        st.execute("insert into temp_emails (id, email) select id, email from emails");
        st.execute("insert into temp_emails (id, email) select distinct b.id, a.email from names b, " +
                    "all_commits a where a.name = b.name and a.name is not null and a.email is " +
                    "not null and instr(a.name ,'?') = 0 and length(a.name) > 9 " +
                    "and (select count(*) from names z where z.name = a.name) = 1;");
        st.execute("insert into temp_emails (id, email) select distinct b.id, a.email from names b, " +
                "translations_commits a where a.name = b.name and a.name is not null and a.email is " +
                "not null and instr(a.name ,'?') = 0 and length(a.name) > 9 " +
                 "and (select count(*) from names z where z.name = a.name) = 1;");
        st.execute("delete from emails");
        st.execute("insert into emails (id, email) select distinct id, email from temp_emails");
        st.execute("drop table if exists temp_emails;");
        st.close();
    }

    private static void computeCommitsPerProject(Connection conn) throws Exception {
        Statement st = conn.createStatement();
        st.execute("insert into stats (project, id, commits) " +
                "select z.project, acc.id, z.commits " +
                "from " +
                "( " +
                "select project, id, sum(commits) commits " +
                "from " +
                "( " +
                "select a.project, b.id, a.commits from all_commits a, emails b " +
                "where a.email = b.email " +
                "union " +
                "select a.project, b.id, a.commits * -1 from translations_commits a, emails b " +
                "where a.email = b.email " +
                ") " +
                "group by project, id " +
                ") z, accounts acc " +
                "where z.id = acc.id " +
                "and z.commits > 0;");
        st.close();
    }

    private static void generateCloudDataFromGithub(Connection conn) throws Exception {
        Statement st = conn.createStatement();
        st.execute("insert into raw_cloud_data (commits, id, name, username, filter) " +
                "select sum(stats.commits) commits, accounts.id, accounts.name, accounts.username, a.filter " +
                "from stats, accounts, " +
                "( " +
                "select id, group_concat(filter,'|') filter from " +
                "( " +
                "select id, group_concat(username,'|') filter from usernames " +
                "group by id " +
                "union " +
                "select id, group_concat(name,'|') filter from names " +
                "group by id " +
                ") " +
                "group by id " +
                ") a " +
                "where stats.id = accounts.id " +
                "and accounts.id = a.id " +
                "group by accounts.id, accounts.name, accounts.username, a.filter " +
                "order by 1 desc;");
        st.close();
    }

    private static void generateCloudDataFromGerrit(Connection conn) throws Exception {
        Statement st = conn.createStatement();
        st.execute("insert into cloud_data (commits, id, name, username, filter) " +
                "select count(*) commits, a.id, ifnull(a.name, a.username), a.username, f.filter from ( " +
                "select (select e.id from emails e where e.email = c.author_email) id, changeId from gerrit_commits c " +
                "where c.subject <> 'Automatic translation import' " +
                ") c, accounts a, " +
                "( " +
                "select id, group_concat(filter,'|') filter from " +
                "( " +
                "select id, group_concat(username,'|') filter from usernames " +
                "group by id " +
                "union " +
                "select id, group_concat(name,'|') filter from names " +
                "group by id " +
                ") " +
                "group by id " +
                ") f " +
                "where c.id = a.id " +
                "and a.id = f.id " +
                "group by a.id " +
                "order by commits desc");
        st.close();
    }

    @SuppressWarnings("unchecked")
    private static void filterDataByUserWithCommits(Connection conn) throws Exception {
        Statement st = conn.createStatement();
        ResultSet rs =  st.executeQuery("select accounts.id, accounts.accountId, accounts.name, group_concat(emails.email,'|') emails " +
                    "from raw_cloud_data, accounts, emails " +
                    "where raw_cloud_data.id = accounts.id and accounts.id = emails.id " +
                    "group by accounts.id, accounts.name " +
                    "order by raw_cloud_data.commits desc;");
        while(rs.next()) {
            int id = rs.getInt(1);
            int accountId = rs.getInt(2);
            String name = rs.getString(3);
            String[] emails = rs.getString(4).split("\\|");
            boolean hasCommits = false;
            for (String email : emails) {
                if (userHasCommits(id, accountId, name, email)) {
                    new File(ACCOUNTS_DIR, accountId + ".hasCommits").createNewFile();
                    if (new File(ACCOUNTS_DIR, accountId + ".noHasCommits").exists()) {
                        new File(ACCOUNTS_DIR, accountId + ".noHasCommits").delete();
                    }
                    hasCommits = true;
                    break;
                }
            }

            File noHasCommits = new File(ACCOUNTS_DIR, accountId + ".noHasCommits");
            if (!hasCommits && (!noHasCommits.exists() || (System.currentTimeMillis() - noHasCommits.lastModified()) > 2592000000L)) { // 30 days
                noHasCommits.createNewFile();
            }
        }
        rs.close();

        File accounts = new File(ACCOUNTS_DIR);
        Collection<File> allAccountsWithCommits = FileUtils.listFiles(accounts, new IOFileFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".hasCommits");
            }

            @Override
            public boolean accept(File file) {
                return file.isFile() && file.getName().endsWith(".hasCommits");
            }

        }, TrueFileFilter.INSTANCE);

        PreparedStatement ps1 = conn.prepareStatement("insert into all_accounts_with_commits (accountId) values (?);");
        for (File acc : allAccountsWithCommits) {
            int accountId = Integer.parseInt(acc.getName().substring(0, acc.getName().lastIndexOf(".")));
            ps1.setInt(1, accountId);
            ps1.execute();
        }
        ps1.close();

        st.execute("insert into cloud_data (commits, id, name, username, filter) " +
                    "select sum(r.commits), r.id, ifnull(r.name, r.username), r.username, r.filter from raw_cloud_data r, accounts a "+
                    "where r.id = a.id and a.accountId in (select accountId from all_accounts_with_commits) " +
                    "group by  r.name");

        FileWriter fw = new FileWriter(CLOUD_DATA);
        rs = st.executeQuery("select id, commits, name, filter from cloud_data order by 1;");
        while (rs.next()) {
            String id = rs.getString(1);
            String commits = rs.getString(2);
            String name = cleanup(rs.getString(3));
            String filter = cleanup(rs.getString(4));
            fw.write(String.format("%s,%s,%s|%s", id, commits, name, filter) + "\r\n");
        }
        fw.close();
        rs.close();
        st.close();
    }

    private static void fetchAllGerritCommits() throws Exception {
        final StringBuffer start = new StringBuffer("2010-10-28");
        File statsDir = new File(CHANGES_DIR);
        statsDir.mkdirs();
        FileUtils.listFiles(statsDir, new IOFileFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (name.contains("-") && name.compareTo(start.toString()) > 0) {
                    start.setLength(0);
                    start.append(name);
                }
                return true;
            }

            @Override
            public boolean accept(File file) {
                String name = file.getName();

                if (file.isDirectory() && name.contains("-") && name.compareTo(start.toString()) > 0) {
                    start.setLength(0);
                    start.append(name);
                }
                return true;
            }

        }, TrueFileFilter.INSTANCE);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar c = Calendar.getInstance();
        c.setTime(sdf.parse(start.toString()));
        c.set(Calendar.HOUR, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_YEAR, -30);
        Calendar now = Calendar.getInstance();
        now.set(Calendar.HOUR, 0);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);
        while (c.compareTo(now) <= 0) {
            fetchGerritCommits(c.getTime());
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    private static void fetchGerritCommits(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.set(Calendar.HOUR, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        String end = sdf.format(c.getTime());
        c.add(Calendar.DAY_OF_YEAR, 1);
        String start = sdf.format(c.getTime());

        String gerrit_username = System.getenv("GERRIT_USERNAME");
        String gerrit_password = System.getenv("GERRIT_PASSWORD");

        LOGGER.info("Fetching gerrit changes: " + end);
        File dir = new File(CHANGES_DIR, end);
        dir.mkdirs();
        int i = 1;
        int s = 0;
        final int count = 250;
        while (true) {
            try {
                String url = "changes/?q=status:merged+before:\"" + start + "\"+after:\"" + end + "\"&n=" + count + "&O=a&o=DETAILED_ACCOUNTS";
                if (s > 0) {
                    url += "&S="+s;
                }

                InputStream in = null;
                if (gerrit_username.isEmpty() || gerrit_password.isEmpty()) {
                    in = new URL("https://review.lineageos.org/" + url).openStream();
                }
                else {
                    URL ur = new URL("https://review.lineageos.org/a/" + url);
                    URLConnection uc = ur.openConnection();
                    String userpass = gerrit_username + ":" + gerrit_password;
                    String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()));
                    uc.setRequestProperty ("Authorization", basicAuth);
                    in = uc.getInputStream();
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(in, "UTF-8"));
                FileWriter fw = new FileWriter(new File(dir, String.valueOf(i)));
                reader.readLine();
                int read = -1;
                char[] data = new char[10240];
                StringBuffer sb = new StringBuffer();
                while ((read = reader.read(data, 0, 10240)) != -1) {
                    sb.append(data, 0, read);
                    fw.write(data, 0, read);
                }
                reader.close();
                fw.close();

                if (sb.indexOf("\"_more_changes\": true") == -1) {
                    break;
                }

                i++;
                s+=count;
                LOGGER.info("Fetching more gerrit changes: " + end + " (" + i + ")");

            } catch (Exception ex) {
                LOGGER.error("Error downloading gerrit changes " + end + "-" + start, ex);
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void processAllGerritCommits(Connection conn) throws Exception {
        File changesDir = new File(CHANGES_DIR);
        Collection<File> changes = FileUtils.listFiles(changesDir, new IOFileFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return true;
            }

            @Override
            public boolean accept(File file) {
                return true;
            }

        }, TrueFileFilter.INSTANCE);

        PreparedStatement ps1 = conn.prepareStatement("insert into gerrit_commits (id, changeId, project, branch, subject, author_name, author_email, owner_name, owner_email) values (?,?,?,?,?,?,?,?,?);");

        for (File change : changes) {
            if (!change.isFile()) continue;
            String json = readJson(change, false);
            if (json.trim().length() == 0) continue;
            try {
                JSONArray a = new JSONArray(json);
                int count = a.length();
                for (int i = 0; i < count; i++) {
                    JSONObject o = a.getJSONObject(i);
                    int id = o.optInt("_number", i);
                    String changeId = o.optString("change_id");
                    String project = o.optString("project");
                    String branch = o.optString("branch");
                    String subject = o.optString("subject");
                    String curRev = o.optString("current_revision");
                    String author_name = null;
                    String author_email = null;
                    try {
                        JSONObject author =  o.optJSONObject("revisions").getJSONObject(curRev).getJSONObject("commit").getJSONObject("author");
                        author_name = cleanup(author.optString("name"));
                        author_email = cleanup(author.optString("email"));
                    } catch (Exception ex) {
                        try {
                            JSONObject owner =  o.optJSONObject("owner");
                            author_name = cleanup(owner.optString("name"));
                            author_email = cleanup(owner.optString("email"));
                        } catch (Exception ex2) {
                            System.out.println("Fail to read current revision data. change: " + change + "; changeId: " + changeId);
                        }
                    }
                    JSONObject owner =  o.optJSONObject("owner");
                    String owner_name = cleanup(owner.optString("name"));
                    String owner_email = cleanup(owner.optString("email"));

                    ps1.setInt(1, id);
                    if (!isEmpty(changeId)) {
                        ps1.setString(2, changeId);
                    } else {
                        ps1.setNull(2, Types.VARCHAR);
                    }
                    if (!isEmpty(project)) {
                        ps1.setString(3, project);
                    } else {
                        ps1.setNull(3, Types.VARCHAR);
                    }
                    if (!isEmpty(branch)) {
                        ps1.setString(4, branch);
                    } else {
                        ps1.setNull(4, Types.VARCHAR);
                    }
                    if (!isEmpty(subject)) {
                        ps1.setString(5, subject);
                    } else {
                        ps1.setNull(5, Types.VARCHAR);
                    }
                    if (!isEmpty(author_name)) {
                        ps1.setString(6, author_name);
                    } else {
                        ps1.setNull(6, Types.VARCHAR);
                    }
                    if (!isEmpty(author_email)) {
                        ps1.setString(7, author_email);
                    } else {
                        ps1.setNull(7, Types.VARCHAR);
                    }
                    if (!isEmpty(owner_name)) {
                        ps1.setString(8, owner_name);
                    } else {
                        ps1.setNull(8, Types.VARCHAR);
                    }
                    if (!isEmpty(owner_email)) {
                        ps1.setString(9, owner_email);
                    } else {
                        ps1.setNull(9, Types.VARCHAR);
                    }
                    ps1.execute();
                }

            } catch (Exception ex) {
                System.out.println("Unable to parse changes in " + change);
                System.out.println(json);
                throw ex;
            }
        }

        ps1.close();
    }

    private static void writeGerritCloudData(Connection conn) throws Exception {
        FileWriter fw = new FileWriter(CLOUD_DATA);
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("select id, commits, name, filter from cloud_data order by 1;");
        while (rs.next()) {
            try {
                String id = rs.getString(1);
                String commits = rs.getString(2);
                String name = cleanup(rs.getString(3));
                String filter = cleanup(rs.getString(4));
                fw.write(String.format("%s,%s,%s|%s", id, commits, name, filter) + "\r\n");
            } catch (NullPointerException e) {
                // Do nothing, one of the columns returns null pointer
            }
        }
        fw.close();
        rs.close();
        st.close();
    }

    private static void generateMetadata(Connection conn, Connection connMetadata) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(new File(CLOUD_DATA_OUTPUT)));
        PreparedStatement ps1 = conn.prepareStatement("select name, username, filter, commits from cloud_data where id = ?;");
        PreparedStatement ps2 = connMetadata.prepareStatement("insert into metadata (id, name, username, filter, commits, x, y, w, h, r, fs) values (?,?,?,?,?,?,?,?,?,?,?);");
        String line = br.readLine();
        int fillId = -1000;
        while ((line = br.readLine()) != null) {
            String[] data = line.split(",");
            int id = Integer.parseInt(data[0]);
            int x = Integer.parseInt(data[1]);
            int y = Integer.parseInt(data[2]);
            int w = Integer.parseInt(data[3]);
            int h = Integer.parseInt(data[4]);
            int r = Integer.parseInt(data[5]);
            float fs = Float.parseFloat(data[6]);

            if (id != -1) {
                ps1.setInt(1, id);
                ResultSet rs = ps1.executeQuery();
                if (rs.next()) {
                    String name = rs.getString(1);
                    String username = rs.getString(2);
                    String filter = rs.getString(3);
                    int commits = rs.getInt(4);

                    ps2.setInt(1, id);
                    if (!isEmpty(name)) {
                        ps2.setString(2, name);
                    } else {
                        ps2.setNull(2, Types.VARCHAR);
                    }
                    if (!isEmpty(username)) {
                        ps2.setString(3, username);
                    } else {
                        ps2.setNull(3, Types.VARCHAR);
                    }
                    if (!isEmpty(filter)) {
                        ps2.setString(4, filter);
                    } else {
                        ps2.setNull(4, Types.VARCHAR);
                    }
                    ps2.setInt(5, commits);
                    ps2.setInt(6, x);
                    ps2.setInt(7, y);
                    ps2.setInt(8, w);
                    ps2.setInt(9, h);
                    ps2.setInt(10, r);
                    ps2.setFloat(11, fs);
                    ps2.execute();
                }
                rs.close();
            } else {
                String[] aux = data[7].split("\\|");
                ps2.setInt(1, fillId);
                ps2.setString(2, aux[0]);
                ps2.setString(3, aux[1]);
                ps2.setNull(4, Types.VARCHAR);
                ps2.setInt(5, 0);
                ps2.setInt(6, x);
                ps2.setInt(7, y);
                ps2.setInt(8, w);
                ps2.setInt(9, h);
                ps2.setInt(10, r);
                ps2.setFloat(11, fs);
                ps2.execute();

                fillId--;
            }
        }
        ps1.close();
        ps2.close();
        br.close();
    }

    private static void writeMetadataInfo(Connection conn, int originalSize) throws Exception {
        PreparedStatement ps1 = conn.prepareStatement("insert into info (key, value) values (?,?);");

        //--- Date
        ps1.setString(1, "date");
        ps1.setString(2, String.valueOf(System.currentTimeMillis()));
        ps1.execute();
        //--- Original Image Size (the one in which is based the metadata)
        ps1.setString(1, "orig_size");
        ps1.setString(2, String.valueOf(originalSize));
        ps1.execute();

        ps1.close();
    }

    private static void generateCloudZip() throws Exception {
        final int BUFFER = 10240;
        BufferedInputStream origin = null;
        FileOutputStream dest = new FileOutputStream(CLOUD_DIST_FILE);
        ZipOutputStream out = new ZipOutputStream(new  BufferedOutputStream(dest));
        byte data[] = new byte[BUFFER];

        String[] files = {CLOUD_IMAGE, CLOUD_SVG, CLOUD_DATA_OUTPUT, CLOUD_DB_FILE};
        for (int i=0; i < files.length; i++) {
            File cloudImage = new File(files[i]);
            FileInputStream fi = new FileInputStream(cloudImage);
            origin = new BufferedInputStream(fi, BUFFER);
            ZipEntry entry = new ZipEntry(cloudImage.getName());
            out.putNextEntry(entry);
            int count;
            while((count = origin.read(data, 0,
              BUFFER)) != -1) {
               out.write(data, 0, count);
            }
            origin.close();
        }
        out.close();
    }

    private static List<Account> processAccounts(Connection conn) throws Exception {
        List<Account> accts = new ArrayList<>();
        accts.addAll(readWellKnownAccounts());
        wellKnownAccounts = accts.size();
        int id = wellKnownAccounts + 1;

        PreparedStatement ps1 = conn.prepareStatement("insert into accounts (id, accountId, username, name, email) values (?,?,?,?,?);");
        PreparedStatement ps5 = conn.prepareStatement("insert into gerrit_accounts (accountId, username, name, email) values (?,?,?,?);");
        PreparedStatement ps2 = conn.prepareStatement("insert into usernames (id, username) values (?,?);");
        PreparedStatement ps3 = conn.prepareStatement("insert into names (id, name) values (?,?);");
        PreparedStatement ps4 = conn.prepareStatement("insert into emails (id, email) values (?,?);");

        File accountsDir = new File(ACCOUNTS_DIR);
        File[] accounts = accountsDir.listFiles();

        for (File account : accounts) {
            if (account.getName().equals("last.txt") || account.getName().endsWith(".hasCommits")
                    || account.getName().endsWith(".noHasCommits")) continue;
            String json = readJson(account, true);
            if (json.trim().length() == 0) continue;
            try {
                JSONObject o = new JSONObject(json);
                int accId = Integer.parseInt(account.getName());
                String username = o.optString("username");
                if (username != null) username = cleanup(username);
                String name = o.optString("name");
                if (name != null) name = cleanup(name);
                if (!isLegalName(name)) {
                    name = username;
                }
                String email = o.optString("email");
                if (email != null) email = cleanup(email);

                ps5.setInt(1, accId);
                if (isEmpty(username)) {
                    ps5.setNull(2, Types.VARCHAR);
                } else {
                    ps5.setString(2, username);
                }
                if (isEmpty(name)) {
                    ps5.setNull(3, Types.VARCHAR);
                } else {
                    ps5.setString(3, name);
                }
                if (isEmpty(email)) {
                    ps5.setNull(4, Types.VARCHAR);
                } else {
                    ps5.setString(4, email);
                }
                ps5.execute();

                boolean found = false;
                for (Account acc : accts) {
                    if (!isEmpty(email) && acc.emails.contains(email)) {
                        if (!isEmpty(username) && !acc.usernames.contains(username)) {
                            acc.usernames.add(username);
                        }
                        if (!isEmpty(name) && !acc.names.contains(name)) {
                            acc.names.add(name);
                        }

                        if (acc.accountId == -1) acc.accountId = accId;
                        found = true;
                        break;
                    }
                    if (!isEmpty(name) && acc.names.contains(name) && isValidNameForSearch(name)) {
                        if (!isEmpty(username) && !acc.usernames.contains(username)) {
                            acc.usernames.add(username);
                        }
                        if (!isEmpty(email) && !acc.emails.contains(email)) {
                            acc.emails.add(email);
                        }

                        if (acc.accountId == -1) acc.accountId = accId;
                        found = true;
                        break;
                    }
                    if (!isEmpty(username) && acc.usernames.contains(username)) {
                        if (!isEmpty(name) && !acc.names.contains(name)) {
                            acc.names.add(name);
                        }
                        if (!isEmpty(email) && !acc.emails.contains(email)) {
                            acc.emails.add(email);
                        }

                        if (acc.accountId == -1) acc.accountId = accId;
                        found = true;
                        break;
                    }
                }
                if (found) {
                    continue;
                }

                id++;
                Account acc = new Account();
                acc.id = id;
                acc.accountId = accId;
                boolean hasData = false;
                if (!isEmpty(email)) {
                    acc.emails.add(email);
                    hasData = true;
                }
                if (!isEmpty(name)) {
                    acc.names.add(name);
                    hasData = true;
                }
                if (!isEmpty(username)) {
                    acc.usernames.add(username);
                    hasData = true;
                }
                if (hasData) {
                    accts.add(acc);
                }

            } catch (Exception ex) {
                System.out.println("Unable to parse account " + account.getName());
                System.out.println(json);
                throw ex;
            }
        }

        for (Account acc : accts) {
            for (String username : acc.usernames) {
                ps2.setInt(1, acc.id);
                ps2.setString(2, username);
                ps2.execute();
            }
            for (String name : acc.names) {
                ps3.setInt(1, acc.id);
                ps3.setString(2, name);
                ps3.execute();
            }
            for (String email : acc.emails) {
                ps4.setInt(1, acc.id);
                ps4.setString(2, email);
                ps4.execute();
            }

            String username = null;
            String name = null;
            String email = null;
            if (acc.usernames.size() > 0) {
                username = acc.usernames.get(0);
            }
            if (acc.names.size() > 0) {
                name = acc.names.get(0);
            }
            if (acc.emails.size() > 0) {
                email = acc.emails.get(0);
            }

            ps1.setInt(1, acc.id);
            ps1.setInt(2, acc.accountId);
            if (isEmpty(username)) {
                ps1.setNull(3, Types.VARCHAR);
            } else {
                ps1.setString(3, username);
            }
            if (isEmpty(name)) {
                ps1.setNull(4, Types.VARCHAR);
            } else {
                ps1.setString(4, name);
            }
            if (isEmpty(email)) {
                ps1.setNull(5, Types.VARCHAR);
            } else {
                ps1.setString(5, email);
            }
            ps1.execute();
        }

        ps1.close();
        ps2.close();
        ps3.close();
        ps4.close();
        ps5.close();

        return accts;
    }

    @SuppressWarnings("unchecked")
    private static void processStats(Connection conn) throws Exception {
        PreparedStatement ps1 = conn.prepareStatement("insert into all_commits (project, commits, name, email) values (?,?,?,?);");
        PreparedStatement ps2 = conn.prepareStatement("insert into translations_commits (project, commits, name, email) values (?,?,?,?);");

        File statsDir = new File(STATS_DIR);
        Collection<File> allStats = FileUtils.listFiles(statsDir, new IOFileFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.equals(ALL_STATS_FILENAME);
            }

            @Override
            public boolean accept(File file) {
                return file.isFile() && file.getName().equals(ALL_STATS_FILENAME);
            }

        }, TrueFileFilter.INSTANCE);
        for (File stats : allStats) {
            String project = stats.getPath().replaceAll("\\\\", "/").replaceFirst(STATS_DIR, "");
            project = project.substring(0, project.lastIndexOf("/"));
            BufferedReader br = new BufferedReader(new FileReader(stats));
            String line = null;
            while ((line = br.readLine()) != null) {
                try {
                	int commits = Integer.parseInt(line.substring(0, line.indexOf("\t")).trim());
                	String name = cleanup(line.substring(line.indexOf("\t")+1,line.lastIndexOf("<")).trim());
	                String email = cleanup(line.substring(line.lastIndexOf("<")+1, line.length()-1).trim());
	
	                ps1.setString(1, project);
	                ps1.setInt(2, commits);
	                if (isEmpty(name)) {
	                    ps1.setNull(3, Types.VARCHAR);
	                } else {
	                    ps1.setString(3, name);
	                }
	                if (isEmpty(email)) {
	                    ps1.setNull(4, Types.VARCHAR);
	                } else {
	                    ps1.setString(4, email);
	                }
	                ps1.execute();
                } catch (Exception ex ) {
                	// Ignore this commit
                }
            }
            br.close();
        }

        Collection<File> translationStats = FileUtils.listFiles(statsDir, new IOFileFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.equals(TRANSLATIONS_STATS_FILENAME);
            }

            @Override
            public boolean accept(File file) {
                return file.isFile() && file.getName().equals(TRANSLATIONS_STATS_FILENAME);
            }

        }, TrueFileFilter.INSTANCE);
        for (File stats : translationStats) {
            String project = stats.getPath().replaceAll("\\\\", "/").replaceFirst(STATS_DIR, "");
            project = project.substring(0, project.lastIndexOf("/"));
            BufferedReader br = new BufferedReader(new FileReader(stats));
            String line = null;
            while ((line = br.readLine()) != null) {
                int commits = Integer.parseInt(line.substring(0, line.indexOf("\t")).trim());
                String name = cleanup(line.substring(line.indexOf("\t")+1,line.lastIndexOf("<")).trim());
                String email = cleanup(line.substring(line.lastIndexOf("<")+1, line.length()-1).trim());

                ps2.setString(1, project);
                ps2.setInt(2, commits);
                if (isEmpty(name)) {
                    ps2.setNull(3, Types.VARCHAR);
                } else {
                    ps2.setString(3, name);
                }
                if (isEmpty(email)) {
                    ps2.setNull(4, Types.VARCHAR);
                } else {
                    ps2.setString(4, email);
                }
                ps2.execute();
            }
            br.close();
        }

        ps1.close();
        ps2.close();
    }

    private static boolean isEmpty(String src) {
        return src == null || src.trim().length() == 0;
    }

    private static List<Account> readWellKnownAccounts() throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(WELL_KNOWN_ACCOUNTS));
        String line = null;
        List<Account> accounts = new ArrayList<>();
        int i = 1;
        while ((line = br.readLine()) != null) {
            if (line.length() == 0) continue;
            String[] fields = line.split("\\|");
            Account acc = new Account();
            acc.id = i;
            acc.names.add(fields[0].trim());
            String[] usernames = fields[1].trim().split("/");
            for (String username : usernames) {
                acc.usernames.add(username.trim());
            }
            for (int j = 2; j < fields.length; j++) {
                acc.emails.add(fields[j].trim());
            }
            accounts.add(acc);
            i++;
        }
        br.close();
        return accounts;
    }

    private static String readJson(File json, boolean hasheader) throws Exception {
        FileReader fr = new FileReader(json);
        if (hasheader) {
            fr.read(new char[5]);
        }
        StringBuffer sb = new StringBuffer();
        char[] data = new char[10240];
        int read = -1;
        while ((read = fr.read(data, 0, 10240)) != -1) {
            sb.append(data, 0, read);
        }
        fr.close();
        return sb.toString();
    }

    private static String cleanup(String src) {
        try {
            String dst = src;
            int s = src.indexOf("(");
            int e = src.indexOf(")");
            if (s != -1 && s < e) {
                dst = dst.substring(0, s) + dst.substring(e + 1);
            }
            s = dst.indexOf("[");
            e = dst.indexOf("]");
            if (s != -1 && s < e) {
                dst = dst.substring(0, s) + dst.substring(e + 1);
            }
            s = dst.indexOf("~");
            if (s != -1) {
                dst = dst.substring(0, s);
            }
            dst = dst.replaceAll("\\|", "");
            dst = dst.replaceAll("\u200e", "");
            dst = dst.replaceAll("\u200f", "");
            return dst.replaceAll("  ", " ").trim();
        } catch (NullPointerException e) {
            return src;
        }
    }

    private static boolean isLegalName(String name) {
        return name == null || !(name.contains("?") || name.equals("DO NOT USE"));
    }

    private static boolean isValidNameForSearch(String name) {
        return name.indexOf(" ") != -1 && name.length() >= 5 && !name.equals("John Smith") && !name.equals("John Doe");
    }

    private static boolean userHasCommits(int id, int accountId, String name, String email) {
        InputStream is = null;
        String url = null;

        String gerrit_username = System.getenv("GERRIT_USERNAME");
        String gerrit_password = System.getenv("GERRIT_PASSWORD");

        try {
            if (id <= wellKnownAccounts) {
                return true;
            }
            if (new File(ACCOUNTS_DIR, accountId + ".hasCommits").exists()) {
                return true;
            }
            File noHasCommits = new File(ACCOUNTS_DIR, accountId + ".noHasCommits");
            if (noHasCommits.exists() && (System.currentTimeMillis() - noHasCommits.lastModified()) < 2592000000L) { // 30 days
                return false;
            }

            url = "https://review.lineageos.org/changes/?q=status:merged+owner:\"" + email+ "\"&limit=1";
            email = URLEncoder.encode(email, "UTF-8");
            InputStream in = null;
            if (gerrit_username.isEmpty() || gerrit_password.isEmpty()) {
                is = new URL("https://review.lineageos.org/changes/?q=status:merged+owner:\"" + email+ "\"&limit=1").openStream();
            }
            else {
                URL ur = new URL("https://review.lineageos.org/a/changes/?q=status:merged+owner:\"" + email+ "\"&limit=1");
                URLConnection uc = ur.openConnection();
                String userpass = gerrit_username + ":" + gerrit_password;
                String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()));
                uc.setRequestProperty ("Authorization", basicAuth);
                is = uc.getInputStream();
            }
            byte[] data = new byte[11];
            int read = is.read(data);
            LOGGER.info ("Fetched " + url + ": " + (read > 8));
            return read > 8;
        } catch (Exception e) {
            LOGGER.info ("Fetched " + url + ": error");
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) { }
            }
        }
        return false;
    }

    public static void generateCloud(Connection conn) throws Exception {
        final List<WordFrequency> wordFrequencies = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(CLOUD_DATA));
        String line = null;
        while ((line = reader.readLine()) != null) {
            String record = line.trim();
            if (record.length() > 0) {
                int pos = record.indexOf(",");
                int pos2 = record.indexOf(",", pos+1);
                int pos3 = record.indexOf("|");
                int id = Integer.parseInt(record.substring(0, pos));
                int freq = Integer.parseInt(record.substring(pos+1, pos2));
                String word = record.substring(pos2+1, pos3);
                String filter = record.substring(pos3+1);
                if (word.length() <= 0) {
                    continue;
                }
                wordFrequencies.add(new WordFrequency(id, word, filter, freq));
            }
        }
        reader.close();

        int size = readLastCloudSize();
        int increment = DEFAULT_CLOUD_INCREMENT;
        while (true) {
            convertSvgToPng(CLOUD_BG_SVG, CLOUD_BG_PNG, 1024, size, null);

            final WordCloud wordCloud = new WordCloud(size, size, CollisionMode.RECTANGLE, true);
            final String[] EXTRA_WORDS = {"laos", "lineageos", "android", "aosp",
                    "nexus", "bacon", "adb", "apk", "dalvik", "droid", "logcat", "fastboot"};
            Font font = Font.createFont(Font.TRUETYPE_FONT, new FileInputStream(CLOUD_FONT));
            wordCloud.setCloudFont(new CloudFont(font));
            wordCloud.setPadding(1);
            wordCloud.setBackground(new PixelBoundryBackground(new FileInputStream(CLOUD_BG_PNG)));
            wordCloud.setBackgroundColor(new Color(0x474647));
            wordCloud.setColorPalette(new ColorPalette(new Color(0x6199b9)));
            FontScalar scalar = new SqrtFontScalar(12, 24);
            wordCloud.setFontScalar(scalar);
            wordCloud.build(wordFrequencies);

            boolean done = false;
            int i;
            for (i = 0; i <=4; i++) {
                scalar.reduceBy(1);
                if (wordCloud.fill(wordFrequencies, i+1)) {
                    done = true;
                    break;
                }
            }
            if (!done) {
                int newsize = size + DEFAULT_CLOUD_INCREMENT;
                LOGGER.info("Word cloud doesn't fit in " + size + "x" + size +
                        ". Retrying at " + newsize + "x" + newsize +" ...");
                wordCloud.writeToImage(CLOUD_IMAGE + "." + size + "x" + size + ".png");
                size += increment;
                continue;
            }
            scalar.reduceBy(4-i);
            wordCloud.printSkippedWords();
            wordCloud.fillWithOtherWords(wordFrequencies, EXTRA_WORDS);

            LOGGER.info("Saving wordcloud. Image size " + size + "x" + size);
            wordCloud.drawForgroundToBackground();
            wordCloud.writeToImage(CLOUD_IMAGE);
            wordCloud.writeWordsToFile(CLOUD_DATA_OUTPUT, size);
            break;
        }

        // Write metadainfo and save last size
        writeLastCloudSize(size);
        writeMetadataInfo(conn, size);

        // Write the cloud to an SVG
        writeToSvg(CLOUD_SVG, size, new Color(0x6199b9), new Color(0x474647));
    }

    private static void writeToSvg(String svg, int size, Color color, Color bgcolor) throws Exception {
        String c = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        String bg = String.format("#%02x%02x%02x", bgcolor.getRed(), bgcolor.getGreen(), bgcolor.getBlue());

        FileWriter fw = new FileWriter(svg);
        fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n");
        fw.write("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"\r\n");
        fw.write("viewBox=\"0 0 " + size + " " + size + "\"\r\n");
        fw.write("version=\"1.1\"\r\n");
        fw.write("width=\"" + size + "\"\r\n");
        fw.write("height=\"" + size + "\"\r\n");
        fw.write("text-rendering=\"geometricPrecision\">\r\n");
        fw.write("<defs >\r\n");
        fw.write("<font-face font-family=\"Roboto\">\r\n");
        fw.write("<font-face-src>\r\n");
        fw.write("<font-face-uri xlink:href=\"../Roboto-Bold.svg#Roboto-Bold\">\r\n");
        fw.write("<font-face-format string=\"svg\"/>\r\n");
        fw.write("</font-face-uri>\r\n");
        fw.write("</font-face-src>\r\n");
        fw.write("</font-face>\r\n");
        fw.write("</defs>\r\n");
        fw.write("<g>\r\n");
        fw.write("<rect width=\"" + size + "\" height=\"" + size + "\" style=\"fill: " + bg + ";\"/>\r\n");

        BufferedReader br = new BufferedReader(new FileReader(CLOUD_DATA_OUTPUT));
        String line=null;
        br.readLine();
        while ((line = br.readLine()) != null) {
            int lastIndex = 0;
            int index = line.indexOf(',', lastIndex);
            lastIndex = index+1;
            index = line.indexOf(',', lastIndex);
            float x = Integer.parseInt(line.substring(lastIndex, index));
            lastIndex = index+1;
            index = line.indexOf(',', lastIndex);
            float y = Integer.parseInt(line.substring(lastIndex, index));
            lastIndex = index+1;
            index = line.indexOf(',', lastIndex);
            float w = Integer.parseInt(line.substring(lastIndex, index));
            lastIndex = index+1;
            index = line.indexOf(',', lastIndex);
            float h = Integer.parseInt(line.substring(lastIndex, index));
            lastIndex = index+1;
            index = line.indexOf(',', lastIndex);
            int r = Integer.parseInt(line.substring(lastIndex, index));
            lastIndex = index+1;
            index = line.indexOf(',', lastIndex);
            float fs = Float.parseFloat(line.substring(lastIndex, index));
            lastIndex = index+1;
            String xx = line.substring(lastIndex);
            String n = xx.substring(0, xx.indexOf("|"));
            if (r == 0) {
                fw.write("<text x=\"" + x + "\" y=\"" + y + "\" font-family=\"'Roboto'\" font-size=\"" + fs + "\" style=\"fill: " + c + "\">" + n + "</text>\r\n");
            } else {
                if (r == -1) {
                    fw.write("<text x=\"" + x + "\" y=\"" + y + "\" transform=\"translate(" + (w / 2) + "," + (h - (w / 2)) + ")rotate(-90 " + x + "," + y + ")\" font-family=\"'Roboto'\" font-size=\"" + fs + "\" style=\"fill: " + c + "\">" + n + "</text>\r\n");
                } else {
                    fw.write("<text x=\"" + x + "\" y=\"" + y + "\" transform=\"translate(0,-" + (w / 2) + ")rotate(90 " + x + "," + y + ")\" font-family=\"'Roboto'\" font-size=\"" + fs + "\" style=\"fill: " + c + "\">" + n + "</text>\r\n");
                }
            }
        }
        br.close();
        fw.write("</g>\r\n");
        fw.write("</svg>\r\n");
        fw.close();
    }

    private static void convertSvgToPng(String svg, String png, int origSize, int dstSize, Color bg) throws Exception {
        String svg_URI_input = Paths.get(svg).toUri().toURL().toString();
        TranscoderInput input_svg_image = new TranscoderInput(svg_URI_input);
        OutputStream png_ostream = new FileOutputStream(png);
        TranscoderOutput output_png_image = new TranscoderOutput(png_ostream);
        PNGTranscoder my_converter = new PNGTranscoder();
        my_converter.addTranscodingHint( PNGTranscoder.KEY_WIDTH, new Float( dstSize ) );
        my_converter.addTranscodingHint( PNGTranscoder.KEY_HEIGHT, new Float( dstSize ) );
        my_converter.addTranscodingHint( PNGTranscoder.KEY_AOI, new Rectangle( 0, 0, origSize, origSize) );
        if (bg != null) {
            my_converter.addTranscodingHint( PNGTranscoder.KEY_BACKGROUND_COLOR, bg);
        }
        my_converter.transcode(input_svg_image, output_png_image);
        png_ostream.flush();
        png_ostream.close();
    }

    private static int readLastCloudSize() throws Exception {
        File file = new File(LAST_CLOUD_SIZE);
        if (!file.exists()) {
            return DEFAULT_CLOUD_SIZE;
        }
        BufferedReader br = new BufferedReader(new FileReader(file));
        try {
            return Integer.parseInt(br.readLine());
        } catch (Exception ex) {
        } finally {
            br.close();
        }
        return DEFAULT_CLOUD_SIZE;
    }

    private static void writeLastCloudSize(int size) throws Exception {
        File file = new File(LAST_CLOUD_SIZE);
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        bw.write(String.valueOf(size));
        bw.close();
    }
}
