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

package net.micode.notes.tool;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * 数据工具类
 *
 * 【类功能】
 * 提供笔记/文件夹的批量操作、查询、验证、格式处理等通用静态方法
 *
 * 【类间关系】
 * - 被NotesListActivity调用：批量删除/移动笔记
 * - 被NoteEditActivity调用：获取笔记摘要
 * - 被WorkingNote调用：验证笔记存在性
 */
public class DataUtils {
    public static final String TAG = "DataUtils";

    /**
     * 批量删除笔记
     */
    public static boolean batchDeleteNotes(ContentResolver resolver, HashSet<Long> ids) {
        if (ids == null || ids.size() == 0) {
            Log.d(TAG, "ids is null or empty");
            return true;
        }

        ArrayList<ContentProviderOperation> operationList = new ArrayList<>();
        for (long id : ids) {
            if (id == Notes.ID_ROOT_FOLDER) {
                Log.e(TAG, "Don't delete system folder root");
                continue;
            }
            operationList.add(ContentProviderOperation
                    .newDelete(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id))
                    .build());
        }

        try {
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);
            return results != null && results.length > 0 && results[0] != null;
        } catch (RemoteException | OperationApplicationException e) {
            Log.e(TAG, e.toString());
        }
        return false;
    }

    /**
     * 移动单条笔记
     */
    public static void moveNoteToFoler(ContentResolver resolver, long id, long srcFolderId, long desFolderId) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.PARENT_ID, desFolderId);
        values.put(NoteColumns.ORIGIN_PARENT_ID, srcFolderId);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        resolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id), values, null, null);
    }

    /**
     * 批量移动笔记
     */
    public static boolean batchMoveToFolder(ContentResolver resolver, HashSet<Long> ids, long folderId) {
        if (ids == null || ids.size() == 0) {
            Log.d(TAG, "ids is null or empty");
            return true;
        }

        ArrayList<ContentProviderOperation> operationList = new ArrayList<>();
        for (long id : ids) {
            operationList.add(ContentProviderOperation
                    .newUpdate(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id))
                    .withValue(NoteColumns.PARENT_ID, folderId)
                    .withValue(NoteColumns.LOCAL_MODIFIED, 1)
                    .build());
        }

        try {
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);
            return results != null && results.length > 0 && results[0] != null;
        } catch (RemoteException | OperationApplicationException e) {
            Log.e(TAG, e.toString());
        }
        return false;
    }

    /**
     * 获取用户创建的文件夹数量
     */
    public static int getUserFolderCount(ContentResolver resolver) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { "COUNT(*)" },
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[] { String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER) },
                null);

        int count = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
            cursor.close();
        }
        return count;
    }

    /**
     * 判断笔记是否存在且可见（不在垃圾箱）
     */
    public static boolean visibleInNoteDatabase(ContentResolver resolver, long noteId, int type) {
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null,
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER,
                new String[] { String.valueOf(type) },
                null);

        boolean exist = false;
        if (cursor != null) {
            exist = cursor.getCount() > 0;
            cursor.close();
        }
        return exist;
    }

    /**
     * 判断笔记是否存在于数据库
     */
    public static boolean existInNoteDatabase(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            exist = cursor.getCount() > 0;
            cursor.close();
        }
        return exist;
    }

    /**
     * 判断数据记录是否存在
     */
    public static boolean existInDataDatabase(ContentResolver resolver, long dataId) {
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            exist = cursor.getCount() > 0;
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查文件夹名称是否已存在
     */
    public static boolean checkVisibleFolderName(ContentResolver resolver, String name) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI, null,
                NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER +
                        " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER +
                        " AND " + NoteColumns.SNIPPET + "=?",
                new String[] { name }, null);
        boolean exist = false;
        if (cursor != null) {
            exist = cursor.getCount() > 0;
            cursor.close();
        }
        return exist;
    }

    /**
     * 获取文件夹下绑定的桌面小部件
     */
    public static HashSet<AppWidgetAttribute> getFolderNoteWidget(ContentResolver resolver, long folderId) {
        Cursor c = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { NoteColumns.WIDGET_ID, NoteColumns.WIDGET_TYPE },
                NoteColumns.PARENT_ID + "=?",
                new String[] { String.valueOf(folderId) },
                null);

        HashSet<AppWidgetAttribute> set = null;
        if (c != null) {
            if (c.moveToFirst()) {
                set = new HashSet<>();
                do {
                    AppWidgetAttribute widget = new AppWidgetAttribute();
                    widget.widgetId = c.getInt(0);
                    widget.widgetType = c.getInt(1);
                    set.add(widget);
                } while (c.moveToNext());
            }
            c.close();
        }
        return set;
    }

    /**
     * 获取通话笔记的电话号码
     */
    public static String getCallNumberByNoteId(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String[] { CallNote.PHONE_NUMBER },
                CallNote.NOTE_ID + "=? AND " + CallNote.MIME_TYPE + "=?",
                new String[] { String.valueOf(noteId), CallNote.CONTENT_ITEM_TYPE },
                null);

        if (cursor != null && cursor.moveToFirst()) {
            String number = cursor.getString(0);
            cursor.close();
            return number;
        }
        return "";
    }

    /**
     * 根据号码+通话时间查找通话笔记ID
     */
    public static long getNoteIdByPhoneNumberAndCallDate(ContentResolver resolver, String phoneNumber, long callDate) {
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String[] { CallNote.NOTE_ID },
                CallNote.CALL_DATE + "=? AND " + CallNote.MIME_TYPE + "=? AND PHONE_NUMBERS_EQUAL("
                        + CallNote.PHONE_NUMBER + ",?)",
                new String[] { String.valueOf(callDate), CallNote.CONTENT_ITEM_TYPE, phoneNumber },
                null);

        if (cursor != null && cursor.moveToFirst()) {
            long id = cursor.getLong(0);
            cursor.close();
            return id;
        }
        return 0;
    }

    /**
     * 获取笔记摘要
     */
    public static String getSnippetById(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { NoteColumns.SNIPPET },
                NoteColumns.ID + "=?",
                new String[] { String.valueOf(noteId) },
                null);

        if (cursor != null && cursor.moveToFirst()) {
            String snippet = cursor.getString(0);
            cursor.close();
            return snippet;
        }
        throw new IllegalArgumentException("Note is not found with id: " + noteId);
    }

    /**
     * 格式化笔记摘要（取第一行，去除空白）
     */
    public static String getFormattedSnippet(String snippet) {
        if (snippet != null) {
            snippet = snippet.trim();
            int index = snippet.indexOf('\n');
            if (index != -1) {
                snippet = snippet.substring(0, index);
            }
        }
        return snippet;
    }
}