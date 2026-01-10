package com.lbq.ai_chat_project.common.id;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 高性能、线程安全的雪花 ID 生成器，支持生成 long 和 String 两种格式。
 * <p>
 * ID 结构（64 位）：
 * <pre>
 * | 1 bit (符号位，始终为0) | 41 bits (毫秒时间戳) | 10 bits (机器ID) | 12 bits (序列号) |
 * </pre>
 * <p>
 * 特性：
 * <ul>
 *   <li>每毫秒最多生成 4096 个 ID（12 位序列号）</li>
 *   <li>支持最多 1024 台机器（10 位机器 ID）</li>
 *   <li>时间戳基于自定义起始时间（2023-01-01 UTC），避免高位浪费</li>
 *   <li>自动处理时钟回拨（容忍 ≤5ms）</li>
 *   <li>提供 {@link #nextId()}（long）和 {@link #nextIdStr()}（String）两种输出</li>
 * </ul>
 */
@Slf4j
public class SnowflakeIdGenerator {

    // ================== 基础配置 ==================
    private static final long START_TIMESTAMP = 1767196800000L; // 2026-01-01 00:00:00 UTC
    private static final long MACHINE_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_ID_BITS); // 1023
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);     // 4095

    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;

    // ================== 实例字段 ==================
    private final long machineId;
    private final AtomicLong lastTimestamp = new AtomicLong(-1L);
    private final AtomicLong sequence = new AtomicLong(0L);

    /**
     * 构造雪花 ID 生成器。
     *
     * @param machineId 机器 ID，范围 [0, 1023]
     * @throws IllegalArgumentException 如果 machineId 超出有效范围
     */
    public SnowflakeIdGenerator(long machineId) {
        if (machineId < 0 || machineId > MAX_MACHINE_ID) {
            throw new IllegalArgumentException(
                    String.format("Machine ID must be between %d and %d", 0, MAX_MACHINE_ID));
        }
        this.machineId = machineId;
    }

    /**
     * 生成下一个全局唯一的雪花 ID（long 类型）。
     * <p>
     * 此方法是线程安全的，适用于数据库主键、内部计算等场景。
     *
     * @return 64 位雪花 ID（long）
     * @throws RuntimeException 如果系统时钟回拨超过 5 毫秒
     */
    public synchronized long nextId() {
        long currentTimestamp = System.currentTimeMillis();

        // 时钟回拨检测（容忍 ≤5ms）
        if (currentTimestamp < lastTimestamp.get()) {
            long offset = lastTimestamp.get() - currentTimestamp;
            if (offset <= 5) {
                try {
                    Thread.sleep(offset + 1); // 等待时钟追上
                    currentTimestamp = System.currentTimeMillis();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during clock rollback wait", e);
                }
            } else {
                throw new RuntimeException("Clock moved backwards. Refusing to generate id for " +
                        offset + " milliseconds");
            }
        }

        // 同一毫秒内，序列号递增
        if (currentTimestamp == lastTimestamp.get()) {
            sequence.set((sequence.get() + 1) & MAX_SEQUENCE);
            if (sequence.get() == 0) {
                // 序列号溢出，等待下一毫秒
                currentTimestamp = waitUntilNextMillis(currentTimestamp);
            }
        } else {
            // 新毫秒，重置序列号
            sequence.set(0L);
        }

        lastTimestamp.set(currentTimestamp);

        // 组装最终 ID
        return ((currentTimestamp - START_TIMESTAMP) << TIMESTAMP_LEFT_SHIFT)
                | (machineId << MACHINE_ID_SHIFT)
                | sequence.get();
    }

    /**
     * 生成下一个全局唯一的雪花 ID（String 类型）。
     * <p>
     * 内部调用 {@link #nextId()} 并转换为字符串，适用于 JSON API、日志、前端展示等场景。
     *
     * @return 雪花 ID 的字符串表示（十进制数字字符串，如 "1701234567890123456"）
     */
    public String nextIdStr() {
        return String.valueOf(nextId());
    }

    /**
     * 等待直到进入下一个毫秒。
     */
    private long waitUntilNextMillis(long lastTimestamp) {
        long currentTimestamp = System.currentTimeMillis();
        while (currentTimestamp <= lastTimestamp) {
            currentTimestamp = System.currentTimeMillis();
        }
        return currentTimestamp;
    }

}