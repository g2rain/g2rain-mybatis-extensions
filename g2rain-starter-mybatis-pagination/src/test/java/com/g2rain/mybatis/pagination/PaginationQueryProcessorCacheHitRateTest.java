package com.g2rain.mybatis.pagination;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 验证 COUNT_SQL_CACHE 对同一 SQL 的命中率：首次 miss，后续 hit。
 */
class PaginationQueryProcessorCacheHitRateTest {

    static class TestablePaginationQueryProcessor extends PaginationQueryProcessor {
        TestablePaginationQueryProcessor(int order) {
            super(order);
        }

        String cachedAutoCountSqlPublic(String sql) throws Exception {
            return cachedAutoCountSql(sql);
        }
    }

    @Test
    void count_sql_cache_should_hit_for_same_sql_multiple_calls() throws Exception {
        PaginationQueryProcessor.clearInternalCachesForTest();

        CacheStats before = PaginationQueryProcessor.countSqlCacheStats();

        TestablePaginationQueryProcessor processor = new TestablePaginationQueryProcessor(20000);
        String sql = "SELECT id, name FROM test_user WHERE id = 1 ORDER BY id DESC";

        int calls = 10;
        for (int i = 0; i < calls; i++) {
            processor.cachedAutoCountSqlPublic(sql);
        }

        CacheStats after = PaginationQueryProcessor.countSqlCacheStats();
        CacheStats delta = after.minus(before);

        Assertions.assertEquals(calls, delta.requestCount(), "requestCount should equal call count");
        Assertions.assertEquals(1, delta.missCount(), "first call should be a miss");
        Assertions.assertEquals(calls - 1, delta.hitCount(), "subsequent calls should hit cache");
    }
}

