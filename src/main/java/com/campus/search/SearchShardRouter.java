package com.campus.search;

/**
 * 分布式分片路由：按商品 ID 取模分配到 Shard（可扩展为一致性哈希环）
 */
public final class SearchShardRouter {

    private SearchShardRouter() {
    }

    public static int shardForProductId(int productId, int shardCount) {
        if (shardCount <= 0) {
            return 0;
        }
        return Math.floorMod(productId, shardCount);
    }

    public static String productTermKey(int shardId, String term) {
        return "idx:product:s" + shardId + ":term:" + term;
    }

    public static String productDocKey(int productId) {
        return "search:doc:" + productId;
    }

    public static String productVectorKey(int productId) {
        return "search:vec:" + productId;
    }

    public static String docFreqKey(String term) {
        return "search:df:" + term;
    }

    public static String metaDocCountKey() {
        return "search:meta:doc_count";
    }
}
