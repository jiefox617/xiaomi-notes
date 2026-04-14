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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;

import java.io.IOException;

/**
 * 笔记闹钟提醒弹窗 Activity
 *
 * 【类功能】
 * 当笔记设置的提醒时间到达时，系统会启动此Activity，在锁屏或桌面弹出提醒对话框，
 * 并播放闹钟铃声，用户可关闭提醒或跳转到笔记详情。
 *
 * 【类间调用关系】
 * 1. 被系统AlarmManager调用：当提醒时间到达时，系统通过Intent启动此Activity
 * 2. 调用DataUtils类：通过getSnippetById()获取笔记摘要，通过visibleInNoteDatabase()验证笔记有效性
 * 3. 调用NoteEditActivity：用户点击"进入笔记"时，跳转到笔记编辑界面
 * 4. 引用Notes常量：使用Notes.TYPE_NOTE判断笔记类型
 * 5. 使用系统服务：PowerManager(电源管理)、AudioManager(音频管理)、RingtoneManager(铃声管理)
 *
 * 【实现接口】
 * - OnClickListener: 处理对话框按钮点击事件
 * - OnDismissListener: 处理对话框消失事件
 */
public class AlarmAlertActivity extends Activity implements OnClickListener, OnDismissListener {
    private long mNoteId;                  // 提醒对应的笔记ID（从Intent的URI中解析）
    private String mSnippet;               // 笔记摘要内容（在对话框中显示）
    private static final int SNIPPET_PREW_MAX_LEN = 60;  // 摘要最大显示长度，超过则截断
    MediaPlayer mPlayer;                   // 媒体播放器，用于播放和循环闹钟铃声

    /**
     * Activity创建时的生命周期回调
     *
     * 【函数功能】
     * 1. 配置窗口属性（无标题、锁屏显示、自动亮屏）
     * 2. 从Intent中解析笔记ID和摘要内容
     * 3. 验证笔记有效性，有效则显示对话框并播放铃声，无效则直接关闭
     *
     * 【调用关系】
     * - getIntent().getData(): 获取启动Activity的URI数据（格式：content://notes/note/123）
     * - DataUtils.getSnippetById(): 查询ContentProvider获取笔记摘要
     * - DataUtils.visibleInNoteDatabase(): 验证笔记是否存在且未删除
     * - showActionDialog(): 显示提醒对话框
     * - playAlarmSound(): 播放闹钟铃声
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 去掉标题栏，使对话框更简洁
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        final Window win = getWindow();
        // 设置窗口在锁屏界面上显示（即使手机已锁屏也能看到提醒）
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        // 如果屏幕当前是关闭状态，则自动点亮屏幕并保持亮屏
        if (!isScreenOn()) {
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON      // 保持屏幕常亮
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON        // 点亮屏幕
                    | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON  // 允许在亮屏时锁定
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);  // 布局嵌入装饰
        }

        Intent intent = getIntent();

        try {
            // 从URI中解析笔记ID，URI格式：content://net.micode.notes/note/123
            // getPathSegments()返回路径段列表，get(1)获取第二段即笔记ID
            mNoteId = Long.valueOf(intent.getData().getPathSegments().get(1));

            // 调用DataUtils工具类获取笔记摘要内容
            mSnippet = DataUtils.getSnippetById(this.getContentResolver(), mNoteId);

            // 截断超长文本，添加"..."提示
            mSnippet = mSnippet.length() > SNIPPET_PREW_MAX_LEN ?
                    mSnippet.substring(0, SNIPPET_PREW_MAX_LEN) + getResources().getString(R.string.notelist_string_info)
                    : mSnippet;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return;  // 解析失败则退出Activity
        }

        mPlayer = new MediaPlayer();

        // 验证笔记是否有效（未被删除、不在垃圾箱中）
        if (DataUtils.visibleInNoteDatabase(getContentResolver(), mNoteId, Notes.TYPE_NOTE)) {
            showActionDialog();    // 弹出提醒对话框
            playAlarmSound();      // 播放提醒铃声
        } else {
            finish();              // 笔记无效（已被删除），直接关闭页面
        }
    }

    /**
     * 判断当前屏幕是否处于点亮状态
     *
     * 【函数功能】
     * 检测屏幕的电源状态，用于决定是否需要自动亮屏和显示"进入笔记"按钮
     *
     * 【调用关系】
     * - getSystemService(Context.POWER_SERVICE): 获取电源管理服务
     * - PowerManager.isScreenOn(): 系统API，返回true表示屏幕亮着
     *
     * 【注意】
     * isScreenOn() API在API 20 (Android L) 已废弃，但这里使用了版本判断保持兼容
     */
    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR_MR1) {
            return pm.isScreenOn();  // API 7+ 支持
        }
        return false;
    }

    /**
     * 播放系统默认闹钟铃声（循环播放直到用户响应）
     *
     * 【函数功能】
     * 1. 获取系统默认的闹钟铃声URI
     * 2. 根据系统设置确定音频流类型（闹钟流或受影响的流）
     * 3. 设置MediaPlayer为循环播放模式并开始播放
     *
     * 【调用关系】
     * - RingtoneManager.getActualDefaultRingtoneUri(): 获取系统默认闹钟铃声URI
     * - Settings.System.getInt(): 读取系统设置，判断哪些音频流受静音模式影响
     * - MediaPlayer类: 设置数据源、准备、循环、播放
     *
     * 【注意】
     * - setLooping(true)使铃声循环播放，直到用户关闭对话框
     * - 使用STREAM_ALARM类型，不受手机静音模式影响（保证闹钟必响）
     */
    private void playAlarmSound() {
        // 获取系统默认闹钟铃声的URI
        Uri url = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);

        // 读取系统设置：哪些音频流受静音模式影响（位掩码）
        int silentModeStreams = Settings.System.getInt(getContentResolver(),
                Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);

        // 设置音频流类型
        if ((silentModeStreams & (1 << AudioManager.STREAM_ALARM)) != 0) {
            // 如果闹钟流受静音模式影响，则使用受影响的流
            mPlayer.setAudioStreamType(silentModeStreams);
        } else {
            // 否则直接使用闹钟流（闹钟通常不受静音影响）
            mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        }
        try {
            mPlayer.setDataSource(this, url);  // 设置铃声数据源
            mPlayer.prepare();                  // 准备播放
            mPlayer.setLooping(true);           // 设置为循环播放，持续提醒用户
            mPlayer.start();                    // 开始播放
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 显示提醒对话框
     *
     * 【函数功能】
     * 构建并显示AlertDialog，显示笔记摘要内容，提供关闭和跳转按钮
     *
     * 【调用关系】
     * - AlertDialog.Builder: 构造对话框
     * - setPositiveButton/setNegativeButton: 设置按钮及监听器（this实现了OnClickListener）
     * - setOnDismissListener: 设置消失监听（this实现了OnDismissListener）
     *
     * 【显示逻辑】
     * - 始终显示"确定"按钮（关闭提醒）
     * - 仅当屏幕亮着时显示"进入笔记"按钮（锁屏时只显示关闭，避免误操作）
     */
    private void showActionDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.app_name);          // 标题：小米笔记
        dialog.setMessage(mSnippet);                 // 显示笔记摘要内容
        dialog.setPositiveButton(R.string.notealert_ok, this);  // "确定"按钮，关闭提醒
        // 如果屏幕已点亮，显示"进入笔记"按钮
        if (isScreenOn()) {
            dialog.setNegativeButton(R.string.notealert_enter, this);  // "进入笔记"按钮
        }
        dialog.show().setOnDismissListener(this);    // 设置对话框消失监听
    }

    /**
     * 对话框按钮点击事件处理（实现OnClickListener接口）
     *
     * 【函数功能】
     * 处理对话框按钮的点击事件，目前只处理"进入笔记"按钮
     *
     * 【调用关系】
     * - 创建Intent跳转到NoteEditActivity
     * - startActivity(): 启动笔记编辑界面
     *
     * 【参数说明】
     * - which: 被点击的按钮标识（BUTTON_NEGATIVE表示"进入笔记"，BUTTON_POSITIVE表示"确定"）
     */
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_NEGATIVE:
                // 点击"进入笔记"按钮：跳转到笔记编辑页面
                Intent intent = new Intent(this, NoteEditActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra(Intent.EXTRA_UID, mNoteId);  // 传递笔记ID
                startActivity(intent);                        // 启动编辑Activity
                break;
            default:
                // BUTTON_POSITIVE（确定按钮）不做任何处理，直接关闭对话框
                break;
        }
    }

    /**
     * 对话框消失时的回调（实现OnDismissListener接口）
     *
     * 【函数功能】
     * 无论用户点击"确定"、"进入笔记"还是按返回键，对话框消失时都会调用此方法
     * 负责停止铃声并关闭当前Activity
     *
     * 【调用关系】
     * - stopAlarmSound(): 停止并释放MediaPlayer
     * - finish(): 结束当前Activity，返回之前界面
     */
    public void onDismiss(DialogInterface dialog) {
        stopAlarmSound();  // 停止播放铃声
        finish();          // 关闭提醒页面
    }

    /**
     * 停止并释放媒体播放器资源
     *
     * 【函数功能】
     * 停止铃声播放，释放MediaPlayer占用的系统资源
     *
     * 【注意】
     * 必须在对话框关闭时调用，否则铃声会一直在后台循环播放
     * 释放后置null以便垃圾回收
     */
    private void stopAlarmSound() {
        if (mPlayer != null) {
            mPlayer.stop();     // 停止播放
            mPlayer.release();  // 释放MediaPlayer资源
            mPlayer = null;     // 清空引用
        }
    }
}