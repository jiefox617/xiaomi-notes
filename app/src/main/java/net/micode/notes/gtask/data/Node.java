/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.gtask.data;

import android.database.Cursor;

import org.json.JSONObject;

/**
 * 类名：Node
 * 从名字理解：节点 = 数据同步的基础单元
 * 定义：抽象类
 * 作用：定义 Google Task 同步的公共属性与行为
 * 子类：Task、MetaData 都继承此类
 * 属于：gtask 同步模块的数据基类
 */
public abstract class Node {

    /**
     * 同步动作常量定义
     * 从名字理解：SYNC_ACTION = 同步要执行的操作
     * 作用：标记本地/远程数据该如何同步
     */

    /** 无操作 */
    public static final int SYNC_ACTION_NONE = 0;

    /** 需要添加到远程（Google Task） */
    public static final int SYNC_ACTION_ADD_REMOTE = 1;

    /** 需要添加到本地（数据库 note 表） */
    public static final int SYNC_ACTION_ADD_LOCAL = 2;

    /** 需要删除远程任务 */
    public static final int SYNC_ACTION_DEL_REMOTE = 3;

    /** 需要删除本地数据 */
    public static final int SYNC_ACTION_DEL_LOCAL = 4;

    /** 需要更新远程任务 */
    public static final int SYNC_ACTION_UPDATE_REMOTE = 5;

    /** 需要更新本地数据 */
    public static final int SYNC_ACTION_UPDATE_LOCAL = 6;

    /** 同步冲突，需要用户处理 */
    public static final int SYNC_ACTION_UPDATE_CONFLICT = 7;

    /** 同步出错 */
    public static final int SYNC_ACTION_ERROR = 8;

    /**
     * mGid
     * 从名字理解：Google Task 远程唯一 ID
     * 作用：标识这条数据对应云端哪个任务
     */
    private String mGid;

    /**
     * mName
     * 从名字理解：节点名称
     * 对应：任务标题 / 笔记标题
     */
    private String mName;

    /**
     * mLastModified
     * 从名字理解：最后修改时间
     * 作用：同步时判断谁更新
     */
    private long mLastModified;

    /**
     * mDeleted
     * 从名字理解：是否已删除
     * 作用：标记删除状态，用于同步删除
     */
    private boolean mDeleted;

    /**
     * 构造方法 Node()
     * 作用：初始化同步节点的默认值
     */
    public Node() {
        mGid = null;
        mName = "";
        mLastModified = 0;
        mDeleted = false;
    }

    // ==================== 抽象方法 ====================

    /**
     * 方法名：getCreateAction
     * 从名字理解：获取创建任务的 JSON 动作
     * 作用：生成用于提交给 Google Task 的创建请求
     */
    public abstract JSONObject getCreateAction(int actionId);

    /**
     * 方法名：getUpdateAction
     * 从名字理解：获取更新任务的 JSON 动作
     * 作用：生成用于提交给 Google Task 的更新请求
     */
    public abstract JSONObject getUpdateAction(int actionId);

    /**
     * 方法名：setContentByRemoteJSON
     * 从名字理解：用远程 JSON 设置节点内容
     * 作用：从 Google Task 返回的数据解析到本地对象
     */
    public abstract void setContentByRemoteJSON(JSONObject js);

    /**
     * 方法名：setContentByLocalJSON
     * 从名字理解：用本地 JSON 设置节点内容
     * 作用：从数据库数据解析到节点对象
     */
    public abstract void setContentByLocalJSON(JSONObject js);

    /**
     * 方法名：getLocalJSONFromContent
     * 从名字理解：从内容生成本地 JSON
     * 作用：将对象转为可存入数据库的格式
     */
    public abstract JSONObject getLocalJSONFromContent();

    /**
     * 方法名：getSyncAction
     * 从名字理解：获取同步动作
     * 参数：Cursor 本地数据库游标
     * 作用：对比本地与远程，返回该执行哪种同步
     */
    public abstract int getSyncAction(Cursor c);

    // ==================== GET/SET 方法 ====================

    /** 设置 Google Task 任务 ID */
    public void setGid(String gid) {
        this.mGid = gid;
    }

    /** 设置节点名称 */
    public void setName(String name) {
        this.mName = name;
    }

    /** 设置最后修改时间 */
    public void setLastModified(long lastModified) {
        this.mLastModified = lastModified;
    }

    /** 设置删除标记 */
    public void setDeleted(boolean deleted) {
        this.mDeleted = deleted;
    }

    /** 获取 Google Task 任务 ID */
    public String getGid() {
        return this.mGid;
    }

    /** 获取节点名称 */
    public String getName() {
        return this.mName;
    }

    /** 获取最后修改时间 */
    public long getLastModified() {
        return this.mLastModified;
    }

    /** 获取删除状态 */
    public boolean getDeleted() {
        return this.mDeleted;
    }

}