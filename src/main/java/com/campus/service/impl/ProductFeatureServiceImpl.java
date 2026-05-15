package com.campus.service.impl;

import com.campus.entity.Product;
import com.campus.service.ProductFeatureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 成员B：商品特征提取（分词 + Redis Hash）
 */
@Service
public class ProductFeatureServiceImpl implements ProductFeatureService {

    private static final Logger log = LoggerFactory.getLogger(ProductFeatureServiceImpl.class);

    private static final String PRODUCT_FEATURE_KEY_PREFIX = "product:feature:";
    private static final long FEATURE_TTL_SECONDS = 7 * 24 * 60 * 60;

    private static final String FIELD_CATEGORY_ID = "categoryId";
    private static final String FIELD_PRICE = "price";
    private static final String FIELD_KEYWORDS = "keywords";

    private static final Pattern ASCII_TOKEN = Pattern.compile("[a-zA-Z][a-zA-Z0-9]*|[0-9]+");

    /** 教材/数码类常见词，按长度在代码中先长后短匹配 */
    private static final String[] DICT_WORDS = new String[]{
            "算法导论", "高等数学", "线性代数", "概率论", "数理统计", "离散数学",
            "数据结构", "操作系统", "计算机网络", "计算机组成", "软件工程",
            "数据库系统", "数据库", "编译原理", "数字电路", "模拟电路",
            "大学物理", "大学英语", "四级词汇", "六级词汇", "考研数学",
            "计算机", "编程", "习题集", "教材", "二手书", "九成新",
            "算法", "导论", "Java", "Python", "C语言", "C++", "Spring", "MySQL", "Redis"
    };

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "的", "了", "和", "与", "或", "及", "在", "是", "有", "一个", "一本",
            "第", "版", "包邮", "正品", "全新", "闲置", "出售", "转让", "急出", "可刀",
            "校园", "二手", "跳蚤", "市场"
    ));

    private int maxWordLen = 6;
    private final Set<String> dict = new LinkedHashSet<>();

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void initDict() {
        Arrays.sort(DICT_WORDS, (a, b) -> Integer.compare(b.length(), a.length()));
        Collections.addAll(dict, DICT_WORDS);
        for (String w : DICT_WORDS) {
            maxWordLen = Math.max(maxWordLen, w.length());
        }
    }

    @Override
    public List<String> tokenizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return Collections.emptyList();
        }
        String t = title.trim();
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("[\\u4e00-\\u9fa5]+").matcher(t);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                tokenizeAsciiChunk(t.substring(last, m.start()), out);
            }
            tokenizeChineseChunk(m.group(), out);
            last = m.end();
        }
        if (last < t.length()) {
            tokenizeAsciiChunk(t.substring(last), out);
        }
        return out;
    }

    private void tokenizeAsciiChunk(String chunk, List<String> out) {
        Matcher m = ASCII_TOKEN.matcher(chunk);
        while (m.find()) {
            out.add(m.group());
        }
    }

    private void tokenizeChineseChunk(String chunk, List<String> out) {
        int i = 0;
        int n = chunk.length();
        while (i < n) {
            boolean matched = false;
            int maxL = Math.min(maxWordLen, n - i);
            for (int L = maxL; L >= 1; L--) {
                String w = chunk.substring(i, i + L);
                if (dict.contains(w)) {
                    out.add(w);
                    i += L;
                    matched = true;
                    break;
                }
            }
            if (matched) {
                continue;
            }
            if (n - i >= 2) {
                String bi = chunk.substring(i, i + 2);
                out.add(bi);
                i += 2;
            } else {
                out.add(chunk.substring(i, i + 1));
                i += 1;
            }
        }
    }

    @Override
    public List<String> extractKeywords(String title) {
        List<String> raw = tokenizeTitle(title);
        List<String> keywords = new ArrayList<>();
        for (String w : raw) {
            if (!StringUtils.hasText(w)) {
                continue;
            }
            String norm = w.trim();
            if (norm.length() == 1 && STOPWORDS.contains(norm)) {
                continue;
            }
            if (STOPWORDS.contains(norm)) {
                continue;
            }
            if (norm.length() == 1 && !Character.isLetterOrDigit(norm.charAt(0))) {
                continue;
            }
            keywords.add(norm);
        }
        return keywords;
    }

    @Override
    public void saveProductFeatures(Product product, List<String> keywords) {
        if (product == null || product.getId() == null || redisTemplate == null) {
            return;
        }
        try {
            String key = PRODUCT_FEATURE_KEY_PREFIX + product.getId();
            Map<String, Object> map = new HashMap<>();
            if (product.getCategoryId() != null) {
                map.put(FIELD_CATEGORY_ID, product.getCategoryId());
            }
            if (product.getPrice() != null) {
                map.put(FIELD_PRICE, product.getPrice().doubleValue());
            }
            map.put(FIELD_KEYWORDS, objectMapper.writeValueAsString(keywords != null ? keywords : Collections.emptyList()));
            redisTemplate.opsForHash().putAll(key, map);
            redisTemplate.expire(key, FEATURE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("保存商品特征到 Redis 失败 productId={}", product.getId(), e);
        }
    }

    @Override
    public void deleteProductFeatures(Integer productId) {
        if (productId == null || redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(PRODUCT_FEATURE_KEY_PREFIX + productId);
        } catch (Exception e) {
            log.warn("删除商品特征失败 productId={}", productId, e);
        }
    }
}
