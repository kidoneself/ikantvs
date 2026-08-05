<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import { useSiteStore } from '@/store/site'

const siteStore = useSiteStore()
void siteStore.init()

const route = useRoute()
const bare = computed(() => !!route.meta.bare)
</script>

<template>
  <router-view v-if="bare" />
  <MainLayout v-else>
    <router-view v-slot="{ Component, route: r }">
      <transition name="page" mode="out-in">
        <keep-alive :include="['Home', 'Ranking', 'Search']">
          <component
            :is="Component"
            :key="r.meta.keepAlive ? (r.name as string) : r.fullPath"
          />
        </keep-alive>
      </transition>
    </router-view>
  </MainLayout>
</template>
