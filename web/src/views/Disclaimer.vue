<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="container doc">
    <div class="crumbs">
      <span @click="router.push('/')">首页</span> / <span class="cur">免责声明</span>
    </div>

    <article class="doc-card">
      <h1>免责声明</h1>
      <p class="lead">
        请在使用本站前仔细阅读以下条款。继续访问或使用本站，即表示你已知悉并同意本声明。
      </p>

      <ol>
        <li>
          本站为影视资源<b>信息聚合与检索</b>服务，<b>不存储、不制作、不上传</b>任何影视文件，
          所有资源链接均来自第三方网盘的公开分享，本站仅提供信息展示与跳转。
        </li>
        <li>
          本站所展示的影视元数据（标题、海报、简介、评分等）来自公开的第三方数据源，
          版权归原作者及相关权利人所有，仅用于信息检索与学习交流。
        </li>
        <li>
          本站不对第三方资源的<b>合法性、完整性、时效性及安全性</b>作任何担保，
          因使用第三方资源造成的任何后果由使用者自行承担。
        </li>
        <li>
          请支持正版。资源仅供个人学习、研究使用，请于下载后 24 小时内删除，
          不得用于任何商业用途。如对相关作品感兴趣，请通过正规渠道购买、观看。
        </li>
        <li>
          如本站内容无意中侵犯了你的合法权益，请通过下方方式联系我们，
          我们将在核实后<b>及时删除</b>相关信息。
        </li>
      </ol>

      <h2>侵权联系</h2>
      <p class="muted">
        如需投诉或反馈侵权内容，请发送邮件至 <b>abuse@jyinshi.example</b>，
        并附上权属证明与具体链接，我们会尽快处理。
      </p>

      <div class="links">
        <router-link to="/about">← 返回关于爱看</router-link>
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
  ol {
    margin: 0;
    padding-left: 20px;
    li {
      font-size: 14px;
      line-height: 2;
      color: var(--text-soft);
      margin-bottom: 8px;
      b {
        color: var(--text);
      }
    }
  }
  .muted {
    font-size: 13px;
    line-height: 1.9;
    color: var(--text-muted);
    b {
      color: var(--text-soft);
    }
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
