package com.nhanh.phim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Nhận kết quả cài bản mới. Nếu Android cần người dùng xác nhận thì mở hộp xác nhận / báo thông báo. */
public class CapNhatXong extends BroadcastReceiver {
  @Override public void onReceive(Context c, Intent i) {
    int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
    String tag = i.getStringExtra("tag");
    if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
      Intent xacNhan = i.getParcelableExtra(Intent.EXTRA_INTENT);
      if (xacNhan == null) return;
      xacNhan.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      CapNhat.ghi(c, "Bấm Cài đặt để cập nhật bản " + tag);
      try { c.startActivity(xacNhan); } catch (Exception ignored) {}
      CapNhat.thongBao(c, "Có bản mới " + tag, "Bấm để cài đặt", xacNhan);
    } else if (st == PackageInstaller.STATUS_SUCCESS) {
      CapNhat.ghi(c, "Đã cập nhật lên bản " + tag);
    } else {
      CapNhat.ghi(c, "Cài bản mới không thành công: " + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
    }
  }
}
