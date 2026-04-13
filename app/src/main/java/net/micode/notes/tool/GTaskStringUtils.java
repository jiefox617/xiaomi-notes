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

/**
 * Google Tasks同步工具常量类
 *
 * 【类功能】
 * 统一管理与Google Tasks同步时用到的JSON字段、文件夹名称、元数据标识
 *
 * 【类间关系】
 * - 被GTaskClient使用：构造网络请求JSON
 * - 被GTaskManager使用：解析服务器返回数据
 * - 被Task/TaskList/MetaData使用：数据模型转换
 */
public class GTaskStringUtils {

    // ==================== Google Tasks API JSON字段 ====================
    public final static String GTASK_JSON_ACTION_ID = "action_id";
    public final static String GTASK_JSON_ACTION_LIST = "action_list";
    public final static String GTASK_JSON_ACTION_TYPE = "action_type";
    public final static String GTASK_JSON_ACTION_TYPE_CREATE = "create";
    public final static String GTASK_JSON_ACTION_TYPE_GETALL = "get_all";
    public final static String GTASK_JSON_ACTION_TYPE_MOVE = "move";
    public final static String GTASK_JSON_ACTION_TYPE_UPDATE = "update";
    public final static String GTASK_JSON_CREATOR_ID = "creator_id";
    public final static String GTASK_JSON_CHILD_ENTITY = "child_entity";
    public final static String GTASK_JSON_CLIENT_VERSION = "client_version";
    public final static String GTASK_JSON_COMPLETED = "completed";
    public final static String GTASK_JSON_CURRENT_LIST_ID = "current_list_id";
    public final static String GTASK_JSON_DEFAULT_LIST_ID = "default_list_id";
    public final static String GTASK_JSON_DELETED = "deleted";
    public final static String GTASK_JSON_DEST_LIST = "dest_list";
    public final static String GTASK_JSON_DEST_PARENT = "dest_parent";
    public final static String GTASK_JSON_DEST_PARENT_TYPE = "dest_parent_type";
    public final static String GTASK_JSON_ENTITY_DELTA = "entity_delta";
    public final static String GTASK_JSON_ENTITY_TYPE = "entity_type";
    public final static String GTASK_JSON_GET_DELETED = "get_deleted";
    public final static String GTASK_JSON_ID = "id";
    public final static String GTASK_JSON_INDEX = "index";
    public final static String GTASK_JSON_LAST_MODIFIED = "last_modified";
    public final static String GTASK_JSON_LATEST_SYNC_POINT = "latest_sync_point";
    public final static String GTASK_JSON_LIST_ID = "list_id";
    public final static String GTASK_JSON_LISTS = "lists";
    public final static String GTASK_JSON_NAME = "name";
    public final static String GTASK_JSON_NEW_ID = "new_id";
    public final static String GTASK_JSON_NOTES = "notes";
    public final static String GTASK_JSON_PARENT_ID = "parent_id";
    public final static String GTASK_JSON_PRIOR_SIBLING_ID = "prior_sibling_id";
    public final static String GTASK_JSON_RESULTS = "results";
    public final static String GTASK_JSON_SOURCE_LIST = "source_list";
    public final static String GTASK_JSON_TASKS = "tasks";
    public final static String GTASK_JSON_TYPE = "type";
    public final static String GTASK_JSON_TYPE_GROUP = "GROUP";
    public final static String GTASK_JSON_TYPE_TASK = "TASK";
    public final static String GTASK_JSON_USER = "user";

    // ==================== 本地同步标识常量 ====================
    public final static String MIUI_FOLDER_PREFFIX = "[MIUI_Notes]";      // 文件夹前缀
    public final static String FOLDER_DEFAULT = "Default";                // 默认文件夹
    public final static String FOLDER_CALL_NOTE = "Call_Note";            // 通话笔记文件夹
    public final static String FOLDER_META = "METADATA";                  // 元数据文件夹
    public final static String META_HEAD_GTASK_ID = "meta_gid";           // 远程任务ID
    public final static String META_HEAD_NOTE = "meta_note";              // 笔记信息
    public final static String META_HEAD_DATA = "meta_data";              // 数据信息
    public final static String META_NOTE_NAME = "[META INFO] DON'T UPDATE AND DELETE";  // 元数据标记
}