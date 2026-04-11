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
 * 类名：TaskList
 * 从名字理解：任务列表
 * 继承：Node（同步节点基类）
 * 功能：对应 Google Task 中的【任务列表】，本地对应【文件夹】
 * 作用：管理一组子任务，处理列表的创建/更新/同步，维护任务顺序与父子关系
 */
public class TaskList extends Node {
    /**
     * TAG：类名简称，用于日志打印
     */
    private static final String TAG = TaskList.class.getSimpleName();

    /**
     * mIndex：任务列表在云端的排序索引
     */
    private int mIndex;

    /**
     * mChildren：子任务集合，存储当前列表下所有 Task 对象
     */
    private ArrayList<Task> mChildren;

    /**
     * 构造方法：初始化子任务列表与默认索引
     */
    public TaskList() {
        super();
        mChildren = new ArrayList<Task>();
        mIndex = 1;
    }

    /**
     * 方法名：getCreateAction
     * 作用：生成【创建任务列表】的 JSON 请求（提交给 Google Task）
     * 重写自 Node 抽象方法
     */
    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // 操作类型：创建
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);

            // 操作唯一 ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // 列表排序索引
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mIndex);

            // 任务列表实体数据
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-create jsonobject");
        }

        return js;
    }

    /**
     * 方法名：getUpdateAction
     * 作用：生成【更新任务列表】的 JSON 请求
     * 重写自 Node 抽象方法
     */
    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // 操作类型：更新
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);

            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // 云端任务列表 ID
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // 更新内容：名称 + 删除状态
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-update jsonobject");
        }

        return js;
    }

    /**
     * 方法名：setContentByRemoteJSON
     * 作用：从【云端 JSON】解析数据到本地任务列表
     * 重写自 Node 抽象方法
     */
    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                // 设置云端 ID
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }
                // 设置最后修改时间
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }
                // 设置列表名称
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }

            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get tasklist content from jsonobject");
            }
        }
    }

    /**
     * 方法名：setContentByLocalJSON
     * 作用：从【本地文件夹 JSON】设置任务列表信息
     * 本地文件夹 → 云端任务列表
     * 重写自 Node 抽象方法
     */
    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
        }

        try {
            JSONObject folder = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);

            // 普通文件夹：添加前缀区分
            if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                String name = folder.getString(NoteColumns.SNIPPET);
                setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + name);
            }
            // 系统文件夹：根目录/通话记录
            else if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                if (folder.getLong(NoteColumns.ID) == Notes.ID_ROOT_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT);
                else if (folder.getLong(NoteColumns.ID) == Notes.ID_CALL_RECORD_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                            + GTaskStringUtils.FOLDER_CALL_NOTE);
                else
                    Log.e(TAG, "invalid system folder");
            } else {
                Log.e(TAG, "error type");
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 方法名：getLocalJSONFromContent
     * 作用：将【云端任务列表】转为【本地文件夹 JSON】
     * 用于同步回写到本地数据库
     * 重写自 Node 抽象方法
     */
    public JSONObject getLocalJSONFromContent() {
        try {
            JSONObject js = new JSONObject();
            JSONObject folder = new JSONObject();

            // 去掉本地前缀，还原文件夹名
            String folderName = getName();
            if (getName().startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX))
                folderName = folderName.substring(GTaskStringUtils.MIUI_FOLDER_PREFFIX.length());
            folder.put(NoteColumns.SNIPPET, folderName);

            // 系统文件夹 / 普通文件夹
            if (folderName.equals(GTaskStringUtils.FOLDER_DEFAULT)
                    || folderName.equals(GTaskStringUtils.FOLDER_CALL_NOTE))
                folder.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
            else
                folder.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);

            js.put(GTaskStringUtils.META_HEAD_NOTE, folder);

            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 方法名：getSyncAction
     * 作用：对比本地与云端，返回【同步动作类型】
     * 重写自 Node 抽象方法
     */
    public int getSyncAction(Cursor c) {
        try {
            // 本地未修改
            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // 两端完全一致
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    return SYNC_ACTION_NONE;
                } else {
                    // 云端更新本地
                    return SYNC_ACTION_UPDATE_LOCAL;
                }
            }
            // 本地已修改
            else {
                // 校验云端 ID 是否匹配
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;
                }
                // 仅本地修改，或冲突 → 都以本地为准
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()
                        || c.getLong(SqlNote.SYNC_ID_COLUMN) != getLastModified()) {
                    return SYNC_ACTION_UPDATE_REMOTE;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }

    /**
     * 获取子任务总数
     */
    public int getChildTaskCount() {
        return mChildren.size();
    }

    /**
     * 添加子任务到列表末尾，维护任务前后关系
     */
    public boolean addChildTask(Task task) {
        boolean ret = false;
        if (task != null && !mChildren.contains(task)) {
            ret = mChildren.add(task);
            if (ret) {
                // 设置前一个任务 & 父列表
                task.setPriorSibling(mChildren.isEmpty() ? null : mChildren.get(mChildren.size() - 1));
                task.setParent(this);
            }
        }
        return ret;
    }

    /**
     * 在指定位置插入任务，维护链表关系
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
            Task preTask = null;
            Task afterTask = null;
            if (index != 0)
                preTask = mChildren.get(index - 1);
            if (index != mChildren.size() - 1)
                afterTask = mChildren.get(index + 1);

            task.setPriorSibling(preTask);
            if (afterTask != null)
                afterTask.setPriorSibling(task);
        }

        return true;
    }

    /**
     * 移除子任务，重置关联关系
     */
    public boolean removeChildTask(Task task) {
        boolean ret = false;
        int index = mChildren.indexOf(task);
        if (index != -1) {
            ret = mChildren.remove(task);

            if (ret) {
                // 清空关联
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
     * 移动任务到指定位置（先删后加）
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

        if (pos == index)
            return true;
        return (removeChildTask(task) && addChildTask(task, index));
    }

    /**
     * 根据云端 gid 查找子任务
     */
    public Task findChildTaskByGid(String gid) {
        for (int i = 0; i < mChildren.size(); i++) {
            Task t = mChildren.get(i);
            if (t.getGid().equals(gid)) {
                return t;
            }
        }
        return null;
    }

    /**
     * 获取任务在列表中的下标
     */
    public int getChildTaskIndex(Task task) {
        return mChildren.indexOf(task);
    }

    /**
     * 根据下标获取任务
     */
    public Task getChildTaskByIndex(int index) {
        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "getTaskByIndex: invalid index");
            return null;
        }
        return mChildren.get(index);
    }

    /**
     * 根据 gid 获取子任务
     */
    public Task getChilTaskByGid(String gid) {
        for (Task task : mChildren) {
            if (task.getGid().equals(gid))
                return task;
        }
        return null;
    }

    /**
     * 获取全部子任务列表
     */
    public ArrayList<Task> getChildTaskList() {
        return this.mChildren;
    }

    /**
     * 设置列表排序索引
     */
    public void setIndex(int index) {
        this.mIndex = index;
    }

    /**
     * 获取列表排序索引
     */
    public int getIndex() {
        return this.mIndex;
    }
}