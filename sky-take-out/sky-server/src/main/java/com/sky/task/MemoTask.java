package com.sky.task;

import com.sky.entity.Memo;
import com.sky.mapper.MemoMapper;
import com.sky.notification.api.OrderNotificationPort;
import com.sky.notification.api.AdminSessionNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 备忘录定时任务
 */
@Component
@Slf4j
public class MemoTask {
    
    @Autowired
    private MemoMapper memoMapper;
    @Autowired
    private OrderNotificationPort notificationPort;
    @Autowired
    private AdminSessionNotifier webSocketSessions;
    
    /**
     * 检查需要提醒的备忘录
     * 每分钟执行一次
     */
    @Scheduled(cron = "0 * * * * ?")
    public void checkRemind() {
        log.debug("执行备忘录提醒检查任务");
        
        LocalDateTime now = LocalDateTime.now();
        List<Memo> memoList = memoMapper.selectNeedRemind(now);
        
        if (memoList == null || memoList.isEmpty()) {
            return;
        }
        
        log.info("发现{}条需要提醒的备忘录", memoList.size());
        
        List<Long> deliveredIds = new ArrayList<>();
        for (Memo memo : memoList) {
            Long userId = memo.getUserId();
            try {
                if ("USER".equals(memo.getOwnerType())) {
                    // A durable inbox row delivers the reminder even when the user is offline.
                    memoMapper.insertUserReminder(memo.getId());
                    deliveredIds.add(memo.getId());
                } else if ("ADMIN".equals(memo.getOwnerType())) {
                    Map<String, Object> message = new HashMap<>();
                    message.put("type", "MEMO_REMIND");
                    message.put("memoId", memo.getId());
                    message.put("title", memo.getTitle());
                    message.put("content", memo.getContent());
                    message.put("dueDate", memo.getDueDate() != null ? memo.getDueDate().toString() : null);
                    message.put("priority", memo.getPriority());
                
                    if (webSocketSessions.sendTo(userId.toString(), com.alibaba.fastjson.JSON.toJSONString(message))) {
                        deliveredIds.add(memo.getId());
                    }
                }
            } catch (Exception e) {
                log.warn("发送备忘录提醒失败，用户ID={}，memoId={}，errorType={}", userId, memo.getId(), e.getClass().getSimpleName());
            }
        }
        if (!deliveredIds.isEmpty()) memoMapper.batchUpdateReminded(deliveredIds);
        
        log.info("备忘录提醒任务完成，已处理{}条", deliveredIds.size());
    }
    
    /**
     * 检查过期的备忘录
     * 每天凌晨1点执行
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void checkOverdue() {
        log.info("执行备忘录过期检查任务");
        
        LocalDateTime now = LocalDateTime.now();
        List<Memo> overdueList = memoMapper.selectOverdue(now);
        
        if (overdueList == null || overdueList.isEmpty()) {
            log.info("没有过期的备忘录");
            return;
        }
        
        log.info("发现{}条过期的备忘录", overdueList.size());
        
        // User reminders are persisted; employee reminders use the admin WebSocket.
        for (Memo memo : overdueList) {
            if ("USER".equals(memo.getOwnerType())) {
                try {
                    memoMapper.insertUserReminder(memo.getId());
                } catch (Exception e) {
                    log.warn("保存备忘录过期提醒失败，memoId={}，errorType={}", memo.getId(), e.getClass().getSimpleName());
                }
            }
        }
        Map<Long, List<Memo>> userMemoMap = overdueList.stream()
                .filter(memo -> "ADMIN".equals(memo.getOwnerType()))
                .collect(Collectors.groupingBy(Memo::getUserId));
        
        for (Map.Entry<Long, List<Memo>> entry : userMemoMap.entrySet()) {
            Long userId = entry.getKey();
            List<Memo> userMemos = entry.getValue();
            
            Map<String, Object> message = new HashMap<>();
            message.put("type", "MEMO_OVERDUE");
            message.put("count", userMemos.size());
            message.put("memoIds", userMemos.stream().map(Memo::getId).collect(Collectors.toList()));
            
            try {
                notificationPort.sendTo(
                        com.alibaba.fastjson.JSON.toJSONString(message), 
                        userId.toString());
                log.info("已向用户{}发送过期提醒，过期数量：{}", userId, userMemos.size());
            } catch (Exception e) {
                log.warn("发送过期提醒失败，userId={}，memoCount={}，errorType={}", userId, userMemos.size(), e.getClass().getSimpleName());
            }
        }
        
        log.info("备忘录过期检查任务完成");
    }
}
