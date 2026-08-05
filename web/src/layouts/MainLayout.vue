<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { CATEGORIES, HOT_KEYWORDS } from '@/data/mock'
import { fetchHotSearches } from '@/api/content'
import { getRecent, addRecent, clearRecent } from '@/utils/recent'
import { formatPerfMs, pagePerf } from '@/lib/pagePerf'
import NoticeBar from '@/components/NoticeBar.vue'
import ContactDialog from '@/components/ContactDialog.vue'
import { useSiteStore } from '@/store/site'

const siteStore = useSiteStore()
const { contactReady } = storeToRefs(siteStore)
const contactOpen = ref(false)

function openContact() {
  if (!contactReady.value) return
  contactOpen.value = true
}

const router = useRouter()
const route = useRoute()
const keyword = ref('')

const showSuggest = ref(false)
const recent = ref<string[]>([])
const hotSearches = ref<string[]>([...HOT_KEYWORDS])
/** 顶栏连点/连回车防抖（对齐老站结果页 500ms） */
let lastSearchTime = 0

const showBackTop = ref(false)
function onScroll() {
  showBackTop.value = window.scrollY > 480
}
function backToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  void fetchHotSearches(12).then((list) => {
    if (list.length) hotSearches.value = list
  })
})
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))

/** 从结果页带回关键词，保持顶栏一致 */
watch(
  () => route.query.q,
  (q) => {
    if (typeof q === 'string') keyword.value = q
  },
  { immediate: true },
)

function openSuggest() {
  recent.value = getRecent()
  showSuggest.value = true
}

function runSearch(word?: string) {
  const now = Date.now()
  if (now - lastSearchTime < 500) return
  const q = (word ?? keyword.value).trim()
  if (!q) return
  lastSearchTime = now
  if (word) keyword.value = word
  addRecent(q)
  showSuggest.value = false
  router.push({ name: 'find', query: { q } })
}

function clearHistory() {
  clearRecent()
  recent.value = []
}

function goCategory(c: string) {
  router.push({ name: 'search', query: { cat: c === '全部' ? undefined : c } })
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="container bar-inner">
        <div class="bar-left">
          <div class="brand" @click="router.push('/')">
            <span class="logo-mark">爱</span>
            <span class="logo">爱看</span>
            <span class="slogan">网盘资源信息流</span>
          </div>

          <nav class="topnav">
            <span :class="{ on: route.name === 'home' }" @click="router.push('/')">首页</span>
            <span :class="{ on: route.name === 'ranking' }" @click="router.push('/ranking')">榜单</span>
            <span :class="{ on: route.name === 'drama' }" @click="router.push('/drama')">短剧</span>
          </nav>
        </div>

        <div class="search-wrap">
          <div class="search" :class="{ open: showSuggest }">
            <el-icon class="s-icon"><Search /></el-icon>
            <input
              v-model="keyword"
              placeholder="搜电影 / 剧集 / 动漫 / 综艺…"
              @focus="openSuggest"
              @blur="showSuggest = false"
              @keyup.enter="runSearch()"
            />
            <button class="s-btn" @click="runSearch()">搜索</button>

            <div v-if="showSuggest" class="suggest" @mousedown.prevent>
              <div v-if="recent.length" class="sug-block">
                <div class="sug-head">
                  <span>最近搜索</span>
                  <a @click="clearHistory">清空</a>
                </div>
                <div class="sug-tags">
                  <span v-for="w in recent" :key="'r' + w" class="sug-tag" @click="runSearch(w)">
                    {{ w }}
                  </span>
                </div>
              </div>
              <div v-if="hotSearches.length" class="sug-block">
                <div class="sug-head"><span>热门搜索</span></div>
                <div class="sug-tags">
                  <span
                    v-for="(w, i) in hotSearches"
                    :key="'h' + w"
                    class="sug-tag hot"
                    @click="runSearch(w)"
                  >
                    <em :class="{ top: i < 3 }">{{ i + 1 }}</em>{{ w }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <nav class="bar-right">
          <router-link to="/about" :class="{ on: route.name === 'about' }">关于</router-link>
          <router-link to="/disclaimer" :class="{ on: route.name === 'disclaimer' }">免责</router-link>
        </nav>
      </div>

      <nav class="catbar">
        <div class="container cat-inner">
          <span
            v-for="c in CATEGORIES"
            :key="c"
            class="cat"
            :class="{ active: (route.query.cat || '全部') === c }"
            @click="goCategory(c)"
            >{{ c }}</span
          >
        </div>
      </nav>
    </header>

    <NoticeBar @open-contact="openContact" />

    <main class="content">
      <slot />
    </main>

    <footer class="footer">
      <div class="container foot-line">
        <router-link to="/about">关于爱看</router-link>
        <span class="sep">·</span>
        <router-link to="/disclaimer">免责声明</router-link>
        <span class="sep">·</span>
        <span class="tagline">爱看 · 网盘资源聚合信息流</span>
      </div>
      <div v-if="pagePerf.backend != null" class="container perf-line">
        后端: {{ formatPerfMs(pagePerf.backend) }} 毫秒
        <span class="sep">·</span>
        VUE: {{ formatPerfMs(pagePerf.vue) }} 毫秒
        <span class="sep">·</span>
        网络: {{ formatPerfMs(pagePerf.network) }} 毫秒
      </div>
    </footer>

    <button
      v-if="contactReady"
      class="fab-contact"
      type="button"
      aria-label="加群防失联"
      @click="openContact"
    >
      <span class="fab-icon">群</span>
      <span class="fab-text">加群防失联</span>
    </button>

    <transition name="fade-up">
      <button v-show="showBackTop" class="back-top" aria-label="回到顶部" @click="backToTop">
        <el-icon><Top /></el-icon>
      </button>
    </transition>

    <ContactDialog v-model="contactOpen" />
  </div>
</template>

<style scoped lang="scss">
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: saturate(180%) blur(16px);
  -webkit-backdrop-filter: saturate(180%) blur(16px);
  box-shadow: 0 1px 0 var(--border), 0 8px 24px -16px rgba(15, 23, 42, 0.22);
}

.bar-inner {
  height: 66px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 20px;
}

.bar-left {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-shrink: 0;
}

.search-wrap {
  display: flex;
  justify-content: center;
  min-width: 0;
}

.bar-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;

  a {
    padding: 6px 12px;
    border-radius: 8px;
    font-size: 14px;
    color: var(--text-soft);
    text-decoration: none;
    transition: all 0.15s;
    white-space: nowrap;

    &:hover {
      color: var(--brand);
      background: var(--brand-soft);
    }
    &.on {
      color: var(--brand-strong);
      font-weight: 600;
      background: var(--brand-soft);
    }
  }
}

.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  cursor: pointer;
  flex-shrink: 0;
  transition: opacity 0.15s;
  &:hover {
    opacity: 0.85;
  }

  .logo-mark {
    width: 32px;
    height: 32px;
    border-radius: 9px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    font-weight: 800;
    color: #fff;
    background: linear-gradient(135deg, var(--brand), var(--brand-strong));
    box-shadow: 0 4px 12px -2px color-mix(in srgb, var(--brand) 55%, transparent);
  }
  .logo {
    font-size: 22px;
    font-weight: 800;
    letter-spacing: 0.02em;
    color: var(--text);
  }
  .slogan {
    display: none;
    font-size: 12px;
    color: var(--text-muted);
    padding-left: 10px;
    border-left: 1px solid var(--border);
    @media (min-width: 960px) {
      display: inline;
    }
  }
}

.topnav {
  display: flex;
  gap: 4px;
  span {
    padding: 6px 12px;
    border-radius: 8px;
    font-size: 14px;
    color: var(--text-soft);
    cursor: pointer;
    transition: all 0.15s;
    &:hover {
      color: var(--brand);
      background: var(--brand-soft);
    }
    &.on {
      color: var(--brand-strong);
      font-weight: 600;
      background: var(--brand-soft);
    }
  }
}

.search {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  max-width: 520px;
  height: 40px;
  padding: 0 6px 0 16px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--surface-2);
  transition: border-color 0.15s, box-shadow 0.15s;
  &:focus-within,
  &.open {
    border-color: var(--brand);
    background: var(--surface);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--brand) 18%, transparent);
  }
  .s-icon {
    color: var(--text-muted);
    flex-shrink: 0;
  }
  input {
    flex: 1;
    min-width: 0;
    height: 100%;
    padding: 0 12px;
    border: none;
    background: transparent;
    outline: none;
    font-size: 14px;
    color: var(--text);
  }
  .s-btn {
    flex-shrink: 0;
    height: 30px;
    padding: 0 16px;
    border: none;
    border-radius: 999px;
    background: var(--brand);
    color: #fff;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    &:hover {
      background: var(--brand-strong);
    }
  }
}

.suggest {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  right: 0;
  z-index: 20;
  padding: 12px 14px 14px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: var(--shadow-m, 0 12px 40px -12px rgba(15, 23, 42, 0.25));
  .sug-block + .sug-block {
    margin-top: 12px;
  }
  .sug-head {
    display: flex;
    justify-content: space-between;
    margin-bottom: 8px;
    font-size: 12px;
    font-weight: 600;
    color: var(--text-muted);
    a {
      color: var(--brand);
      cursor: pointer;
      &:hover {
        text-decoration: underline;
      }
    }
  }
  .sug-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .sug-tag {
    padding: 5px 12px;
    border-radius: 999px;
    background: var(--surface-2);
    font-size: 13px;
    color: var(--text);
    cursor: pointer;
    &:hover {
      color: var(--brand);
      background: var(--brand-soft);
    }
    &.hot em {
      display: inline-block;
      margin-right: 4px;
      font-style: normal;
      font-weight: 700;
      color: var(--text-muted);
      &.top {
        color: #ff4d4f;
      }
    }
  }
}

.catbar {
  border-top: 1px solid var(--border);
  .cat-inner {
    display: flex;
    gap: 6px;
    height: 46px;
    align-items: center;
    overflow-x: auto;
    scrollbar-width: none;
    -ms-overflow-style: none;
    &::-webkit-scrollbar {
      display: none;
    }
  }
  .cat {
    flex-shrink: 0;
    padding: 6px 14px;
    border-radius: 999px;
    font-size: 14px;
    color: var(--text-soft);
    cursor: pointer;
    transition: all 0.15s;
    &:hover {
      color: var(--brand);
      background: var(--brand-soft);
    }
    &.active {
      color: #fff;
      background: var(--brand);
    }
  }
}

.content {
  flex: 1;
}

.footer {
  margin-top: auto;
  padding: 16px 0 20px;
  border-top: 1px solid var(--border);
  background: var(--surface);
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
  .foot-line {
    a {
      color: var(--text-soft);
      text-decoration: none;
      &:hover {
        color: var(--brand);
      }
    }
    .sep {
      margin: 0 8px;
    }
  }
  .perf-line {
    margin-top: 8px;
    opacity: 0.7;
  }
}

.fab-contact {
  position: fixed;
  right: 20px;
  bottom: 148px;
  z-index: 90;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px 10px 10px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--brand), var(--brand-strong));
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 8px 20px -6px color-mix(in srgb, var(--brand) 55%, transparent);
  cursor: pointer;

  .fab-icon {
    width: 26px;
    height: 26px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.22);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
  }

  &:hover {
    filter: brightness(1.05);
  }
}

.back-top {
  position: fixed;
  right: 24px;
  bottom: 88px;
  z-index: 90;
  width: 44px;
  height: 44px;
  border: 1px solid var(--border);
  border-radius: 50%;
  background: var(--surface);
  color: var(--text);
  box-shadow: var(--shadow-s);
  cursor: pointer;
  &:hover {
    color: var(--brand);
    border-color: var(--brand);
  }
}

@media (max-width: 768px) {
  .fab-contact .fab-text {
    display: none;
  }
  .fab-contact {
    padding: 10px;
    border-radius: 50%;
  }
}

.fade-up-enter-active,
.fade-up-leave-active {
  transition: opacity 0.2s, transform 0.2s;
}
.fade-up-enter-from,
.fade-up-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 768px) {
  .bar-right,
  .slogan {
    display: none !important;
  }
  /* 双行：上品牌+导航，下全宽搜索，避免联想面板被挤成竖条 */
  .bar-inner {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    height: auto;
    gap: 10px;
    padding-top: 10px;
    padding-bottom: 10px;
  }
  .bar-left {
    width: 100%;
    justify-content: space-between;
    gap: 8px;
  }
  .search-wrap {
    width: 100%;
  }
  .search {
    max-width: none;
    height: 42px;
  }
  .search input {
    font-size: 16px; /* iOS 避免聚焦自动放大 */
  }
  .suggest {
    max-height: min(65vh, 440px);
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
  }
  .suggest .sug-tags {
    flex-direction: column;
    flex-wrap: nowrap;
    gap: 4px;
  }
  .suggest .sug-tag {
    width: 100%;
    box-sizing: border-box;
    border-radius: 8px;
    padding: 9px 12px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .topnav span {
    padding: 6px 8px;
    font-size: 13px;
  }
  .brand .logo {
    font-size: 18px;
  }
  .back-top {
    bottom: calc(24px + env(safe-area-inset-bottom, 0px));
  }
}
</style>
