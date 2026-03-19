package com.billiardsmonitor;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClipFileQuery {
    private static final Logger logger = LogManager.getLogger(ClipFileQuery.class);
    private static final String QUERY_CLIP_URL = "https://open.ys7.com/api/service/open/cloud/vod/task/files/";
    
    private String accessToken;

    public ClipFileQuery(String accessToken) {
        this.accessToken = accessToken;
    }

    public Map<String, Object> queryClipStatus(String taskId) {
        logger.info("查询剪辑状态，任务ID: {}", taskId);
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // 构建请求URL
            URIBuilder uriBuilder = new URIBuilder(QUERY_CLIP_URL + taskId);
            uriBuilder.addParameter("expireSeconds", "3600");
            
            URI uri = uriBuilder.build();
            HttpGet httpGet = new HttpGet(uri);
            httpGet.setHeader("accessToken", accessToken);
            httpGet.setHeader("Content-Type", "application/json");
            
            logger.debug("查询剪辑状态请求: {}", uri.toString());
            
            org.apache.hc.core5.http.ClassicHttpResponse response = httpClient.execute(httpGet);
            int statusCode = response.getCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            
            logger.debug("查询剪辑状态响应状态码: {}", statusCode);
            logger.debug("查询剪辑状态响应: {}", responseBody);
            
            com.google.gson.Gson gson = new com.google.gson.Gson();
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
                        List<Map<String, Object>> dataList = (List<Map<String, Object>>) dataObj;
                        if (!dataList.isEmpty()) {
                            Map<String, Object> data = dataList.get(0);
                            String folderNode = (String) data.get("folderNode");
                            String fileName = (String) data.get("fileName");
                            String fileUrl = (String) data.get("fileUrl");
                            String coverPicUrl = (String) data.get("coverPicUrl");
                            String startTime = (String) data.get("startTime");
                            String stopTime = (String) data.get("stopTime");
                            Integer fileSize = (Integer) data.get("fileSize");
                            
                            logger.info("剪辑文件查询成功，文件名: {}, 文件节点: {}", fileName, folderNode);
                            logger.info("文件URL: {}", fileUrl);
                            
                            Map<String, Object> queryResult = new HashMap<>();
                            queryResult.put("success", true);
                            queryResult.put("status", "completed"); // 能查询到文件表示已完成
                            queryResult.put("fileId", folderNode);
                            queryResult.put("fileName", fileName);
                            queryResult.put("fileUrl", fileUrl);
                            queryResult.put("coverPicUrl", coverPicUrl);
                            queryResult.put("startTime", startTime);
                            queryResult.put("stopTime", stopTime);
                            queryResult.put("fileSize", fileSize);
                            return queryResult;
                        } else {
                            logger.error("查询剪辑状态失败: 无返回文件数据");
                            return createErrorResult("无返回文件数据");
                        }
                    } else {
                        logger.error("查询剪辑状态失败: 无返回数据");
                        return createErrorResult("无返回数据");
                    }
                } else if (code == 404) {
                    // 404表示资源不存在，可能是剪辑任务还未完成
                    logger.info("剪辑任务还未完成，资源不存在");
                    Map<String, Object> queryResult = new HashMap<>();
                    queryResult.put("success", true);
                    queryResult.put("status", "processing");
                    return queryResult;
                } else {
                    String errorMsg = meta.get("message").toString();
                    logger.error("查询剪辑状态失败: {}", errorMsg);
                    return createErrorResult(errorMsg);
                }
            } else {
                logger.error("查询剪辑状态失败: 响应格式错误");
                return createErrorResult("响应格式错误");
            }
        } catch (Exception e) {
            logger.error("查询剪辑状态异常: {}", e.getMessage(), e);
            return createErrorResult(e.getMessage());
        }
    }

    private Map<String, Object> createErrorResult(String errorMessage) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", errorMessage);
        return result;
    }

    public Map<String, Object> waitForClipComplete(String taskId, int maxWaitSeconds) {
        logger.info("等待剪辑完成，任务ID: {}, 最大等待时间: {}秒", taskId, maxWaitSeconds);
        
        for (int i = 0; i < maxWaitSeconds; i++) {
            Map<String, Object> result = queryClipStatus(taskId);
            
            if (!((Boolean) result.get("success"))) {
                logger.error("查询剪辑状态失败: {}", result.get("error"));
                return result;
            }
            
            String status = (String) result.get("status");
            logger.info("剪辑状态: {} (等待 {}/{} 秒)", status, i + 1, maxWaitSeconds);
            
            // 剪辑完成状态
            if ("completed".equals(status)) {
                logger.info("剪辑任务完成");
                return result;
            }
            
            // 等待1秒后再次查询
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.error("等待被中断: {}", e.getMessage());
                return createErrorResult("等待被中断: " + e.getMessage());
            }
        }
        
        logger.error("等待剪辑完成超时");
        return createErrorResult("等待剪辑完成超时，超过最大等待时间 " + maxWaitSeconds + " 秒");
    }
}