// SPDX-License-Identifier: AGPL-3.0-only
package cattv.ratings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Ratings {
    private static final String PREF = "cattv_mdb_private";
    private static final String TAG = "cattv_mdb_settings";
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Map<TextView,Runnable> pending = new WeakHashMap<>();
    private static final Map<String,JSONArray> cache = new LinkedHashMap<>();
    private static long retryAfter;

    private static String key(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("key", "");
    }

    public static String version(Activity activity) {
        try {
            android.content.pm.PackageInfo info=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);
            return "CatTV v"+info.versionCode+" · ARM32";
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            return "CatTV TV · ARM32";
        }
    }

    public static void setup(Activity activity) {
        View row = activity.findViewById(0x7f0b0487);
        if (row == null || !(row.getParent() instanceof ViewGroup)) return;
        ViewGroup list = (ViewGroup) row.getParent();
        if (list.findViewWithTag(TAG) != null) return;
        Button button = new Button(activity);
        button.setTag(TAG);
        button.setAllCaps(false);
        button.setText("MDBList API Key");
        button.setOnClickListener(v -> {
            EditText field = new EditText(activity);
            field.setSingleLine();
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            field.setText(key(activity));
            new AlertDialog.Builder(activity).setTitle("MDBList API Key")
                .setMessage("登录 mdblist.com，在 Preferences 申请 API Key。Key 保存在本机，仅用于向 MDBList 查询评分。留空关闭额外评分。")
                .setView(field).setPositiveButton("保存", (d,w) -> {
                    String entered = field.getText().toString().trim();
                    activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                        .edit().putString("key", entered).apply();
                    synchronized (cache) { cache.clear(); retryAfter = 0; }
                    if (!entered.isEmpty()) IO.execute(() -> {
                        int status = checkKey(entered);
                        UI.post(() -> {
                            if (activity.isFinishing() || activity.isDestroyed() || !entered.equals(key(activity))) return;
                            String message = status == 200 ? "MDBList 连接成功。返回影片详情查看评分。"
                                : status == 401 || status == 403 ? "MDBList 拒绝了 Key，请检查是否复制完整。"
                                : status == 429 ? "MDBList 查询次数已达上限，请稍后再试。"
                                : "暂时无法验证 MDBList，请检查网络。";
                            new AlertDialog.Builder(activity).setTitle("MDBList").setMessage(message)
                                .setPositiveButton(android.R.string.ok,null).show();
                        });
                    });
                }).setNegativeButton(android.R.string.cancel,null).show();
        });
        list.addView(button,list.indexOfChild(row)+1,new ViewGroup.LayoutParams(-1,-2));
    }

    public static void bind(TextView view, String id, String media) {
        Runnable previous = pending.remove(view);
        if (previous != null) UI.removeCallbacks(previous);
        String token = key(view.getContext());
        if (token.isEmpty() || id == null || !id.matches("[1-9][0-9]{0,9}")) return;
        String kind = "tv".equals(media) ? "show" : "movie".equals(media) ? "movie" : "";
        if (kind.isEmpty()) return;
        CharSequence base = view.getText();
        Runnable job = () -> IO.execute(() -> {
            JSONArray ratings = fetch(kind + "/" + id, token);
            if (ratings == null) return;
            UI.post(() -> {
                if (!view.isAttachedToWindow() || view.getText() != base ||
                    !token.equals(key(view.getContext()))) return;
                SpannableStringBuilder line = new SpannableStringBuilder(base);
                add(line,ratings,"imdb","IMDb",0xfff5cf3f,false);
                add(line,ratings,"tomatoes","烂番茄",0xffd84848,true);
                add(line,ratings,"popcorn","爆米花",0xffb53f78,true);
                add(line,ratings,"metacritic","MC",0xff60b93d,false);
                if (!base.toString().contains("Trakt")) add(line,ratings,"trakt","Trakt",0xffbb3472,true);
                if (line.length() != base.length()) view.setText(line);
            });
        });
        pending.put(view,job);
        UI.postDelayed(job,400);
    }

    private static void add(SpannableStringBuilder line,JSONArray items,String source,String label,int color,boolean percent) {
        for (int i=0;i<items.length();i++) {
            JSONObject item=items.optJSONObject(i);
            if (item==null || !source.equalsIgnoreCase(item.optString("source"))) continue;
            double value=item.optDouble("value",-1);
            if (!(value>0 && value<=("imdb".equals(source)?10:100))) continue;
            String number=java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
            line.append("   ");
            int start=line.length();
            line.append(" ").append(label).append(" ");
            line.setSpan(new BackgroundColorSpan(color),start,line.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            line.setSpan(new ForegroundColorSpan("imdb".equals(source)?0xff151515:0xffffffff),start,line.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            line.setSpan(new StyleSpan(Typeface.BOLD),start,line.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            line.append(" ").append(number);
            if(percent) line.append("%");
            return;
        }
    }

    private static int checkKey(String token) {
        HttpsURLConnection connection = null;
        try {
            connection = connect("movie/550",token);
            return connection.getResponseCode();
        } catch (Exception ignored) { return -1; }
        finally { if (connection != null) connection.disconnect(); }
    }

    private static HttpsURLConnection connect(String path,String token) throws Exception {
        HttpsURLConnection connection=(HttpsURLConnection)new URL("https://api.mdblist.com/tmdb/"+path+"?apikey="+URLEncoder.encode(token,"UTF-8")).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        return connection;
    }

    private static JSONArray fetch(String path,String token) {
        synchronized(cache) {
            if(cache.containsKey(path)) return cache.get(path);
            if (android.os.SystemClock.elapsedRealtime() < retryAfter) return null;
        }
        HttpsURLConnection c=null;
        try {
            c=connect(path,token);
            int status=c.getResponseCode();
            if(status==401||status==403||status==429) {
                synchronized(cache) { retryAfter=android.os.SystemClock.elapsedRealtime()+120000; }
                return null;
            }
            if(status!=200) return null;
            ByteArrayOutputStream buf=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()) {
                byte[] b=new byte[4096]; int n;
                while((n=in.read(b))!=-1) { if(buf.size()+n>262144) return null; buf.write(b,0,n); }
            }
            Object raw=new org.json.JSONTokener(buf.toString("UTF-8")).nextValue();
            JSONObject item=raw instanceof JSONArray ? ((JSONArray)raw).optJSONObject(0) :
                raw instanceof JSONObject ? (JSONObject)raw : null;
            if(item==null) return null;
            JSONArray rows=item.optJSONArray("ratings");
            if(rows==null) rows=new JSONArray();
            synchronized(cache) { if(cache.size()>=160) cache.remove(cache.keySet().iterator().next()); cache.put(path,rows); }
            return rows;
        } catch(Exception ignored) { return null; }
        finally { if(c!=null) c.disconnect(); }
    }
}
