# 台球精彩瞬间智能监控与剪辑系统

## 项目简介

本项目是基于萤石开放平台API开发的台球精彩瞬间智能监控与剪辑系统，能够自动录制台球比赛、分析精彩瞬间，并生成精彩片段剪辑。

## 功能特性

- **自动录制**：创建一次性录制计划，自动录制台球比赛
- **智能分析**：使用AI算法检测台球比赛中的精彩瞬间（击球、进球等）
- **自动剪辑**：根据分析结果自动剪辑精彩片段
- **转场效果**：支持添加淡入淡出等转场效果
- **云存储**：剪辑结果存储到云点播平台，提供可播放链接

## 技术栈

- **开发语言**：Java 8+
- **构建工具**：Maven 3.6+
- **HTTP客户端**：Apache HttpClient 5
- **JSON处理**：Gson
- **日志框架**：Log4j2
- **配置管理**：.env文件

## 环境要求

- JDK 8或更高版本
- Maven 3.6或更高版本
- 萤石开放平台账号
- 已开通的服务：
  - 云录制服务
  - 云点播服务
  - AI算法服务（台球分析）
  - 云信令服务（接收分析结果推送）

## 安装与配置

### 1. 克隆项目

```bash
git clone <项目地址>
cd demo-billiards
```

### 2. 配置.env文件

在项目根目录创建`.env`文件，填写以下配置：

```env
# 萤石开放平台配置
BILLIARDS_ACCESS_TOKEN=your_access_token
BILLIARDS_DEVICE_SERIAL=your_device_serial
BILLIARDS_CHANNEL_NO=1
BILLIARDS_VOD_SPACE_ID=your_vod_space_id
BILLIARDS_RECORD_DURATION=300
```

**配置说明**：
- `BILLIARDS_ACCESS_TOKEN`：萤石开放API访问令牌
- `BILLIARDS_DEVICE_SERIAL`：设备序列号
- `BILLIARDS_CHANNEL_NO`：通道号，默认为1
- `BILLIARDS_VOD_SPACE_ID`：云点播空间ID
- `BILLIARDS_RECORD_DURATION`：录制时长（秒），默认300秒

### 3. 构建项目

```bash
mvn clean package -DskipTests
```

## 快速开始

### 1. 运行主程序

```bash
java -jar target/billiards-monitor-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### 2. 查看日志

程序运行后，会在控制台输出执行日志，包括：
- 录制计划创建结果
- 文件搜索结果
- 视频分析任务创建结果
- 剪辑任务创建结果
- 剪辑完成状态

## 项目结构

```
demo-billiards/
├── src/main/java/com/billiardsmonitor/
│   ├── Main.java                  # 程序入口
│   ├── ConfigManager.java         # 配置管理
│   ├── BilliardsMonitor.java      # 主流程控制
│   ├── VideoRecorder.java         # 创建录制计划
│   ├── VideoLocator.java          # 文件搜索和地址获取
│   ├── BilliardsAnalyzer.java     # 视频分析
│   ├── VideoClipper.java          # 视频剪辑
│   └── ClipFileQuery.java         # 剪辑文件查询
├── src/main/resources/
│   └── log4j2.xml                 # 日志配置
├── .env                           # 配置文件
├── pom.xml                        # Maven配置
└── README.md                      # 项目说明
```

## 核心流程

1. **创建录制计划**：调用一次性录制计划接口录制台球比赛
2. **文件元数据搜索**：搜索录制完成的视频文件并获取播放地址
3. **视频分析**：调用台球分析算法接口检测精彩瞬间（异步分析）
4. **视频剪辑**：根据检测到的精彩瞬间创建剪辑任务
5. **等待剪辑完成并获取地址**：轮询等待剪辑任务完成并获取可播放URL

## 配置说明

### 萤石开放平台配置

1. **获取Access Token**：
   - 登录萤石开放平台
   - 在开发者中心创建应用
   - 使用AppKey和AppSecret获取Access Token

2. **设备配置**：
   - 将设备添加到萤石账号
   - 确保设备在线且能正常录制

3. **服务开通**：
   - 开通云录制服务
   - 开通云点播服务
   - 开通AI算法服务（台球分析）
   - 开通云信令服务

## 注意事项

1. **Access Token有效期**：Access Token默认有效期为7天，需要定期更新
2. **录制时长**：建议根据实际比赛情况调整录制时长
3. **网络环境**：确保设备网络稳定，带宽满足视频传输需求
4. **云存储空间**：剪辑后的视频会存储到云点播空间，注意空间容量
5. **API调用限制**：萤石开放平台有API调用频率限制，避免过于频繁的调用

## 故障排除

### 常见问题

1. **录制计划创建失败**
   - 检查Access Token是否有效
   - 检查设备是否在线
   - 检查设备是否支持云录制

2. **文件搜索失败**
   - 检查云点播空间ID是否正确
   - 等待足够时间后再搜索（建议等待60秒）
   - 检查录制计划是否成功执行

3. **视频分析失败**
   - 检查AI算法服务是否开通
   - 检查视频文件是否过大（建议不超过2G）
   - 检查网络连接是否稳定

4. **剪辑任务失败**
   - 检查设备序列号和通道号是否正确
   - 检查云点播服务是否正常
   - 检查剪辑参数是否符合要求

### 日志查看

详细日志会输出到控制台，包含API调用、错误信息等。如果遇到问题，请查看日志获取详细信息。

## 相关文档

- [萤石开放平台文档](https://open.ys7.com/help)
- [云录制API文档](https://open.ys7.com/help/4444)
- [台球分析算法API文档](https://open.ys7.com/help/4444)
- [视频剪辑API文档](https://open.ys7.com/help/2863)

## 许可证

本项目采用MIT许可证。
