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
 * 4x 大尺寸笔记桌面小部件实现类
 * 继承自抽象基类 NoteWidgetProvider
 * 仅实现自身特有的布局、背景资源、类型标识
 * 所有核心逻辑由父类统一处理
 */
public class NoteWidgetProvider_4x extends NoteWidgetProvider {

    /**
     * 系统触发小部件更新时调用
     * 调用父类的 update 方法完成实际更新逻辑
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.update(context, appWidgetManager, appWidgetIds);
    }

    /**
     * 返回 4x 小部件对应的布局文件 ID
     */
    @Override
    protected int getLayoutId() {
        return R.layout.widget_4x;
    }

    /**
     * 返回 4x 小部件对应的背景图片资源
     */
    @Override
    protected int getBgResourceId(int bgId) {
        return ResourceParser.WidgetBgResources.getWidget4xBgResource(bgId);
    }

    /**
     * 返回当前小部件的类型：4x 型号
     */
    @Override
    protected int getWidgetType() {
        return Notes.TYPE_WIDGET_4X;
    }
}