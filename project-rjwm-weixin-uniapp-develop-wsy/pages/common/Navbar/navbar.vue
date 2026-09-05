<template>
	<view class="navBar">
		<!-- 导航栏，已移除个人中心入口 -->
	</view>
</template>

<script>
export default {
	computed: {
		ht: function () {
			let res = uni.getMenuButtonBoundingClientRect() 
			return res.top +5
		}
	}
}
</script>

<style src="./navbar.scss" lang="scss"></style>
