package com.billiardsmonitor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

public class BilliardsMonitor {
    private static final Logger logger = LogManager.getLogger(BilliardsMonitor.class);
    
    private ConfigManager config;
    private VideoRecorder videoRecorder;
    private VideoLocator videoLocator;
    private BilliardsAnalyzer billiardsAnalyzer;
    private VideoClipper videoClipper;
    private ClipFileQuery clipFileQuery;
    
    public BilliardsMonitor() {
        this.config = ConfigManager.getInstance();
        this.videoRecorder = new VideoRecorder(
            config.getAccessToken(),
            config.getDeviceSerial(),
            config.getChannelNo(),
            config.getSpaceId()
        );
        this.videoLocator = new VideoLocator(config.getAccessToken());
        this.billiardsAnalyzer = new BilliardsAnalyzer(config.getAccessToken());
        this.videoClipper = new VideoClipper(config.getAccessToken(), config.getDeviceSerial(), config.getChannelNo());
        this.clipFileQuery = new ClipFileQuery(config.getAccessToken());
    }

    public void startFullProcess() {
        logger.info("开始台球监控完整流程");
        
        try {
            // 步骤1: 创建一次性录制计划
            logger.info("步骤1: 创建一次性录制计划");
            int recordDuration = config.getRecordDurationSeconds();
            logger.info("录制时长: {}秒", recordDuration);
            Map<String, Object> recordResult = videoRecorder.createOneTimeRecordPlan(recordDuration);
            if (!((Boolean) recordResult.get("success"))) {
                logger.error("创建录制计划失败: {}", recordResult.get("error"));
                return;
            }
            String planId = (String) recordResult.get("planId");
            logger.info("录制计划创建成功，计划ID: {}", planId);
            
            // 等待60秒，确保录制计划完成并生成文件
            logger.info("等待60秒，确保录制计划完成并生成文件...");
            try {
                Thread.sleep(60000);
            } catch (InterruptedException e) {
                logger.error("等待被中断: {}", e.getMessage());
            }
            
            // 步骤2: 文件元数据搜索
            logger.info("步骤2: 文件元数据搜索");
            Map<String, Object> searchResult = videoLocator.searchRecordedFiles(config.getVodSpaceId(), planId, 300);
            if (!((Boolean) searchResult.get("success"))) {
                logger.error("文件元数据搜索失败: {}", searchResult.get("error"));
                return;
            }
            String folderNode = (String) searchResult.get("folderNode");
            String fileName = (String) searchResult.get("fileName");
            String videoUrl = (String) searchResult.get("fileUrl");
            logger.info("文件元数据搜索成功，文件名: {}, 文件节点: {}", fileName, folderNode);
            logger.info("文件URL: {}", videoUrl);
            
            // 步骤3: 分析视频
            logger.info("步骤3: 分析视频");
            Map<String, Object> analyzeResult = billiardsAnalyzer.analyzeBilliards(videoUrl);
            if (!((Boolean) analyzeResult.get("success"))) {
                logger.error("视频分析失败: {}", analyzeResult.get("error"));
                return;
            }
            String requestId = (String) analyzeResult.get("requestId");
            logger.info("视频分析任务创建成功，请求ID: {}", requestId);
            logger.info("分析结果将通过云信令推送，消息类型: ys.open.ai.resultData");
            
            // 由于是异步分析，流程到此结束
            // 实际的精彩瞬间分析结果需要通过云信令消息获取
            // 后续的剪辑操作需要在接收到分析结果后手动触发
            
            logger.info("台球监控完整流程执行完毕");
            
        } catch (Exception e) {
            logger.error("流程执行异常: {}", e.getMessage(), e);
        }
    }
}