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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;
import net.micode.notes.gtask.exception.ActionFailureException;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 类名：SqlData
 * 从名字理解：SQL 数据操作类
 * 功能：封装 本地数据库 data 表 的读写操作
 * 专门用于 Google Task 同步时操作本地笔记内容数据
 * 依赖：ContentResolver 访问 NotesProvider
 */
public class SqlData {
    /**
     * TAG
     * 从名字理解：类名简称 SqlData
     * 用于日志输出
     */
    private static final String TAG = SqlData.class.getSimpleName();

    /**
     * INVALID_ID
     * 从名字理解：无效 ID
     * 作用：标记未初始化/不存在的数据 ID
     */
    private static final int INVALID_ID = -99999;

    /**
     * PROJECTION_DATA
     * 从名字理解：查询 data 表的投影列
     * 来自 DataColumns 字段定义
     * 作用：查询时只需要这些列
     */
    public static final String[] PROJECTION_DATA = new String[] {
            DataColumns.ID, DataColumns.MIME_TYPE, DataColumns.CONTENT, DataColumns.DATA1,
            DataColumns.DATA3
    };

    /**
     * 以下为查询结果的列索引
     * 与 PROJECTION_DATA 顺序一一对应
     */
    public static final int DATA_ID_COLUMN = 0;
    public static final int DATA_MIME_TYPE_COLUMN = 1;
    public static final int DATA_CONTENT_COLUMN = 2;
    public static final int DATA_CONTENT_DATA_1_COLUMN = 3;
    public static final int DATA_CONTENT_DATA_3_COLUMN = 4;

    /**
     * mContentResolver
     * 从名字理解：内容解析器
     * 系统类：ContentResolver
     * 作用：访问 ContentProvider 进行增删改查
     */
    private ContentResolver mContentResolver;

    /**
     * mIsCreate
     * 从名字理解：是否为新建数据
     * true = 未插入数据库
     * false = 已存在数据库
     */
    private boolean mIsCreate;

    /**
     * mDataId
     * 从名字理解：data 表主键 ID
     */
    private long mDataId;

    /**
     * mDataMimeType
     * 从名字理解：数据类型
     * 例：text_note / call_note
     */
    private String mDataMimeType;

    /**
     * mDataContent
     * 从名字理解：笔记文本内容
     */
    private String mDataContent;

    /**
     * mDataContentData1
     * 从名字理解：扩展数字字段1
     * 例：清单模式、通话时间
     */
    private long mDataContentData1;

    /**
     * mDataContentData3
     * 从名字理解：扩展文本字段3
     * 例：电话号码
     */
    private String mDataContentData3;

    /**
     * mDiffDataValues
     * 从名字理解：需要更新的数据
     * 作用：只保存变化的字段，提高效率
     */
    private ContentValues mDiffDataValues;

    /**
     * 构造方法：创建新数据
     * 初始化所有字段为默认值
     */
    public SqlData(Context context) {
        mContentResolver = context.getContentResolver();
        mIsCreate = true;
        mDataId = INVALID_ID;
        mDataMimeType = DataConstants.NOTE;
        mDataContent = "";
        mDataContentData1 = 0;
        mDataContentData3 = "";
        mDiffDataValues = new ContentValues();
    }

    /**
     * 构造方法：从 Cursor 加载已有数据
     * 从数据库读取并赋值到成员变量
     */
    public SqlData(Context context, Cursor c) {
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(c);
        mDiffDataValues = new ContentValues();
    }

    /**
     * 方法名：loadFromCursor
     * 从名字理解：从游标加载数据
     * 将查询结果赋值给成员变量
     */
    private void loadFromCursor(Cursor c) {
        mDataId = c.getLong(DATA_ID_COLUMN);
        mDataMimeType = c.getString(DATA_MIME_TYPE_COLUMN);
        mDataContent = c.getString(DATA_CONTENT_COLUMN);
        mDataContentData1 = c.getLong(DATA_CONTENT_DATA_1_COLUMN);
        mDataContentData3 = c.getString(DATA_CONTENT_DATA_3_COLUMN);
    }

    /**
     * 方法名：setContent
     * 从名字理解：从 JSON 设置数据
     * 作用：将同步 JSON 数据解析到本地对象
     * 只保存变化字段到 mDiffDataValues
     */
    public void setContent(JSONObject js) throws JSONException {
        long dataId = js.has(DataColumns.ID) ? js.getLong(DataColumns.ID) : INVALID_ID;
        if (mIsCreate || mDataId != dataId) {
            mDiffDataValues.put(DataColumns.ID, dataId);
        }
        mDataId = dataId;

        String dataMimeType = js.has(DataColumns.MIME_TYPE) ? js.getString(DataColumns.MIME_TYPE)
                : DataConstants.NOTE;
        if (mIsCreate || !mDataMimeType.equals(dataMimeType)) {
            mDiffDataValues.put(DataColumns.MIME_TYPE, dataMimeType);
        }
        mDataMimeType = dataMimeType;

        String dataContent = js.has(DataColumns.CONTENT) ? js.getString(DataColumns.CONTENT) : "";
        if (mIsCreate || !mDataContent.equals(dataContent)) {
            mDiffDataValues.put(DataColumns.CONTENT, dataContent);
        }
        mDataContent = dataContent;

        long dataContentData1 = js.has(DataColumns.DATA1) ? js.getLong(DataColumns.DATA1) : 0;
        if (mIsCreate || mDataContentData1 != dataContentData1) {
            mDiffDataValues.put(DataColumns.DATA1, dataContentData1);
        }
        mDataContentData1 = dataContentData1;

        String dataContentData3 = js.has(DataColumns.DATA3) ? js.getString(DataColumns.DATA3) : "";
        if (mIsCreate || !mDataContentData3.equals(dataContentData3)) {
            mDiffDataValues.put(DataColumns.DATA3, dataContentData3);
        }
        mDataContentData3 = dataContentData3;
    }

    /**
     * 方法名：getContent
     * 从名字理解：获取数据 JSON
     * 作用：将本地对象转为同步 JSON
     */
    public JSONObject getContent() throws JSONException {
        if (mIsCreate) {
            Log.e(TAG, "it seems that we haven't created this in database yet");
            return null;
        }
        JSONObject js = new JSONObject();
        js.put(DataColumns.ID, mDataId);
        js.put(DataColumns.MIME_TYPE, mDataMimeType);
        js.put(DataColumns.CONTENT, mDataContent);
        js.put(DataColumns.DATA1, mDataContentData1);
        js.put(DataColumns.DATA3, mDataContentData3);
        return js;
    }

    /**
     * 方法名：commit
     * 从名字理解：提交数据到数据库
     * 新建 → insert
     * 更新 → update
     * 支持版本校验同步
     */
    public void commit(long noteId, boolean validateVersion, long version) {

        if (mIsCreate) {
            // 新建数据
            if (mDataId == INVALID_ID && mDiffDataValues.containsKey(DataColumns.ID)) {
                mDiffDataValues.remove(DataColumns.ID);
            }

            mDiffDataValues.put(DataColumns.NOTE_ID, noteId);
            Uri uri = mContentResolver.insert(Notes.CONTENT_DATA_URI, mDiffDataValues);
            try {
                mDataId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
        } else {
            // 更新数据
            if (mDiffDataValues.size() > 0) {
                int result = 0;
                if (!validateVersion) {
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues, null, null);
                } else {
                    result = mContentResolver.update(ContentUris.withAppendedId(
                                    Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues,
                            " ? in (SELECT " + NoteColumns.ID + " FROM " + TABLE.NOTE
                                    + " WHERE " + NoteColumns.VERSION + "=?)", new String[] {
                                    String.valueOf(noteId), String.valueOf(version)
                            });
                }
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }
        }

        // 提交后清空变更，标记为已存在
        mDiffDataValues.clear();
        mIsCreate = false;
    }

    /**
     * 方法名：getId
     * 从名字理解：获取数据 ID
     */
    public long getId() {
        return mDataId;
    }
}