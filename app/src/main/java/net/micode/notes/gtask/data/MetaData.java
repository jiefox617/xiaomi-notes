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
import android.util.Log;

import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 类名：MetaData
 * 从名字理解：元数据
 * 继承：Task（父类表示任务/笔记）
 * 功能：专门存储 Google Task 同步的元信息
 * 作用：保存关联的 gid，作为同步标记使用
 */
public class MetaData extends Task {

    /**
     * TAG
     * 从名字理解：类名简称 MetaData
     * 用于日志输出
     */
    private final static String TAG = MetaData.class.getSimpleName();

    /**
     * mRelatedGid
     * 从名字理解：related gid → 关联的谷歌任务ID
     * 作用：记录这条元数据 对应哪个远程 Google Task
     */
    private String mRelatedGid = null;

    /**
     * 方法名：setMeta
     * 从名字理解：设置元数据
     * 功能：将关联的 gid 存入 JSON，再存入 notes 字段
     * 引用资源：GTaskStringUtils 中的 KEY 常量
     */
    public void setMeta(String gid, JSONObject metaInfo) {
        try {
            // 将关联的 gid 存入 JSON 对象
            metaInfo.put(GTaskStringUtils.META_HEAD_GTASK_ID, gid);
        } catch (JSONException e) {
            Log.e(TAG, "failed to put related gid");
        }
        // 把元数据 JSON 存入 notes 字段
        setNotes(metaInfo.toString());
        // 设置固定名称标记，表示这是元数据
        setName(GTaskStringUtils.META_NOTE_NAME);
    }

    /**
     * 方法名：getRelatedGid
     * 从名字理解：获取关联的谷歌任务ID
     */
    public String getRelatedGid() {
        return mRelatedGid;
    }

    /**
     * 方法名：isWorthSaving
     * 重写父类方法
     * 从名字理解：是否值得保存
     * 规则：只要 notes 不为空，就需要保存
     */
    @Override
    public boolean isWorthSaving() {
        return getNotes() != null;
    }

    /**
     * 方法名：setContentByRemoteJSON
     * 重写父类
     * 从名字理解：用远程 JSON 设置内容
     * 功能：从远程返回的 JSON 中解析出关联的 gid
     * 系统库：JSONObject
     */
    @Override
    public void setContentByRemoteJSON(JSONObject js) {
        // 先调用父类设置基础任务信息
        super.setContentByRemoteJSON(js);

        // 如果 notes 元数据不为空
        if (getNotes() != null) {
            try {
                // 解析 notes 里的元数据 JSON
                JSONObject metaInfo = new JSONObject(getNotes().trim());
                // 取出关联的 gid
                mRelatedGid = metaInfo.getString(GTaskStringUtils.META_HEAD_GTASK_ID);
            } catch (JSONException e) {
                Log.w(TAG, "failed to get related gid");
                mRelatedGid = null;
            }
        }
    }

    /**
     * 方法名：setContentByLocalJSON
     * 重写父类
     * 从名字理解：用本地 JSON 设置内容
     * 元数据 不支持本地设置，直接抛异常
     */
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        throw new IllegalAccessError("MetaData:setContentByLocalJSON should not be called");
    }

    /**
     * 方法名：getLocalJSONFromContent
     * 重写父类
     * 从名字理解：从内容生成本地 JSON
     * 元数据 不需要生成本地 JSON，抛异常
     */
    @Override
    public JSONObject getLocalJSONFromContent() {
        throw new IllegalAccessError("MetaData:getLocalJSONFromContent should not be called");
    }

    /**
     * 方法名：getSyncAction
     * 重写父类
     * 从名字理解：获取同步动作
     * 元数据 不需要计算同步动作，抛异常
     */
    @Override
    public int getSyncAction(Cursor c) {
        throw new IllegalAccessError("MetaData:getSyncAction should not be called");
    }

}