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

package net.micode.notes.ui;

import android.content.Context;
import android.graphics.Rect;
import android.text.Layout;
import android.text.Selection;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.widget.EditText;

import net.micode.notes.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义编辑框控件
 *
 * 【类功能】
 * 清单模式下的多行编辑框，实现回车新增行、删除空行、链接识别等功能
 *
 * 【类间关系】
 * - 被NoteEditActivity使用：作为清单模式的列表项
 * - 实现OnTextViewChangeListener接口：回调删除/回车/文本变化事件
 * - 使用URLSpan：识别并处理文本中的链接
 */
public class NoteEditText extends androidx.appcompat.widget.AppCompatEditText {

    private static final String TAG = "NoteEditText";
    private int mIndex;                          // 当前编辑框在列表中的索引
    private int mSelectionStartBeforeDelete;     // 删除前光标位置

    // 链接协议
    private static final String SCHEME_TEL = "tel:";
    private static final String SCHEME_HTTP = "http:";
    private static final String SCHEME_EMAIL = "mailto:";

    // 协议 → 菜单文字映射
    private static final Map<String, Integer> sSchemaActionResMap = new HashMap<>();
    static {
        sSchemaActionResMap.put(SCHEME_TEL, R.string.note_link_tel);
        sSchemaActionResMap.put(SCHEME_HTTP, R.string.note_link_web);
        sSchemaActionResMap.put(SCHEME_EMAIL, R.string.note_link_email);
    }

    private OnTextViewChangeListener mOnTextViewChangeListener;

    /**
     * 文本变化回调接口
     * 由NoteEditActivity实现
     */
    public interface OnTextViewChangeListener {
        void onEditTextDelete(int index, String text);   // 删除空行
        void onEditTextEnter(int index, String text);    // 回车新增行
        void onTextChange(int index, boolean hasText);   // 文本变化
    }

    public NoteEditText(Context context) {
        super(context, null);
        mIndex = 0;
    }

    public NoteEditText(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.editTextStyle);
    }

    public NoteEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setIndex(int index) {
        mIndex = index;
    }

    public void setOnTextViewChangeListener(OnTextViewChangeListener listener) {
        mOnTextViewChangeListener = listener;
    }

    /**
     * 触摸事件：精确设置光标位置
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // 计算触摸点对应的文本偏移量
            int x = (int) event.getX() - getTotalPaddingLeft() + getScrollX();
            int y = (int) event.getY() - getTotalPaddingTop() + getScrollY();

            Layout layout = getLayout();
            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);
            Selection.setSelection(getText(), off);
        }
        return super.onTouchEvent(event);
    }

    /**
     * 按键按下：记录删除前的光标位置
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            mSelectionStartBeforeDelete = getSelectionStart();
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 按键抬起：处理删除空行和回车换行
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch(keyCode) {
            case KeyEvent.KEYCODE_DEL:
                if (mOnTextViewChangeListener != null) {
                    // 光标在行首且不是第一行 → 删除当前行
                    if (mSelectionStartBeforeDelete == 0 && mIndex != 0) {
                        mOnTextViewChangeListener.onEditTextDelete(mIndex, getText().toString());
                        return true;
                    }
                }
                break;
            case KeyEvent.KEYCODE_ENTER:
                if (mOnTextViewChangeListener != null) {
                    // 光标后内容移到新行
                    int start = getSelectionStart();
                    String text = getText().subSequence(start, length()).toString();
                    setText(getText().subSequence(0, start));
                    mOnTextViewChangeListener.onEditTextEnter(mIndex + 1, text);
                }
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * 焦点变化：控制复选框显示/隐藏
     */
    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (mOnTextViewChangeListener != null) {
            mOnTextViewChangeListener.onTextChange(mIndex, focused || !TextUtils.isEmpty(getText()));
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    /**
     * 长按菜单：识别并处理链接
     */
    @Override
    protected void onCreateContextMenu(ContextMenu menu) {
        if (getText() instanceof Spanned) {
            int start = getSelectionStart();
            int end = getSelectionEnd();
            int min = Math.min(start, end);
            int max = Math.max(start, end);

            // 获取选中区域的链接
            URLSpan[] urls = ((Spanned) getText()).getSpans(min, max, URLSpan.class);
            if (urls.length == 1) {
                int resId = 0;
                // 匹配协议类型
                for (String schema : sSchemaActionResMap.keySet()) {
                    if (urls[0].getURL().indexOf(schema) >= 0) {
                        resId = sSchemaActionResMap.get(schema);
                        break;
                    }
                }
                if (resId == 0) {
                    resId = R.string.note_link_other;
                }

                final URLSpan url = urls[0];
                menu.add(0, 0, 0, resId).setOnMenuItemClickListener(item -> {
                    url.onClick(NoteEditText.this);
                    return true;
                });
            }
        }
        super.onCreateContextMenu(menu);
    }
}