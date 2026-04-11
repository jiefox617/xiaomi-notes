/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.data;


import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;

/**
 * 类名：NotesProvider
 * 从名字理解：笔记数据提供者
 * 继承：ContentProvider（Android 四大组件之一）
 * 功能：对外提供 增删改查 统一接口，封装数据库操作
 * 全局唯一数据访问入口
 */
public class NotesProvider extends ContentProvider {

    /**
     * mMatcher
     * 从名字理解：URI 匹配器
     * 系统类：UriMatcher
     * 作用：根据传入的 URI 区分要操作的表/数据项
     */
    private static final UriMatcher mMatcher;

    /**
     * mHelper
     * 从名字理解：数据库帮助类
     * 类型：NotesDatabaseHelper
     * 作用：获取数据库实例，管理表结构
     */
    private NotesDatabaseHelper mHelper;

    private static final String TAG = "NotesProvider";

    // ==================== URI 匹配类型 ====================
    /**
     * URI_NOTE：匹配 /note → 整个笔记表
     * URI_NOTE_ITEM：匹配 /note/# → 单条笔记
     * URI_DATA：匹配 /data → 整个内容表
     * URI_DATA_ITEM：匹配 /data/# → 单条内容
     * URI_SEARCH：搜索
     * URI_SEARCH_SUGGEST：搜索建议
     */
    private static final int URI_NOTE            = 1;
    private static final int URI_NOTE_ITEM       = 2;
    private static final int URI_DATA            = 3;
    private static final int URI_DATA_ITEM       = 4;
    private static final int URI_SEARCH          = 5;
    private static final int URI_SEARCH_SUGGEST  = 6;

    /**
     * 静态代码块
     * 作用：初始化 URI 匹配规则
     * Authority 来自 Notes.AUTHORITY
     */
    static {
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mMatcher.addURI(Notes.AUTHORITY, "note", URI_NOTE);
        mMatcher.addURI(Notes.AUTHORITY, "note/#", URI_NOTE_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "data", URI_DATA);
        mMatcher.addURI(Notes.AUTHORITY, "data/#", URI_DATA_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "search", URI_SEARCH);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, URI_SEARCH_SUGGEST);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", URI_SEARCH_SUGGEST);
    }

    /**
     * NOTES_SEARCH_PROJECTION
     * 从名字理解：搜索结果投影列
     * 作用：定义系统搜索展示的字段
     * 依赖：Android SearchManager
     */
    private static final String NOTES_SEARCH_PROJECTION = NoteColumns.ID + ","
            + NoteColumns.ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA + ","
            + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + ","
            + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2 + ","
            + R.drawable.search_result + " AS " + SearchManager.SUGGEST_COLUMN_ICON_1 + ","
            + "'" + Intent.ACTION_VIEW + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_ACTION + ","
            + "'" + Notes.TextNote.CONTENT_TYPE + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA;

    /**
     * NOTES_SNIPPET_SEARCH_QUERY
     * 从名字理解：笔记搜索语句
     * 功能：模糊查询 snippet，排除回收站
     */
    private static String NOTES_SNIPPET_SEARCH_QUERY = "SELECT " + NOTES_SEARCH_PROJECTION
            + " FROM " + TABLE.NOTE
            + " WHERE " + NoteColumns.SNIPPET + " LIKE ?"
            + " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER
            + " AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE;

    /**
     * onCreate
     * 系统回调
     * 作用：初始化数据库帮助类单例
     */
    @Override
    public boolean onCreate() {
        mHelper = NotesDatabaseHelper.getInstance(getContext());
        return true;
    }

    /**
     * query
     * 从名字理解：查询数据
     * 重写 ContentProvider
     * 功能：根据 URI 查询 note / data / search
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        Cursor c = null;
        SQLiteDatabase db = mHelper.getReadableDatabase();
        String id = null;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                c = db.query(TABLE.NOTE, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.NOTE, projection, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            case URI_DATA:
                c = db.query(TABLE.DATA, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.DATA, projection, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            case URI_SEARCH:
            case URI_SEARCH_SUGGEST:
                String searchString = null;
                if (mMatcher.match(uri) == URI_SEARCH_SUGGEST) {
                    if (uri.getPathSegments().size() > 1) {
                        searchString = uri.getPathSegments().get(1);
                    }
                } else {
                    searchString = uri.getQueryParameter("pattern");
                }
                if (TextUtils.isEmpty(searchString)) {
                    return null;
                }
                searchString = String.format("%%%s%%", searchString);
                c = db.rawQuery(NOTES_SNIPPET_SEARCH_QUERY, new String[] { searchString });
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    /**
     * insert
     * 从名字理解：插入数据
     * 功能：向 note 或 data 表插入数据
     * 完成后发送通知更新
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mHelper.getWritableDatabase();
        long dataId = 0, noteId = 0, insertedId = 0;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                insertedId = noteId = db.insert(TABLE.NOTE, null, values);
                break;
            case URI_DATA:
                insertedId = dataId = db.insert(TABLE.DATA, null, values);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        if (noteId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), null);
        }
        if (dataId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId), null);
        }
        return ContentUris.withAppendedId(uri, insertedId);
    }

    /**
     * delete
     * 从名字理解：删除数据
     * 保护系统ID（≤0）不被删除
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean deleteData = false;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                count = db.delete(TABLE.NOTE, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                long noteId = Long.valueOf(id);
                if (noteId <= 0) {
                    break;
                }
                count = db.delete(TABLE.NOTE, NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                count = db.delete(TABLE.DATA, selection, selectionArgs);
                deleteData = true;
                break;
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.delete(TABLE.DATA, DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                deleteData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        if (count > 0) {
            if (deleteData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * update
     * 从名字理解：更新数据
     * 自动更新版本号（用于同步）
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean updateData = false;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                increaseNoteVersion(-1, selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                increaseNoteVersion(Long.valueOf(id), selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                count = db.update(TABLE.DATA, values, selection, selectionArgs);
                updateData = true;
                break;
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.update(TABLE.DATA, values, DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                updateData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        if (count > 0) {
            if (updateData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * parseSelection
     * 从名字理解：拼接查询条件
     * 作用：安全拼接 SQL 条件
     */
    private String parseSelection(String selection) {
        return (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : "");
    }

    /**
     * increaseNoteVersion
     * 从名字理解：增加笔记版本
     * 功能：同步机制使用，每次更新自动+1
     */
    private void increaseNoteVersion(long id, String selection, String[] selectionArgs) {
        StringBuilder sql = new StringBuilder(120);
        sql.append("UPDATE ");
        sql.append(TABLE.NOTE);
        sql.append(" SET ");
        sql.append(NoteColumns.VERSION);
        sql.append("=" + NoteColumns.VERSION + "+1 ");

        if (id > 0 || !TextUtils.isEmpty(selection)) {
            sql.append(" WHERE ");
        }
        if (id > 0) {
            sql.append(NoteColumns.ID + "=" + String.valueOf(id));
        }
        if (!TextUtils.isEmpty(selection)) {
            String selectString = id > 0 ? parseSelection(selection) : selection;
            for (String args : selectionArgs) {
                selectString = selectString.replaceFirst("\\?", args);
            }
            sql.append(selectString);
        }
        mHelper.getWritableDatabase().execSQL(sql.toString());
    }

    /**
     * getType
     * 未实现
     */
    @Override
    public String getType(Uri uri) {
        return null;
    }

}