package com.g2rain.mybatis.extension;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Invocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 复合拦截器，基于 {@link PluginProcessor} 插件链扩展 MyBatis 执行逻辑。
 * <p>
 * 该拦截器执行拦截器链操作，并按照以下生命周期调度插件：
 * <ol>
 *     <li>判定：通过 {@link PluginProcessor#supports()} 和 {@link PluginProcessor#shouldIntercept} 筛选待执行插件。</li>
 *     <li>前置：依次执行匹配插件的 {@link PluginProcessor#preHandle}。</li>
 *     <li>执行：调用目标方法。</li>
 *     <li>后置：依次执行匹配插件的 {@link PluginProcessor#postHandle}。</li>
 *     <li>收尾：在 {@code finally} 块中执行 {@link PluginProcessor#afterCompletion} 清理资源。</li>
 * </ol>
 *
 * @author alpha
 * @since 2026/3/3
 */
public abstract class CompositeInterceptor implements Interceptor {
    /**
     * 插件处理器列表，按 {@link PluginProcessor#order()} 排序。
     */
    private final List<PluginProcessor> pluginProcessors = new ArrayList<>();

    /**
     * 复合拦截器链执行入口。
     * <p>
     * 创建 {@link InvocationContext}，识别拦截点类型，
     * 并依次执行匹配的插件处理器，最后调用对应 {@link InterceptPoint#handle(InvocationContext)}。
     *
     * @param invocation 当前方法调用信息
     * @return 执行结果对象
     * @throws Throwable 执行方法或插件处理时抛出的异常
     */
    protected Object executeChain(Invocation invocation) throws Throwable {
        InvocationContext context = new InvocationContext(invocation);
        InterceptPoint point = InterceptPoint.identify(context);

        // 1. 筛选出本次调用需要执行的活跃插件子集
        List<PluginProcessor> activeProcessors = new ArrayList<>();
        for (PluginProcessor processor : pluginProcessors) {
            if (!point.equals(processor.supports())) {
                continue;
            }

            activeProcessors.add(processor);
        }
        try {
            // 2. 执行前置增强逻辑
            for (PluginProcessor processor : activeProcessors) {
                if (processor.shouldIntercept(context)) {
                    processor.preHandle(context);
                }
            }

            // 3. 执行 MyBatis 核心业务逻辑
            Object result = point.handle(context);

            // 4. 执行后置结果处理逻辑
            for (int i = activeProcessors.size() - 1; i >= 0; i--) {
                activeProcessors.get(i).postHandle(context, result);
            }

            return result;
        } finally {
            // 5. 最终收尾：逆序执行清理工作，确保 ScopedValue 等资源及时释放
            // 采用逆序是为了符合“先开启、后关闭”的资源管理原则
            for (int i = activeProcessors.size() - 1; i >= 0; i--) {
                activeProcessors.get(i).afterCompletion();
            }
        }
    }

    /**
     * 添加插件处理器，并按 {@link PluginProcessor#order()} 排序。
     *
     * @param pluginProcessor 插件处理器实例
     */
    public void addPluginProcessor(PluginProcessor pluginProcessor) {
        this.pluginProcessors.add(pluginProcessor);
        this.pluginProcessors.sort(Comparator.comparingInt(PluginProcessor::order));
    }

    /**
     * 获取已添加的插件处理器列表，返回不可变集合。
     *
     * @return 插件处理器不可变列表
     */
    public List<PluginProcessor> getPluginProcessors() {
        return Collections.unmodifiableList(this.pluginProcessors);
    }
}
