package com.g2rain.mybatis.extension;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;

/**
 * 函数式接口，封装可能抛出 {@link JSQLParserException} 的 SQL 解析操作。
 * <p>
 * 主要用于 {@link SqlParserDelegate} 中对 SQL 字符串进行单条或多条语句解析的回调。
 * <p>
 * 设计要点：
 * <ul>
 *     <li>是一个标准的函数式接口，可使用 lambda 表达式或方法引用实现。</li>
 *     <li>允许在函数执行过程中抛出 {@link JSQLParserException} 异常，便于解析错误统一处理。</li>
 * </ul>
 *
 * @param <T> 输入类型，通常为 {@link String} SQL 字符串
 * @param <R> 返回类型，通常为 {@link Statement} 或 {@link Statements}
 * @author alpha
 * @since 2026/3/5
 */
@FunctionalInterface
public interface JSqlParserFunction<T, R> {

    /**
     * 应用 SQL 解析函数。
     *
     * @param t 待解析的输入对象（通常为 SQL 字符串）
     * @return 解析后的结果对象
     * @throws JSQLParserException 解析过程中可能抛出的异常
     */
    R apply(T t) throws JSQLParserException;
}
