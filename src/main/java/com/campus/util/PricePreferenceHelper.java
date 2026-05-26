package com.campus.util;

import com.campus.entity.UserProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 按浏览频次统计价格档位，用加权分位数推导偏好区间（避免 1 元与 800 元拉宽到 1–800）。
 */
public final class PricePreferenceHelper {

    /** 档位上界（元）：(0,10], (10,30], ... */
    private static final double[] BAND_UPPERS = {10, 30, 60, 100, 200, 500, 1000, Double.MAX_VALUE};

    private PricePreferenceHelper() {
    }

    public static String bandKey(double price) {
        if (price < 0) {
            price = 0;
        }
        for (int i = 0; i < BAND_UPPERS.length; i++) {
            if (price <= BAND_UPPERS[i]) {
                return String.valueOf(BAND_UPPERS[i]);
            }
        }
        return "inf";
    }

    public static double bandCenter(String key) {
        if ("inf".equals(key)) {
            return 1500;
        }
        try {
            double upper = Double.parseDouble(key);
            int idx = indexOfUpper(upper);
            double lower = idx <= 0 ? 0 : BAND_UPPERS[idx - 1];
            if (Double.isInfinite(upper) || upper >= 1000) {
                return (lower + 1000) / 2;
            }
            return (lower + upper) / 2;
        } catch (NumberFormatException e) {
            return 50;
        }
    }

    private static int indexOfUpper(double upper) {
        for (int i = 0; i < BAND_UPPERS.length; i++) {
            if (BAND_UPPERS[i] == upper || (Double.isInfinite(BAND_UPPERS[i]) && upper >= 1000)) {
                return i;
            }
        }
        return BAND_UPPERS.length - 1;
    }

    /**
     * 记录一次浏览价格（权重默认 1，购买可调高）。
     */
    public static void recordPrice(UserProfile.PriceRange range, double price, int weight) {
        if (range == null || price < 0 || weight <= 0) {
            return;
        }
        Map<String, Integer> bands = range.getBandWeights();
        if (bands == null) {
            bands = new LinkedHashMap<>();
            range.setBandWeights(bands);
        }
        String key = bandKey(price);
        bands.merge(key, weight, Integer::sum);
        refreshDerived(range);
    }

    /**
     * 根据档位权重重算 min/max/avg（P15–P85 为偏好带，avg 为加权中心）。
     */
    public static void refreshDerived(UserProfile.PriceRange range) {
        if (range == null) {
            return;
        }
        Map<String, Integer> bands = range.getBandWeights();
        if (bands == null || bands.isEmpty()) {
            normalizeLegacyBounds(range);
            return;
        }

        List<BandPoint> points = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, Integer> e : new TreeMap<>(bands).entrySet()) {
            int w = e.getValue() == null ? 0 : e.getValue();
            if (w <= 0) {
                continue;
            }
            points.add(new BandPoint(bandCenter(e.getKey()), w));
            total += w;
        }
        if (total == 0) {
            normalizeLegacyBounds(range);
            return;
        }

        double p15 = weightedPercentile(points, total, 0.15);
        double p50 = weightedPercentile(points, total, 0.50);
        double p85 = weightedPercentile(points, total, 0.85);
        double mean = weightedMean(points, total);

        double margin = Math.max(5, (p85 - p15) * 0.15);
        range.setMinPrice(Math.max(0, p15 - margin));
        range.setMaxPrice(p85 + margin);
        range.setAvgPrice(p50 > 0 ? p50 : mean);
        range.setTotalBrowseWeight(total);
    }

    /**
     * 商品价与画像偏好的匹配度 [0,1]，按档位权重 + 高斯衰减，而非简单 min–max 包含。
     */
    public static double preferenceScore(UserProfile.PriceRange range, Double productPrice) {
        if (productPrice == null || productPrice < 0) {
            return 0.5;
        }
        if (range == null) {
            return 0.5;
        }
        refreshDerived(range);

        Map<String, Integer> bands = range.getBandWeights();
        if (bands != null && !bands.isEmpty()) {
            double bandScore = bandWeightScore(bands, productPrice);
            double gaussian = gaussianScore(productPrice, range.getAvgPrice(), range.getMinPrice(), range.getMaxPrice());
            return clamp01(0.55 * bandScore + 0.45 * gaussian);
        }

        Double min = range.getMinPrice();
        Double max = range.getMaxPrice();
        Double avg = range.getAvgPrice();
        if (min == null || max == null || max <= 0 || min >= Double.MAX_VALUE / 4) {
            return 0.5;
        }
        return gaussianScore(productPrice, avg, min, max);
    }

    private static double bandWeightScore(Map<String, Integer> bands, double price) {
        String key = bandKey(price);
        int hit = bands.getOrDefault(key, 0);
        int total = bands.values().stream().mapToInt(v -> v == null ? 0 : v).sum();
        if (total <= 0) {
            return 0.5;
        }
        double exact = (double) hit / total;
        if (exact >= 0.05) {
            return Math.min(1.0, 0.65 + 0.35 * Math.min(1.0, exact * 3));
        }
        // 相邻档位也给少量分
        double neighbor = 0;
        for (Map.Entry<String, Integer> e : bands.entrySet()) {
            double center = bandCenter(e.getKey());
            int w = e.getValue() == null ? 0 : e.getValue();
            if (w <= 0) {
                continue;
            }
            double dist = Math.abs(Math.log1p(price) - Math.log1p(center));
            neighbor = Math.max(neighbor, (w / (double) total) * Math.exp(-dist));
        }
        return clamp01(Math.max(exact, neighbor));
    }

    private static double gaussianScore(double price, Double center, Double min, Double max) {
        double c = center != null && center > 0 ? center : (min + max) / 2;
        double span = 1.0;
        if (min != null && max != null && max > min) {
            span = Math.max((max - min) / 2, c * 0.2);
        } else {
            span = Math.max(c * 0.25, 10);
        }
        double z = (price - c) / span;
        return Math.exp(-0.5 * z * z);
    }

    private static double weightedPercentile(List<BandPoint> points, int total, double p) {
        int target = (int) Math.ceil(total * p);
        int cum = 0;
        for (BandPoint pt : points) {
            cum += pt.weight;
            if (cum >= target) {
                return pt.center;
            }
        }
        return points.get(points.size() - 1).center;
    }

    private static double weightedMean(List<BandPoint> points, int total) {
        double sum = 0;
        for (BandPoint pt : points) {
            sum += pt.center * pt.weight;
        }
        return sum / total;
    }

    private static void normalizeLegacyBounds(UserProfile.PriceRange range) {
        if (range.getMinPrice() != null && range.getMinPrice() >= Double.MAX_VALUE / 4) {
            range.setMinPrice(null);
        }
        if (range.getMaxPrice() != null && range.getMaxPrice() <= 0) {
            range.setMaxPrice(null);
        }
    }

    private static double clamp01(double v) {
        if (v < 0) {
            return 0;
        }
        if (v > 1) {
            return 1;
        }
        return v;
    }

    private static final class BandPoint {
        final double center;
        final int weight;

        BandPoint(double center, int weight) {
            this.center = center;
            this.weight = weight;
        }
    }
}
