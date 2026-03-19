package com.billiardsmonitor;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class VideoLocator {
    private static final Logger logger = LogManager.getLogger(VideoLocator.class);
    private static final String GET_FILE_URL = "https://open.ys7.com/api/service/vod/video/address/get";
    private static final String SEARCH_FILE_URL = "https://open.ys7.com/api/service/open/vod/file/listById";
    
    private String accessToken;

    public VideoLocator(String accessToken) {
        this.accessToken = accessToken;
    }

    public Map<String, Object> getFileAddress(String fileId, String projectId) {
        logger.info("获取文件地址，文件ID: {}, 项目ID: {}", fileId, projectId);
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            URIBuilder uriBuilder = new URIBuilder(GET_FILE_URL);
            uriBuilder.addParameter("fileNodeIds", fileId);
            uriBuilder.addParameter("expire", "3600");
            uriBuilder.addParameter("protocol", "hls");
            
            URI uri = uriBuilder.build();
            HttpGet httpGet = new HttpGet(uri);
            httpGet.addHeader("accessToken", accessToken);
            
            logger.debug("获取文件地址请求: {}", uri.toString());
            
            org.apache.hc.core5.http.ClassicHttpResponse response = httpClient.execute(httpGet);
            int statusCode = response.getCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            
            logger.debug("获取文件地址响应状态码: {}", statusCode);
            logger.debug("获取文件地址响应: {}", responseBody);
            
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
                        Map<String, Object> data = (Map<String, Object>) dataObj;
                        String url = (String) data.get("url");
                        String rtmpUrl = (String) data.get("rtmpUrl");
                        String hlsUrl = (String) data.get("hlsUrl");
                        
                        logger.info("获取文件地址成功");
                        
                        Map<String, Object> locatorResult = new HashMap<>();
                        locatorResult.put("success", true);
                        locatorResult.put("url", url);
                        locatorResult.put("rtmpUrl", rtmpUrl);
                        locatorResult.put("hlsUrl", hlsUrl);
                        return locatorResult;
                    } else {
                        logger.error("获取文件地址失败: 无返回数据");
                        return createErrorResult("无返回数据");
                    }
                } else {
                    String errorMsg = meta.get("message").toString();
                    logger.error("获取文件地址失败: {}", errorMsg);
                    return createErrorResult(errorMsg);
                }
            } else {
                logger.error("获取文件地址失败: 响应格式错误");
                return createErrorResult("响应格式错误");
            }
        } catch (Exception e) {
            logger.error("获取文件地址异常: {}", e.getMessage(), e);
            return createErrorResult(e.getMessage());
        }
    }

    public Map<String, Object> searchRecordedFiles(long spaceId, String planId, int waitSeconds) {
        logger.info("搜索录制文件，空间ID: {}, 计划ID: {}, 等待时间: {}秒", spaceId, planId, waitSeconds);
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            for (int i = 0; i < waitSeconds; i++) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.error("等待被中断: {}", e.getMessage());
                    break;
                }
                
                LocalDateTime startTime = LocalDateTime.now().minusMinutes(5);
                LocalDateTime endTime = LocalDateTime.now();
                
                HttpPost httpPost = new HttpPost(SEARCH_FILE_URL);
                httpPost.addHeader("accessToken", accessToken);
                httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
                
                String tagIdStr = planId;
                if (planId.contains(".")) {
                    tagIdStr = planId.substring(0, planId.indexOf("."));
                }
                
                URIBuilder uriBuilder = new URIBuilder(SEARCH_FILE_URL);
                uriBuilder.addParameter("spaceIds", String.valueOf(spaceId));
                uriBuilder.addParameter("fileType", "1");
                uriBuilder.addParameter("fileChildType", "10");
                uriBuilder.addParameter("startTime", startTime.format(formatter));
                uriBuilder.addParameter("endTime", endTime.format(formatter));
                uriBuilder.addParameter("pageSize", "10");
                uriBuilder.addParameter("tagId", tagIdStr);
                
                StringEntity entity = new StringEntity(uriBuilder.build().getQuery());
                httpPost.setEntity(entity);
                
                logger.debug("搜索录制文件请求: {}", SEARCH_FILE_URL);
                logger.debug("搜索参数: spaceIds={}, fileType={}, fileChildType={}, startTime={}, endTime={}, tagId={}", 
                        spaceId, 1, 10, startTime.format(formatter), endTime.format(formatter), tagIdStr);
                
                org.apache.hc.core5.http.ClassicHttpResponse response = httpClient.execute(httpPost);
                int statusCode = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                
                logger.debug("搜索录制文件响应状态码: {}", statusCode);
                logger.debug("搜索录制文件响应: {}", responseBody);
                
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
                            Map<String, Object> data = (Map<String, Object>) dataObj;
                            java.util.List<Map<String, Object>> resultList = (java.util.List<Map<String, Object>>) data.get("result");
                            
                            if (resultList != null && !resultList.isEmpty()) {
                                Map<String, Object> firstFile = resultList.get(0);
                                String folderNode = (String) firstFile.get("folderNode");
                                String fileName = (String) firstFile.get("fileName");
                                
                                logger.info("找到录制文件，文件名: {}, 文件节点: {}", fileName, folderNode);
                                
                                Map<String, Object> searchResult = new HashMap<>();
                                searchResult.put("success", true);
                                searchResult.put("folderNode", folderNode);
                                searchResult.put("fileName", fileName);
                                searchResult.put("fileData", firstFile);
                                
                                String fileUrl = null;
                                Map<String, Object> urls = (Map<String, Object>) firstFile.get("urls");
                                if (urls != null && !urls.isEmpty()) {
                                    fileUrl = (String) urls.values().iterator().next();
                                    logger.info("文件URL: {}", fileUrl);
                                    searchResult.put("fileUrl", fileUrl);
                                }
                                
                                return searchResult;
                            }
                        }
                    } else {
                        String errorMsg = meta.get("message").toString();
                        logger.debug("搜索录制文件失败: {}", errorMsg);
                    }
                }
                
                logger.info("未找到录制文件，继续等待... ({}/{})", i + 1, waitSeconds);
            }
            
            logger.error("搜索录制文件超时");
            return createErrorResult("搜索录制文件超时");
            
        } catch (Exception e) {
            logger.error("搜索录制文件异常: {}", e.getMessage(), e);
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
