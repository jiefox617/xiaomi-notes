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

import android.net.Uri;

/**
 * 笔记数据中心类
 *
 * 【类功能】
 * 定义整个笔记应用的核心常量：ContentProvider URI、数据库表结构、字段名、数据类型等
 * 属于ContentProvider标准架构中的常量定义类
 *
 * 【类间关系】
 * - 被NotesProvider使用：定义URI和数据库表结构
 * - 被所有Activity/Service使用：访问数据时引用URI和字段名
 * - 被WorkingNote使用：操作笔记数据
 * - 被DataUtils使用：数据库工具方法
 */
public class Notes {

    // ==================== ContentProvider 标识 ====================
    /** ContentProvider唯一授权名，用于构建URI */
    public static final String AUTHORITY = "micode_notes";

    /** 日志标签 */
    public static final String TAG = "Notes";

    // ==================== 数据类型常量 ====================
    public static final int TYPE_NOTE     = 0;   // 普通笔记
    public static final int TYPE_FOLDER   = 1;   // 文件夹
    public static final int TYPE_SYSTEM   = 2;   // 系统类型（不可删除）

    // ==================== 系统内置文件夹ID ====================
    public static final int ID_ROOT_FOLDER = 0;          // 根文件夹
    public static final int ID_TEMPARAY_FOLDER = -1;     // 临时文件夹
    public static final int ID_CALL_RECORD_FOLDER = -2;  // 通话记录文件夹
    public static final int ID_TRASH_FOLER = -3;         // 回收站

    // ==================== Intent传参KEY ====================
    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date";
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id";
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id";
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type";
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id";
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date";

    // ==================== 桌面小部件类型 ====================
    public static final int TYPE_WIDGET_INVALIDE = -1;   // 无效
    public static final int TYPE_WIDGET_2X = 0;          // 2x2小部件
    public static final int TYPE_WIDGET_4X = 1;          // 4x4小部件

    /**
     * 数据类型常量（用于Data表的mime_type字段）
     */
    public static class DataConstants {
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE;      // 文本笔记
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE; // 通话笔记
    }

    // ==================== ContentProvider URI ====================
    /** 笔记/文件夹表URI：content://micode_notes/note */
    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note");

    /** 数据扩展表URI：content://micode_notes/data */
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data");

    // ==================== Note表字段定义 ====================
    /**
     * 笔记表字段
     * 存储笔记和文件夹的基本信息
     */
    public interface NoteColumns {
        public static final String ID = "_id";                    // 主键
        public static final String PARENT_ID = "parent_id";      // 父文件夹ID
        public static final String CREATED_DATE = "created_date"; // 创建时间
        public static final String MODIFIED_DATE = "modified_date"; // 修改时间
        public static final String ALERTED_DATE = "alert_date";   // 提醒时间
        public static final String SNIPPET = "snippet";          // 摘要/文件夹名
        public static final String WIDGET_ID = "widget_id";       // 小部件ID
        public static final String WIDGET_TYPE = "widget_type";   // 小部件类型
        public static final String BG_COLOR_ID = "bg_color_id";   // 背景颜色ID
        public static final String HAS_ATTACHMENT = "has_attachment"; // 是否有附件
        public static final String NOTES_COUNT = "notes_count";   // 子笔记数量（文件夹用）
        public static final String TYPE = "type";                 // 类型：笔记/文件夹
        public static final String SYNC_ID = "sync_id";           // 同步ID
        public static final String LOCAL_MODIFIED = "local_modified"; // 本地是否修改
        public static final String ORIGIN_PARENT_ID = "origin_parent_id"; // 原父文件夹
        public static final String GTASK_ID = "gtask_id";         // Google Tasks ID
        public static final String VERSION = "version";           // 版本号
    }

    // ==================== Data表字段定义 ====================
    /**
     * 数据扩展表字段
     * 存储笔记的具体内容（支持多种MIME类型）
     */
    public interface DataColumns {
        public static final String ID = "_id";                    // 主键
        public static final String MIME_TYPE = "mime_type";      // 数据类型
        public static final String NOTE_ID = "note_id";          // 关联的笔记ID
        public static final String CREATED_DATE = "created_date"; // 创建时间
        public static final String MODIFIED_DATE = "modified_date"; // 修改时间
        public static final String CONTENT = "content";          // 内容
        // 扩展字段，用于存储不同类型的数据
        public static final String DATA1 = "data1";
        public static final String DATA2 = "data2";
        public static final String DATA3 = "data3";
        public static final String DATA4 = "data4";
        public static final String DATA5 = "data5";
    }

    // ==================== 文本笔记 ====================
    /**
     * 文本笔记
     * MIME类型：vnd.android.cursor.item/text_note
     */
    public static final class TextNote implements DataColumns {
        public static final String MODE = DATA1;                 // 模式：普通/清单
        public static final int MODE_CHECK_LIST = 1;             // 清单模式

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note";
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note");
    }

    // ==================== 通话笔记 ====================
    /**
     * 通话笔记
     * MIME类型：vnd.android.cursor.item/call_note
     * 存储通话记录：电话号码、通话时间等
     */
    public static final class CallNote implements DataColumns {
        public static final String CALL_DATE = DATA1;            // 通话日期
        public static final String PHONE_NUMBER = DATA3;         // 电话号码

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note";
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note");
    }
}