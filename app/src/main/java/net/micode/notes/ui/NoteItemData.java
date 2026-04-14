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

package net.micode.notes.ui;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import net.micode.notes.data.Contact;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.DataUtils;

/**
 * 笔记列表项数据实体类
 *
 * 【类功能】
 * 将数据库Cursor中的笔记数据封装为对象，供NotesListActivity的适配器使用
 *
 * 【类间关系】
 * - 被NotesListAdapter调用：创建列表项数据
 * - 调用Contact：获取联系人姓名
 * - 调用DataUtils：获取来电号码
 * - 使用NoteEditActivity常量：过滤清单模式标记
 */
public class NoteItemData {

    // 数据库查询字段
    static final String [] PROJECTION = new String [] {
            NoteColumns.ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.CREATED_DATE,
            NoteColumns.HAS_ATTACHMENT,
            NoteColumns.MODIFIED_DATE,
            NoteColumns.NOTES_COUNT,
            NoteColumns.PARENT_ID,
            NoteColumns.SNIPPET,
            NoteColumns.TYPE,
            NoteColumns.WIDGET_ID,
            NoteColumns.WIDGET_TYPE,
    };

    // 字段索引
    private static final int ID_COLUMN                    = 0;
    private static final int ALERTED_DATE_COLUMN          = 1;
    private static final int BG_COLOR_ID_COLUMN           = 2;
    private static final int CREATED_DATE_COLUMN          = 3;
    private static final int HAS_ATTACHMENT_COLUMN        = 4;
    private static final int MODIFIED_DATE_COLUMN         = 5;
    private static final int NOTES_COUNT_COLUMN           = 6;
    private static final int PARENT_ID_COLUMN             = 7;
    private static final int SNIPPET_COLUMN               = 8;
    private static final int TYPE_COLUMN                  = 9;
    private static final int WIDGET_ID_COLUMN             = 10;
    private static final int WIDGET_TYPE_COLUMN           = 11;

    // 数据字段
    private long mId;
    private long mAlertDate;
    private int mBgColorId;
    private long mCreatedDate;
    private boolean mHasAttachment;
    private long mModifiedDate;
    private int mNotesCount;
    private long mParentId;
    private String mSnippet;
    private int mType;
    private int mWidgetId;
    private int mWidgetType;
    private String mName;
    private String mPhoneNumber;

    // 位置状态
    private boolean mIsLastItem;
    private boolean mIsFirstItem;
    private boolean mIsOnlyOneItem;
    private boolean mIsOneNoteFollowingFolder;
    private boolean mIsMultiNotesFollowingFolder;

    /**
     * 构造方法：从Cursor读取数据封装
     */
    public NoteItemData(Context context, Cursor cursor) {
        // 读取基础字段
        mId = cursor.getLong(ID_COLUMN);
        mAlertDate = cursor.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = cursor.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = cursor.getLong(CREATED_DATE_COLUMN);
        mHasAttachment = cursor.getInt(HAS_ATTACHMENT_COLUMN) > 0;
        mModifiedDate = cursor.getLong(MODIFIED_DATE_COLUMN);
        mNotesCount = cursor.getInt(NOTES_COUNT_COLUMN);
        mParentId = cursor.getLong(PARENT_ID_COLUMN);
        mSnippet = cursor.getString(SNIPPET_COLUMN);

        // 移除清单标记（✓/□）
        mSnippet = mSnippet.replace(NoteEditActivity.TAG_CHECKED, "")
                .replace(NoteEditActivity.TAG_UNCHECKED, "");

        mType = cursor.getInt(TYPE_COLUMN);
        mWidgetId = cursor.getInt(WIDGET_ID_COLUMN);
        mWidgetType = cursor.getInt(WIDGET_TYPE_COLUMN);
        mPhoneNumber = "";

        // 来电记录：加载联系人信息
        if (mParentId == Notes.ID_CALL_RECORD_FOLDER) {
            mPhoneNumber = DataUtils.getCallNumberByNoteId(context.getContentResolver(), mId);
            if (!TextUtils.isEmpty(mPhoneNumber)) {
                mName = Contact.getContact(context, mPhoneNumber);
                if (mName == null) {
                    mName = mPhoneNumber;
                }
            }
        }

        if (mName == null) {
            mName = "";
        }

        checkPostion(cursor);
    }

    /**
     * 检查位置状态
     * 判断是否为第一项/最后一项/唯一一项/文件夹后的笔记
     */
    private void checkPostion(Cursor cursor) {
        mIsLastItem = cursor.isLast();
        mIsFirstItem = cursor.isFirst();
        mIsOnlyOneItem = cursor.getCount() == 1;
        mIsMultiNotesFollowingFolder = false;
        mIsOneNoteFollowingFolder = false;

        if (mType == Notes.TYPE_NOTE && !mIsFirstItem) {
            int position = cursor.getPosition();
            if (cursor.moveToPrevious()) {
                int prevType = cursor.getInt(TYPE_COLUMN);
                if (prevType == Notes.TYPE_FOLDER || prevType == Notes.TYPE_SYSTEM) {
                    if (cursor.getCount() > position + 1) {
                        mIsMultiNotesFollowingFolder = true;
                    } else {
                        mIsOneNoteFollowingFolder = true;
                    }
                }
                cursor.moveToNext();
            }
        }
    }

    // Getter方法
    public boolean isOneFollowingFolder() { return mIsOneNoteFollowingFolder; }
    public boolean isMultiFollowingFolder() { return mIsMultiNotesFollowingFolder; }
    public boolean isLast() { return mIsLastItem; }
    public String getCallName() { return mName; }
    public boolean isFirst() { return mIsFirstItem; }
    public boolean isSingle() { return mIsOnlyOneItem; }
    public long getId() { return mId; }
    public long getAlertDate() { return mAlertDate; }
    public long getCreatedDate() { return mCreatedDate; }
    public boolean hasAttachment() { return mHasAttachment; }
    public long getModifiedDate() { return mModifiedDate; }
    public int getBgColorId() { return mBgColorId; }
    public long getParentId() { return mParentId; }
    public int getNotesCount() { return mNotesCount; }
    public long getFolderId() { return mParentId; }
    public int getType() { return mType; }
    public int getWidgetType() { return mWidgetType; }
    public int getWidgetId() { return mWidgetId; }
    public String getSnippet() { return mSnippet; }
    public boolean isTop() { return false; }  // 置顶功能预留
    public boolean hasAlert() { return mAlertDate > 0; }

    public boolean isCallRecord() {
        return mParentId == Notes.ID_CALL_RECORD_FOLDER && !TextUtils.isEmpty(mPhoneNumber);
    }

    /**
     * 静态方法：获取笔记类型
     */
    public static int getNoteType(Cursor cursor) {
        return cursor.getInt(TYPE_COLUMN);
    }
}