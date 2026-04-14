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
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser.NoteItemBgResources;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 笔记列表项自定义布局
 *
 * 【类功能】
 * 1. 显示笔记或文件夹的单个列表项
 * 2. 支持四种显示类型：普通笔记、通话记录笔记、普通文件夹、通话记录文件夹
 * 3. 支持多选模式（显示复选框）
 * 4. 支持搜索关键词高亮显示（红色）
 * 5. 根据列表位置自动设置圆角背景（顶部圆角、底部圆角、四角圆角、无圆角）
 *
 * 【类间关系】
 * - 被 NotesListAdapter 创建并调用 bind() 方法绑定数据
 * - 使用 NoteItemData 获取笔记的所有数据字段
 * - 使用 DataUtils.getFormattedSnippet() 格式化摘要文本
 * - 使用 NoteItemBgResources 获取不同位置对应的背景资源ID
 * - 与 NoteEditActivity 共享 TAG_CHECKED/TAG_UNCHECKED 常量用于过滤清单标记
 *
 * 【布局文件】
 * - R.layout.note_item：包含闹钟图标、标题、时间、联系人、复选框
 */
public class NotesListItem extends LinearLayout {

    // ====================== UI组件 ======================
    private final ImageView mAlert;      // 闹钟提醒图标（时钟图标）/ 通话记录图标
    private final TextView mTitle;       // 笔记标题/摘要文本/文件夹名称
    private final TextView mTime;        // 最后修改时间（相对时间格式）
    private final TextView mCallName;    // 通话记录笔记的联系人姓名
    private NoteItemData mItemData;      // 当前列表项的数据模型
    private final CheckBox mCheckBox;    // 多选模式下的复选框

    // ====================== 搜索高亮 ======================
    // 静态变量：所有列表项共享同一个搜索关键词
    // 当用户在搜索框输入关键词时，NotesListAdapter.setSearchKeyword() 会调用此方法
    // 然后 notifyDataSetChanged() 刷新列表，所有匹配的关键词都会标红
    private static String sSearchKeyword = "";

    /**
     * 设置搜索关键词（静态方法）
     * 供 NotesListAdapter 调用，设置当前搜索的关键词
     * 所有列表项都会使用这个关键词进行高亮匹配
     *
     * @param keyword 搜索关键词，如："笔记"、"重要"等
     */
    public static void setSearchKeyword(String keyword) {
        sSearchKeyword = keyword;
    }

    /**
     * 构造函数
     * 加载 note_item.xml 布局文件并初始化UI组件
     *
     * @param context 上下文对象（通常是 NotesListActivity）
     */
    public NotesListItem(Context context) {
        super(context);
        // 加载布局文件，将 note_item.xml 解析并添加到当前 LinearLayout 中
        inflate(context, R.layout.note_item, this);

        // 初始化UI组件
        mAlert = (ImageView) findViewById(R.id.iv_alert_icon);      // 闹钟/通话图标
        mTitle = (TextView) findViewById(R.id.tv_title);            // 标题/摘要
        mTime = (TextView) findViewById(R.id.tv_time);              // 修改时间
        mCallName = (TextView) findViewById(R.id.tv_name);          // 通话联系人姓名

        // android.R.id.checkbox 是系统内置的复选框ID
        // 在 note_item.xml 中声明了 CheckBox 并使用此ID
        mCheckBox = (CheckBox) findViewById(android.R.id.checkbox);
    }

    /**
     * 绑定数据到列表项
     * 这是整个类的核心方法，根据笔记类型和状态设置UI显示
     *
     * 显示逻辑分支：
     * 1. 通话记录文件夹（ID = ID_CALL_RECORD_FOLDER）→ 显示"通话记录 (数量)"
     * 2. 通话记录笔记（parentId = ID_CALL_RECORD_FOLDER）→ 显示联系人+摘要+闹钟图标
     * 3. 普通文件夹（TYPE_FOLDER）→ 显示"文件夹名 (数量)"
     * 4. 普通笔记（TYPE_NOTE）→ 显示摘要+闹钟图标
     *
     * @param context    上下文
     * @param data       笔记数据对象（包含ID、类型、内容、时间、闹钟等所有字段）
     * @param choiceMode 是否处于多选模式（true=显示复选框，false=隐藏复选框）
     * @param checked    当前项是否被选中（仅多选模式下有效）
     */
    public void bind(Context context, NoteItemData data, boolean choiceMode, boolean checked) {

        // ====================== 1. 处理多选模式复选框 ======================
        // 多选模式 && 当前项是笔记类型（文件夹不能被选中删除）→ 显示复选框
        if (choiceMode && data.getType() == Notes.TYPE_NOTE) {
            mCheckBox.setVisibility(View.VISIBLE);   // 显示复选框
            mCheckBox.setChecked(checked);            // 根据传入的checked状态设置勾选
        } else {
            mCheckBox.setVisibility(View.GONE);       // 非多选模式或文件夹：隐藏复选框
        }

        // 保存数据对象，供外部 getItemData() 获取
        mItemData = data;

        // ====================== 2. 根据笔记类型设置显示内容 ======================

        // ---------- 情况1：通话记录文件夹（特殊的系统文件夹）----------
        // Notes.ID_CALL_RECORD_FOLDER 是常量，值为 -2，表示通话记录文件夹
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            // 通话记录文件夹不需要显示联系人姓名
            mCallName.setVisibility(View.GONE);

            // 显示闹钟图标区域（这里实际显示的是通话记录图标，复用同一个ImageView）
            mAlert.setVisibility(View.VISIBLE);
            mAlert.setImageResource(R.drawable.call_record);  // 通话记录图标

            // 标题使用主文本样式（较大、较醒目）
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);

            // 设置标题文字："通话记录 (X)"，其中X是通话记录的数量
            // 例如：通话记录 (3)
            mTitle.setText(context.getString(R.string.call_record_folder_name)
                    + context.getString(R.string.format_folder_files_count, data.getNotesCount()));
        }
        // ---------- 情况2：通话记录文件夹下的笔记（通话录音笔记）----------
        // 当笔记的父文件夹是通话记录文件夹时
        else if (data.getParentId() == Notes.ID_CALL_RECORD_FOLDER) {
            // 显示联系人姓名（如"张三"）
            mCallName.setVisibility(View.VISIBLE);
            mCallName.setText(data.getCallName());  // 通过电话号码查询到的联系人姓名

            // 标题使用次要文本样式（稍小、颜色稍淡）
            mTitle.setTextAppearance(context, R.style.TextAppearanceSecondaryItem);

            // 获取格式化的摘要内容：去除多余空白、换行符、清单标记（✓/□）等
            String snippet = DataUtils.getFormattedSnippet(data.getSnippet());
            // 高亮显示搜索关键词（将匹配的词标红）
            mTitle.setText(highlightText(snippet));

            // 如果有提醒闹钟，显示时钟图标
            if (data.hasAlert()) {
                mAlert.setImageResource(R.drawable.clock);  // 闹钟图标
                mAlert.setVisibility(View.VISIBLE);
            } else {
                mAlert.setVisibility(View.GONE);
            }
        }
        // ---------- 情况3：普通笔记或文件夹 ----------
        else {
            // 普通笔记/文件夹不需要显示联系人姓名
            mCallName.setVisibility(View.GONE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);

            // 子情况3.1：文件夹类型
            if (data.getType() == Notes.TYPE_FOLDER) {
                // 显示文件夹名称和包含的笔记数量，例如："工作 (5)"
                mTitle.setText(data.getSnippet()
                        + context.getString(R.string.format_folder_files_count, data.getNotesCount()));
                // 文件夹不显示闹钟图标（文件夹不能设置提醒）
                mAlert.setVisibility(View.GONE);
            }
            // 子情况3.2：普通笔记类型
            else {
                // 获取格式化的摘要内容
                String snippet = DataUtils.getFormattedSnippet(data.getSnippet());
                // 高亮显示搜索关键词
                mTitle.setText(highlightText(snippet));

                // 如果有提醒闹钟，显示时钟图标
                if (data.hasAlert()) {
                    mAlert.setImageResource(R.drawable.clock);
                    mAlert.setVisibility(View.VISIBLE);
                } else {
                    mAlert.setVisibility(View.GONE);
                }
            }
        }

        // ====================== 3. 设置修改时间 ======================
        // DateUtils.getRelativeTimeSpanString() 将时间戳转换为相对时间格式
        // 示例输出：
        //   - 刚刚（< 1分钟）
        //   - 5分钟前（< 1小时）
        //   - 2小时前（< 24小时）
        //   - 昨天（< 48小时）
        //   - 具体日期（≥ 48小时）
        mTime.setText(DateUtils.getRelativeTimeSpanString(data.getModifiedDate()));

        // ====================== 4. 设置背景样式 ======================
        // 根据笔记在列表中的位置和类型设置不同的背景
        // 使列表项有圆角效果：顶部圆角、底部圆角、四周圆角、直角
        setBackground(data);
    }

    /**
     * 高亮关键词（标红）
     *
     * 【功能】
     * 在文本中搜索用户输入的关键词，将所有匹配的关键词标记为红色
     *
     * 【实现原理】
     * 1. 使用正则表达式匹配关键词（不区分大小写）
     * 2. 使用 SpannableString 可以给字符串的不同部分设置不同样式
     * 3. ForegroundColorSpan 改变文字前景色（这里用红色）
     * 4. 循环匹配直到文本末尾
     *
     * 【示例】
     * 内容："这是一个笔记示例"
     * 关键词："笔记"
     * 结果："这是一个【笔记】示例"（"笔记"两个字显示为红色）
     *
     * @param content 原始文本内容
     * @return 处理后的 CharSequence（如果有关键词则带红色高亮，否则返回原文本）
     */
    private CharSequence highlightText(String content) {
        // 如果内容为空或搜索关键词为空，不需要高亮，直接返回原内容
        if (TextUtils.isEmpty(content) || TextUtils.isEmpty(sSearchKeyword)) {
            return content;
        }

        // 创建可样式化的字符串
        SpannableString sp = new SpannableString(content);

        // 编译正则表达式：不区分大小写（Pattern.CASE_INSENSITIVE）
        // 这样无论用户输入大写还是小写都能匹配
        Pattern pattern = Pattern.compile(sSearchKeyword, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sp);

        // 遍历所有匹配项，设置红色高亮
        while (matcher.find()) {
            // 0xFFFF0000 是红色（ARGB格式：Alpha=FF不透明, Red=FF, Green=00, Blue=00）
            // SPAN_EXCLUSIVE_EXCLUSIVE 表示高亮不扩展到相邻文本
            sp.setSpan(new ForegroundColorSpan(0xFFFF0000),
                    matcher.start(),      // 匹配开始位置（字符索引）
                    matcher.end(),        // 匹配结束位置（字符索引）
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sp;
    }

    /**
     * 设置列表项背景
     *
     * 【功能】
     * 根据笔记类型和在列表中的位置设置不同的背景资源
     *
     * 【背景选择规则 - 笔记类型】
     * - 单独的笔记（列表中只有这一个）→ 四周圆角背景
     * - 第一项 → 顶部圆角背景
     * - 最后一项 → 底部圆角背景
     * - 中间项 → 直角背景（无圆角）
     * - 后面紧跟文件夹的笔记 → 四周圆角背景
     * - 后面有多个文件夹的笔记 → 顶部圆角背景
     *
     * 【背景选择规则 - 文件夹类型】
     * - 使用固定的文件夹背景（灰色，无圆角效果）
     *
     * 【为什么要这样做？】
     * 在Android的ListView中，列表项通常紧密排列。通过给第一项加顶部圆角、最后一项加底部圆角，
     * 可以让整个列表看起来像一个有圆角的卡片，视觉效果更好。
     *
     * @param data 笔记数据对象
     */
    private void setBackground(NoteItemData data) {
        int id = data.getBgColorId();  // 获取背景颜色ID（黄、红、蓝、绿、白等）

        if (data.getType() == Notes.TYPE_NOTE) {
            // 笔记类型：根据位置选择不同的背景资源

            // isSingle(): 列表中只有这一项
            // isOneFollowingFolder(): 这一项后面紧跟着一个文件夹（没有其他笔记）
            // → 使用四角圆角背景
            if (data.isSingle() || data.isOneFollowingFolder()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgSingleRes(id));
            }
            // isLast(): 是列表中的最后一项
            // → 使用底部圆角背景
            else if (data.isLast()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgLastRes(id));
            }
            // isFirst(): 是列表中的第一项
            // isMultiFollowingFolder(): 后面有多个文件夹
            // → 使用顶部圆角背景
            else if (data.isFirst() || data.isMultiFollowingFolder()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgFirstRes(id));
            }
            // 中间项 → 使用直角背景（无圆角）
            else {
                setBackgroundResource(NoteItemBgResources.getNoteBgNormalRes(id));
            }
        } else {
            // 文件夹类型：使用固定的文件夹背景
            // 文件夹通常有自己的特殊样式，不参与圆角效果
            setBackgroundResource(NoteItemBgResources.getFolderBgRes());
        }
    }

    /**
     * 获取当前列表项的数据对象
     * 供外部调用获取当前项的完整信息
     *
     * 使用场景：在 NotesListActivity 中点击列表项时，
     * 通过此方法获取点击项的 NoteItemData，进而获得笔记ID、类型等信息
     *
     * @return NoteItemData 对象，包含当前项的完整信息
     */
    public NoteItemData getItemData() {
        return mItemData;
    }
}