package com.nhanh.phim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Sau khi khởi động máy / cập nhật app: lên lịch kiểm tra cập nhật lại. */
public class KhoiDong extends BroadcastReceiver {
  @Override public void onReceive(Context c, Intent i) { HanhDong.tuBatTroNang(c); CapNhat.lenLich(c); }
}
