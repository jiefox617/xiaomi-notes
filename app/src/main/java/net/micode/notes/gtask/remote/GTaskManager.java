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

package net.micode.notes.gtask.remote;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.data.MetaData;
import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.SqlNote;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * Google Tasks同步管理器
 *
 * 【类功能】
 * 整个同步模块的核心调度类：拉取云端任务列表、对比本地与云端差异、执行双向同步、
 * 处理冲突、管理元数据
 *
 * 【类间关系】
 * - 调用GTaskClient：执行网络请求
 * - 调用SqlNote：操作本地数据库
 * - 使用Task/TaskList/MetaData：数据模型
 */
public class GTaskManager {
    private static final String TAG = GTaskManager.class.getSimpleName();

    // 同步状态码
    public static final int STATE_SUCCESS = 0;
    public static final int STATE_NETWORK_ERROR = 1;
    public static final int STATE_INTERNAL_ERROR = 2;
    public static final int STATE_SYNC_IN_PROGRESS = 3;
    public static final int STATE_SYNC_CANCELLED = 4;

    private static GTaskManager mInstance = null;
    private Activity mActivity;
    private Context mContext;
    private ContentResolver mContentResolver;
    private boolean mSyncing;
    private boolean mCancelled;

    // 数据映射
    private HashMap<String, TaskList> mGTaskListHashMap;  // 远程任务列表
    private HashMap<String, Node> mGTaskHashMap;          // 远程节点
    private HashMap<String, MetaData> mMetaHashMap;       // 元数据映射
    private TaskList mMetaList;                            // 元数据列表
    private HashSet<Long> mLocalDeleteIdMap;               // 本地删除ID集合
    private HashMap<String, Long> mGidToNid;               // 远程ID→本地ID
    private HashMap<Long, String> mNidToGid;               // 本地ID→远程ID

    private GTaskManager() {
        mSyncing = false;
        mCancelled = false;
        mGTaskListHashMap = new HashMap<>();
        mGTaskHashMap = new HashMap<>();
        mMetaHashMap = new HashMap<>();
        mMetaList = null;
        mLocalDeleteIdMap = new HashSet<>();
        mGidToNid = new HashMap<>();
        mNidToGid = new HashMap<>();
    }

    public static synchronized GTaskManager getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskManager();
        }
        return mInstance;
    }

    public synchronized void setActivityContext(Activity activity) {
        mActivity = activity;
    }

    /**
     * 同步入口方法
     */
    public int sync(Context context, GTaskASyncTask asyncTask) {
        if (mSyncing) {
            Log.d(TAG, "Sync is in progress");
            return STATE_SYNC_IN_PROGRESS;
        }
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mSyncing = true;
        mCancelled = false;
        clearMaps();

        try {
            GTaskClient client = GTaskClient.getInstance();
            client.resetUpdateArray();

            if (!mCancelled && !client.login(mActivity)) {
                throw new NetworkFailureException("login google task failed");
            }

            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_init_list));
            initGTaskList();

            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_syncing));
            syncContent();
        } catch (NetworkFailureException e) {
            Log.e(TAG, e.toString());
            return STATE_NETWORK_ERROR;
        } catch (ActionFailureException e) {
            Log.e(TAG, e.toString());
            return STATE_INTERNAL_ERROR;
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            return STATE_INTERNAL_ERROR;
        } finally {
            clearMaps();
            mSyncing = false;
        }
        return mCancelled ? STATE_SYNC_CANCELLED : STATE_SUCCESS;
    }

    private void clearMaps() {
        mGTaskListHashMap.clear();
        mGTaskHashMap.clear();
        mMetaHashMap.clear();
        mLocalDeleteIdMap.clear();
        mGidToNid.clear();
        mNidToGid.clear();
    }

    /**
     * 初始化远程任务列表
     */
    private void initGTaskList() throws NetworkFailureException {
        if (mCancelled) return;
        GTaskClient client = GTaskClient.getInstance();

        try {
            JSONArray jsTaskLists = client.getTaskLists();

            // 加载元数据列表
            mMetaList = null;
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                if (name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META)) {
                    mMetaList = new TaskList();
                    mMetaList.setContentByRemoteJSON(object);

                    JSONArray jsMetas = client.getTaskList(gid);
                    for (int j = 0; j < jsMetas.length(); j++) {
                        MetaData metaData = new MetaData();
                        metaData.setContentByRemoteJSON(jsMetas.getJSONObject(j));
                        if (metaData.isWorthSaving()) {
                            mMetaList.addChildTask(metaData);
                            mMetaHashMap.put(metaData.getRelatedGid(), metaData);
                        }
                    }
                }
            }

            // 创建元数据列表（如果不存在）
            if (mMetaList == null) {
                mMetaList = new TaskList();
                mMetaList.setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META);
                client.createTaskList(mMetaList);
            }

            // 加载普通任务列表
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                if (name.startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX)
                        && !name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META)) {
                    TaskList tasklist = new TaskList();
                    tasklist.setContentByRemoteJSON(object);
                    mGTaskListHashMap.put(gid, tasklist);
                    mGTaskHashMap.put(gid, tasklist);

                    JSONArray jsTasks = client.getTaskList(gid);
                    for (int j = 0; j < jsTasks.length(); j++) {
                        Task task = new Task();
                        task.setContentByRemoteJSON(jsTasks.getJSONObject(j));
                        if (task.isWorthSaving()) {
                            task.setMetaInfo(mMetaHashMap.get(task.getGid()));
                            tasklist.addChildTask(task);
                            mGTaskHashMap.put(task.getGid(), task);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            throw new ActionFailureException("initGTaskList: handing JSONObject failed");
        }
    }

    /**
     * 同步所有内容
     */
    private void syncContent() throws NetworkFailureException {
        Cursor c = null;

        // 处理回收站笔记
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id=?)", new String[]{
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, null);
            if (c != null) {
                while (c.moveToNext()) {
                    String gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    Node node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        doContentSync(Node.SYNC_ACTION_DEL_REMOTE, node, c);
                    }
                    mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                }
            }
        } finally {
            if (c != null) c.close();
        }

        // 同步文件夹
        syncFolder();

        // 同步已有笔记
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[]{
                            String.valueOf(Notes.TYPE_NOTE), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    String gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    Node node = mGTaskHashMap.get(gid);
                    int syncType;
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        syncType = node.getSyncAction(c);
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    doContentSync(syncType, node, c);
                }
            }
        } finally {
            if (c != null) c.close();
        }

        // 处理剩余远程节点
        Iterator<Map.Entry<String, Node>> iter = mGTaskHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            doContentSync(Node.SYNC_ACTION_ADD_LOCAL, iter.next().getValue(), null);
        }

        // 批量删除
        if (!mCancelled && !DataUtils.batchDeleteNotes(mContentResolver, mLocalDeleteIdMap)) {
            throw new ActionFailureException("failed to batch-delete local deleted notes");
        }

        // 刷新同步ID
        if (!mCancelled) {
            GTaskClient.getInstance().commitUpdate();
            refreshLocalSyncId();
        }
    }

    /**
     * 同步文件夹
     */
    private void syncFolder() throws NetworkFailureException {
        Cursor c = null;

        // 根文件夹
        try {
            c = mContentResolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                    Notes.ID_ROOT_FOLDER), SqlNote.PROJECTION_NOTE, null, null, null);
            if (c != null && c.moveToNext()) {
                String gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                Node node = mGTaskHashMap.get(gid);
                if (node != null) {
                    mGTaskHashMap.remove(gid);
                    mGidToNid.put(gid, (long) Notes.ID_ROOT_FOLDER);
                    mNidToGid.put((long) Notes.ID_ROOT_FOLDER, gid);
                    if (!node.getName().equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT)) {
                        doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                    }
                } else {
                    doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                }
            }
        } finally {
            if (c != null) c.close();
        }

        // 通话记录文件夹
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(_id=?)", new String[]{String.valueOf(Notes.ID_CALL_RECORD_FOLDER)}, null);
            if (c != null && c.moveToNext()) {
                String gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                Node node = mGTaskHashMap.get(gid);
                if (node != null) {
                    mGTaskHashMap.remove(gid);
                    mGidToNid.put(gid, (long) Notes.ID_CALL_RECORD_FOLDER);
                    mNidToGid.put((long) Notes.ID_CALL_RECORD_FOLDER, gid);
                    if (!node.getName().equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE)) {
                        doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                    }
                } else {
                    doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                }
            }
        } finally {
            if (c != null) c.close();
        }

        // 普通文件夹
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[]{
                            String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    String gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    Node node = mGTaskHashMap.get(gid);
                    int syncType;
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        syncType = node.getSyncAction(c);
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    doContentSync(syncType, node, c);
                }
            }
        } finally {
            if (c != null) c.close();
        }

        // 远程新增文件夹
        Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, TaskList> entry = iter.next();
            if (mGTaskHashMap.containsKey(entry.getKey())) {
                mGTaskHashMap.remove(entry.getKey());
                doContentSync(Node.SYNC_ACTION_ADD_LOCAL, entry.getValue(), null);
            }
        }

        if (!mCancelled) {
            GTaskClient.getInstance().commitUpdate();
        }
    }

    /**
     * 执行具体同步操作
     */
    private void doContentSync(int syncType, Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) return;

        switch (syncType) {
            case Node.SYNC_ACTION_ADD_LOCAL:
                addLocalNode(node);
                break;
            case Node.SYNC_ACTION_ADD_REMOTE:
                addRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_DEL_LOCAL:
                MetaData meta = mMetaHashMap.get(c.getString(SqlNote.GTASK_ID_COLUMN));
                if (meta != null) GTaskClient.getInstance().deleteNode(meta);
                mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                break;
            case Node.SYNC_ACTION_DEL_REMOTE:
                meta = mMetaHashMap.get(node.getGid());
                if (meta != null) GTaskClient.getInstance().deleteNode(meta);
                GTaskClient.getInstance().deleteNode(node);
                break;
            case Node.SYNC_ACTION_UPDATE_LOCAL:
                updateLocalNode(node, c);
                break;
            case Node.SYNC_ACTION_UPDATE_REMOTE:
            case Node.SYNC_ACTION_UPDATE_CONFLICT:
                updateRemoteNode(node, c);
                break;
            case Node.SYNC_ACTION_NONE:
                break;
            default:
                throw new ActionFailureException("unknown sync action type");
        }
    }

    private void addLocalNode(Node node) throws NetworkFailureException {
        if (mCancelled) return;

        SqlNote sqlNote;
        if (node instanceof TaskList) {
            if (node.getName().equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT)) {
                sqlNote = new SqlNote(mContext, Notes.ID_ROOT_FOLDER);
            } else if (node.getName().equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE)) {
                sqlNote = new SqlNote(mContext, Notes.ID_CALL_RECORD_FOLDER);
            } else {
                sqlNote = new SqlNote(mContext);
                sqlNote.setContent(node.getLocalJSONFromContent());
                sqlNote.setParentId(Notes.ID_ROOT_FOLDER);
            }
        } else {
            sqlNote = new SqlNote(mContext);
            removeExistingIds(node);
            sqlNote.setContent(node.getLocalJSONFromContent());
            Long parentId = mGidToNid.get(((Task) node).getParent().getGid());
            if (parentId == null) {
                throw new ActionFailureException("cannot find task's parent id locally");
            }
            sqlNote.setParentId(parentId);
        }

        sqlNote.setGtaskId(node.getGid());
        sqlNote.commit(false);

        mGidToNid.put(node.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), node.getGid());
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    private void removeExistingIds(Node node) {
        try {
            JSONObject js = node.getLocalJSONFromContent();
            if (js.has(GTaskStringUtils.META_HEAD_NOTE)) {
                JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                if (note.has(NoteColumns.ID)) {
                    long id = note.getLong(NoteColumns.ID);
                    if (DataUtils.existInNoteDatabase(mContentResolver, id)) {
                        note.remove(NoteColumns.ID);
                    }
                }
            }
            if (js.has(GTaskStringUtils.META_HEAD_DATA)) {
                JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    if (data.has(DataColumns.ID)) {
                        long dataId = data.getLong(DataColumns.ID);
                        if (DataUtils.existInDataDatabase(mContentResolver, dataId)) {
                            data.remove(DataColumns.ID);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, e.toString());
        }
    }

    private void updateLocalNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) return;

        SqlNote sqlNote = new SqlNote(mContext, c);
        sqlNote.setContent(node.getLocalJSONFromContent());

        Long parentId = (node instanceof Task) ? mGidToNid.get(((Task) node).getParent().getGid())
                : Notes.ID_ROOT_FOLDER;
        if (parentId == null) {
            throw new ActionFailureException("cannot find task's parent id locally");
        }
        sqlNote.setParentId(parentId);
        sqlNote.commit(true);
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    private void addRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) return;

        SqlNote sqlNote = new SqlNote(mContext, c);

        if (sqlNote.isNoteType()) {
            Task task = new Task();
            task.setContentByLocalJSON(sqlNote.getContent());

            String parentGid = mNidToGid.get(sqlNote.getParentId());
            if (parentGid == null) {
                throw new ActionFailureException("cannot find task's parent tasklist");
            }
            mGTaskListHashMap.get(parentGid).addChildTask(task);
            GTaskClient.getInstance().createTask(task);

            updateRemoteMeta(task.getGid(), sqlNote);
            sqlNote.setGtaskId(task.getGid());
        } else {
            String folderName = GTaskStringUtils.MIUI_FOLDER_PREFFIX;
            if (sqlNote.getId() == Notes.ID_ROOT_FOLDER) {
                folderName += GTaskStringUtils.FOLDER_DEFAULT;
            } else if (sqlNote.getId() == Notes.ID_CALL_RECORD_FOLDER) {
                folderName += GTaskStringUtils.FOLDER_CALL_NOTE;
            } else {
                folderName += sqlNote.getSnippet();
            }

            TaskList tasklist = null;
            for (Map.Entry<String, TaskList> entry : mGTaskListHashMap.entrySet()) {
                if (entry.getValue().getName().equals(folderName)) {
                    tasklist = entry.getValue();
                    mGTaskHashMap.remove(entry.getKey());
                    break;
                }
            }

            if (tasklist == null) {
                tasklist = new TaskList();
                tasklist.setContentByLocalJSON(sqlNote.getContent());
                GTaskClient.getInstance().createTaskList(tasklist);
                mGTaskListHashMap.put(tasklist.getGid(), tasklist);
            }
            sqlNote.setGtaskId(tasklist.getGid());
        }

        sqlNote.commit(false);
        sqlNote.resetLocalModified();
        sqlNote.commit(true);

        mGidToNid.put(sqlNote.getGtaskId(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), sqlNote.getGtaskId());
    }

    private void updateRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) return;

        SqlNote sqlNote = new SqlNote(mContext, c);
        node.setContentByLocalJSON(sqlNote.getContent());
        GTaskClient.getInstance().addUpdateNode(node);
        updateRemoteMeta(node.getGid(), sqlNote);

        if (sqlNote.isNoteType()) {
            Task task = (Task) node;
            TaskList preParent = task.getParent();
            String curParentGid = mNidToGid.get(sqlNote.getParentId());
            if (curParentGid == null) {
                throw new ActionFailureException("cannot find task's parent tasklist");
            }
            TaskList curParent = mGTaskListHashMap.get(curParentGid);
            if (preParent != curParent) {
                preParent.removeChildTask(task);
                curParent.addChildTask(task);
                GTaskClient.getInstance().moveTask(task, preParent, curParent);
            }
        }

        sqlNote.resetLocalModified();
        sqlNote.commit(true);
    }

    private void updateRemoteMeta(String gid, SqlNote sqlNote) throws NetworkFailureException {
        if (sqlNote != null && sqlNote.isNoteType()) {
            MetaData metaData = mMetaHashMap.get(gid);
            if (metaData != null) {
                metaData.setMeta(gid, sqlNote.getContent());
                GTaskClient.getInstance().addUpdateNode(metaData);
            } else {
                metaData = new MetaData();
                metaData.setMeta(gid, sqlNote.getContent());
                mMetaList.addChildTask(metaData);
                mMetaHashMap.put(gid, metaData);
                GTaskClient.getInstance().createTask(metaData);
            }
        }
    }

    private void refreshLocalSyncId() throws NetworkFailureException {
        if (mCancelled) return;

        mGTaskHashMap.clear();
        mGTaskListHashMap.clear();
        mMetaHashMap.clear();
        initGTaskList();

        Cursor c = null;
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id<>?)", new String[]{
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    String gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    Node node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SYNC_ID, node.getLastModified());
                        mContentResolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                                c.getLong(SqlNote.ID_COLUMN)), values, null, null);
                    } else {
                        throw new ActionFailureException("some local items don't have gid after sync");
                    }
                }
            }
        } finally {
            if (c != null) c.close();
        }
    }

    public String getSyncAccount() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
            return GTaskClient.getInstance().getSyncAccount().name;
        }
        return "";
    }

    public void cancelSync() {
        mCancelled = true;
    }
}