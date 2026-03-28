package com.mall.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SiliconFlow AI配置属性
 */
@Component
@ConfigurationProperties(prefix = "mall.ai.siliconflow")
@Data
public class DeepSeekProperties {
    
    /**
     * API密钥 - 需要在配置文件中填写
     */
    private String apiKey;
    
    /**
     * API基础URL（SiliconFlow地址）
     */
    private String baseUrl = "https://api.siliconflow.cn";
    
    /**
     * 使用的模型名称（SiliconFlow上的DeepSeek模型）
     */
    private String model = "deepseek-ai/DeepSeek-V3";
    
    /**
     * 请求超时时间（秒）
     */
    private Integer timeout = 60;
    
    /**
     * 是否启用AI解析功能
     */
    private Boolean enabled = true;
}
