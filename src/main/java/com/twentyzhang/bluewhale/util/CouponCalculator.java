package com.twentyzhang.bluewhale.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 多优惠券叠加计价：给定订单总额与一组已通过资格校验的券，
 * 穷举所有「应用先后顺序」，返回折扣最大（实付最小）的方案。
 *
 * <p>纯函数、不依赖 Spring/DB，便于单测。门槛(minOrderAmount)资格校验在
 * Service 层按原始订单总额提前完成，此处只负责「顺序最优化」。
 *
 * <p>计价模型（链式作用于"运行金额"）：
 * <ul>
 *   <li>DISCOUNT（折扣）：running = running × value（value 为折扣率，如 0.8）— 乘性</li>
 *   <li>FULL_REDUCTION（满减）/ DIRECT_OFF（直减）：running = running − value — 加性</li>
 * </ul>
 * 每步后将 running 四舍五入到分、并以 0 兜底（券额超过余额时不产生负数）；
 * 最终实付不低于 {@link #MIN_PAYABLE}。
 *
 * <p>顺序为何影响结果：乘性折扣会把"其后"的减免一并打折，故"先乘后减"removes 全额减免、
 * 折扣最大。本类用穷举保证正确（券数 ≤ 3，至多 6 种排列，开销可忽略），无需并发。
 */
public final class CouponCalculator {

    public static final BigDecimal MIN_PAYABLE = new BigDecimal("0.01");

    public static final String TYPE_DISCOUNT       = "DISCOUNT";
    public static final String TYPE_FULL_REDUCTION = "FULL_REDUCTION";
    public static final String TYPE_DIRECT_OFF     = "DIRECT_OFF";

    private CouponCalculator() {}

    /** 单张券的计价要素（type 决定乘性/加性，value 为折扣率或减免额）。 */
    public record CouponSpec(Long couponId, String type, BigDecimal value) {}

    /** 计价结果：折扣金额、实付金额、以及取得最优时的券应用顺序。 */
    public record Result(BigDecimal discountAmount, BigDecimal payableAmount, List<Long> appliedCouponIds) {}

    /**
     * @param totalAmount 订单原始总额（折扣前）
     * @param specs       已通过资格校验的券（每种类型至多一张，调用方保证）
     */
    public static Result calculate(BigDecimal totalAmount, List<CouponSpec> specs) {
        BigDecimal total = totalAmount.setScale(2, RoundingMode.HALF_UP);

        if (specs == null || specs.isEmpty()) {
            BigDecimal payable = total.max(MIN_PAYABLE);
            return new Result(total.subtract(payable), payable, List.of());
        }

        Result best = null;
        for (List<CouponSpec> order : permutations(specs)) {
            BigDecimal payable = applyInOrder(total, order);
            BigDecimal discount = total.subtract(payable);
            if (best == null || discount.compareTo(best.discountAmount()) > 0) {
                List<Long> ids = order.stream().map(CouponSpec::couponId).toList();
                best = new Result(discount, payable, ids);
            }
        }
        return best;
    }

    /** 按给定顺序链式应用券，返回实付金额（已落地下限 MIN_PAYABLE）。 */
    private static BigDecimal applyInOrder(BigDecimal total, List<CouponSpec> order) {
        BigDecimal running = total;
        for (CouponSpec spec : order) {
            if (TYPE_DISCOUNT.equals(spec.type())) {
                running = running.multiply(spec.value()).setScale(2, RoundingMode.HALF_UP);
            } else {
                running = running.subtract(spec.value());
            }
            if (running.compareTo(BigDecimal.ZERO) < 0) {
                running = BigDecimal.ZERO;
            }
        }
        return running.max(MIN_PAYABLE);
    }

    /** 生成全排列（Heap 递归），券数极少（≤3）开销可忽略。 */
    private static <T> List<List<T>> permutations(List<T> items) {
        List<List<T>> result = new ArrayList<>();
        permute(new ArrayList<>(items), 0, result);
        return result;
    }

    private static <T> void permute(List<T> arr, int k, List<List<T>> out) {
        if (k == arr.size()) {
            out.add(new ArrayList<>(arr));
            return;
        }
        for (int i = k; i < arr.size(); i++) {
            swap(arr, k, i);
            permute(arr, k + 1, out);
            swap(arr, k, i);
        }
    }

    private static <T> void swap(List<T> arr, int i, int j) {
        T tmp = arr.get(i);
        arr.set(i, arr.get(j));
        arr.set(j, tmp);
    }
}
