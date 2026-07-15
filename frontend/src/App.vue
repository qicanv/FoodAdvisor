<template>
  <router-view />
  <el-container class="app-container">
    <el-header class="app-header">
      <div class="brand">FoodAdvisor</div>

      <el-menu
        mode="horizontal"
        :default-active="$route.path"
        router
      >
        <el-menu-item index="/">首页</el-menu-item>
        <el-menu-item index="/restaurants">餐厅列表</el-menu-item>
        <el-menu-item index="/admin/model-configs">模型配置</el-menu-item>
      </el-menu>
    </el-header>

    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<style>
html, body, #app {
  min-height: 100%;
  margin: 0;
  padding: 0;
}
</style>
