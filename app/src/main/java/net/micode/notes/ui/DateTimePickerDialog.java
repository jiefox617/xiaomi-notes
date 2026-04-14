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

import java.util.Calendar;

import net.micode.notes.R;
import net.micode.notes.ui.DateTimePicker;
import net.micode.notes.ui.DateTimePicker.OnDateTimeChangedListener;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

/**
 * 日期时间选择对话框
 *
 * 【类功能】
 * 封装自定义DateTimePicker控件，以AlertDialog弹窗形式提供时间选择界面。
 * 用户在笔记设置提醒时间时，通过此对话框选择具体的日期和时间，
 * 选择完成后通过回调接口返回毫秒时间戳。
 *
 * 【类间调用关系】
 * 1. 被NoteEditActivity调用：当用户点击"设置提醒"按钮时，创建并显示此对话框
 * 2. 调用DateTimePicker：将时间选择控件嵌入到对话框中
 * 3. 实现OnDateTimeChangedListener接口：监听DateTimePicker的时间变化
 * 4. 实现DialogInterface.OnClickListener接口：处理对话框按钮点击
 * 5. 定义OnDateTimeSetListener接口：回调选择的时间给调用者
 *
 * 【工作流程】
 * NoteEditActivity创建对话框 → 设置初始时间 → 用户滚动选择器
 * → DateTimePicker回调时间变化 → 更新对话框标题
 * → 用户点击"确定" → 回调OnDateTimeSetListener → NoteEditActivity获取时间并保存
 *
 * 【设计特点】
 * - 秒数自动归零，确保提醒时间精确到分钟
 * - 实时更新标题显示当前选择的时间
 * - 支持12/24小时制自适应
 * - 提供确认/取消按钮，符合用户习惯
 */
public class DateTimePickerDialog extends AlertDialog implements DialogInterface.OnClickListener {

    private Calendar mDate = Calendar.getInstance();                // 存储用户选择的时间
    private boolean mIs24HourView;                                  // 是否24小时制（根据系统设置）
    private OnDateTimeSetListener mOnDateTimeSetListener;           // 时间设置完成监听器
    private DateTimePicker mDateTimePicker;                         // 自定义时间选择控件

    /**
     * 时间设置完成回调接口
     *
     * 【接口功能】
     * 当用户点击"确定"按钮时，通过此接口将选中的时间返回给调用者
     *
     * 【调用关系】
     * 由NoteEditActivity实现此接口，接收用户选择的时间并保存到数据库
     */
    public interface OnDateTimeSetListener {
        /**
         * 时间设置完成回调方法
         *
         * @param dialog 当前对话框实例
         * @param date 用户选择的毫秒时间戳
         */
        void OnDateTimeSet(AlertDialog dialog, long date);
    }

    /**
     * 构造方法：初始化对话框、时间选择控件、按钮、标题
     *
     * 【函数功能】
     * 1. 创建DateTimePicker控件并设置为对话框视图
     * 2. 设置时间变化监听器，实时更新对话框标题
     * 3. 设置初始时间
     * 4. 添加确定和取消按钮
     * 5. 根据系统时间格式设置12/24小时制
     * 6. 更新标题显示
     *
     * 【关键点】
     * - 将秒数设置为0，因为笔记提醒只精确到分钟
     * - 使用setView()将自定义控件嵌入AlertDialog
     * - 取消按钮的监听器设为null（点击取消直接关闭对话框）
     *
     * @param context 上下文（通常是NoteEditActivity）
     * @param date 初始时间（毫秒时间戳，通常是当前时间或已设置的提醒时间）
     */
    public DateTimePickerDialog(Context context, long date) {
        super(context);

        // 创建自定义时间选择控件并设为对话框视图
        mDateTimePicker = new DateTimePicker(context);
        setView(mDateTimePicker);

        /**
         * 设置时间改变监听器（匿名内部类实现OnDateTimeChangedListener接口）
         *
         * 【功能】
         * 当用户在DateTimePicker上滚动选择时间时，实时更新：
         * 1. 内部Calendar对象的值
         * 2. 对话框标题显示的时间文本
         *
         * 【调用时机】
         * DateTimePicker的每个滚轮（日期、小时、分钟、上下午）
         * 变化时都会触发此回调
         */
        mDateTimePicker.setOnDateTimeChangedListener(new OnDateTimeChangedListener() {
            public void onDateTimeChanged(DateTimePicker view, int year, int month,
                                          int dayOfMonth, int hourOfDay, int minute) {
                // 更新Calendar对象的各个字段
                mDate.set(Calendar.YEAR, year);
                mDate.set(Calendar.MONTH, month);
                mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                mDate.set(Calendar.MINUTE, minute);
                // 更新时间标题显示
                updateTitle(mDate.getTimeInMillis());
            }
        });

        // 初始化时间
        mDate.setTimeInMillis(date);
        mDate.set(Calendar.SECOND, 0); // 秒数置0，保证时间精确到分钟
        mDateTimePicker.setCurrentDate(mDate.getTimeInMillis());

        /**
         * 设置对话框按钮
         *
         * setButton() 和 setButton2() 是AlertDialog的方法
         * - setButton(): 设置正面按钮（通常是"确定"）
         * - setButton2(): 设置负面按钮（通常是"取消"）
         *
         * 【参数说明】
         * - 第一个参数：按钮文本
         * - 第二个参数：点击监听器（this表示当前类实现了OnClickListener接口）
         * - 取消按钮的监听器设为null，点击后只关闭对话框不做其他处理
         */
        setButton(context.getString(R.string.datetime_dialog_ok), this);
        setButton2(context.getString(R.string.datetime_dialog_cancel), (OnClickListener) null);

        // 根据系统设置切换24/12小时制
        set24HourView(DateFormat.is24HourFormat(this.getContext()));

        // 更新对话框标题（显示当前时间）
        updateTitle(mDate.getTimeInMillis());
    }

    /**
     * 设置是否24小时制
     *
     * 【函数功能】
     * 设置时间显示格式，会传递给内部的DateTimePicker控件
     *
     * @param is24HourView true:24小时制，false:12小时制
     */
    public void set24HourView(boolean is24HourView) {
        mIs24HourView = is24HourView;
        // 注意：这里没有调用mDateTimePicker.set24HourView()
        // 因为DateTimePicker会在内部根据系统设置自动调整
        // 如果需要动态切换，可以添加这行代码
    }

    /**
     * 设置时间选择完成监听器
     *
     * 【函数功能】
     * 由调用者（如NoteEditActivity）设置监听器，接收用户选择的时间
     *
     * @param callBack 实现OnDateTimeSetListener接口的对象
     */
    public void setOnDateTimeSetListener(OnDateTimeSetListener callBack) {
        mOnDateTimeSetListener = callBack;
    }

    /**
     * 更新对话框标题，显示当前选择的时间
     *
     * 【函数功能】
     * 根据当前选择的时间和系统时间格式，生成格式化的时间字符串作为对话框标题
     *
     * 【格式化标志说明】
     * - FORMAT_SHOW_YEAR: 显示年份
     * - FORMAT_SHOW_DATE: 显示日期（月/日）
     * - FORMAT_SHOW_TIME: 显示时间
     * - FORMAT_24HOUR: 使用24小时制
     * - FORMAT_12HOUR: 使用12小时制
     *
     * 【示例输出】
     * 24小时制：2024年1月15日 14:30
     * 12小时制：2024年1月15日 下午2:30
     *
     * @param date 毫秒时间戳
     */
    private void updateTitle(long date) {
        int flag = DateUtils.FORMAT_SHOW_YEAR |
                DateUtils.FORMAT_SHOW_DATE |
                DateUtils.FORMAT_SHOW_TIME;

        // 根据时间制式添加相应标志
        flag |= mIs24HourView ? DateUtils.FORMAT_24HOUR : DateUtils.FORMAT_12HOUR;

        // 格式化时间并设置为对话框标题
        setTitle(DateUtils.formatDateTime(this.getContext(), date, flag));
    }

    /**
     * 对话框按钮点击事件处理（实现DialogInterface.OnClickListener接口）
     *
     * 【函数功能】
     * 处理"确定"按钮的点击事件，通过回调接口返回用户选择的时间
     *
     * 【调用时机】
     * 用户点击对话框的"确定"按钮时调用
     *
     * 【参数说明】
     * @param arg0 对话框接口（当前对话框）
     * @param arg1 点击的按钮标识（BUTTON_POSITIVE表示确定按钮）
     *
     * 【注意】
     * - 取消按钮的监听器设置为null，所以点击取消不会触发此方法
     * - 只有确定按钮会触发回调，返回用户选择的时间
     */
    public void onClick(DialogInterface arg0, int arg1) {
        if (mOnDateTimeSetListener != null) {
            // 回调选中的时间（毫秒时间戳）
            mOnDateTimeSetListener.OnDateTimeSet(this, mDate.getTimeInMillis());
        }
    }
}