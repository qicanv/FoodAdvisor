from flask import Flask, request, jsonify
import requests
import logging
import os

app = Flask(__name__)
logging.basicConfig(level=logging.INFO)

# 请创建并替换为服务端AK
BAIDU_MAP_AK = "naT7GCQRPdJs4a22oe33dsoFXPV3Oku6"  # 服务端AK
BAIDU_API_URL = "https://api.map.baidu.com/directionlite/v1/driving"  # 驾车路线规划

@app.route('/')
def index():
    # 提供前端页面
    return app.send_static_file('index.html')

@app.route('/api/route')
def get_route():
    """获取驾车路线规划"""
    origin = request.args.get('origin')
    destination = request.args.get('destination')
    
    if not origin or not destination:
        return jsonify({"status": 1, "message": "缺少起点或终点参数"}), 400
    
    # 构造请求参数
    params = {
        "ak": BAIDU_MAP_AK,
        "origin": origin,
        "destination": destination,
        "coord_type": "bd09ll",  # 坐标类型
        "ret_coordtype": "bd09ll",  # 返回坐标类型
        "output": "json"
    }
    
    try:
        app.logger.info(f"请求百度API: origin={origin}, destination={destination}")
        response = requests.get(BAIDU_API_URL, params=params, timeout=10)
        response.raise_for_status()
        data = response.json()
        
        # 检查百度API返回的状态
        if data.get('status') != 0:
            app.logger.error(f"百度API错误: {data}")
            return jsonify({"status": data.get('status', 1), "message": data.get('message', '未知错误')}), 500
            
        return jsonify(data)
        
    except requests.exceptions.RequestException as e:
        app.logger.error(f"请求百度API失败: {e}")
        return jsonify({"status": 1, "message": f"网络请求失败: {str(e)}"}), 500
    except Exception as e:
        app.logger.error(f"处理请求时发生错误: {e}")
        return jsonify({"status": 1, "message": f"服务器内部错误: {str(e)}"}), 500

if __name__ == '__main__':
    # 创建static目录（如果不存在）
    if not os.path.exists('static'):
        os.makedirs('static')
    
    # 将index.html移动到static目录
    if os.path.exists('index.html'):
        import shutil
        shutil.move('index.html', 'static/index.html')
    
    app.run(host='0.0.0.0', port=5000, debug=True)
