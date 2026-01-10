<template>
  <!-- 入口页面：仅引入聊天组件，无其他逻辑 -->
  <div class="app-container">
    <ChatComponent />
  </div>
</template>

<script setup>
// 仅引入聊天组件，无额外逻辑
import ChatComponent from '@/components/ChatComponent.vue';
</script>

<style>
/* 全局基础样式：仅保留页面布局，不涉及聊天组件细节 */
.app-container {
  max-width: 1280px;
  margin: 0 auto;
  padding: 2rem;
  text-align: center;
}

</style>