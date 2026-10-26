package com.nhanh.phim;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.Toast;
import org.json.JSONObject;

/** Thực hiện chức năng đã gắn cho một nút. */
public final class HanhDong {
  static volatile boolean denBat = false;
  static boolean daTheoDoiDen = false;

  public static void chayNut(Context c, String nut) {
    JSONObject o = CauHinh.nut(c, nut);
    if (o == null) return;
    chay(c, o);
  }

  public static void chay(Context c, JSONObject o) {
    String loai = o.optString("loai");
    if (CauHinh.rung(c)) rung(c);
    try {
      switch (loai) {
        case "dich": PhimService.dichManHinh(c); break;
        case "app": moApp(c, o.optString("pkg")); break;
        case "gemini": moGemini(c); break;
        case "den_pin": denPin(c); break;
        case "chup": toanCuc(c, AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT); break;
        case "khoa": toanCuc(c, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN); break;
        case "thong_bao": toanCuc(c, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS); break;
        case "cai_dat_nhanh": toanCuc(c, AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS); break;
        case "gan_day": toanCuc(c, AccessibilityService.GLOBAL_ACTION_RECENTS); break;
        case "nhac": phimNhac(c, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); break;
        case "nhac_tiep": phimNhac(c, KeyEvent.KEYCODE_MEDIA_NEXT); break;
        case "nhac_truoc": phimNhac(c, KeyEvent.KEYCODE_MEDIA_PREVIOUS); break;
        case "camera": moIntent(c, new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); break;
        case "im_lang": imLang(c); break;
        default: break;
      }
    } catch (Throwable t) {
      Nhatky.ghi(c, "loi hanh dong " + loai + ": " + t);
    }
  }

  static void rung(Context c) {
    try {
      Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
      if (v != null) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
    } catch (Throwable ignored) {}
  }

  static void moIntent(Context c, Intent i) {
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    c.startActivity(i);
  }

  static void moApp(Context c, String pkg) {
    Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
    if (i == null) { thongBao(c, "Không mở được app này (có thể đã bị gỡ)"); return; }
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    c.startActivity(i);
  }

  static void moGemini(Context c) {
    Intent i = c.getPackageManager().getLaunchIntentForPackage("com.google.android.apps.bard");
    if (i != null) { moIntent(c, i); return; }
    try {
      moIntent(c, new Intent(Intent.ACTION_VOICE_COMMAND).setPackage("com.google.android.googlequicksearchbox"));
    } catch (Exception e) { thongBao(c, "Chưa cài Gemini"); }
  }

  static void denPin(Context c) throws Exception {
    CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
    if (!daTheoDoiDen) {
      daTheoDoiDen = true;
      cm.registerTorchCallback(new CameraManager.TorchCallback() {
        @Override public void onTorchModeChanged(String id, boolean on) { denBat = on; }
      }, null);
      SystemClock.sleep(80);
    }
    for (String id : cm.getCameraIdList()) {
      Boolean co = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
      if (co != null && co) { cm.setTorchMode(id, !denBat); denBat = !denBat; return; }
    }
  }

  static void phimNhac(Context c, int code) {
    AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    long t = SystemClock.uptimeMillis();
    am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
    am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
  }

  static void imLang(Context c) {
    AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    int m = am.getRingerMode();
    try {
      am.setRingerMode(m == AudioManager.RINGER_MODE_NORMAL ? AudioManager.RINGER_MODE_VIBRATE : AudioManager.RINGER_MODE_NORMAL);
      thongBao(c, m == AudioManager.RINGER_MODE_NORMAL ? "Đã chuyển sang rung" : "Đã bật chuông");
    } catch (SecurityException e) { thongBao(c, "Máy không cho đổi chế độ chuông"); }
  }

  static void toanCuc(Context c, int act) {
    PhimService s = PhimService.ban;
    if (s == null) { thongBao(c, "Hãy bật quyền Trợ năng cho Phím Nhanh"); return; }
    s.performGlobalAction(act);
  }

  static void thongBao(Context c, String s) {
    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> Toast.makeText(c, s, Toast.LENGTH_SHORT).show());
  }

  public static boolean troNangDangBat(Context c) {
    String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    return s != null && s.contains(c.getPackageName() + "/");
  }

  public static boolean laTroLyMacDinh(Context c) {
    if (Build.VERSION.SDK_INT >= 29) {
      android.app.role.RoleManager rm = c.getSystemService(android.app.role.RoleManager.class);
      if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_ASSISTANT))
        return rm.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT);
    }
    String a = Settings.Secure.getString(c.getContentResolver(), "assistant");
    return a != null && a.startsWith(c.getPackageName());
  }
}
