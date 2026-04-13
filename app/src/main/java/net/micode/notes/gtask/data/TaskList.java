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
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * 任务列表类
 *
 * 【类功能】
 * 对应Google Tasks中的【任务列表】，本地对应【文件夹】
 * 管理一组子任务，处理列表的创建/更新/同步，维护任务顺序与父子关系
 *
 * 【类间关系】
 * - 继承Node：复用基础属性（gid、name、deleted等）
 * - 包含Task：作为任务列表的子节点
 * - 被GTaskManager使用：同步任务列表
 */
public class TaskList extends Node {
    private static final String TAG = TaskList.class.getSimpleName();

    private int mIndex;                      // 列表排序索引
    private ArrayList<Task> mChildren;      // 子任务集合

    public TaskList() {
        super();
        mChildren = new ArrayList<Task>();
        mIndex = 1;
    }

    /**
     * 生成创建任务列表的JSON（用于Google Tasks API）
     */
    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();
        try {
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mIndex);

            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            throw new ActionFailureException("fail to generate tasklist-create jsonobject");
        }
        return js;
    }

    /**
     * 生成更新任务列表的JSON（用于Google Tasks API）
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
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            throw new ActionFailureException("fail to generate tasklist-update jsonobject");
        }
        return js;
    }

    /**
     * 从远程JSON设置任务列表内容（解析Google Tasks API返回的数据）
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
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                throw new ActionFailureException("fail to get tasklist content from jsonobject");
            }
        }
    }

    /**
     * 从本地JSON设置任务列表内容（解析本地文件夹数据）
     */
    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
        }
        try {
            JSONObject folder = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);

            if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                // 普通文件夹：添加前缀标识
                String name = folder.getString(NoteColumns.SNIPPET);
                setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + name);
            } else if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                // 系统文件夹：根目录/通话记录
                if (folder.getLong(NoteColumns.ID) == Notes.ID_ROOT_FOLDER) {
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT);
                } else if (folder.getLong(NoteColumns.ID) == Notes.ID_CALL_RECORD_FOLDER) {
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE);
                } else {
                    Log.e(TAG, "invalid system folder");
                }
            } else {
                Log.e(TAG, "error type");
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * 从任务列表内容生成本地文件夹JSON（用于同步回写）
     */
    public JSONObject getLocalJSONFromContent() {
        try {
            JSONObject js = new JSONObject();
            JSONObject folder = new JSONObject();

            // 去掉本地前缀，还原文件夹名
            String folderName = getName();
            if (getName().startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX)) {
                folderName = folderName.substring(GTaskStringUtils.MIUI_FOLDER_PREFFIX.length());
            }
            folder.put(NoteColumns.SNIPPET, folderName);

            // 判断是否为系统文件夹
            if (folderName.equals(GTaskStringUtils.FOLDER_DEFAULT)
                    || folderName.equals(GTaskStringUtils.FOLDER_CALL_NOTE)) {
                folder.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
            } else {
                folder.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
            }

            js.put(GTaskStringUtils.META_HEAD_NOTE, folder);
            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            return null;
        }
    }

    /**
     * 获取同步动作
     * 对比本地与远程数据，决定执行哪种同步操作
     */
    public int getSyncAction(Cursor c) {
        try {
            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // 本地无修改
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    return SYNC_ACTION_NONE;          // 完全同步
                } else {
                    return SYNC_ACTION_UPDATE_LOCAL;  // 远程更新本地
                }
            } else {
                // 本地有修改
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;
                }
                // 无论是否冲突，都以本地为准
                return SYNC_ACTION_UPDATE_REMOTE;
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        return SYNC_ACTION_ERROR;
    }

    // ====================== 子任务管理 ======================

    public int getChildTaskCount() {
        return mChildren.size();
    }

    /**
     * 添加子任务到末尾
     */
    public boolean addChildTask(Task task) {
        boolean ret = false;
        if (task != null && !mChildren.contains(task)) {
            ret = mChildren.add(task);
            if (ret) {
                task.setPriorSibling(mChildren.isEmpty() ? null : mChildren.get(mChildren.size() - 1));
                task.setParent(this);
            }
        }
        return ret;
    }

    /**
     * 在指定位置插入子任务
     */
    public boolean addChildTask(Task task, int index) {
        if (index < 0 || index > mChildren.size()) {
            Log.e(TAG, "add child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (task != null && pos == -1) {
            mChildren.add(index, task);

            // 修正前后任务关系
            Task preTask = (index != 0) ? mChildren.get(index - 1) : null;
            Task afterTask = (index != mChildren.size() - 1) ? mChildren.get(index + 1) : null;

            task.setPriorSibling(preTask);
            if (afterTask != null) {
                afterTask.setPriorSibling(task);
            }
        }
        return true;
    }

    /**
     * 移除子任务
     */
    public boolean removeChildTask(Task task) {
        boolean ret = false;
        int index = mChildren.indexOf(task);
        if (index != -1) {
            ret = mChildren.remove(task);
            if (ret) {
                task.setPriorSibling(null);
                task.setParent(null);

                // 更新剩余任务顺序
                if (index != mChildren.size()) {
                    mChildren.get(index).setPriorSibling(
                            index == 0 ? null : mChildren.get(index - 1));
                }
            }
        }
        return ret;
    }

    /**
     * 移动子任务到指定位置
     */
    public boolean moveChildTask(Task task, int index) {
        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "move child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (pos == -1) {
            Log.e(TAG, "move child task: the task should in the list");
            return false;
        }

        if (pos == index) return true;
        return (removeChildTask(task) && addChildTask(task, index));
    }

    /**
     * 根据云端gid查找子任务
     */
    public Task findChildTaskByGid(String gid) {
        for (Task t : mChildren) {
            if (t.getGid().equals(gid)) {
                return t;
            }
        }
        return null;
    }

    public int getChildTaskIndex(Task task) {
        return mChildren.indexOf(task);
    }

    public Task getChildTaskByIndex(int index) {
        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "getTaskByIndex: invalid index");
            return null;
        }
        return mChildren.get(index);
    }

    public Task getChilTaskByGid(String gid) {
        for (Task task : mChildren) {
            if (task.getGid().equals(gid)) return task;
        }
        return null;
    }

    public ArrayList<Task> getChildTaskList() {
        return this.mChildren;
    }

    public void setIndex(int index) {
        this.mIndex = index;
    }

    public int getIndex() {
        return this.mIndex;
    }
}