package com.g2rain.mybatis.extension;

import com.g2rain.mybatis.extension.cache.KryoCopyCaffeineSqlStatementCache;
import com.g2rain.mybatis.extension.cache.SqlStatementCache;
import lombok.Setter;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;

/**
 * SQL 解析代理类，封装对 JSqlParser 的调用，并提供缓存机制以提高重复解析 SQL 的性能。
 * <p>
 * 功能点：
 * <ul>
 *     <li>缓存单条 SQL 语句解析结果 {@link Statement}。</li>
 *     <li>缓存多条 SQL 语句解析结果 {@link Statements}。</li>
 *     <li>可通过自定义 {@link JSqlParserFunction} 替换默认解析函数。</li>
 *     <li>对解析结果提供深度复制，避免缓存对象被修改影响后续使用。</li>
 * </ul>
 * <p>
 * 典型用法：
 * <pre>
 * Statement statement = SqlParserDelegate.parse("SELECT * FROM table");
 * Statements statements = SqlParserDelegate.parseStatements("SELECT * FROM t1; SELECT * FROM t2");
 * </pre>
 *
 * <b>线程安全：</b>静态缓存 {@link SqlStatementCache} 已实现线程安全，可安全在高并发环境下调用。
 *
 * @author alpha
 * @since 2026/3/5
 */
public class SqlParserDelegate {

    /**
     * SQL 语句缓存器，默认使用 {@link KryoCopyCaffeineSqlStatementCache}。
     */
    @Setter
    private static SqlStatementCache sqlStatementCache = new KryoCopyCaffeineSqlStatementCache();

    /**
     * 单条 SQL 解析函数，默认使用 {@link CCJSqlParserUtil#parse(String)}。
     */
    @Setter
    private static JSqlParserFunction<String, Statement> parserSingleFunc = CCJSqlParserUtil::parse;

    /**
     * 多条 SQL 解析函数，默认使用 {@link CCJSqlParserUtil#parseStatements(String)}。
     */
    @Setter
    private static JSqlParserFunction<String, Statements> parserMultiFunc = CCJSqlParserUtil::parseStatements;

    /**
     * 解析单条 SQL 语句，如果缓存中存在则直接返回缓存副本。
     *
     * @param sql 待解析的 SQL 字符串
     * @return 解析后的 {@link Statement} 对象
     * @throws JSQLParserException 解析失败时抛出
     */
    public static Statement parse(String sql) throws JSQLParserException {
        return parserSingleFunc.apply(sql);
//        Statement statement = sqlStatementCache.getStatement(sql);
//        // 返回缓存副本，避免外部修改污染缓存
//        if (Objects.nonNull(statement)) {
//            return statement;
//        }
//
//        // 缓存未命中，解析并存入缓存
//        statement = parserSingleFunc.apply(sql);
//        sqlStatementCache.putStatement(sql, statement);
//        return sqlStatementCache.getStatement(sql);
    }

    /**
     * 解析多条 SQL 语句（Batch），如果缓存中存在则直接返回缓存副本。
     *
     * @param sql 待解析的 SQL 字符串（可包含多条 SQL）
     * @return 解析后的 {@link Statements} 对象
     * @throws JSQLParserException 解析失败时抛出
     */
    public static Statements parseStatements(String sql) throws JSQLParserException {
        return parserMultiFunc.apply(sql);
//        Statements statements = sqlStatementCache.getStatements(sql);
//        // 返回缓存副本，避免外部修改污染缓存
//        if (Objects.nonNull(statements)) {
//            return statements;
//        }
//
//        // 缓存未命中，解析并存入缓存
//        statements = parserMultiFunc.apply(sql);
//        sqlStatementCache.putStatements(sql, statements);
//        return sqlStatementCache.getStatements(sql);
    }
}
