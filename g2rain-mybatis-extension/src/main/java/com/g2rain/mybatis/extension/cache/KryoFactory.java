package com.g2rain.mybatis.extension.cache;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import com.esotericsoftware.kryo.util.Pool;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.Offset;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.objenesis.strategy.StdInstantiatorStrategy;

/**
 * Kryo 工厂类，提供高性能、线程安全的对象序列化与深拷贝功能。
 * <p>
 * 核心功能：
 * <ul>
 *     <li>对象序列化/反序列化为字节数组，适用于缓存 {@link Statement}/{@link Statements} 等对象。</li>
 *     <li>提供对象深拷贝能力，保证高并发环境下的对象副本安全使用。</li>
 *     <li>内部维护 Kryo、Output、Input 池，避免频繁创建对象导致的性能开销。</li>
 * </ul>
 * <p>
 * 设计要点：
 * <ul>
 *     <li>单例模式，通过 {@link #getDefaultFactory()} 获取全局 KryoFactory 实例。</li>
 *     <li>Pool 对象保证 Kryo、Input、Output 可重复利用，降低 GC 压力。</li>
 *     <li>批量注册 {@link Select} 热门类，提高解析性能并避免注册缺失异常。</li>
 *     <li>使用 {@link Kryo#setReferences(boolean)} 保持对象引用，避免循环引用问题。</li>
 * </ul>
 * <p>
 * 注意事项：
 * <ul>
 *     <li>Kryo 对象非线程安全，所有操作均通过池化获得独立实例。</li>
 *     <li>Output/ Input buffer 初始大小为 2048 字节，可根据实际 SQL 对象大小调整。</li>
 *     <li>copy() 方法适合在多线程环境中获取对象副本，避免缓存对象被修改。</li>
 * </ul>
 *
 * @author alpha
 * @since 2026/3/5
 */
public class KryoFactory {

    /**
     * 单例实例
     */
    private static final KryoFactory FACTORY = new KryoFactory();

    /**
     * Kryo 对象池，用于序列化/反序列化/深拷贝
     */
    private final Pool<Kryo> kryoPool;

    /**
     * Output 对象池，用于序列化输出
     */
    private final Pool<Output> outputPool;

    /**
     * Input 对象池，用于反序列化输入
     */
    private final Pool<Input> inputPool;

    /**
     * 私有构造器，初始化 Kryo、Output、Input 池。
     * <p>
     * Kryo 初始化包含：
     * <ul>
     *     <li>开启对象引用支持</li>
     *     <li>关闭注册类要求</li>
     *     <li>InstantiatorStrategy 支持无参构造</li>
     *     <li>批量注册 JSqlParser 热门类以提升解析性能</li>
     * </ul>
     * Output/ Input 池使用固定初始容量，支持多线程获取和释放。
     */
    private KryoFactory() {
        // Kryo pool
        kryoPool = new Pool<>(true, false, 8) {
            @Override
            protected Kryo create() {
                Kryo kryo = new Kryo();
                kryo.setReferences(true);
                kryo.setRegistrationRequired(false);
                kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
                // 批量注册 JSqlParser 热门类
                Class<?>[] hotClasses = {
                        Select.class, PlainSelect.class, SelectItem.class, Table.class, Column.class,
                        AndExpression.class, EqualsTo.class, InExpression.class, JdbcParameter.class,
                        LongValue.class, StringValue.class, ParenthesedExpressionList.class, Alias.class,
                        OrderByElement.class, Limit.class, Offset.class
                };
                for (Class<?> clazz : hotClasses) {
                    kryo.register(clazz);
                }
                return kryo;
            }
        };

        // Output pool
        this.outputPool = new Pool<>(true, false, 64) {
            @Override
            protected Output create() {
                return new Output(2048, -1);
            }
        };

        // Input pool
        this.inputPool = new Pool<>(true, false, 64) {
            @Override
            protected Input create() {
                return new Input(2048);
            }
        };
    }

    /**
     * 获取单例 KryoFactory。
     *
     * @return 全局 KryoFactory 实例
     */
    public static KryoFactory getDefaultFactory() {
        return FACTORY;
    }

    /**
     * 序列化对象为字节数组。
     * <p>
     * 从 Kryo/Output 池获取对象，完成序列化后释放回池。
     *
     * @param obj 待序列化对象
     * @return 序列化后的字节数组
     */
    public byte[] serialize(Object obj) {
        Kryo kryo = kryoPool.obtain();
        Output output = outputPool.obtain();
        try {
            kryo.writeClassAndObject(output, obj);
            return output.toBytes();
        } finally {
            output.reset();
            outputPool.free(output);
            kryoPool.free(kryo);
        }
    }

    /**
     * 反序列化字节数组为对象。
     * <p>
     * 从 Kryo/Input 池获取对象，完成反序列化后释放回池。
     *
     * @param bytes 待反序列化字节数组
     * @return 反序列化得到的对象
     */
    public Object deserialize(byte[] bytes) {
        Kryo kryo = kryoPool.obtain();
        Input input = inputPool.obtain();
        try {
            input.setBuffer(bytes);
            return kryo.readClassAndObject(input);
        } finally {
            inputPool.free(input);
            kryoPool.free(kryo);
        }
    }
}
