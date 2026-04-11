/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, the "License";
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

package net.micode.notes.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;
import android.widget.RemoteViews;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NoteEditActivity;
import net.micode.notes.ui.NotesListActivity;

/**
 * 笔记桌面小部件 抽象基类
 * 功能：提供桌面小部件的通用逻辑（更新、删除、点击事件、数据加载）
 * 特点：抽象类，子类只需实现布局、背景、类型即可快速生成不同大小小部件
 */
public abstract class NoteWidgetProvider extends AppWidgetProvider {
    // 查询小部件绑定笔记的字段：ID、背景色、摘要
    public static final String [] PROJECTION = new String [] {
            NoteColumns.ID,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.SNIPPET
    };

    // 列索引定义
    public static final int COLUMN_ID           = 0;
    public static final int COLUMN_BG_COLOR_ID  = 1;
    public static final int COLUMN_SNIPPET      = 2;

    private static final String TAG = "NoteWidgetProvider";

    /**
     * 小部件被删除时回调
     * 作用：清空数据库中对应笔记绑定的 widgetId，解除关联
     */
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        // 遍历被删除的小部件，解除数据库中绑定关系
        for (int i = 0; i < appWidgetIds.length; i++) {
            context.getContentResolver().update(Notes.CONTENT_NOTE_URI,
                    values,
                    NoteColumns.WIDGET_ID + "=?",
                    new String[] { String.valueOf(appWidgetIds[i])});
        }
    }

    /**
     * 查询绑定了当前小部件的笔记信息
     * 过滤：不在垃圾箱中
     */
    private Cursor getNoteWidgetInfo(Context context, int widgetId) {
        return context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.WIDGET_ID + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[] { String.valueOf(widgetId), String.valueOf(Notes.ID_TRASH_FOLER) },
                null);
    }

    /**
     * 更新小部件（对外方法）
     */
    protected void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        update(context, appWidgetManager, appWidgetIds, false);
    }

    /**
     * 真正执行小部件更新的核心方法
     * 支持隐私模式
     */
    private void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds,
                        boolean privacyMode) {
        for (int i = 0; i < appWidgetIds.length; i++) {
            if (appWidgetIds[i] != AppWidgetManager.INVALID_APPWIDGET_ID) {

                // 默认背景、空内容
                int bgId = ResourceParser.getDefaultBgId(context);
                String snippet = "";

                // 构建跳转到编辑页的 Intent
                Intent intent = new Intent(context, NoteEditActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_ID, appWidgetIds[i]);
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_TYPE, getWidgetType());

                // 查询绑定的笔记
                Cursor c = getNoteWidgetInfo(context, appWidgetIds[i]);
                if (c != null && c.moveToFirst()) {
                    // 安全检查：一个小部件只能绑定一条笔记
                    if (c.getCount() > 1) {
                        Log.e(TAG, "Multiple message with same widget id:" + appWidgetIds[i]);
                        c.close();
                        return;
                    }
                    // 获取笔记内容与背景
                    snippet = c.getString(COLUMN_SNIPPET);
                    bgId = c.getInt(COLUMN_BG_COLOR_ID);
                    // 携带笔记ID，跳转到查看页面
                    intent.putExtra(Intent.EXTRA_UID, c.getLong(COLUMN_ID));
                    intent.setAction(Intent.ACTION_VIEW);
                } else {
                    // 无绑定笔记 → 显示空提示
                    snippet = context.getResources().getString(R.string.widget_havenot_content);
                    intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
                }

                if (c != null) {
                    c.close();
                }

                // 加载小部件布局
                RemoteViews rv = new RemoteViews(context.getPackageName(), getLayoutId());
                // 设置背景
                rv.setImageViewResource(R.id.widget_bg_image, getBgResourceId(bgId));
                intent.putExtra(Notes.INTENT_EXTRA_BACKGROUND_ID, bgId);

                // 设置点击跳转意图
                PendingIntent pendingIntent = null;
                if (privacyMode) {
                    // 隐私模式：显示提示文字，点击跳转到列表页
                    rv.setTextViewText(R.id.widget_text,
                            context.getString(R.string.widget_under_visit_mode));
                    pendingIntent = PendingIntent.getActivity(context, appWidgetIds[i], new Intent(
                            context, NotesListActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
                } else {
                    // 正常模式：显示笔记内容，点击跳转到编辑页
                    rv.setTextViewText(R.id.widget_text, snippet);
                    pendingIntent = PendingIntent.getActivity(context, appWidgetIds[i], intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                }

                rv.setOnClickPendingIntent(R.id.widget_text, pendingIntent);
                // 刷新小部件
                appWidgetManager.updateAppWidget(appWidgetIds[i], rv);
            }
        }
    }

    // ==================== 抽象方法，由子类实现 ====================
    /**
     * 获取小部件背景资源（2x/4x不同）
     */
    protected abstract int getBgResourceId(int bgId);

    /**
     * 获取小部件布局ID
     */
    protected abstract int getLayoutId();

    /**
     * 获取小部件类型（区分2x/4x）
     */
    protected abstract int getWidgetType();
}