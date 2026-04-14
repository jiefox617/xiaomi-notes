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

import java.text.DateFormatSymbols;
import java.util.Calendar;

import net.micode.notes.R;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.NumberPicker;

/**
 * 自定义日期时间选择控件
 *
 * 【类功能】
 * 提供一个集成了日期、小时、分钟、上/下午选择器的滚动控件，支持12/24小时制切换，
 * 用于笔记应用中设置提醒时间时使用。用户可以通过滚动选择器来设置提醒的具体时间。
 *
 * 【类间调用关系】
 * 1. 被NoteEditActivity调用：在设置笔记提醒时显示时间选择对话框
 * 2. 使用NumberPicker控件：Android原生数字滚动选择器
 * 3. 使用Calendar类：处理日期时间的计算和转换
 * 4. 使用DateFormat：格式化日期显示文本
 * 5. 回调接口OnDateTimeChangedListener：通知外部时间发生变化
 *
 * 【设计特点】
 * - 日期只显示近7天（今天前后各3天），简化用户选择
 * - 自动处理分钟进位/借位对小时的影响
 * - 自动处理小时跨天对日期的影响
 * - 支持12/24小时制动态切换
 * - 循环滚动体验流畅
 */
public class DateTimePicker extends FrameLayout {

    // 默认启用状态
    private static final boolean DEFAULT_ENABLE_STATE = true;

    // 时间相关常量定义
    private static final int HOURS_IN_HALF_DAY = 12;   // 半天的小时数
    private static final int HOURS_IN_ALL_DAY = 24;    // 全天的小时数
    private static final int DAYS_IN_ALL_WEEK = 7;     // 一周的天数

    // 各滚轮取值范围常量
    private static final int DATE_SPINNER_MIN_VAL = 0;                    // 日期滚轮最小值
    private static final int DATE_SPINNER_MAX_VAL = DAYS_IN_ALL_WEEK - 1; // 日期滚轮最大值（0-6）
    private static final int HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW = 0;       // 24小时制最小值
    private static final int HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW = 23;      // 24小时制最大值
    private static final int HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW = 1;       // 12小时制最小值
    private static final int HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW = 12;      // 12小时制最大值
    private static final int MINUT_SPINNER_MIN_VAL = 0;                   // 分钟最小值
    private static final int MINUT_SPINNER_MAX_VAL = 59;                  // 分钟最大值
    private static final int AMPM_SPINNER_MIN_VAL = 0;                   // 上下午最小值
    private static final int AMPM_SPINNER_MAX_VAL = 1;                   // 上下午最大值

    // 四个滚轮控件
    private final NumberPicker mDateSpinner;    // 日期选择器（显示近7天）
    private final NumberPicker mHourSpinner;    // 小时选择器
    private final NumberPicker mMinuteSpinner;  // 分钟选择器
    private final NumberPicker mAmPmSpinner;    // 上/下午选择器（12小时制时显示）

    // 日历对象，维护当前选择的时间
    private Calendar mDate;

    // 日期显示字符串数组（存储近7天的显示文本）
    private String[] mDateDisplayValues = new String[DAYS_IN_ALL_WEEK];

    // 是否为上午
    private boolean mIsAm;

    // 是否为24小时制
    private boolean mIs24HourView;

    // 控件整体是否可用
    private boolean mIsEnabled = DEFAULT_ENABLE_STATE;

    // 是否正在初始化（防止初始化过程中触发不必要的回调）
    private boolean mInitialising;

    // 时间改变监听器
    private OnDateTimeChangedListener mOnDateTimeChangedListener;

    /**
     * 日期滚轮监听器：切换日期时更新日历并回调
     *
     * 【函数功能】
     * 当用户滚动日期选择器时，根据滚动的偏移量增加或减少日期，
     * 更新日历对象并刷新日期显示。
     */
    private NumberPicker.OnValueChangeListener mOnDateChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            // 根据新旧值的差值调整日期
            mDate.add(Calendar.DAY_OF_YEAR, newVal - oldVal);
            updateDateControl();  // 更新日期显示
            onDateTimeChanged();  // 触发时间改变回调
        }
    };

    /**
     * 小时滚轮监听器：处理12/24小时制、跨天、上下午切换逻辑
     *
     * 【函数功能】
     * 1. 处理12小时制下滚动到12点的跨日逻辑
     * 2. 处理24小时制下23点到0点的跨日逻辑
     * 3. 自动切换上下午状态
     *
     * 【核心算法】
     * - 12小时制：11点→12点表示从上午到下午（或反之），需要跨日
     * - 24小时制：23点→0点表示跨到第二天
     */
    private NumberPicker.OnValueChangeListener mOnHourChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            boolean isDateChanged = false;
            Calendar cal = Calendar.getInstance();

            if (!mIs24HourView) {
                // 12小时制跨日/上下午切换处理
                // 上午11点 -> 下午12点：日期加1天
                if (!mIsAm && oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                }
                // 下午12点 -> 上午11点：日期减1天
                else if (mIsAm && oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }

                // 切换上下午状态
                if (oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY ||
                        oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1) {
                    mIsAm = !mIsAm;
                    updateAmPmControl();  // 更新上下午显示
                }
            } else {
                // 24小时制跨日处理
                // 23点 -> 0点：日期加1天
                if (oldVal == HOURS_IN_ALL_DAY - 1 && newVal == 0) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                }
                // 0点 -> 23点：日期减1天
                else if (oldVal == 0 && newVal == HOURS_IN_ALL_DAY - 1) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }
            }

            // 计算真实小时（转换12小时制到24小时制）
            int newHour = mHourSpinner.getValue() % HOURS_IN_HALF_DAY + (mIsAm ? 0 : HOURS_IN_HALF_DAY);
            mDate.set(Calendar.HOUR_OF_DAY, newHour);
            onDateTimeChanged();

            // 如果日期发生变化，更新年月日
            if (isDateChanged) {
                setCurrentYear(cal.get(Calendar.YEAR));
                setCurrentMonth(cal.get(Calendar.MONTH));
                setCurrentDay(cal.get(Calendar.DAY_OF_MONTH));
            }
        }
    };

    /**
     * 分钟滚轮监听器：处理分钟循环、进位/借位
     *
     * 【函数功能】
     * 当分钟从59滚动到0时，小时自动+1
     * 当分钟从0滚动到59时，小时自动-1
     * 并处理小时变化带来的日期变化
     */
    private NumberPicker.OnValueChangeListener mOnMinuteChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            int minValue = mMinuteSpinner.getMinValue();
            int maxValue = mMinuteSpinner.getMaxValue();
            int offset = 0;

            // 分钟进位/借位处理
            if (oldVal == maxValue && newVal == minValue) {
                offset += 1;  // 59 -> 0，小时+1
            } else if (oldVal == minValue && newVal == maxValue) {
                offset -= 1;  // 0 -> 59，小时-1
            }

            // 如果有进位/借位，更新时间
            if (offset != 0) {
                mDate.add(Calendar.HOUR_OF_DAY, offset);
                mHourSpinner.setValue(getCurrentHour());  // 更新小时显示
                updateDateControl();  // 更新日期显示
                // 自动切换上下午
                int newHour = getCurrentHourOfDay();
                mIsAm = newHour < HOURS_IN_HALF_DAY;
                updateAmPmControl();
            }

            mDate.set(Calendar.MINUTE, newVal);
            onDateTimeChanged();
        }
    };

    /**
     * 上/下午滚轮监听器
     *
     * 【函数功能】
     * 切换上下午时，小时数相应增减12小时
     */
    private NumberPicker.OnValueChangeListener mOnAmPmChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            mIsAm = !mIsAm;
            // 切换上下午：上午→下午加12小时，下午→上午减12小时
            if (mIsAm) {
                mDate.add(Calendar.HOUR_OF_DAY, -HOURS_IN_HALF_DAY);
            } else {
                mDate.add(Calendar.HOUR_OF_DAY, HOURS_IN_HALF_DAY);
            }
            updateAmPmControl();
            onDateTimeChanged();
        }
    };

    /**
     * 时间改变回调接口
     *
     * 【接口功能】
     * 当用户通过滚动选择器改变任何时间字段时，通过此接口通知外部组件
     *
     * 【调用关系】
     * 由NoteEditActivity实现此接口，实时获取用户选择的时间
     */
    public interface OnDateTimeChangedListener {
        void onDateTimeChanged(DateTimePicker view, int year, int month,
                               int dayOfMonth, int hourOfDay, int minute);
    }

    /**
     * 构造方法：使用当前时间
     *
     * @param context 上下文
     */
    public DateTimePicker(Context context) {
        this(context, System.currentTimeMillis());
    }

    /**
     * 构造方法：指定初始时间
     *
     * @param context 上下文
     * @param date 初始时间（毫秒时间戳）
     */
    public DateTimePicker(Context context, long date) {
        this(context, date, DateFormat.is24HourFormat(context));
    }

    /**
     * 完整构造方法：初始化所有控件、监听器、状态
     *
     * 【函数功能】
     * 1. 加载布局文件datetime_picker.xml
     * 2. 初始化4个NumberPicker控件
     * 3. 设置各滚轮的取值范围和监听器
     * 4. 根据系统时间格式设置12/24小时制
     * 5. 设置初始时间
     *
     * 【关键点】
     * - 日期滚轮显示近7天（通过updateDateControl实现）
     * - 分钟滚轮设置长按更新间隔为100ms，提高滚动效率
     * - 使用DateFormatSymbols获取系统上午/下午文本
     *
     * @param context 上下文
     * @param date 初始时间
     * @param is24HourView 是否使用24小时制
     */
    public DateTimePicker(Context context, long date, boolean is24HourView) {
        super(context);
        mDate = Calendar.getInstance();
        mInitialising = true;  // 标记正在初始化，避免触发回调
        mIsAm = getCurrentHourOfDay() >= HOURS_IN_HALF_DAY;

        // 加载布局
        inflate(context, R.layout.datetime_picker, this);

        // 初始化日期滚轮
        mDateSpinner = (NumberPicker) findViewById(R.id.date);
        mDateSpinner.setMinValue(DATE_SPINNER_MIN_VAL);
        mDateSpinner.setMaxValue(DATE_SPINNER_MAX_VAL);
        mDateSpinner.setOnValueChangedListener(mOnDateChangedListener);

        // 初始化小时滚轮
        mHourSpinner = (NumberPicker) findViewById(R.id.hour);
        mHourSpinner.setOnValueChangedListener(mOnHourChangedListener);

        // 初始化分钟滚轮
        mMinuteSpinner =  (NumberPicker) findViewById(R.id.minute);
        mMinuteSpinner.setMinValue(MINUT_SPINNER_MIN_VAL);
        mMinuteSpinner.setMaxValue(MINUT_SPINNER_MAX_VAL);
        mMinuteSpinner.setOnLongPressUpdateInterval(100);  // 长按时100ms更新一次
        mMinuteSpinner.setOnValueChangedListener(mOnMinuteChangedListener);

        // 初始化上/下午滚轮
        String[] stringsForAmPm = new DateFormatSymbols().getAmPmStrings();  // 获取"上午"/"下午"
        mAmPmSpinner = (NumberPicker) findViewById(R.id.amPm);
        mAmPmSpinner.setMinValue(AMPM_SPINNER_MIN_VAL);
        mAmPmSpinner.setMaxValue(AMPM_SPINNER_MAX_VAL);
        mAmPmSpinner.setDisplayedValues(stringsForAmPm);  // 设置显示文本
        mAmPmSpinner.setOnValueChangedListener(mOnAmPmChangedListener);

        // 更新控件初始显示
        updateDateControl();
        updateHourControl();
        updateAmPmControl();

        // 设置24/12小时制
        set24HourView(is24HourView);

        // 设置初始时间
        setCurrentDate(date);

        // 设置可用状态
        setEnabled(isEnabled());

        mInitialising = false;  // 初始化完成
    }

    /**
     * 设置控件整体可用状态
     *
     * 【函数功能】
     * 统一启用/禁用所有子控件
     */
    @Override
    public void setEnabled(boolean enabled) {
        if (mIsEnabled == enabled) {
            return;
        }
        super.setEnabled(enabled);
        mDateSpinner.setEnabled(enabled);
        mMinuteSpinner.setEnabled(enabled);
        mHourSpinner.setEnabled(enabled);
        mAmPmSpinner.setEnabled(enabled);
        mIsEnabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return mIsEnabled;
    }

    /**
     * 获取当前选择时间（毫秒）
     *
     * 【函数功能】
     * 返回用户当前选择的时间戳，用于保存到数据库
     *
     * @return 毫秒时间戳
     */
    public long getCurrentDateInTimeMillis() {
        return mDate.getTimeInMillis();
    }

    /**
     * 设置当前时间（毫秒）
     *
     * @param date 毫秒时间戳
     */
    public void setCurrentDate(long date) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(date);
        setCurrentDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    /**
     * 完整设置年月日时分
     */
    public void setCurrentDate(int year, int month,
                               int dayOfMonth, int hourOfDay, int minute) {
        setCurrentYear(year);
        setCurrentMonth(month);
        setCurrentDay(dayOfMonth);
        setCurrentHour(hourOfDay);
        setCurrentMinute(minute);
    }

    // 获取/设置 年
    public int getCurrentYear() {
        return mDate.get(Calendar.YEAR);
    }
    public void setCurrentYear(int year) {
        if (!mInitialising && year == getCurrentYear()) {
            return;
        }
        mDate.set(Calendar.YEAR, year);
        updateDateControl();
        onDateTimeChanged();
    }

    // 获取/设置 月
    public int getCurrentMonth() {
        return mDate.get(Calendar.MONTH);
    }
    public void setCurrentMonth(int month) {
        if (!mInitialising && month == getCurrentMonth()) {
            return;
        }
        mDate.set(Calendar.MONTH, month);
        updateDateControl();
        onDateTimeChanged();
    }

    // 获取/设置 日
    public int getCurrentDay() {
        return mDate.get(Calendar.DAY_OF_MONTH);
    }
    public void setCurrentDay(int dayOfMonth) {
        if (!mInitialising && dayOfMonth == getCurrentDay()) {
            return;
        }
        mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        updateDateControl();
        onDateTimeChanged();
    }

    /**
     * 获取24小时制小时
     */
    public int getCurrentHourOfDay() {
        return mDate.get(Calendar.HOUR_OF_DAY);
    }

    /**
     * 获取当前界面显示的小时（适配12/24小时制）
     *
     * 【函数功能】
     * 根据当前制式返回显示的小时值
     * - 24小时制：返回0-23
     * - 12小时制：返回1-12
     */
    private int getCurrentHour() {
        if (mIs24HourView){
            return getCurrentHourOfDay();
        } else {
            int hour = getCurrentHourOfDay();
            if (hour > HOURS_IN_HALF_DAY) {
                return hour - HOURS_IN_HALF_DAY;
            } else {
                return hour == 0 ? HOURS_IN_HALF_DAY : hour;
            }
        }
    }

    /**
     * 设置小时（自动适配12/24小时制）
     *
     * @param hourOfDay 24小时制小时（0-23）
     */
    public void setCurrentHour(int hourOfDay) {
        if (!mInitialising && hourOfDay == getCurrentHourOfDay()) {
            return;
        }
        mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
        if (!mIs24HourView) {
            // 12小时制自动判断上午/下午
            if (hourOfDay >= HOURS_IN_HALF_DAY) {
                mIsAm = false;
                hourOfDay = (hourOfDay > HOURS_IN_HALF_DAY) ? hourOfDay - 12 : 12;
            } else {
                mIsAm = true;
                hourOfDay = (hourOfDay == 0) ? 12 : hourOfDay;
            }
            updateAmPmControl();
        }
        mHourSpinner.setValue(hourOfDay);
        onDateTimeChanged();
    }

    // 获取/设置 分钟
    public int getCurrentMinute() {
        return mDate.get(Calendar.MINUTE);
    }
    public void setCurrentMinute(int minute) {
        if (!mInitialising && minute == getCurrentMinute()) {
            return;
        }
        mMinuteSpinner.setValue(minute);
        mDate.set(Calendar.MINUTE, minute);
        onDateTimeChanged();
    }

    /**
     * 是否24小时制
     */
    public boolean is24HourView () {
        return mIs24HourView;
    }

    /**
     * 切换24/12小时制
     *
     * 【函数功能】
     * 动态切换时间显示格式，同时调整小时滚轮的取值范围
     *
     * @param is24HourView true:24小时制，false:12小时制
     */
    public void set24HourView(boolean is24HourView) {
        if (mIs24HourView == is24HourView) {
            return;
        }
        mIs24HourView = is24HourView;
        // 24小时制时隐藏上下午选择器
        mAmPmSpinner.setVisibility(is24HourView ? View.GONE : View.VISIBLE);
        int hour = getCurrentHourOfDay();
        updateHourControl();  // 更新小时滚轮取值范围
        setCurrentHour(hour);
        updateAmPmControl();
    }

    /**
     * 更新日期滚轮显示（近7天）
     *
     * 【函数功能】
     * 以当前日期为中心，显示前后各3天，共7天的日期选项
     * 格式：MM.dd EEEE（如：01.15 星期一）
     *
     * 【算法说明】
     * 1. 将日历设置为当前日期前3天
     * 2. 依次生成7天的显示文本
     * 3. 将中间项（索引3）作为默认选中项
     */
    private void updateDateControl() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mDate.getTimeInMillis());
        cal.add(Calendar.DAY_OF_YEAR, -DAYS_IN_ALL_WEEK / 2 - 1);  // 向前偏移4天
        mDateSpinner.setDisplayedValues(null);

        // 生成近一周日期显示文本
        for (int i = 0; i < DAYS_IN_ALL_WEEK; ++i) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            mDateDisplayValues[i] = (String) DateFormat.format("MM.dd EEEE", cal);
        }
        mDateSpinner.setDisplayedValues(mDateDisplayValues);
        mDateSpinner.setValue(DAYS_IN_ALL_WEEK / 2);  // 选中中间项
        mDateSpinner.invalidate();
    }

    /**
     * 更新上/下午控件显示
     */
    private void updateAmPmControl() {
        if (!mIs24HourView) {
            int index = mIsAm ? Calendar.AM : Calendar.PM;
            mAmPmSpinner.setValue(index);
            mAmPmSpinner.setVisibility(View.VISIBLE);
        } else {
            mAmPmSpinner.setVisibility(View.GONE);
        }
    }

    /**
     * 更新小时滚轮取值范围
     *
     * 【函数功能】
     * 根据12/24小时制设置小时选择器的取值范围
     */
    private void updateHourControl() {
        if (mIs24HourView) {
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW);
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW);
        } else {
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW);
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW);
        }
    }

    /**
     * 设置时间改变监听器
     *
     * @param callback 监听器实现
     */
    public void setOnDateTimeChangedListener(OnDateTimeChangedListener callback) {
        mOnDateTimeChangedListener = callback;
    }

    /**
     * 回调时间改变事件
     *
     * 【函数功能】
     * 当任何时间字段发生变化时，通过接口通知外部组件
     */
    private void onDateTimeChanged() {
        if (mOnDateTimeChangedListener != null) {
            mOnDateTimeChangedListener.onDateTimeChanged(this, getCurrentYear(),
                    getCurrentMonth(), getCurrentDay(), getCurrentHourOfDay(), getCurrentMinute());
        }
    }
}