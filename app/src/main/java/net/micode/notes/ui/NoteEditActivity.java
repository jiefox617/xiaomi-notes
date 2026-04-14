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

package net.micode.notes.ui;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.model.WorkingNote.NoteSettingChangedListener;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.tool.ResourceParser.TextAppearanceResources;
import net.micode.notes.ui.NoteEditText.OnTextViewChangeListener;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 笔记编辑Activity
 *
 * 【类功能】
 * 笔记编辑界面，支持普通文本编辑和清单模式，提供背景颜色、字体大小设置，
 * 以及提醒闹钟、分享、桌面快捷方式等功能。
 *
 * 【类间调用关系】
 * - 被NotesListActivity调用：点击笔记时启动
 * - 调用WorkingNote：笔记数据模型，封装数据库操作
 * - 调用DateTimePickerDialog：设置提醒时间
 * - 调用AlarmReceiver：设置系统闹钟
 * - 调用DataUtils：数据库工具类
 * - 实现NoteSettingChangedListener：监听笔记设置变化
 * - 实现OnTextViewChangeListener：监听清单模式文本变化
 */
public class NoteEditActivity extends Activity implements OnClickListener,
        NoteSettingChangedListener, OnTextViewChangeListener {

    // 头部视图缓存
    private class HeadViewHolder {
        public TextView tvModified;      // 最后修改时间
        public ImageView ivAlertIcon;    // 闹钟图标
        public TextView tvAlertDate;     // 提醒时间
        public ImageView ibSetBgColor;   // 背景颜色按钮
    }

    // 背景颜色映射
    private static final Map<Integer, Integer> sBgSelectorBtnsMap = new HashMap<>();
    static {
        sBgSelectorBtnsMap.put(R.id.iv_bg_yellow, ResourceParser.YELLOW);
        sBgSelectorBtnsMap.put(R.id.iv_bg_red, ResourceParser.RED);
        sBgSelectorBtnsMap.put(R.id.iv_bg_blue, ResourceParser.BLUE);
        sBgSelectorBtnsMap.put(R.id.iv_bg_green, ResourceParser.GREEN);
        sBgSelectorBtnsMap.put(R.id.iv_bg_white, ResourceParser.WHITE);
    }

    private static final Map<Integer, Integer> sBgSelectorSelectionMap = new HashMap<>();
    static {
        sBgSelectorSelectionMap.put(ResourceParser.YELLOW, R.id.iv_bg_yellow_select);
        sBgSelectorSelectionMap.put(ResourceParser.RED, R.id.iv_bg_red_select);
        sBgSelectorSelectionMap.put(ResourceParser.BLUE, R.id.iv_bg_blue_select);
        sBgSelectorSelectionMap.put(ResourceParser.GREEN, R.id.iv_bg_green_select);
        sBgSelectorSelectionMap.put(ResourceParser.WHITE, R.id.iv_bg_white_select);
    }

    // 字体大小映射
    private static final Map<Integer, Integer> sFontSizeBtnsMap = new HashMap<>();
    static {
        sFontSizeBtnsMap.put(R.id.ll_font_large, ResourceParser.TEXT_LARGE);
        sFontSizeBtnsMap.put(R.id.ll_font_small, ResourceParser.TEXT_SMALL);
        sFontSizeBtnsMap.put(R.id.ll_font_normal, ResourceParser.TEXT_MEDIUM);
        sFontSizeBtnsMap.put(R.id.ll_font_super, ResourceParser.TEXT_SUPER);
    }

    private static final Map<Integer, Integer> sFontSelectorSelectionMap = new HashMap<>();
    static {
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_LARGE, R.id.iv_large_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SMALL, R.id.iv_small_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_MEDIUM, R.id.iv_medium_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SUPER, R.id.iv_super_select);
    }

    // 清单模式标记
    public static final String TAG_CHECKED = String.valueOf('\u221A');     // ✓
    public static final String TAG_UNCHECKED = String.valueOf('\u25A1');   // □

    private HeadViewHolder mNoteHeaderHolder;
    private View mHeadViewPanel;
    private View mNoteBgColorSelector;
    private View mFontSizeSelector;
    private EditText mNoteTitle;
    private EditText mNoteContent;
    private View mNoteEditorPanel;
    private LinearLayout mEditTextList;

    private WorkingNote mWorkingNote;
    private SharedPreferences mSharedPrefs;
    private int mFontSizeId;
    private String mUserQuery;
    private Pattern mPattern;

    private static final String PREFERENCE_FONT_SIZE = "pref_font_size";
    private static final int SHORTCUT_ICON_TITLE_MAX_LEN = 10;

    // ====================== 生命周期 ======================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_edit);

        if (savedInstanceState == null && !initActivityState(getIntent())) {
            finish();
            return;
        }
        initResources();
    }

    /**
     * 初始化Activity状态
     * 根据Intent的Action判断是查看已有笔记还是创建新笔记
     */
    private boolean initActivityState(Intent intent) {
        mWorkingNote = null;

        // 查看已有笔记
        if (TextUtils.equals(Intent.ACTION_VIEW, intent.getAction())) {
            long noteId = intent.getLongExtra(Intent.EXTRA_UID, 0);
            mUserQuery = "";

            if (intent.hasExtra(SearchManager.EXTRA_DATA_KEY)) {
                noteId = Long.parseLong(intent.getStringExtra(SearchManager.EXTRA_DATA_KEY));
                mUserQuery = intent.getStringExtra(SearchManager.USER_QUERY);
            }

            if (!DataUtils.visibleInNoteDatabase(getContentResolver(), noteId, Notes.TYPE_NOTE)) {
                startActivity(new Intent(this, NotesListActivity.class));
                showToast(R.string.error_note_not_exist);
                return false;
            } else {
                mWorkingNote = WorkingNote.load(this, noteId);
                if (mWorkingNote == null) return false;
            }
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        // 新建笔记
        else if (TextUtils.equals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction())) {
            long folderId = intent.getLongExtra(Notes.INTENT_EXTRA_FOLDER_ID, 0);
            int widgetId = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            int widgetType = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_TYPE, Notes.TYPE_WIDGET_INVALIDE);
            int bgResId = intent.getIntExtra(Notes.INTENT_EXTRA_BACKGROUND_ID, ResourceParser.getDefaultBgId(this));

            // 处理通话记录笔记
            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            long callDate = intent.getLongExtra(Notes.INTENT_EXTRA_CALL_DATE, 0);
            if (callDate != 0 && phoneNumber != null) {
                long noteId = DataUtils.getNoteIdByPhoneNumberAndCallDate(
                        getContentResolver(), phoneNumber, callDate);
                if (noteId > 0) {
                    mWorkingNote = WorkingNote.load(this, noteId);
                } else {
                    mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType, bgResId);
                    mWorkingNote.convertToCallNote(phoneNumber, callDate);
                }
            } else {
                mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType, bgResId);
            }
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        } else {
            return false;
        }

        mWorkingNote.setOnSettingStatusChangedListener(this);
        return true;
    }

    /**
     * 初始化UI控件
     */
    private void initResources() {
        mHeadViewPanel = findViewById(R.id.note_title);

        mNoteHeaderHolder = new HeadViewHolder();
        mNoteHeaderHolder.tvModified = findViewById(R.id.tv_modified_date);
        mNoteHeaderHolder.ivAlertIcon = findViewById(R.id.iv_alert_icon);
        mNoteHeaderHolder.tvAlertDate = findViewById(R.id.tv_alert_date);
        mNoteHeaderHolder.ibSetBgColor = findViewById(R.id.btn_set_bg_color);
        mNoteHeaderHolder.ibSetBgColor.setOnClickListener(this);

        mNoteTitle = findViewById(R.id.note_edit_title);
        mNoteContent = findViewById(R.id.note_edit_content);
        mNoteEditorPanel = findViewById(R.id.sv_note_edit);
        mNoteBgColorSelector = findViewById(R.id.note_bg_color_selector);
        mFontSizeSelector = findViewById(R.id.font_size_selector);
        mEditTextList = findViewById(R.id.note_edit_list);

        // 设置背景颜色按钮监听
        for (int id : sBgSelectorBtnsMap.keySet()) {
            findViewById(id).setOnClickListener(this);
        }
        // 设置字体大小按钮监听
        for (int id : sFontSizeBtnsMap.keySet()) {
            findViewById(id).setOnClickListener(this);
        }

        // 读取保存的字体大小
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mFontSizeId = mSharedPrefs.getInt(PREFERENCE_FONT_SIZE, ResourceParser.BG_DEFAULT_FONT_SIZE);
        if (mFontSizeId >= TextAppearanceResources.getResourcesSize()) {
            mFontSizeId = ResourceParser.BG_DEFAULT_FONT_SIZE;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        initNoteScreen();
    }

    /**
     * 刷新笔记界面
     */
    private void initNoteScreen() {
        mNoteTitle.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
        mNoteContent.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));

        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            // 清单模式
            mNoteTitle.setVisibility(View.GONE);
            mNoteContent.setVisibility(View.GONE);
            mEditTextList.setVisibility(View.VISIBLE);
            switchToListMode(mWorkingNote.getContent());
        } else {
            // 普通模式
            mNoteTitle.setVisibility(View.VISIBLE);
            mNoteContent.setVisibility(View.VISIBLE);
            mEditTextList.setVisibility(View.GONE);

            String content = mWorkingNote.getContent();
            if (content == null) content = "";
            int lineBreak = content.indexOf('\n');
            if (lineBreak >= 0) {
                mNoteTitle.setText(content.substring(0, lineBreak));
                mNoteContent.setText(content.substring(lineBreak + 1));
            } else {
                mNoteTitle.setText(content);
                mNoteContent.setText("");
            }
        }

        // 隐藏所有选中标记
        for (Integer id : sBgSelectorSelectionMap.keySet()) {
            findViewById(sBgSelectorSelectionMap.get(id)).setVisibility(View.GONE);
        }

        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());

        mNoteHeaderHolder.tvModified.setText(DateUtils.formatDateTime(this,
                mWorkingNote.getModifiedDate(), DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_TIME
                        | DateUtils.FORMAT_SHOW_YEAR));

        showAlertHeader();
    }

    /**
     * 显示提醒头部
     */
    private void showAlertHeader() {
        if (mWorkingNote.hasClockAlert()) {
            long now = System.currentTimeMillis();
            if (now > mWorkingNote.getAlertDate()) {
                mNoteHeaderHolder.tvAlertDate.setText(R.string.note_alert_expired);
            } else {
                mNoteHeaderHolder.tvAlertDate.setText(DateUtils.getRelativeTimeSpanString(
                        mWorkingNote.getAlertDate(), now, DateUtils.MINUTE_IN_MILLIS));
            }
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.VISIBLE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.VISIBLE);
        } else {
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.GONE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveNote();
        clearSettingState();
    }

    // ====================== 保存逻辑 ======================

    /**
     * 获取当前编辑内容
     */
    private boolean getWorkingText() {
        boolean hasChecked = false;

        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mEditTextList.getChildCount(); i++) {
                View view = mEditTextList.getChildAt(i);
                NoteEditText edit = view.findViewById(R.id.et_edit_text);
                if (!TextUtils.isEmpty(edit.getText())) {
                    if (((CheckBox) view.findViewById(R.id.cb_edit_item)).isChecked()) {
                        sb.append(TAG_CHECKED).append(" ").append(edit.getText()).append("\n");
                        hasChecked = true;
                    } else {
                        sb.append(TAG_UNCHECKED).append(" ").append(edit.getText()).append("\n");
                    }
                }
            }
            mWorkingNote.setWorkingText(sb.toString());
        } else {
            String title = mNoteTitle.getText().toString();
            String body = mNoteContent.getText().toString();
            mWorkingNote.setWorkingText(title + "\n" + body);
        }
        return hasChecked;
    }

    /**
     * 保存笔记
     * 空内容自动删除，有内容时自动生成标题
     */
    private void saveNote() {
        getWorkingText();
        String content = mWorkingNote.getContent();

        // 空内容 → 删除
        if (TextUtils.isEmpty(content) || content.trim().isEmpty()) {
            if (mWorkingNote.existInDatabase()) {
                deleteCurrentNote();
            }
            return;
        }

        // 提取标题和正文
        String title, body;
        int lineBreak = content.indexOf('\n');
        if (lineBreak >= 0) {
            title = content.substring(0, lineBreak).trim();
            body = content.substring(lineBreak + 1).trim();
        } else {
            title = content.trim();
            body = "";
        }

        // 正文有内容但标题为空 → 自动生成标题
        if (!TextUtils.isEmpty(body) && TextUtils.isEmpty(title)) {
            String time = DateUtils.formatDateTime(this, System.currentTimeMillis(),
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NUMERIC_DATE
                            | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_YEAR);
            title = "未命名 " + time;
            content = title + "\n" + body;
            mWorkingNote.setWorkingText(content);
        }

        if (mWorkingNote.saveNote()) {
            setResult(RESULT_OK);
        }
    }

    /**
     * 删除当前笔记
     */
    private void deleteCurrentNote() {
        if (mWorkingNote.existInDatabase()) {
            HashSet<Long> ids = new HashSet<>();
            long id = mWorkingNote.getNoteId();
            if (id != Notes.ID_ROOT_FOLDER) {
                ids.add(id);
            }
            if (NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0) {
                DataUtils.batchMoveToFolder(getContentResolver(), ids, Notes.ID_TRASH_FOLER);
            } else {
                DataUtils.batchDeleteNotes(getContentResolver(), ids);
            }
        }
        mWorkingNote.markDeleted(true);
    }

    // ====================== 清单模式 ======================

    /**
     * 切换到清单模式
     */
    private void switchToListMode(String text) {
        mEditTextList.removeAllViews();
        String[] items = text.split("\n");
        int index = 0;
        for (String item : items) {
            if (!TextUtils.isEmpty(item)) {
                mEditTextList.addView(getListItem(item, index++));
            }
        }
        mEditTextList.addView(getListItem("", index));
        mEditTextList.getChildAt(index).findViewById(R.id.et_edit_text).requestFocus();
    }

    /**
     * 创建列表项视图
     */
    private View getListItem(String item, int index) {
        View view = LayoutInflater.from(this).inflate(R.layout.note_edit_list_item, null);
        final NoteEditText edit = view.findViewById(R.id.et_edit_text);
        edit.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
        CheckBox cb = view.findViewById(R.id.cb_edit_item);

        cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                edit.setPaintFlags(Paint.DEV_KERN_TEXT_FLAG);
            }
        });

        if (item.startsWith(TAG_CHECKED)) {
            cb.setChecked(true);
            edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            item = item.substring(TAG_CHECKED.length()).trim();
        } else if (item.startsWith(TAG_UNCHECKED)) {
            cb.setChecked(false);
            item = item.substring(TAG_UNCHECKED.length()).trim();
        }

        edit.setOnTextViewChangeListener(this);
        edit.setIndex(index);
        edit.setText(getHighlightQueryResult(item, mUserQuery));
        return view;
    }

    /**
     * 高亮搜索关键词
     */
    private Spannable getHighlightQueryResult(String fullText, String userQuery) {
        SpannableString spannable = new SpannableString(fullText == null ? "" : fullText);
        if (!TextUtils.isEmpty(userQuery)) {
            mPattern = Pattern.compile(userQuery);
            Matcher m = mPattern.matcher(fullText);
            int start = 0;
            while (m.find(start)) {
                spannable.setSpan(new BackgroundColorSpan(getResources().getColor(R.color.user_query_highlight)),
                        m.start(), m.end(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                start = m.end();
            }
        }
        return spannable;
    }

    @Override
    public void onEditTextDelete(int index, String text) {
        int childCount = mEditTextList.getChildCount();
        if (childCount == 1) return;

        for (int i = index + 1; i < childCount; i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text)).setIndex(i - 1);
        }
        mEditTextList.removeViewAt(index);

        NoteEditText edit = (NoteEditText) mEditTextList.getChildAt(Math.max(0, index - 1))
                .findViewById(R.id.et_edit_text);
        int len = edit.length();
        edit.append(text);
        edit.requestFocus();
        edit.setSelection(len);
    }

    @Override
    public void onEditTextEnter(int index, String text) {
        View view = getListItem(text, index);
        mEditTextList.addView(view, index);
        NoteEditText edit = view.findViewById(R.id.et_edit_text);
        edit.requestFocus();
        edit.setSelection(0);

        for (int i = index + 1; i < mEditTextList.getChildCount(); i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text)).setIndex(i);
        }
    }

    @Override
    public void onTextChange(int index, boolean hasText) {
        if (index >= mEditTextList.getChildCount()) return;
        mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item)
                .setVisibility(hasText ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onCheckListModeChanged(int oldMode, int newMode) {
        getWorkingText();

        if (newMode == TextNote.MODE_CHECK_LIST) {
            mNoteTitle.setVisibility(View.GONE);
            mNoteContent.setVisibility(View.GONE);
            mEditTextList.setVisibility(View.VISIBLE);
            switchToListMode(mWorkingNote.getContent());
        } else {
            mEditTextList.setVisibility(View.GONE);
            mNoteTitle.setVisibility(View.VISIBLE);
            mNoteContent.setVisibility(View.VISIBLE);

            String content = mWorkingNote.getContent();
            int lineBreak = content.indexOf('\n');
            if (lineBreak >= 0) {
                mNoteTitle.setText(content.substring(0, lineBreak));
                mNoteContent.setText(content.substring(lineBreak + 1));
            } else {
                mNoteTitle.setText(content);
                mNoteContent.setText("");
            }
        }
    }

    // ====================== 闹钟提醒 ======================

    @Override
    public void onClockAlertChanged(long date, boolean set) {
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        if (mWorkingNote.getNoteId() > 0) {
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mWorkingNote.getNoteId()));
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            showAlertHeader();

            if (!set) {
                alarmManager.cancel(pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, date, pendingIntent);
            }
        } else {
            showToast(R.string.error_note_empty_for_clock);
        }
    }

    @Override
    public void onBackgroundColorChanged() {
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
    }

    @Override
    public void onWidgetChanged() {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            return;
        }
        int[] ids = {mWorkingNote.getWidgetId()};
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(intent);
    }

    // ====================== 点击事件 ======================

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.btn_set_bg_color) {
            mNoteBgColorSelector.setVisibility(View.VISIBLE);
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(View.VISIBLE);
        } else if (sBgSelectorBtnsMap.containsKey(id)) {
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(View.GONE);
            mWorkingNote.setBgColorId(sBgSelectorBtnsMap.get(id));
            mNoteBgColorSelector.setVisibility(View.GONE);
        } else if (sFontSizeBtnsMap.containsKey(id)) {
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.GONE);
            mFontSizeId = sFontSizeBtnsMap.get(id);
            mSharedPrefs.edit().putInt(PREFERENCE_FONT_SIZE, mFontSizeId).apply();
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);

            mNoteTitle.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
            mNoteContent.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
            mFontSizeSelector.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE && !inRangeOfView(mNoteBgColorSelector, ev)) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        }
        if (mFontSizeSelector.getVisibility() == View.VISIBLE && !inRangeOfView(mFontSizeSelector, ev)) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean inRangeOfView(View view, MotionEvent ev) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        return !(ev.getX() < x || ev.getX() > x + view.getWidth()
                || ev.getY() < y || ev.getY() > y + view.getHeight());
    }

    @Override
    public void onBackPressed() {
        if (clearSettingState()) return;
        saveNote();
        super.onBackPressed();
    }

    private boolean clearSettingState() {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        } else if (mFontSizeSelector.getVisibility() == View.VISIBLE) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    // ====================== 菜单 ======================

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isFinishing()) return true;
        clearSettingState();
        menu.clear();

        if (mWorkingNote.getFolderId() == Notes.ID_CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_note_edit, menu);
        } else {
            getMenuInflater().inflate(R.menu.note_edit, menu);
        }

        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_normal_mode);
        } else {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_list_mode);
        }

        if (mWorkingNote.hasClockAlert()) {
            menu.findItem(R.id.menu_alert).setVisible(false);
            menu.findItem(R.id.menu_delete_remind).setVisible(true);
        } else {
            menu.findItem(R.id.menu_alert).setVisible(true);
            menu.findItem(R.id.menu_delete_remind).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.menu_new_note) {
            saveNote();
            finish();
            Intent intent = new Intent(this, NoteEditActivity.class);
            intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
            intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mWorkingNote.getFolderId());
            startActivity(intent);
        } else if (itemId == R.id.menu_delete) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.alert_title_delete)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.alert_message_delete_note)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        deleteCurrentNote();
                        finish();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else if (itemId == R.id.menu_font_size) {
            mFontSizeSelector.setVisibility(View.VISIBLE);
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
        } else if (itemId == R.id.menu_list_mode) {
            mWorkingNote.setCheckListMode(mWorkingNote.getCheckListMode() == 0
                    ? TextNote.MODE_CHECK_LIST : 0);
        } else if (itemId == R.id.menu_share) {
            getWorkingText();
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_TEXT, mWorkingNote.getContent());
            shareIntent.setType("text/plain");
            startActivity(shareIntent);
        } else if (itemId == R.id.menu_send_to_desktop) {
            if (!mWorkingNote.existInDatabase()) saveNote();
            if (mWorkingNote.getNoteId() > 0) {
                Intent shortcutIntent = new Intent(this, NoteEditActivity.class);
                shortcutIntent.setAction(Intent.ACTION_VIEW);
                shortcutIntent.putExtra(Intent.EXTRA_UID, mWorkingNote.getNoteId());

                Intent sender = new Intent();
                sender.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
                String title = mWorkingNote.getContent().replace(TAG_CHECKED, "").replace(TAG_UNCHECKED, "");
                if (title.length() > SHORTCUT_ICON_TITLE_MAX_LEN) {
                    title = title.substring(0, SHORTCUT_ICON_TITLE_MAX_LEN);
                }
                sender.putExtra(Intent.EXTRA_SHORTCUT_NAME, title);
                sender.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                        Intent.ShortcutIconResource.fromContext(this, R.drawable.icon_app));
                sender.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
                showToast(R.string.info_note_enter_desktop);
                sendBroadcast(sender);
            } else {
                showToast(R.string.error_note_empty_for_send_to_desktop);
            }
        } else if (itemId == R.id.menu_alert) {
            DateTimePickerDialog d = new DateTimePickerDialog(this, System.currentTimeMillis());
            d.setOnDateTimeSetListener((dialog, date) -> mWorkingNote.setAlertDate(date, true));
            d.show();
        } else if (itemId == R.id.menu_delete_remind) {
            mWorkingNote.setAlertDate(0, false);
        }
        return true;
    }

    private void showToast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }
}