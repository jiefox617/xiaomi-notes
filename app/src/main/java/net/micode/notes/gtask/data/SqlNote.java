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

package net.micode.notes.gtask.data;

import android.appwidget.AppWidgetManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;
import net.micode.notes.tool.ResourceParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * 类名：SqlNote
 * 从名字理解：SQL 笔记操作类
 * 功能：封装 本地 note 表 的读写操作
 * 专门用于 Google Task 同步时操作本地笔记/文件夹数据
 * 依赖：ContentResolver + NotesProvider
 * 组合：内部包含 SqlData 管理内容数据
 */
public class SqlNote {
    /**
     * TAG
     * 从名字理解：类名简称 SqlNote
     * 用于日志输出
     */
    private static final String TAG = SqlNote.class.getSimpleName();

    /**
     * INVALID_ID
     * 从名字理解：无效 ID
     * 作用：标记未初始化的笔记 ID
     */
    private static final int INVALID_ID = -99999;

    /**
     * PROJECTION_NOTE
     * 从名字理解：查询 note 表的投影列
     * 来自 NoteColumns 接口定义
     * 作用：同步时需要查询的全部字段
     */
    public static final String[] PROJECTION_NOTE = new String[] {
            NoteColumns.ID, NoteColumns.ALERTED_DATE, NoteColumns.BG_COLOR_ID,
            NoteColumns.CREATED_DATE, NoteColumns.HAS_ATTACHMENT, NoteColumns.MODIFIED_DATE,
            NoteColumns.NOTES_COUNT, NoteColumns.PARENT_ID, NoteColumns.SNIPPET, NoteColumns.TYPE,
            NoteColumns.WIDGET_ID, NoteColumns.WIDGET_TYPE, NoteColumns.SYNC_ID,
            NoteColumns.LOCAL_MODIFIED, NoteColumns.ORIGIN_PARENT_ID, NoteColumns.GTASK_ID,
            NoteColumns.VERSION
    };

    /**
     * 列索引
     * 与 PROJECTION_NOTE 顺序一一对应
     * 用于从 Cursor 快速取值
     */
    public static final int ID_COLUMN = 0;
    public static final int ALERTED_DATE_COLUMN = 1;
    public static final int BG_COLOR_ID_COLUMN = 2;
    public static final int CREATED_DATE_COLUMN = 3;
    public static final int HAS_ATTACHMENT_COLUMN = 4;
    public static final int MODIFIED_DATE_COLUMN = 5;
    public static final int NOTES_COUNT_COLUMN = 6;
    public static final int PARENT_ID_COLUMN = 7;
    public static final int SNIPPET_COLUMN = 8;
    public static final int TYPE_COLUMN = 9;
    public static final int WIDGET_ID_COLUMN = 10;
    public static final int WIDGET_TYPE_COLUMN = 11;
    public static final int SYNC_ID_COLUMN = 12;
    public static final int LOCAL_MODIFIED_COLUMN = 13;
    public static final int ORIGIN_PARENT_ID_COLUMN = 14;
    public static final int GTASK_ID_COLUMN = 15;
    public static final int VERSION_COLUMN = 16;

    /**
     * mContext
     * 上下文，用于获取资源、ContentResolver
     */
    private Context mContext;

    /**
     * mContentResolver
     * 内容解析器
     * 系统类：ContentResolver
     * 作用：访问 ContentProvider 操作数据库
     */
    private ContentResolver mContentResolver;

    /**
     * mIsCreate
     * 从名字理解：是否为新建笔记
     * true = 未入库，需要 insert
     * false = 已存在，需要 update
     */
    private boolean mIsCreate;

    // ==================== 笔记字段 ====================
    private long mId;                // 笔记ID
    private long mAlertDate;         // 提醒时间
    private int mBgColorId;          // 背景色
    private long mCreatedDate;       // 创建时间
    private int mHasAttachment;      // 是否有附件
    private long mModifiedDate;     // 修改时间
    private long mParentId;          // 父文件夹ID
    private String mSnippet;         // 摘要
    private int mType;               // 类型：笔记/文件夹/系统
    private int mWidgetId;           // 小部件ID
    private int mWidgetType;         // 小部件类型
    private long mOriginParent;      // 原始父文件夹
    private long mVersion;           // 版本号（同步用）

    /**
     * mDiffNoteValues
     * 从名字理解：变更的字段值
     * 作用：只保存变化的字段，提高更新效率
     */
    private ContentValues mDiffNoteValues;

    /**
     * mDataList
     * 从名字理解：SqlData 列表
     * 作用：管理笔记对应的内容数据（data 表）
     */
    private ArrayList<SqlData> mDataList;

    /**
     * 构造方法：创建新笔记
     * 初始化默认值
     */
    public SqlNote(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = true;
        mId = INVALID_ID;
        mAlertDate = 0;
        mBgColorId = ResourceParser.getDefaultBgId(context);
        mCreatedDate = System.currentTimeMillis();
        mHasAttachment = 0;
        mModifiedDate = System.currentTimeMillis();
        mParentId = 0;
        mSnippet = "";
        mType = Notes.TYPE_NOTE;
        mWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;
        mOriginParent = 0;
        mVersion = 0;
        mDiffNoteValues = new ContentValues();
        mDataList = new ArrayList<SqlData>();
    }

    /**
     * 构造方法：从 Cursor 加载已有笔记
     */
    public SqlNote(Context context, Cursor c) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(c);
        mDataList = new ArrayList<SqlData>();
        if (mType == Notes.TYPE_NOTE)
            loadDataContent();
        mDiffNoteValues = new ContentValues();
    }

    /**
     * 构造方法：通过 ID 加载笔记
     */
    public SqlNote(Context context, long id) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(id);
        mDataList = new ArrayList<SqlData>();
        if (mType == Notes.TYPE_NOTE)
            loadDataContent();
        mDiffNoteValues = new ContentValues();
    }

    /**
     * 方法名：loadFromCursor(long id)
     * 从名字理解：通过 ID 查询并加载笔记
     */
    private void loadFromCursor(long id) {
        Cursor c = null;
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, PROJECTION_NOTE, "(_id=?)",
                    new String[] { String.valueOf(id) }, null);
            if (c != null) {
                c.moveToNext();
                loadFromCursor(c);
            }
        } finally {
            if (c != null) c.close();
        }
    }

    /**
     * 方法名：loadFromCursor(Cursor c)
     * 从名字理解：从游标赋值到成员变量
     */
    private void loadFromCursor(Cursor c) {
        mId = c.getLong(ID_COLUMN);
        mAlertDate = c.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = c.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = c.getLong(CREATED_DATE_COLUMN);
        mHasAttachment = c.getInt(HAS_ATTACHMENT_COLUMN);
        mModifiedDate = c.getLong(MODIFIED_DATE_COLUMN);
        mParentId = c.getLong(PARENT_ID_COLUMN);
        mSnippet = c.getString(SNIPPET_COLUMN);
        mType = c.getInt(TYPE_COLUMN);
        mWidgetId = c.getInt(WIDGET_ID_COLUMN);
        mWidgetType = c.getInt(WIDGET_TYPE_COLUMN);
        mVersion = c.getLong(VERSION_COLUMN);
    }

    /**
     * 方法名：loadDataContent
     * 从名字理解：加载笔记对应的内容数据
     * 查询 data 表并封装成 SqlData
     */
    private void loadDataContent() {
        Cursor c = null;
        mDataList.clear();
        try {
            c = mContentResolver.query(Notes.CONTENT_DATA_URI, SqlData.PROJECTION_DATA,
                    "(note_id=?)", new String[] { String.valueOf(mId) }, null);
            if (c != null) {
                while (c.moveToNext()) {
                    SqlData data = new SqlData(mContext, c);
                    mDataList.add(data);
                }
            }
        } finally {
            if (c != null) c.close();
        }
    }

    /**
     * 方法名：setContent
     * 从名字理解：从 JSON 设置笔记内容
     * 作用：将同步 JSON 解析成本地对象
     * 支持：文件夹、普通笔记
     */
    public boolean setContent(JSONObject js) {
        try {
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                Log.w(TAG, "cannot set system folder");
            } else if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                // 文件夹只更新摘要和类型
                String snippet = note.has(NoteColumns.SNIPPET) ? note.getString(NoteColumns.SNIPPET) : "";
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);
                }
                mSnippet = snippet;

                int type = note.has(NoteColumns.TYPE) ? note.getInt(NoteColumns.TYPE) : Notes.TYPE_NOTE;
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);
                }
                mType = type;
            } else if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_NOTE) {
                JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
                // 解析所有基础字段
                long id = note.has(NoteColumns.ID) ? note.getLong(NoteColumns.ID) : INVALID_ID;
                if (mIsCreate || mId != id) {
                    mDiffNoteValues.put(NoteColumns.ID, id);
                }
                mId = id;

                long alertDate = note.has(NoteColumns.ALERTED_DATE) ? note.getLong(NoteColumns.ALERTED_DATE) : 0;
                if (mIsCreate || mAlertDate != alertDate) {
                    mDiffNoteValues.put(NoteColumns.ALERTED_DATE, alertDate);
                }
                mAlertDate = alertDate;

                int bgColorId = note.has(NoteColumns.BG_COLOR_ID) ? note.getInt(NoteColumns.BG_COLOR_ID) : ResourceParser.getDefaultBgId(mContext);
                if (mIsCreate || mBgColorId != bgColorId) {
                    mDiffNoteValues.put(NoteColumns.BG_COLOR_ID, bgColorId);
                }
                mBgColorId = bgColorId;

                long createDate = note.has(NoteColumns.CREATED_DATE) ? note.getLong(NoteColumns.CREATED_DATE) : System.currentTimeMillis();
                if (mIsCreate || mCreatedDate != createDate) {
                    mDiffNoteValues.put(NoteColumns.CREATED_DATE, createDate);
                }
                mCreatedDate = createDate;

                int hasAttachment = note.has(NoteColumns.HAS_ATTACHMENT) ? note.getInt(NoteColumns.HAS_ATTACHMENT) : 0;
                if (mIsCreate || mHasAttachment != hasAttachment) {
                    mDiffNoteValues.put(NoteColumns.HAS_ATTACHMENT, hasAttachment);
                }
                mHasAttachment = hasAttachment;

                long modifiedDate = note.has(NoteColumns.MODIFIED_DATE) ? note.getLong(NoteColumns.MODIFIED_DATE) : System.currentTimeMillis();
                if (mIsCreate || mModifiedDate != modifiedDate) {
                    mDiffNoteValues.put(NoteColumns.MODIFIED_DATE, modifiedDate);
                }
                mModifiedDate = modifiedDate;

                long parentId = note.has(NoteColumns.PARENT_ID) ? note.getLong(NoteColumns.PARENT_ID) : 0;
                if (mIsCreate || mParentId != parentId) {
                    mDiffNoteValues.put(NoteColumns.PARENT_ID, parentId);
                }
                mParentId = parentId;

                String snippet = note.has(NoteColumns.SNIPPET) ? note.getString(NoteColumns.SNIPPET) : "";
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);
                }
                mSnippet = snippet;

                int type = note.has(NoteColumns.TYPE) ? note.getInt(NoteColumns.TYPE) : Notes.TYPE_NOTE;
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);
                }
                mType = type;

                int widgetId = note.has(NoteColumns.WIDGET_ID) ? note.getInt(NoteColumns.WIDGET_ID) : AppWidgetManager.INVALID_APPWIDGET_ID;
                if (mIsCreate || mWidgetId != widgetId) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_ID, widgetId);
                }
                mWidgetId = widgetId;

                int widgetType = note.has(NoteColumns.WIDGET_TYPE) ? note.getInt(NoteColumns.WIDGET_TYPE) : Notes.TYPE_WIDGET_INVALIDE;
                if (mIsCreate || mWidgetType != widgetType) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_TYPE, widgetType);
                }
                mWidgetType = widgetType;

                long originParent = note.has(NoteColumns.ORIGIN_PARENT_ID) ? note.getLong(NoteColumns.ORIGIN_PARENT_ID) : 0;
                if (mIsCreate || mOriginParent != originParent) {
                    mDiffNoteValues.put(NoteColumns.ORIGIN_PARENT_ID, originParent);
                }
                mOriginParent = originParent;

                // 解析内容数据
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    SqlData sqlData = null;
                    if (data.has(DataColumns.ID)) {
                        long dataId = data.getLong(DataColumns.ID);
                        for (SqlData temp : mDataList) {
                            if (dataId == temp.getId()) {
                                sqlData = temp;
                            }
                        }
                    }
                    if (sqlData == null) {
                        sqlData = new SqlData(mContext);
                        mDataList.add(sqlData);
                    }
                    sqlData.setContent(data);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            return false;
        }
        return true;
    }

    /**
     * 方法名：getContent
     * 从名字理解：转为同步 JSON
     * 作用：将本地笔记转为 JSON 用于上传
     */
    public JSONObject getContent() {
        try {
            JSONObject js = new JSONObject();
            JSONObject note = new JSONObject();

            if (mType == Notes.TYPE_NOTE) {
                note.put(NoteColumns.ID, mId);
                note.put(NoteColumns.ALERTED_DATE, mAlertDate);
                note.put(NoteColumns.BG_COLOR_ID, mBgColorId);
                note.put(NoteColumns.CREATED_DATE, mCreatedDate);
                note.put(NoteColumns.HAS_ATTACHMENT, mHasAttachment);
                note.put(NoteColumns.MODIFIED_DATE, mModifiedDate);
                note.put(NoteColumns.PARENT_ID, mParentId);
                note.put(NoteColumns.SNIPPET, mSnippet);
                note.put(NoteColumns.TYPE, mType);
                note.put(NoteColumns.WIDGET_ID, mWidgetId);
                note.put(NoteColumns.WIDGET_TYPE, mWidgetType);
                note.put(NoteColumns.ORIGIN_PARENT_ID, mOriginParent);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);

                JSONArray dataArray = new JSONArray();
                for (SqlData sqlData : mDataList) {
                    dataArray.put(sqlData.getContent());
                }
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);
            } else if (mType == Notes.TYPE_FOLDER || mType == Notes.TYPE_SYSTEM) {
                note.put(NoteColumns.ID, mId);
                note.put(NoteColumns.TYPE, mType);
                note.put(NoteColumns.SNIPPET, mSnippet);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
            }
            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }
        return null;
    }

    // ==================== SET 方法 ====================
    public void setParentId(long id) {
        mParentId = id;
        mDiffNoteValues.put(NoteColumns.PARENT_ID, id);
    }

    public void setGtaskId(String gid) {
        mDiffNoteValues.put(NoteColumns.GTASK_ID, gid);
    }

    public void setSyncId(long syncId) {
        mDiffNoteValues.put(NoteColumns.SYNC_ID, syncId);
    }

    public void resetLocalModified() {
        mDiffNoteValues.put(NoteColumns.LOCAL_MODIFIED, 0);
    }

    // ==================== GET 方法 ====================
    public long getId() {
        return mId;
    }

    public long getParentId() {
        return mParentId;
    }

    public String getSnippet() {
        return mSnippet;
    }

    public boolean isNoteType() {
        return mType == Notes.TYPE_NOTE;
    }

    /**
     * 方法名：commit
     * 从名字理解：提交变更到数据库
     * 新建 → insert
     * 更新 → update（支持版本校验）
     * 同时提交子项 SqlData
     */
    public void commit(boolean validateVersion) {
        if (mIsCreate) {
            // 新建笔记
            if (mId == INVALID_ID && mDiffNoteValues.containsKey(NoteColumns.ID)) {
                mDiffNoteValues.remove(NoteColumns.ID);
            }

            Uri uri = mContentResolver.insert(Notes.CONTENT_NOTE_URI, mDiffNoteValues);
            mId = Long.valueOf(uri.getPathSegments().get(1));

            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, false, -1);
                }
            }
        } else {
            // 更新笔记
            if (mDiffNoteValues.size() > 0) {
                mVersion++;
                int result = 0;
                if (!validateVersion) {
                    result = mContentResolver.update(Notes.CONTENT_NOTE_URI, mDiffNoteValues,
                            "(" + NoteColumns.ID + "=?)", new String[] { String.valueOf(mId) });
                } else {
                    result = mContentResolver.update(Notes.CONTENT_NOTE_URI, mDiffNoteValues,
                            "(" + NoteColumns.ID + "=?) AND (" + NoteColumns.VERSION + "<=?)",
                            new String[] { String.valueOf(mId), String.valueOf(mVersion) });
                }
            }

            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, validateVersion, mVersion);
                }
            }
        }

        // 重新加载最新数据
        loadFromCursor(mId);
        if (mType == Notes.TYPE_NOTE)
            loadDataContent();

        mDiffNoteValues.clear();
        mIsCreate = false;
    }
}