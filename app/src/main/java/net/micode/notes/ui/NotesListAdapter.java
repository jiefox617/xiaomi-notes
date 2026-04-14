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

package net.micode.notes.ui;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import net.micode.notes.data.Notes;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 * 笔记列表适配器
 *
 * 【类功能】
 * 将数据库中的笔记/文件夹数据绑定到ListView，支持多选模式、全选、获取选中项
 *
 * 【类间关系】
 * - 被NotesListActivity使用：提供列表数据
 * - 创建NotesListItem：列表项视图
 * - 使用NoteItemData：封装Cursor数据
 */
public class NotesListAdapter extends CursorAdapter {
    private static final String TAG = "NotesListAdapter";

    private Context mContext;
    private HashMap<Integer, Boolean> mSelectedIndex;  // 选中状态存储
    private int mNotesCount;    // 笔记总数（不含文件夹）
    private boolean mChoiceMode;  // 多选模式

    /**
     * 小部件属性
     */
    public static class AppWidgetAttribute {
        public int widgetId;
        public int widgetType;
    }

    public NotesListAdapter(Context context) {
        super(context, null);
        mSelectedIndex = new HashMap<>();
        mContext = context;
        mNotesCount = 0;
    }

    /**
     * 创建列表项视图
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new NotesListItem(context);
    }

    /**
     * 绑定数据到视图
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof NotesListItem) {
            NoteItemData itemData = new NoteItemData(context, cursor);
            ((NotesListItem) view).bind(context, itemData, mChoiceMode,
                    isSelectedItem(cursor.getPosition()));
        }
    }

    /**
     * 设置某项的选中状态
     */
    public void setCheckedItem(final int position, final boolean checked) {
        mSelectedIndex.put(position, checked);
        notifyDataSetChanged();
    }

    public boolean isInChoiceMode() {
        return mChoiceMode;
    }

    /**
     * 设置多选模式（清空选中状态）
     */
    public void setChoiceMode(boolean mode) {
        mSelectedIndex.clear();
        mChoiceMode = mode;
    }

    /**
     * 全选/取消全选（仅对笔记类型生效）
     */
    public void selectAll(boolean checked) {
        Cursor cursor = getCursor();
        for (int i = 0; i < getCount(); i++) {
            if (cursor.moveToPosition(i)) {
                if (NoteItemData.getNoteType(cursor) == Notes.TYPE_NOTE) {
                    setCheckedItem(i, checked);
                }
            }
        }
    }

    /**
     * 获取所有选中项的ID
     */
    public HashSet<Long> getSelectedItemIds() {
        HashSet<Long> itemSet = new HashSet<>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position)) {
                Long id = getItemId(position);
                if (id != Notes.ID_ROOT_FOLDER) {
                    itemSet.add(id);
                }
            }
        }
        return itemSet;
    }

    /**
     * 获取选中笔记的小部件信息
     */
    public HashSet<AppWidgetAttribute> getSelectedWidget() {
        HashSet<AppWidgetAttribute> itemSet = new HashSet<>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position)) {
                Cursor c = (Cursor) getItem(position);
                if (c != null) {
                    AppWidgetAttribute widget = new AppWidgetAttribute();
                    NoteItemData item = new NoteItemData(mContext, c);
                    widget.widgetId = item.getWidgetId();
                    widget.widgetType = item.getWidgetType();
                    itemSet.add(widget);
                }
            }
        }
        return itemSet;
    }

    /**
     * 获取选中项数量
     */
    public int getSelectedCount() {
        int count = 0;
        for (Boolean selected : mSelectedIndex.values()) {
            if (selected) count++;
        }
        return count;
    }

    /**
     * 是否全选
     */
    public boolean isAllSelected() {
        int checkedCount = getSelectedCount();
        return checkedCount != 0 && checkedCount == mNotesCount;
    }

    /**
     * 判断某位置是否选中
     */
    public boolean isSelectedItem(final int position) {
        return mSelectedIndex.get(position) != null && mSelectedIndex.get(position);
    }

    @Override
    protected void onContentChanged() {
        super.onContentChanged();
        calcNotesCount();
    }

    @Override
    public void changeCursor(Cursor cursor) {
        super.changeCursor(cursor);
        calcNotesCount();
    }

    /**
     * 统计笔记数量（用于全选判断）
     */
    private void calcNotesCount() {
        mNotesCount = 0;
        for (int i = 0; i < getCount(); i++) {
            Cursor c = (Cursor) getItem(i);
            if (c != null && NoteItemData.getNoteType(c) == Notes.TYPE_NOTE) {
                mNotesCount++;
            }
        }
    }

    /**
     * 设置搜索关键词（高亮）
     */
    public void setSearchKeyword(String keyword) {
        NotesListItem.setSearchKeyword(keyword);
        notifyDataSetChanged();
    }
}