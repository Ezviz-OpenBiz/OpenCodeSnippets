package com.billiardsmonitor;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BilliardsAnalyzer {
    private static final Logger logger = LogManager.getLogger(BilliardsAnalyzer.class);
    private static final String BILLIARDS_ANALYZE_URL = "https://open.ys7.com/api/service/intelligence/algo/analysis/billiards_video_analysis";
    
    private String accessToken;

    public BilliardsAnalyzer(String accessToken) {
        this.accessToken = accessToken;
    }

    public Map<String, Object> analyzeBilliards(String videoUrl) {
        logger.info("分析台球视频: {}", videoUrl);
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(BILLIARDS_ANALYZE_URL);
            httpPost.setHeader("accessToken", accessToken);
            httpPost.setHeader("Content-Type", "application/json");
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("stream", true);
            requestBody.put("requestId", java.util.UUID.randomUUID().toString());
            requestBody.put("level", "normal");
            
            List<Map<String, Object>> dataInfo = new ArrayList<>();
            Map<String, Object> dataItem = new HashMap<>();
            dataItem.put("modal", "video");
            dataItem.put("type", "url");
            dataItem.put("data", videoUrl);
            dataInfo.add(dataItem);
            requestBody.put("dataInfo", dataInfo);
            
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String jsonBody = gson.toJson(requestBody);
            httpPost.setEntity(new StringEntity(jsonBody));
            
            logger.debug("台球分析请求: {}", jsonBody);
            
            org.apache.hc.core5.http.ClassicHttpResponse response = httpClient.execute(httpPost);
            int statusCode = response.getCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            
            logger.debug("台球分析响应状态码: {}", statusCode);
            logger.debug("台球分析响应: {}", responseBody);
            
            Map<String, Object> result = gson.fromJson(responseBody, Map.class);
            Map<String, Object> meta = (Map<String, Object>) result.get("meta");
            
            if (meta != null) {
                Object codeObj = meta.get("code");
                int code = 0;
                if (codeObj instanceof Number) {
                    code = ((Number) codeObj).intValue();
                }
                
                if (code != 200) {
                    String errorMsg = meta.get("message").toString();
                    logger.error("台球分析失败: {}", errorMsg);
                    return createErrorResult(errorMsg);
                }
            } else {
                logger.error("台球分析失败: 响应格式错误");
                return createErrorResult("响应格式错误");
            }
            
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            String requestId = (String) data.get("requestId");
            String taskType = (String) data.get("taskType");
            
            logger.info("台球分析任务创建成功，请求ID: {}, 任务类型: {}", requestId, taskType);
            
            // 由于是异步接口，结果通过云信令推送
            // 这里返回任务创建成功，实际分析结果需要通过消息推送获取
            Map<String, Object> analysisResult = new HashMap<>();
            analysisResult.put("success", true);
            analysisResult.put("hasResults", false);
            analysisResult.put("requestId", requestId);
            analysisResult.put("taskType", taskType);
            analysisResult.put("message", "台球分析任务已创建，结果将通过云信令推送");
            
            return analysisResult;
        } catch (Exception e) {
            logger.error("台球分析异常: {}", e.getMessage(), e);
            return createErrorResult(e.getMessage());
        }
    }

    private Map<String, Object> createErrorResult(String errorMessage) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", errorMessage);
        return result;
    }
}