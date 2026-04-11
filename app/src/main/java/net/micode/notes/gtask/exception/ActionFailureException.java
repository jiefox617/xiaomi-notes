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

package net.micode.notes.gtask.exception;

/**
 * 类名：ActionFailureException
 * 从名字理解：操作失败异常
 * 继承：RuntimeException（运行时异常，不需要强制捕获）
 * 作用：Google Task 同步过程中，操作（创建/更新/删除/JSON 转换）失败时抛出
 * 用于中断同步流程，并提示错误信息
 */
public class ActionFailureException extends RuntimeException {
    /**
     * 序列化版本号
     * 作用：保证反序列化时版本一致，避免崩溃
     */
    private static final long serialVersionUID = 4425249765923293627L;

    /**
     * 无参构造方法
     */
    public ActionFailureException() {
        super();
    }

    /**
     * 带异常信息的构造方法
     * @param paramString 异常提示文字
     */
    public ActionFailureException(String paramString) {
        super(paramString);
    }

    /**
     * 带异常信息 + 原始异常的构造方法
     * @param paramString 异常提示文字
     * @param paramThrowable 原始异常（如 JSON 解析失败、数据库错误）
     */
    public ActionFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}