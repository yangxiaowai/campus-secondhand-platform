package com.campus.search;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 轻量语义扩展：同义词/上下位词扩展查询词（无需外部 Embedding 服务）
 */
public final class SemanticSynonymExpander {

    private static final Map<String, String[]> SYNONYMS = new HashMap<>();

    static {
        put("手机", "手机", "数码", "苹果", "iPhone", "华为", "小米", "二手手机");
        put("电脑", "电脑", "笔记本", "MacBook", "联想", "戴尔", "计算机");
        put("教材", "教材", "书籍", "课本", "图书", "参考书", "考研");
        put("书籍", "书籍", "教材", "课本", "图书", "小说");
        put("耳机", "耳机", "蓝牙", "AirPods", "音响");
        put("自行车", "自行车", "单车", "山地车", "骑行");
        put("键盘", "键盘", "机械键盘", "外设");
        put("鼠标", "鼠标", "外设", "罗技");
        put("相机", "相机", "摄影", "单反", "微单");
        put("平板", "平板", "iPad", "安卓平板");
        put("算法", "算法", "数据结构", "编程", "计算机");
        put("数学", "数学", "高数", "线代", "概率论");
        put("英语", "英语", "四级", "六级", "雅思");
        put("衣服", "衣服", "服装", "外套", "卫衣");
        put("鞋", "鞋", "球鞋", "运动鞋", "Nike", "Adidas");
    }

    private SemanticSynonymExpander() {
    }

    private static void put(String key, String... values) {
        SYNONYMS.put(key, values);
    }

    public static List<String> expand(List<String> baseTerms) {
        Set<String> expanded = new LinkedHashSet<>();
        if (baseTerms != null) {
            for (String t : baseTerms) {
                if (StringUtils.hasText(t)) {
                    expanded.add(t.trim());
                }
            }
        }
        for (String t : new ArrayList<>(expanded)) {
            String[] syns = SYNONYMS.get(t);
            if (syns != null) {
                for (String s : syns) {
                    if (StringUtils.hasText(s)) {
                        expanded.add(s.trim());
                    }
                }
            }
        }
        return new ArrayList<>(expanded);
    }
}
