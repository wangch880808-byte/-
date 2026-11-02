<template>
  <div class="pagination-container">
    <el-pagination :current-page="page" :page-size="limit" :total="total" :page-sizes="pageSizes"
      layout="total, sizes, prev, pager, next" background
      @update:current-page="changePage" @update:page-size="changeSize" />
  </div>
</template>
<script setup>
defineProps({ total: { type: Number, default: 0 }, page: { type: Number, default: 1 },
  limit: { type: Number, default: 10 }, pageSizes: { type: Array, default: () => [10, 12, 20, 50, 100] } })
const emit = defineEmits(['update:page', 'update:limit', 'pagination'])
function changePage(page) { emit('update:page', page); emit('pagination') }
function changeSize(limit) { emit('update:limit', limit); emit('update:page', 1); emit('pagination') }
</script>
