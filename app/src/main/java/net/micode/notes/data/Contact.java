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

package net.micode.notes.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * 联系人工具类
 *
 * 【类功能】
 * 根据电话号码查询系统通讯录中的联系人姓名，支持缓存机制避免重复查询
 *
 * 【类间关系】
 * - 被NoteItemData调用：显示来电笔记的联系人姓名
 * - 使用PhoneNumberUtils：系统电话号码工具类
 * - 查询Data.CONTENT_URI：系统联系人ContentProvider
 *
 * 【缓存机制】
 * 使用HashMap缓存已查询过的电话号码，避免重复查询数据库
 */
public class Contact {
    private static HashMap<String, String> sContactCache;  // 电话→姓名缓存
    private static final String TAG = "Contact";

    /**
     * 联系人查询SQL条件
     *
     * 功能：匹配电话号码对应的联系人记录
     *
     * 查询逻辑：
     * 1. PHONE_NUMBERS_EQUAL(Phone.NUMBER, ?) - 电话号码精确匹配
     * 2. Data.MIMETYPE = Phone.CONTENT_ITEM_TYPE - 只查电话类型的数据
     * 3. 子查询：在phone_lookup表中通过min_match匹配号码
     *
     * 【系统表说明】
     * - Data.CONTENT_URI：联系人数据表（存储所有类型的数据）
     * - Phone.NUMBER：电话号码字段
     * - Phone.DISPLAY_NAME：联系人显示名称
     * - phone_lookup：号码匹配索引表，用于快速查找
     */
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
            + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
            + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    /**
     * 根据电话号码获取联系人姓名
     *
     * 【查询流程】
     * 1. 先查缓存HashMap，命中则直接返回
     * 2. 未命中则查询系统联系人ContentProvider
     * 3. 查询成功则存入缓存后返回
     * 4. 查询失败返回null
     *
     * @param context 上下文
     * @param phoneNumber 电话号码
     * @return 联系人姓名，未找到返回null
     */
    public static String getContact(Context context, String phoneNumber) {
        // 初始化缓存
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 1. 查缓存
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        // 2. 替换min_match占位符
        // PhoneNumberUtils.toCallerIDMinMatch()：生成号码的最小匹配格式
        // 例如：13812345678 → 5678（后4位用于模糊匹配）
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));

        // 3. 查询系统联系人数据库
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,                              // 联系人数据表
                new String[] { Phone.DISPLAY_NAME },          // 只查姓名
                selection,                                     // 匹配条件
                new String[] { phoneNumber },                  // 参数：电话号码
                null
        );

        // 4. 处理查询结果
        if (cursor != null && cursor.moveToFirst()) {
            try {
                String name = cursor.getString(0);            // 获取联系人姓名
                sContactCache.put(phoneNumber, name);         // 存入缓存
                return name;
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                cursor.close();                                // 关闭游标，防止泄漏
            }
        } else {
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}