<!--备注信息弹层-->
<template>
  <view class="box order_list">
    <view class="uniInfo">
      <view @click="goRemark">
        <uni-list>
          <uni-list-item showArrow title="备注" class="uniListItem">
            <template v-slot:footer>
              <text class="temarkText">{{
                remark ? remark : "可填写商品说明、到店说明或其他补充信息"
              }}</text>
            </template>
          </uni-list-item>
        </uni-list>
      </view>
      <view class="invoiceBox">
        <uni-list>
          <uni-list-item title="发票" class="uniListItem">
            <template v-slot:footer>
              <text>当前版本暂不支持</text>
            </template>
          </uni-list-item>
        </uni-list>
      </view>
    </view>
  </view>
</template>
<script>
export default {
  props: {
    remark: {
      type: String,
      default: "",
    },
  },
  methods: {
    goRemark() {
      this.$emit("goRemark");
    },
  },
};
</script>
<style src="./../style.scss" lang="scss" scoped></style>
