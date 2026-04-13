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

import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 元数据类
 *
 * 【类功能】
 * 专门存储Google Tasks同步的元信息，用于记录笔记与远程任务的关联关系。
 * 每个需要同步的笔记都会对应一个MetaData对象，保存其远程GTask ID。
 *
 * 【类间关系】
 * - 继承Task：复用Task的基础字段（name、notes、deleted等）
 * - 被GTaskManager使用：管理同步元数据
 * - 使用GTaskStringUtils：引用JSON键名常量
 *
 * 【存储结构】
 * notes字段存储JSON格式的元数据：{"gid":"远程任务ID"}
 */
public class MetaData extends Task {

    private final static String TAG = MetaData.class.getSimpleName();
    private String mRelatedGid = null;   // 关联的Google Tasks远程ID

    /**
     * 设置元数据
     *
     * 【功能】
     * 将远程GTask ID存入JSON，再存入notes字段
     *
     * @param gid 远程Google Tasks ID
     * @param metaInfo 元数据JSON对象（可为空，方法内部会填充）
     */
    public void setMeta(String gid, JSONObject metaInfo) {
        try {
            metaInfo.put(GTaskStringUtils.META_HEAD_GTASK_ID, gid);
        } catch (JSONException e) {
            Log.e(TAG, "failed to put related gid");
        }
        setNotes(metaInfo.toString());
        setName(GTaskStringUtils.META_NOTE_NAME);  // 固定名称标记为元数据
    }

    /**
     * 获取关联的远程GTask ID
     */
    public String getRelatedGid() {
        return mRelatedGid;
    }

    /**
     * 判断是否值得保存
     * 元数据只要有notes内容就需要保存
     */
    @Override
    public boolean isWorthSaving() {
        return getNotes() != null;
    }

    /**
     * 从远程JSON设置内容
     *
     * 【功能】
     * 解析远程返回的JSON，提取notes字段中的关联gid
     *
     * @param js 远程返回的JSON对象
     */
    @Override
    public void setContentByRemoteJSON(JSONObject js) {
        super.setContentByRemoteJSON(js);

        if (getNotes() != null) {
            try {
                JSONObject metaInfo = new JSONObject(getNotes().trim());
                mRelatedGid = metaInfo.getString(GTaskStringUtils.META_HEAD_GTASK_ID);
            } catch (JSONException e) {
                Log.w(TAG, "failed to get related gid");
                mRelatedGid = null;
            }
        }
    }

    /**
     * 从本地JSON设置内容
     * 元数据不支持此操作
     */
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        throw new IllegalAccessError("MetaData:setContentByLocalJSON should not be called");
    }

    /**
     * 从内容生成本地JSON
     * 元数据不支持此操作
     */
    @Override
    public JSONObject getLocalJSONFromContent() {
        throw new IllegalAccessError("MetaData:getLocalJSONFromContent should not be called");
    }

    /**
     * 获取同步动作
     * 元数据不需要计算同步动作
     */
    @Override
    public int getSyncAction(Cursor c) {
        throw new IllegalAccessError("MetaData:getSyncAction should not be called");
    }
}