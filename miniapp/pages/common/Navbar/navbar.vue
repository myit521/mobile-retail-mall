<template>
	<view class="navBar">
		<!-- 个人中心 -->
		<view @click="myCenterFun" class="person-box" :style="{top:ht + 'px'}">
			<view class="person-icon">我</view>
			<span class="person-title">个人中心</span>
		</view>
	</view>
</template>

<script>
export default {
	computed: {
		// ht: function () {
		// 	let res = uni.getMenuButtonBoundingClientRect()
		// 	let num = 24
		// 	if(/iPhone.*/.test(uni.getSystemInfoSync().model)){
		// 		 num = res.top * 1.6
		// 	} else {
		// 		num = res.top * 2
		// 	}
		// 	return num
		// }
		ht: function () {
			let res = uni.getMenuButtonBoundingClientRect() 
			return res.top +5
		}
	},
	methods: {
		myCenterFun () {
			uni.navigateTo({
				url: '/pages/my/my'
			})
		}
	}
}
</script>

<style src="./navbar.scss" lang="scss"></style>
