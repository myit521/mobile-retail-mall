package com.mall.task;

import com.mall.entity.Memo;
import com.mall.mapper.MemoMapper;
import com.mall.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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
        
        // 按用户分组发送提醒
        Map<Long, List<Memo>> userMemoMap = memoList.stream()
                .collect(Collectors.groupingBy(Memo::getUserId));
        
        for (Map.Entry<Long, List<Memo>> entry : userMemoMap.entrySet()) {
            Long userId = entry.getKey();
            List<Memo> userMemos = entry.getValue();
            
            // 通过WebSocket发送提醒
            for (Memo memo : userMemos) {
                Map<String, Object> message = new HashMap<>();
                message.put("type", "MEMO_REMIND");
                message.put("memoId", memo.getId());
                message.put("title", memo.getTitle());
                message.put("content", memo.getContent());
                message.put("dueDate", memo.getDueDate() != null ? memo.getDueDate().toString() : null);
                message.put("priority", memo.getPriority());
                
                try {
                    WebSocketServer.sendMessage(
                            com.alibaba.fastjson.JSON.toJSONString(message), 
                            userId.toString());
                    log.info("已向用户{}发送备忘录提醒，备忘录ID：{}", userId, memo.getId());
                } catch (Exception e) {
                    log.warn("发送备忘录提醒失败，用户ID={}，memoId={}，errorType={}", userId, memo.getId(), e.getClass().getSimpleName());
                }
            }
        }
        
        // 批量更新提醒状态
        List<Long> ids = memoList.stream()
                .map(Memo::getId)
                .collect(Collectors.toList());
        memoMapper.batchUpdateReminded(ids);
        
        log.info("备忘录提醒任务完成，已处理{}条", ids.size());
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
        
        // 通过WebSocket通知用户有过期的备忘录
        Map<Long, List<Memo>> userMemoMap = overdueList.stream()
                .collect(Collectors.groupingBy(Memo::getUserId));
        
        for (Map.Entry<Long, List<Memo>> entry : userMemoMap.entrySet()) {
            Long userId = entry.getKey();
            List<Memo> userMemos = entry.getValue();
            
            Map<String, Object> message = new HashMap<>();
            message.put("type", "MEMO_OVERDUE");
            message.put("count", userMemos.size());
            message.put("memoIds", userMemos.stream().map(Memo::getId).collect(Collectors.toList()));
            
            try {
                WebSocketServer.sendMessage(
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
