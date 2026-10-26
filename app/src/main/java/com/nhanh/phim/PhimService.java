package com.nhanh.phim;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;

/**
 * Dịch vụ trợ năng:
 *  - Nhận nút tăng/giảm âm lượng (giữ, bấm 2 lần) khi màn hình đang sáng.
 *  - Bấm hộ ô "Dịch màn hình" và nút Start của Xiaomi để dịch ngay.
 */
public class PhimService extends AccessibilityService {
  static volatile PhimService ban;
  final Handler h = new Handler(Looper.getMainLooper());

  // ---------------- Nút âm lượng ----------------
  static final long GIU_MS = 500, CHO_LAN_2_MS = 320;

  final class Phim {
    final String ten; final int huong;
    boolean daGiu, laLan2, choLan2, lapLai;
    Phim(String ten, int huong) { this.ten = ten; this.huong = huong; }
    final Runnable khiGiu = () -> { daGiu = true; HanhDong.chayNut(PhimService.this, this.ten + "_giu"); };
    final Runnable motLan = () -> { choLan2 = false; chinhAm(this.huong); };
  }
  final Phim len = new Phim("len", AudioManager.ADJUST_RAISE);
  final Phim xuong = new Phim("xuong", AudioManager.ADJUST_LOWER);

  @Override protected boolean onKeyEvent(KeyEvent e) {
    Phim p = e.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP ? len : e.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN ? xuong : null;
    if (p == null) return false;
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    if (am.getMode() != AudioManager.MODE_NORMAL) return false;          // đang gọi điện / đổ chuông: để máy xử lý
    boolean giu = CauHinh.nut(this, p.ten + "_giu") != null;
    boolean hai = CauHinh.nut(this, p.ten + "_2lan") != null;
    if (!giu && !hai) return false;                                      // nút này không gắn gì

    if (e.getAction() == KeyEvent.ACTION_DOWN) {
      if (e.getRepeatCount() == 0) {
        p.daGiu = false; p.lapLai = false;
        if (p.choLan2) { h.removeCallbacks(p.motLan); p.choLan2 = false; p.laLan2 = true; }
        else p.laLan2 = false;
        if (giu && !p.laLan2) h.postDelayed(p.khiGiu, GIU_MS);
      } else if (!giu && !p.laLan2) {                                    // không gắn "giữ": giữ để chỉnh âm liên tục
        p.lapLai = true; chinhAm(p.huong);
      }
      return true;
    }
    if (e.getAction() == KeyEvent.ACTION_UP) {
      h.removeCallbacks(p.khiGiu);
      if (p.daGiu || p.lapLai) { p.daGiu = false; p.lapLai = false; return true; }
      if (p.laLan2) { p.laLan2 = false; HanhDong.chayNut(this, p.ten + "_2lan"); return true; }
      if (hai) { p.choLan2 = true; h.postDelayed(p.motLan, CHO_LAN_2_MS); }
      else chinhAm(p.huong);
      return true;
    }
    return true;
  }

  void chinhAm(int huong) {
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    am.adjustSuggestedStreamVolume(huong, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
  }

  // ---------------- Dịch màn hình Xiaomi ----------------
  static final String V = "com.xiaomi.aiasst.vision";
  static final String VA = "com.miui.voiceassist";
  static final String SW = "com.miui.voiceassist:id/cl_trans_sw";
  static final String[] O_DICH = {"Dịch màn hình", "Screen translation", "Translate screen", "屏幕翻译"};
  static final String[] NUT_START = {"Start", "Bắt đầu", "开始", "開始"};
  static volatile long cho = 0; static volatile int buoc = 0;
  static long thay = 0, lanBam = 0; static int soLan = 0;

  static void dichManHinh(Context c) {
    PhimService s = ban;
    if (s == null) { HanhDong.thongBao(c, "Hãy bật quyền Trợ năng cho Phím Nhanh để dịch màn hình"); return; }
    AccessibilityNodeInfo sw = s.timId(SW);
    if (sw != null) { s.bam(sw); Nhatky.ghi(c, "dich: bat/tat thanh dich"); return; }
    cho = SystemClock.uptimeMillis(); buoc = 1; thay = 0; lanBam = 0; soLan = 0;
    Intent i = new Intent().setComponent(new ComponentName(V, V + ".ui.MainActivity"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
    try { c.startActivity(i); } catch (Exception e) {
      buoc = 0; HanhDong.thongBao(c, "Máy không có Dịch màn hình của Xiaomi"); return;
    }
    s.quetLai();
  }

  void quetLai() {
    h.postDelayed(() -> {
      if (buoc != 0 && SystemClock.uptimeMillis() - cho < 12000) { quet(); quetLai(); }
      else if (buoc != 0) { Nhatky.ghi(this, "dich: het gio o buoc " + buoc); buoc = 0; }
    }, 200);
  }

  void quet() {
    for (AccessibilityWindowInfo w : getWindows()) {
      AccessibilityNodeInfo r = w.getRoot();
      if (r == null || r.getPackageName() == null) continue;
      String pk = r.getPackageName().toString();
      if (buoc == 1 && pk.equals(V)) {
        AccessibilityNodeInfo n = tim(r, O_DICH);
        if (n != null) { bam(n); buoc = 2; cho = SystemClock.uptimeMillis(); return; }
      }
      if (buoc == 2 && pk.equals(VA)) {
        AccessibilityNodeInfo n = tim(r, NUT_START);
        long now = SystemClock.uptimeMillis();
        if (n != null) {
          if (thay == 0) thay = now;
          if (now - thay >= 500 && now - lanBam >= 1200) {
            if (soLan >= 4) { buoc = 0; return; }
            Rect b = new Rect(); n.getBoundsInScreen(b);
            AccessibilityNodeInfo p = n; while (p != null && !p.isClickable()) p = p.getParent();
            if (p != null) p.getBoundsInScreen(b);
            cham(b.centerX(), b.centerY()); lanBam = now; soLan++;
          }
          return;
        } else if (soLan > 0) { buoc = 0; Nhatky.ghi(this, "dich: dang dich"); return; }
        else if (now - cho > 1500) {
          List<AccessibilityNodeInfo> l = r.findAccessibilityNodeInfosByViewId(SW);
          if (!l.isEmpty()) { bam(l.get(0)); buoc = 0; return; }
        }
      }
    }
  }

  AccessibilityNodeInfo timId(String id) {
    for (AccessibilityWindowInfo w : getWindows()) {
      AccessibilityNodeInfo r = w.getRoot();
      if (r == null || r.getPackageName() == null || !VA.contentEquals(r.getPackageName())) continue;
      List<AccessibilityNodeInfo> l = r.findAccessibilityNodeInfosByViewId(id);
      if (!l.isEmpty()) return l.get(0);
    }
    return null;
  }

  AccessibilityNodeInfo tim(AccessibilityNodeInfo r, String[] chu) {
    for (String s : chu)
      for (AccessibilityNodeInfo n : r.findAccessibilityNodeInfosByText(s)) {
        CharSequence t = n.getText() != null ? n.getText() : n.getContentDescription();
        if (t != null && t.toString().trim().equalsIgnoreCase(s)) return n;
      }
    return null;
  }

  void bam(AccessibilityNodeInfo n) {
    AccessibilityNodeInfo p = n;
    while (p != null && !p.isClickable()) p = p.getParent();
    if (p != null && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
    Rect b = new Rect(); n.getBoundsInScreen(b); cham(b.centerX(), b.centerY());
  }

  void cham(int x, int y) {
    Path path = new Path(); path.moveTo(x, y);
    dispatchGesture(new GestureDescription.Builder()
        .addStroke(new GestureDescription.StrokeDescription(path, 0, 50)).build(), null, null);
  }

  // ---------------- Vòng đời ----------------
  @Override protected void onServiceConnected() { ban = this; Nhatky.ghi(this, "dich vu ket noi"); }
  @Override public boolean onUnbind(Intent i) { if (ban == this) ban = null; return super.onUnbind(i); }
  @Override public void onAccessibilityEvent(AccessibilityEvent e) { if (buoc != 0) quet(); }
  @Override public void onInterrupt() {}
}
