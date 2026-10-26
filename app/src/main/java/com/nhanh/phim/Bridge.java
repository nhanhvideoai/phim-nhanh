package com.nhanh.phim;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import java.io.ByteArrayOutputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Cầu nối giữa giao diện (HTML) và Android. */
public class Bridge {
  final MainActivity a;
  Bridge(MainActivity a) { this.a = a; }

  @JavascriptInterface public String cauHinh() { return CauHinh.doc(a).toString(); }
  @JavascriptInterface public void luu(String json) { CauHinh.luu(a, json); }

  @JavascriptInterface public String trangThai() {
    try {
      return new JSONObject()
          .put("troNang", HanhDong.troNangDangBat(a))
          .put("troLy", HanhDong.laTroLyMacDinh(a))
          .put("phienBan", a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName)
          .toString();
    } catch (Exception e) { return "{}"; }
  }

  @JavascriptInterface public String dsApp() {
    PackageManager pm = a.getPackageManager();
    Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    List<ResolveInfo> l = pm.queryIntentActivities(i, 0);
    List<String[]> ds = new ArrayList<>();
    java.util.HashSet<String> da = new java.util.HashSet<>();
    for (ResolveInfo r : l) {
      String p = r.activityInfo.packageName;
      if (p.equals(a.getPackageName()) || !da.add(p)) continue;
      ds.add(new String[] {p, String.valueOf(r.loadLabel(pm))});
    }
    Collator col = Collator.getInstance(new Locale("vi"));
    ds.sort((x, y) -> col.compare(x[1], y[1]));
    JSONArray arr = new JSONArray();
    for (String[] d : ds) {
      try { arr.put(new JSONObject().put("pkg", d[0]).put("ten", d[1])); } catch (Exception ignored) {}
    }
    return arr.toString();
  }

  @JavascriptInterface public String icon(String pkg) {
    try {
      Drawable d = a.getPackageManager().getApplicationIcon(pkg);
      int s = 96;
      Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
      Canvas c = new Canvas(b);
      d.setBounds(0, 0, s, s);
      d.draw(c);
      ByteArrayOutputStream o = new ByteArrayOutputStream();
      b.compress(Bitmap.CompressFormat.PNG, 100, o);
      return "data:image/png;base64," + Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
    } catch (Exception e) { return ""; }
  }

  @JavascriptInterface public void moTroNang() {
    a.runOnUiThread(() -> {
      try {
        Intent i = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
        i.putExtra(Intent.EXTRA_COMPONENT_NAME, new android.content.ComponentName(a, PhimService.class).flattenToString());
        a.startActivity(i);
      } catch (Exception e) {
        a.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
      }
    });
  }

  @JavascriptInterface public void moTroLy() {
    a.runOnUiThread(() -> {
      try { a.startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)); }
      catch (Exception e) { a.startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    });
  }

  @JavascriptInterface public void thu(String json) {
    try { JSONObject o = new JSONObject(json); a.runOnUiThread(() -> HanhDong.chay(a, o)); } catch (Exception ignored) {}
  }

  @JavascriptInterface public void moWeb(String url) {
    a.runOnUiThread(() -> a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
  }
}
