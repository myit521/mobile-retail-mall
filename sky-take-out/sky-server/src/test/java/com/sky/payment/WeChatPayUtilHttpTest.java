package com.sky.payment;

import com.sky.utils.WeChatPayHttpException;
import com.sky.utils.WeChatPayUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeChatPayUtilHttpTest {
    @Test
    void nonSuccessHttpStatusCannotBeUsedAsRefundSuccess() {
        assertThatThrownBy(() -> WeChatPayUtil.requireSuccess(400, "{\"code\":\"INVALID_REQUEST\"}"))
                .isInstanceOf(WeChatPayHttpException.class);
    }
}
