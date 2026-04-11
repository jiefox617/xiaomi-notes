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

package net.micode.notes.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 类名：NotesDatabaseHelper
 * 从名字理解：笔记数据库帮助类
 * 继承：SQLiteOpenHelper（Android 系统数据库管理类）
 * 功能：创建、升级数据库，管理表结构与触发器
 */
public class NotesDatabaseHelper extends SQLiteOpenHelper {

    /**
     * DB_NAME
     * 从名字理解：数据库文件名
     * 作用：app 内部存储的 note.db 数据库文件
     */
    private static final String DB_NAME = "note.db";

    /**
     * DB_VERSION
     * 从名字理解：数据库版本
     * 用于升级表结构
     */
    private static final int DB_VERSION = 4;

    /**
     * TABLE 接口
     * 从名字理解：表名集合
     * 定义数据库中两张主表
     * Ctrl+左键可在代码中全局查看表使用位置
     */
    public interface TABLE {
        /** 笔记/文件夹主表 */
        public static final String NOTE = "note";
        /** 笔记内容/附加数据表 */
        public static final String DATA = "data";
    }

    private static final String TAG = "NotesDatabaseHelper";

    /**
     * mInstance
     * 单例模式
     * 作用：全局只保留一个数据库连接，避免资源泄漏
     */
    private static NotesDatabaseHelper mInstance;

    // ==================== 建表语句 ====================

    /**
     * CREATE_NOTE_TABLE_SQL
     * 从名字理解：创建 note 表
     * 表结构来自 NoteColumns 接口定义
     * 作用：存储笔记、文件夹、系统目录信息
     */
    private static final String CREATE_NOTE_TABLE_SQL =
            "CREATE TABLE " + TABLE.NOTE + "(" +
                    NoteColumns.ID + " INTEGER PRIMARY KEY," +
                    NoteColumns.PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.ALERTED_DATE + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.BG_COLOR_ID + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
                    NoteColumns.HAS_ATTACHMENT + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
                    NoteColumns.NOTES_COUNT + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.SNIPPET + " TEXT NOT NULL DEFAULT ''," +
                    NoteColumns.TYPE + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.WIDGET_ID + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.WIDGET_TYPE + " INTEGER NOT NULL DEFAULT -1," +
                    NoteColumns.SYNC_ID + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.LOCAL_MODIFIED + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.ORIGIN_PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.GTASK_ID + " TEXT NOT NULL DEFAULT ''," +
                    NoteColumns.VERSION + " INTEGER NOT NULL DEFAULT 0" +
                    ")";

    /**
     * CREATE_DATA_TABLE_SQL
     * 从名字理解：创建 data 表
     * 表结构来自 DataColumns
     * 作用：存储文本内容、通话记录等扩展数据
     */
    private static final String CREATE_DATA_TABLE_SQL =
            "CREATE TABLE " + TABLE.DATA + "(" +
                    DataColumns.ID + " INTEGER PRIMARY KEY," +
                    DataColumns.MIME_TYPE + " TEXT NOT NULL," +
                    DataColumns.NOTE_ID + " INTEGER NOT NULL DEFAULT 0," +
                    NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
                    NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
                    DataColumns.CONTENT + " TEXT NOT NULL DEFAULT ''," +
                    DataColumns.DATA1 + " INTEGER," +
                    DataColumns.DATA2 + " INTEGER," +
                    DataColumns.DATA3 + " TEXT NOT NULL DEFAULT ''," +
                    DataColumns.DATA4 + " TEXT NOT NULL DEFAULT ''," +
                    DataColumns.DATA5 + " TEXT NOT NULL DEFAULT ''" +
                    ")";

    /**
     * CREATE_DATA_NOTE_ID_INDEX_SQL
     * 从名字理解：为 note_id 建立索引
     * 作用：加速根据笔记ID查询内容
     */
    private static final String CREATE_DATA_NOTE_ID_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS note_id_index ON " +
                    TABLE.DATA + "(" + DataColumns.NOTE_ID + ");";

    // ==================== 触发器 ====================

    /**
     * 触发器：修改父文件夹 → 目标文件夹计数 +1
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
            "CREATE TRIGGER increase_folder_count_on_update "+
                    " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
                    "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
                    " END";

    /**
     * 触发器：修改父文件夹 → 原文件夹计数 -1
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
            "CREATE TRIGGER decrease_folder_count_on_update " +
                    " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
                    "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
                    "  AND " + NoteColumns.NOTES_COUNT + ">0" + ";" +
                    " END";

    /**
     * 触发器：插入笔记 → 父文件夹计数 +1
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER =
            "CREATE TRIGGER increase_folder_count_on_insert " +
                    " AFTER INSERT ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
                    "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
                    " END";

    /**
     * 触发器：删除笔记 → 父文件夹计数 -1
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER =
            "CREATE TRIGGER decrease_folder_count_on_delete " +
                    " AFTER DELETE ON " + TABLE.NOTE +
                    " BEGIN " +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
                    "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
                    "  AND " + NoteColumns.NOTES_COUNT + ">0;" +
                    " END";

    /**
     * 触发器：插入文本 → 更新笔记摘要 snippet
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER =
            "CREATE TRIGGER update_note_content_on_insert " +
                    " AFTER INSERT ON " + TABLE.DATA +
                    " WHEN new." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
                    "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
                    " END";

    /**
     * 触发器：更新文本 → 同步更新摘要
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER =
            "CREATE TRIGGER update_note_content_on_update " +
                    " AFTER UPDATE ON " + TABLE.DATA +
                    " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
                    "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
                    " END";

    /**
     * 触发器：删除文本 → 清空摘要
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER =
            "CREATE TRIGGER update_note_content_on_delete " +
                    " AFTER delete ON " + TABLE.DATA +
                    " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.SNIPPET + "=''" +
                    "  WHERE " + NoteColumns.ID + "=old." + DataColumns.NOTE_ID + ";" +
                    " END";

    /**
     * 触发器：删除笔记 → 级联删除 data 表内容
     */
    private static final String NOTE_DELETE_DATA_ON_DELETE_TRIGGER =
            "CREATE TRIGGER delete_data_on_delete " +
                    " AFTER DELETE ON " + TABLE.NOTE +
                    " BEGIN" +
                    "  DELETE FROM " + TABLE.DATA +
                    "   WHERE " + DataColumns.NOTE_ID + "=old." + NoteColumns.ID + ";" +
                    " END";

    /**
     * 触发器：删除文件夹 → 级联删除子笔记
     */
    private static final String FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER =
            "CREATE TRIGGER folder_delete_notes_on_delete " +
                    " AFTER DELETE ON " + TABLE.NOTE +
                    " BEGIN" +
                    "  DELETE FROM " + TABLE.NOTE +
                    "   WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
                    " END";

    /**
     * 触发器：文件夹移入回收站 → 子笔记全部移入回收站
     */
    private static final String FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER =
            "CREATE TRIGGER folder_move_notes_on_trash " +
                    " AFTER UPDATE ON " + TABLE.NOTE +
                    " WHEN new." + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
                    " BEGIN" +
                    "  UPDATE " + TABLE.NOTE +
                    "   SET " + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
                    "  WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
                    " END";

    /**
     * 构造方法
     * 调用父类 SQLiteOpenHelper 初始化数据库
     */
    public NotesDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    /**
     * createNoteTable
     * 从名字理解：创建 note 表
     * 同时创建触发器与系统文件夹
     */
    public void createNoteTable(SQLiteDatabase db) {
        db.execSQL(CREATE_NOTE_TABLE_SQL);
        reCreateNoteTableTriggers(db);
        createSystemFolder(db);
        Log.d(TAG, "note table has been created");
    }

    /**
     * reCreateNoteTableTriggers
     * 重建 note 表所有触发器
     */
    private void reCreateNoteTableTriggers(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS delete_data_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS folder_delete_notes_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS folder_move_notes_on_trash");

        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_DELETE_DATA_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER);
        db.execSQL(FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER);
        db.execSQL(FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER);
    }

    /**
     * createSystemFolder
     * 创建系统内置文件夹：根目录、通话、临时、回收站
     */
    private void createSystemFolder(SQLiteDatabase db) {
        ContentValues values = new ContentValues();

        values.put(NoteColumns.ID, Notes.ID_CALL_RECORD_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        values.clear();
        values.put(NoteColumns.ID, Notes.ID_ROOT_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TEMPARAY_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * createDataTable
     * 创建 data 表、索引、触发器
     */
    public void createDataTable(SQLiteDatabase db) {
        db.execSQL(CREATE_DATA_TABLE_SQL);
        reCreateDataTableTriggers(db);
        db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL);
        Log.d(TAG, "data table has been created");
    }

    /**
     * reCreateDataTableTriggers
     * 重建 data 表触发器
     */
    private void reCreateDataTableTriggers(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_delete");

        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER);
    }

    /**
     * getInstance
     * 单例获取实例
     */
    static synchronized NotesDatabaseHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new NotesDatabaseHelper(context);
        }
        return mInstance;
    }

    /**
     * onCreate
     * 系统回调：首次创建数据库
     * 创建两张主表
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        createNoteTable(db);
        createDataTable(db);
    }

    /**
     * onUpgrade
     * 数据库版本升级
     * 按版本号逐步升级表结构
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        boolean reCreateTriggers = false;
        boolean skipV2 = false;

        if (oldVersion == 1) {
            upgradeToV2(db);
            skipV2 = true;
            oldVersion++;
        }

        if (oldVersion == 2 && !skipV2) {
            upgradeToV3(db);
            reCreateTriggers = true;
            oldVersion++;
        }

        if (oldVersion == 3) {
            upgradeToV4(db);
            oldVersion++;
        }

        if (reCreateTriggers) {
            reCreateNoteTableTriggers(db);
            reCreateDataTableTriggers(db);
        }

        if (oldVersion != newVersion) {
            throw new IllegalStateException("Upgrade notes database to version " + newVersion
                    + "fails");
        }
    }

    /**
     * upgradeToV2
     * 版本1→2：重建表
     */
    private void upgradeToV2(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.NOTE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.DATA);
        createNoteTable(db);
        createDataTable(db);
    }

    /**
     * upgradeToV3
     * 版本2→3：添加 gtask_id 字段、回收站
     */
    private void upgradeToV3(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_update");

        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_ID
                + " TEXT NOT NULL DEFAULT ''");

        ContentValues values = new ContentValues();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * upgradeToV4
     * 版本3→4：添加 version 版本字段
     */
    private void upgradeToV4(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.VERSION
                + " INTEGER NOT NULL DEFAULT 0");
    }
}