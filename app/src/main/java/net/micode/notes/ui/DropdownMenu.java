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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import net.micode.notes.R;

/**
 * 下拉菜单封装类
 * 功能：将Button封装成带弹出菜单的下拉控件，简化菜单创建逻辑
 *
 * 类间关系：
 * - 被NoteEditActivity使用：提供编辑界面的更多操作菜单
 * - 使用PopupMenu：Android原生弹出菜单
 * - 使用Button：作为菜单触发器
 */
public class DropdownMenu {
    private Button mButton;           // 触发菜单的按钮
    private PopupMenu mPopupMenu;     // 弹出菜单
    private Menu mMenu;               // 菜单对象

    /**
     * 构造方法：初始化按钮和弹出菜单
     * @param context 上下文
     * @param button 绑定的按钮
     * @param menuId 菜单布局资源ID
     */
    public DropdownMenu(Context context, Button button, int menuId) {
        mButton = button;
        mButton.setBackgroundResource(R.drawable.dropdown_icon);  // 设置下拉箭头样式

        mPopupMenu = new PopupMenu(context, mButton);
        mMenu = mPopupMenu.getMenu();
        mPopupMenu.getMenuInflater().inflate(menuId, mMenu);

        // 按钮点击时显示菜单
        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mPopupMenu.show();
            }
        });
    }

    /**
     * 设置菜单项点击监听器
     * 调用方（如NoteEditActivity）通过此方法处理菜单点击事件
     */
    public void setOnDropdownMenuItemClickListener(OnMenuItemClickListener listener) {
        if (mPopupMenu != null) {
            mPopupMenu.setOnMenuItemClickListener(listener);
        }
    }

    /**
     * 根据ID查找菜单项
     * 用于动态修改菜单项属性（如设置可见性、更改文字等）
     */
    public MenuItem findItem(int id) {
        return mMenu.findItem(id);
    }

    /**
     * 设置按钮显示文字
     */
    public void setTitle(CharSequence title) {
        mButton.setText(title);
    }
}