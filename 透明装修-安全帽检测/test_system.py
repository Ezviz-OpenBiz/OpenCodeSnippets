# real_process_test_with_cleanup.py
import os
import time
import json
import logging
import requests
from datetime import datetime
from dotenv import load_dotenv

# 加载环境变量
load_dotenv()

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler("real_process_test.log"),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

# 读取固定配置
YS_ACCESS_TOKEN = os.getenv("YS_ACCESS_TOKEN")
DEVICE_SERIAL = os.getenv("DEVICE_SERIAL")
CHANNEL_NO = int(os.getenv("CAPTURE_CHANNEL_NO", "1"))
PROJECT_ID = os.getenv("CAPTURE_PROJECT_ID")
VOICE_FILE_PATH = os.getenv("VOICE_FILE_PATH")
# 新增配置：是否启用云端图片清理
ENABLE_CLOUD_CLEANUP = os.getenv("ENABLE_CLOUD_CLEANUP", "1").lower() in ("1", "true", "yes")

# 接口URL
CAPTURE_URL = "https://open.ys7.com/api/open/cloud/v1/capture/save"
HELMET_DETECT_URL = "https://open.ys7.com/api/service/intelligence/algo/analysis/helmet_detection"
# 使用临时语音下发接口
VOICE_SENDONCE_URL = "https://open.ys7.com/api/lapp/voice/sendonce"
# 新增删除文件接口URL
DELETE_FILE_URL = "https://open.ys7.com/api/open/cloud/v1/file"


class CloudFileManager:
    """云端文件管理器 - 负责文件的创建和清理"""

    def __init__(self, access_token, project_id):
        self.access_token = access_token
        self.project_id = project_id

    def delete_cloud_file(self, file_id: str) -> bool:
        """
        删除云端文件
        Args:
            file_id: 要删除的文件ID
        Returns:
            删除是否成功
        """
        print(f"尝试删除云端文件: {file_id}")
        try:
            # 准备删除请求参数
            params = {
                "accessToken": self.access_token,
                "fileId": file_id,
                "projectId": self.project_id
            }

            headers = {
                "User-Agent": "SafetyHelmetMonitor/1.0"
            }

            # 发送DELETE请求
            response = requests.delete(DELETE_FILE_URL, params=params, headers=headers, timeout=10)

            print(f"删除文件响应状态码: {response.status_code}")
            print(f"删除文件响应内容: {response.text}")

            if response.status_code == 200:
                result = response.json()
                meta = result.get("meta", {})

                if meta.get("code") == 200:
                    logger.info(f"云端文件删除成功: {file_id}")
                    return True
                else:
                    error_msg = meta.get("message", "未知错误")
                    error_code = meta.get("code")
                    logger.warning(f"云端文件删除失败 (code={error_code}): {error_msg}")
                    return False
            else:
                logger.warning(f"⚠删除请求失败，状态码: {response.status_code}")
                return False

        except Exception as e:
            logger.error(f"删除云端文件异常: {str(e)}")
            return False


class VoiceAlert:
    """语音告警类 - 支持直接使用本地语音文件下发"""

    def __init__(self, access_token, device_serial):
        self.access_token = access_token
        self.device_serial = device_serial

    def send_voice_alert_from_local(self, voice_file_path: str, channel_no: int = 1) -> dict:
        """直接使用本地语音文件发送语音告警"""
        # 验证文件是否存在
        if not os.path.exists(voice_file_path):
            return {"error": "本地语音文件不存在", "code": "10001"}

        # 验证文件是否为MP3格式
        if not voice_file_path.lower().endswith(('.mp3', '.mpeg')):
            return {"error": "语音文件格式不支持，仅支持MP3", "code": "10002"}

        file_handle = None
        try:
            # 打开本地文件
            file_handle = open(voice_file_path, 'rb')
            files = {
                'voiceFile': (os.path.basename(voice_file_path), file_handle, 'audio/mpeg')
            }

            data = {
                "accessToken": self.access_token,
                "deviceSerial": self.device_serial,
                "channelNo": channel_no
            }

            logger.info(f"从本地发送语音告警: 设备{self.device_serial}, 文件{voice_file_path}")
            response = requests.post(VOICE_SENDONCE_URL, files=files, data=data, timeout=15)
            response.raise_for_status()

            result = response.json()
            logger.info(f"本地语音下发响应: {result}")
            return result

        except requests.exceptions.Timeout:
            return {"error": "语音下发请求超时", "code": "20008"}
        except Exception as e:
            return {"error": f"语音下发异常: {str(e)}", "code": "49999"}
        finally:
            # 确保文件被关闭
            if file_handle:
                file_handle.close()

    def play_alert_voice(self, local_file_path: str = None):
        """播放告警语音 - 支持直接使用本地文件路径"""
        try:
            # 优先使用传入的本地路径，其次使用配置的本地路径
            voice_path = local_file_path or VOICE_FILE_PATH
            if not voice_path:
                logger.error("语音文件路径未配置")
                return False

            logger.info(f"准备播放本地语音告警: 设备{self.device_serial}, 文件{voice_path}")

            # 直接使用本地文件发送
            result = self.send_voice_alert_from_local(voice_path, CHANNEL_NO)

            if "error" in result:
                logger.error(f"语音播报失败: {result['error']}")
                return False

            code = result.get("code")
            if code == "200":
                logger.info("本地语音播报成功")
                return True
            else:
                error_mapping = {
                    "10001": "参数不合法",
                    "20002": "设备不存在",
                    "10031": "权限不足",
                    "20018": "用户不拥有该设备",
                    "20007": "设备不在线",
                    "20008": "设备响应超时",
                    "20001": "通道不存在",
                    "20015": "设备不支持对讲",
                    "111000": "资源包余量不足",
                }
                error_msg = error_mapping.get(code, f"未知错误码: {code}")
                logger.error(f"语音播报失败: {error_msg}")
                return False

        except Exception as e:
            logger.error(f"语音播报异常: {str(e)}")
            return False


class RealProcessTester:
    """真实流程测试器 - 集成云端图片清理功能"""

    def __init__(self):
        print(" 初始化真实流程测试器...")

        # 验证必要配置
        if not all([YS_ACCESS_TOKEN, DEVICE_SERIAL, PROJECT_ID]):
            missing = []
            if not YS_ACCESS_TOKEN: missing.append("YS_ACCESS_TOKEN")
            if not DEVICE_SERIAL: missing.append("DEVICE_SERIAL")
            if not PROJECT_ID: missing.append("CAPTURE_PROJECT_ID")
            raise ValueError(f" 缺少必要配置: {', '.join(missing)}")

        # 存储配置
        self.storage_base_path = os.getenv("STORAGE_BASE_PATH", "./real_capture_images")
        if not os.path.exists(self.storage_base_path):
            os.makedirs(self.storage_base_path)

        # 告警配置
        self.alert_threshold = int(os.getenv("ALERT_THRESHOLD", "1"))

        # 初始化服务模块
        self.voice_alert = VoiceAlert(YS_ACCESS_TOKEN, DEVICE_SERIAL)
        self.cloud_manager = CloudFileManager(YS_ACCESS_TOKEN, PROJECT_ID)

        # 测试结果记录
        self.test_results = {
            "start_time": datetime.now(),
            "steps": [],
            "success": False,
            "end_time": None,
            "duration": 0,
            "detection_result": {},
            "alert_triggered": False,
            "cloud_file_cleaned": False
        }

        print(f"配置信息:")
        print(f"   设备序列号: {DEVICE_SERIAL}")
        print(f"   通道号: {CHANNEL_NO}")
        print(f"   项目ID: {PROJECT_ID}")
        print(f"   云端清理: {'启用' if ENABLE_CLOUD_CLEANUP else '禁用'}")

    def log_step(self, step_name: str, success: bool, details: str = ""):
        """记录测试步骤"""
        step_record = {
            "name": step_name,
            "success": success,
            "details": details,
            "timestamp": datetime.now().isoformat()
        }
        self.test_results["steps"].append(step_record)

        status = "ture" if success else "false"
        print(f"{status} {step_name}: {details}")

    def cloud_capture_sync(self):
        """同步云抓拍"""
        print("\n1. 执行同步云抓拍...")
        try:
            file_id = f"capture_{datetime.now().strftime('%Y%m%d_%H%M%S')}"

            data = {
                "accessToken": YS_ACCESS_TOKEN,
                "channelNo": str(CHANNEL_NO),
                "deviceSerial": DEVICE_SERIAL,
                "fileId": file_id,
                "projectId": PROJECT_ID,
                "captureType": "1"
            }

            headers = {
                "accessToken": YS_ACCESS_TOKEN,
                "Content-Type": "application/x-www-form-urlencoded"
            }

            response = requests.post(CAPTURE_URL, data=data, headers=headers, timeout=30)
            print(f" 抓拍响应: {response.status_code}")

            result = response.json()
            meta = result.get("meta", {})

            if meta.get("code") == 200:
                image_url = result.get("data", "")

                if image_url and image_url.startswith("http"):
                    self.log_step("同步云抓拍", True, f"抓拍成功")
                    return {
                        "success": True,
                        "image_url": image_url,
                        "file_id": file_id,
                        "timestamp": datetime.now(),
                        "device_serial": DEVICE_SERIAL,
                        "channel_no": CHANNEL_NO
                    }
                else:
                    self.log_step("同步云抓拍", False, f"图片URL无效")
                    return None
            else:
                error_msg = meta.get("message", "未知错误")
                self.log_step("同步云抓拍", False, f"API错误: {error_msg}")
                return None

        except Exception as e:
            self.log_step("同步云抓拍", False, f"异常: {str(e)}")
            return None

    def ai_helmet_detection(self, image_url: str):
        """AI安全帽识别"""
        print("\n2. 执行AI安全帽识别...")
        try:
            headers = {
                "accessToken": YS_ACCESS_TOKEN,
                "Content-Type": "application/json"
            }

            request_body = {
                "stream": False,
                "dataInfo": [{"data": image_url, "type": "url", "modal": "image"}],
                "dataParams": [{"modal": "image", "img_width": 1280, "img_height": 720}]
            }

            response = requests.post(HELMET_DETECT_URL, headers=headers, json=request_body, timeout=20)
            result = response.json()
            meta = result.get("meta", {})

            if meta.get("code") != 200:
                error_msg = meta.get("message", "无错误描述")
                raise ValueError(f"识别接口错误: {error_msg}")

            data = result.get("data", {})
            images = data.get("images", [])

            if not images:
                detection_result = {
                    "person_count": 0, "unworn_count": 0, "worn_count": 0,
                    "conclusion": "未检测到人员", "is_alert": False
                }
                self.log_step("AI安全帽识别", True, "未检测到人员")
                return detection_result

            content_ann = images[0].get("contentAnn", {})
            bboxes = content_ann.get("bboxes", [])
            person_count = len(bboxes)
            unworn_count = 0

            for bbox in bboxes:
                tag_info = bbox.get("tagInfo", {})
                labels = tag_info.get("labels", [])
                for label in labels:
                    if label.get("key") == "category" and label.get("label") == "no":
                        unworn_count += 1
                        break

            worn_count = person_count - unworn_count
            is_alert = unworn_count > 0
            conclusion = "存在未佩戴安全帽情况" if is_alert else "安全状态"

            detection_result = {
                "person_count": person_count, "unworn_count": unworn_count,
                "worn_count": worn_count, "conclusion": conclusion, "is_alert": is_alert
            }

            self.log_step("AI安全帽识别", True, f"识别完成: {person_count}人, 未佩戴: {unworn_count}人")
            return detection_result

        except Exception as e:
            self.log_step("AI安全帽识别", False, f"异常: {str(e)}")
            return None

    def download_and_save_image(self, image_url: str, capture_result: dict, detection_result: dict):
        """下载图片并保存识别结果"""
        print("\n3. 下载图片并保存结果...")
        try:
            date_folder = datetime.now().strftime("%Y-%m-%d")
            date_folder_path = os.path.join(self.storage_base_path, date_folder)
            if not os.path.exists(date_folder_path):
                os.makedirs(date_folder_path)

            filename = f"capture_{capture_result['timestamp'].strftime('%H%M%S')}.jpg"
            file_path = os.path.join(date_folder_path, filename)

            response = requests.get(image_url, timeout=30)
            response.raise_for_status()

            with open(file_path, 'wb') as f:
                f.write(response.content)

            result_filename = f"result_{capture_result['timestamp'].strftime('%H%M%S')}.json"
            result_path = os.path.join(date_folder_path, result_filename)

            result_data = {
                "capture_info": {**capture_result, "timestamp": capture_result["timestamp"].isoformat()},
                "detection_result": detection_result,
                "storage_info": {
                    "image_path": file_path, "result_path": result_path,
                    "saved_time": datetime.now().isoformat()
                }
            }

            with open(result_path, 'w', encoding='utf-8') as f:
                json.dump(result_data, f, indent=2, ensure_ascii=False)

            self.log_step("下载保存", True, "图片和结果保存成功")
            return file_path, result_path

        except Exception as e:
            self.log_step("下载保存", False, f"异常: {str(e)}")
            return None, None

    def check_alert_condition(self, detection_result: dict):
        """检查告警条件"""
        print("\n4. 检查告警条件...")
        try:
            unworn_count = detection_result.get("unworn_count", 0)
            should_alert = unworn_count >= self.alert_threshold
            self.log_step("告警检查", True, f"未佩戴: {unworn_count}, 需要告警: {'是' if should_alert else '否'}")
            return should_alert
        except Exception as e:
            self.log_step("告警检查", False, f"异常: {str(e)}")
            return False

    def play_voice_alert(self):
        """播放语音告警 - 使用本地文件"""
        print("\n5. 播放语音告警...")
        try:
            if not VOICE_FILE_PATH:
                self.log_step("语音告警", False, "语音文件路径未配置")
                return False

            # 直接使用本地文件路径
            voice_success = self.voice_alert.play_alert_voice(VOICE_FILE_PATH)

            if voice_success:
                self.log_step("语音告警", True, "告警播报成功")
                return True
            else:
                self.log_step("语音告警", False, "语音播报失败")
                return False

        except Exception as e:
            self.log_step("语音告警", False, f"异常: {str(e)}")
            return False

    def cleanup_cloud_file(self, file_id: str, detection_result: dict):
        """清理云端文件 - 如果不是告警图片则删除"""
        print("\n6. 检查云端文件清理...")

        if not ENABLE_CLOUD_CLEANUP:
            self.log_step("云端清理", True, "功能已禁用，跳过清理")
            return False

        try:
            is_alert = detection_result.get("is_alert", False)
            unworn_count = detection_result.get("unworn_count", 0)

            if not is_alert and unworn_count == 0:
                # 非告警图片，执行清理
                cleanup_success = self.cloud_manager.delete_cloud_file(file_id)

                if cleanup_success:
                    self.log_step("云端清理", True, "非告警图片已从云端删除")
                    self.test_results["cloud_file_cleaned"] = True
                    return True
                else:
                    self.log_step("云端清理", False, "云端文件删除失败")
                    return False
            else:
                # 告警图片，保留在云端
                alert_reason = "存在未佩戴安全帽" if is_alert else "检测到人员但无需告警"
                self.log_step("云端清理", True, f"告警图片保留在云端: {alert_reason}")
                return True

        except Exception as e:
            self.log_step("云端清理", False, f"异常: {str(e)}")
            return False

    def run_real_process_test(self):
        """运行真实流程测试"""
        print("=" * 60)
        print(" 开始真实流程测试")
        print("=" * 60)
        print(f"测试时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"设备: {DEVICE_SERIAL}, 通道: {CHANNEL_NO}")
        print(f"告警阈值: {self.alert_threshold}人, 云端清理: {'启用' if ENABLE_CLOUD_CLEANUP else '禁用'}")
        print("=" * 60)

        try:
            # 步骤1: 同步云抓拍
            capture_result = self.cloud_capture_sync()
            if not capture_result:
                self.finalize_test(False)
                return self.test_results

            # 步骤2: AI安全帽识别
            detection_result = self.ai_helmet_detection(capture_result["image_url"])
            if not detection_result:
                self.finalize_test(False)
                return self.test_results

            # 步骤3: 下载图片并保存结果
            self.download_and_save_image(capture_result["image_url"], capture_result, detection_result)

            # 步骤4: 检查告警并播放语音
            should_alert = self.check_alert_condition(detection_result)
            if should_alert:
                self.play_voice_alert()
            else:
                self.log_step("语音告警", True, "无需告警，跳过播报")

            # 步骤5: 云端文件清理（如果不是告警图片）
            self.cleanup_cloud_file(capture_result["file_id"], detection_result)

            # 总结测试结果
            self.finalize_test(True, detection_result, should_alert)
            return self.test_results

        except Exception as e:
            logger.error(f"测试过程异常: {str(e)}")
            self.finalize_test(False)
            return self.test_results

    def finalize_test(self, success: bool, detection_result: dict = None, alert_triggered: bool = False):
        """最终化测试结果"""
        self.test_results["success"] = success and all(step["success"] for step in self.test_results["steps"])
        self.test_results["end_time"] = datetime.now()
        self.test_results["duration"] = (
                self.test_results["end_time"] - self.test_results["start_time"]).total_seconds()

        if detection_result:
            self.test_results["detection_result"] = detection_result
            self.test_results["alert_triggered"] = alert_triggered

        self.print_test_summary()

    def print_test_summary(self):
        """打印测试总结"""
        print("\n" + "=" * 60)
        print(" 真实流程测试总结")
        print("=" * 60)

        total_steps = len(self.test_results["steps"])
        successful_steps = sum(1 for step in self.test_results["steps"] if step["success"])

        print(
            f"测试时间: {self.test_results['start_time'].strftime('%H:%M:%S')} - {self.test_results['end_time'].strftime('%H:%M:%S')}")
        print(f"总耗时: {self.test_results['duration']:.2f}秒")
        print(f"步骤完成: {successful_steps}/{total_steps}")
        print(f"整体结果: {'成功' if self.test_results['success'] else '失败'}")

        if 'detection_result' in self.test_results:
            dr = self.test_results['detection_result']
            print(f"\n识别结果:")
            print(f"  总人数: {dr.get('person_count', 0)}")
            print(f"  未佩戴: {dr.get('unworn_count', 0)}")
            print(f"  结论: {dr.get('conclusion', '未知')}")
            print(f"  告警触发: {'是' if self.test_results['alert_triggered'] else '否'}")
            print(f"  云端清理: {'是' if self.test_results.get('cloud_file_cleaned') else '否'}")

        if self.test_results["success"]:
            print("\n 测试通过！系统功能正常。")
        else:
            print("\n 失败的步骤:")
            for step in self.test_results["steps"]:
                if not step["success"]:
                    print(f"  - {step['name']}: {step['details']}")

        print("=" * 60)


def main():
    """主函数"""
    try:
        # 检查必要配置
        required_env_vars = ["YS_ACCESS_TOKEN", "DEVICE_SERIAL", "CAPTURE_PROJECT_ID"]
        missing_vars = [var for var in required_env_vars if not os.getenv(var)]

        if missing_vars:
            print(f" 缺少配置: {', '.join(missing_vars)}")
            return

        # 运行测试
        tester = RealProcessTester()
        results = tester.run_real_process_test()

        # 保存结果
        if results["end_time"]:
            results_file = f"test_results_{results['start_time'].strftime('%Y%m%d_%H%M%S')}.json"
            with open(results_file, 'w', encoding='utf-8') as f:
                serializable_results = results.copy()
                serializable_results["start_time"] = serializable_results["start_time"].isoformat()
                serializable_results["end_time"] = serializable_results["end_time"].isoformat()
                json.dump(serializable_results, f, indent=2, ensure_ascii=False)

            print(f"\n 结果已保存: {results_file}")

    except Exception as e:
        print(f" 测试失败: {str(e)}")


if __name__ == "__main__":
    main()