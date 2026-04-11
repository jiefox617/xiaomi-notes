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
 * 类名：Notes
 * 从名字理解：笔记/便签 数据中心
 * 实际功能：定义整个笔记应用的 常量、URI、数据库字段、数据结构
 * 属于 Android ContentProvider 标准结构类
 */
public class Notes {

    /**
     * 权威标识 AUTHORITY
     * 从名字理解：ContentProvider 的唯一授权名
     * Ctrl+溯源：会被用于所有 CONTENT_URI 的构建
     * 作用：系统识别该应用数据源的唯一标识
     */
    public static final String AUTHORITY = "micode_notes";

    /**
     * 日志TAG
     * 从名字理解：类名 Notes
     */
    public static final String TAG = "Notes";

    // ==================== 数据类型常量 ====================
    /**
     * TYPE_NOTE = 0
     * 从名字理解：普通笔记
     * 数据库含义：表中 type 字段标记为笔记
     */
    public static final int TYPE_NOTE     = 0;

    /**
     * TYPE_FOLDER = 1
     * 从名字理解：文件夹
     * 数据库含义：表中 type 字段标记为文件夹
     */
    public static final int TYPE_FOLDER   = 1;

    /**
     * TYPE_SYSTEM = 2
     * 从名字理解：系统类型
     * 作用：系统内置数据（不可删除）
     */
    public static final int TYPE_SYSTEM   = 2;

    // ==================== 系统内置文件夹 ID ====================
    /**
     * ID_ROOT_FOLDER = 0
     * 从名字理解：根文件夹
     * 作用：所有笔记默认父目录
     */
    public static final int ID_ROOT_FOLDER = 0;

    /**
     * ID_TEMPARAY_FOLDER = -1
     * 从名字理解：临时文件夹
     * 作用：未归类笔记存放目录
     */
    public static final int ID_TEMPARAY_FOLDER = -1;

    /**
     * ID_CALL_RECORD_FOLDER = -2
     * 从名字理解：通话记录文件夹
     * 作用：保存通话笔记
     */
    public static final int ID_CALL_RECORD_FOLDER = -2;

    /**
     * ID_TRASH_FOLER = -3
     * 从名字理解：回收站文件夹
     * 作用：已删除笔记存放目录
     */
    public static final int ID_TRASH_FOLER = -3;

    // ==================== Intent 传参 KEY ====================
    /**
     * 从名字理解：提醒日期
     * 用于页面跳转传递闹钟时间
     */
    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date";

    /**
     * 从名字理解：背景ID
     * 传递笔记颜色主题
     */
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id";

    /**
     * 从名字理解：小部件ID
     */
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id";

    /**
     * 从名字理解：小部件类型
     */
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type";

    /**
     * 从名字理解：文件夹ID
     */
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id";

    /**
     * 从名字理解：通话日期
     */
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date";

    // ==================== 桌面小部件类型 ====================
    public static final int TYPE_WIDGET_INVALIDE      = -1;
    public static final int TYPE_WIDGET_2X            = 0;
    public static final int TYPE_WIDGET_4X            = 1;

    /**
     * 数据常量类
     * 从名字理解：数据类型常量
     */
    public static class DataConstants {
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE;
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE;
    }

    // ==================== ContentProvider 访问 URI ====================
    /**
     * CONTENT_NOTE_URI
     * 从名字理解：笔记和文件夹的总URI
     * 标准格式：content://权限/note
     * 作用：查询、删除、修改笔记/文件夹
     */
    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note");

    /**
     * CONTENT_DATA_URI
     * 从名字理解：数据内容URI
     * 作用：查询笔记文本、通话记录等扩展数据
     */
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data");

    // ==================== 数据库表结构：Note 表 ====================
    /**
     * NoteColumns
     * 从名字理解：笔记列表的字段
     * 接口作用：定义 note 表所有列名
     * Android 数据库标准命名
     */
    public interface NoteColumns {
        /** 主键ID */
        public static final String ID = "_id";

        /** 父文件夹ID */
        public static final String PARENT_ID = "parent_id";

        /** 创建时间 */
        public static final String CREATED_DATE = "created_date";

        /** 修改时间 */
        public static final String MODIFIED_DATE = "modified_date";

        /** 提醒时间 */
        public static final String ALERTED_DATE = "alert_date";

        /** 笔记摘要/文件夹名称 */
        public static final String SNIPPET = "snippet";

        /** 绑定小部件ID */
        public static final String WIDGET_ID = "widget_id";

        /** 小部件类型 */
        public static final String WIDGET_TYPE = "widget_type";

        /** 背景颜色ID */
        public static final String BG_COLOR_ID = "bg_color_id";

        /** 是否有附件 */
        public static final String HAS_ATTACHMENT = "has_attachment";

        /** 文件夹内笔记数量 */
        public static final String NOTES_COUNT = "notes_count";

        /** 类型：笔记/文件夹 */
        public static final String TYPE = "type";

        /** 同步ID */
        public static final String SYNC_ID = "sync_id";

        /** 是否本地修改 */
        public static final String LOCAL_MODIFIED = "local_modified";

        /** 原始父文件夹（移动前） */
        public static final String ORIGIN_PARENT_ID = "origin_parent_id";

        /** 谷歌任务ID */
        public static final String GTASK_ID = "gtask_id";

        /** 版本号 */
        public static final String VERSION = "version";
    }

    // ==================== 数据库表结构：Data 表 ====================
    /**
     * DataColumns
     * 从名字理解：数据扩展表字段
     * 作用：存储笔记内容、通话记录等扩展信息
     */
    public interface DataColumns {
        public static final String ID = "_id";
        public static final String MIME_TYPE = "mime_type";
        public static final String NOTE_ID = "note_id";
        public static final String CREATED_DATE = "created_date";
        public static final String MODIFIED_DATE = "modified_date";
        public static final String CONTENT = "content";

        public static final String DATA1 = "data1";
        public static final String DATA2 = "data2";
        public static final String DATA3 = "data3";
        public static final String DATA4 = "data4";
        public static final String DATA5 = "data5";
    }

    // ==================== 文本笔记结构 ====================
    /**
     * TextNote
     * 从名字理解：文本笔记
     * 实现 DataColumns 扩展表
     */
    public static final class TextNote implements DataColumns {
        /** 模式：普通/清单 */
        public static final String MODE = DATA1;
        public static final int MODE_CHECK_LIST = 1;

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note";

        /** 文本笔记专用URI */
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note");
    }

    // ==================== 通话笔记结构 ====================
    /**
     * CallNote
     * 从名字理解：通话笔记
     * 存储电话、日期、录音等
     */
    public static final class CallNote implements DataColumns {
        public static final String CALL_DATE = DATA1;
        public static final String PHONE_NUMBER = DATA3;

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note";

        /** 通话笔记URI */
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note");
    }
}