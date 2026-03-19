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

public class VideoClipper {
    private static final Logger logger = LogManager.getLogger(VideoClipper.class);
    private static final String VIDEO_CLIP_URL = "https://open.ys7.com/api/service/cloudrecord/video/convert";
    
    private String accessToken;
    private String deviceSerial;
    private int channelNo;

    public VideoClipper(String accessToken, String deviceSerial, int channelNo) {
        this.accessToken = accessToken;
        this.deviceSerial = deviceSerial;
        this.channelNo = channelNo;
    }

    public Map<String, Object> clipVideo(String folderNode, String planId, List<Map<String, Object>>精彩瞬间) {
        logger.info("剪辑视频，文件夹节点: {}, 计划ID: {}, 精彩瞬间数量: {}", folderNode, planId, 精彩瞬间.size());
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(VIDEO_CLIP_URL);
            httpPost.setHeader("accessToken", accessToken);
            httpPost.setHeader("Content-Type", "application/json");
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            
            // 构建时间线
            List<Map<String, Object>> timeLine = new ArrayList<>();
            for (Map<String, Object> event : 精彩瞬间) {
                Map<String, Object> timeLineItem = new HashMap<>();
                timeLineItem.put("type", 1); // 1-文件
                timeLineItem.put("fileId", folderNode);
                
                // 提取事件的开始和结束时间
                Map<String, Object> timeRange = (Map<String, Object>) event.get("timeRange");
                if (timeRange != null) {
                    double startTime = ((Number) timeRange.get("start")).doubleValue();
                    double endTime = ((Number) timeRange.get("end")).doubleValue();
                    
                    timeLineItem.put("in", startTime);
                    timeLineItem.put("out", endTime);
                    timeLineItem.put("timeLineIn", startTime);
                    timeLineItem.put("timeLineOut", endTime);
                }
                
                // 添加转场效果
                List<Map<String, Object>> effects = new ArrayList<>();
                Map<String, Object> effect = new HashMap<>();
                effect.put("type", "Transition");
                effect.put("subType", "fade"); // 淡入淡出转场
                effects.add(effect);
                timeLineItem.put("effects", effects);
                
                timeLine.add(timeLineItem);
            }
            requestBody.put("timeLine", timeLine);
            
            // 设备信息
            requestBody.put("deviceSerial", deviceSerial);
            requestBody.put("localIndex", channelNo);
            
            // 输出文件名
            requestBody.put("fileName", "台球精彩瞬间_" + System.currentTimeMillis());
            
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String jsonBody = gson.toJson(requestBody);
            httpPost.setEntity(new StringEntity(jsonBody));
            
            logger.debug("视频剪辑请求: {}", jsonBody);
            
            org.apache.hc.core5.http.ClassicHttpResponse response = httpClient.execute(httpPost);
            int statusCode = response.getCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            
            logger.debug("视频剪辑响应状态码: {}", statusCode);
            logger.debug("视频剪辑响应: {}", responseBody);
            
            Map<String, Object> result = gson.fromJson(responseBody, Map.class);
            Map<String, Object> meta = (Map<String, Object>) result.get("meta");
            
            if (meta != null) {
                Object codeObj = meta.get("code");
                int code = 0;
                if (codeObj instanceof Number) {
                    code = ((Number) codeObj).intValue();
                }
                
                if (code == 200) {
                    Object dataObj = result.get("data");
                    if (dataObj != null) {
                        Map<String, Object> data = (Map<String, Object>) dataObj;
                        String taskId = (String) data.get("taskId");
                        
                        logger.info("视频剪辑任务创建成功，任务ID: {}", taskId);
                        
                        Map<String, Object> clipResult = new HashMap<>();
                        clipResult.put("success", true);
                        clipResult.put("clipId", taskId);
                        return clipResult;
                    } else {
                        logger.error("视频剪辑失败: 无返回数据");
                        return createErrorResult("无返回数据");
                    }
                } else {
                    String errorMsg = meta.get("message").toString();
                    logger.error("视频剪辑失败: {}", errorMsg);
                    return createErrorResult(errorMsg);
                }
            } else {
                logger.error("视频剪辑失败: 响应格式错误");
                return createErrorResult("响应格式错误");
            }
        } catch (Exception e) {
            logger.error("视频剪辑异常: {}", e.getMessage(), e);
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