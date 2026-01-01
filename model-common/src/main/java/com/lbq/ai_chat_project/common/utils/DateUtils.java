package com.lbq.ai_chat_project.common.utils;

import com.lbq.ai_chat_project.common.exception.BaseException;
import com.lbq.ai_chat_project.common.Constants.CommonExceptionConstants;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * 线程安全的日期工具类（基于 Java 8+ Time API）。
 * <p>
 * 所有格式化器均为 static final，天然线程安全。
 * <p>
 * 异常策略：任何解析或格式化失败将抛出 {@link BaseException}，
 * 错误码定义在 {@link CommonExceptionConstants}。
 * <p>
 * 用法示例：
 * <pre>
 * // 自定义格式解析
 * Date date = DateUtils.parseDate("202512020300", "yyyyMMddHHmm");
 *
 * // 自定义格式输出
 * String str = DateUtils.formatDate(new Date(), "yyyyMMddHHmm");
 * </pre>
 */
public final class DateUtils {

    private DateUtils() { /* 工具类禁止实例化 */ }

    public static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";
    public static final String YYYY_MM_DD = "yyyy-MM-dd";

    // ===== 常用格式器 =====
    public static final DateTimeFormatter ISO_LOCAL_DATE = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd
    public static final DateTimeFormatter ISO_LOCAL_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME; // yyyy-MM-dd'T'HH:mm:ss
    public static final DateTimeFormatter DF_YYYY_MM_DD_HH_MM_SS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DF_YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ==================== 核心转换方法 ====================

    /**
     * Date → LocalDateTime（使用系统默认时区）
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            throw new BaseException(CommonExceptionConstants.DATE_PARSE_ERROR, "输入的 Date 为 null");
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * LocalDateTime → Date（使用系统默认时区）
     */
    public static Date toDate(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            throw new BaseException(CommonExceptionConstants.DATE_PARSE_ERROR, "输入的 LocalDateTime 为 null");
        }
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * 格式化 LocalDateTime 为字符串（使用指定格式器）
     */
    public static String format(LocalDateTime dateTime, DateTimeFormatter formatter) {
        try {
            return formatter.format(dateTime);
        } catch (Exception e) {
            throw new BaseException(CommonExceptionConstants.DATE_FORMAT_ERROR,
                    "日期格式化失败: %s", e.getMessage());
        }
    }

    /**
     * 解析字符串为 LocalDateTime（使用指定格式器）
     */
    public static LocalDateTime parse(String text, DateTimeFormatter formatter) {
        if (text == null || formatter == null) {
            throw new BaseException(CommonExceptionConstants.DATE_PARSE_ERROR, "输入文本或格式器为 null");
        }
        try {
            return LocalDateTime.parse(text.trim(), formatter);
        } catch (DateTimeParseException e) {
            throw new BaseException(CommonExceptionConstants.DATE_PARSE_ERROR,
                    "无法解析日期字符串 '%s' 使用格式器 %s", text, formatter.toString());
        }
    }

    // ==================== 支持任意 pattern 的字符串 ↔ Date 转换 ====================

    /**
     * 使用自定义格式解析字符串为 Date。
     * <p>
     * 示例：parseDate("202512020300", "yyyyMMddHHmm") → Date
     *
     * @param dateStr 日期字符串，如 "202512020300"
     * @param pattern 日期格式，如 "yyyyMMddHHmm"
     * @return 对应的 Date 对象
     * @throws BaseException 解析失败时抛出
     */
    public static Date parseDate(String dateStr, String pattern) {
        if (dateStr == null || pattern == null) {
            throw new BaseException(CommonExceptionConstants.DATE_PARSE_ERROR, "日期字符串或格式不能为 null");
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            LocalDateTime ldt = LocalDateTime.parse(dateStr.trim(), formatter);
            return toDate(ldt);
        } catch (Exception e) {
            throw new BaseException(CommonExceptionConstants.DATE_PARSE_ERROR,
                    "无法使用格式 '%s' 解析日期字符串 '%s'", pattern, dateStr);
        }
    }

    /**
     * 使用默认格式 "yyyy-MM-dd HH:mm:ss" 解析字符串为 Date。
     */
    public static Date parseDate(String dateStr) {
        return parseDate(dateStr, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 使用自定义格式将 Date 格式化为字符串。
     * <p>
     * 示例：formatDate(new Date(), "yyyyMMddHHmm") → "202512020300"
     *
     * @param date    Date 对象
     * @param pattern 日期格式，如 "yyyyMMddHHmm"
     * @return 格式化后的字符串
     * @throws BaseException 格式化失败时抛出
     */
    public static String formatDate(Date date, String pattern) {
        if (date == null || pattern == null) {
            throw new BaseException(CommonExceptionConstants.DATE_FORMAT_ERROR, "Date 或格式不能为 null");
        }
        try {
            LocalDateTime ldt = toLocalDateTime(date);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return formatter.format(ldt);
        } catch (Exception e) {
            throw new BaseException(CommonExceptionConstants.DATE_FORMAT_ERROR,
                    "无法使用格式 '%s' 格式化日期", pattern);
        }
    }

    /**
     * 使用默认格式 "yyyy-MM-dd HH:mm:ss" 将 Date 格式化为字符串。
     */
    public static String formatDate(Date date) {
        return formatDate(date, YYYY_MM_DD_HH_MM_SS);
    }


    /**
     * 将时间戳（毫秒）格式化为 "yyyy-MM-dd HH:mm:ss" 字符串。
     */
    public static String formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            throw new BaseException(CommonExceptionConstants.DATE_PARSE_ERROR, "时间戳必须大于 0");
        }
        LocalDateTime ldt = Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        return format(ldt, DF_YYYY_MM_DD_HH_MM_SS);
    }

    /**
     * 将字符串形式的时间戳解析并格式化为日期字符串。
     */
    public static String formatTimestampStr(String timestampStr) {
        if (timestampStr == null || timestampStr.trim().isEmpty()) {
            throw new BaseException(CommonExceptionConstants.DATE_PARSE_ERROR, "时间戳字符串为空");
        }
        try {
            long ts = Long.parseLong(timestampStr.trim());
            return formatTimestamp(ts);
        } catch (NumberFormatException e) {
            throw new BaseException(CommonExceptionConstants.DATE_PARSE_ERROR,
                    "时间戳字符串 '%s' 不是有效数字", timestampStr);
        }
    }

    /**
     * 获取当前时间字符串（"yyyy-MM-dd HH:mm:ss"）
     */
    public static String nowStr() {
        return formatDate(new Date());
    }

    /**
     * 获取当前时间戳（毫秒）
     */
    public static long currentTimestamp() {
        return System.currentTimeMillis();
    }
}