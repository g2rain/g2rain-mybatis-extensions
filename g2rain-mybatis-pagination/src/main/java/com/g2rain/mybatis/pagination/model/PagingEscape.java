package com.g2rain.mybatis.pagination.model;


import java.util.concurrent.Callable;

/**
 * 分页逃逸控制工具类。
 * <p>
 * 该类通过 {@link ScopedValue} 机制在当前执行栈中植入“逃逸信号”。
 * 当某些内部 SQL 查询（如权限校验、数据隔离逻辑）不希望被分页拦截器改写时，
 * 可使用此类将目标代码块包裹，从而实现对分页逻辑的自动绕过。
 * </p>
 *
 * <p>相比于手动清理上下文，该方式利用了作用域绑定特性，更加安全且对业务无侵入。</p>
 *
 * @author alpha
 * @since 2026/4/15
 */
public final class PagingEscape {

    /**
     * 内部逃逸信号标识。
     * 使用 ScopedValue 确保信号在当前线程及其子任务（结构化并发）中有效，并在作用域结束时自动释放。
     */
    private static final ScopedValue<Boolean> SIGNAL = ScopedValue.newInstance();

    /**
     * 私有构造方法，防止外部实例化。
     */
    private PagingEscape() {
    }

    /**
     * 判断当前执行上下文是否处于“逃逸状态”。
     * <p>
     * 分页拦截器应调用此方法，若返回 {@code true}，则应跳过对 SQL 的分页处理，直接放行。
     * </p>
     *
     * @return 如果当前作用域已绑定逃逸信号且信号为 true，则返回 {@code true}；否则返回 {@code false}。
     */
    public static boolean isEscaped() {
        return SIGNAL.isBound() && Boolean.TRUE.equals(SIGNAL.get());
    }

    /**
     * 在逃逸信号作用域内执行特定任务。
     * <p>
     * 该方法会开启一个临时的作用域并发送“逃逸信号”，使得在该任务块执行期间，
     * 底层的分页插件能够识别并跳过分页拦截逻辑。
     * </p>
     *
     * <pre>{@code
     * PagingEscape.run(() -> {
     *     // 此处执行的 SQL 将不会触发分页
     *     return dao.internalQuery();
     * });
     * }</pre>
     *
     * @param task 待执行的业务逻辑回调
     * @param <T>  任务返回值的类型
     * @return 任务执行后的返回结果
     * @throws RuntimeException 如果任务执行过程中抛出任何异常，将被封装为 RuntimeException 抛出
     */
    public static <T> T run(Callable<T> task) {
        try {
            // 开启 ScopedValue 作用域，绑定信号量为 TRUE
            return ScopedValue.where(SIGNAL, Boolean.TRUE).call(task::call);
        } catch (Exception e) {
            // 异常转换，保持 API 简洁性
            throw new RuntimeException("Error occurred during paging escape execution", e);
        }
    }
}
