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

package net.micode.notes.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * 类名 Contact → 联系人工具类
 * 功能：根据电话号码 查询系统联系人 并返回联系人姓名（带缓存）
 * 依赖：Android 系统联系人 ContentProvider 数据源
 */
public class Contact {
    // 静态缓存：key=电话号码，value=联系人姓名，避免重复查询数据库
    private static HashMap<String, String> sContactCache;

    // 日志TAG：类名 Contact
    private static final String TAG = "Contact";

    /**
     * 电话号码匹配查询语句
     * 从名字与字段可知：
     * CALLER_ID_SELECTION = 来电号码匹配条件
     * 作用：查询系统联系人中 与输入号码匹配的联系人数据
     * 引用系统类：
     * Phone.NUMBER / Data.MIMETYPE / Data.RAW_CONTACT_ID
     * 来自 Android ContactsContract 联系人数据库
     */
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
            + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
            + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    /**
     * 方法名 getContact → 获取联系人
     * 功能：根据电话号码查询系统联系人姓名，先查缓存，再查数据库
     * 系统库查询：ContentResolver.query() 查询联系人数据
     * Ctrl+左键可跳：Data.CONTENT_URI（联系人数据源）、Phone.DISPLAY_NAME（联系人显示名）
     */
    public static String getContact(Context context, String phoneNumber) {
        // 第一次使用时初始化缓存
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 先从缓存获取，命中直接返回
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        // 替换查询条件中的 min_match 值
        // PhoneNumberUtils：Android 系统号码格式化工具类
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));

        // 查询系统联系人数据库
        // Data.CONTENT_URI：系统联系人数据访问地址
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String [] { Phone.DISPLAY_NAME }, // 查询列：联系人显示名称
                selection,                            // 查询条件：号码匹配
                new String[] { phoneNumber },         // 查询参数
                null
        );

        // 游标不为空且移动到第一条数据，说明查询成功
        if (cursor != null && cursor.moveToFirst()) {
            try {
                // 获取第一列：DISPLAY_NAME 联系人姓名
                String name = cursor.getString(0);
                // 存入缓存，下次直接使用
                sContactCache.put(phoneNumber, name);
                return name;
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                // 必须关闭游标，避免泄漏
                cursor.close();
            }
        } else {
            // 无匹配联系人
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}