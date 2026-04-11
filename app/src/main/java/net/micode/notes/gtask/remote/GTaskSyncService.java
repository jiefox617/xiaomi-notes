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

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

/**
 * Google Tasks 同步后台服务
 * 作用：
 * 1. 在**后台独立进程**中执行同步，不阻塞 UI
 * 2. 接收外部指令：开始同步 / 取消同步
 * 3. 通过广播向 UI 同步状态、进度
 * 4. 同步完成后自动停止服务，节约资源
 * 属于同步模块的**入口调度服务**
 */
public class GTaskSyncService extends Service {
    // 意图 Action 名称：用于区分服务要执行的操作类型
    public final static String ACTION_STRING_NAME = "sync_action_type";

    // 操作类型：开始同步
    public final static int ACTION_START_SYNC = 0;

    // 操作类型：取消同步
    public final static int ACTION_CANCEL_SYNC = 1;

    // 操作类型：无效操作
    public final static int ACTION_INVALID = 2;

    // 广播 Action 名称：同步服务向外发送状态广播
    public final static String GTASK_SERVICE_BROADCAST_NAME = "net.micode.notes.gtask.remote.gtask_sync_service";

    // 广播 Extra 字段：当前是否正在同步
    public final static String GTASK_SERVICE_BROADCAST_IS_SYNCING = "isSyncing";

    // 广播 Extra 字段：同步进度提示文字（如：正在初始化列表、正在同步）
    public final static String GTASK_SERVICE_BROADCAST_PROGRESS_MSG = "progressMsg";

    // 异步同步任务（全局静态，保证同一时间只有一个同步任务）
    private static GTaskASyncTask mSyncTask = null;

    // 同步进度提示文本
    private static String mSyncProgress = "";

    /**
     * 开始同步
     * 创建异步任务，执行同步逻辑
     * 任务结束后自动清理并停止服务
     */
    private void startSync() {
        if (mSyncTask == null) {
            mSyncTask = new GTaskASyncTask(this, new GTaskASyncTask.OnCompleteListener() {
                public void onComplete() {
                    // 同步完成，清空任务对象
                    mSyncTask = null;
                    // 发送结束广播
                    sendBroadcast("");
                    // 停止服务自身
                    stopSelf();
                }
            });
            // 发送开始同步广播
            sendBroadcast("");
            // 启动异步任务
            mSyncTask.execute();
        }
    }

    /**
     * 取消同步
     * 通知异步任务中断执行
     */
    private void cancelSync() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    /**
     * 服务创建时初始化
     */
    @Override
    public void onCreate() {
        mSyncTask = null;
    }

    /**
     * 服务启动时调用
     * 根据 Intent 携带的指令执行：开始 / 取消同步
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle bundle = intent.getExtras();
        if (bundle != null && bundle.containsKey(ACTION_STRING_NAME)) {
            switch (bundle.getInt(ACTION_STRING_NAME, ACTION_INVALID)) {
                case ACTION_START_SYNC:
                    startSync();
                    break;
                case ACTION_CANCEL_SYNC:
                    cancelSync();
                    break;
                default:
                    break;
            }
            // 服务被杀死后自动重启
            return START_STICKY;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * 系统内存不足时取消同步，释放资源
     */
    @Override
    public void onLowMemory() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    /**
     * 绑定服务（本服务不需要绑定，返回 null）
     */
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 发送同步状态广播
     * 通知界面：是否同步中、同步提示文字
     */
    public void sendBroadcast(String msg) {
        mSyncProgress = msg;
        Intent intent = new Intent(GTASK_SERVICE_BROADCAST_NAME);
        intent.putExtra(GTASK_SERVICE_BROADCAST_IS_SYNCING, mSyncTask != null);
        intent.putExtra(GTASK_SERVICE_BROADCAST_PROGRESS_MSG, msg);
        sendBroadcast(intent);
    }

    /**
     * 外部静态调用方法：开始同步
     * 界面可直接调用，无需关心服务细节
     */
    public static void startSync(Activity activity) {
        GTaskManager.getInstance().setActivityContext(activity);
        Intent intent = new Intent(activity, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_START_SYNC);
        activity.startService(intent);
    }

    /**
     * 外部静态调用方法：取消同步
     */
    public static void cancelSync(Context context) {
        Intent intent = new Intent(context, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_CANCEL_SYNC);
        context.startService(intent);
    }

    /**
     * 获取当前是否正在同步
     */
    public static boolean isSyncing() {
        return mSyncTask != null;
    }

    /**
     * 获取同步进度提示文本
     */
    public static String getProgressString() {
        return mSyncProgress;
    }
}