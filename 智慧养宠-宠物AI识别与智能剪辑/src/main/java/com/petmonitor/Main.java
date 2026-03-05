package com.petmonitor;

import io.github.cdimascio.dotenv.Dotenv;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.io.FileWriter;
import java.io.IOException;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);
    
    public static void main(String[] args) {
        System.out.println("宠物监控系统启动...");
        
        try {
            // 加载配置
            Dotenv dotenv = Dotenv.configure()
                    .directory(".")
                    .ignoreIfMalformed()
                    .ignoreIfMissing()
                    .load();
            
            // 获取配置参数
            String accessToken = dotenv.get("PET_ACCESS_TOKEN");
            String deviceSerial = dotenv.get("PET_DEVICE_SERIAL");
            String channelNoStr = dotenv.get("PET_CHANNEL_NO", "1");
            String projectId = dotenv.get("PET_PROJECT_ID", "PET_MONITOR");
            String voiceFilePath = dotenv.get("PET_VOICE_FILE_PATH");
            String enableCloudCleanupStr = dotenv.get("PET_ENABLE_CLOUD_CLEANUP", "true");
            String storageBasePath = dotenv.get("PET_STORAGE_BASE_PATH", "./pet_monitor_results");
            String petType = dotenv.get("PET_TYPE", "dog");
            String spaceIdStr = dotenv.get("PET_SPACE_ID", "182756");
            
            // 验证必要配置
            if (accessToken == null || accessToken.isEmpty()) {
                System.err.println("错误: 未配置PET_ACCESS_TOKEN环境变量");
                return;
            }
            
            if (deviceSerial == null || deviceSerial.isEmpty()) {
                System.err.println("错误: 未配置PET_DEVICE_SERIAL环境变量");
                return;
            }
            
            // 解析配置
            int channelNo = Integer.parseInt(channelNoStr);
            boolean enableCloudCleanup = Boolean.parseBoolean(enableCloudCleanupStr);
            long spaceId = Long.parseLong(spaceIdStr);
            
            // 初始化宠物监控器
            PetMonitor petMonitor = new PetMonitor(
                accessToken,
                deviceSerial,
                channelNo,
                projectId,
                voiceFilePath,
                enableCloudCleanup,
                storageBasePath,
                petType,
                spaceId
            );
            
            // 检查是否使用mock测试
            Map<String, Object> testResults;
            if (args.length > 0) {
                // 使用命令行参数作为本地图片路径
                String localImagePath = args[0];
                System.out.println("使用本地图片进行mock测试: " + localImagePath);
                testResults = petMonitor.runPetMonitorTestWithMock(localImagePath);
            } else {
                // 运行常规测试
                testResults = petMonitor.runPetMonitorTest();
            }
            
            // 保存测试结果
            saveTestResults(testResults);
            
        } catch (Exception e) {
            logger.error("系统启动异常: " + e.getMessage(), e);
            System.err.println("系统启动失败: " + e.getMessage());
        }
    }
    
    private static void saveTestResults(Map<String, Object> testResults) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String resultFile = "pet_test_results_" + timestamp + ".json";
            
            var gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            String jsonResult = gson.toJson(testResults);
            
            try (FileWriter writer = new FileWriter(resultFile)) {
                writer.write(jsonResult);
            }
            
            System.out.println("\n 结果已保存: " + resultFile);
            
        } catch (IOException e) {
            logger.error("保存测试结果失败: " + e.getMessage(), e);
            System.err.println("保存测试结果失败: " + e.getMessage());
        }
    }
    
    private static String getEnvOrDefault(Dotenv dotenv, String key, String defaultValue) {
        String value = dotenv.get(key);
        return value != null ? value : defaultValue;
    }
}