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

package net.micode.notes.tool;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 * 笔记备份工具类
 *
 * 【类功能】
 * 1. 将所有笔记、文件夹、通话记录批量导出为TXT文件
 * 2. 自动保存到SD卡/内部存储
 * 3. 支持状态返回（SD卡未挂载、成功、系统错误等）
 *
 * 【类间关系】
 * - 被NotesListActivity调用：执行导出备份
 * - 查询NotesProvider：获取笔记数据
 */
public class BackupUtils {
    private static final String TAG = "BackupUtils";
    private static BackupUtils sInstance;

    // 状态常量
    public static final int STATE_SD_CARD_UNMOUONTED = 0;    // SD卡未挂载
    public static final int STATE_BACKUP_FILE_NOT_EXIST = 1; // 备份文件不存在
    public static final int STATE_DATA_DESTROIED = 2;        // 数据损坏
    public static final int STATE_SYSTEM_ERROR = 3;          // 系统错误
    public static final int STATE_SUCCESS = 4;               // 成功

    private TextExport mTextExport;

    private BackupUtils(Context context) {
        mTextExport = new TextExport(context);
    }

    public static synchronized BackupUtils getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BackupUtils(context);
        }
        return sInstance;
    }

    private static boolean externalStorageAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    public int exportToText() {
        return mTextExport.exportToText();
    }

    public String getExportedTextFileName() {
        return mTextExport.mFileName;
    }

    public String getExportedTextFileDir() {
        return mTextExport.mFileDirectory;
    }

    /**
     * 文本导出内部实现类
     */
    private static class TextExport {
        // 笔记表查询字段
        private static final String[] NOTE_PROJECTION = {
                NoteColumns.ID, NoteColumns.MODIFIED_DATE, NoteColumns.SNIPPET, NoteColumns.TYPE
        };
        private static final int NOTE_COLUMN_ID = 0;
        private static final int NOTE_COLUMN_MODIFIED_DATE = 1;
        private static final int NOTE_COLUMN_SNIPPET = 2;

        // 数据表查询字段
        private static final String[] DATA_PROJECTION = {
                DataColumns.CONTENT, DataColumns.MIME_TYPE, DataColumns.DATA1,
                DataColumns.DATA2, DataColumns.DATA3, DataColumns.DATA4,
        };
        private static final int DATA_COLUMN_CONTENT = 0;
        private static final int DATA_COLUMN_MIME_TYPE = 1;
        private static final int DATA_COLUMN_CALL_DATE = 2;
        private static final int DATA_COLUMN_PHONE_NUMBER = 4;

        // 导出格式
        private final String[] TEXT_FORMAT;
        private static final int FORMAT_FOLDER_NAME = 0;
        private static final int FORMAT_NOTE_DATE = 1;
        private static final int FORMAT_NOTE_CONTENT = 2;

        private Context mContext;
        private String mFileName;
        private String mFileDirectory;

        public TextExport(Context context) {
            TEXT_FORMAT = context.getResources().getStringArray(R.array.format_for_exported_note);
            mContext = context;
            mFileName = "";
            mFileDirectory = "";
        }

        private String getFormat(int id) {
            return TEXT_FORMAT[id];
        }

        /**
         * 导出指定文件夹下的所有笔记
         */
        private void exportFolderToText(String folderId, PrintStream ps) {
            Cursor notesCursor = mContext.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION, NoteColumns.PARENT_ID + "=?", new String[]{folderId}, null);

            if (notesCursor != null) {
                if (notesCursor.moveToFirst()) {
                    do {
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                notesCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        exportNoteToText(notesCursor.getString(NOTE_COLUMN_ID), ps);
                    } while (notesCursor.moveToNext());
                }
                notesCursor.close();
            }
        }

        /**
         * 导出单条笔记内容
         */
        private void exportNoteToText(String noteId, PrintStream ps) {
            Cursor dataCursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI,
                    DATA_PROJECTION, DataColumns.NOTE_ID + "=?", new String[]{noteId}, null);

            if (dataCursor != null) {
                if (dataCursor.moveToFirst()) {
                    do {
                        String mimeType = dataCursor.getString(DATA_COLUMN_MIME_TYPE);
                        if (DataConstants.CALL_NOTE.equals(mimeType)) {
                            // 通话笔记
                            String phoneNumber = dataCursor.getString(DATA_COLUMN_PHONE_NUMBER);
                            long callDate = dataCursor.getLong(DATA_COLUMN_CALL_DATE);
                            String location = dataCursor.getString(DATA_COLUMN_CONTENT);

                            if (!TextUtils.isEmpty(phoneNumber)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), phoneNumber));
                            }
                            ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                    DateFormat.format(mContext.getString(R.string.format_datetime_mdhm), callDate)));
                            if (!TextUtils.isEmpty(location)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), location));
                            }
                        } else if (DataConstants.NOTE.equals(mimeType)) {
                            // 普通笔记
                            String content = dataCursor.getString(DATA_COLUMN_CONTENT);
                            if (!TextUtils.isEmpty(content)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), content));
                            }
                        }
                    } while (dataCursor.moveToNext());
                }
                dataCursor.close();
            }
            ps.write(new byte[]{Character.LINE_SEPARATOR, Character.LETTER_NUMBER});
        }

        /**
         * 执行完整导出
         */
        public int exportToText() {
            if (!externalStorageAvailable()) {
                Log.d(TAG, "Media was not mounted");
                return STATE_SD_CARD_UNMOUONTED;
            }

            PrintStream ps = getExportToTextPrintStream();
            if (ps == null) {
                Log.e(TAG, "get print stream error");
                return STATE_SYSTEM_ERROR;
            }

            // 导出文件夹
            Cursor folderCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI, NOTE_PROJECTION,
                    "(" + NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER + " AND "
                            + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER + ") OR "
                            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER, null, null);

            if (folderCursor != null) {
                if (folderCursor.moveToFirst()) {
                    do {
                        String folderName;
                        if (folderCursor.getLong(NOTE_COLUMN_ID) == Notes.ID_CALL_RECORD_FOLDER) {
                            folderName = mContext.getString(R.string.call_record_folder_name);
                        } else {
                            folderName = folderCursor.getString(NOTE_COLUMN_SNIPPET);
                        }
                        if (!TextUtils.isEmpty(folderName)) {
                            ps.println(String.format(getFormat(FORMAT_FOLDER_NAME), folderName));
                        }
                        exportFolderToText(folderCursor.getString(NOTE_COLUMN_ID), ps);
                    } while (folderCursor.moveToNext());
                }
                folderCursor.close();
            }

            // 导出根目录笔记
            Cursor noteCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI, NOTE_PROJECTION,
                    NoteColumns.TYPE + "=" + Notes.TYPE_NOTE + " AND " + NoteColumns.PARENT_ID + "=0", null, null);

            if (noteCursor != null) {
                if (noteCursor.moveToFirst()) {
                    do {
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                noteCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        exportNoteToText(noteCursor.getString(NOTE_COLUMN_ID), ps);
                    } while (noteCursor.moveToNext());
                }
                noteCursor.close();
            }

            ps.close();
            return STATE_SUCCESS;
        }

        /**
         * 创建导出文件并获取输出流
         */
        private PrintStream getExportToTextPrintStream() {
            File file = generateFileMountedOnSDcard(mContext, R.string.file_path, R.string.file_name_txt_format);
            if (file == null) {
                Log.e(TAG, "create file to exported failed");
                return null;
            }
            mFileName = file.getName();
            mFileDirectory = mContext.getString(R.string.file_path);
            try {
                return new PrintStream(new FileOutputStream(file));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    /**
     * 在SD卡上生成备份文件
     */
    private static File generateFileMountedOnSDcard(Context context, int filePathResId, int fileNameFormatResId) {
        StringBuilder sb = new StringBuilder();
        sb.append(Environment.getExternalStorageDirectory());
        sb.append(context.getString(filePathResId));
        File filedir = new File(sb.toString());

        sb.append(context.getString(fileNameFormatResId,
                DateFormat.format(context.getString(R.string.format_date_ymd), System.currentTimeMillis())));
        File file = new File(sb.toString());

        try {
            if (!filedir.exists()) {
                filedir.mkdir();
            }
            if (!file.exists()) {
                file.createNewFile();
            }
            return file;
        } catch (SecurityException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}