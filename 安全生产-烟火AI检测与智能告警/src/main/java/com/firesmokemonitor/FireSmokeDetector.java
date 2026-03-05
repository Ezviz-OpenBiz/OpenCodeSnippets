package com.firesmokemonitor;

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

public class FireSmokeDetector {
    private static final Logger logger = LogManager.getLogger(FireSmokeDetector.class);
    private static final String FIRE_SMOKE_DETECT_URL = "https://open.ys7.com/api/service/intelligence/algo/analysis/firesmoke_detection";
    
    private String accessToken;
    
    public FireSmokeDetector(String accessToken) {
        this.accessToken = accessToken;
    }
    
    public Map<String, Object> detectFireSmoke(String imageUrl) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(FIRE_SMOKE_DETECT_URL);
            httpPost.setHeader("accessToken", accessToken);
            httpPost.setHeader("Content-Type", "application/json");
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("stream", false);
            requestBody.put("requestId", java.util.UUID.randomUUID().toString());
            requestBody.put("taskType", "firesmoke_detection");
            requestBody.put("mark", true);
            
            List<Map<String, Object>> dataInfo = new ArrayList<>();
            Map<String, Object> dataItem = new HashMap<>();
            
            // 检查是否是本地文件路径
            boolean isLocalFile = false;
            try {
                java.nio.file.Path path = java.nio.file.Paths.get(imageUrl);
                isLocalFile = java.nio.file.Files.exists(path);
            } catch (Exception e) {
                // 不是有效的文件路径
                isLocalFile = false;
            }
            
            if (isLocalFile) {
                // 本地文件，转换为base64
                logger.info("处理本地图片: " + imageUrl);
                byte[] imageBytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(imageUrl));
                String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
                dataItem.put("data", base64Image);
                dataItem.put("type", "base64");
                dataItem.put("modal", "image");
            } else if (imageUrl.startsWith("http")) {
                // HTTP URL
                dataItem.put("data", imageUrl);
                dataItem.put("type", "url");
                dataItem.put("modal", "image");
            } else {
                // 其他情况，尝试作为URL处理
                dataItem.put("data", imageUrl);
                dataItem.put("type", "url");
                dataItem.put("modal", "image");
            }
            
            dataInfo.add(dataItem);
            requestBody.put("dataInfo", dataInfo);
            
            List<Map<String, Object>> dataParams = new ArrayList<>();
            Map<String, Object> paramItem = new HashMap<>();
            paramItem.put("modal", "image");
            paramItem.put("img_width", 1280);
            paramItem.put("img_height", 720);
            dataParams.add(paramItem);
            requestBody.put("dataParams", dataParams);
            
            var gson = new com.google.gson.Gson();
            String jsonBody = gson.toJson(requestBody);
            httpPost.setEntity(new StringEntity(jsonBody));
            
            logger.info("执行烟火识别: 图片类型=" + (isLocalFile ? "本地文件" : "URL"));
            var response = httpClient.execute(httpPost);
            
            // 检查响应状态码
            int statusCode = response.getCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            
            logger.info("烟火识别响应状态码: " + statusCode);
            logger.info("烟火识别响应: " + responseBody);
            
            // 处理404错误（API不存在）
            if (statusCode == 404) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("is_fire_smoke_detected", false);
                errorResult.put("fire_smoke_status", "烟火识别API未找到（404），系统使用默认逻辑处理");
                errorResult.put("confidence", 0.0);
                errorResult.put("api_error", "Not Found");
                return errorResult;
            }
            
            var result = gson.fromJson(responseBody, Map.class);
            var meta = (Map<String, Object>) result.get("meta");
            
            if (meta != null) {
                Object codeObj = meta.get("code");
                int code = 0;
                if (codeObj instanceof Number) {
                    code = ((Number) codeObj).intValue();
                }
                
                if (code != 200) {
                    String errorMsg = meta.get("message").toString();
                    logger.warn("烟火识别接口错误: " + errorMsg);
                    
                    // 处理API错误但不抛出异常，返回默认结果
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("is_fire_smoke_detected", false);
                    errorResult.put("fire_smoke_status", "识别接口错误: " + errorMsg);
                    errorResult.put("confidence", 0.0);
                    errorResult.put("api_error", errorMsg);
                    return errorResult;
                }
            } else {
                logger.warn("烟火识别接口响应格式错误");
                
                // 处理格式错误但不抛出异常，返回默认结果
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("is_fire_smoke_detected", false);
                errorResult.put("fire_smoke_status", "识别响应格式错误");
                errorResult.put("confidence", 0.0);
                errorResult.put("api_error", "响应格式错误");
                return errorResult;
            }
            
            var data = (Map<String, Object>) result.get("data");
            var images = (List<Map<String, Object>>) data.get("images");
            
            if (images == null || images.isEmpty()) {
                Map<String, Object> detectionResult = new HashMap<>();
                detectionResult.put("is_fire_smoke_detected", false);
                detectionResult.put("fire_smoke_status", "未检测到烟火");
                detectionResult.put("confidence", 0.0);
                return detectionResult;
            }
            
            var contentAnn = (Map<String, Object>) images.get(0).get("contentAnn");
            var bboxes = (List<Map<String, Object>>) contentAnn.get("bboxes");
            
            if (bboxes == null || bboxes.isEmpty()) {
                Map<String, Object> detectionResult = new HashMap<>();
                detectionResult.put("is_fire_smoke_detected", false);
                detectionResult.put("fire_smoke_status", "未检测到烟火");
                detectionResult.put("confidence", 0.0);
                return detectionResult;
            }
            
            // 处理烟火识别结果
            double bestConfidence = 0.0;
            boolean isFireSmokeDetected = false;
            String detectedType = "无";
            
            for (Map<String, Object> bbox : bboxes) {
                var tagInfo = (Map<String, Object>) bbox.get("tagInfo");
                
                // 检查tag是否为fire或smoke
                String tag = tagInfo.get("tag").toString();
                if ("fire".equals(tag) || "smoke".equals(tag)) {
                    // 从bbox中获取置信度
                    double confidence = ((Number) bbox.get("weight")).doubleValue();
                    
                    if (confidence > bestConfidence) {
                        bestConfidence = confidence;
                        isFireSmokeDetected = true;
                        detectedType = "fire".equals(tag) ? "火焰" : "烟雾";
                    }
                }
            }
            
            Map<String, Object> detectionResult = new HashMap<>();
            if (isFireSmokeDetected && bestConfidence > 0.5) {
                detectionResult.put("is_fire_smoke_detected", true);
                detectionResult.put("detected_type", detectedType);
                detectionResult.put("fire_smoke_status", "检测到" + detectedType);
                detectionResult.put("confidence", bestConfidence);
            } else {
                detectionResult.put("is_fire_smoke_detected", false);
                detectionResult.put("fire_smoke_status", "未检测到烟火或置信度低");
                detectionResult.put("confidence", 0.0);
            }
            
            return detectionResult;
            
        } catch (Exception e) {
            logger.error("烟火识别异常: " + e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("is_fire_smoke_detected", false);
            errorResult.put("fire_smoke_status", "识别失败: " + e.getMessage());
            errorResult.put("confidence", 0.0);
            return errorResult;
        }
    }
}