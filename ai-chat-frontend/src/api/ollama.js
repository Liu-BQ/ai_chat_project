import axios from 'axios';
import qs from 'qs'; // 用于将参数转为 form-urlencoded 格式

// 基础地址（后端服务地址）
const baseUrl = 'http://localhost:8080/api/ollama';

// 1. 获取模型列表（对应后端 /models 接口）
export const getModelList = async () => {
    try {
        const res = await axios.get(`${baseUrl}/models`);
        if (res.data.code === '200' && res.data.data.length > 0) {
            return res.data.data; // 返回模型列表数组（如 ["qwen:latest", "llama3:8b"]）
        }
        throw new Error('获取模型列表失败');
    } catch (err) {
        console.error('模型列表接口异常：', err);
        return [];
    }
};

// 2. 非流式聊天请求（Form 表单格式提交）
export const sendChat = async (model, message) => {
    try {
        const res = await axios.post(
            `${baseUrl}/chat`,
            qs.stringify({ model, message }), // 转为 form-urlencoded 格式（关键）
            {
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded' // 声明 Form 表单格式
                }
            }
        );
        return res.data;
    } catch (err) {
        console.error('非流式聊天异常：', err);
        throw err;
    }
};

// 3. 流式聊天请求（Form 表单格式提交，SSE 流式响应）
export const sendStreamChat = async (model, message, onMessage) => {
    try {
        const res = await axios.post(
            `${baseUrl}/chat/stream`,
            qs.stringify({ model, message }), // Form 格式提交
            {
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                },
                responseType: 'stream' // 流式响应必须配置
            }
        );

        // 解析 SSE 流式响应
        res.data.on('data', (chunk) => {
            const text = chunk.toString('utf-8');
            const lines = text.split('\n\n').filter(line => line.startsWith('data: '));
            lines.forEach(line => {
                const content = line.replace('data: ', '').trim();
                if (content) onMessage(content); // 回调传递流式内容
            });
        });

        return res.data;
    } catch (err) {
        console.error('流式聊天异常：', err);
        throw err;
    }
};