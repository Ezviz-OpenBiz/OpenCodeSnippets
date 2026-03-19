package com.billiardsmonitor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);
    
    public static void main(String[] args) {
        logger.info("台球监控系统启动...");
        
        try {
            // 加载配置
            ConfigManager config = ConfigManager.getInstance();
            if (!config.validateConfig()) {
                logger.error("配置验证失败，系统退出");
                return;
            }
            config.printConfig();
            
            // 执行主流程
            logger.info("开始执行主流程...");
            BilliardsMonitor monitor = new BilliardsMonitor();
            monitor.startFullProcess();
            
        } catch (Exception e) {
            logger.error("系统异常: {}", e.getMessage(), e);
        }
    }
}