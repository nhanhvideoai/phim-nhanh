package com.nhanh.phim;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Kiểm tra cập nhật định kỳ khi có mạng. */
public class CapNhatJob extends JobService {
  @Override public boolean onStartJob(JobParameters p) {
    if (!CapNhat.tuDong(this)) return false;
    CapNhat.kiemTra(this, true, t -> jobFinished(p, false));
    return true;
  }
  @Override public boolean onStopJob(JobParameters p) { return true; }
}
