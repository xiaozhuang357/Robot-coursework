package com.lineguard;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试用的 SQL 计数器。
 *
 * 不能用 Hibernate 的 {@code Statistics#getPrepareStatementCount()}：它统计的是"创建了多少条
 * 预编译语句"，同一条 SQL 复用预编译语句时不会增加，N+1 在它眼里跟批量取一模一样，
 * 断言会假通过。这里逐条语句计数，才数得出 N+1。
 *
 * 通过 hibernate.session_factory.statement_inspector 挂到测试上下文（见 src/test/resources/application.yml）。
 */
public class SqlCounter implements StatementInspector {

    private static final AtomicInteger COUNT = new AtomicInteger();

    @Override
    public String inspect(String sql) {
        COUNT.incrementAndGet();
        return sql;
    }

    public static void reset() {
        COUNT.set(0);
    }

    public static int count() {
        return COUNT.get();
    }
}
