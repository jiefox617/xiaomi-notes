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
 * 任务类
 *
 * 【类功能】
 * 封装单个Google Task的数据与行为，负责本地笔记与远程任务的数据转换和同步逻辑
 *
 * 【类间关系】
 * - 继承Node：复用基础属性（gid、name、deleted等）
 * - 被TaskList管理：作为任务列表的子节点
 * - 使用MetaData：保存本地笔记结构信息
 */
public class Task extends Node {
    private static final String TAG = Task.class.getSimpleName();

    private boolean mCompleted;      // 完成状态
    private String mNotes;           // 任务备注
    private JSONObject mMetaInfo;    // 本地笔记元信息（用于同步回写）
    private Task mPriorSibling;      // 前一个兄弟任务（用于排序）
    private TaskList mParent;        // 父任务列表

    public Task() {
        super();
        mCompleted = false;
        mNotes = null;
        mPriorSibling = null;
        mParent = null;
        mMetaInfo = null;
    }

    /**
     * 生成创建任务的JSON（用于Google Tasks API）
     */
    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();
        try {
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mParent.getChildTaskIndex(this));

            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_TASK);
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);
            js.put(GTaskStringUtils.GTASK_JSON_PARENT_ID, mParent.getGid());
            js.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);
            js.put(GTaskStringUtils.GTASK_JSON_LIST_ID, mParent.getGid());

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
     * 生成更新任务的JSON（用于Google Tasks API）
     */
    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();
        try {
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

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
     * 从远程JSON设置任务内容（解析Google Tasks API返回的数据）
     */
    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }
                if (js.has(GTaskStringUtils.GTASK_JSON_NOTES)) {
                    setNotes(js.getString(GTaskStringUtils.GTASK_JSON_NOTES));
                }
                if (js.has(GTaskStringUtils.GTASK_JSON_DELETED)) {
                    setDeleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_DELETED));
                }
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
     * 从本地JSON设置任务内容（解析本地笔记数据）
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

            // 从data数组中提取笔记文本作为任务标题
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
     * 从任务内容生成本地笔记JSON（用于同步回写）
     */
    public JSONObject getLocalJSONFromContent() {
        String name = getName();
        try {
            if (mMetaInfo == null) {
                // 新建笔记结构
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
                // 更新已有笔记内容
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
     * 设置元信息（保存本地笔记结构，用于同步回写）
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
     * 获取同步动作
     * 对比本地与远程数据，决定执行哪种同步操作
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

            // 校验笔记ID
            if (c.getLong(SqlNote.ID_COLUMN) != noteInfo.getLong(NoteColumns.ID)) {
                Log.w(TAG, "note id doesn't match");
                return SYNC_ACTION_UPDATE_LOCAL;
            }

            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // 本地无修改
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    return SYNC_ACTION_NONE;      // 完全同步
                } else {
                    return SYNC_ACTION_UPDATE_LOCAL;  // 远程更新
                }
            } else {
                // 本地有修改
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    return SYNC_ACTION_UPDATE_REMOTE;  // 仅本地修改
                } else {
                    return SYNC_ACTION_UPDATE_CONFLICT; // 双方都修改 → 冲突
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        return SYNC_ACTION_ERROR;
    }

    /**
     * 判断是否值得保存（有有效内容）
     */
    public boolean isWorthSaving() {
        return mMetaInfo != null || (getName() != null && getName().trim().length() > 0)
                || (getNotes() != null && getNotes().trim().length() > 0);
    }

    // ====================== Getter/Setter ======================
    public void setCompleted(boolean completed) { this.mCompleted = completed; }
    public void setNotes(String notes) { this.mNotes = notes; }
    public void setPriorSibling(Task priorSibling) { this.mPriorSibling = priorSibling; }
    public void setParent(TaskList parent) { this.mParent = parent; }

    public boolean getCompleted() { return this.mCompleted; }
    public String getNotes() { return this.mNotes; }
    public Task getPriorSibling() { return this.mPriorSibling; }
    public TaskList getParent() { return this.mParent; }
}