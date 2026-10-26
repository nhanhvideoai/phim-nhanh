package com.nhanh.phim;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Tự cập nhật từ GitHub Releases.
 * - Mặc định bật; người dùng tắt được trong Cài đặt của app.
 * - Kiểm tra khi mở app và định kỳ khoảng 6 tiếng (JobScheduler, cần mạng).
 * - So sánh tên bản (vd 1.0.12) với tag mới nhất (vd v1.0.13), tải APK rồi cài bằng PackageInstaller.
 */
public final class CapNhat {
  public static final String KHO = "nhanhvideoai/phim-nhanh";          // chủ/kho trên GitHub
  static final int JOB_ID = 7301;
  static final String KENH = "cap_nhat";

  public interface KetQua { void xong(JSONObject trangThai); }

  static SharedPreferences sp(Context c) { return c.getSharedPreferences("cap_nhat", Context.MODE_PRIVATE); }
  public static boolean tuDong(Context c) { return sp(c).getBoolean("tu_dong", true); }
  public static void datTuDong(Context c, boolean b) { sp(c).edit().putBoolean("tu_dong", b).apply(); lenLich(c); }

  public static String phienBan(Context c) {
    try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Exception e) { return "0"; }
  }

  public static JSONObject trangThai(Context c) {
    JSONObject o = new JSONObject();
    try {
      o.put("tuDong", tuDong(c)).put("phienBan", phienBan(c))
       .put("banMoi", sp(c).getString("ban_moi", "")).put("thongBao", sp(c).getString("thong_bao", ""))
       .put("lanKiemTra", sp(c).getLong("lan_kiem_tra", 0));
    } catch (Exception ignored) {}
    return o;
  }

  static void ghi(Context c, String thongBao) { sp(c).edit().putString("thong_bao", thongBao).apply(); }

  /** Lên lịch kiểm tra định kỳ (gọi khi mở app và sau khi khởi động máy). */
  public static void lenLich(Context c) {
    JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    if (js == null) return;
    if (!tuDong(c)) { js.cancel(JOB_ID); return; }
    JobInfo j = new JobInfo.Builder(JOB_ID, new ComponentName(c, CapNhatJob.class))
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        .setPeriodic(6 * 60 * 60 * 1000L)
        .setPersisted(true)
        .build();
    try { js.schedule(j); } catch (Exception ignored) {}
  }

  /** So sánh "1.0.12" với "1.0.13": trả về số dương nếu a mới hơn b. */
  static int soSanh(String a, String b) {
    String[] x = a.replaceAll("[^0-9.]", "").split("\\."), y = b.replaceAll("[^0-9.]", "").split("\\.");
    for (int i = 0; i < Math.max(x.length, y.length); i++) {
      long p = i < x.length && !x[i].isEmpty() ? Long.parseLong(x[i]) : 0;
      long q = i < y.length && !y[i].isEmpty() ? Long.parseLong(y[i]) : 0;
      if (p != q) return p > q ? 1 : -1;
    }
    return 0;
  }

  /** Kiểm tra (chạy nền). caiLuon=true: có bản mới thì tải và cài ngay. */
  public static void kiemTra(Context ctx, boolean caiLuon, KetQua kq) {
    final Context c = ctx.getApplicationContext();
    new Thread(() -> {
      try {
        JSONObject r = new JSONObject(doc("https://api.github.com/repos/" + KHO + "/releases/latest"));
        String tag = r.optString("tag_name").replaceFirst("^v", "");
        String apk = null;
        JSONArray as = r.optJSONArray("assets");
        if (as != null) for (int i = 0; i < as.length(); i++) {
          String n = as.getJSONObject(i).optString("name");
          if (n.endsWith(".apk")) { apk = as.getJSONObject(i).optString("browser_download_url"); break; }
        }
        sp(c).edit().putLong("lan_kiem_tra", System.currentTimeMillis()).apply();
        if (apk == null || soSanh(tag, phienBan(c)) <= 0) {
          sp(c).edit().putString("ban_moi", "").apply();
          ghi(c, "Đang dùng bản mới nhất");
        } else {
          sp(c).edit().putString("ban_moi", tag).apply();
          ghi(c, "Có bản mới " + tag);
          if (caiLuon) taiVaCai(c, apk, tag);
        }
      } catch (Exception e) {
        ghi(c, "Không kiểm tra được (kiểm tra mạng)");
      }
      if (kq != null) new Handler(Looper.getMainLooper()).post(() -> kq.xong(trangThai(c)));
    }).start();
  }

  static String doc(String u) throws Exception {
    HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
    h.setRequestProperty("Accept", "application/vnd.github+json");
    h.setRequestProperty("User-Agent", "nhanh-app");
    h.setConnectTimeout(15000); h.setReadTimeout(20000);
    try (InputStream in = h.getInputStream()) {
      java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
      byte[] b = new byte[8192]; int n;
      while ((n = in.read(b)) > 0) o.write(b, 0, n);
      return o.toString("UTF-8");
    } finally { h.disconnect(); }
  }

  static void taiVaCai(Context c, String url, String tag) throws Exception {
    if (Build.VERSION.SDK_INT >= 26 && !c.getPackageManager().canRequestPackageInstalls()) {
      ghi(c, "Cần cho phép \"Cài ứng dụng không rõ nguồn\" để tự cập nhật");
      Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName()));
      thongBao(c, "Có bản mới " + tag, "Bấm để cho phép app tự cập nhật", i);
      return;
    }
    ghi(c, "Đang tải bản " + tag + "…");
    File f = new File(c.getCacheDir(), "ban_moi.apk");
    HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
    h.setInstanceFollowRedirects(true);
    h.setRequestProperty("User-Agent", "nhanh-app");
    h.setConnectTimeout(15000); h.setReadTimeout(60000);
    int code = h.getResponseCode();
    if (code >= 300 && code < 400) {                       // GitHub chuyển sang máy chủ khác
      String moi = h.getHeaderField("Location"); h.disconnect();
      h = (HttpURLConnection) new URL(moi).openConnection();
      h.setRequestProperty("User-Agent", "nhanh-app");
    }
    try (InputStream in = h.getInputStream(); OutputStream out = new FileOutputStream(f)) {
      byte[] b = new byte[65536]; int n;
      while ((n = in.read(b)) > 0) out.write(b, 0, n);
    } finally { h.disconnect(); }

    PackageInstaller pi = c.getPackageManager().getPackageInstaller();
    PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
    p.setAppPackageName(c.getPackageName());
    if (Build.VERSION.SDK_INT >= 31) p.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
    int id = pi.createSession(p);
    try (PackageInstaller.Session s = pi.openSession(id)) {
      try (OutputStream o = s.openWrite("ban_moi.apk", 0, f.length()); InputStream in = new java.io.FileInputStream(f)) {
        byte[] b = new byte[65536]; int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        s.fsync(o);
      }
      Intent kq = new Intent(c, CapNhatXong.class).putExtra("tag", tag);
      int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
      PendingIntent pend = PendingIntent.getBroadcast(c, id, kq, fl);
      s.commit(pend.getIntentSender());
    }
    ghi(c, "Đang cài bản " + tag + "…");
  }

  static void thongBao(Context c, String tieuDe, String noiDung, Intent mo) {
    NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= 26)
      nm.createNotificationChannel(new NotificationChannel(KENH, "Cập nhật app", NotificationManager.IMPORTANCE_DEFAULT));
    mo.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    PendingIntent pi = PendingIntent.getActivity(c, 7302, mo, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    android.app.Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(c, KENH) : new android.app.Notification.Builder(c);
    b.setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle(tieuDe).setContentText(noiDung)
     .setContentIntent(pi).setAutoCancel(true);
    try { nm.notify(7303, b.build()); } catch (SecurityException ignored) {}
  }
}
