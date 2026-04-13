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
 * 笔记数据提供者
 *
 * 【类功能】
 * 1. 封装数据库操作，对外提供统一的数据访问接口
 * 2. 支持note表和data表的增删改查
 * 3. 支持系统搜索（SearchManager）
 * 4. 自动维护版本号（用于同步）
 *
 * 【类间关系】
 * - 继承ContentProvider：Android四大组件之一
 * - 调用NotesDatabaseHelper：获取数据库实例
 * - 被所有Activity/Service调用：通过ContentResolver访问
 * - URI定义来自Notes类
 */
public class NotesProvider extends ContentProvider {

    private static final UriMatcher mMatcher;      // URI匹配器
    private NotesDatabaseHelper mHelper;           // 数据库帮助类
    private static final String TAG = "NotesProvider";

    // ==================== URI匹配类型 ====================
    private static final int URI_NOTE            = 1;   // /note
    private static final int URI_NOTE_ITEM       = 2;   // /note/#
    private static final int URI_DATA            = 3;   // /data
    private static final int URI_DATA_ITEM       = 4;   // /data/#
    private static final int URI_SEARCH          = 5;   // /search
    private static final int URI_SEARCH_SUGGEST  = 6;   // 搜索建议

    /**
     * 静态代码块：初始化URI匹配规则
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
     * 搜索结果投影列
     * 定义系统搜索返回的数据格式
     */
    private static final String NOTES_SEARCH_PROJECTION =
            NoteColumns.ID + ","
                    + NoteColumns.ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA + ","
                    + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + ","
                    + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2 + ","
                    + R.drawable.search_result + " AS " + SearchManager.SUGGEST_COLUMN_ICON_1 + ","
                    + "'" + Intent.ACTION_VIEW + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_ACTION + ","
                    + "'" + Notes.TextNote.CONTENT_TYPE + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA;

    /**
     * 笔记搜索SQL语句
     * 模糊匹配snippet，排除回收站
     */
    private static String NOTES_SNIPPET_SEARCH_QUERY =
            "SELECT " + NOTES_SEARCH_PROJECTION
                    + " FROM " + TABLE.NOTE
                    + " WHERE " + NoteColumns.SNIPPET + " LIKE ?"
                    + " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER
                    + " AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE;

    // ====================== ContentProvider生命周期 ======================

    @Override
    public boolean onCreate() {
        mHelper = NotesDatabaseHelper.getInstance(getContext());
        return true;
    }

    // ====================== 查询 ======================

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        Cursor c = null;
        SQLiteDatabase db = mHelper.getReadableDatabase();
        String id = null;

        switch (mMatcher.match(uri)) {
            case URI_NOTE:      // 查询所有笔记/文件夹
                c = db.query(TABLE.NOTE, projection, selection, selectionArgs, null, null, sortOrder);
                break;

            case URI_NOTE_ITEM: // 查询单条笔记
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.NOTE, projection,
                        NoteColumns.ID + "=" + id + parseSelection(selection),
                        selectionArgs, null, null, sortOrder);
                break;

            case URI_DATA:      // 查询所有数据
                c = db.query(TABLE.DATA, projection, selection, selectionArgs, null, null, sortOrder);
                break;

            case URI_DATA_ITEM: // 查询单条数据
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.DATA, projection,
                        DataColumns.ID + "=" + id + parseSelection(selection),
                        selectionArgs, null, null, sortOrder);
                break;

            case URI_SEARCH:    // 搜索笔记
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

    // ====================== 插入 ======================

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

        // 发送数据变化通知
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

    // ====================== 删除 ======================

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
                if (noteId <= 0) {  // 系统文件夹不能删除
                    break;
                }
                count = db.delete(TABLE.NOTE,
                        NoteColumns.ID + "=" + id + parseSelection(selection),
                        selectionArgs);
                break;

            case URI_DATA:
                count = db.delete(TABLE.DATA, selection, selectionArgs);
                deleteData = true;
                break;

            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.delete(TABLE.DATA,
                        DataColumns.ID + "=" + id + parseSelection(selection),
                        selectionArgs);
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

    // ====================== 更新 ======================

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean updateData = false;

        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                increaseNoteVersion(-1, selection, selectionArgs);  // 版本号+1
                count = db.update(TABLE.NOTE, values, selection, selectionArgs);
                break;

            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                increaseNoteVersion(Long.valueOf(id), selection, selectionArgs);
                count = db.update(TABLE.NOTE, values,
                        NoteColumns.ID + "=" + id + parseSelection(selection),
                        selectionArgs);
                break;

            case URI_DATA:
                count = db.update(TABLE.DATA, values, selection, selectionArgs);
                updateData = true;
                break;

            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.update(TABLE.DATA, values,
                        DataColumns.ID + "=" + id + parseSelection(selection),
                        selectionArgs);
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

    // ====================== 辅助方法 ======================

    /**
     * 拼接查询条件
     * @param selection 原有条件
     * @return 带AND的新条件
     */
    private String parseSelection(String selection) {
        return (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : "");
    }

    /**
     * 增加笔记版本号（用于同步）
     * 每次更新笔记时自动将version字段+1
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

    @Override
    public String getType(Uri uri) {
        return null;
    }
}