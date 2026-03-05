package com.firesmokemonitor;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FireSmokeMonitor {
    private static final Logger logger = LogManager.getLogger(FireSmokeMonitor.class);
    private static final String CAPTURE_URL = "https://open.ys7.com/api/open/cloud/v1/capture/save";
    private static final String FIRE_SMOKE_DETECT_URL = "https://open.ys7.com/api/service/intelligence/algo/analysis/firesmoke_detection";
    
    private String accessToken;
    private String deviceSerial;
    private int channelNo;
    private String projectId;
    private String voiceFilePath;
    private boolean enableCloudCleanup;
    private String storageBasePath;
    
    private FireSmokeDetector fireSmokeDetector;
    private FireSmokeAlert fireSmokeAlert;
    
    private Map<String, Object> testResults;
    private List<Map<String, Object>> steps;
    
    public FireSmokeMonitor(String accessToken, String deviceSerial, int channelNo, 
                     String projectId, String voiceFilePath, boolean enableCloudCleanup,
                     String storageBasePath) {
        this.accessToken = accessToken;
        this.deviceSerial = deviceSerial;
        this.channelNo = channelNo;
        this.projectId = projectId;
        this.voiceFilePath = voiceFilePath;
        this.enableCloudCleanup = enableCloudCleanup;
        this.storageBasePath = storageBasePath;
        
        this.fireSmokeDetector = new FireSmokeDetector(accessToken);
        this.fireSmokeAlert = new FireSmokeAlert(accessToken, deviceSerial);
        
        this.testResults = new HashMap<>();
        this.steps = new ArrayList<>();
        this.testResults.put("start_time", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        this.testResults.put("steps", steps);
        this.testResults.put("success", false);
        this.testResults.put("alert_triggered", false);
        this.testResults.put("cloud_file_cleaned", false);
        
        System.out.println("烟火监控配置信息:");
        System.out.println("   设备序列号: " + deviceSerial);
        System.out.println("   通道号: " + channelNo);
        System.out.println("   项目ID: " + projectId);
        System.out.println("   云端清理: " + (enableCloudCleanup ? "启用" : "禁用"));
    }
    

    
    public void logStep(String stepName, boolean success, String details) {
        Map<String, Object> stepRecord = new HashMap<>();
        stepRecord.put("name", stepName);
        stepRecord.put("success", success);
        stepRecord.put("details", details);
        stepRecord.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        steps.add(stepRecord);
        
        String status = success ? "true" : "false";
        System.out.println(status + " " + stepName + ": " + details);
    }
    
    public Map<String, Object> captureImage() {
        System.out.println("\n1. 执行烟火监控抓拍...");
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String fileId = "pet_capture_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            
            // 构建请求参数
            URIBuilder uriBuilder = new URIBuilder(CAPTURE_URL);
            uriBuilder.addParameter("accessToken", accessToken);
            uriBuilder.addParameter("channelNo", String.valueOf(channelNo));
            uriBuilder.addParameter("deviceSerial", deviceSerial);
            uriBuilder.addParameter("fileId", fileId);
            uriBuilder.addParameter("projectId", projectId);
            uriBuilder.addParameter("captureType", "1");
            
            URI uri = uriBuilder.build();
            HttpPost httpPost = new HttpPost(uri);
            httpPost.setHeader("accessToken", accessToken);
            httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
            
            var response = httpClient.execute(httpPost);
            int statusCode = response.getCode();
            System.out.println(" 抓拍响应: " + statusCode);
            
            String responseBody = EntityUtils.toString(response.getEntity());
            var gson = new com.google.gson.Gson();
            var result = gson.fromJson(responseBody, Map.class);
            var meta = (Map<String, Object>) result.get("meta");
            
            if (meta != null) {
                Object codeObj = meta.get("code");
                int code = 0;
                if (codeObj instanceof Number) {
                    code = ((Number) codeObj).intValue();
                }
                
                if (code == 200) {
                    Object dataObj = result.get("data");
                    if (dataObj != null) {
                        String imageUrl = dataObj.toString();
                        
                        if (imageUrl != null && imageUrl.startsWith("http")) {
                            logStep("烟火监控抓拍", true, "抓拍成功");
                            Map<String, Object> captureResult = new HashMap<>();
                            captureResult.put("success", true);
                            captureResult.put("image_url", imageUrl);
                            captureResult.put("file_id", fileId);
                            captureResult.put("timestamp", LocalDateTime.now());
                            captureResult.put("device_serial", deviceSerial);
                            captureResult.put("channel_no", channelNo);
                            return captureResult;
                        } else {
                            logStep("烟火监控抓拍", false, "图片URL无效: " + imageUrl);
                            return null;
                        }
                    } else {
                        logStep("烟火监控抓拍", false, "无返回数据");
                        return null;
                    }
                } else {
                    String errorMsg = meta.get("message").toString();
                    logStep("烟火监控抓拍", false, "API错误: " + errorMsg);
                    return null;
                }
            } else {
                logStep("烟火监控抓拍", false, "响应格式错误，无meta信息");
                return null;
            }
        } catch (Exception e) {
            logStep("烟火监控抓拍", false, "异常: " + e.getMessage());
            return null;
        }
    }
    
    public Map<String, Object> detectFireSmoke(String imageUrl) {
        System.out.println("\n2. 执行烟火识别...");
        return fireSmokeDetector.detectFireSmoke(imageUrl);
    }
    
    public Map<String, String> saveResults(String imageUrl, Map<String, Object> captureResult, 
                                         Map<String, Object> detectionResult) {
        System.out.println("\n3. 保存监控结果...");
        try {
            // 创建存储目录
            String dateFolder = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String dateFolderPath = storageBasePath + File.separator + dateFolder;
            Files.createDirectories(Paths.get(dateFolderPath));
            
            // 下载图片
            String timestamp = ((LocalDateTime) captureResult.get("timestamp")).format(DateTimeFormatter.ofPattern("HHmmss"));
            String filename = "fire_smoke_capture_" + timestamp + ".jpg";
            String filePath = dateFolderPath + File.separator + filename;
            
            // 检查是否是本地文件路径
            if (imageUrl.startsWith("file:")) {
                // 本地文件路径，直接复制
                String localPath = imageUrl.substring(5); // 移除"file:"前缀
                java.nio.file.Path sourcePath = Paths.get(localPath);
                java.nio.file.Path targetPath = Paths.get(filePath);
                Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else if (imageUrl.startsWith("http")) {
                // URL路径，下载图片
                try (var in = new URL(imageUrl).openStream();
                     var out = new FileOutputStream(filePath)) {
                    in.transferTo(out);
                }
            } else {
                // 本地文件路径（没有file:前缀），直接复制
                java.nio.file.Path sourcePath = Paths.get(imageUrl);
                java.nio.file.Path targetPath = Paths.get(filePath);
                Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            
            // 保存识别结果
            String resultFilename = "fire_smoke_result_" + timestamp + ".json";
            String resultPath = dateFolderPath + File.separator + resultFilename;
            
            // 创建可序列化的结果数据
            Map<String, Object> resultData = new HashMap<>();
            
            // 处理capture_info，确保LocalDateTime可序列化
            Map<String, Object> captureInfo = new HashMap<>();
            captureInfo.put("success", captureResult.get("success"));
            captureInfo.put("image_url", captureResult.get("image_url"));
            captureInfo.put("file_id", captureResult.get("file_id"));
            captureInfo.put("timestamp", ((LocalDateTime) captureResult.get("timestamp")).format(DateTimeFormatter.ISO_DATE_TIME));
            captureInfo.put("device_serial", captureResult.get("device_serial"));
            captureInfo.put("channel_no", captureResult.get("channel_no"));
            resultData.put("capture_info", captureInfo);
            
            resultData.put("detection_result", detectionResult);
            
            Map<String, Object> storageInfo = new HashMap<>();
            storageInfo.put("image_path", filePath);
            storageInfo.put("result_path", resultPath);
            storageInfo.put("saved_time", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            resultData.put("storage_info", storageInfo);
            
            var gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            Files.write(Paths.get(resultPath), gson.toJson(resultData).getBytes());
            
            logStep("保存结果", true, "图片和结果保存成功");
            Map<String, String> paths = new HashMap<>();
            paths.put("image_path", filePath);
            paths.put("result_path", resultPath);
            return paths;
            
        } catch (Exception e) {
            logStep("保存结果", false, "异常: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 使用本地图片路径创建模拟的抓拍结果
     * @param localImagePath 本地图片路径
     * @return 模拟的抓拍结果
     */
    public Map<String, Object> createMockCaptureResult(String localImagePath) {
        System.out.println("\n1. 使用本地图片作为抓拍结果...");
        try {
            // 验证文件是否存在
            java.nio.file.Path imagePath = Paths.get(localImagePath);
            if (!Files.exists(imagePath)) {
                logStep("使用本地图片", false, "本地图片不存在: " + localImagePath);
                return null;
            }
            
            // 创建模拟的抓拍结果
            Map<String, Object> captureResult = new HashMap<>();
            captureResult.put("success", true);
            captureResult.put("image_url", localImagePath); // 直接使用本地路径
            captureResult.put("file_id", "mock_pet_capture_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
            captureResult.put("timestamp", LocalDateTime.now());
            captureResult.put("device_serial", deviceSerial);
            captureResult.put("channel_no", channelNo);
            
            logStep("使用本地图片", true, "本地图片加载成功: " + localImagePath);
            return captureResult;
            
        } catch (Exception e) {
            logStep("使用本地图片", false, "异常: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 运行带mock的烟火监控测试
     * @param localImagePath 本地图片路径
     * @return 测试结果
     */
    public Map<String, Object> runFireSmokeMonitorTestWithMock(String localImagePath) {
        System.out.println("=" + "=".repeat(58));
        System.out.println(" 开始烟火监控测试（使用本地图片）");
        System.out.println("=" + "=".repeat(58));
        System.out.println("测试时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("设备: " + deviceSerial + ", 通道: " + channelNo);
        System.out.println("云端清理: " + (enableCloudCleanup ? "启用" : "禁用"));
        System.out.println("使用本地图片: " + localImagePath);
        System.out.println("=" + "=".repeat(58));
        
        try {
            // 步骤1: 使用本地图片作为抓拍结果
            Map<String, Object> captureResult = createMockCaptureResult(localImagePath);
            if (captureResult == null) {
                finalizeTest(false, null, false);
                return testResults;
            }
            
            // 步骤2: 烟火识别
            Map<String, Object> detectionResult = detectFireSmoke(captureResult.get("image_url").toString());
            if (detectionResult == null) {
                finalizeTest(false, null, false);
                return testResults;
            }
            
            // 步骤3: 保存结果
            saveResults(captureResult.get("image_url").toString(), captureResult, detectionResult);
            
            // 步骤4: 检查烟火状态并播放告警
            boolean isFireSmokeDetected = checkFireSmokeStatus(detectionResult);
            if (isFireSmokeDetected) {
                playFireSmokeAlert();
            } else {
                logStep("烟火告警", true, "未检测到烟火，跳过播报");
            }
            
            // 总结测试结果
            finalizeTest(true, detectionResult, isFireSmokeDetected);
            return testResults;
            
        } catch (Exception e) {
            logger.error("测试过程异常: " + e.getMessage(), e);
            finalizeTest(false, null, false);
            return testResults;
        }
    }
    
    public boolean checkFireSmokeStatus(Map<String, Object> detectionResult) {
        System.out.println("\n4. 检查烟火状态...");
        try {
            boolean isFireSmokeDetected = (boolean) detectionResult.getOrDefault("is_fire_smoke_detected", false);
            String fireSmokeStatus = (String) detectionResult.getOrDefault("fire_smoke_status", "未检测到烟火");
            
            logStep("烟火状态检查", true, "烟火检测: " + (isFireSmokeDetected ? "是" : "否") + ", 状态: " + fireSmokeStatus);
            return isFireSmokeDetected;
            
        } catch (Exception e) {
            logStep("烟火状态检查", false, "异常: " + e.getMessage());
            return false;
        }
    }
    
    public boolean playFireSmokeAlert() {
        System.out.println("\n5. 播放烟火告警...");
        try {
            if (voiceFilePath == null || voiceFilePath.isEmpty()) {
                logStep("烟火告警", false, "语音文件路径未配置");
                return false;
            }
            
            boolean alertSuccess = fireSmokeAlert.playFireSmokeAlert(voiceFilePath);
            
            if (alertSuccess) {
                logStep("烟火告警", true, "告警播报成功");
                return true;
            } else {
                logStep("烟火告警", false, "语音播报失败");
                return false;
            }
            
        } catch (Exception e) {
            logStep("烟火告警", false, "异常: " + e.getMessage());
            return false;
        }
    }
    
    public Map<String, Object> runFireSmokeMonitorTest() {
        System.out.println("=" + "=".repeat(58));
        System.out.println(" 开始烟火监控测试");
        System.out.println("=" + "=".repeat(58));
        System.out.println("测试时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("设备: " + deviceSerial + ", 通道: " + channelNo);
        System.out.println("云端清理: " + (enableCloudCleanup ? "启用" : "禁用"));
        System.out.println("=" + "=".repeat(58));
        
        try {
            // 步骤1: 烟火监控抓拍
            Map<String, Object> captureResult = captureImage();
            if (captureResult == null) {
                finalizeTest(false, null, false);
                return testResults;
            }
            
            // 步骤2: 烟火识别
            Map<String, Object> detectionResult = detectFireSmoke(captureResult.get("image_url").toString());
            if (detectionResult == null) {
                finalizeTest(false, null, false);
                return testResults;
            }
            
            // 步骤3: 保存结果
            saveResults(captureResult.get("image_url").toString(), captureResult, detectionResult);
            
            // 步骤4: 检查烟火状态并播放告警
            boolean isFireSmokeDetected = checkFireSmokeStatus(detectionResult);
            if (isFireSmokeDetected) {
                playFireSmokeAlert();
            } else {
                logStep("烟火告警", true, "未检测到烟火，跳过播报");
            }
            
            // 总结测试结果
            finalizeTest(true, detectionResult, isFireSmokeDetected);
            return testResults;
            
        } catch (Exception e) {
            logger.error("测试过程异常: " + e.getMessage(), e);
            finalizeTest(false, null, false);
            return testResults;
        }
    }
    

    

    

    
    private void finalizeTest(boolean success, Map<String, Object> detectionResult, boolean alertTriggered) {
        boolean allStepsSuccess = steps.stream().allMatch(step -> (boolean) step.get("success"));
        testResults.put("success", success && allStepsSuccess);
        testResults.put("end_time", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        
        String startTimeStr = (String) testResults.get("start_time");
        String endTimeStr = (String) testResults.get("end_time");
        LocalDateTime startTime = LocalDateTime.parse(startTimeStr, DateTimeFormatter.ISO_DATE_TIME);
        LocalDateTime endTime = LocalDateTime.parse(endTimeStr, DateTimeFormatter.ISO_DATE_TIME);
        long durationSeconds = java.time.Duration.between(startTime, endTime).getSeconds();
        testResults.put("duration", durationSeconds);
        
        if (detectionResult != null) {
            testResults.put("detection_result", detectionResult);
            testResults.put("alert_triggered", alertTriggered);
        }
        
        printTestSummary();
    }
    
    private void printTestSummary() {
        System.out.println("\n" + "=" + "=".repeat(58));
        System.out.println(" 烟火监控测试总结");
        System.out.println("=" + "=".repeat(58));
        
        int totalSteps = steps.size();
        int successfulSteps = (int) steps.stream().filter(step -> (boolean) step.get("success")).count();
        
        String startTimeStr = (String) testResults.get("start_time");
        String endTimeStr = (String) testResults.get("end_time");
        LocalDateTime startTime = LocalDateTime.parse(startTimeStr, DateTimeFormatter.ISO_DATE_TIME);
        LocalDateTime endTime = LocalDateTime.parse(endTimeStr, DateTimeFormatter.ISO_DATE_TIME);
        System.out.println("测试时间: " + startTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " - " + 
                         endTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        System.out.println("总耗时: " + testResults.get("duration") + "秒");
        System.out.println("步骤完成: " + successfulSteps + "/" + totalSteps);
        System.out.println("整体结果: " + ((boolean) testResults.get("success") ? "成功" : "失败"));
        
        if (testResults.containsKey("detection_result")) {
            Map<String, Object> dr = (Map<String, Object>) testResults.get("detection_result");
            System.out.println("\n识别结果:");
            System.out.println("  烟火检测: " + (((boolean) dr.getOrDefault("is_fire_smoke_detected", false)) ? "是" : "否"));
            System.out.println("  烟火状态: " + dr.getOrDefault("fire_smoke_status", "未检测到烟火"));
            System.out.println("  告警触发: " + (((boolean) testResults.getOrDefault("alert_triggered", false)) ? "是" : "否"));
        }
        
        if ((boolean) testResults.get("success")) {
            System.out.println("\n 测试通过！烟火监控系统功能正常。");
        } else {
            System.out.println("\n 失败的步骤:");
            for (Map<String, Object> step : steps) {
                if (!(boolean) step.get("success")) {
                    System.out.println("  - " + step.get("name") + ": " + step.get("details"));
                }
            }
        }
        
        System.out.println("=" + "=".repeat(58));
    }
    
    public Map<String, Object> getTestResults() {
        return testResults;
    }
}