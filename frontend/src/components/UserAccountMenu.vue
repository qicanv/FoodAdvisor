<template>
  <div
    ref="menuRoot"
    class="user-account-menu"
    :class="`role-${role}`"
  >
    <button
      type="button"
      class="account-trigger"
      aria-haspopup="menu"
      :aria-expanded="isOpen"
      :aria-controls="menuId"
      :aria-label="`${displayName}账户菜单`"
      @click="toggleMenu"
    >
      <span class="account-avatar" aria-hidden="true">{{ avatarText }}</span>
      <span class="account-name">{{ displayName }}</span>
      <svg
        class="account-arrow"
        :class="{ open: isOpen }"
        viewBox="0 0 16 16"
        width="14"
        height="14"
        aria-hidden="true"
      >
        <path d="m4 6 4 4 4-4" />
      </svg>
    </button>

    <div
      v-if="isOpen"
      :id="menuId"
      class="account-dropdown"
      role="menu"
      :aria-label="`${displayName}账户操作`"
    >
      <button
        v-if="role === 'diner' && profilePath"
        type="button"
        class="menu-item"
        role="menuitem"
        @click="goToProfile"
      >
        个人中心
      </button>

      <div
        v-if="role === 'diner' && profilePath"
        class="menu-divider"
        role="separator"
      ></div>

      <button
        type="button"
        class="menu-item logout-item"
        role="menuitem"
        @click="handleLogout"
      >
        退出登录
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const props = defineProps({
  role: {
    type: String,
    required: true,
    validator: value => ['diner', 'merchant', 'admin'].includes(value)
  },
  profilePath: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['logged-out'])
const router = useRouter()
const route = useRoute()
const menuRoot = ref(null)
const isOpen = ref(false)
const menuId = `user-account-menu-${Math.random().toString(36).slice(2, 10)}`

const roleDefaults = {
  diner: {
    name: '食客用户',
    avatar: '食',
    loginPath: '/diner'
  },
  merchant: {
    name: '商户',
    avatar: '商',
    loginPath: '/merchant'
  },
  admin: {
    name: '管理员',
    avatar: '管',
    loginPath: '/admin'
  }
}

const readStoredUser = () => {
  const rawUser = localStorage.getItem('user')

  if (!rawUser) {
    return {}
  }

  try {
    const parsedUser = JSON.parse(rawUser)
    return parsedUser && typeof parsedUser === 'object' ? parsedUser : {}
  } catch {
    return {}
  }
}

const user = ref(readStoredUser())

const storedName = computed(() => {
  const candidate = (
    user.value.nickname ||
    user.value.username ||
    user.value.name ||
    ''
  )

  return typeof candidate === 'string' ? candidate.trim() : String(candidate).trim()
})

const displayName = computed(() => (
  storedName.value || roleDefaults[props.role].name
))

const avatarText = computed(() => {
  if (!storedName.value) {
    return roleDefaults[props.role].avatar
  }

  const firstCharacter = Array.from(storedName.value)[0]
  return /^[a-z]$/i.test(firstCharacter)
    ? firstCharacter.toUpperCase()
    : firstCharacter
})

const closeMenu = () => {
  isOpen.value = false
}

const toggleMenu = () => {
  isOpen.value = !isOpen.value
}

const handleDocumentClick = event => {
  if (isOpen.value && !menuRoot.value?.contains(event.target)) {
    closeMenu()
  }
}

const handleDocumentKeydown = event => {
  if (event.key === 'Escape') {
    closeMenu()
  }
}

const goToProfile = async () => {
  closeMenu()

  if (props.profilePath) {
    await router.push(props.profilePath)
  }
}

const handleLogout = async () => {
  closeMenu()

  const storageKeys = [
    'token',
    'accessToken',
    'user',
    'userInfo',
    'userRole',
    'userId',
    'activeMerchantId'
  ]

  storageKeys.forEach(key => localStorage.removeItem(key))
  emit('logged-out')
  await router.replace(roleDefaults[props.role].loginPath)
}

watch(() => route.fullPath, closeMenu)

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
  document.addEventListener('keydown', handleDocumentKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
  document.removeEventListener('keydown', handleDocumentKeydown)
})
</script>

<style scoped>
.user-account-menu {
  position: relative;
  display: inline-flex;
  max-width: 100%;
  flex: 0 0 auto;
  font-family: inherit;
}

.account-trigger {
  display: inline-flex;
  max-width: 100%;
  min-height: 40px;
  align-items: center;
  gap: 9px;
  padding: 4px 9px 4px 4px;
  border: 1px solid transparent;
  border-radius: 999px;
  color: #35312d;
  font: inherit;
  background: rgba(255, 255, 255, 0.86);
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.account-trigger:hover,
.account-trigger:focus-visible,
.account-trigger[aria-expanded='true'] {
  outline: none;
  box-shadow: 0 4px 14px rgba(45, 36, 28, 0.1);
}

.role-diner .account-trigger:hover,
.role-diner .account-trigger:focus-visible,
.role-diner .account-trigger[aria-expanded='true'] {
  border-color: #fdba74;
  background: #fff8f1;
}

.role-merchant .account-trigger:hover,
.role-merchant .account-trigger:focus-visible,
.role-merchant .account-trigger[aria-expanded='true'] {
  border-color: #86c779;
  background: #f4fbf2;
}

.role-admin .account-trigger:hover,
.role-admin .account-trigger:focus-visible,
.role-admin .account-trigger[aria-expanded='true'] {
  border-color: #a9b8f5;
  background: #f6f7ff;
}

.account-avatar {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  line-height: 1;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.28);
}

.role-diner .account-avatar {
  background: linear-gradient(135deg, #f97316, #fb923c);
}

.role-merchant .account-avatar {
  background: linear-gradient(135deg, #2f7d32, #63a953);
}

.role-admin .account-avatar {
  background: linear-gradient(135deg, #4f67d8, #7c5cc4);
}

.account-name {
  overflow: hidden;
  max-width: 160px;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-arrow {
  flex: 0 0 auto;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.7;
  opacity: 0.58;
  transition: transform 0.2s ease;
}

.account-arrow.open {
  transform: rotate(180deg);
}

.account-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 10000;
  width: max-content;
  min-width: 164px;
  max-width: min(240px, calc(100vw - 24px));
  padding: 6px;
  border: 1px solid rgba(35, 28, 22, 0.08);
  border-radius: 12px;
  background: #fff;
  box-shadow:
    0 14px 34px rgba(39, 31, 24, 0.14),
    0 3px 10px rgba(39, 31, 24, 0.08);
}

.menu-item {
  display: flex;
  width: 100%;
  min-height: 38px;
  align-items: center;
  padding: 8px 11px;
  border: 0;
  border-radius: 8px;
  color: #3f3a35;
  font: inherit;
  font-size: 14px;
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.menu-item:hover,
.menu-item:focus-visible {
  outline: none;
  background: #f5f4f2;
}

.menu-divider {
  height: 1px;
  margin: 5px 7px;
  background: #ece9e5;
}

.logout-item {
  color: #b65353;
}

.logout-item:hover,
.logout-item:focus-visible {
  background: #fcf2f2;
}

@media (max-width: 640px) {
  .account-trigger {
    gap: 0;
    padding-right: 4px;
  }

  .account-name,
  .account-arrow {
    display: none;
  }
}
</style>
