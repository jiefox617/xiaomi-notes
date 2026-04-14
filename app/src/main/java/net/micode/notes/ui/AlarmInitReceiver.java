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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 闹钟初始化广播接收器
 *
 * 【类功能】
 * 在系统开机/重启后自动运行，重新注册所有未过期的笔记提醒闹钟。
 * 因为Android系统重启后会清空所有已设置的闹钟，必须通过此接收器恢复。
 *
 * 【类间调用关系】
 * 1. 被系统调用：在AndroidManifest.xml中注册接收 android.intent.action.BOOT_COMPLETED 广播
 * 2. 调用Notes.CONTENT_NOTE_URI：查询笔记数据库中的提醒信息
 * 3. 调用AlarmReceiver：为每个提醒创建Intent，提醒时间到达时发送给AlarmReceiver
 * 4. 调用AlarmManager系统服务：设置实际的系统闹钟
 * 5. 使用Notes.NoteColumns常量：定义数据库字段名（ID、ALERTED_DATE、TYPE）
 *
 * 【工作流程】
 * 系统开机 → 发送BOOT_COMPLETED广播 → AlarmInitReceiver接收 → 查询数据库获取所有有效提醒
 * → 遍历每个提醒 → 创建PendingIntent → 调用AlarmManager设置闹钟
 */
public class AlarmInitReceiver extends BroadcastReceiver {

    /**
     * 数据库查询投影：定义需要从数据库中查询哪些字段
     * 只查询必要的字段以提高查询效率
     */
    private static final String [] PROJECTION = new String [] {
            NoteColumns.ID,              // 笔记ID，用于标识哪个笔记需要提醒
            NoteColumns.ALERTED_DATE     // 提醒时间，毫秒时间戳
    };

    // 查询结果列的索引（与PROJECTION顺序对应）
    private static final int COLUMN_ID                = 0;  // 笔记ID列的索引
    private static final int COLUMN_ALERTED_DATE      = 1;  // 提醒时间列的索引

    /**
     * 广播接收器的回调方法（系统开机完成后调用）
     *
     * 【函数功能】
     * 1. 查询数据库中所有未过期的笔记提醒
     * 2. 为每个有效的提醒重新设置系统闹钟
     * 3. 确保重启后用户的笔记提醒依然有效
     *
     * 【调用关系】
     * - context.getContentResolver().query(): 通过ContentProvider查询笔记数据库
     * - ContentUris.withAppendedId(): 构建带ID的URI，用于标识具体的笔记
     * - PendingIntent.getBroadcast(): 创建延迟执行的广播Intent
     * - AlarmManager.set(): 调用系统闹钟服务设置提醒
     *
     * 【关键点】
     * - 只查询提醒时间大于当前时间的笔记（未过期）
     * - 只查询类型为TYPE_NOTE的笔记（文件夹类型不需要提醒）
     * - 使用RTC_WAKEUP模式，确保在休眠状态下也能唤醒CPU触发提醒
     *
     * @param context 上下文对象，用于访问ContentProvider和系统服务
     * @param intent 触发的Intent（BOOT_COMPLETED或其他系统广播）
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 获取当前系统时间，用于过滤已经过期的提醒
        long currentDate = System.currentTimeMillis();

        /**
         * 查询数据库中的有效提醒
         * 查询条件：
         *   1. alert_date > currentDate（提醒时间在未来，未过期）
         *   2. type = Notes.TYPE_NOTE（只查询笔记类型，不包括文件夹）
         *
         * Notes.CONTENT_NOTE_URI = content://net.micode.notes/notes
         */
        Cursor c = context.getContentResolver().query(
                Notes.CONTENT_NOTE_URI,          // 查询笔记表
                PROJECTION,                       // 只查询ID和提醒时间字段
                NoteColumns.ALERTED_DATE + ">? AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE,  // WHERE子句
                new String[] { String.valueOf(currentDate) },  // 替换"?"的参数
                null                             // 不需要排序
        );

        // 处理查询结果
        if (c != null) {
            if (c.moveToFirst()) {  // 移动到第一条记录
                do {
                    // 获取该笔记的提醒时间（毫秒时间戳）
                    long alertDate = c.getLong(COLUMN_ALERTED_DATE);

                    // 获取笔记ID
                    long noteId = c.getLong(COLUMN_ID);

                    /**
                     * 创建提醒Intent
                     * 当闹钟时间到达时，系统会发送这个Intent给AlarmReceiver
                     *
                     * 【Intent数据传递】
                     * - 设置Action为空（AlarmReceiver会自己识别）
                     * - setData()将笔记ID编码到URI中
                     *   格式：content://net.micode.notes/notes/123
                     */
                    Intent sender = new Intent(context, AlarmReceiver.class);
                    sender.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId));

                    /**
                     * 创建PendingIntent（延迟执行的Intent包装器）
                     *
                     * 【参数说明】
                     * - context: 上下文
                     * - requestCode: 0（请求码，用于区分不同的PendingIntent，这里不需要）
                     * - sender: 要包装的Intent
                     * - flags: 0（标志位，使用默认行为）
                     *
                     * 【作用】
                     * PendingIntent允许AlarmManager在未来的某个时间点执行这个Intent，
                     * 即使当前进程已经不存在。
                     */
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, sender, 0);

                    /**
                     * 获取系统闹钟服务
                     * AlarmManager是Android系统服务，用于设置定时任务
                     */
                    AlarmManager alarmManager = (AlarmManager) context
                            .getSystemService(Context.ALARM_SERVICE);

                    /**
                     * 设置闹钟
                     *
                     * 【参数说明】
                     * - AlarmManager.RTC_WAKEUP:
                     *   使用真实时间（RTC），即使设备处于休眠状态也会唤醒CPU
                     * - alertDate: 提醒触发的时间（毫秒时间戳）
                     * - pendingIntent: 时间到达时要执行的PendingIntent
                     *
                     * 【注意】
                     * 这里使用了set()而不是setExact()，系统可能会对闹钟进行微调以优化电量，
                     * 但对于笔记闹钟来说精度要求不高，这样设置可以节省电量。
                     */
                    alarmManager.set(AlarmManager.RTC_WAKEUP, alertDate, pendingIntent);

                } while (c.moveToNext());  // 继续处理下一条记录
            }
            c.close();  // 关闭游标，释放数据库资源
        }
    }
}