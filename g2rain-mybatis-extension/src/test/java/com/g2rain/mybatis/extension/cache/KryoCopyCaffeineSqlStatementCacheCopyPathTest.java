package com.g2rain.mybatis.extension.cache;

import com.g2rain.mybatis.extension.SqlParserDelegate;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 验证 KryoCopyCaffeineSqlStatementCache 在正常 SQL AST 场景下能够走 copy 路径并返回副本。
 */
class KryoCopyCaffeineSqlStatementCacheCopyPathTest {

    @Test
    void should_use_copy_path_for_normal_sql_ast() throws Exception {
        KryoCopyCaffeineSqlStatementCache cache = new KryoCopyCaffeineSqlStatementCache();

        String sql = "SELECT id, name FROM test_user WHERE id = 1 ORDER BY id DESC";
        Statement ast = SqlParserDelegate.parse(sql);

        cache.putStatement(sql, ast);
        Statement got = cache.getStatement(sql);

        Assertions.assertNotNull(got, "cached AST should not be null");
        Assertions.assertNotSame(ast, got, "copy path should return a different instance");
        Assertions.assertEquals(ast.toString(), got.toString(), "AST string representation should be equal after copy");
    }
}

