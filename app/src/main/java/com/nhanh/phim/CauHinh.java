package com.nhanh.phim;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** Lưu cài đặt: mỗi nút một mục {bat, loai, pkg, ten}. */
public final class CauHinh {
  public static final String[] NUT = {"nguon_giu", "len_giu", "len_2lan", "xuong_giu", "xuong_2lan"};

  static SharedPreferences sp(Context c) { return c.getSharedPreferences("cau_hinh", Context.MODE_PRIVATE); }

  public static JSONObject doc(Context c) {
    try {
      String s = sp(c).getString("json", null);
      if (s != null) return new JSONObject(s);
    } catch (Exception ignored) {}
    return macDinh();
  }

  public static JSONObject macDinh() {
    try {
      JSONObject o = new JSONObject();
      o.put("nguon_giu", new JSONObject().put("bat", true).put("loai", "dich"));
      o.put("len_giu", new JSONObject().put("bat", false).put("loai", "den_pin"));
      o.put("len_2lan", new JSONObject().put("bat", false).put("loai", "camera"));
      o.put("xuong_giu", new JSONObject().put("bat", false).put("loai", "gemini"));
      o.put("xuong_2lan", new JSONObject().put("bat", false).put("loai", "chup"));
      o.put("rung", true);
      return o;
    } catch (Exception e) { return new JSONObject(); }
  }

  public static void luu(Context c, String json) {
    sp(c).edit().putString("json", json).apply();
  }

  /** Trả về hành động của nút nếu đang bật, ngược lại null. */
  public static JSONObject nut(Context c, String ten) {
    JSONObject o = doc(c).optJSONObject(ten);
    if (o == null || !o.optBoolean("bat") || "none".equals(o.optString("loai"))) return null;
    return o;
  }

  public static boolean rung(Context c) { return doc(c).optBoolean("rung", true); }
}
