package com.nhanh.phim;

import android.app.Activity;
import android.os.Bundle;

/** Mở khi giữ nút nguồn (Phím Nhanh là trợ lý mặc định). */
public class TroLy extends Activity {
  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    Nhatky.ghi(this, "giu nut nguon");
    HanhDong.chayNut(this, "nguon_giu");
    finish();
    overridePendingTransition(0, 0);
  }
}
