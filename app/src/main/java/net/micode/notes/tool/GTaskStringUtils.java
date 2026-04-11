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

package net.micode.notes.tool;

/**
 * Google Task 同步工具常量类
 * 作用：统一管理与 Google Tasks 同步时用到的 JSON 字段、文件夹名称、元数据标识
 * 避免硬编码字符串，方便维护与修改
 */
public class GTaskStringUtils {

    // ==================== Google Task API JSON 字段常量 ====================
    // 操作唯一 ID
    public final static String GTASK_JSON_ACTION_ID = "action_id";
    // 批量操作列表
    public final static String GTASK_JSON_ACTION_LIST = "action_list";
    // 操作类型（增删改查）
    public final static String GTASK_JSON_ACTION_TYPE = "action_type";
    // 操作类型：创建
    public final static String GTASK_JSON_ACTION_TYPE_CREATE = "create";
    // 操作类型：获取全部数据
    public final static String GTASK_JSON_ACTION_TYPE_GETALL = "get_all";
    // 操作类型：移动
    public final static String GTASK_JSON_ACTION_TYPE_MOVE = "move";
    // 操作类型：更新
    public final static String GTASK_JSON_ACTION_TYPE_UPDATE = "update";
    // 创建者 ID
    public final static String GTASK_JSON_CREATOR_ID = "creator_id";
    // 子实体（子任务）
    public final static String GTASK_JSON_CHILD_ENTITY = "child_entity";
    // 客户端版本号
    public final static String GTASK_JSON_CLIENT_VERSION = "client_version";
    // 任务是否已完成
    public final static String GTASK_JSON_COMPLETED = "completed";
    // 当前列表 ID
    public final static String GTASK_JSON_CURRENT_LIST_ID = "current_list_id";
    // 默认列表 ID
    public final static String GTASK_JSON_DEFAULT_LIST_ID = "default_list_id";
    // 是否已删除
    public final static String GTASK_JSON_DELETED = "deleted";
    // 目标列表（移动时使用）
    public final static String GTASK_JSON_DEST_LIST = "dest_list";
    // 目标父项
    public final static String GTASK_JSON_DEST_PARENT = "dest_parent";
    // 目标父项类型
    public final static String GTASK_JSON_DEST_PARENT_TYPE = "dest_parent_type";
    // 实体变更数据
    public final static String GTASK_JSON_ENTITY_DELTA = "entity_delta";
    // 实体类型（任务/文件夹）
    public final static String GTASK_JSON_ENTITY_TYPE = "entity_type";
    // 是否获取已删除数据
    public final static String GTASK_JSON_GET_DELETED = "get_deleted";
    // 数据唯一 ID
    public final static String GTASK_JSON_ID = "id";
    // 排序索引
    public final static String GTASK_JSON_INDEX = "index";
    // 最后修改时间
    public final static String GTASK_JSON_LAST_MODIFIED = "last_modified";
    // 最近同步时间点
    public final static String GTASK_JSON_LATEST_SYNC_POINT = "latest_sync_point";
    // 列表 ID（文件夹 ID）
    public final static String GTASK_JSON_LIST_ID = "list_id";
    // 所有列表（文件夹）集合
    public final static String GTASK_JSON_LISTS = "lists";
    // 名称（标题）
    public final static String GTASK_JSON_NAME = "name";
    // 新 ID（重命名时使用）
    public final static String GTASK_JSON_NEW_ID = "new_id";
    // 备注（笔记正文）
    public final static String GTASK_JSON_NOTES = "notes";
    // 父项 ID
    public final static String GTASK_JSON_PARENT_ID = "parent_id";
    // 上一个兄弟节点 ID（排序用）
    public final static String GTASK_JSON_PRIOR_SIBLING_ID = "prior_sibling_id";
    // 返回结果
    public final static String GTASK_JSON_RESULTS = "results";
    // 源列表（移动时使用）
    public final static String GTASK_JSON_SOURCE_LIST = "source_list";
    // 所有任务集合
    public final static String GTASK_JSON_TASKS = "tasks";
    // 类型字段
    public final static String GTASK_JSON_TYPE = "type";
    // 类型：分组/文件夹
    public final static String GTASK_JSON_TYPE_GROUP = "GROUP";
    // 类型：任务
    public final static String GTASK_JSON_TYPE_TASK = "TASK";
    // 用户信息
    public final static String GTASK_JSON_USER = "user";

    // ==================== 本地笔记 ↔ Google Task 同步常量 ====================
    // MIUI 笔记在 Google Tasks 中的文件夹前缀标识
    public final static String MIUI_FOLDER_PREFFIX = "[MIUI_Notes]";
    // 默认同步文件夹
    public final static String FOLDER_DEFAULT = "Default";
    // 通话笔记专用同步文件夹
    public final static String FOLDER_CALL_NOTE = "Call_Note";
    // 元数据专用文件夹
    public final static String FOLDER_META = "METADATA";
    // 元数据字段：存储 Google Task 对应的 ID
    public final static String META_HEAD_GTASK_ID = "meta_gid";
    // 元数据字段：笔记信息
    public final static String META_HEAD_NOTE = "meta_note";
    // 元数据字段：数据信息
    public final static String META_HEAD_DATA = "meta_data";
    // 元数据笔记名称（提示用户不可修改删除）
    public final static String META_NOTE_NAME = "[META INFO] DON'T UPDATE AND DELETE";

}