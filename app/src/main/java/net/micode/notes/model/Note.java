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

package net.micode.notes.model;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

import java.util.ArrayList;

/**
 * 笔记数据模型（Model 层核心类）
 * 功能：
 * 1. 封装笔记的基础信息（标题、时间、类型、文件夹等）
 * 2. 封装笔记的详细数据（普通文本、通话记录）
 * 3. 统一管理本地修改状态
 * 4. 提供原子化的数据库同步方法（更新/插入）
 */
public class Note {
    // 笔记基础信息（note 表）的变更缓存
    private ContentValues mNoteDiffValues;
    // 笔记详细数据（data 表）的管理对象（文本/通话）
    private NoteData mNoteData;
    private static final String TAG = "Note";

    /**
     * 创建一条新笔记并返回笔记 ID
     * 仅在 notes 表中插入基础信息，不包含详细内容
     * @param context 上下文
     * @param folderId 父文件夹 ID
     * @return 新笔记的 ID
     */
    public static synchronized long getNewNoteId(Context context, long folderId) {
        ContentValues values = new ContentValues();
        long createdTime = System.currentTimeMillis();
        values.put(NoteColumns.CREATED_DATE, createdTime);
        values.put(NoteColumns.MODIFIED_DATE, createdTime);
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
        values.put(NoteColumns.LOCAL_MODIFIED, 1); // 标记本地已修改
        values.put(NoteColumns.PARENT_ID, folderId);

        // 插入数据库并获取返回的 URI
        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);

        long noteId = 0;
        try {
            // 从 URI 中解析出笔记 ID
            noteId = Long.valueOf(uri.getPathSegments().get(1));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Get note id error :" + e.toString());
            noteId = 0;
        }
        if (noteId == -1) {
            throw new IllegalStateException("Wrong note id:" + noteId);
        }
        return noteId;
    }

    /**
     * 构造方法：初始化基础信息和详细数据对象
     */
    public Note() {
        mNoteDiffValues = new ContentValues();
        mNoteData = new NoteData();
    }

    /**
     * 设置笔记基础信息字段
     * 自动更新修改时间和本地修改标记
     * @param key 字段名
     * @param value 字段值
     */
    public void setNoteValue(String key, String value) {
        mNoteDiffValues.put(key, value);
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
    }

    /**
     * 设置普通文本笔记的数据
     * @param key 键名
     * @param value 内容
     */
    public void setTextData(String key, String value) {
        mNoteData.setTextData(key, value);
    }

    /**
     * 设置文本数据对应的 data 表 ID
     * @param id data 表中的记录 ID
     */
    public void setTextDataId(long id) {
        mNoteData.setTextDataId(id);
    }

    /**
     * 获取文本数据 ID
     */
    public long getTextDataId() {
        return mNoteData.mTextDataId;
    }

    /**
     * 设置通话记录数据 ID
     * @param id data 表中的记录 ID
     */
    public void setCallDataId(long id) {
        mNoteData.setCallDataId(id);
    }

    /**
     * 设置通话笔记数据
     * @param key 键名
     * @param value 内容
     */
    public void setCallData(String key, String value) {
        mNoteData.setCallData(key, value);
    }

    /**
     * 判断当前笔记是否有未同步的本地修改
     * @return true 表示有修改，false 表示无修改
     */
    public boolean isLocalModified() {
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
    }

    /**
     * 将内存中的笔记变更同步到数据库
     * 先同步基础信息，再同步详细数据
     * @param context 上下文
     * @param noteId 笔记 ID
     * @return 同步是否成功
     */
    public boolean syncNote(Context context, long noteId) {
        if (noteId <= 0) {
            throw new IllegalArgumentException("Wrong note id:" + noteId);
        }

        // 无修改则直接返回
        if (!isLocalModified()) {
            return true;
        }

        // 更新笔记基础信息到 note 表
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), mNoteDiffValues, null,
                null) == 0) {
            Log.e(TAG, "Update note error, should not happen");
        }
        // 清空已同步的变更
        mNoteDiffValues.clear();

        // 同步详细数据（文本/通话）到 data 表
        if (mNoteData.isLocalModified()
                && (mNoteData.pushIntoContentResolver(context, noteId) == null)) {
            return false;
        }

        return true;
    }

    /**
     * 笔记内部数据类
     * 专门管理 data 表中的数据：文本笔记、通话笔记
     * 实现数据的新增、更新、批量提交
     */
    private class NoteData {
        // 文本数据在 data 表中的 ID
        private long mTextDataId;
        // 文本数据变更缓存
        private ContentValues mTextDataValues;
        // 通话数据在 data 表中的 ID
        private long mCallDataId;
        // 通话数据变更缓存
        private ContentValues mCallDataValues;

        private static final String TAG = "NoteData";

        public NoteData() {
            mTextDataValues = new ContentValues();
            mCallDataValues = new ContentValues();
            mTextDataId = 0;
            mCallDataId = 0;
        }

        /**
         * 判断是否有文本或通话数据修改
         */
        boolean isLocalModified() {
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }

        /**
         * 设置文本数据 ID
         */
        void setTextDataId(long id) {
            if(id <= 0) {
                throw new IllegalArgumentException("Text data id should larger than 0");
            }
            mTextDataId = id;
        }

        /**
         * 设置通话数据 ID
         */
        void setCallDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("Call data id should larger than 0");
            }
            mCallDataId = id;
        }

        /**
         * 设置通话数据并自动标记笔记已修改
         */
        void setCallData(String key, String value) {
            mCallDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 设置文本数据并自动标记笔记已修改
         */
        void setTextData(String key, String value) {
            mTextDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 将文本/通话数据批量提交到 ContentResolver
         * 支持：新增数据、更新已有数据
         * @return 操作成功返回笔记 URI，失败返回 null
         */
        Uri pushIntoContentResolver(Context context, long noteId) {
            if (noteId <= 0) {
                throw new IllegalArgumentException("Wrong note id:" + noteId);
            }

            // 批量操作列表，保证原子性
            ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
            ContentProviderOperation.Builder builder = null;

            // -------------------- 处理文本数据 --------------------
            if(mTextDataValues.size() > 0) {
                mTextDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mTextDataId == 0) {
                    // 无 ID：插入新文本数据
                    mTextDataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mTextDataValues);
                    try {
                        setTextDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new text data fail with noteId" + noteId);
                        mTextDataValues.clear();
                        return null;
                    }
                } else {
                    // 有 ID：更新文本数据
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mTextDataId));
                    builder.withValues(mTextDataValues);
                    operationList.add(builder.build());
                }
                mTextDataValues.clear();
            }

            // -------------------- 处理通话数据 --------------------
            if(mCallDataValues.size() > 0) {
                mCallDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mCallDataId == 0) {
                    // 无 ID：插入新通话数据
                    mCallDataValues.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mCallDataValues);
                    try {
                        setCallDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new call data fail with noteId" + noteId);
                        mCallDataValues.clear();
                        return null;
                    }
                } else {
                    // 有 ID：更新通话数据
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mCallDataId));
                    builder.withValues(mCallDataValues);
                    operationList.add(builder.build());
                }
                mCallDataValues.clear();
            }

            // 批量执行数据库操作
            if (operationList.size() > 0) {
                try {
                    ContentProviderResult[] results = context.getContentResolver().applyBatch(
                            Notes.AUTHORITY, operationList);
                    return (results == null || results.length == 0 || results[0] == null) ? null
                            : ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                } catch (RemoteException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                } catch (OperationApplicationException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                }
            }
            return null;
        }
    }
}