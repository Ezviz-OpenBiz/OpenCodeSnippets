package com.billiardsmonitor;

import io.github.cdimascio.dotenv.Dotenv;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConfigManager {
    private static final Logger logger = LogManager.getLogger(ConfigManager.class);
    private static ConfigManager instance;
    private Dotenv dotenv;

    private ConfigManager() {
        // 加载.env文件
        String currentDir = System.getProperty("user.dir");
        logger.info("Current working directory: " + currentDir);
        
        // 直接读取.env文件内容，用于调试
        try {
            java.io.File envFile = new java.io.File(currentDir + "/.env");
            if (envFile.exists()) {
                logger.info(".env文件存在，大小: " + envFile.length() + " bytes");
                // 使用UTF-8编码读取文件
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(envFile), "UTF-8"));
                String line;
                int lineNumber = 1;
                boolean foundVodSpaceId = false;
                while ((line = reader.readLine()) != null) {
                    logger.info(".env文件第" + lineNumber + "行: '" + line + "'");
                    if (line.startsWith("BILLIARDS_VOD_SPACE_ID=")) {
                        logger.info("找到BILLIARDS_VOD_SPACE_ID配置: '" + line + "'");
                        foundVodSpaceId = true;
                    }
                    lineNumber++;
                }
                reader.close();
                if (!foundVodSpaceId) {
                    logger.warn("未在.env文件中找到BILLIARDS_VOD_SPACE_ID配置");
                }
            } else {
                logger.warn(".env文件不存在");
            }
        } catch (Exception e) {
            logger.warn("读取.env文件失败: " + e.getMessage());
        }
        
        // 尝试不同的加载方式
        try {
            // 方式1: 使用默认加载
            dotenv = Dotenv.load();
            logger.info("使用默认加载方式");
        } catch (Exception e) {
            logger.warn("默认加载方式失败: " + e.getMessage());
            // 方式2: 明确指定目录
            dotenv = Dotenv.configure()
                    .directory(currentDir)
                    .load();
            logger.info("使用指定目录加载方式");
        }
        
        logger.info("配置加载完成");
        logger.info("BILLIARDS_VOD_SPACE_ID value: '" + dotenv.get("BILLIARDS_VOD_SPACE_ID") + "'");
        logger.info("BILLIARDS_SPACE_ID value: '" + dotenv.get("BILLIARDS_SPACE_ID") + "'");
        logger.info("BILLIARDS_ACCESS_TOKEN value: '" + dotenv.get("BILLIARDS_ACCESS_TOKEN") + "'");
        
        // 打印所有环境变量
        logger.info("系统环境变量 BILLIARDS_VOD_SPACE_ID: '" + System.getenv("BILLIARDS_VOD_SPACE_ID") + "'");
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public String getAccessToken() {
        return dotenv.get("BILLIARDS_ACCESS_TOKEN");
    }

    public String getDeviceSerial() {
        return dotenv.get("BILLIARDS_DEVICE_SERIAL");
    }

    public int getChannelNo() {
        return Integer.parseInt(dotenv.get("BILLIARDS_CHANNEL_NO", "1"));
    }

    public String getProjectId() {
        return dotenv.get("BILLIARDS_PROJECT_ID");
    }

    public long getSpaceId() {
        return Long.parseLong(dotenv.get("BILLIARDS_SPACE_ID"));
    }

    public long getVodSpaceId() {
        // 尝试从dotenv获取
        String vodSpaceId = dotenv.get("BILLIARDS_VOD_SPACE_ID");
        if (vodSpaceId != null && !vodSpaceId.isEmpty()) {
            return Long.parseLong(vodSpaceId);
        }
        
        // 如果dotenv获取失败，直接从文件读取
        try {
            String currentDir = System.getProperty("user.dir");
            java.io.File envFile = new java.io.File(currentDir + "/.env");
            if (envFile.exists()) {
                // 使用UTF-8编码读取文件
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(envFile), "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("BILLIARDS_VOD_SPACE_ID=")) {
                        String value = line.substring("BILLIARDS_VOD_SPACE_ID=".length()).trim();
                        if (!value.isEmpty()) {
                            reader.close();
                            logger.info("从.env文件直接读取BILLIARDS_VOD_SPACE_ID: " + value);
                            return Long.parseLong(value);
                        }
                    }
                }
                reader.close();
            }
        } catch (Exception e) {
            logger.warn("直接从文件读取BILLIARDS_VOD_SPACE_ID失败: " + e.getMessage());
        }
        
        // 如果所有方法都失败，抛出异常
        throw new RuntimeException("未配置BILLIARDS_VOD_SPACE_ID");
    }

    public String getStorageBasePath() {
        return dotenv.get("BILLIARDS_STORAGE_BASE_PATH", "./billiards_monitor_results");
    }

    public int getRecordDurationSeconds() {
        return Integer.parseInt(dotenv.get("BILLIARDS_RECORD_DURATION_SECONDS", "60"));
    }

    public boolean validateConfig() {
        if (getAccessToken() == null || getAccessToken().isEmpty()) {
            logger.error("未配置BILLIARDS_ACCESS_TOKEN");
            return false;
        }
        if (getDeviceSerial() == null || getDeviceSerial().isEmpty()) {
            logger.error("未配置BILLIARDS_DEVICE_SERIAL");
            return false;
        }
        if (getProjectId() == null || getProjectId().isEmpty()) {
            logger.error("未配置BILLIARDS_PROJECT_ID");
            return false;
        }
        if (dotenv.get("BILLIARDS_SPACE_ID") == null || dotenv.get("BILLIARDS_SPACE_ID").isEmpty()) {
            logger.error("未配置BILLIARDS_SPACE_ID");
            return false;
        }
        // 验证BILLIARDS_VOD_SPACE_ID
        boolean vodSpaceIdValid = false;
        // 尝试从dotenv获取
        String vodSpaceId = dotenv.get("BILLIARDS_VOD_SPACE_ID");
        if (vodSpaceId != null && !vodSpaceId.isEmpty()) {
            vodSpaceIdValid = true;
        } else {
            // 如果dotenv获取失败，直接从文件读取
            try {
                String currentDir = System.getProperty("user.dir");
                java.io.File envFile = new java.io.File(currentDir + "/.env");
                if (envFile.exists()) {
                    // 使用UTF-8编码读取文件
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(envFile), "UTF-8"));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("BILLIARDS_VOD_SPACE_ID=")) {
                            String value = line.substring("BILLIARDS_VOD_SPACE_ID=".length()).trim();
                            if (!value.isEmpty()) {
                                vodSpaceIdValid = true;
                                logger.info("从.env文件验证BILLIARDS_VOD_SPACE_ID: " + value);
                            }
                            break;
                        }
                    }
                    reader.close();
                }
            } catch (Exception e) {
                logger.warn("验证BILLIARDS_VOD_SPACE_ID失败: " + e.getMessage());
            }
        }
        if (!vodSpaceIdValid) {
            logger.error("未配置BILLIARDS_VOD_SPACE_ID");
            return false;
        }
        return true;
    }

    public void printConfig() {
        logger.info("台球监控系统配置:");
        logger.info("  设备序列号: {}", getDeviceSerial());
        logger.info("  通道号: {}", getChannelNo());
        logger.info("  项目ID: {}", getProjectId());
        logger.info("  空间ID: {}", getSpaceId());
        logger.info("  录制时长: {}秒", getRecordDurationSeconds());
        logger.info("  存储路径: {}", getStorageBasePath());
    }
}