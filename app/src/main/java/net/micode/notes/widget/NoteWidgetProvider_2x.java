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

package net.micode.notes.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.ResourceParser;

/**
 * 2x 尺寸笔记桌面小部件
 * 继承自抽象基类 NoteWidgetProvider
 * 仅实现具体布局、背景、类型，业务逻辑全部由父类完成
 */
public class NoteWidgetProvider_2x extends NoteWidgetProvider {

    /**
     * 系统刷新小部件时调用
     * 直接调用父类的更新逻辑
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.update(context, appWidgetManager, appWidgetIds);
    }

    /**
     * 返回 2x 小部件对应的布局文件
     */
    @Override
    protected int getLayoutId() {
        return R.layout.widget_2x;
    }

    /**
     * 返回 2x 小部件对应的背景资源
     */
    @Override
    protected int getBgResourceId(int bgId) {
        return ResourceParser.WidgetBgResources.getWidget2xBgResource(bgId);
    }

    /**
     * 返回小部件类型：2x 型号
     */
    @Override
    protected int getWidgetType() {
        return Notes.TYPE_WIDGET_2X;
    }
}