package com.billiardsmonitor;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoRecorder {
    private static final Logger logger = LogManager.getLogger(VideoRecorder.class);
    private static final String CREATE_ONE_OFF_PLAN_URL = "https://open.ys7.com/api/service/cloudrecord/video/plan/oneOff";
    
    private String accessToken;
    private String deviceSerial;
    private int channelNo;
    private long spaceId;

    public VideoRecorder(String accessToken, String deviceSerial, int channelNo, long spaceId) {
        this.accessToken = accessToken;
        this.deviceSerial = deviceSerial;
        this.channelNo = channelNo;
        this.spaceId = spaceId;
    }

    public Map<String, Object> createOneTimeRecordPlan(int durationSeconds) {
        logger.info("创建一次性录制计划，时长: {}秒", durationSeconds);
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // 构建请求URL
            URI uri = new URI(CREATE_ONE_OFF_PLAN_URL);
            HttpPost httpPost = new HttpPost(uri);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("accessToken", accessToken);
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("planName", "billiards_record_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
            requestBody.put("spaceId", spaceId);
            requestBody.put("autoConvertHls", false);
            requestBody.put("autoDelete", false);
            requestBody.put("templateId", 6); // 根据实际模板ID调整
            requestBody.put("specifiedEndTime", true);
            
            // 构建设备通道信息
            List<Map<String, String>> devIndexInfos = new ArrayList<>();
            Map<String, String> devInfo = new HashMap<>();
            devInfo.put("deviceSerial", deviceSerial);
            devInfo.put("localIndex", String.valueOf(channelNo));
            devIndexInfos.add(devInfo);
            requestBody.put("devIndexInfos", devIndexInfos);
            
            // 计算开始和结束时间
            LocalDateTime startTime = LocalDateTime.now();
            LocalDateTime endTime = startTime.plusSeconds(durationSeconds);
            
            // 格式化时间为yyyyMMddHHmmss
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
            requestBody.put("startTime", startTime.format(formatter));
            requestBody.put("endTime", endTime.format(formatter));
            
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String jsonBody = gson.toJson(requestBody);
            httpPost.setEntity(new StringEntity(jsonBody));
            
            logger.debug("创建一次性录制计划请求: {}", jsonBody);
            
            org.apache.hc.core5.http.ClassicHttpResponse response = httpClient.execute(httpPost);
            int statusCode = response.getCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            
            logger.debug("创建一次性录制计划响应状态码: {}", statusCode);
            logger.debug("创建一次性录制计划响应: {}", responseBody);
            
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
                        Object oneOffPlanIdObj = data.get("oneOffPlanId");
                        String oneOffPlanId = null;
                        if (oneOffPlanIdObj != null) {
                            oneOffPlanId = oneOffPlanIdObj.toString();
                        }
                        
                        logger.info("一次性录制计划创建成功，计划ID: {}", oneOffPlanId);
                        
                        Map<String, Object> recordResult = new HashMap<>();
                        recordResult.put("success", true);
                        recordResult.put("planId", oneOffPlanId);
                        recordResult.put("startTime", startTime);
                        recordResult.put("endTime", endTime);
                        return recordResult;
                    } else {
                        logger.error("创建一次性录制计划失败: 无返回数据");
                        return createErrorResult("无返回数据");
                    }
                } else {
                    String errorMsg = meta.get("message").toString();
                    logger.error("创建一次性录制计划失败: {}", errorMsg);
                    return createErrorResult(errorMsg);
                }
            } else {
                logger.error("创建一次性录制计划失败: 响应格式错误");
                return createErrorResult("响应格式错误");
            }
        } catch (Exception e) {
            logger.error("创建一次性录制计划异常: {}", e.getMessage(), e);
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