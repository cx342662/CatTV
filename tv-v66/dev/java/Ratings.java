// SPDX-License-Identifier: AGPL-3.0-only
package cattv.ratings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
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
    private static final Object CACHE_LOCK = new Object();
    private static final int CACHE_LIMIT = 160;
    private static final long CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000L;
    private static long cacheGeneration;
    private static long retryAfter;

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static String key(Context context) {
        return preferences(context).getString("key", "");
    }

    private static SharedPreferences ratingCache(Context context) {
        return context.getSharedPreferences("cattv_rating_cache", Context.MODE_PRIVATE);
    }

    private static void resetCache(Context context) {
        synchronized (CACHE_LOCK) {
            cacheGeneration++;
            retryAfter = 0;
            ratingCache(context).edit().clear().apply();
        }
    }

    public static String saveRemote(Context context, Map<?, ?> fields, Map<?, ?> headers) {
        if (!"mdblist".equals(headers.get("x-cattv-settings"))) return "REJECTED";
        Object raw = fields.get("key");
        if (!(raw instanceof String)) return "REJECTED";
        String entered = ((String) raw).trim();
        if (entered.isEmpty() || entered.length() > 256 || !entered.matches("[!-~]+")) {
            return "REJECTED";
        }
        if (!entered.equals(key(context))) {
            if (!preferences(context).edit().putString("key", entered).commit()) return "REJECTED";
            resetCache(context);
        }
        return "SAVED";
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
        LinearLayout entry = new LinearLayout(activity);
        entry.setTag(TAG);
        entry.setFocusable(true);
        entry.setGravity(Gravity.CENTER_VERTICAL);
        float density = activity.getResources().getDisplayMetrics().density;
        int padding = Math.round(16 * density);
        entry.setPaddingRelative(padding, 0, padding, 0);
        entry.setMinimumHeight(Math.round(48 * density));

        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] {android.R.attr.state_focused},
            settingsBackground(0xff30445d, 0xff9fd4ff, density));
        background.addState(new int[0], settingsBackground(0xff242e3d, 0xff242e3d, density));
        entry.setBackground(background);

        TextView label = new TextView(activity);
        label.setText("MDBList API Key");
        label.setTextColor(Color.WHITE);
        label.setTextSize(16);
        entry.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

        TextView statusLabel = new TextView(activity);
        statusLabel.setTextColor(0xffc6dbef);
        statusLabel.setTextSize(14);
        entry.addView(statusLabel);
        SharedPreferences prefs = preferences(activity);
        Runnable updateStatus = () -> statusLabel.setText(key(activity).isEmpty()
            ? "未配置  ›" : "已配置  ›");
        SharedPreferences.OnSharedPreferenceChangeListener listener = (changed, name) -> {
            if ("key".equals(name)) updateStatus.run();
        };
        entry.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                prefs.registerOnSharedPreferenceChangeListener(listener);
                updateStatus.run();
            }
            @Override public void onViewDetachedFromWindow(View view) {
                prefs.unregisterOnSharedPreferenceChangeListener(listener);
            }
        });
        entry.setOnClickListener(v -> {
            EditText field = new EditText(activity);
            field.setSingleLine();
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            field.setText(key(activity));
            new AlertDialog.Builder(activity).setTitle("MDBList API Key")
                .setMessage("登录 mdblist.com，在 Preferences 申请 API Key。Key 保存在本机，仅用于向 MDBList 查询评分。留空关闭额外评分。")
                .setView(field).setPositiveButton("保存", (d,w) -> {
                    String entered = field.getText().toString().trim();
                    if (!entered.equals(key(activity))) {
                        prefs.edit().putString("key", entered).apply();
                        resetCache(activity);
                    }
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
        ViewGroup.MarginLayoutParams layout = new ViewGroup.MarginLayoutParams(-1, -2);
        layout.topMargin = Math.round(12 * density);
        list.addView(entry, list.indexOfChild(row) + 1, layout);

        Button clear = new Button(activity);
        clear.setAllCaps(false);
        clear.setText("清理评分缓存");
        clear.setTextColor(Color.WHITE);
        clear.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        clear.setPaddingRelative(padding, 0, padding, 0);
        clear.setBackground(background.getConstantState().newDrawable().mutate());
        clear.setOnClickListener(v -> new AlertDialog.Builder(activity)
            .setTitle("清理评分缓存")
            .setMessage("清理后，打开影片时会重新获取评分。")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("清理", (dialog, which) -> {
                resetCache(activity);
                android.widget.Toast.makeText(activity, "评分缓存已清理",
                    android.widget.Toast.LENGTH_SHORT).show();
            }).show());
        ViewGroup.MarginLayoutParams clearLayout = new ViewGroup.MarginLayoutParams(-1, -2);
        clearLayout.topMargin = Math.round(8 * density);
        list.addView(clear, list.indexOfChild(entry) + 1, clearLayout);
    }

    private static GradientDrawable settingsBackground(int color, int border, float density) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(8 * density);
        drawable.setStroke(Math.round(2 * density), border);
        return drawable;
    }

    public static void bind(TextView view, String id, String media) {
        Runnable previous = pending.remove(view);
        if (previous != null) UI.removeCallbacks(previous);
        String token = key(view.getContext());
        if (token.isEmpty() || id == null || !id.matches("[1-9][0-9]{0,9}")) return;
        String kind = "tv".equals(media) ? "show" : "movie".equals(media) ? "movie" : "";
        if (kind.isEmpty()) return;
        Context context = view.getContext().getApplicationContext();
        String path = kind + "/" + id;
        CharSequence base = view.getText();
        long generation;
        JSONArray saved;
        synchronized (CACHE_LOCK) {
            generation = cacheGeneration;
            saved = readCached(context, path);
        }
        if (saved != null) {
            render(view, base, saved);
            return;
        }
        Runnable job = () -> {
            pending.remove(view);
            IO.execute(() -> {
                JSONArray ratings = fetch(context, path, token, generation);
                if (ratings == null) return;
                UI.post(() -> {
                    synchronized (CACHE_LOCK) {
                        if (generation != cacheGeneration) return;
                    }
                    if (!view.isAttachedToWindow() || view.getText() != base ||
                        !token.equals(key(context))) return;
                    render(view, base, ratings);
                });
            });
        };
        pending.put(view, job);
        UI.postDelayed(job, 400);
    }

    private static void render(TextView view, CharSequence base, JSONArray ratings) {
        SpannableStringBuilder line = new SpannableStringBuilder(base);
        add(line, ratings, "imdb", "IMDb", 0xffffde55, 0xffffbe26, false);
        if (!base.toString().contains("Trakt")) {
            add(line, ratings, "trakt", "✓", 0xffee3459, 0xff9c3fb8, false);
        }
        add(line, ratings, "tomatoes", "🍅", 0, 0, true);
        add(line, ratings, "popcorn", "🍿", 0, 0, true);
        add(line, ratings, "metacritic", "MC", 0xff83db40, 0xff47a91f, false);
        if (line.length() != base.length()) view.setText(line);
    }

    private static JSONArray readCached(Context context, String path) {
        String entry = ratingCache(context).getString(path, "");
        if (entry.isEmpty()) return null;
        try {
            JSONArray envelope = new JSONArray(entry);
            long age = System.currentTimeMillis() - envelope.getLong(0);
            return age >= 0 && age < CACHE_TTL_MS ? envelope.optJSONArray(1) : null;
        } catch (org.json.JSONException ignored) {
            return null;
        }
    }

    private static void writeCached(Context context, String path, JSONArray rows) {
        SharedPreferences prefs = ratingCache(context);
        Map<String, ?> entries = prefs.getAll();
        SharedPreferences.Editor edit = prefs.edit();
        if (!entries.containsKey(path) && entries.size() >= CACHE_LIMIT) {
            String oldest = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                long time = 0;
                try {
                    time = new JSONArray((String) entry.getValue()).getLong(0);
                } catch (org.json.JSONException | ClassCastException ignored) {
                    // Remove unreadable cache records first.
                }
                if (time < oldestTime) {
                    oldest = entry.getKey();
                    oldestTime = time;
                }
            }
            if (oldest != null) edit.remove(oldest);
        }
        JSONArray envelope = new JSONArray().put(System.currentTimeMillis()).put(rows);
        edit.putString(path, envelope.toString()).apply();
    }

    private static void add(SpannableStringBuilder line, JSONArray items, String source,
                            String label, int light, int dark, boolean percent) {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null || !source.equalsIgnoreCase(item.optString("source"))) continue;
            double value = item.optDouble("value", -1);
            if (!(value > 0 && value <= ("imdb".equals(source) ? 10 : 100))) continue;
            if ("trakt".equals(source)) value /= 10;
            String number = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
            if (percent) number += "%";
            line.append("  ");
            int start = line.length();
            line.append(label).append(" ").append(number);
            line.setSpan(new ScoreBadge(label, number, light, dark), start, line.length(),
                         Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return;
        }
    }

    private static final class ScoreBadge extends ReplacementSpan {
        private final String label;
        private final String score;
        private final int light;
        private final int dark;

        ScoreBadge(String label, String score, int light, int dark) {
            this.label = label;
            this.score = score;
            this.light = light;
            this.dark = dark;
        }

        private Paint bold(Paint original, float scale) {
            Paint paint = new Paint(original);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(original.getTextSize() * scale);
            paint.setAntiAlias(true);
            return paint;
        }

        private float badgeWidth(Paint paint) {
            float size = paint.getTextSize();
            if ("✓".equals(label)) return size * 1.12f;
            return bold(paint, light == 0 ? 1.03f : .76f).measureText(label)
                + (light == 0 ? 0 : size * .52f);
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end,
                           Paint.FontMetricsInt metrics) {
            return (int) Math.ceil(badgeWidth(paint) + paint.getTextSize() * .28f
                + bold(paint, 1.06f).measureText(score));
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                         int top, int y, int bottom, Paint original) {
            float size = original.getTextSize();
            float width = badgeWidth(original);
            float middle = y + (original.ascent() + original.descent()) / 2f;
            Paint mark = bold(original, light == 0 ? 1.03f : .76f);
            if (light != 0) {
                float halfHeight = size * .56f;
                float radius = "✓".equals(label) ? halfHeight : size * .23f;
                mark.clearShadowLayer();
                mark.setShader(new LinearGradient(x, middle - halfHeight, x + width,
                    middle + halfHeight, light, dark, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(x, middle - halfHeight, x + width, middle + halfHeight,
                                     radius, radius, mark);
                mark.setShader(null);
            }
            mark.setColor("IMDb".equals(label) ? 0xff19130a : Color.WHITE);
            canvas.drawText(label, x + (width - mark.measureText(label)) / 2f,
                            middle - (mark.ascent() + mark.descent()) / 2f, mark);
            Paint number = bold(original, 1.06f);
            number.setColor(Color.WHITE);
            canvas.drawText(score, x + width + size * .28f, y, number);
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

    private static JSONArray fetch(Context context, String path, String token, long generation) {
        synchronized (CACHE_LOCK) {
            if (generation != cacheGeneration || !token.equals(key(context))) return null;
            JSONArray saved = readCached(context, path);
            if (saved != null) return saved;
            if (android.os.SystemClock.elapsedRealtime() < retryAfter) return null;
        }
        HttpsURLConnection c=null;
        try {
            c=connect(path,token);
            int status=c.getResponseCode();
            if(status==401||status==403||status==429) {
                synchronized (CACHE_LOCK) {
                    if (generation == cacheGeneration) {
                        retryAfter = android.os.SystemClock.elapsedRealtime() + 120000;
                    }
                }
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
            synchronized (CACHE_LOCK) {
                if (generation != cacheGeneration || !token.equals(key(context))) return null;
                if (rows.length() > 0 && rows.toString().length() <= 8192) {
                    writeCached(context, path, rows);
                }
            }
            return rows;
        } catch(Exception ignored) { return null; }
        finally { if(c!=null) c.disconnect(); }
    }
}
