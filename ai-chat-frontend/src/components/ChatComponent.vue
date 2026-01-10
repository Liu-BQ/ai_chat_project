<template>
  <div class="chat-component">
    <!-- 1. 模型选择 + 清空聊天开关 -->
    <div class="chat-header">
      <h2>AI 聊天助手</h2>
      <div class="model-selector">
        <label>选择模型：</label>
        <select
            v-model="selectedModel"
            @change="handleModelChange"
            :disabled="isLoading || modelList.length === 0"
        >
          <option value="" disabled>{{ modelList.length === 0 ? "无可用模型" : "加载中..." }}</option>
          <option v-for="model in modelList" :key="model" :value="model">
            {{ model }}
          </option>
        </select>
        <label class="clear-chat-switch">
          <input type="checkbox" v-model="clearChatOnModelChange"> 切换清空聊天
        </label>
      </div>
    </div>

    <!-- 2. 聊天记录（自动滚动） -->
    <div class="message-list" ref="messageList">
      <!-- 系统欢迎消息 -->
      <div class="message ai-message">
        <div class="message-bubble">
          你好！支持流式/非流式回复，选择模型后即可聊天～
          <div class="message-time">{{ new Date().toLocaleTimeString() }}</div>
        </div>
      </div>

      <!-- 动态消息 -->
      <div
          v-for="(msg, index) in chatHistory"
          :key="index"
          :class="['message', msg.role === 'user' ? 'user-message' : 'ai-message']"
      >
        <div class="message-bubble">
          <strong>{{ msg.role === 'user' ? '你' : 'AI' }}：</strong>
          <span>{{ msg.content }}</span>
          <div class="message-time">{{ msg.timestamp }}</div>
          <!-- 流式加载动画 -->
          <div v-if="msg.isStreaming" class="typing">
            <span></span><span></span><span></span>
          </div>
        </div>
      </div>
    </div>

    <!-- 3. 输入区域（支持流式/非流式发送） -->
    <div class="input-area">
      <textarea
          v-model="inputContent"
          placeholder="输入消息（Enter提交，Shift+Enter换行）..."
          @keydown.enter="handleEnterSubmit"
          :disabled="isLoading || !selectedModel"
      ></textarea>
      <div class="send-button-group">
        <!-- 主按钮（默认流式） -->
        <button
            @click="sendMessage(true)"
            :disabled="isLoading || !selectedModel || !inputContent.trim()"
            class="main-send-btn"
        >
          {{ isLoading ? '发送中...' : '发送' }}
        </button>
        <!-- 下拉触发器（小箭头） -->
        <button
            @click="toggleDropdown"
            :disabled="isLoading || !selectedModel || !inputContent.trim()"
            class="dropdown-toggle"
            aria-haspopup="true"
            aria-expanded="false"
        >
          ▼
        </button>
        <!-- 下拉菜单 -->
        <div v-if="isDropdownOpen" class="dropdown-menu">
          <button
              @click="() => { sendMessage(false); isDropdownOpen = false; }"
              class="dropdown-item"
          >
            非流式发送
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from "vue";
import axios from "axios";
import qs from "qs";

// 响应式状态（完全保留原逻辑）
const modelList = ref([]);
const selectedModel = ref("");
const inputContent = ref("");
const chatHistory = ref([]);
const isLoading = ref(false);
const clearChatOnModelChange = ref(true);
const messageList = ref(null);
const isDropdownOpen = ref(false);

// 下拉菜单切换（完全保留原逻辑）
const toggleDropdown = () => {
  isDropdownOpen.value = !isDropdownOpen.value;
};

// 后端接口地址（完全保留原逻辑）
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api/ollama";

/** 1. 组件挂载时获取模型列表（完全保留原逻辑） */
onMounted(async () => {
  try {
    const res = await axios.get(`${API_BASE_URL}/models`);
    if (res.data.code === "200" && res.data.data.length > 0) {
      modelList.value = res.data.data;
      selectedModel.value = res.data.data[0];
    } else {
      addSystemMessage("❌ 未检测到后端模型，请执行 `ollama run qwen:latest` 拉取后刷新");
    }
  } catch (err) {
    addSystemMessage(`❌ 连接后端失败：${err.message}，检查服务是否启动（端口8080）`);
  }
});

/** 2. 回车提交（完全保留原逻辑） */
const handleEnterSubmit = (e) => {
  e.shiftKey ? (inputContent.value += "\n") : (e.preventDefault(), sendMessage(false));
};

/** 3. 模型切换处理（完全保留原逻辑） */
const handleModelChange = () => {
  if (clearChatOnModelChange.value) {
    chatHistory.value = [];
    addSystemMessage(`✅ 已切换至模型：${selectedModel.value}，新对话使用该模型`);
  }
};

/** 4. 发送消息（核心修改：仅流式请求处理逻辑） */
const sendMessage = async (isStream = false) => {
  const model = selectedModel.value;
  const content = inputContent.value.trim();
  if (!model || !content) return;

  // 关闭下拉菜单 + 添加用户消息（完全保留原逻辑）
  isDropdownOpen.value = false;
  chatHistory.value.push({
    role: "user",
    content,
    timestamp: new Date().toLocaleTimeString(),
    isStreaming: false
  });
  inputContent.value = "";
  isLoading.value = true;
  await scrollToBottom();

  try {
    if (isStream) {
      // ------------------------------ 核心修改：流式请求处理（替换原 on('data') 逻辑）
      const aiMsgIndex = chatHistory.value.length;
      // 添加AI消息占位（带加载动画）
      chatHistory.value.push({
        role: "ai",
        content: "",
        timestamp: new Date().toLocaleTimeString(),
        isStreaming: true
      });

      // 1. 发起Axios请求（保留原配置：POST + form-urlencoded + responseType: stream）
      const res = await axios.post(
          `${API_BASE_URL}/chat/stream`,
          qs.stringify({ model, message: content }),
          {
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            responseType: "stream" // 关键：浏览器环境下返回 ReadableStream
          }
      );

      // 2. 关键：获取ReadableStream的读取器（替代原 res.data.on()）
      const stream = res.data; // 浏览器中 res.data 是 ReadableStream
      const reader = stream.getReader(); // 创建流读取器
      const decoder = new TextDecoder("utf-8"); // 解码二进制数据为字符串
      let accumulatedChunk = ""; // 缓存未完整解析的SSE片段（处理分块拆分）

      // 3. 循环读取流数据（核心逻辑：替代原 on('data') 事件）
      const readStreamChunk = async () => {
        try {
          // 读取一块数据（返回 { done: 流是否结束, value: Uint8Array 二进制数据 }）
          const { done, value } = await reader.read();

          // 3.1 流结束：清理状态
          if (done) {
            chatHistory.value[aiMsgIndex].isStreaming = false;
            isLoading.value = false;
            reader.releaseLock(); // 释放读取器（避免内存泄漏）
            await scrollToBottom();
            return;
          }

          // 3.2 处理二进制数据：解码为字符串 + 解析SSE格式
          accumulatedChunk += decoder.decode(value, { stream: true }); // 增量解码（支持分块）
          const sseLines = accumulatedChunk.split("\n\n"); // SSE格式用 \n\n 分隔片段

          // 3.3 解析SSE片段（过滤空行 + 处理未完整的片段）
          const completeLines = [];
          for (let i = 0; i < sseLines.length; i++) {
            const line = sseLines[i].trim();
            if (line === "") continue;
            // 最后一个片段可能不完整，重新缓存
            if (i === sseLines.length - 1 && !accumulatedChunk.endsWith("\n\n")) {
              accumulatedChunk = line;
            } else if (line.startsWith("data: ")) {
              // 提取AI回复内容（去掉 "data: " 前缀）
              completeLines.push(line.replace("data: ", ""));
            }
          }

          // 3.4 更新AI消息内容
          if (completeLines.length > 0) {
            const aiContent = completeLines.join(""); // 合并多段内容
            chatHistory.value[aiMsgIndex].content += aiContent;
            await scrollToBottom();
          }

          // 3.5 继续读取下一块数据
          await readStreamChunk();

        } catch (streamErr) {
          // 4. 流式错误处理（替代原 on('error') 事件）
          chatHistory.value[aiMsgIndex].content = `❌ 流式请求异常：${streamErr.message}`;
          chatHistory.value[aiMsgIndex].isStreaming = false;
          isLoading.value = false;
          reader.releaseLock(); // 释放读取器
          await scrollToBottom();
        }
      };

      // 启动流读取
      await readStreamChunk();

    } else {
      // ------------------------------ 非流式请求（完全保留原逻辑）
      const res = await axios.post(
          `${API_BASE_URL}/chat`,
          qs.stringify({ model, message: content }),
          { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
      );
      if (res.data.code === "200") {
        chatHistory.value.push({
          role: "ai",
          content: res.data.data,
          timestamp: new Date().toLocaleTimeString(),
          isStreaming: false
        });
      } else {
        addSystemMessage(`❌ 非流式请求失败：${res.data.message}`);
      }
      isLoading.value = false;
      await scrollToBottom();
    }
  } catch (err) {
    // 全局错误处理（完全保留原逻辑）
    addSystemMessage(`❌ 请求失败：${err.message || "未知错误"}`);
    isLoading.value = false;
    await scrollToBottom();
  }
};

/** 5. 自动滚动到底部（完全保留原逻辑） */
const scrollToBottom = async () => {
  await nextTick();
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight;
};

/** 辅助方法：添加系统提示消息（完全保留原逻辑） */
const addSystemMessage = (content) => {
  chatHistory.value.push({
    role: "ai",
    content,
    timestamp: new Date().toLocaleTimeString(),
    isStreaming: false
  });
  scrollToBottom();
};
</script>

<style scoped>
/* 样式完全保留原逻辑，无任何修改 */
.chat-component {
  --primary-color: #5b6ef5;
  --primary-light: #7889f7;
  --primary-disabled: #aab6f8;
  --bg-primary: #f8fafc;
  --bg-secondary: #ffffff;
  --border-color: #e2e8f0;
  --text-primary: #1e293b;
  --text-secondary: #64748b;
  --text-light: #94a3b8;
  --user-bubble: #5b6ef5;
  --ai-bubble: #f1f5f9;
  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 20px;
  --shadow-sm: 0 2px 8px rgba(0, 0, 0, 0.04);
  --shadow-md: 0 4px 12px rgba(0, 0, 0, 0.06);
  --transition: all 0.25s ease-in-out;

  max-width: 850px;
  margin: 2rem auto;
  border: 1px solid var(--border-color) !important;
  border-radius: var(--radius-md);
  overflow: hidden;
  font-family: "Inter", system-ui, -apple-system, sans-serif;
  box-shadow: var(--shadow-md);
  background: var(--bg-secondary);
  min-height: 700px;
  display: flex;
  flex-direction: column;
}

.chat-header {
  padding: 20px 24px;
  background-color: var(--bg-primary);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.chat-header h2 {
  margin: 0;
  font-size: 19px;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: -0.2px;
}

.model-selector {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--text-secondary);
}

.model-selector select {
  padding: 8px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-secondary);
  cursor: pointer;
  transition: var(--transition);
  font-size: 14px;
  color: var(--text-primary);
}

.model-selector select:hover:not(:disabled) {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 2px rgba(91, 110, 245, 0.1);
}

.model-selector select:disabled {
  cursor: not-allowed;
  background: var(--bg-primary);
  color: var(--text-light);
  border-color: var(--border-color);
}

.clear-chat-switch {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}

.clear-chat-switch input[type="checkbox"] {
  width: 16px;
  height: 16px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  appearance: none;
  cursor: pointer;
  position: relative;
  background: var(--bg-secondary);
  transition: var(--transition);
}

.clear-chat-switch input[type="checkbox"]:checked {
  background: var(--primary-color);
  border-color: var(--primary-color);
}

.clear-chat-switch input[type="checkbox"]:checked::after {
  content: "✓";
  position: absolute;
  color: white;
  font-size: 10px;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.message-list {
  flex: 1;
  height: 550px;
  overflow-y: auto;
  padding: 24px;
  background: var(--bg-secondary);
  gap: 16px;
  display: flex;
  flex-direction: column;
  border-bottom: 1px solid var(--border-color);
}

.message-list::-webkit-scrollbar {
  width: 8px;
}

.message-list::-webkit-scrollbar-track {
  background: var(--bg-primary);
  border-radius: 4px;
}

.message-list::-webkit-scrollbar-thumb {
  background: var(--border-color);
  border-radius: 4px;
  transition: var(--transition);
}

.message-list::-webkit-scrollbar-thumb:hover {
  background: var(--text-light);
}

.message {
  display: flex;
  margin-bottom: 8px;
}

.user-message {
  justify-content: flex-end;
}

.ai-message {
  justify-content: flex-start;
}

.message-bubble {
  max-width: 75%;
  padding: 12px 18px;
  border-radius: var(--radius-lg);
  line-height: 1.6;
  font-size: 14px;
  position: relative;
  box-shadow: var(--shadow-sm);
}

.user-message .message-bubble {
  background: var(--user-bubble);
  color: #ffffff;
  border-bottom-right-radius: 6px;
}

.ai-message .message-bubble {
  background: var(--ai-bubble);
  color: var(--text-primary);
  border-bottom-left-radius: 6px;
}

.message-time {
  font-size: 11px;
  margin-top: 6px;
  text-align: right;
  opacity: 0.7;
  letter-spacing: 0.5px;
}

.typing {
  display: inline-flex;
  gap: 4px;
  margin-left: 8px;
}

.typing span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary-color);
  animation: typing 1.4s infinite ease-in-out both;
  opacity: 0.7;
}

.typing span:nth-child(1) { animation-delay: -0.32s; }
.typing span:nth-child(2) { animation-delay: -0.16s; }

@keyframes typing {
  0%, 80%, 100% { transform: scale(0); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

.input-area {
  padding: 20px 24px;
  background: var(--bg-primary);
  display: flex;
  gap: 12px;
  align-items: flex-end;
  min-height: 120px;
}

.input-area textarea {
  flex: 1;
  padding: 14px 16px;
  border: 1px solid var(--border-color) !important;
  border-radius: var(--radius-md);
  resize: none;
  font-size: 14px;
  min-height: 70px;
  transition: var(--transition);
  background: var(--bg-secondary);
  color: var(--text-primary);
  line-height: 1.5;
  display: block;
}

.input-area textarea:focus {
  border-color: var(--primary-color) !important;
  box-shadow: 0 0 0 2px rgba(91, 110, 245, 0.1);
  outline: none;
}

.input-area textarea:disabled {
  background: #f1f5f9;
  cursor: not-allowed;
  color: var(--text-light);
}

.send-button-group {
  position: relative;
  display: flex;
  height: 48px;
  flex-shrink: 0;
}

.main-send-btn {
  padding: 0 20px;
  border: none;
  border-radius: var(--radius-md) 0 0 var(--radius-md);
  background: var(--primary-color);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: var(--transition);
  min-width: 90px;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 1 !important;
  visibility: visible !important;
}

.main-send-btn:disabled {
  background: var(--primary-disabled);
  cursor: not-allowed;
}

.main-send-btn:hover:not(:disabled) {
  background: var(--primary-light);
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(91, 110, 245, 0.15);
}

.dropdown-toggle {
  width: 40px;
  padding: 0;
  border: none;
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: var(--primary-color);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  transition: var(--transition);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 1 !important;
  visibility: visible !important;
}

.dropdown-toggle:disabled {
  background: var(--primary-disabled);
  cursor: not-allowed;
}

.dropdown-toggle:hover:not(:disabled) {
  background: var(--primary-light);
}

.dropdown-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  z-index: 100;
  min-width: 140px;
  overflow: hidden;
  transform-origin: top right;
  animation: fadeIn 0.2s ease-in-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: scale(0.95); }
  to { opacity: 1; transform: scale(1); }
}

.dropdown-item {
  display: block;
  width: 100%;
  padding: 10px 16px;
  background: none;
  border: none;
  text-align: left;
  font-size: 14px;
  color: var(--text-primary);
  cursor: pointer;
  transition: var(--transition);
}

.dropdown-item:hover {
  background: var(--bg-primary);
  color: var(--primary-color);
  padding-left: 18px;
}
</style>