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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.tool.BackupUtils;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import android.widget.ArrayAdapter;
import android.view.ViewGroup;
import androidx.annotation.NonNull;

/**
 * 笔记列表主页面
 *
 * 【类功能】
 * 1. 展示笔记列表，支持按分组筛选（全部/收藏/回收站/自定义分组）
 * 2. 支持笔记的增删改查、收藏、移动、恢复等操作
 * 3. 支持多选模式（批量删除/移动/收藏）
 * 4. 支持搜索笔记内容
 * 5. 支持与Google Tasks同步
 *
 * 【类间关系】
 * - 调用NoteEditActivity：新建/编辑笔记
 * - 使用NotesListAdapter：列表数据适配
 * - 使用NotesListItem：列表项视图
 * - 使用NoteItemData：列表项数据
 * - 调用GTaskSyncService：同步服务
 * - 使用WorkingNote：笔记数据模型
 * - 调用DataUtils：数据库操作工具
 * - 使用BackupUtils：导出备份
 */
public class NotesListActivity extends Activity implements OnClickListener, OnItemLongClickListener {

    // ==================== 异步查询常量 ====================
    private static final int NOTES_QUERY_TOKEN = 0;          // 笔记查询
    private static final int GROUPS_QUERY_TOKEN = 1;         // 分组查询
    private static final int DEST_FOLDERS_QUERY_TOKEN = 2;   // 目标文件夹查询

    // ==================== 分组上下文菜单常量 ====================
    private static final int MENU_GROUP_DELETE = 0;          // 删除分组
    private static final int MENU_GROUP_RENAME = 1;          // 重命名分组
    private static final int MENU_GROUP_MOVE_NOTES = 2;      // 移动分组内笔记

    // ==================== 配置常量 ====================
    private static final String PREFERENCE_ADD_INTRODUCTION = "net.micode.notes.introduction";
    private static final String TAG = "NotesListActivity";

    // ==================== 页面跳转请求码 ====================
    private final static int REQUEST_CODE_OPEN_NODE = 102;   // 打开笔记
    private final static int REQUEST_CODE_NEW_NODE = 103;    // 新建笔记

    // ==================== 核心成员变量 ====================
    private BackgroundQueryHandler mBackgroundQueryHandler;  // 异步查询处理器
    private NotesListAdapter mNotesListAdapter;              // 列表适配器
    private ListView mNotesListView;                         // 笔记列表
    private Button mAddNewNote;                              // 新建笔记按钮
    private TextView mTitleBar;                              // 标题栏（显示当前分组）
    private ContentResolver mContentResolver;                // 内容解析器
    private ModeCallback mModeCallBack;                      // 多选模式回调
    private NoteItemData mFocusNoteDataItem;                 // 当前长按选中的笔记
    private GroupInfo mFocusGroupInfo;                       // 当前长按选中的分组

    // ==================== 滑动分发相关 ====================
    private boolean mDispatch;
    private int mOriginY;
    private int mDispatchY;

    // ==================== 搜索相关 ====================
    private String mCurrentSearchQuery = null;               // 当前搜索关键词
    private boolean mIsSearchMode = false;                   // 是否处于搜索模式

    // ==================== 分组模式变量 ====================
    private long mCurrentGroupId = 0;                        // 当前分组ID（0=全部，-1=收藏，-2=回收站）
    private String mCurrentGroupName = "全部便签";            // 当前分组名称
    private final List<GroupInfo> mGroupList = new ArrayList<>();  // 分组列表

    /**
     * 分组信息实体类
     */
    private static class GroupInfo {
        static final int TYPE_ALL = 0;      // 全部便签
        static final int TYPE_FAVORITE = 1; // 收藏便签
        static final int TYPE_TRASH = 2;    // 回收站
        static final int TYPE_NORMAL = 3;   // 自定义分组

        long id;          // 分组ID
        String name;      // 分组名称
        int noteCount;    // 笔记数量
        int type;         // 分组类型

        GroupInfo(long id, String name, int noteCount, int type) {
            this.id = id;
            this.name = name;
            this.noteCount = noteCount;
            this.type = type;
        }
    }

    // ====================== 生命周期方法 ======================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_list);

        initResources();                    // 初始化控件
        setAppInfoFromRawRes();             // 首次启动添加引导笔记
        handleSearchIntent(getIntent());    // 处理搜索Intent
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSearchIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mIsSearchMode) {
            loadGroupList();        // 加载分组列表
            refreshNotesList();     // 刷新笔记列表
            updateNewNoteButtonVisibility();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && (requestCode == REQUEST_CODE_NEW_NODE || requestCode == REQUEST_CODE_OPEN_NODE)) {
            if (mIsSearchMode && mCurrentSearchQuery != null) {
                performSearch(mCurrentSearchQuery);  // 重新搜索
            } else {
                refreshNotesList();                  // 刷新列表
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        if (mIsSearchMode) {
            clearSearch();  // 搜索模式下先退出搜索
            return;
        }
        super.onBackPressed();
    }

    // ====================== 初始化方法 ======================

    /**
     * 初始化所有控件、适配器、事件监听
     */
    private void initResources() {
        mContentResolver = this.getContentResolver();
        mBackgroundQueryHandler = new BackgroundQueryHandler(this.getContentResolver());

        // 初始化笔记列表
        mNotesListView = (ListView) findViewById(R.id.notes_list);
        mNotesListView.addFooterView(LayoutInflater.from(this).inflate(R.layout.note_list_footer, null), null, false);
        mNotesListView.setOnItemClickListener(new OnListItemClickListener());
        mNotesListView.setOnItemLongClickListener(this);

        mNotesListAdapter = new NotesListAdapter(this);
        mNotesListView.setAdapter(mNotesListAdapter);

        // 初始化新建笔记按钮
        mAddNewNote = (Button) findViewById(R.id.btn_new_note);
        mAddNewNote.setOnClickListener(this);
        mAddNewNote.setOnTouchListener(new NewNoteOnTouchListener());

        // 初始化标题栏
        mTitleBar = (TextView) findViewById(R.id.tv_title_bar);
        mTitleBar.setClickable(true);
        mTitleBar.setEnabled(true);
        mTitleBar.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showGroupSwitcherDialog();  // 点击：切换分组
            }
        });
        mTitleBar.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showGroupManageMenu();      // 长按：管理分组
                return true;
            }
        });

        // 滑动标记初始化
        mDispatch = false;
        mDispatchY = 0;
        mOriginY = 0;

        // 多选模式回调
        mModeCallBack = new ModeCallback();
    }

    /**
     * 首次启动创建引导介绍笔记
     */
    private void setAppInfoFromRawRes() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (!sp.getBoolean(PREFERENCE_ADD_INTRODUCTION, false)) {
            StringBuilder sb = new StringBuilder();
            InputStream in = null;
            try {
                in = getResources().openRawResource(R.raw.introduction);
                if (in != null) {
                    InputStreamReader isr = new InputStreamReader(in);
                    BufferedReader br = new BufferedReader(isr);
                    char[] buf = new char[1024];
                    int len;
                    while ((len = br.read(buf)) > 0) {
                        sb.append(buf, 0, len);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            } finally {
                if (in != null) {
                    try { in.close(); } catch (IOException e) { e.printStackTrace(); }
                }
            }

            WorkingNote note = WorkingNote.createEmptyNote(this, Notes.ID_ROOT_FOLDER,
                    AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                    ResourceParser.RED);
            note.setWorkingText(sb.toString());
            if (note.saveNote()) {
                sp.edit().putBoolean(PREFERENCE_ADD_INTRODUCTION, true).commit();
            }
        }
    }

    // ====================== 分组管理 ======================

    /**
     * 加载所有分组（全部、收藏、回收站、自定义分组）
     */
    private void loadGroupList() {
        mGroupList.clear();

        // 1. 全部便签
        int totalNoteCount = getTotalNoteCountExcludeTrash();
        mGroupList.add(new GroupInfo(0, "全部便签", totalNoteCount, GroupInfo.TYPE_ALL));

        // 2. 收藏便签
        int favoriteCount = getFavoriteNoteCount();
        mGroupList.add(new GroupInfo(-1, "收藏便签", favoriteCount, GroupInfo.TYPE_FAVORITE));

        // 3. 回收站
        int trashCount = getTrashNoteCount();
        mGroupList.add(new GroupInfo(Notes.ID_TRASH_FOLER, "回收站", trashCount, GroupInfo.TYPE_TRASH));

        // 4. 用户自定义分组（文件夹）
        String selection = NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER
                + " AND " + NoteColumns.PARENT_ID + "=" + Notes.ID_ROOT_FOLDER;
        Cursor cursor = mContentResolver.query(Notes.CONTENT_NOTE_URI,
                new String[]{NoteColumns.ID, NoteColumns.SNIPPET},
                selection, null, NoteColumns.MODIFIED_DATE + " DESC");

        if (cursor != null) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String name = cursor.getString(1);
                int noteCount = getNoteCountByFolder(id);
                mGroupList.add(new GroupInfo(id, name, noteCount, GroupInfo.TYPE_NORMAL));
            }
            cursor.close();
        }

        updateTitleBar();
    }

    /**
     * 获取非回收站笔记总数
     */
    private int getTotalNoteCountExcludeTrash() {
        int count = 0;
        Cursor cursor = mContentResolver.query(Notes.CONTENT_NOTE_URI,
                new String[]{"count(*) as c"},
                NoteColumns.TYPE + "=" + Notes.TYPE_NOTE + " AND " + NoteColumns.PARENT_ID + "<>?",
                new String[]{String.valueOf(Notes.ID_TRASH_FOLER)},
                null);
        if (cursor != null) {
            if (cursor.moveToFirst()) count = cursor.getInt(0);
            cursor.close();
        }
        return count;
    }

    /**
     * 获取收藏笔记数量（标题含[FAV]）
     */
    private int getFavoriteNoteCount() {
        int count = 0;
        Cursor cursor = mContentResolver.query(Notes.CONTENT_NOTE_URI,
                new String[]{"count(*) as c"},
                NoteColumns.TYPE + "=" + Notes.TYPE_NOTE + " AND " + NoteColumns.SNIPPET + " LIKE '%[FAV]%' AND " + NoteColumns.PARENT_ID + "<>?",
                new String[]{String.valueOf(Notes.ID_TRASH_FOLER)},
                null);
        if (cursor != null) {
            if (cursor.moveToFirst()) count = cursor.getInt(0);
            cursor.close();
        }
        return count;
    }

    /**
     * 获取回收站笔记数量
     */
    private int getTrashNoteCount() {
        int count = 0;
        Cursor cursor = mContentResolver.query(Notes.CONTENT_NOTE_URI,
                new String[]{"count(*) as c"},
                NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER + " AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE,
                null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) count = cursor.getInt(0);
            cursor.close();
        }
        return count;
    }

    /**
     * 获取指定文件夹下的笔记数量
     */
    private int getNoteCountByFolder(long folderId) {
        int count = 0;
        Cursor cursor = mContentResolver.query(Notes.CONTENT_NOTE_URI,
                new String[]{"count(*) as c"},
                NoteColumns.PARENT_ID + "=? AND " + NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[]{String.valueOf(folderId), String.valueOf(Notes.TYPE_NOTE), String.valueOf(Notes.ID_TRASH_FOLER)},
                null);
        if (cursor != null) {
            if (cursor.moveToFirst()) count = cursor.getInt(0);
            cursor.close();
        }
        return count;
    }

    /**
     * 更新标题栏显示
     */
    private void updateTitleBar() {
        if (mCurrentGroupId == 0) {
            mCurrentGroupName = "全部便签";
        } else if (mCurrentGroupId == -1) {
            mCurrentGroupName = "收藏便签";
        } else if (mCurrentGroupId == Notes.ID_TRASH_FOLER) {
            mCurrentGroupName = "回收站";
        } else {
            for (GroupInfo group : mGroupList) {
                if (group.id == mCurrentGroupId) {
                    mCurrentGroupName = group.name;
                    break;
                }
            }
        }
        mTitleBar.setText(mCurrentGroupName + " ▼");
    }

    /**
     * 更新新建笔记按钮可见性（收藏页/回收站不可新建）
     */
    private void updateNewNoteButtonVisibility() {
        if (mCurrentGroupId == -1 || mCurrentGroupId == Notes.ID_TRASH_FOLER) {
            mAddNewNote.setVisibility(View.GONE);
        } else {
            mAddNewNote.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 显示分组切换对话框
     */
    private void showGroupSwitcherDialog() {
        ArrayAdapter<GroupInfo> adapter = new ArrayAdapter<GroupInfo>(this, android.R.layout.select_dialog_singlechoice, mGroupList) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                TextView textView = (TextView) super.getView(position, convertView, parent);
                GroupInfo group = getItem(position);
                if (group != null) {
                    textView.setText(group.name + " (" + group.noteCount + ")");
                    if (group.type == GroupInfo.TYPE_ALL) {
                        textView.setTextSize(22);
                        textView.setTypeface(textView.getTypeface(), android.graphics.Typeface.BOLD);
                    } else if (group.type == GroupInfo.TYPE_FAVORITE || group.type == GroupInfo.TYPE_TRASH) {
                        textView.setTextSize(20);
                        textView.setTypeface(textView.getTypeface(), android.graphics.Typeface.BOLD);
                    } else {
                        textView.setTextSize(20);
                        textView.setTypeface(textView.getTypeface(), android.graphics.Typeface.NORMAL);
                    }
                }
                return textView;
            }
        };

        int checkedIndex = 0;
        for (int i = 0; i < mGroupList.size(); i++) {
            if (mGroupList.get(i).id == mCurrentGroupId) {
                checkedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("选择分组")
                .setSingleChoiceItems(adapter, checkedIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        GroupInfo selected = mGroupList.get(which);
                        mCurrentGroupId = selected.id;
                        mCurrentGroupName = selected.name;
                        updateTitleBar();
                        updateNewNoteButtonVisibility();
                        refreshNotesList();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 显示分组管理菜单（长按标题栏触发）
     */
    private void showGroupManageMenu() {
        List<GroupInfo> manageGroups = new ArrayList<>();
        for (GroupInfo g : mGroupList) {
            if (g.type == GroupInfo.TYPE_NORMAL) {
                manageGroups.add(g);
            }
        }

        if (manageGroups.isEmpty()) {
            Toast.makeText(this, "暂无分组可管理", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] groupNames = new String[manageGroups.size()];
        for (int i = 0; i < manageGroups.size(); i++) {
            groupNames[i] = manageGroups.get(i).name + " (" + manageGroups.get(i).noteCount + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle("请选择分组")
                .setItems(groupNames, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final GroupInfo group = manageGroups.get(which);
                        showGroupOperationDialog(group);
                    }
                })
                .show();
    }

    /**
     * 显示分组操作菜单：重命名、删除、移动笔记
     */
    private void showGroupOperationDialog(final GroupInfo group) {
        String[] operations = {"重命名分组", "删除分组", "移动分组内所有笔记"};

        new AlertDialog.Builder(this)
                .setTitle(group.name)
                .setItems(operations, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0: showRenameGroupDialog(group); break;
                            case 1: showDeleteGroupConfirmDialog(group); break;
                            case 2: showMoveGroupNotesDialog(group); break;
                        }
                    }
                })
                .show();
    }

    /**
     * 重命名分组对话框
     */
    private void showRenameGroupDialog(final GroupInfo group) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        final EditText etName = (EditText) view.findViewById(R.id.et_foler_name);
        etName.setText(group.name);
        etName.setSelection(etName.length());

        builder.setTitle("重命名分组");
        builder.setView(view);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, null);

        final Dialog dialog = builder.show();
        final Button positive = (Button) dialog.findViewById(android.R.id.button1);

        positive.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String newName = etName.getText().toString().trim();
                if (TextUtils.isEmpty(newName)) {
                    Toast.makeText(NotesListActivity.this, "分组名称不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                ContentValues values = new ContentValues();
                values.put(NoteColumns.SNIPPET, newName);
                values.put(NoteColumns.LOCAL_MODIFIED, 1);
                mContentResolver.update(Notes.CONTENT_NOTE_URI, values,
                        NoteColumns.ID + "=?", new String[]{String.valueOf(group.id)});

                dialog.dismiss();
                loadGroupList();
                if (mCurrentGroupId == group.id) {
                    mCurrentGroupName = newName;
                    updateTitleBar();
                }
                refreshNotesList();
                Toast.makeText(NotesListActivity.this, "重命名成功", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 删除分组确认对话框
     */
    private void showDeleteGroupConfirmDialog(final GroupInfo group) {
        new AlertDialog.Builder(this)
                .setTitle("删除分组")
                .setMessage("确定要删除分组 \"" + group.name + "\" 吗？\n分组内的笔记将移至回收站")
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteGroup(group);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * 删除分组：笔记移入回收站
     */
    private void deleteGroup(GroupInfo group) {
        Cursor cursor = mContentResolver.query(Notes.CONTENT_NOTE_URI,
                new String[]{NoteColumns.ID, NoteColumns.SNIPPET},
                NoteColumns.PARENT_ID + "=? AND " + NoteColumns.TYPE + "=?",
                new String[]{String.valueOf(group.id), String.valueOf(Notes.TYPE_NOTE)},
                null);

        if (cursor != null) {
            HashSet<Long> noteIds = new HashSet<>();
            while (cursor.moveToNext()) {
                long noteId = cursor.getLong(0);
                String snippet = cursor.getString(1);
                noteIds.add(noteId);

                ContentValues values = new ContentValues();
                values.put("widget_id", group.id);  // 保存原分组ID
                if (snippet != null && snippet.contains("[FAV]")) {
                    snippet = snippet.replace("[FAV] ", "");
                    values.put(NoteColumns.SNIPPET, snippet);
                }
                mContentResolver.update(Notes.CONTENT_NOTE_URI, values,
                        NoteColumns.ID + "=?", new String[]{String.valueOf(noteId)});
            }
            cursor.close();

            if (!noteIds.isEmpty()) {
                DataUtils.batchMoveToFolder(mContentResolver, noteIds, Notes.ID_TRASH_FOLER);
            }
        }

        HashSet<Long> ids = new HashSet<>();
        ids.add(group.id);
        DataUtils.batchDeleteNotes(mContentResolver, ids);

        if (mCurrentGroupId == group.id) {
            mCurrentGroupId = 0;
            mCurrentGroupName = "全部便签";
            updateTitleBar();
            updateNewNoteButtonVisibility();
        }

        loadGroupList();
        refreshNotesList();
        Toast.makeText(this, "分组已删除，便签已移至回收站", Toast.LENGTH_SHORT).show();
    }

    /**
     * 移动分组内所有笔记
     */
    private void showMoveGroupNotesDialog(final GroupInfo sourceGroup) {
        final List<GroupInfo> targetGroups = new ArrayList<>();
        for (GroupInfo g : mGroupList) {
            if (g.type == GroupInfo.TYPE_NORMAL && g.id != sourceGroup.id) {
                targetGroups.add(g);
            }
        }

        if (targetGroups.isEmpty()) {
            Toast.makeText(this, "没有可移动的目标分组", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] groupNames = new String[targetGroups.size()];
        for (int i = 0; i < targetGroups.size(); i++) {
            groupNames[i] = targetGroups.get(i).name;
        }

        new AlertDialog.Builder(this)
                .setTitle("选择目标分组")
                .setItems(groupNames, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        GroupInfo targetGroup = targetGroups.get(which);
                        moveAllNotesInGroup(sourceGroup.id, targetGroup.id);
                    }
                })
                .show();
    }

    /**
     * 将一个分组内所有笔记移动到另一个分组
     */
    private void moveAllNotesInGroup(long sourceGroupId, long targetGroupId) {
        Cursor cursor = mContentResolver.query(Notes.CONTENT_NOTE_URI,
                new String[]{NoteColumns.ID},
                NoteColumns.PARENT_ID + "=? AND " + NoteColumns.TYPE + "=?",
                new String[]{String.valueOf(sourceGroupId), String.valueOf(Notes.TYPE_NOTE)},
                null);

        if (cursor != null) {
            HashSet<Long> noteIds = new HashSet<>();
            while (cursor.moveToNext()) {
                noteIds.add(cursor.getLong(0));
            }
            cursor.close();

            if (!noteIds.isEmpty()) {
                DataUtils.batchMoveToFolder(mContentResolver, noteIds, targetGroupId);
                loadGroupList();
                refreshNotesList();
                Toast.makeText(this, "已移动 " + noteIds.size() + " 条笔记", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "该分组下没有笔记", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ====================== 笔记列表操作 ======================

    /**
     * 刷新笔记列表
     */
    private void refreshNotesList() {
        mNotesListAdapter.changeCursor(null);
        startAsyncNotesListQuery();
    }

    /**
     * 根据当前分组类型，异步查询对应笔记
     */
    private void startAsyncNotesListQuery() {
        String selection;
        String[] selectionArgs;

        GroupInfo currentGroup = getCurrentGroup();

        if (currentGroup != null) {
            switch (currentGroup.type) {
                case GroupInfo.TYPE_ALL:
                    selection = NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?";
                    selectionArgs = new String[]{String.valueOf(Notes.TYPE_NOTE), String.valueOf(Notes.ID_TRASH_FOLER)};
                    break;
                case GroupInfo.TYPE_FAVORITE:
                    selection = NoteColumns.TYPE + "=? AND " + NoteColumns.SNIPPET + " LIKE ? AND " + NoteColumns.PARENT_ID + "<>?";
                    selectionArgs = new String[]{String.valueOf(Notes.TYPE_NOTE), "%[FAV]%", String.valueOf(Notes.ID_TRASH_FOLER)};
                    break;
                case GroupInfo.TYPE_TRASH:
                    selection = NoteColumns.PARENT_ID + "=? AND " + NoteColumns.TYPE + "=?";
                    selectionArgs = new String[]{String.valueOf(Notes.ID_TRASH_FOLER), String.valueOf(Notes.TYPE_NOTE)};
                    break;
                default:
                    selection = NoteColumns.PARENT_ID + "=? AND " + NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?";
                    selectionArgs = new String[]{String.valueOf(mCurrentGroupId), String.valueOf(Notes.TYPE_NOTE), String.valueOf(Notes.ID_TRASH_FOLER)};
                    break;
            }
        } else {
            selection = NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?";
            selectionArgs = new String[]{String.valueOf(Notes.TYPE_NOTE), String.valueOf(Notes.ID_TRASH_FOLER)};
        }

        mBackgroundQueryHandler.startQuery(NOTES_QUERY_TOKEN, null,
                Notes.CONTENT_NOTE_URI, NoteItemData.PROJECTION,
                selection, selectionArgs,
                NoteColumns.MODIFIED_DATE + " DESC");
    }

    private GroupInfo getCurrentGroup() {
        for (GroupInfo group : mGroupList) {
            if (group.id == mCurrentGroupId) {
                return group;
            }
        }
        return null;
    }

    /**
     * 新建笔记
     */
    private void createNewNote() {
        if (mCurrentGroupId == -1 || mCurrentGroupId == Notes.ID_TRASH_FOLER) {
            Toast.makeText(this, "此页面不能新建便签", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        long folderId = (mCurrentGroupId == 0) ? Notes.ID_ROOT_FOLDER : mCurrentGroupId;
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, folderId);
        startActivityForResult(intent, REQUEST_CODE_NEW_NODE);
    }

    /**
     * 打开已有笔记
     */
    private void openNode(NoteItemData data) {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Intent.EXTRA_UID, data.getId());
        startActivityForResult(intent, REQUEST_CODE_OPEN_NODE);
    }

    /**
     * 批量删除笔记
     */
    private void batchDelete(HashSet<Long> ids) {
        boolean isInTrash = (mCurrentGroupId == Notes.ID_TRASH_FOLER);

        new AsyncTask<HashSet<Long>, Void, HashSet<AppWidgetAttribute>>() {
            @SafeVarargs
            @Override
            protected final HashSet<AppWidgetAttribute> doInBackground(HashSet<Long>... params) {
                HashSet<Long> ids = params[0];
                if (isInTrash) {
                    DataUtils.batchDeleteNotes(mContentResolver, ids);
                } else {
                    for (long noteId : ids) {
                        ContentValues values = new ContentValues();

                        long originalFolderId = 0;
                        Cursor c = mContentResolver.query(Notes.CONTENT_NOTE_URI,
                                new String[]{NoteColumns.PARENT_ID},
                                NoteColumns.ID + "=?", new String[]{String.valueOf(noteId)}, null);
                        if (c != null && c.moveToFirst()) {
                            originalFolderId = c.getLong(0);
                            c.close();
                        }

                        if (originalFolderId == 0) {
                            originalFolderId = Notes.ID_ROOT_FOLDER;
                        }

                        values.put("widget_id", originalFolderId);

                        Cursor cursor = mContentResolver.query(Notes.CONTENT_NOTE_URI,
                                new String[]{NoteColumns.SNIPPET},
                                NoteColumns.ID + "=?", new String[]{String.valueOf(noteId)}, null);
                        if (cursor != null && cursor.moveToFirst()) {
                            String snippet = cursor.getString(0);
                            if (snippet != null && snippet.contains("[FAV]")) {
                                snippet = snippet.replace("[FAV] ", "");
                                values.put(NoteColumns.SNIPPET, snippet);
                            }
                            cursor.close();
                        }

                        mContentResolver.update(Notes.CONTENT_NOTE_URI, values,
                                NoteColumns.ID + "=?", new String[]{String.valueOf(noteId)});
                    }
                    DataUtils.batchMoveToFolder(mContentResolver, ids, Notes.ID_TRASH_FOLER);
                }
                return null;
            }

            @Override
            protected void onPostExecute(HashSet<AppWidgetAttribute> widgets) {
                refreshNotesList();
                loadGroupList();
                if (mModeCallBack != null) {
                    mModeCallBack.finishActionMode();
                }
                Toast.makeText(NotesListActivity.this,
                        isInTrash ? "已永久删除" : "已移至回收站",
                        Toast.LENGTH_SHORT).show();
            }
        }.execute(ids);
    }

    /**
     * 批量收藏/取消收藏
     */
    private void toggleFavoriteBatch(HashSet<Long> ids, boolean favorite) {
        ContentValues values = new ContentValues();
        for (long noteId : ids) {
            Cursor cursor = mContentResolver.query(Notes.CONTENT_NOTE_URI,
                    new String[]{NoteColumns.SNIPPET},
                    NoteColumns.ID + "=?", new String[]{String.valueOf(noteId)}, null);

            if (cursor != null && cursor.moveToFirst()) {
                String snippet = cursor.getString(0);
                if (snippet == null) snippet = "";

                if (favorite) {
                    if (!snippet.contains("[FAV]")) {
                        snippet = "[FAV] " + snippet;
                    }
                } else {
                    snippet = snippet.replace("[FAV] ", "");
                }

                values.put(NoteColumns.SNIPPET, snippet);
                mContentResolver.update(Notes.CONTENT_NOTE_URI, values,
                        NoteColumns.ID + "=?", new String[]{String.valueOf(noteId)});
                cursor.close();
            }
        }

        refreshNotesList();
        loadGroupList();
        Toast.makeText(this, favorite ? "已收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
    }

    /**
     * 从回收站恢复笔记到原始分组
     */
    private void restoreNotesToOriginalFolder(HashSet<Long> ids) {
        int successCount = 0;
        for (long noteId : ids) {
            Cursor cursor = mContentResolver.query(Notes.CONTENT_NOTE_URI,
                    new String[]{"widget_id"},
                    NoteColumns.ID + "=?", new String[]{String.valueOf(noteId)}, null);

            if (cursor != null && cursor.moveToFirst()) {
                long originalFolderId = cursor.getLong(0);
                cursor.close();

                long targetFolderId = Notes.ID_ROOT_FOLDER;
                if (originalFolderId != 0 && originalFolderId != Notes.ID_TRASH_FOLER) {
                    targetFolderId = originalFolderId;
                }

                HashSet<Long> singleId = new HashSet<>();
                singleId.add(noteId);
                DataUtils.batchMoveToFolder(mContentResolver, singleId, targetFolderId);

                ContentValues values = new ContentValues();
                values.put("widget_id", 0);
                mContentResolver.update(Notes.CONTENT_NOTE_URI, values,
                        NoteColumns.ID + "=?", new String[]{String.valueOf(noteId)});

                successCount++;
            }
        }

        refreshNotesList();
        loadGroupList();
        Toast.makeText(this, "已恢复 " + successCount + " 条笔记", Toast.LENGTH_SHORT).show();
    }

    /**
     * 判断单条笔记是否已收藏
     */
    private boolean isNoteFavorite(long noteId) {
        Cursor cursor = mContentResolver.query(Notes.CONTENT_NOTE_URI,
                new String[]{NoteColumns.SNIPPET},
                NoteColumns.ID + "=?", new String[]{String.valueOf(noteId)}, null);
        if (cursor != null && cursor.moveToFirst()) {
            String snippet = cursor.getString(0);
            cursor.close();
            return snippet != null && snippet.contains("[FAV]");
        }
        return false;
    }

    // ====================== 搜索功能 ======================

    /**
     * 处理搜索Intent
     */
    private void handleSearchIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(query)) {
                mCurrentSearchQuery = query;
                mIsSearchMode = true;
                performSearch(query);
            }
        }
    }

    /**
     * 执行搜索
     */
    private void performSearch(String query) {
        NotesListItem.setSearchKeyword(query);

        String likePattern = "%" + query + "%";
        String selection = NoteColumns.TYPE + " = ? AND (" +
                NoteColumns.SNIPPET + " LIKE ? OR " +
                NoteColumns.ID + " IN (SELECT " + DataColumns.NOTE_ID +
                " FROM data WHERE " + DataColumns.CONTENT + " LIKE ?))";

        String[] selectionArgs = new String[]{
                String.valueOf(Notes.TYPE_NOTE),
                likePattern,
                likePattern
        };

        mBackgroundQueryHandler.startQuery(NOTES_QUERY_TOKEN, null,
                Notes.CONTENT_NOTE_URI, NoteItemData.PROJECTION,
                selection, selectionArgs,
                NoteColumns.MODIFIED_DATE + " DESC");

        mTitleBar.setVisibility(View.VISIBLE);
        mTitleBar.setText(getString(R.string.searching));
        mTitleBar.setClickable(false);
        mTitleBar.setEnabled(false);
        mAddNewNote.setVisibility(View.GONE);
        mNotesListAdapter.notifyDataSetChanged();
    }

    /**
     * 清除搜索状态
     */
    private void clearSearch() {
        mCurrentSearchQuery = null;
        mIsSearchMode = false;
        NotesListItem.setSearchKeyword("");
        refreshNotesList();

        mTitleBar.setVisibility(View.VISIBLE);
        updateTitleBar();
        mTitleBar.setClickable(true);
        mTitleBar.setEnabled(true);
        updateNewNoteButtonVisibility();
    }

    // ====================== 点击事件 ======================

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_new_note) {
            createNewNote();
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (view instanceof NotesListItem) {
            mFocusNoteDataItem = ((NotesListItem) view).getItemData();
            if (mFocusNoteDataItem != null && mFocusNoteDataItem.getType() == Notes.TYPE_NOTE
                    && !mNotesListAdapter.isInChoiceMode()) {
                if (mNotesListView.startActionMode(mModeCallBack) != null) {
                    mNotesListView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
            }
        }
        return true;
    }

    /**
     * 笔记列表项点击监听
     */
    private class OnListItemClickListener implements OnItemClickListener {
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (view instanceof NotesListItem) {
                NoteItemData item = ((NotesListItem) view).getItemData();
                if (item == null) return;

                if (mNotesListAdapter.isInChoiceMode()) {
                    if (item.getType() == Notes.TYPE_NOTE) {
                        int actualPosition = position - mNotesListView.getHeaderViewsCount();
                        boolean newChecked = !mNotesListAdapter.isSelectedItem(actualPosition);

                        boolean isFavorite = item.getSnippet() != null && item.getSnippet().contains("[FAV]");

                        if (mModeCallBack != null && mModeCallBack.getSelectedCount() > 0) {
                            boolean existingFavorite = mModeCallBack.isAllSelectedFavorite();
                            if (isFavorite != existingFavorite) {
                                Toast.makeText(NotesListActivity.this,
                                        "不能同时选择收藏和未收藏的笔记", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }

                        mNotesListAdapter.setCheckedItem(actualPosition, newChecked);

                        if (mModeCallBack != null) {
                            if (newChecked) {
                                mModeCallBack.addSelectedId(id);
                            } else {
                                mModeCallBack.removeSelectedId(id);
                            }
                            mModeCallBack.updateTitle();
                        }
                    }
                    return;
                }

                if (item.getType() == Notes.TYPE_NOTE) {
                    openNode(item);
                }
            }
        }
    }

    /**
     * 新建按钮触摸监听（处理滑动冲突）
     */
    private class NewNoteOnTouchListener implements OnTouchListener {
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    Display display = getWindowManager().getDefaultDisplay();
                    int screenHeight = display.getHeight();
                    int newNoteViewHeight = mAddNewNote.getHeight();
                    int start = screenHeight - newNoteViewHeight;
                    int eventY = start + (int) event.getY();
                    if (event.getY() < (event.getX() * (-0.12) + 94)) {
                        View view = mNotesListView.getChildAt(mNotesListView.getChildCount() - 1
                                - mNotesListView.getFooterViewsCount());
                        if (view != null && view.getBottom() > start
                                && (view.getTop() < (start + 94))) {
                            mOriginY = (int) event.getY();
                            mDispatchY = eventY;
                            event.setLocation(event.getX(), mDispatchY);
                            mDispatch = true;
                            return mNotesListView.dispatchTouchEvent(event);
                        }
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mDispatch) {
                        mDispatchY += (int) event.getY() - mOriginY;
                        event.setLocation(event.getX(), mDispatchY);
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                    break;
                default:
                    if (mDispatch) {
                        event.setLocation(event.getX(), mDispatchY);
                        mDispatch = false;
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                    break;
            }
            return false;
        }
    }

    // ====================== 多选模式回调 ======================

    /**
     * 笔记多选操作模式回调
     */
    private class ModeCallback implements ActionMode.Callback {
        private ActionMode mActionMode;
        private final HashSet<Long> mSelectedIds = new HashSet<>();

        public void finishActionMode() {
            if (mActionMode != null) {
                mActionMode.finish();
            }
        }

        public void addSelectedId(long id) {
            mSelectedIds.add(id);
            updateTitle();
            if (mActionMode != null) {
                mActionMode.invalidate();
            }
        }

        public void removeSelectedId(long id) {
            mSelectedIds.remove(id);
            updateTitle();
            if (mActionMode != null) {
                mActionMode.invalidate();
            }
        }

        public void updateTitle() {
            if (mActionMode != null) {
                mActionMode.setTitle(getString(R.string.menu_select_title, mSelectedIds.size()));
            }
        }

        public int getSelectedCount() {
            return mSelectedIds.size();
        }

        public boolean isAllSelectedFavorite() {
            if (mSelectedIds.isEmpty()) return false;
            for (long id : mSelectedIds) {
                if (!isNoteFavorite(id)) return false;
            }
            return true;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.note_list_options, menu);
            mActionMode = mode;
            mSelectedIds.clear();
            mNotesListAdapter.setChoiceMode(true);
            mNotesListView.setLongClickable(false);
            mAddNewNote.setVisibility(View.GONE);
            updateTitle();
            mNotesListAdapter.notifyDataSetChanged();

            mTitleBar.setEnabled(false);
            mTitleBar.setClickable(false);
            mTitleBar.setFocusable(false);

            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            MenuItem moveMenu = menu.findItem(R.id.move);
            MenuItem favoriteMenu = menu.findItem(R.id.favorite);

            if (mCurrentGroupId == Notes.ID_TRASH_FOLER) {
                if (moveMenu != null) moveMenu.setVisible(false);
                if (favoriteMenu != null) {
                    favoriteMenu.setTitle("恢复");
                    favoriteMenu.setVisible(true);
                }
            } else if (mCurrentGroupId == -1) {
                if (moveMenu != null) moveMenu.setVisible(false);
                if (favoriteMenu != null) {
                    favoriteMenu.setTitle("取消收藏");
                    favoriteMenu.setVisible(true);
                }
            } else {
                if (moveMenu != null) {
                    moveMenu.setVisible(DataUtils.getUserFolderCount(mContentResolver) > 0);
                }
                if (favoriteMenu != null) {
                    boolean allFavorite = isAllSelectedFavorite();
                    favoriteMenu.setTitle(allFavorite ? "取消收藏" : "收藏");
                }
            }
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (mSelectedIds.isEmpty()) {
                Toast.makeText(NotesListActivity.this, getString(R.string.menu_select_none), Toast.LENGTH_SHORT).show();
                return true;
            }

            int itemId = item.getItemId();

            if (itemId == R.id.favorite) {
                if (mCurrentGroupId == Notes.ID_TRASH_FOLER) {
                    restoreNotesToOriginalFolder(mSelectedIds);
                } else if (mCurrentGroupId == -1) {
                    toggleFavoriteBatch(mSelectedIds, false);
                } else {
                    boolean shouldFavorite = "收藏".equals(item.getTitle());
                    toggleFavoriteBatch(mSelectedIds, shouldFavorite);
                }
                mode.finish();
                return true;
            }

            if (itemId == R.id.delete) {
                new AlertDialog.Builder(NotesListActivity.this)
                        .setTitle(getString(R.string.alert_title_delete))
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(getString(R.string.alert_message_delete_notes, mSelectedIds.size()))
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                batchDelete(mSelectedIds);
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            } else if (itemId == R.id.move) {
                long[] ids = new long[mSelectedIds.size()];
                int index = 0;
                for (long id : mSelectedIds) {
                    ids[index++] = id;
                }
                String selection = NoteColumns.TYPE + "=? AND " + NoteColumns.ID + " NOT IN (?, ?)";
                mBackgroundQueryHandler.startQuery(DEST_FOLDERS_QUERY_TOKEN, ids,
                        Notes.CONTENT_NOTE_URI,
                        new String[]{NoteColumns.ID, NoteColumns.SNIPPET},
                        selection,
                        new String[]{String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER), String.valueOf(Notes.ID_CALL_RECORD_FOLDER)},
                        NoteColumns.MODIFIED_DATE + " DESC");
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mNotesListAdapter.setChoiceMode(false);
            mNotesListView.setLongClickable(true);
            updateNewNoteButtonVisibility();

            mActionMode = null;
            mSelectedIds.clear();
            mNotesListAdapter.notifyDataSetChanged();

            mTitleBar.setEnabled(true);
            mTitleBar.setClickable(true);
            mTitleBar.setFocusable(true);
        }
    }

    // ====================== 异步查询处理器 ======================

    /**
     * 后台异步查询处理器
     */
    private final class BackgroundQueryHandler extends AsyncQueryHandler {
        public BackgroundQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            switch (token) {
                case NOTES_QUERY_TOKEN:
                    mNotesListAdapter.changeCursor(cursor);
                    if (mIsSearchMode && cursor != null) {
                        int count = cursor.getCount();
                        if (count == 0) {
                            mTitleBar.setText(getString(R.string.no_search_results, mCurrentSearchQuery));
                        } else {
                            mTitleBar.setText(getString(R.string.search_results_title_format, count, mCurrentSearchQuery));
                        }
                    }
                    break;
                case DEST_FOLDERS_QUERY_TOKEN:
                    if (cursor != null && cursor.getCount() > 0 && cookie instanceof long[]) {
                        showMoveDestinationDialog(cursor, (long[]) cookie);
                    } else if (cursor != null) {
                        cursor.close();
                    }
                    break;
            }
        }
    }

    /**
     * 显示批量移动目标文件夹选择对话框
     */
    private void showMoveDestinationDialog(Cursor cursor, final long[] selectedIds) {
        final List<GroupInfo> destGroups = new ArrayList<>();

        while (cursor.moveToNext()) {
            long id = cursor.getLong(0);
            String name = cursor.getString(1);
            destGroups.add(new GroupInfo(id, name, 0, GroupInfo.TYPE_NORMAL));
        }
        cursor.close();

        if (destGroups.isEmpty()) {
            Toast.makeText(this, "没有可移动的目标分组", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] groupNames = new String[destGroups.size()];
        for (int i = 0; i < destGroups.size(); i++) {
            groupNames[i] = destGroups.get(i).name;
        }

        new AlertDialog.Builder(this)
                .setTitle("移动到分组")
                .setItems(groupNames, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        long targetId = destGroups.get(which).id;
                        HashSet<Long> ids = new HashSet<>();
                        for (long id : selectedIds) {
                            ids.add(id);
                        }
                        DataUtils.batchMoveToFolder(mContentResolver, ids, targetId);
                        refreshNotesList();
                        loadGroupList();
                        Toast.makeText(NotesListActivity.this, "已移动 " + selectedIds.length + " 条笔记", Toast.LENGTH_SHORT).show();
                        if (mModeCallBack != null) {
                            mModeCallBack.finishActionMode();
                        }
                    }
                })
                .show();
    }

    // ====================== 选项菜单 ======================

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        getMenuInflater().inflate(R.menu.note_list, menu);
        menu.findItem(R.id.menu_sync).setTitle(
                GTaskSyncService.isSyncing() ? R.string.menu_sync_cancel : R.string.menu_sync);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_new_folder) {
            showCreateGroupDialog();
        } else if (itemId == R.id.menu_export_text) {
            exportNoteToText();
        } else if (itemId == R.id.menu_sync) {
            if (isSyncMode()) {
                if (TextUtils.equals(item.getTitle(), getString(R.string.menu_sync))) {
                    GTaskSyncService.startSync(this);
                } else {
                    GTaskSyncService.cancelSync(this);
                }
            } else {
                startPreferenceActivity();
            }
        } else if (itemId == R.id.menu_setting) {
            startPreferenceActivity();
        } else if (itemId == R.id.menu_new_note) {
            createNewNote();
        } else if (itemId == R.id.menu_search) {
            onSearchRequested();
        }
        return true;
    }

    /**
     * 新建分组对话框
     */
    private void showCreateGroupDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        final EditText etName = (EditText) view.findViewById(R.id.et_foler_name);
        etName.setHint("分组名称");

        builder.setTitle("新建分组");
        builder.setView(view);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, null);

        final Dialog dialog = builder.show();
        final Button positive = (Button) dialog.findViewById(android.R.id.button1);

        positive.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = etName.getText().toString().trim();
                if (TextUtils.isEmpty(name)) {
                    Toast.makeText(NotesListActivity.this, "分组名称不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                ContentValues values = new ContentValues();
                values.put(NoteColumns.SNIPPET, name);
                values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                mContentResolver.insert(Notes.CONTENT_NOTE_URI, values);

                dialog.dismiss();
                loadGroupList();
                refreshNotesList();
                Toast.makeText(NotesListActivity.this, "分组创建成功", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 导出所有笔记到文本文件
     */
    private void exportNoteToText() {
        final BackupUtils backup = BackupUtils.getInstance(NotesListActivity.this);
        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground(Void... unused) {
                return backup.exportToText();
            }

            @Override
            protected void onPostExecute(Integer result) {
                if (result == BackupUtils.STATE_SD_CARD_UNMOUONTED) {
                    new AlertDialog.Builder(NotesListActivity.this)
                            .setTitle(getString(R.string.failed_sdcard_export))
                            .setMessage(getString(R.string.error_sdcard_unmounted))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                } else if (result == BackupUtils.STATE_SUCCESS) {
                    new AlertDialog.Builder(NotesListActivity.this)
                            .setTitle(getString(R.string.success_sdcard_export))
                            .setMessage(getString(R.string.format_exported_file_location,
                                    backup.getExportedTextFileName(), backup.getExportedTextFileDir()))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                } else if (result == BackupUtils.STATE_SYSTEM_ERROR) {
                    new AlertDialog.Builder(NotesListActivity.this)
                            .setTitle(getString(R.string.failed_sdcard_export))
                            .setMessage(getString(R.string.error_sdcard_export))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            }
        }.execute();
    }

    /**
     * 判断是否已配置同步账号
     */
    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    /**
     * 打开设置页面
     */
    private void startPreferenceActivity() {
        Activity from = getParent() != null ? getParent() : this;
        Intent intent = new Intent(from, NotesPreferenceActivity.class);
        from.startActivityIfNeeded(intent, -1);
    }

    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null, false);
        return true;
    }
}