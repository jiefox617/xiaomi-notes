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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 闹钟提醒广播接收器
 *
 * 【类功能】
 * 接收AlarmManager触发的广播，将提醒从后台广播转换为前台Activity显示。
 * 当系统时间到达笔记设定的提醒时间时，AlarmManager会发送广播，
 * AlarmReceiver接收后启动AlarmAlertActivity，弹出提醒对话框并播放铃声。
 *
 * 【类间调用关系】
 * 1. 被AlarmManager调用：在AlarmInitReceiver中创建的PendingIntent，
 *    当系统时间到达alertDate时，AlarmManager会发送广播给AlarmReceiver
 * 2. 调用AlarmAlertActivity：启动提醒弹窗界面，传递笔记ID等参数
 * 3. 与AlarmInitReceiver配合：AlarmInitReceiver负责初始化所有闹钟，
 *    AlarmReceiver负责响应每个具体闹钟的触发
 *
 * 【工作流程】
 * AlarmManager时间到达 → 发送PendingIntent中的广播 → AlarmReceiver.onReceive()
 * → 跳转到AlarmAlertActivity → 显示提醒对话框和播放铃声
 */
public class AlarmReceiver extends BroadcastReceiver {

    /**
     * 广播接收器的回调方法（闹钟时间到达时调用）
     *
     * 【函数功能】
     * 1. 接收来自AlarmManager的闹钟触发广播
     * 2. 将广播Intent转换为启动Activity的Intent
     * 3. 启动AlarmAlertActivity显示提醒界面
     *
     * 【调用关系】
     * - intent.setClass(): 修改Intent的目标组件，从BroadcastReceiver改为Activity
     * - intent.addFlags(): 添加FLAG_ACTIVITY_NEW_TASK标志
     * - context.startActivity(): 启动Activity（从广播接收器中启动必须使用此方法）
     *
     * 【关键点】
     * - 为什么需要FLAG_ACTIVITY_NEW_TASK？
     *   因为BroadcastReceiver运行在系统进程中，没有自己的任务栈，
     *   必须添加NEW_TASK标志创建一个新任务栈才能启动Activity
     * - Intent数据传递：Intent中已经通过setData()携带了笔记ID，
     *   在AlarmInitReceiver中设置：setData(ContentUris.withAppendedId(...))
     *   AlarmAlertActivity可以通过getIntent().getData()获取这个ID
     *
     * @param context 上下文对象，用于启动Activity
     * @param intent 闹钟触发时发送的Intent，包含笔记ID等信息
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        /**
         * 修改Intent的目标组件
         *
         * 【转换前】
         * Intent原本是为AlarmReceiver设计的，Action可能为空，Data包含笔记URI
         * 格式：content://net.micode.notes/notes/123
         *
         * 【转换后】
         * Intent目标变为AlarmAlertActivity.class
         * 保留了原有的Data数据（笔记URI），所以AlarmAlertActivity可以解析出笔记ID
         */
        intent.setClass(context, AlarmAlertActivity.class);

        /**
         * 添加NEW_TASK标志
         *
         * 【为什么必须添加？】
         * - BroadcastReceiver本身不是Activity，没有自己的任务栈
         * - 如果不添加FLAG_ACTIVITY_NEW_TASK，startActivity()会抛出异常：
         *   "Calling startActivity() from outside of an Activity context requires
         *    the FLAG_ACTIVITY_NEW_TASK flag."
         * - 添加此标志后，系统会为AlarmAlertActivity创建一个新的任务栈
         *
         * 【其他可选标志】
         * 这里只使用了NEW_TASK，也可以组合使用：
         * - FLAG_ACTIVITY_CLEAR_TOP：如果栈中已有实例则清除上面的Activity
         * - FLAG_ACTIVITY_SINGLE_TOP：避免重复创建实例
         * 但笔记提醒场景下，每次都创建新实例更简单可靠
         */
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        /**
         * 启动提醒弹窗Activity
         *
         * 【执行效果】
         * 1. 系统创建或切换到AlarmAlertActivity
         * 2. 调用AlarmAlertActivity.onCreate()方法
         * 3. 显示提醒对话框、播放闹钟铃声
         *
         * 【注意】
         * 这里使用context.startActivity()而不是Activity.startActivity()
         * 因为当前context是BroadcastReceiver的Context
         */
        context.startActivity(intent);
    }
}