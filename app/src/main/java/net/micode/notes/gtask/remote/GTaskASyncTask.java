/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.gtask.remote;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import net.micode.notes.R;
import net.micode.notes.ui.NotesListActivity;
import net.micode.notes.ui.NotesPreferenceActivity;

import androidx.core.app.NotificationCompat;

/**
 * 谷歌任务同步 异步任务类
 * 功能：在后台执行 Google Task 同步，通过通知显示进度与结果
 * 继承：AsyncTask 实现后台执行 + 进度更新 + 结果回调
 */
public class GTaskASyncTask extends AsyncTask<Void, String, Integer> {

    // 同步通知的固定 ID，用于更新或取消同一通知
    private static int GTASK_SYNC_NOTIFICATION_ID = 5234235;

    /**
     * 同步完成回调接口
     * 外部可通过此接口监听同步结束
     */
    public interface OnCompleteListener {
        void onComplete();
    }

    private Context mContext;  // 上下文

    private NotificationManager mNotifiManager;  // 系统通知管理器

    private GTaskManager mTaskManager;  // 谷歌任务同步核心管理器

    private OnCompleteListener mOnCompleteListener;  // 完成监听器


    /**
     * 构造方法
     * 初始化上下文、监听器、通知管理器、单例 GTaskManager
     */
    public GTaskASyncTask(Context context, OnCompleteListener listener) {
        mContext = context;
        mOnCompleteListener = listener;
        mNotifiManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        mTaskManager = GTaskManager.getInstance();
    }

    /**
     * 外部调用：取消正在进行的同步
     */
    public void cancelSync() {
        mTaskManager.cancelSync();
    }

    /**
     * 向外发布同步进度信息，触发通知更新
     */
    public void publishProgess(String message) {
        publishProgress(new String[] {
                message
        });
    }

    /**
     * 显示同步状态通知
     * @param tickerId 提示文字类型（成功/失败/取消）
     * @param content 通知内容
     */
    private void showNotification(int tickerId, String content) {
        // 构建通知（适配新版 Android，已修复 setLatestEventInfo）
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, "default_channel")
                .setSmallIcon(R.drawable.notification)
                .setContentTitle(mContext.getString(R.string.app_name))
                .setContentText(content)
                .setDefaults(Notification.DEFAULT_LIGHTS)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis());

        PendingIntent pendingIntent;

        // 成功 → 跳转到笔记列表
        if (tickerId != R.string.ticker_success) {
            pendingIntent = PendingIntent.getActivity(
                    mContext,
                    0,
                    new Intent(mContext, NotesPreferenceActivity.class),
                    PendingIntent.FLAG_IMMUTABLE
            );
        }
        // 失败 → 跳转到设置界面
        else {
            pendingIntent = PendingIntent.getActivity(
                    mContext,
                    0,
                    new Intent(mContext, NotesListActivity.class),
                    PendingIntent.FLAG_IMMUTABLE
            );
        }

        builder.setContentIntent(pendingIntent);
        Notification notification = builder.build();
        mNotifiManager.notify(GTASK_SYNC_NOTIFICATION_ID, notification);
    }

    /**
     * 后台线程：执行真正的同步逻辑
     */
    @Override
    protected Integer doInBackground(Void... unused) {
        // 显示登录中进度
        publishProgess(mContext.getString(R.string.sync_progress_login,
                NotesPreferenceActivity.getSyncAccountName(mContext)));

        // 调用 GTaskManager 同步，并返回状态码
        return mTaskManager.sync(mContext, this);
    }

    /**
     * 进度更新：显示同步中的通知
     */
    @Override
    protected void onProgressUpdate(String... progress) {
        showNotification(R.string.ticker_syncing, progress[0]);

        // 如果是服务启动的同步，发送广播更新界面
        if (mContext instanceof GTaskSyncService) {
            ((GTaskSyncService) mContext).sendBroadcast(progress[0]);
        }
    }

    /**
     * 同步结束：根据结果显示成功/失败/取消通知
     */
    @Override
    protected void onPostExecute(Integer result) {
        if (result == GTaskManager.STATE_SUCCESS) {
            // 同步成功
            showNotification(R.string.ticker_success,
                    mContext.getString(R.string.success_sync_account,
                            mTaskManager.getSyncAccount()));
            NotesPreferenceActivity.setLastSyncTime(mContext, System.currentTimeMillis());
        }
        else if (result == GTaskManager.STATE_NETWORK_ERROR) {
            // 网络错误
            showNotification(R.string.ticker_fail,
                    mContext.getString(R.string.error_sync_network));
        }
        else if (result == GTaskManager.STATE_INTERNAL_ERROR) {
            // 内部错误
            showNotification(R.string.ticker_fail,
                    mContext.getString(R.string.error_sync_internal));
        }
        else if (result == GTaskManager.STATE_SYNC_CANCELLED) {
            // 同步被取消
            showNotification(R.string.ticker_cancel,
                    mContext.getString(R.string.error_sync_cancelled));
        }

        // 回调同步完成
        if (mOnCompleteListener != null) {
            new Thread(new Runnable() {
                public void run() {
                    mOnCompleteListener.onComplete();
                }
            }).start();
        }
    }
}