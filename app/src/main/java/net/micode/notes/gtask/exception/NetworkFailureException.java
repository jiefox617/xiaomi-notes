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
 * 类名：NetworkFailureException
 * 从名字理解：网络失败异常
 * 继承：Exception（受检异常，必须显式捕获处理）
 * 作用：专门用于 Google Task 同步时，**网络请求失败、连接超时、网络断开** 等网络相关错误
 * 与 ActionFailureException 区分：一个管业务操作失败，一个管网络失败
 */
public class NetworkFailureException extends Exception {
    /**
     * 序列化版本ID
     * 作用：保证对象序列化 / 反序列化时版本兼容，避免崩溃
     */
    private static final long serialVersionUID = 2107610287180234136L;

    /**
     * 无参构造方法
     */
    public NetworkFailureException() {
        super();
    }

    /**
     * 带异常信息的构造方法
     * @param paramString 异常描述文字
     */
    public NetworkFailureException(String paramString) {
        super(paramString);
    }

    /**
     * 带异常信息 + 原始异常的构造方法
     * @param paramString 异常描述
     * @param paramThrowable 底层原始异常（如IOException）
     */
    public NetworkFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}