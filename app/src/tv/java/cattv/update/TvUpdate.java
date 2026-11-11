package cattv.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

public final class TvUpdate {
    private static final String REPO = "https://github.com/cx342662/CatTV";
    private static final String API = "https://api.github.com/repos/cx342662/CatTV/releases?per_page=30";
    private static volatile WeakReference<Activity> current = new WeakReference<>(null);
    private static long checked = -1;
    public static synchronized void check(Activity activity) {
        current = new WeakReference<>(activity);
        long now = SystemClock.elapsedRealtime();
        if (checked >= 0 && now - checked < 900000) return;
        checked = now;
        new Thread(TvUpdate::fetch, "CatTV-TV-Update").start();
    }
    private static void fetch() {
        HttpsURLConnection connection = null;
        try {
            Activity activity = current.get();
            if (activity == null) return;
            int installed = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;
            connection = (HttpsURLConnection) new URL(API).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            if (connection.getResponseCode() != 200) return;
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream stream = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = stream.read(buffer)) != -1) {
                    if (body.size() + n > 1048576) return;
                    body.write(buffer, 0, n);
                }
            }
            JSONArray releases = new JSONArray(body.toString("UTF-8"));
            int newest = installed;
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.getJSONObject(i);
                String tag = release.optString("tag_name");
                if (release.optBoolean("draft") || !tag.matches("tv-arm32-v[0-9]{1,8}")) continue;
                int version = Integer.parseInt(tag.substring(10));
                JSONArray assets = release.optJSONArray("assets");
                if (assets == null || version <= newest) continue;
                String filename = "CatTV-TV-ARM32-v" + version + ".apk";
                for (int j = 0; j < assets.length(); j++) {
                    JSONObject asset = assets.getJSONObject(j);
                    if (filename.equals(asset.optString("name")) && asset.optLong("size") > 0 &&
                        (REPO + "/releases/download/" + tag + "/" + filename).equals(asset.optString("browser_download_url"))) {
                        newest = version;
                        break;
                    }
                }
            }
            if (newest <= installed) return;
            final int version = newest;
            activity = current.get();
            if (activity != null) activity.runOnUiThread(() -> prompt(version));
        } catch (Exception ignored) {
            // Network or rate-limit failures never block the TV home.
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
    private static void prompt(int version) {
        Activity activity = current.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || !activity.hasWindowFocus()) return;
        String page = REPO + "/releases/tag/tv-arm32-v" + version;
        new AlertDialog.Builder(activity)
            .setTitle("CatTV TV v" + version)
            .setMessage("发现 TV 新版本。打开 GitHub 发布页下载，安装时由系统确认。")
            .setNegativeButton("稍后", null)
            .setPositiveButton("打开发布页", (dialog, which) -> {
                try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(page))); }
                catch (android.content.ActivityNotFoundException ignored) {
                    new AlertDialog.Builder(activity).setMessage(page).setPositiveButton(android.R.string.ok, null).show();
                }
            }).show();
    }
}
