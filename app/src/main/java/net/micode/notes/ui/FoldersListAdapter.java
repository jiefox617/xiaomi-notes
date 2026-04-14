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
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 文件夹列表适配器
 * 功能：将数据库中的文件夹数据绑定到ListView，用于移动笔记时选择目标文件夹
 *
 * 类间关系：
 * - 被MoveNoteActivity调用：显示文件夹列表供用户选择
 * - 使用Notes.CONTENT_NOTE_URI：查询文件夹数据
 * - 继承CursorAdapter：自动管理数据库游标
 */
public class FoldersListAdapter extends CursorAdapter {
    // 查询文件夹所需的字段
    public static final String [] PROJECTION = {
            NoteColumns.ID,           // 文件夹ID
            NoteColumns.SNIPPET       // 文件夹名称
    };

    // 列索引
    public static final int ID_COLUMN   = 0;
    public static final int NAME_COLUMN = 1;

    /**
     * 构造方法
     * @param context 上下文
     * @param c 文件夹数据游标（查询结果）
     */
    public FoldersListAdapter(Context context, Cursor c) {
        super(context, c);
    }

    /**
     * 创建新的列表项视图
     * 返回自定义的FolderListItem视图
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new FolderListItem(context);
    }

    /**
     * 绑定数据到视图
     * 特殊处理：根文件夹显示为"返回上级目录"
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof FolderListItem) {
            // 判断是否为根文件夹（ID为-1）
            String folderName = (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER)
                    ? context.getString(R.string.menu_move_parent_folder)  // "上级目录"
                    : cursor.getString(NAME_COLUMN);
            ((FolderListItem) view).bind(folderName);
        }
    }

    /**
     * 根据位置获取文件夹名称
     * 供外部调用获取用户选择的文件夹名
     */
    public String getFolderName(Context context, int position) {
        Cursor cursor = (Cursor) getItem(position);
        return (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER)
                ? context.getString(R.string.menu_move_parent_folder)
                : cursor.getString(NAME_COLUMN);
    }

    /**
     * 自定义文件夹列表项视图
     * 包含一个TextView显示文件夹名称
     */
    private class FolderListItem extends LinearLayout {
        private TextView mName;

        public FolderListItem(Context context) {
            super(context);
            inflate(context, R.layout.folder_list_item, this);
            mName = (TextView) findViewById(R.id.tv_folder_name);
        }

        /**
         * 绑定文件夹名称
         */
        public void bind(String name) {
            mName.setText(name);
        }
    }
}