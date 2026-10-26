package com.nhanh.phim;

import android.content.Context;
import java.io.File;
import java.io.FileWriter;
import java.util.Date;

/** Ghi nhật ký để kiểm tra lỗi: Android/data/com.nhanh.phim/files/nhatky.txt */
public final class Nhatky {
  public static void ghi(Context c, String s) {
    try {
      File f = new File(c.getExternalFilesDir(null), "nhatky.txt");
      if (f.length() > 200_000) f.delete();
      FileWriter w = new FileWriter(f, true);
      w.write(new Date() + " " + s + "\n");
      w.close();
    } catch (Throwable ignored) {}
  }
}
