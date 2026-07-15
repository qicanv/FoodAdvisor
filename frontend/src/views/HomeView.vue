<template>
  <div class="home-page">
    <header class="header">
      <div class="container header-container">
        <div class="logo-section">
          <img src="../assets/images/greedy-cat.png" alt="食尚参谋" class="logo-img" />
          <div class="logo-text">
            <h1 class="brand-name">食尚参谋</h1>
            <span class="brand-slogan">AI美食推荐助手</span>
          </div>
        </div>
      </div>
    </header>
    
    <section class="hero-section">
      <div class="particles-container">
        <div class="particle particle-1"></div>
        <div class="particle particle-2"></div>
        <div class="particle particle-3"></div>
        <div class="particle particle-4"></div>
        <div class="particle particle-5"></div>
        <div class="particle particle-6"></div>
        <div class="particle particle-7"></div>
        <div class="particle particle-8"></div>
        <div class="particle particle-9"></div>
        <div class="particle particle-10"></div>
      </div>
      <div class="hero-container">
        <div class="hero-content">
          <div class="hero-tags">
            <span class="hero-tag">AI驱动</span>
            <span class="hero-tag">智能推荐</span>
            <span class="hero-tag">精准匹配</span>
          </div>
          
          <h2 class="hero-title">
            发现你身边的<span class="highlight">美食</span>
            <br />
            食尚参谋为你寻找最佳餐厅
          </h2>
          
          <p class="hero-desc">
            基于AI技术，为食客、商户和管理员提供专业的美食推荐服务
          </p>
          
          <div class="portals-row">
            <div class="portal-item" @click="enterPortal('diner')">
              <div class="portal-icon-wrapper diner-wrapper">
                <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="#ffffff" stroke-width="2">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                  <circle cx="12" cy="7" r="4"></circle>
                </svg>
              </div>
              <span class="portal-label">我是食客</span>
            </div>
            
            <div class="portal-item" @click="enterPortal('merchant')">
              <div class="portal-icon-wrapper merchant-wrapper">
                <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="#ffffff" stroke-width="2">
                  <path d="M3 7v6h6"></path>
                  <path d="M21 17a2 2 0 0 0-2-2H7l-4 4v-6a2 2 0 0 0-2-2"></path>
                  <path d="M21 7a2 2 0 0 0-2-2H9"></path>
                  <path d="M3 17h18"></path>
                </svg>
              </div>
              <span class="portal-label">我是商户</span>
            </div>
            
            <div class="portal-item" @click="enterPortal('admin')">
              <div class="portal-icon-wrapper admin-wrapper">
                <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="#ffffff" stroke-width="2">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
                  <path d="M9 12l2 2 4-4"></path>
                </svg>
              </div>
              <span class="portal-label">我是管理员</span>
            </div>
          </div>
          
          <div class="hero-stats">
            <div class="stat-item">
              <span class="stat-value">10,000+</span>
              <span class="stat-label">优质餐厅</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">500,000+</span>
              <span class="stat-label">真实评价</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">98%</span>
              <span class="stat-label">推荐准确率</span>
            </div>
          </div>
        </div>
        
        <div class="hero-image">
          <div class="ai-character">
            <img src="../assets/images/greedy-cat.png" alt="食尚参谋大人" class="ai-img" />
            <div class="ai-bubble">
              <span>我是食尚参谋大人，很高兴为您服务~</span>
              <div class="bubble-arrow"></div>
            </div>
            
            <div class="food-float food-float-1">
              <svg viewBox="0 0 100 100" width="70" height="70">
                <circle cx="50" cy="50" r="45" fill="#ffffff" opacity="0.3"/>
                <circle cx="50" cy="50" r="45" fill="none" stroke="#ffffff" stroke-width="3" opacity="0.6"/>
                <text x="50" y="60" text-anchor="middle" font-size="36" fill="#ffffff">🍜</text>
              </svg>
            </div>
            <div class="food-float food-float-2">
              <svg viewBox="0 0 100 100" width="60" height="60">
                <circle cx="50" cy="50" r="40" fill="#ffffff" opacity="0.3"/>
                <circle cx="50" cy="50" r="40" fill="none" stroke="#ffffff" stroke-width="3" opacity="0.6"/>
                <text x="50" y="58" text-anchor="middle" font-size="32" fill="#ffffff">🍔</text>
              </svg>
            </div>
            <div class="food-float food-float-3">
              <svg viewBox="0 0 100 100" width="65" height="65">
                <circle cx="50" cy="50" r="42" fill="#ffffff" opacity="0.3"/>
                <circle cx="50" cy="50" r="42" fill="none" stroke="#ffffff" stroke-width="3" opacity="0.6"/>
                <text x="50" y="58" text-anchor="middle" font-size="32" fill="#ffffff">🍣</text>
              </svg>
            </div>
            <div class="food-float food-float-4">
              <svg viewBox="0 0 100 100" width="55" height="55">
                <circle cx="50" cy="50" r="38" fill="#ffffff" opacity="0.3"/>
                <circle cx="50" cy="50" r="38" fill="none" stroke="#ffffff" stroke-width="3" opacity="0.6"/>
                <text x="50" y="56" text-anchor="middle" font-size="28" fill="#ffffff">🥘</text>
              </svg>
            </div>
          </div>
        </div>
      </div>
      
      <div class="hero-wave">
        <svg viewBox="0 0 1440 120" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M0 60C240 120 480 0 720 60C960 120 1200 0 1440 60V120H0V60Z" fill="#ffffff"/>
        </svg>
      </div>
    </section>
    
    <section class="features-section">
      <div class="features-container">
        <h3 class="section-title">为什么选择我们</h3>
        <p class="section-subtitle">基于AI技术，为你提供专业的美食推荐服务</p>
        
        <div class="features-grid">
          <div class="feature-card">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="#ff6700" stroke-width="1.5">
                <circle cx="12" cy="12" r="10"></circle>
                <path d="M12 6v6l4 2"></path>
              </svg>
            </div>
            <h4 class="feature-title">智能理解</h4>
            <p class="feature-desc">AI能够理解你的自然语言描述，精准提取口味偏好、预算和场景需求</p>
          </div>
          
          <div class="feature-card">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="#ff6700" stroke-width="1.5">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
              </svg>
            </div>
            <h4 class="feature-title">深度分析</h4>
            <p class="feature-desc">分析百万真实用户评价，提炼商家优缺点，帮你做出明智选择</p>
          </div>
          
          <div class="feature-card">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="#ff6700" stroke-width="1.5">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                <circle cx="9" cy="7" r="4"></circle>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
              </svg>
            </div>
            <h4 class="feature-title">个性化推荐</h4>
            <p class="feature-desc">根据你的历史偏好和实时需求，推荐最适合你的餐厅</p>
          </div>
          
          <div class="feature-card">
            <div class="feature-icon">
              <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="#ff6700" stroke-width="1.5">
                <path d="M9 12l2 2 4-4"></path>
                <circle cx="12" cy="12" r="10"></circle>
              </svg>
            </div>
            <h4 class="feature-title">推荐溯源</h4>
            <p class="feature-desc">清晰展示推荐依据，让你了解每一个推荐背后的原因</p>
          </div>
        </div>
      </div>
    </section>
    
    <footer class="footer">
      <div class="footer-container">
        <div class="footer-content">
          <div class="footer-brand">
            <img src="../assets/images/greedy-cat.png" alt="食尚参谋" class="footer-logo" />
            <h4>食尚参谋</h4>
            <p>AI美食推荐助手，让每一顿饭都充满惊喜</p>
          </div>
          
          <div class="footer-links">
            <div class="link-group">
              <h5>产品服务</h5>
              <ul>
                <li><a href="#">智能推荐</a></li>
                <li><a href="#">餐厅搜索</a></li>
                <li><a href="#">评价分析</a></li>
                <li><a href="#">商家入驻</a></li>
              </ul>
            </div>
            <div class="link-group">
              <h5>帮助中心</h5>
              <ul>
                <li><a href="#">使用指南</a></li>
                <li><a href="#">常见问题</a></li>
                <li><a href="#">用户协议</a></li>
                <li><a href="#">隐私政策</a></li>
              </ul>
            </div>
            <div class="link-group">
              <h5>关于我们</h5>
              <ul>
                <li><a href="#">公司介绍</a></li>
                <li><a href="#">联系我们</a></li>
                <li><a href="#">加入我们</a></li>
                <li><a href="#">合作伙伴</a></li>
              </ul>
            </div>
          </div>
        </div>
        
        <div class="footer-bottom">
          <p>&copy; 2026 食尚参谋. All rights reserved.</p>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const enterPortal = (portal) => {
  switch (portal) {
    case 'diner':
      router.push('/diner')
      break
    case 'merchant':
      router.push('/merchant')
      break
    case 'admin':
      router.push('/admin')
      break
    default:
      break
  }
}
</script>

<style scoped>
.home-page {
  min-height: 100vh;
}

.header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
}

.header-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-img {
  width: 40px;
  height: 40px;
  border-radius: 8px;
}

.logo-text {
  display: flex;
  flex-direction: column;
}

.brand-name {
  font-size: 20px;
  font-weight: 700;
  color: #ff6700;
  margin: 0;
}

.brand-slogan {
  font-size: 11px;
  color: #999999;
}

.hero-section {
  padding-top: 64px;
  background: linear-gradient(135deg, #ff6700 0%, #ff7a00 30%, #ff9500 60%, #ffcc00 100%);
  position: relative;
  overflow: hidden;
}

.particles-container {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  overflow: hidden;
  pointer-events: none;
  z-index: 1;
}

.particle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.3);
  animation: float-particle 8s ease-in-out infinite;
}

.particle-1 {
  width: 4px;
  height: 4px;
  left: 10%;
  top: 20%;
  animation-delay: 0s;
  animation-duration: 6s;
}

.particle-2 {
  width: 6px;
  height: 6px;
  left: 20%;
  top: 60%;
  animation-delay: 1s;
  animation-duration: 7s;
}

.particle-3 {
  width: 5px;
  height: 5px;
  left: 30%;
  top: 30%;
  animation-delay: 2s;
  animation-duration: 8s;
}

.particle-4 {
  width: 4px;
  height: 4px;
  left: 45%;
  top: 70%;
  animation-delay: 0.5s;
  animation-duration: 6.5s;
}

.particle-5 {
  width: 7px;
  height: 7px;
  left: 60%;
  top: 25%;
  animation-delay: 3s;
  animation-duration: 9s;
}

.particle-6 {
  width: 5px;
  height: 5px;
  left: 75%;
  top: 55%;
  animation-delay: 1.5s;
  animation-duration: 7.5s;
}

.particle-7 {
  width: 4px;
  height: 4px;
  left: 85%;
  top: 35%;
  animation-delay: 2.5s;
  animation-duration: 6s;
}

.particle-8 {
  width: 6px;
  height: 6px;
  left: 15%;
  top: 80%;
  animation-delay: 4s;
  animation-duration: 8s;
}

.particle-9 {
  width: 5px;
  height: 5px;
  left: 50%;
  top: 45%;
  animation-delay: 1s;
  animation-duration: 7s;
}

.particle-10 {
  width: 4px;
  height: 4px;
  left: 90%;
  top: 65%;
  animation-delay: 3.5s;
  animation-duration: 6.5s;
}

@keyframes float-particle {
  0%, 100% {
    transform: translateY(0) translateX(0) scale(1);
    opacity: 0.3;
  }
  25% {
    transform: translateY(-20px) translateX(10px) scale(1.2);
    opacity: 0.6;
  }
  50% {
    transform: translateY(-40px) translateX(-5px) scale(1);
    opacity: 0.4;
  }
  75% {
    transform: translateY(-20px) translateX(15px) scale(1.1);
    opacity: 0.5;
  }
}

.hero-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 200px 20px;
  max-width: 1200px;
  margin: 0 auto;
  position: relative;
  z-index: 10;
}

.hero-content {
  flex: 1;
  max-width: 550px;
}

.hero-tags {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.hero-tag {
  background: rgba(255, 255, 255, 0.2);
  color: #ffffff;
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
  backdrop-filter: blur(10px);
}

.hero-title {
  font-size: 40px;
  font-weight: 700;
  color: #ffffff;
  line-height: 1.2;
  margin-bottom: 20px;
}

.highlight {
  color: #ffcc00;
}

.hero-desc {
  font-size: 18px;
  color: rgba(255, 255, 255, 0.9);
  line-height: 1.8;
  margin-bottom: 32px;
}

.portals-row {
  display: flex;
  gap: 32px;
  margin-bottom: 32px;
}

.portal-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition: transform 0.3s;
}

.portal-item:hover {
  transform: translateY(-8px);
}

.portal-icon-wrapper {
  width: 88px;
  height: 88px;
  border-radius: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  border: 2px solid rgba(255, 255, 255, 0.3);
  transition: all 0.3s;
}

.portal-item:hover .portal-icon-wrapper {
  background: rgba(255, 255, 255, 0.35);
  border-color: rgba(255, 255, 255, 0.6);
  transform: scale(1.1);
}

.diner-wrapper {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.3), rgba(255, 103, 0, 0.25));
}

.merchant-wrapper {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.3), rgba(82, 196, 26, 0.25));
}

.admin-wrapper {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.3), rgba(24, 144, 255, 0.25));
}

.portal-label {
  font-size: 16px;
  font-weight: 600;
  color: #ffffff;
}

.hero-stats {
  display: flex;
  gap: 48px;
}

.stat-item {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #ffffff;
}

.stat-label {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.8);
  margin-top: 4px;
}

.hero-image {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
}

.ai-character {
  position: relative;
  width: 280px;
  height: 280px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.ai-img {
  width: 200px;
  height: 200px;
  border-radius: 50%;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  z-index: 10;
}

.ai-bubble {
  position: absolute;
  top: 10px;
  right: -40px;
  background: #ffffff;
  padding: 12px 20px;
  border-radius: 12px;
  font-size: 14px;
  color: #333333;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  max-width: 180px;
  z-index: 20;
}

.bubble-arrow {
  position: absolute;
  bottom: -8px;
  left: 20px;
  width: 0;
  height: 0;
  border-left: 8px solid transparent;
  border-right: 8px solid transparent;
  border-top: 8px solid #ffffff;
}

.food-float {
  position: absolute;
  animation: float 3s ease-in-out infinite;
  z-index: 5;
}

.food-float-1 {
  top: 0;
  left: 0;
  animation-delay: 0s;
}

.food-float-2 {
  top: 20px;
  right: 0;
  animation-delay: 0.5s;
}

.food-float-3 {
  bottom: 20px;
  left: 10px;
  animation-delay: 1s;
}

.food-float-4 {
  bottom: 0;
  right: 20px;
  animation-delay: 1.5s;
}

@keyframes float {
  0%, 100% { transform: translateY(0) rotate(0deg); }
  50% { transform: translateY(-12px) rotate(5deg); }
}

.hero-wave {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
}

.features-section {
  padding: 80px 0;
  background: #ffffff;
}

.features-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.feature-card {
  background: #fafafa;
  padding: 32px;
  border-radius: 12px;
  text-align: center;
  transition: all 0.3s;
}

.feature-card:hover {
  background: #fff0e6;
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(255, 103, 0, 0.1);
}

.feature-icon {
  margin-bottom: 20px;
}

.feature-title {
  font-size: 18px;
  font-weight: 600;
  color: #333333;
  margin-bottom: 12px;
}

.feature-desc {
  font-size: 14px;
  color: #666666;
  line-height: 1.6;
}

.footer {
  background: #333333;
  padding: 60px 0 30px;
}

.footer-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.footer-content {
  display: flex;
  justify-content: space-between;
  padding-bottom: 40px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.footer-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.footer-logo {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  margin-bottom: 12px;
}

.footer-brand h4 {
  font-size: 20px;
  font-weight: 700;
  color: #ff6700;
  margin-bottom: 8px;
}

.footer-brand p {
  font-size: 13px;
  color: #999999;
}

.footer-links {
  display: flex;
  gap: 60px;
}

.link-group h5 {
  font-size: 14px;
  font-weight: 600;
  color: #ffffff;
  margin-bottom: 16px;
}

.link-group ul {
  list-style: none;
  padding: 0;
}

.link-group li {
  margin-bottom: 10px;
}

.link-group a {
  font-size: 13px;
  color: #999999;
  text-decoration: none;
  transition: color 0.2s;
}

.link-group a:hover {
  color: #ff6700;
}

.footer-bottom {
  text-align: center;
  padding-top: 30px;
}

.footer-bottom p {
  font-size: 12px;
  color: #666666;
}

@media (max-width: 1024px) {
  .hero-container {
    flex-direction: column;
    text-align: center;
    padding: 40px 20px;
    max-width: 900px;
  }
  
  .hero-content {
    margin-bottom: 40px;
  }
  
  .hero-title {
    font-size: 36px;
  }
  
  .portals-row {
    justify-content: center;
    gap: 24px;
  }
  
  .portal-icon-wrapper {
    width: 72px;
    height: 72px;
  }
  
  .hero-stats {
    justify-content: center;
  }
  
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .footer-content {
    flex-direction: column;
    align-items: center;
    gap: 32px;
  }
  
  .footer-links {
    gap: 40px;
  }
  
  .ai-character {
    width: 240px;
    height: 240px;
  }
  
  .ai-img {
    width: 180px;
    height: 180px;
  }
}

@media (max-width: 768px) {
  .hero-title {
    font-size: 28px;
  }
  
  .hero-desc {
    font-size: 16px;
  }
  
  .portals-row {
    gap: 20px;
  }
  
  .portal-icon-wrapper {
    width: 64px;
    height: 64px;
  }
  
  .portal-label {
    font-size: 14px;
  }
  
  .hero-stats {
    gap: 24px;
  }
  
  .stat-value {
    font-size: 22px;
  }
  
  .features-grid {
    grid-template-columns: 1fr;
  }
  
  .ai-character {
    width: 200px;
    height: 200px;
  }
  
  .ai-img {
    width: 150px;
    height: 150px;
  }
  
  .ai-bubble {
    right: -60px;
    max-width: 140px;
    font-size: 12px;
  }
  
  .footer-links {
    flex-direction: column;
    text-align: center;
  }
}

@media (max-width: 480px) {
  .hero-stats {
    flex-direction: column;
    gap: 16px;
  }
  
  .portals-row {
    gap: 16px;
  }
  
  .portal-icon-wrapper {
    width: 56px;
    height: 56px;
  }
  
  .portal-label {
    font-size: 12px;
  }
  
  .ai-character {
    width: 180px;
    height: 180px;
  }
  
  .ai-img {
    width: 130px;
    height: 130px;
  }
}
</style>