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
 * 笔记桌面小部件抽象基类
 *
 * 【类功能】
 * 提供桌面小部件的通用逻辑：更新、删除、点击事件、数据加载
 * 子类只需实现布局、背景、类型即可生成不同大小的小部件
 *
 * 【类间关系】
 * - 被NoteWidgetProvider_2x/4x继承：实现具体尺寸的小部件
 * - 被NoteEditActivity调用：更新小部件内容
 * - 查询NotesProvider：获取绑定的笔记数据
 */
public abstract class NoteWidgetProvider extends AppWidgetProvider {

    // 查询字段
    public static final String[] PROJECTION = new String[]{
            NoteColumns.ID, NoteColumns.BG_COLOR_ID, NoteColumns.SNIPPET
    };
    public static final int COLUMN_ID = 0;
    public static final int COLUMN_BG_COLOR_ID = 1;
    public static final int COLUMN_SNIPPET = 2;

    private static final String TAG = "NoteWidgetProvider";

    /**
     * 小部件被删除时回调：解除数据库中的绑定关系
     */
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        for (int id : appWidgetIds) {
            context.getContentResolver().update(Notes.CONTENT_NOTE_URI, values,
                    NoteColumns.WIDGET_ID + "=?", new String[]{String.valueOf(id)});
        }
    }

    /**
     * 查询绑定了当前小部件的笔记
     */
    private Cursor getNoteWidgetInfo(Context context, int widgetId) {
        return context.getContentResolver().query(Notes.CONTENT_NOTE_URI, PROJECTION,
                NoteColumns.WIDGET_ID + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[]{String.valueOf(widgetId), String.valueOf(Notes.ID_TRASH_FOLER)}, null);
    }

    protected void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        update(context, appWidgetManager, appWidgetIds, false);
    }

    /**
     * 核心更新方法
     * @param privacyMode 隐私模式（隐藏内容）
     */
    private void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds,
                        boolean privacyMode) {
        for (int id : appWidgetIds) {
            if (id == AppWidgetManager.INVALID_APPWIDGET_ID) continue;

            int bgId = ResourceParser.getDefaultBgId(context);
            String snippet = "";

            // 构建跳转Intent
            Intent intent = new Intent(context, NoteEditActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra(Notes.INTENT_EXTRA_WIDGET_ID, id);
            intent.putExtra(Notes.INTENT_EXTRA_WIDGET_TYPE, getWidgetType());

            // 查询绑定的笔记
            Cursor c = getNoteWidgetInfo(context, id);
            if (c != null && c.moveToFirst()) {
                if (c.getCount() > 1) {
                    Log.e(TAG, "Multiple message with same widget id:" + id);
                    c.close();
                    return;
                }
                snippet = c.getString(COLUMN_SNIPPET);
                bgId = c.getInt(COLUMN_BG_COLOR_ID);
                intent.putExtra(Intent.EXTRA_UID, c.getLong(COLUMN_ID));
                intent.setAction(Intent.ACTION_VIEW);
            } else {
                snippet = context.getString(R.string.widget_havenot_content);
                intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
            }
            if (c != null) c.close();

            // 构建RemoteViews
            RemoteViews rv = new RemoteViews(context.getPackageName(), getLayoutId());
            rv.setImageViewResource(R.id.widget_bg_image, getBgResourceId(bgId));
            intent.putExtra(Notes.INTENT_EXTRA_BACKGROUND_ID, bgId);

            // 设置点击事件
            PendingIntent pendingIntent;
            if (privacyMode) {
                rv.setTextViewText(R.id.widget_text, context.getString(R.string.widget_under_visit_mode));
                pendingIntent = PendingIntent.getActivity(context, id,
                        new Intent(context, NotesListActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
            } else {
                rv.setTextViewText(R.id.widget_text, snippet);
                pendingIntent = PendingIntent.getActivity(context, id, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT);
            }
            rv.setOnClickPendingIntent(R.id.widget_text, pendingIntent);
            appWidgetManager.updateAppWidget(id, rv);
        }
    }

    // ==================== 抽象方法 ====================
    protected abstract int getBgResourceId(int bgId);
    protected abstract int getLayoutId();
    protected abstract int getWidgetType();
}