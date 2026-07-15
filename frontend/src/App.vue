<template>
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
html,
body,
#app {
  min-height: 100%;
  margin: 0;
}

body {
  background: #f5f7fa;
  font-family:
    Arial,
    "Microsoft YaHei",
    sans-serif;
}

.app-container {
  min-height: 100vh;
}

.app-header {
  display: flex;
  align-items: center;
  padding: 0 24px;
  background: #ffffff;
  border-bottom: 1px solid #e4e7ed;
}

.brand {
  margin-right: 40px;
  font-size: 20px;
  font-weight: 700;
}
</style>
