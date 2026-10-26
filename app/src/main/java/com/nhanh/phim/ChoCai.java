package com.nhanh.phim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/** App của Nhanh (cùng chữ ký) báo sắp cài bản mới → Phím Nhanh sẵn sàng bấm "Install" trên hộp cảnh báo của Xiaomi. */
public class ChoCai extends BroadcastReceiver {
  @Override public void onReceive(Context c, Intent i) {
    PhimService.choCaiDen = SystemClock.uptimeMillis() + 90000;
    Nhatky.ghi(c, "cho cai ban moi: " + i.getStringExtra("pkg"));
    PhimService s = PhimService.ban;
    if (s != null) s.h.post(s::batDauXacNhan);
  }
}
