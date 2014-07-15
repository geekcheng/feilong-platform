/*
 * Copyright (C) 2008 feilong (venusdrogon@163.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.feilong.commons.core.util;

import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StringBuilder工具类,可以用来快速的拼接字符串.
 * 
 * @author <a href="mailto:venusdrogon@163.com">金鑫</a>
 * @version 1.0 2012-7-11 下午5:05:56
 * @since 1.0.0
 */
public final class StringBuilderUtil{

	/** The Constant log. */
	@SuppressWarnings("unused")
	private static final Logger	log	= LoggerFactory.getLogger(StringBuilderUtil.class);

	/**
	 * 将不定参数,拼接成string
	 * 
	 * @param strings
	 *            不定参数
	 * @return 使用stringBuilder.append来拼接
	 */
	public final static String append(Object...strings){
		if (Validator.isNotNullOrEmpty(strings)){
			StringBuilder stringBuilder = new StringBuilder();
			for (Object string : strings){
				stringBuilder.append(string);
			}
			return stringBuilder.toString();
		}
		return null;
	}

	/**
	 * append 并且换行.
	 * 
	 * @param stringBuilder
	 *            stringBuilder
	 * @param text
	 *            需要append 的内容
	 */
	public final static void appendTextWithLn(StringBuilder stringBuilder,Object text){
		stringBuilder.append(text);
		stringBuilder.append(SystemUtils.LINE_SEPARATOR);
	}

	/**
	 * <code>
	 * stringBuilder.append(Constants.lineSeparator);
	 * </code>
	 * 
	 * @param stringBuilder
	 *            the string builder
	 */
	public final static void appendLn(StringBuilder stringBuilder){
		stringBuilder.append(SystemUtils.LINE_SEPARATOR);
	}

	/**
	 * append 并且换行,键值对形式输出.
	 * 
	 * @param stringBuilder
	 *            stringBuilder
	 * @param key
	 *            key
	 * @param value
	 *            value
	 */
	public final static void appendTextWithLn(StringBuilder stringBuilder,String key,Object value){
		stringBuilder.append(key);
		stringBuilder.append(":");
		stringBuilder.append(value);
		stringBuilder.append(SystemUtils.LINE_SEPARATOR);
	}

	/**
	 * 文件 以醒目字符分隔,一般用于 文本输出.例如:
	 * 
	 * <pre>
	 * *****************************************
	 * </pre>
	 * 
	 * @param stringBuilder
	 *            the string builder
	 */
	public final static void appendTextWithSeparated(StringBuilder stringBuilder){
		appendTextWithSeparatedWithTitle(stringBuilder, null);
	}

	/**
	 * 文件 以醒目字符分隔,一般用于 文本输出 带标题的 <br>
	 * 例如:
	 * 
	 * <pre>
	 * ***************呵呵,下面是销售日报-库存部分*******************
	 * </pre>
	 * 
	 * .
	 * 
	 * @param stringBuilder
	 *            the string builder
	 * @param title
	 *            the title
	 */
	public final static void appendTextWithSeparatedWithTitle(StringBuilder stringBuilder,String title){
		stringBuilder.append(SystemUtils.LINE_SEPARATOR);
		stringBuilder.append("**************************");
		if (Validator.isNotNullOrEmpty(title)){
			stringBuilder.append(title);
		}
		stringBuilder.append("**************************");
		stringBuilder.append(SystemUtils.LINE_SEPARATOR);
	}

	/**
	 * 文件 以醒目字符分隔,一般用于 文本输出.
	 * 
	 * @return stringBuilder
	 */
	public final static StringBuilder appendTextWithSeparated(){
		StringBuilder stringBuilder = new StringBuilder();
		appendTextWithSeparated(stringBuilder);
		return stringBuilder;
	}
}
