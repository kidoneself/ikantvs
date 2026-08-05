<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="container doc">
    <div class="crumbs">
      <span @click="router.push('/')">首页</span> / <span class="cur">关于爱看</span>
    </div>

    <article class="doc-card">
      <h1>关于爱看</h1>
      <p class="lead">
        爱看是一个「网盘影视资源信息流」平台：把分散在各处的影视资源信息聚合起来，
        让你先看到清晰的影片信息，再决定要不要保存到自己的网盘。
      </p>

      <h2>我们做什么</h2>
      <ul>
        <li><b>信息流浏览</b>：按最新 / 最热 / 高分浏览影视信息，封面、评分、简介一目了然。</li>
        <li><b>聚合搜索</b>：一个入口搜片名、演员、类型，结果以信息流形式呈现。</li>
        <li><b>资源直达</b>：在详情页查看各网盘资源，复制分享链接即可保存到你自己的网盘。</li>
      </ul>

      <h2>我们不做什么</h2>
      <ul>
        <li>不存储、不托管任何影视文件，资源均来自第三方网盘公开分享链接。</li>
        <li>不提供盗版下载，仅做信息聚合与跳转。</li>
      </ul>

      <div class="links">
        <router-link to="/disclaimer">查看免责声明 →</router-link>
      </div>
    </article>
  </div>
</template>

<style scoped lang="scss">
.doc {
  padding: 22px 0 8px;
}
.crumbs {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0 auto 14px;
  max-width: 760px;
  span {
    cursor: pointer;
    &:hover {
      color: var(--brand);
    }
    &.cur {
      color: var(--text-soft);
      cursor: default;
    }
  }
}
.doc-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 28px 30px;
  max-width: 760px;
  margin: 0 auto;

  h1 {
    margin: 0 0 12px;
    font-size: 24px;
  }
  h2 {
    margin: 26px 0 10px;
    font-size: 17px;
  }
  .lead {
    font-size: 15px;
    line-height: 1.9;
    color: var(--text-soft);
  }
  ul {
    margin: 0;
    padding-left: 20px;
    li {
      font-size: 14px;
      line-height: 2;
      color: var(--text-soft);
      b {
        color: var(--text);
      }
    }
  }
  .muted {
    margin-top: 24px;
    font-size: 13px;
    color: var(--text-muted);
  }
  .links {
    margin-top: 18px;
    a {
      font-size: 14px;
      color: var(--brand);
      font-weight: 600;
    }
  }
}

@media (max-width: 680px) {
  .doc-card {
    padding: 20px 18px;
  }
}
</style>
