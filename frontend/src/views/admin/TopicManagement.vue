<template>
  <AdminLayout title="专题管理" subtitle="管理分类标签和探店专题，构建不同主题的内容推荐">
    <template #sidebar>
      <div class="page-sidebar-nav">
        <span class="page-sidebar-title">专题管理</span>
        <div class="page-sidebar-items-wrapper">
          <div 
            v-for="item in sidebarItems" 
            :key="item.key"
            :class="['page-sidebar-item', { active: activeTab === item.key }]"
            @click="activeTab = item.key"
          >
            <span class="menu-icon">{{ item.icon }}</span>
            <span>{{ item.label }}</span>
          </div>
        </div>
      </div>
    </template>

    <div v-if="activeTab === 'topics'" class="tab-content">
      <div class="search-bar">
        <div class="search-item">
          <input 
            type="text" 
            v-model="searchKeyword" 
            placeholder="搜索专题名称..."
            class="search-input"
            @keyup.enter="loadTopics"
          />
        </div>
        <div class="search-item">
          <select v-model="statusFilter" class="search-select" @change="loadTopics">
            <option value="">全部状态</option>
            <option value="PUBLISHED">公开</option>
            <option value="DRAFT">草稿</option>
            <option value="OFFLINE">已下架</option>
          </select>
        </div>
        <button class="search-btn" @click="loadTopics">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"></circle>
            <path d="M21 21l-4.35-4.35"></path>
          </svg>
          <span>搜索</span>
        </button>
        <button class="add-btn" @click="openTopicModal()">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#fff" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"></line>
            <line x1="5" y1="12" x2="19" y2="12"></line>
          </svg>
          <span>新建专题</span>
        </button>
      </div>

      <div class="topics-grid">
        <div 
          v-for="topic in topics" 
          :key="topic.id" 
          class="topic-card"
          :class="{ 'offline': topic.status === 'OFFLINE', 'draft': topic.status === 'DRAFT' }"
        >
          <div class="topic-cover">
            <img :src="topic.coverImage || defaultCover" :alt="topic.name" />
            <span :class="['status-badge', topic.status.toLowerCase()]">{{ getStatusText(topic.status) }}</span>
          </div>
          <div class="topic-info">
            <h3>{{ topic.name }}</h3>
            <p class="topic-desc">{{ topic.description }}</p>
            <div class="topic-tags">
              <span v-for="tag in topic.tags" :key="tag" class="topic-tag">{{ tag }}</span>
            </div>
            <div class="topic-meta">
              <span class="merchant-count" @click="openMerchantListModal(topic)">{{ topic.merchantCount }}个商家</span>
              <span class="update-time">{{ formatTime(topic.updatedAt) }}</span>
            </div>
          </div>
          <div class="topic-actions">
            <button class="action-btn edit-btn" @click="openTopicModal(topic)" title="编辑专题">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M17 3a2.828 2.828 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z"></path>
              </svg>
            </button>
            <button 
              class="action-btn status-btn" 
              @click="toggleTopicStatus(topic)"
              :title="topic.status === 'PUBLISHED' ? '下架专题' : '发布专题'"
            >
              <svg v-if="topic.status === 'PUBLISHED'" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
                <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
              </svg>
              <svg v-else viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <polygon points="5 3 19 12 5 21 5 3"></polygon>
              </svg>
            </button>
            <button class="action-btn view-btn" @click="openMerchantListModal(topic)" title="查看关联商家">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"></path>
                <path d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"></path>
              </svg>
            </button>
            <button class="action-btn delete-btn" @click="confirmDeleteTopic(topic)" title="删除专题">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 6h18"></path>
                <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"></path>
                <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"></path>
              </svg>
            </button>
          </div>
        </div>

        <div v-if="topics.length === 0 && !loading" class="empty-state">
          <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#ccc" stroke-width="1.5">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
            <polyline points="14 2 14 8 20 8"></polyline>
          </svg>
          <p>暂无专题数据</p>
        </div>
      </div>
    </div>

    <div v-if="activeTab === 'tags'" class="tab-content">
      <div class="search-bar">
        <div class="search-item">
          <input 
            type="text" 
            v-model="tagSearchKeyword" 
            placeholder="搜索标签名称..."
            class="search-input"
            @keyup.enter="loadTags"
          />
        </div>
        <div class="search-item">
          <select v-model="tagTypeFilter" class="search-select" @change="loadTags">
            <option value="">全部类型</option>
            <option value="category">餐饮类型</option>
            <option value="cuisine">菜系</option>
            <option value="scene">消费场景</option>
            <option value="environment">环境特点</option>
            <option value="price">价格区间</option>
          </select>
        </div>
        <button class="search-btn" @click="loadTags">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"></circle>
            <path d="M21 21l-4.35-4.35"></path>
          </svg>
          <span>搜索</span>
        </button>
        <button class="add-btn" @click="openTagModal()">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#fff" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"></line>
            <line x1="5" y1="12" x2="19" y2="12"></line>
          </svg>
          <span>新增标签</span>
        </button>
      </div>

      <div class="tags-section">
        <div v-for="type in tagTypes" :key="type.key" class="tag-group">
          <h3 class="tag-group-title">{{ type.label }}</h3>
          <div class="tags-list">
            <div 
              v-for="tag in getTagsByType(type.key)" 
              :key="tag.id" 
              class="tag-item"
            >
              <span class="tag-name">{{ tag.name }}</span>
              <span class="tag-count" @click="openTagMerchantListModal(tag)">{{ tag.count }}个关联</span>
              <button class="view-tag-merchants-btn" @click="openTagMerchantListModal(tag)" title="查看关联商家">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"></path>
                  <path d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"></path>
                </svg>
              </button>
              <button class="remove-tag-btn" @click="removeTag(tag)">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M18 6L6 18M6 6l12 12"></path>
                </svg>
              </button>
            </div>
            <button class="add-tag-btn" @click="openTagModal(type.key)">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"></line>
                <line x1="5" y1="12" x2="19" y2="12"></line>
              </svg>
              <span>添加标签</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showTopicModal" class="modal-overlay" @click.self="closeTopicModal">
      <div class="modal-content">
        <div class="modal-header">
          <h3>{{ editingTopic ? '编辑专题' : '新建专题' }}</h3>
          <button class="modal-close" @click="closeTopicModal">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 6L6 18M6 6l12 12"></path>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>专题名称</label>
            <input type="text" v-model="topicForm.name" placeholder="请输入专题名称" class="form-input" />
          </div>
          <div class="form-group">
            <label>专题描述</label>
            <textarea v-model="topicForm.description" placeholder="请输入专题描述" class="form-textarea" rows="3"></textarea>
          </div>
          <div class="form-group">
            <label>封面图片</label>
            <input type="text" v-model="topicForm.coverImage" placeholder="请输入封面图片URL" class="form-input" />
          </div>
          <div class="form-group">
            <label>关联标签</label>
            <div class="tags-select">
              <div 
                v-for="tag in availableTags" 
                :key="tag.id"
                :class="['tag-option', { selected: topicForm.tags.includes(tag.name) }]"
                @click="toggleTag(tag.name)"
              >
                {{ tag.name }}
              </div>
            </div>
          </div>
          <div class="form-group">
            <label>关联商家</label>
            <div class="merchant-select">
              <div class="merchant-search-trigger" @click="toggleMerchantSearch">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="11" cy="11" r="8"></circle>
                  <path d="M21 21l-4.35-4.35"></path>
                </svg>
                <span>{{ topicForm.merchants.length > 0 ? `已选择 ${topicForm.merchants.length} 个商家` : '点击选择关联商家' }}</span>
                <svg :class="['toggle-icon', { expanded: showMerchantSearch }]" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="6 9 12 15 18 9"></polyline>
                </svg>
              </div>
              <div v-if="showMerchantSearch" class="merchant-search-panel">
                <input 
                  type="text" 
                  v-model="merchantSearch" 
                  placeholder="搜索商家名称..."
                  class="merchant-search-input"
                  @input="searchMerchants"
                  ref="merchantSearchInput"
                />
                <div class="merchant-search-scroll">
                  <div 
                    v-for="merchant in searchResults" 
                    :key="merchant.id"
                    :class="['merchant-option', { selected: isMerchantSelected(merchant.id) }]"
                    @click="toggleMerchant(merchant)"
                  >
                    <span class="merchant-name">{{ merchant.name }}</span>
                    <span :class="['merchant-status', merchant.operationStatus.toLowerCase()]">
                      {{ merchant.operationStatus === 'OPERATING' ? '营业中' : '停业' }}
                    </span>
                  </div>
                </div>
              </div>
              <div class="selected-merchants">
                <div 
                  v-for="m in topicForm.merchants" 
                  :key="m.id" 
                  class="selected-merchant"
                >
                  <span>{{ m.name }}</span>
                  <button @click="removeMerchant(m.id)">
                    <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M18 6L6 18M6 6l12 12"></path>
                    </svg>
                  </button>
                </div>
              </div>
            </div>
          </div>
          <div class="form-group">
            <label>状态</label>
            <select v-model="topicForm.status" class="form-select">
              <option value="DRAFT">草稿</option>
              <option value="PUBLISHED">公开</option>
              <option value="OFFLINE">已下架</option>
            </select>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" @click="closeTopicModal">取消</button>
          <button class="btn btn-primary" @click="saveTopic">{{ editingTopic ? '保存修改' : '创建专题' }}</button>
        </div>
      </div>
    </div>

    <div v-if="showTagModal" class="modal-overlay" @click.self="closeTagModal">
      <div class="modal-content">
        <div class="modal-header">
          <h3>{{ editingTag ? '编辑标签' : '新增标签' }}</h3>
          <button class="modal-close" @click="closeTagModal">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 6L6 18M6 6l12 12"></path>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>标签名称</label>
            <input type="text" v-model="tagForm.name" placeholder="请输入标签名称" class="form-input" />
          </div>
          <div class="form-group">
            <label>标签类型</label>
            <select v-model="tagForm.type" class="form-select">
              <option value="category">餐饮类型</option>
              <option value="cuisine">菜系</option>
              <option value="scene">消费场景</option>
              <option value="environment">环境特点</option>
              <option value="price">价格区间</option>
            </select>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" @click="closeTagModal">取消</button>
          <button class="btn btn-primary" @click="saveTag">{{ editingTag ? '保存修改' : '添加标签' }}</button>
        </div>
      </div>
    </div>

    <div v-if="showMerchantListModal" class="modal-overlay" @click.self="closeMerchantListModal">
      <div class="modal-content merchant-list-modal">
        <div class="modal-header">
          <h3>{{ currentTopic?.name }} - 关联商家列表</h3>
          <button class="modal-close" @click="closeMerchantListModal">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 6L6 18M6 6l12 12"></path>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <div v-if="currentTopicMerchants.length === 0" class="empty-state">
            <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#ccc" stroke-width="1.5">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
              <circle cx="9" cy="7" r="4"></circle>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
            </svg>
            <p>该专题暂无关联商家</p>
          </div>
          <div v-else class="merchant-list">
            <div 
              v-for="merchant in currentTopicMerchants" 
              :key="merchant.id" 
              class="merchant-item"
            >
              <div class="merchant-avatar">
                <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M8 21l1-17a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4l1 17"></path>
                </svg>
              </div>
              <div class="merchant-info">
                <div class="merchant-name-row">
                  <span class="merchant-name">{{ merchant.name }}</span>
                  <span :class="['status-tag', merchant.operationStatus.toLowerCase()]">
                    {{ merchant.operationStatus === 'OPERATING' ? '营业中' : '停业' }}
                  </span>
                </div>
                <p class="merchant-desc">{{ merchant.description || '暂无描述' }}</p>
                <div class="merchant-tags">
                  <span v-for="tag in merchant.tags" :key="tag" class="mini-tag">{{ tag }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-primary" @click="closeMerchantListModal">关闭</button>
        </div>
      </div>
    </div>

    <div v-if="showTagMerchantListModal" class="modal-overlay" @click.self="closeTagMerchantListModal">
      <div class="modal-content merchant-list-modal">
        <div class="modal-header">
          <h3>标签「{{ currentTag?.name }}」- 关联商家列表</h3>
          <button class="modal-close" @click="closeTagMerchantListModal">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 6L6 18M6 6l12 12"></path>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <div v-if="currentTagMerchants.length === 0" class="empty-state">
            <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#ccc" stroke-width="1.5">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
              <circle cx="9" cy="7" r="4"></circle>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
            </svg>
            <p>该标签暂无关联商家</p>
          </div>
          <div v-else class="merchant-list">
            <div 
              v-for="merchant in currentTagMerchants" 
              :key="merchant.id" 
              class="merchant-item"
            >
              <div class="merchant-avatar">
                <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M8 21l1-17a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4l1 17"></path>
                </svg>
              </div>
              <div class="merchant-info">
                <div class="merchant-name-row">
                  <span class="merchant-name">{{ merchant.name }}</span>
                  <span :class="['status-tag', merchant.operationStatus.toLowerCase()]">
                    {{ merchant.operationStatus === 'OPERATING' ? '营业中' : '停业' }}
                  </span>
                </div>
                <p class="merchant-desc">{{ merchant.description || '暂无描述' }}</p>
                <div class="merchant-tags">
                  <span v-for="tag in [merchant.category, merchant.cuisine].filter(Boolean)" :key="tag" class="mini-tag">{{ tag }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-primary" @click="closeTagMerchantListModal">关闭</button>
        </div>
      </div>
    </div>

    <div v-if="showDeleteConfirmModal" class="modal-overlay" @click.self="cancelDeleteTopic">
      <div class="modal-content confirm-modal">
        <div class="modal-icon-wrapper danger">
          <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" stroke-width="1.5">
            <polyline points="3 6 9 12 3 18"></polyline>
            <line x1="18" y1="6" x2="18" y2="18"></line>
          </svg>
        </div>
        <div class="modal-body-content">
          <h3 class="modal-title">确认删除</h3>
          <p class="modal-message">确定要删除专题「<strong>{{ topicToDelete?.name }}</strong>」吗？</p>
          <p class="modal-hint">此操作不可恢复，所有关联的商家和标签关系也将被删除。</p>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" @click="cancelDeleteTopic">取消</button>
          <button class="btn btn-danger" @click="performDeleteTopic">确认删除</button>
        </div>
      </div>
    </div>

    <div v-if="showPublishConfirmModal" class="modal-overlay" @click.self="cancelPublishTopic">
      <div class="modal-content confirm-modal">
        <div class="modal-icon-wrapper warning">
          <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
            <line x1="12" y1="9" x2="12" y2="13"></line>
            <line x1="12" y1="17" x2="12.01" y2="17"></line>
          </svg>
        </div>
        <div class="modal-body-content">
          <h3 class="modal-title">确认{{ publishAction === 'publish' ? '发布' : '下架' }}</h3>
          <p class="modal-message">确定要{{ publishAction === 'publish' ? '发布' : '下架' }}专题「<strong>{{ topicToPublish?.name }}</strong>」吗？</p>
          <p v-if="publishAction === 'publish'" class="modal-hint">发布后该专题将对所有用户可见，请确保内容完整且符合平台规范。</p>
          <p v-else class="modal-hint">下架后该专题将不再对用户展示，但数据将被保留。</p>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" @click="cancelPublishTopic">取消</button>
          <button :class="['btn', publishAction === 'publish' ? 'btn-primary' : 'btn-danger']" @click="performPublishTopic">确认{{ publishAction === 'publish' ? '发布' : '下架' }}</button>
        </div>
      </div>
    </div>

    <div v-if="showToastModal" class="modal-overlay" @click.self="closeToastModal">
      <div class="modal-content toast-modal">
        <div :class="['modal-icon-wrapper', toastType]">
          <svg v-if="toastType === 'error'" viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="15" y1="9" x2="9" y2="15"></line>
            <line x1="9" y1="9" x2="15" y2="15"></line>
          </svg>
          <svg v-else-if="toastType === 'success'" viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
            <polyline points="22 4 12 14.01 9 11.01"></polyline>
          </svg>
          <svg v-else-if="toastType === 'warning'" viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
            <line x1="12" y1="9" x2="12" y2="13"></line>
            <line x1="12" y1="17" x2="12.01" y2="17"></line>
          </svg>
          <svg v-else viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="16" x2="12" y2="12"></line>
            <line x1="12" y1="8" x2="12.01" y2="8"></line>
          </svg>
        </div>
        <div class="modal-body-content">
          <h3 class="modal-title">{{ toastTitle }}</h3>
          <p class="modal-message">{{ toastMessage }}</p>
        </div>
        <div class="modal-footer">
          <button class="btn btn-primary" @click="closeToastModal">知道了</button>
        </div>
      </div>
    </div>

    <div v-if="showTagDeleteConfirmModal" class="modal-overlay" @click.self="cancelDeleteTag">
      <div class="modal-content confirm-modal">
        <div class="modal-icon-wrapper danger">
          <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" stroke-width="1.5">
            <polyline points="3 6 9 12 3 18"></polyline>
            <line x1="18" y1="6" x2="18" y2="18"></line>
          </svg>
        </div>
        <div class="modal-body-content">
          <h3 class="modal-title">确认删除</h3>
          <p class="modal-message">确定要删除标签「<strong>{{ tagToDelete?.name }}</strong>」吗？</p>
          <p class="modal-hint">此操作不可恢复，所有关联的专题和商家关系也将被删除。</p>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" @click="cancelDeleteTag">取消</button>
          <button class="btn btn-danger" @click="performDeleteTag">确认删除</button>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import { getTopics, getTopic, createTopic, updateTopic, deleteTopic, getTopicMerchants, addTopicMerchant, removeTopicMerchant, getTags, createTag, deleteTag, getTagMerchants } from '../../api/topic'

const activeTab = ref('topics')
const sidebarItems = [
  { key: 'topics', label: '专题列表', icon: '📋' },
  { key: 'tags', label: '分类标签', icon: '🏷️' },
]

const searchKeyword = ref('')
const statusFilter = ref('')
const topics = ref([])
const loading = ref(false)
const defaultCover = 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=food%20blog%20banner%20with%20delicious%20dishes%20and%20restaurant%20atmosphere&image_size=landscape_16_9'

const tagSearchKeyword = ref('')
const tagTypeFilter = ref('')
const tags = ref([])

const showTopicModal = ref(false)
const editingTopic = ref(null)
const topicForm = ref({
  name: '',
  description: '',
  coverImage: '',
  tags: [],
  merchants: [],
  status: 'DRAFT'
})

const merchantSearch = ref('')
const searchResults = ref([])
const showMerchantSearch = ref(false)

const showTagModal = ref(false)
const editingTag = ref(null)
const tagForm = ref({
  name: '',
  type: 'category'
})

const showMerchantListModal = ref(false)
const currentTopic = ref(null)
const currentTopicMerchants = ref([])

const showTagMerchantListModal = ref(false)

const showDeleteConfirmModal = ref(false)
const topicToDelete = ref(null)
const currentTag = ref(null)
const currentTagMerchants = ref([])

const showPublishConfirmModal = ref(false)
const topicToPublish = ref(null)
const publishAction = ref('')

const showToastModal = ref(false)
const toastType = ref('info')
const toastTitle = ref('')
const toastMessage = ref('')

const showTagDeleteConfirmModal = ref(false)
const tagToDelete = ref(null)

const tagTypes = [
  { key: 'category', label: '餐饮类型' },
  { key: 'cuisine', label: '菜系' },
  { key: 'scene', label: '消费场景' },
  { key: 'environment', label: '环境特点' },
  { key: 'price', label: '价格区间' },
]

const availableTags = computed(() => tags.value)

const getTagsByType = (type) => {
  return tags.value.filter(tag => tag.type === type)
}

const loadTopics = async () => {
  loading.value = true
  try {
    const params = {}
    if (statusFilter.value) params.status = statusFilter.value
    if (searchKeyword.value) params.keyword = searchKeyword.value
    const response = await getTopics(params)
    if (response.success && response.data) {
      topics.value = response.data.map(topic => ({
        ...topic,
        coverImage: topic.coverUrl,
        tags: topic.tags || [],
        merchants: []
      }))
    }
  } catch (error) {
    console.error('加载专题列表失败:', error)
    topics.value = [
      { 
        id: 1, 
        name: '夜宵好去处', 
        description: '深夜食堂推荐，探索城市夜晚的美味', 
        coverImage: '', 
        tags: ['夜宵', '烧烤', '小吃'], 
        merchantCount: 3, 
        status: 'PUBLISHED', 
        updatedAt: '2026-07-18 10:30',
        merchants: []
      },
      { 
        id: 2, 
        name: '网红打卡餐厅', 
        description: '刷爆朋友圈的高颜值餐厅', 
        coverImage: '', 
        tags: ['网红', '拍照', '高颜值'], 
        merchantCount: 2, 
        status: 'PUBLISHED', 
        updatedAt: '2026-07-17 15:45',
        merchants: []
      },
      { 
        id: 3, 
        name: '适合约会', 
        description: '浪漫氛围，甜蜜约会首选', 
        coverImage: '', 
        tags: ['约会', '浪漫', '情侣'], 
        merchantCount: 3, 
        status: 'DRAFT', 
        updatedAt: '2026-07-16 09:20',
        merchants: []
      },
      { 
        id: 4, 
        name: '高性价比美食', 
        description: '好吃不贵，性价比之王', 
        coverImage: '', 
        tags: ['性价比', '实惠', '学生党'], 
        merchantCount: 4, 
        status: 'PUBLISHED', 
        updatedAt: '2026-07-15 14:00',
        merchants: []
      },
      { 
        id: 5, 
        name: '商务宴请', 
        description: '高端大气，商务聚餐首选', 
        coverImage: '', 
        tags: ['商务', '高端', '宴请'], 
        merchantCount: 2, 
        status: 'OFFLINE', 
        updatedAt: '2026-07-14 11:30',
        merchants: []
      },
    ]
  } finally {
    loading.value = false
  }
}

const loadTags = async () => {
  try {
    const params = {}
    if (tagTypeFilter.value) params.category = tagTypeFilter.value
    if (tagSearchKeyword.value) params.keyword = tagSearchKeyword.value
    const response = await getTags(params)
    if (response.success && response.data) {
      const tagMap = new Map()
      response.data.forEach(tag => {
        if (!tagMap.has(tag.name)) {
          tagMap.set(tag.name, {
            ...tag,
            type: tag.category
          })
        }
      })
      tags.value = Array.from(tagMap.values())
    }
  } catch (error) {
    console.error('加载标签列表失败:', error)
    tags.value = [
      { id: 1, name: '烧烤', type: 'category', count: 25 },
      { id: 2, name: '火锅', type: 'category', count: 30 },
      { id: 3, name: '麻辣', type: 'category', count: 20 },
      { id: 4, name: '川菜', type: 'cuisine', count: 18 },
      { id: 5, name: '粤菜', type: 'cuisine', count: 12 },
      { id: 6, name: '夜宵', type: 'scene', count: 15 },
      { id: 7, name: '约会', type: 'scene', count: 20 },
      { id: 8, name: '环境优雅', type: 'environment', count: 10 },
      { id: 9, name: '人均50以下', type: 'price', count: 35 },
      { id: 10, name: '人均50-100', type: 'price', count: 28 },
      { id: 11, name: '快餐', type: 'category', count: 40 },
      { id: 12, name: '西餐', type: 'cuisine', count: 8 },
      { id: 13, name: '家庭聚餐', type: 'scene', count: 16 },
      { id: 14, name: '网红', type: 'environment', count: 14 },
      { id: 15, name: '人均100-200', type: 'price', count: 18 },
      { id: 16, name: '日料', type: 'cuisine', count: 15 },
    ]
  }
}

const getStatusText = (status) => {
  switch (status) {
    case 'PUBLISHED': return '公开'
    case 'DRAFT': return '草稿'
    case 'OFFLINE': return '已下架'
    default: return status
  }
}

const formatTime = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const hours = Math.floor(diff / (1000 * 60 * 60))
  const days = Math.floor(hours / 24)
  
  if (hours < 1) return '刚刚'
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return date.toLocaleDateString('zh-CN')
}

const openTopicModal = (topic = null) => {
  if (topic) {
    editingTopic.value = topic
    topicForm.value = {
      ...topic,
      tags: [...topic.tags],
      merchants: [...(topic.merchants || [])]
    }
  } else {
    editingTopic.value = null
    topicForm.value = {
      name: '',
      description: '',
      coverImage: '',
      tags: [],
      merchants: [],
      status: 'DRAFT'
    }
  }
  showTopicModal.value = true
}

const closeTopicModal = () => {
  showTopicModal.value = false
  editingTopic.value = null
  topicForm.value = {
    name: '',
    description: '',
    coverImage: '',
    tags: [],
    merchants: [],
    status: 'DRAFT'
  }
  merchantSearch.value = ''
  searchResults.value = []
}

const toggleTag = (tagName) => {
  const index = topicForm.value.tags.indexOf(tagName)
  if (index === -1) {
    topicForm.value.tags.push(tagName)
  } else {
    topicForm.value.tags.splice(index, 1)
  }
}

const allMerchants = [
  { id: 1, name: '老北京火锅店', operationStatus: 'OPERATING', description: '地道老北京铜锅涮肉', category: '火锅', cuisine: '京菜' },
  { id: 2, name: '川湘人家', operationStatus: 'OPERATING', description: '正宗川菜湘菜，麻辣鲜香', category: '川菜', cuisine: '川菜' },
  { id: 3, name: '深夜食堂', operationStatus: 'OPERATING', description: '24小时营业的日式居酒屋', category: '日料', cuisine: '日式' },
  { id: 4, name: '网红打卡餐厅', operationStatus: 'SUSPENDED', description: '刷爆朋友圈的高颜值餐厅', category: '西餐', cuisine: '西式' },
  { id: 5, name: '浪漫西餐厅', operationStatus: 'OPERATING', description: '法式浪漫氛围，适合约会', category: '西餐', cuisine: '法式' },
  { id: 6, name: '粤港茶餐厅', operationStatus: 'OPERATING', description: '正宗港式茶点，早茶首选', category: '粤菜', cuisine: '港式' },
  { id: 7, name: '日式居酒屋', operationStatus: 'OPERATING', description: '日式清酒、烤串，深夜小酌', category: '日料', cuisine: '日式' },
  { id: 8, name: '韩式烤肉店', operationStatus: 'OPERATING', description: '炭火烤肉，正宗韩式风味', category: '韩式', cuisine: '韩式' },
  { id: 9, name: '泰式料理', operationStatus: 'OPERATING', description: '酸辣开胃，正宗东南亚风味', category: '东南亚', cuisine: '泰式' },
  { id: 10, name: '意大利餐厅', operationStatus: 'OPERATING', description: '手工意面，正宗意式风味', category: '西餐', cuisine: '意式' },
  { id: 11, name: '法式甜品店', operationStatus: 'OPERATING', description: '精致法式甜点，下午茶首选', category: '甜品', cuisine: '法式' },
  { id: 12, name: '川菜小馆', operationStatus: 'OPERATING', description: '家常川菜，价格实惠', category: '川菜', cuisine: '川菜' },
  { id: 13, name: '云南风味', operationStatus: 'OPERATING', description: '特色滇菜，酸辣鲜香', category: '滇菜', cuisine: '云南' },
  { id: 14, name: '新疆大盘鸡', operationStatus: 'OPERATING', description: '正宗新疆风味，分量十足', category: '西北菜', cuisine: '新疆' },
  { id: 15, name: '东北铁锅炖', operationStatus: 'OPERATING', description: '东北特色，铁锅炖一切', category: '东北菜', cuisine: '东北' },
  { id: 16, name: '潮汕牛肉火锅', operationStatus: 'OPERATING', description: '鲜切牛肉，清汤锅底', category: '火锅', cuisine: '潮汕' },
  { id: 17, name: '海鲜大排档', operationStatus: 'OPERATING', description: '新鲜海鲜，现点现做', category: '海鲜', cuisine: '海鲜' },
  { id: 18, name: '贵州酸汤鱼', operationStatus: 'OPERATING', description: '正宗酸汤鱼，酸爽开胃', category: '黔菜', cuisine: '贵州' },
  { id: 19, name: '陕西肉夹馍', operationStatus: 'OPERATING', description: '正宗陕西风味，馍酥肉香', category: '西北菜', cuisine: '陕西' },
  { id: 20, name: '台式卤肉饭', operationStatus: 'OPERATING', description: '地道台湾风味，下饭神器', category: '小吃', cuisine: '台式' },
]

const searchMerchants = () => {
  const keyword = merchantSearch.value.trim().toLowerCase()
  searchResults.value = allMerchants.filter(merchant => {
    if (!keyword) return true
    return merchant.name.toLowerCase().includes(keyword) ||
           merchant.description.toLowerCase().includes(keyword) ||
           merchant.category.toLowerCase().includes(keyword) ||
           merchant.cuisine.toLowerCase().includes(keyword)
  })
}

const toggleMerchantSearch = () => {
  showMerchantSearch.value = !showMerchantSearch.value
  if (showMerchantSearch.value) {
    merchantSearch.value = ''
    searchMerchants()
  }
}

const isMerchantSelected = (merchantId) => {
  return topicForm.value.merchants.some(m => m.id === merchantId)
}

const toggleMerchant = (merchant) => {
  if (merchant.operationStatus !== 'OPERATING') {
    showToast('warning', '无法选择', '已停业商家不能加入公开专题')
    return
  }
  
  const index = topicForm.value.merchants.findIndex(m => m.id === merchant.id)
  if (index === -1) {
    topicForm.value.merchants.push(merchant)
  } else {
    topicForm.value.merchants.splice(index, 1)
  }
}

const showToast = (type, title, message) => {
  toastType.value = type
  toastTitle.value = title
  toastMessage.value = message
  showToastModal.value = true
}

const closeToastModal = () => {
  showToastModal.value = false
}

const removeMerchant = (merchantId) => {
  topicForm.value.merchants = topicForm.value.merchants.filter(m => m.id !== merchantId)
}

const toggleTopicStatus = (topic) => {
  topicToPublish.value = topic
  publishAction.value = topic.status === 'PUBLISHED' ? 'offline' : 'publish'
  showPublishConfirmModal.value = true
}

const cancelPublishTopic = () => {
  showPublishConfirmModal.value = false
  topicToPublish.value = null
  publishAction.value = ''
}

const performPublishTopic = async () => {
  if (!topicToPublish.value) return
  
  const newStatus = publishAction.value === 'publish' ? 'PUBLISHED' : 'OFFLINE'
  
  try {
    const response = await updateTopic(topicToPublish.value.id, { 
      ...topicToPublish.value, 
      status: newStatus, 
      merchantIds: [], 
      tagNames: [] 
    })
    if (response.success) {
      topicToPublish.value.status = newStatus
      showToast('success', '操作成功', `${publishAction.value === 'publish' ? '发布' : '下架'}成功`)
    } else {
      showToast('error', '操作失败', response.message || '更新失败，请稍后重试')
    }
  } catch (error) {
    console.error('更新专题状态失败:', error)
    showToast('error', '操作失败', '更新失败，请稍后重试')
  }
  
  cancelPublishTopic()
}

const confirmDeleteTopic = (topic) => {
  topicToDelete.value = topic
  showDeleteConfirmModal.value = true
}

const cancelDeleteTopic = () => {
  showDeleteConfirmModal.value = false
  topicToDelete.value = null
}

const performDeleteTopic = async () => {
  if (!topicToDelete.value) return
  try {
    const response = await deleteTopic(topicToDelete.value.id)
    if (response.success) {
      topics.value = topics.value.filter(t => t.id !== topicToDelete.value.id)
      showDeleteConfirmModal.value = false
      topicToDelete.value = null
      showToast('success', '删除成功', '专题已成功删除')
    } else {
      showToast('error', '删除失败', response.message || '删除失败，请稍后重试')
    }
  } catch (error) {
    console.error('删除专题失败:', error)
    showToast('error', '删除失败', '删除失败，请稍后重试')
  }
}

const saveTopic = async () => {
  if (!topicForm.value.name.trim()) {
    showToast('warning', '输入提示', '请输入专题名称')
    return
  }
  
  if (topicForm.value.status === 'PUBLISHED' && topicForm.value.merchants.length === 0) {
    showToast('warning', '输入提示', '公开专题必须至少关联一个商家')
    return
  }
  
  try {
    const requestData = {
      name: topicForm.value.name,
      description: topicForm.value.description,
      coverUrl: topicForm.value.coverImage,
      status: topicForm.value.status,
      merchantIds: topicForm.value.merchants.map(m => m.id),
      tagNames: topicForm.value.tags
    }
    
    let response
    if (editingTopic.value) {
      response = await updateTopic(editingTopic.value.id, requestData)
    } else {
      response = await createTopic(requestData)
    }
    
    if (response.success) {
      closeTopicModal()
      loadTopics()
    } else {
      showToast('error', '保存失败', response.message || '保存失败，请稍后重试')
    }
  } catch (error) {
    console.error('保存专题失败:', error)
    showToast('error', '保存失败', '保存失败，请稍后重试')
  }
}

const openTagModal = (type = 'category') => {
  editingTag.value = null
  tagForm.value = {
    name: '',
    type: type
  }
  showTagModal.value = true
}

const closeTagModal = () => {
  showTagModal.value = false
  editingTag.value = null
  tagForm.value = {
    name: '',
    type: 'category'
  }
}

const saveTag = async () => {
  if (!tagForm.value.name.trim()) {
    showToast('warning', '输入提示', '请输入标签名称')
    return
  }
  
  try {
    const response = await createTag(tagForm.value.name, tagForm.value.type)
    if (response.success) {
      closeTagModal()
      loadTags()
    } else {
      showToast('error', '保存失败', response.message || '保存失败，请稍后重试')
    }
  } catch (error) {
    console.error('保存标签失败:', error)
    showToast('error', '保存失败', '保存失败，请稍后重试')
  }
}

const removeTag = (tag) => {
  tagToDelete.value = tag
  showTagDeleteConfirmModal.value = true
}

const cancelDeleteTag = () => {
  showTagDeleteConfirmModal.value = false
  tagToDelete.value = null
}

const performDeleteTag = async () => {
  if (!tagToDelete.value) return
  
  try {
    const response = await deleteTag(tagToDelete.value.id)
    if (response.success) {
      tags.value = tags.value.filter(t => t.id !== tagToDelete.value.id)
      showToast('success', '删除成功', '标签已成功删除')
    } else {
      showToast('error', '删除失败', response.message || '删除失败，请稍后重试')
    }
  } catch (error) {
    console.error('删除标签失败:', error)
    showToast('error', '删除失败', '删除失败，请稍后重试')
  }
  
  cancelDeleteTag()
}

const openMerchantListModal = async (topic) => {
  currentTopic.value = topic
  currentTopicMerchants.value = []
  showMerchantListModal.value = true
  
  try {
    const response = await getTopicMerchants(topic.id)
    if (response.success && response.data) {
      currentTopicMerchants.value = response.data.map(m => ({
        ...m,
        tags: [m.category, m.cuisine].filter(Boolean)
      }))
    }
  } catch (error) {
    console.error('加载专题商家失败:', error)
    currentTopicMerchants.value = [
      { id: 101, name: '深夜食堂', operationStatus: 'OPERATING', description: '24小时营业的日式居酒屋', tags: ['日式', '居酒屋'] },
      { id: 102, name: '老北京烧烤', operationStatus: 'OPERATING', description: '传统炭火烧烤，地道老北京味', tags: ['烧烤'] },
    ]
  }
}

const closeMerchantListModal = () => {
  showMerchantListModal.value = false
  currentTopic.value = null
  currentTopicMerchants.value = []
}

const openTagMerchantListModal = async (tag) => {
  currentTag.value = tag
  currentTagMerchants.value = []
  showTagMerchantListModal.value = true
  
  try {
    const response = await getTagMerchants(tag.id)
    if (response.success && response.data) {
      currentTagMerchants.value = response.data
    }
  } catch (error) {
    console.error('加载标签关联商家失败:', error)
    currentTagMerchants.value = []
  }
}

const closeTagMerchantListModal = () => {
  showTagMerchantListModal.value = false
  currentTag.value = null
  currentTagMerchants.value = []
}

onMounted(() => {
  loadTopics()
  loadTags()
})
</script>

<style scoped>
.tab-content {
  width: 100%;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.search-item {
  flex: 1;
  min-width: 200px;
  max-width: 300px;
}

.search-input {
  width: 100%;
  padding: 10px 16px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
}

.search-input:focus {
  outline: none;
  border-color: #1890ff;
}

.search-select {
  padding: 10px 16px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  background: #fff;
}

.search-btn {
  padding: 10px 20px;
  background: #f5f5f5;
  color: #666;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
}

.search-btn:hover {
  background: #e8e8e8;
}

.add-btn {
  padding: 10px 20px;
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
}

.add-btn:hover {
  opacity: 0.9;
}

.topics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
}

.topic-card {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  border: 1px solid rgba(24, 144, 255, 0.15);
  box-shadow: 0 4px 20px rgba(24, 144, 255, 0.08);
  overflow: hidden;
  transition: all 0.3s;
}

.topic-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 32px rgba(24, 144, 255, 0.15);
}

.topic-card.offline {
  opacity: 0.6;
}

.topic-card.draft {
  border-color: rgba(250, 140, 22, 0.3);
}

.topic-cover {
  position: relative;
  height: 160px;
  overflow: hidden;
}

.topic-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.status-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.status-badge.published {
  background: rgba(82, 196, 26, 0.9);
  color: #fff;
}

.status-badge.draft {
  background: rgba(250, 140, 22, 0.9);
  color: #fff;
}

.status-badge.offline {
  background: rgba(153, 153, 153, 0.9);
  color: #fff;
}

.topic-info {
  padding: 20px;
}

.topic-info h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0 0 8px;
}

.topic-desc {
  font-size: 13px;
  color: #667085;
  margin: 0 0 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.topic-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.topic-tag {
  padding: 4px 10px;
  background: rgba(24, 144, 255, 0.1);
  color: #1890ff;
  border-radius: 16px;
  font-size: 12px;
}

.topic-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.merchant-count {
  font-size: 12px;
  color: #667085;
}

.update-time {
  font-size: 12px;
  color: #999;
}

.topic-actions {
  padding: 0 20px 20px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.action-btn {
  padding: 8px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.edit-btn {
  background: #f0f5ff;
  color: #1890ff;
}

.edit-btn:hover {
  background: #d6e4ff;
}

.status-btn {
  background: #f0f5ff;
  color: #52c41a;
}

.status-btn:hover {
  background: #d9f7be;
}

.view-btn {
  background: #f6ffed;
  color: #52c41a;
}

.view-btn:hover {
  background: #d9f7be;
}

.merchant-count {
  cursor: pointer;
  transition: color 0.2s;
}

.merchant-count:hover {
  color: #1890ff;
}

.merchant-list-modal {
  max-width: 700px;
}

.merchant-list {
  max-height: 400px;
  overflow-y: auto;
}

.merchant-item {
  display: flex;
  gap: 16px;
  padding: 16px;
  background: #fafafa;
  border-radius: 12px;
  margin-bottom: 12px;
}

.merchant-item:last-child {
  margin-bottom: 0;
}

.merchant-avatar {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.merchant-info {
  flex: 1;
  min-width: 0;
}

.merchant-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.merchant-name-row .merchant-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
}

.status-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
}

.status-tag.operating {
  background: rgba(82, 196, 26, 0.15);
  color: #52c41a;
}

.status-tag.suspended {
  background: rgba(255, 177, 0, 0.15);
  color: #ffb100;
}

.merchant-desc {
  font-size: 13px;
  color: #667085;
  margin: 0 0 8px;
}

.merchant-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.mini-tag {
  padding: 3px 8px;
  background: rgba(24, 144, 255, 0.1);
  color: #1890ff;
  border-radius: 8px;
  font-size: 11px;
}

.empty-state {
  grid-column: 1 / -1;
  text-align: center;
  padding: 60px 20px;
}

.empty-state p {
  margin-top: 16px;
  color: #999;
}

.tags-section {
  margin-top: 24px;
}

.tag-group {
  margin-bottom: 32px;
}

.tag-group-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2d3d;
  margin-bottom: 16px;
  padding-left: 8px;
  border-left: 3px solid #1890ff;
}

.tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.tag-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 8px;
  border: 1px solid rgba(24, 144, 255, 0.15);
}

.tag-name {
  font-size: 14px;
  color: #1f2d3d;
}

.tag-count {
  font-size: 12px;
  color: #999;
  cursor: pointer;
  transition: color 0.2s;
}

.tag-count:hover {
  color: #1890ff;
}

.view-tag-merchants-btn {
  padding: 4px;
  background: none;
  border: none;
  color: #999;
  cursor: pointer;
  transition: color 0.2s;
}

.view-tag-merchants-btn:hover {
  color: #1890ff;
}

.remove-tag-btn {
  padding: 4px;
  background: none;
  border: none;
  color: #999;
  cursor: pointer;
}

.remove-tag-btn:hover {
  color: #ff4d4f;
}

.add-tag-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  background: rgba(24, 144, 255, 0.05);
  border: 1px dashed #1890ff;
  border-radius: 8px;
  color: #1890ff;
  font-size: 14px;
  cursor: pointer;
}

.add-tag-btn:hover {
  background: rgba(24, 144, 255, 0.1);
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  width: 90%;
  max-width: 600px;
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
}

.modal-header {
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal-header h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.modal-close {
  background: none;
  border: none;
  color: #999;
  cursor: pointer;
  padding: 4px;
}

.modal-body {
  padding: 24px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #1f2d3d;
  margin-bottom: 8px;
}

.form-input,
.form-select,
.form-textarea {
  width: 100%;
  padding: 10px 16px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  box-sizing: border-box;
}

.form-textarea {
  resize: vertical;
}

.form-input:focus,
.form-select:focus,
.form-textarea:focus {
  outline: none;
  border-color: #1890ff;
}

.tags-select {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
  border: 1px solid #e8e8e8;
}

.tag-option {
  padding: 6px 12px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 20px;
  font-size: 13px;
  cursor: pointer;
}

.tag-option.selected {
  background: rgba(24, 144, 255, 0.1);
  border-color: #1890ff;
  color: #1890ff;
}

.merchant-select {
  position: relative;
}

.merchant-search-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  background: #fff;
  color: #666;
}

.merchant-search-trigger:hover {
  border-color: #1890ff;
  color: #1890ff;
}

.toggle-icon {
  transition: transform 0.3s;
}

.toggle-icon.expanded {
  transform: rotate(180deg);
}

.merchant-search-panel {
  margin-top: 8px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.merchant-search-input {
  width: 100%;
  padding: 10px 16px;
  border: none;
  border-bottom: 1px solid #e8e8e8;
  font-size: 14px;
  box-sizing: border-box;
}

.merchant-search-input:focus {
  outline: none;
}

.merchant-search-scroll {
  max-height: 250px;
  overflow-y: auto;
}

.merchant-option {
  display: flex;
  justify-content: space-between;
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
}

.merchant-option:last-child {
  border-bottom: none;
}

.merchant-option:hover {
  background: #f5f5f5;
}

.merchant-option.selected {
  background: rgba(24, 144, 255, 0.05);
}

.merchant-name {
  font-size: 14px;
}

.merchant-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 12px;
}

.merchant-status.operating {
  background: rgba(82, 196, 26, 0.1);
  color: #52c41a;
}

.merchant-status.suspended {
  background: rgba(255, 177, 0, 0.1);
  color: #ffb100;
}

.selected-merchants {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.selected-merchant {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: rgba(24, 144, 255, 0.1);
  border-radius: 20px;
  font-size: 13px;
}

.selected-merchant button {
  background: none;
  border: none;
  color: #999;
  cursor: pointer;
}

.selected-merchant button:hover {
  color: #ff4d4f;
}

.modal-footer {
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.btn {
  padding: 10px 24px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.btn-secondary {
  background: #f5f5f5;
  color: #666;
}

.btn-primary {
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  color: #fff;
}

.btn-danger {
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7875 100%);
  color: #fff;
}

.btn-danger:hover {
  opacity: 0.9;
}

.delete-btn {
  color: #ff4d4f;
}

.delete-btn:hover {
  background: rgba(255, 77, 79, 0.1);
}

.confirm-modal {
  max-width: 460px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25);
  overflow: hidden;
}

.toast-modal {
  max-width: 420px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.2);
  overflow: hidden;
}

.modal-icon-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 0 20px;
}

.modal-icon-wrapper.danger {
  color: #ff4d4f;
}

.modal-icon-wrapper.warning {
  color: #faad14;
}

.modal-icon-wrapper.success {
  color: #52c41a;
}

.modal-icon-wrapper.error {
  color: #ff4d4f;
}

.modal-icon-wrapper.info {
  color: #1890ff;
}

.modal-body-content {
  text-align: center;
  padding: 0 32px 24px;
}

.modal-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0 0 12px;
}

.modal-message {
  font-size: 15px;
  color: #434e59;
  margin: 0 0 8px;
  line-height: 1.6;
}

.modal-message strong {
  color: #1f2d3d;
}

.modal-hint {
  font-size: 13px;
  color: #8c9aa8;
  margin: 0;
  line-height: 1.5;
}

@media (max-width: 768px) {
  .search-bar {
    flex-direction: column;
    align-items: stretch;
  }
  
  .search-item {
    max-width: none;
  }
  
  .topics-grid {
    grid-template-columns: 1fr;
  }
}
</style>