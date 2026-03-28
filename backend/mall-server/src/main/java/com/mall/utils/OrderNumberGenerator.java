package com.mall.utils;

import com.mall.service.IdSegmentService;
import com.mall.service.IdSegmentService.SegmentRange;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Slf4j
public class OrderNumberGenerator {

    private static final String ORDER_BIZ_TAG = "order";
    private static final int SEQ_LENGTH = 12;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    
    // 监控阈值：使用超过 95% 时触发异步预加载
    private static final double WARNING_THRESHOLD = 0.95;
    // 告警阈值：使用超过 80% 时记录日志
    private static final double LOG_THRESHOLD = 0.80;

    private final ReentrantLock lock = new ReentrantLock();
    private volatile long current = 0L;
    private volatile long max = -1L;
    private volatile long start = 0L;
    
    // 备用号段（异步预加载）
    private volatile SegmentRange nextRange = null;
    
    // 监控计数器
    private final AtomicLong totalRequestCount = new AtomicLong(0);
    private final AtomicLong dbRequestCount = new AtomicLong(0);
    
    // 异步预加载锁
    private final Object asyncLock = new Object();
    private volatile boolean isLoading = false;

    @Autowired
    private IdSegmentService idSegmentService;

    /**
     * 生成下一个订单号
     * @return 订单号
     */
    public String nextOrderNumber() {
        long seq = nextId();
        String datePrefix = LocalDate.now().format(DATE_FORMAT);
        return datePrefix + pad(seq);
    }

    /**
     * 获取下一个序号（带监控和异步预加载）
     * @return 序号
     */
    private long nextId() {
        totalRequestCount.incrementAndGet();
        
        if (current <= max) {
            // 检查是否需要异步预加载
            checkAndPreload();
            return current++;
        }
        
        lock.lock();
        try {
            if (current > max) {
                // 尝试使用备用号段
                if (nextRange != null) {
                    SegmentRange range = nextRange;
                    nextRange = null;
                    current = range.getStart();
                    max = range.getEnd();
                    start = range.getStart();
                    log.info("切换至备用号段，start={}, end={}, size={}", 
                             start, max, (max - start + 1));
                } else {
                    // 没有备用号段，同步申请
                    log.warn("备用号段已用尽，同步申请新号段");
                    SegmentRange range = idSegmentService.nextSegment(ORDER_BIZ_TAG);
                    current = range.getStart();
                    max = range.getEnd();
                    start = range.getStart();
                    dbRequestCount.incrementAndGet();
                }
            }
            
            // 再次检查是否需要预加载（可能其他线程已经触发）
            if (current <= max) {
                checkAndPreload();
            }
            
            return current++;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 检查并触发异步预加载
     */
    private void checkAndPreload() {
        if (start == 0 || max == 0) {
            return;
        }
        
        long used = current - start;
        long total = max - start + 1;
        double usageRate = (double) used / total;
        
        // 达到告警阈值，记录日志
        if (usageRate >= LOG_THRESHOLD && usageRate < WARNING_THRESHOLD) {
            log.info("号段使用率：{}% (当前={}/{})", 
                     String.format("%.1f", usageRate * 100), used, total);
        }
        
        // 达到预警阈值，触发异步预加载
        if (usageRate >= WARNING_THRESHOLD) {
            synchronized (asyncLock) {
                if (!isLoading && nextRange == null) {
                    isLoading = true;
                    log.info("号段使用率达到{}%，触发异步预加载", String.format("%.1f", usageRate * 100));
                    preloadNextSegmentAsync();
                }
            }
        }
    }

    /**
     * 异步预加载下一个号段
     */
    @Async
    public CompletableFuture<Void> preloadNextSegmentAsync() {
        try {
            log.info("开始异步申请新号段...");
            SegmentRange range = idSegmentService.nextSegment(ORDER_BIZ_TAG);
            
            synchronized (asyncLock) {
                if (nextRange == null) {
                    nextRange = range;
                    dbRequestCount.incrementAndGet();
                    log.info("异步预加载成功，新号段：start={}, end={}, size={}", 
                             range.getStart(), range.getEnd(), 
                             (range.getEnd() - range.getStart() + 1));
                } else {
                    log.info("已有备用号段，放弃本次预加载结果");
                }
                isLoading = false;
            }
        } catch (Exception e) {
            log.error("异步预加载号段失败", e);
            synchronized (asyncLock) {
                isLoading = false;
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 获取监控信息
     * @return 监控信息字符串
     */
    public String getMonitorInfo() {
        long total = max - start + 1;
        long used = current - start;
        double usageRate = total > 0 ? (double) used / total : 0;
        
        return String.format(
            "号段监控 [bizTag=%s]: 当前号段=[%d,%d], 已使用=%d/%d (%.1f%%), 备用号段=%s, 总请求=%d, DB 请求=%d, DB 请求占比=%.2f%%",
            ORDER_BIZ_TAG,
            start,
            max,
            used,
            total,
            usageRate * 100,
            nextRange != null ? "有" : "无",
            totalRequestCount.get(),
            dbRequestCount.get(),
            totalRequestCount.get() > 0 ? (double) dbRequestCount.get() / totalRequestCount.get() * 100 : 0
        );
    }

    /**
     * 填充序号到固定长度
     * @param value 序号值
     * @return 填充后的字符串
     */

    private String pad(long value) {
        String seq = String.valueOf(value);
        if (seq.length() >= SEQ_LENGTH) {
            return seq;
        }
        StringBuilder builder = new StringBuilder(SEQ_LENGTH);
        for (int i = seq.length(); i < SEQ_LENGTH; i++) {
            builder.append('0');
        }
        builder.append(seq);
        return builder.toString();
    }
}
