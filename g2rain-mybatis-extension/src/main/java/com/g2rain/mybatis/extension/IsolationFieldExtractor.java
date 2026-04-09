package com.g2rain.mybatis.extension;


import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

import java.lang.reflect.Array;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author alpha
 * @since 2026/3/9
 */
public class IsolationFieldExtractor {
    private IsolationFieldExtractor() {

    }

    public static Set<Object> extractValues(Configuration configuration, Object parameter, String propertyName) {
        Set<Object> values = new LinkedHashSet<>();
        if (Objects.isNull(parameter) || Objects.isNull(propertyName)) {
            return values;
        }

        // 1. 如果是 Map (MyBatis 的 ParamMap)
        if (parameter instanceof Map<?, ?> map) {
            // 【最快路径】直接根据 Key 命中 (@Param 或 -parameters 后的变量名)
            if (map.containsKey(propertyName)) {
                processObject(configuration, map.get(propertyName), propertyName, values);
                return values;
            }

            // 【次快路径】仅遍历一层 Value，寻找实体对象内部的属性
            // 过滤掉 MyBatis 自动生成的冗余 Key (arg0, param1 等)，只看业务定义的 Key
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (key.startsWith("arg") || key.startsWith("param")) {
                    continue;
                }

                processObject(configuration, entry.getValue(), propertyName, values);
            }

            return values;
        }

        // 2. 如果是单参数 (实体对象 或 简单类型)
        processObject(configuration, parameter, propertyName, values);
        return values;
    }

    public static void processObject(Configuration configuration, Object obj, String propertyName, Set<Object> values) {
        if (Objects.isNull(obj)) {
            return;
        }

        // 场景 1: 如果是简单类型，直接存入（对应 Map 直接命中或单参数基础类型）
        if (isSimpleType(obj)) {
            values.add(obj);
            return;
        }

        // 场景 2: 如果是集合/数组，仅循环一次，提取内部实体的属性
        if (obj instanceof Iterable<?> it) {
            for (Object item : it) {
                extractFromEntity(configuration, item, propertyName, values);
            }

            return;
        }

        if (obj.getClass().isArray()) {
            for (int i = 0, len = Array.getLength(obj); i < len; i++) {
                extractFromEntity(configuration, Array.get(obj, i), propertyName, values);
            }

            return;
        }

        // 场景 3: 纯实体对象
        extractFromEntity(configuration, obj, propertyName, values);
    }

    public static void extractFromEntity(Configuration configuration, Object obj, String propertyName, Set<Object> values) {
        if (Objects.isNull(obj)) {
            return;
        }

        if (isSimpleType(obj)) {
            values.add(obj);
            return;
        }

        MetaObject metaObject = configuration.newMetaObject(obj);
        if (!metaObject.hasGetter(propertyName)) {
            return;
        }

        Object value = metaObject.getValue(propertyName);
        if (Objects.isNull(value)) {
            return;
        }

        values.add(value);
    }

    public static boolean isSimpleType(Object obj) {
        Class<?> clazz = obj.getClass();

        // 原始类型 / 枚举
        if (clazz.isPrimitive() || clazz.isEnum()) {
            return true;
        }

        // 常见简单类型
        if (obj instanceof Number
                || obj instanceof CharSequence   // String / StringBuilder / StringBuffer
                || obj instanceof Boolean
                || obj instanceof Character) {
            return true;
        }

        // 日期时间
        if (obj instanceof Date || obj instanceof Temporal) {
            return true;
        }

        // 二进制 / Class 类型
        return obj instanceof byte[] || obj instanceof Class<?>;
    }
}
