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

import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 类名：Task
 * 从名字理解：任务 = Google Task 任务
 * 继承：Node（同步节点基类）
 * 功能：封装单个 Google Task 的数据与行为
 * 作用：负责本地笔记 ↔ 远程任务 的数据转换与同步逻辑
 * 属于：gtask 同步核心数据类
 */
public class Task extends Node {
    /**
     * TAG
     * 从名字理解：类名简称 Task
     * 用于日志输出
     */
    private static final String TAG = Task.class.getSimpleName();

    /**
     * mCompleted
     * 从名字理解：是否完成
     * 作用：任务完成状态
     */
    private boolean mCompleted;

    /**
     * mNotes
     * 从名字理解：任务备注
     * 对应：Google Task 的 notes 字段
     */
    private String mNotes;

    /**
     * mMetaInfo
     * 从名字理解：元信息 JSON
     * 存储：本地笔记的结构数据（用于同步回写）
     */
    private JSONObject mMetaInfo;

    /**
     * mPriorSibling
     * 从名字理解：前一个兄弟任务
     * 作用：保持任务排序
     */
    private Task mPriorSibling;

    /**
     * mParent
     * 从名字理解：父任务列表
     * 对应：任务所在的文件夹/TaskList
     */
    private TaskList mParent;

    /**
     * 构造方法 Task()
     * 初始化任务默认状态
     */
    public Task() {
        super();
        mCompleted = false;
        mNotes = null;
        mPriorSibling = null;
        mParent = null;
        mMetaInfo = null;
    }

    /**
     * 方法名：getCreateAction
     * 从名字理解：生成创建任务的 JSON
     * 重写：Node 抽象方法
     * 作用：生成提交给 Google Task 的创建请求
     */
    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // 操作类型：创建
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);

            // 操作ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // 任务在列表中的位置
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mParent.getChildTaskIndex(this));

            // 任务实体信息
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_TASK);
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

            // 父任务列表ID
            js.put(GTaskStringUtils.GTASK_JSON_PARENT_ID, mParent.getGid());

            // 父节点类型
            js.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);

            // 任务列表ID
            js.put(GTaskStringUtils.GTASK_JSON_LIST_ID, mParent.getGid());

            // 前一个兄弟任务ID
            if (mPriorSibling != null) {
                js.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, mPriorSibling.getGid());
            }

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            throw new ActionFailureException("fail to generate task-create jsonobject");
        }

        return js;
    }

    /**
     * 方法名：getUpdateAction
     * 从名字理解：生成更新任务的 JSON
     * 重写：Node 抽象方法
     * 作用：生成提交给 Google Task 的更新请求
     */
    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // 操作类型：更新
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);

            // 操作ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // 任务ID
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // 更新内容
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            throw new ActionFailureException("fail to generate task-update jsonobject");
        }

        return js;
    }

    /**
     * 方法名：setContentByRemoteJSON
     * 从名字理解：用远程 JSON 设置任务内容
     * 重写：Node 抽象方法
     * 作用：从 Google Task 返回的数据解析到本地对象
     */
    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                // 设置远程任务ID
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }

                // 最后修改时间
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }

                // 任务标题
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }

                // 任务备注
                if (js.has(GTaskStringUtils.GTASK_JSON_NOTES)) {
                    setNotes(js.getString(GTaskStringUtils.GTASK_JSON_NOTES));
                }

                // 是否删除
                if (js.has(GTaskStringUtils.GTASK_JSON_DELETED)) {
                    setDeleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_DELETED));
                }

                // 是否完成
                if (js.has(GTaskStringUtils.GTASK_JSON_COMPLETED)) {
                    setCompleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_COMPLETED));
                }
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                throw new ActionFailureException("fail to get task content from jsonobject");
            }
        }
    }

    /**
     * 方法名：setContentByLocalJSON
     * 从名字理解：用本地 JSON 设置任务内容
     * 重写：Node 抽象方法
     * 作用：从本地笔记数据解析成任务对象
     */
    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)
                || !js.has(GTaskStringUtils.META_HEAD_DATA)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
        }

        try {
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

            if (note.getInt(NoteColumns.TYPE) != Notes.TYPE_NOTE) {
                Log.e(TAG, "invalid type");
                return;
            }

            // 从内容数据中提取笔记文本作为任务标题
            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject data = dataArray.getJSONObject(i);
                if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                    setName(data.getString(DataColumns.CONTENT));
                    break;
                }
            }

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * 方法名：getLocalJSONFromContent
     * 从名字理解：从任务内容生成本地笔记 JSON
     * 重写：Node 抽象方法
     * 作用：将任务对象转回本地笔记格式
     */
    public JSONObject getLocalJSONFromContent() {
        String name = getName();
        try {
            if (mMetaInfo == null) {
                // 任务来自网页，新建本地笔记结构
                JSONObject js = new JSONObject();
                JSONObject note = new JSONObject();
                JSONArray dataArray = new JSONArray();
                JSONObject data = new JSONObject();
                data.put(DataColumns.CONTENT, name);
                dataArray.put(data);
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);
                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
                return js;
            } else {
                // 已有元信息，直接更新内容
                JSONArray dataArray = mMetaInfo.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                        data.put(DataColumns.CONTENT, getName());
                        break;
                    }
                }
                return mMetaInfo;
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            return null;
        }
    }

    /**
     * 方法名：setMetaInfo
     * 从名字理解：设置元信息
     * 作用：保存本地笔记结构，用于同步回写
     */
    public void setMetaInfo(MetaData metaData) {
        if (metaData != null && metaData.getNotes() != null) {
            try {
                mMetaInfo = new JSONObject(metaData.getNotes());
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                mMetaInfo = null;
            }
        }
    }

    /**
     * 方法名：getSyncAction
     * 从名字理解：获取同步动作
     * 重写：Node 抽象方法
     * 作用：对比本地与远程，返回该执行哪种同步
     */
    public int getSyncAction(Cursor c) {
        try {
            JSONObject noteInfo = null;
            if (mMetaInfo != null && mMetaInfo.has(GTaskStringUtils.META_HEAD_NOTE)) {
                noteInfo = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            }

            if (noteInfo == null) {
                Log.w(TAG, "it seems that note meta has been deleted");
                return SYNC_ACTION_UPDATE_REMOTE;
            }

            if (!noteInfo.has(NoteColumns.ID)) {
                Log.w(TAG, "remote note id seems to be deleted");
                return SYNC_ACTION_UPDATE_LOCAL;
            }

            // 校验笔记 ID 是否匹配
            if (c.getLong(SqlNote.ID_COLUMN) != noteInfo.getLong(NoteColumns.ID)) {
                Log.w(TAG, "note id doesn't match");
                return SYNC_ACTION_UPDATE_LOCAL;
            }

            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // 本地无更新
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 双方都无变化
                    return SYNC_ACTION_NONE;
                } else {
                    // 远程更新本地
                    return SYNC_ACTION_UPDATE_LOCAL;
                }
            } else {
                // 校验 Google Task ID 是否匹配
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // 仅本地修改
                    return SYNC_ACTION_UPDATE_REMOTE;
                } else {
                    // 两端都修改 → 冲突
                    return SYNC_ACTION_UPDATE_CONFLICT;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

        return SYNC_ACTION_ERROR;
    }

    /**
     * 方法名：isWorthSaving
     * 从名字理解：是否值得保存
     * 作用：判断任务是否有有效内容
     */
    public boolean isWorthSaving() {
        return mMetaInfo != null || (getName() != null && getName().trim().length() > 0)
                || (getNotes() != null && getNotes().trim().length() > 0);
    }

    // ==================== SET 方法 ====================
    public void setCompleted(boolean completed) {
        this.mCompleted = completed;
    }

    public void setNotes(String notes) {
        this.mNotes = notes;
    }

    public void setPriorSibling(Task priorSibling) {
        this.mPriorSibling = priorSibling;
    }

    public void setParent(TaskList parent) {
        this.mParent = parent;
    }

    // ==================== GET 方法 ====================
    public boolean getCompleted() {
        return this.mCompleted;
    }

    public String getNotes() {
        return this.mNotes;
    }

    public Task getPriorSibling() {
        return this.mPriorSibling;
    }

    public TaskList getParent() {
        return this.mParent;
    }

}