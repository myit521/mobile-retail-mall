package com.mall.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mall.properties.DeepSeekProperties;
import com.mall.service.AiParseService;
import com.mall.vo.MemoParseResultVO;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AI文本解析服务实现 - 使用DeepSeek API
 */
@Service
@Slf4j
public class AiParseServiceImpl implements AiParseService {
    
    @Autowired
    private DeepSeekProperties deepSeekProperties;
    
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    
    private static final String SYSTEM_PROMPT = """
        你是一个智能备忘录解析助手。请从用户输入的自然语言文本中提取结构化信息。
        
        请按以下JSON格式返回解析结果（只返回JSON，不要有其他内容）：
        {
            "title": "简短的任务标题（不超过20字）",
            "dueDate": "截止时间（格式：yyyy-MM-dd HH:mm:ss，如果没有明确时间则为null）",
            "priority": 优先级数字（0-普通，1-重要，2-紧急，根据内容语气判断），
            "tags": ["标签1", "标签2"],
            "keyPoints": ["关键任务点1", "关键任务点2"]
        }
        
        解析规则：
        1. 从内容中提取一个简洁的标题
        2. 识别时间表达（如"明天"、"下周一"、"3月15日"等）并转换为具体日期时间
        3. 根据"紧急"、"重要"、"尽快"等词判断优先级
        4. 提取可能的标签（如"工作"、"学习"、"购物"等）
        5. 提取关键的任务要点
        
        当前时间：%s
        """;
    
    @Override
    public MemoParseResultVO parseMemoContent(String content) {
        log.info("开始AI解析备忘录内容，contentLength={}", content == null ? null : content.length());
        
        // 检查配置
        if (!deepSeekProperties.getEnabled()) {
            log.info("AI解析功能已禁用");
            return buildErrorResult("AI解析功能已禁用");
        }
        
        if (!StringUtils.hasText(deepSeekProperties.getApiKey())) {
            log.warn("DeepSeek API密钥未配置");
            return buildErrorResult("API密钥未配置");
        }
        
        try {
            // 构建请求
            String systemPrompt = String.format(SYSTEM_PROMPT, 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", deepSeekProperties.getModel());
            
            JSONArray messages = new JSONArray();
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.add(systemMessage);
            
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", content);
            messages.add(userMessage);
            
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 1000);
            
            // 发送请求
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(deepSeekProperties.getTimeout(), TimeUnit.SECONDS)
                    .readTimeout(deepSeekProperties.getTimeout(), TimeUnit.SECONDS)
                    .writeTimeout(deepSeekProperties.getTimeout(), TimeUnit.SECONDS)
                    .build();
            
            Request request = new Request.Builder()
                    .url(deepSeekProperties.getBaseUrl() + "/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + deepSeekProperties.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody.toJSONString(), JSON_MEDIA_TYPE))
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("DeepSeek API调用失败，状态码：{}", response.code());
                    return buildErrorResult("API调用失败：" + response.code());
                }
                
                String responseBody = response.body().string();
                log.debug("DeepSeek API响应长度：{}", responseBody == null ? null : responseBody.length());
                
                return parseApiResponse(responseBody);
            }
            
        } catch (Exception e) {
            log.error("AI解析出错", e);
            return buildErrorResult("解析出错：" + e.getMessage());
        }
    }
    
    @Override
    public boolean isServiceAvailable() {
        return deepSeekProperties.getEnabled() 
                && StringUtils.hasText(deepSeekProperties.getApiKey());
    }
    
    /**
     * 解析API响应
     */
    private MemoParseResultVO parseApiResponse(String responseBody) {
        try {
            JSONObject response = JSON.parseObject(responseBody);
            JSONArray choices = response.getJSONArray("choices");
            
            if (choices == null || choices.isEmpty()) {
                return buildErrorResult("API返回结果为空");
            }
            
            String aiContent = choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
            
            // 提取JSON内容（处理可能的markdown代码块）
            String jsonStr = extractJsonFromResponse(aiContent);
            JSONObject parsed = JSON.parseObject(jsonStr);
            
            MemoParseResultVO result = MemoParseResultVO.builder()
                    .title(parsed.getString("title"))
                    .priority(parsed.getInteger("priority"))
                    .rawJson(jsonStr)
                    .success(true)
                    .build();
            
            // 解析截止时间
            String dueDateStr = parsed.getString("dueDate");
            if (StringUtils.hasText(dueDateStr) && !"null".equalsIgnoreCase(dueDateStr)) {
                try {
                    result.setDueDate(LocalDateTime.parse(dueDateStr, 
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                } catch (DateTimeParseException e) {
                    log.warn("截止时间解析失败：{}", dueDateStr);
                }
            }
            
            // 解析标签
            JSONArray tagsArray = parsed.getJSONArray("tags");
            if (tagsArray != null) {
                List<String> tags = new ArrayList<>();
                for (int i = 0; i < tagsArray.size(); i++) {
                    tags.add(tagsArray.getString(i));
                }
                result.setTags(tags);
            }
            
            // 解析关键任务点
            JSONArray keyPointsArray = parsed.getJSONArray("keyPoints");
            if (keyPointsArray != null) {
                List<String> keyPoints = new ArrayList<>();
                for (int i = 0; i < keyPointsArray.size(); i++) {
                    keyPoints.add(keyPointsArray.getString(i));
                }
                result.setKeyPoints(keyPoints);
            }
            
            log.info("AI解析成功，标题：{}，截止时间：{}，优先级：{}", 
                    result.getTitle(), result.getDueDate(), result.getPriority());
            
            return result;
            
        } catch (Exception e) {
            log.error("解析API响应失败", e);
            return buildErrorResult("解析响应失败：" + e.getMessage());
        }
    }
    
    /**
     * 从响应中提取JSON字符串
     */
    private String extractJsonFromResponse(String content) {
        // 去除可能的markdown代码块标记
        content = content.trim();
        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }
        return content.trim();
    }
    
    /**
     * 构建错误结果
     */
    private MemoParseResultVO buildErrorResult(String errorMessage) {
        return MemoParseResultVO.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
