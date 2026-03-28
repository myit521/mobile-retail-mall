<template>
  <view class="customer-box">
    <uni-nav-bar
      @clickLeft="goBack"
      left-icon="back"
      leftIcon="arrowleft"
      title="备注信息"
      statusBar="true"
      fixed="true"
      color="#ffffff"
      backgroundColor="#333333"
    ></uni-nav-bar>
    <view class="wrap">
      <view class="box">
        <view class="contion">
          <view class="order_list">
            <view class="uni-textarea">
              <textarea
                class="beizhu_text"
                :class="{ beizhu_text_ios: platform === 'ios' }"
                placeholder-class="textarea-placeholder"
                v-model="remark"
                placeholder="可填写颜色偏好、到店说明或其他补充信息"
                >{{ getVal }}</textarea
              >
              <text class="numText"
                ><text :class="numVal === 0 ? 'tip' : ''">{{ numVal }}</text
                >/50</text
              >
            </view>
          </view>
        </view>
      </view>
      <view class="btnBox">
        <button
          class="add_btn"
          type="primary"
          plain="true"
          @click="handleSaveRemark"
        >
          完成
        </button>
      </view>
    </view>
  </view>
</template>

<script>
import { mapState, mapMutations } from "vuex";
export default {
  data() {
    return {
      remark: "",
      numVal: 0,
    };
  },
  computed: {
    getVal: function () {
      let leng = this.validateTextLength(this.remark);
      if (leng <= 50) {
        this.numVal = Math.floor(leng);
      } else {
        this.remark = this.remark.substring(0, 50);
      }
    },
  },
  onLoad() {
    if (!this.remarkData) {
      return;
    }
    this.remark = this.remarkData;
    this.numVal = this.remark.length;
  },
  methods: {
    ...mapMutations(["setRemark"]),
    goBack() {
      uni.redirectTo({
        url: "/pages/order/index",
      });
    },
    // 保存返回订单页
    handleSaveRemark() {
      uni.redirectTo({
        url: "/pages/order/index",
      });
      this.setRemark(this.remark);
    },
    validateTextLength(value) {
      // 中文、中文标点、全角字符按1长度，英文、英文符号、数字按0.5长度计算
      let cnReg = /([\u4e00-\u9fa5]|[\u3000-\u303F]|[\uFF00-\uFF60])/g;
      let mat = value.match(cnReg);
      let length;
      if (mat) {
        length = mat.length + (value.length - mat.length) * 0.5;
        return length;
      } else {
        return value.length * 0.5;
      }
    },
  },
};
</script>

<style src="./../common/Navbar/navbar.scss" lang="scss" scoped></style>
<style src="./../order/style.scss" lang="scss"></style>
