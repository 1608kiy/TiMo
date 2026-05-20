<template>
  <div class="not-found">
    <div class="not-found-content">
      <h1 class="not-found-code">404</h1>
      <p class="not-found-text">页面不存在</p>
      <p class="not-found-desc">你访问的页面不存在或已被移除</p>
      <router-link to="/" class="not-found-link">返回首页</router-link>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #fff;
}

.not-found-content {
  text-align: center;
}

.not-found-code {
  font-size: 120px;
  font-weight: 900;
  color: var(--color-primary);
  margin: 0;
  line-height: 1;
  opacity: 0.9;
}

.not-found-text {
  font-size: 22px;
  font-weight: 800;
  color: var(--color-text-primary);
  margin: 16px 0 8px;
}

.not-found-desc {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin: 0 0 32px;
}

.not-found-link {
  display: inline-block;
  padding: 10px 28px;
  background: var(--color-primary);
  color: #fff;
  border-radius: var(--radius-sm);
  text-decoration: none;
  font-size: 14px;
  font-weight: 700;
  transition: background 0.25s ease, transform 0.2s ease;
}

.not-found-link:hover {
  background: var(--color-primary-dark);
  transform: translateY(-1px);
}

.not-found-link:active {
  transform: scale(0.97);
}
</style>
