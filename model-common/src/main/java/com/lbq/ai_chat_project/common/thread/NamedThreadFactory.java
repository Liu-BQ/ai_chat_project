package com.lbq.ai_chat_project.common.thread;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 命名线程工厂，为线程池中的线程生成可读名称。
 * <p>
 * 示例：new NamedThreadFactory("ollama-worker") 会创建名为 "ollama-worker-1", "ollama-worker-2" 的线程。
 */
public class NamedThreadFactory implements ThreadFactory {

    private final String namePrefix;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final boolean daemon;

    /**
     * 构造命名线程工厂。
     *
     * @param namePrefix 线程名称前缀（如 "db-pool", "ai-worker"）
     * @param daemon     是否为守护线程
     */
    public NamedThreadFactory(String namePrefix, boolean daemon) {
        if (namePrefix == null || namePrefix.trim().isEmpty()) {
            throw new IllegalArgumentException("Thread name prefix must not be null or empty");
        }
        this.namePrefix = namePrefix.trim() + "-";
        this.daemon = daemon;
    }

    /**
     * 构造非守护线程的工厂（常用）。
     */
    public NamedThreadFactory(String namePrefix) {
        this(namePrefix, false);
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {
        Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
        t.setDaemon(daemon);
        if (t.getPriority() != Thread.NORM_PRIORITY) {
            t.setPriority(Thread.NORM_PRIORITY);
        }
        return t;
    }
}