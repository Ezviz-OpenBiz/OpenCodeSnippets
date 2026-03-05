package com.firesmokemonitor;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class FireSmokeAlert {
    private static final Logger logger = LogManager.getLogger(FireSmokeAlert.class);
    private static final String VOICE_SENDONCE_URL = "https://open.ys7.com/api/lapp/voice/sendonce";
    
    private String accessToken;
    private String deviceSerial;
    
    public FireSmokeAlert(String accessToken, String deviceSerial) {
        this.accessToken = accessToken;
        this.deviceSerial = deviceSerial;
    }
    
    public Map<String, Object> sendVoiceAlertFromLocal(String voiceFilePath, int channelNo) {
        Map<String, Object> result = new HashMap<>();
        
        // 验证文件是否存在
        File voiceFile = new File(voiceFilePath);
        if (!voiceFile.exists()) {
            result.put("error", "本地语音文件不存在");
            result.put("code", "10001");
            return result;
        }
        
        // 验证文件是否为MP3格式
        String fileName = voiceFile.getName().toLowerCase();
        if (!fileName.endsWith(".mp3") && !fileName.endsWith(".mpeg")) {
            result.put("error", "语音文件格式不支持，仅支持MP3");
            result.put("code", "10002");
            return result;
        }
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(VOICE_SENDONCE_URL);
            
            // 构建multipart请求
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addTextBody("accessToken", accessToken);
            builder.addTextBody("deviceSerial", deviceSerial);
            builder.addTextBody("channelNo", String.valueOf(channelNo));
            builder.addBinaryBody(
                "voiceFile",
                voiceFile,
                ContentType.create("audio/mpeg"),
                voiceFile.getName()
            );
            
            httpPost.setEntity(builder.build());
            
            logger.info("发送烟火告警语音: 设备" + deviceSerial + ", 文件" + voiceFilePath);
            var response = httpClient.execute(httpPost);
            String responseBody = EntityUtils.toString(response.getEntity());
            
            logger.info("烟火告警语音下发响应: " + responseBody);
            
            var gson = new com.google.gson.Gson();
            return gson.fromJson(responseBody, Map.class);
            
        } catch (java.net.SocketTimeoutException e) {
            result.put("error", "语音下发请求超时");
            result.put("code", "20008");
            return result;
        } catch (Exception e) {
            result.put("error", "语音下发异常: " + e.getMessage());
            result.put("code", "49999");
            return result;
        }
    }
    
    public boolean playFireSmokeAlert(String localFilePath) {
        try {
            if (localFilePath == null || localFilePath.isEmpty()) {
                logger.error("语音文件路径未配置");
                return false;
            }
            
            logger.info("准备播放烟火告警语音: 设备" + deviceSerial + ", 文件" + localFilePath);
            
            // 直接使用本地文件发送
            Map<String, Object> result = sendVoiceAlertFromLocal(localFilePath, 1);
            
            if (result.containsKey("error")) {
                logger.error("烟火告警语音播报失败: " + result.get("error"));
                return false;
            }
            
            String code = result.get("code").toString();
            if ("200".equals(code)) {
                logger.info("烟火告警语音播报成功");
                return true;
            } else {
                Map<String, String> errorMapping = new HashMap<>();
                errorMapping.put("10001", "参数不合法");
                errorMapping.put("20002", "设备不存在");
                errorMapping.put("10031", "权限不足");
                errorMapping.put("20018", "用户不拥有该设备");
                errorMapping.put("20007", "设备不在线");
                errorMapping.put("20008", "设备响应超时");
                errorMapping.put("20001", "通道不存在");
                errorMapping.put("20015", "设备不支持对讲");
                errorMapping.put("111000", "资源包余量不足");
                
                String errorMsg = errorMapping.getOrDefault(code, "未知错误码: " + code);
                logger.error("烟火告警语音播报失败: " + errorMsg);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("烟火告警语音播报异常: " + e.getMessage(), e);
            return false;
        }
    }
}