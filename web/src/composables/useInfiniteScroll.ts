import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

/**
 * 无限滚动：把返回的 sentinel 绑到列表底部的哨兵元素上，
 * 当它进入视口（提前 400px 预加载）时触发 onLoadMore。
 *
 * onLoadMore 内部应自行做 loadingMore / hasMore 守卫，避免重复请求。
 * 哨兵元素建议用 v-if="hasMore" 控制：没有更多时自动停止观察。
 */
export function useInfiniteScroll(onLoadMore: () => void) {
  const sentinel = ref<HTMLElement | null>(null)
  let observer: IntersectionObserver | null = null

  onMounted(() => {
    observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) onLoadMore()
      },
      { rootMargin: '400px 0px' },
    )
    watch(
      sentinel,
      (el, _old, onCleanup) => {
        if (el && observer) {
          observer.observe(el)
          onCleanup(() => observer?.unobserve(el))
        }
      },
      { immediate: true },
    )
  })

  onBeforeUnmount(() => observer?.disconnect())

  return { sentinel }
}
