# 宠物监控系统 (Java版)

## 项目概述

这是一个基于萤石云平台的AI视觉识别系统，用于实时监控家庭或宠物区域，自动检测是否有宠物出现，并在检测到宠物时触发语音告警，帮助用户及时了解宠物的动态。

## 核心功能

1. **宠物监控抓拍**：通过萤石云API远程控制摄像头抓拍图片，获取实时监控画面
2. **宠物智能识别**：使用萤石云AI算法分析图片，检测是否有宠物以及宠物的具体类型
3. **智能告警系统**：当检测到宠物时，触发语音告警通知相关人员
4. **结果存储管理**：自动保存监控图片和识别结果到本地文件系统
5. **宠物状态检查**：判断宠物状态是否正常，提供详细的识别结果
6. **录制计划管理**：创建临时录制计划，用于录制宠物活动视频
7. **灵活配置系统**：通过环境变量实现个性化配置，支持不同部署场景

## 技术架构

- **语言**：Java 11
- **构建工具**：Maven
- **HTTP客户端**：Apache HttpClient 5
- **JSON处理**：Gson
- **环境变量**：java-dotenv
- **日志系统**：Log4j2

## 项目结构

```
pet-monitor/
├── pom.xml                # Maven配置文件
├── log4j2.xml             # 日志配置文件
├── src/
│   └── main/
│       └── java/
│           └── com/
│               ├── petmonitor/            # 宠物监控模块
│               │   ├── Main.java          # 主函数
│               │   ├── PetMonitor.java    # 核心监控器
│               │   ├── PetDetector.java   # 宠物识别服务
│               │   ├── PetAlert.java      # 宠物告警服务
│               │   └── VideoManager.java  # 视频管理服务
├── .env                   # 环境配置文件
├── 告警语音.MP3           # 语音告警文件
└── pet_monitor_results/   # 宠物监控结果存储目录
```

## 配置说明

创建 `.env` 文件，配置以下参数：

```env
# 宠物监控系统配置

# 萤石云访问令牌
PET_ACCESS_TOKEN=at.xxxxxxxxxxxx

# 设备序列号
PET_DEVICE_SERIAL=XXXXXXXXX

# 通道号（默认为1）
PET_CHANNEL_NO=1

# 项目ID
PET_PROJECT_ID=XX

# 语音告警文件路径
PET_VOICE_FILE_PATH=./告警语音.mp3

# 是否启用云端清理（默认为true）
PET_ENABLE_CLOUD_CLEANUP=true

# 存储基础路径（默认为./pet_monitor_results）
PET_STORAGE_BASE_PATH=./pet_monitor_results

# 空间ID
PET_SPACE_ID=xxxxxx
```

## 构建和运行

### 构建项目

```bash
mvn clean package
```

### 运行项目

```bash
java -jar target/pet-monitor-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### 使用本地图片进行测试

```bash
java -jar target/pet-monitor-1.0-SNAPSHOT-jar-with-dependencies.jar /path/to/local/image.jpg
```

## 运行流程

1. **云抓拍**：调用萤石云API进行远程抓拍，获取设备实时画面
2. **宠物识别**：使用萤石云AI算法分析图片，检测是否有宠物
3. **结果保存**：将抓拍到的图片和识别结果保存到本地文件系统
4. **宠物状态检查**：分析识别结果，判断宠物状态是否正常
5. **语音告警**：如果检测到宠物，播放语音告警通知
6. **创建录制计划**：为检测到的宠物活动创建临时录制计划
7. **测试总结**：生成详细的测试结果报告

## 测试结果

运行完成后，系统会生成以下文件：

- `pet_test_results_yyyyMMdd_HHmmss.json`：宠物监控测试结果文件
- `pet_monitor_results/yyyy-MM-dd/`：按日期分类的宠物监控结果
  - `pet_capture_HHmmss.jpg`：抓拍到的宠物图片
  - `pet_result_HHmmss.json`：宠物识别结果

## 依赖项

- org.apache.httpcomponents.client5:httpclient5:5.3
- com.google.code.gson:gson:2.10.1
- io.github.cdimascio:java-dotenv:5.2.2
- org.apache.logging.log4j:log4j-core:2.20.0
- org.apache.logging.log4j:log4j-api:2.20.0
- org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0

## 注意事项

1. 确保网络连接正常，能够访问萤石云API
2. 确保设备已添加到萤石云账号并在线
3. 确保已开通萤石云的AI识别服务
4. 语音文件必须为MP3格式
5. 配置正确的项目ID和空间ID

## 故障排查

- **无法连接萤石云**：检查网络连接和访问令牌
- **识别失败**：检查AI服务是否开通，设备是否有画面
- **语音告警失败**：检查设备是否支持对讲功能
- **存储失败**：检查存储路径权限
- **项目ID错误**：确保使用正确的项目ID
- **空间ID错误**：确保使用正确的空间ID

## 系统特点

1. **模块化设计**：各功能模块职责清晰，便于维护和扩展
2. **完整的错误处理**：提供详细的日志和错误信息
3. **可序列化的数据处理**：确保时间字段正确序列化
4. **灵活的配置系统**：支持通过.env文件和环境变量配置
5. **完整的测试流程**：提供详细的测试结果和步骤记录
6. **支持本地图片测试**：方便开发和调试
7. **录制计划管理**：自动创建临时录制计划

## 许可证

MIT
