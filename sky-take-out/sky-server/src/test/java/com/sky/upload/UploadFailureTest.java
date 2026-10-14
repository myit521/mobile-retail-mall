package com.sky.upload;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.sky.controller.admin.UploadController;
import com.sky.handler.GlobalExceptionHandler;
import com.sky.utils.AliOssUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UploadFailureTest {
    @Test
    void rejectedObjectUploadReturnsFailureInsteadOfAnImageUrl() throws Exception {
        assertUploadFails(new OSSException("AccessDenied"));
    }

    @Test
    void disconnectedObjectUploadReturnsFailureInsteadOfAnImageUrl() throws Exception {
        assertUploadFails(new ClientException("connection failed"));
    }

    private void assertUploadFails(RuntimeException failure) throws Exception {
        OSS oss = mock(OSS.class);
        when(oss.putObject(anyString(), anyString(), any(InputStream.class))).thenThrow(failure);
        try (MockedConstruction<OSSClientBuilder> builders = builderReturning(oss)) {
            controller().perform(multipart("/admin/common/upload").file(image()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("code").value("BUSINESS_ERROR"))
                    .andExpect(jsonPath("message").value("文件上传失败"));
            verify(oss).shutdown();
        }
    }

    @Test
    void successfulUploadReturnsItsUrlAndClosesTheClient() throws Exception {
        OSS oss = mock(OSS.class);
        try (MockedConstruction<OSSClientBuilder> builders = builderReturning(oss)) {
            controller().perform(multipart("/admin/common/upload").file(image()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("code").value(1))
                    .andExpect(jsonPath("data").value(org.hamcrest.Matchers.startsWith("https://test-bucket.oss.example.test/")));
            verify(oss).putObject(eq("test-bucket"), anyString(), any(InputStream.class));
            verify(oss).shutdown();
        }
    }

    private MockedConstruction<OSSClientBuilder> builderReturning(OSS oss) {
        return mockConstruction(OSSClientBuilder.class, (builder, context) ->
                when(builder.build(anyString(), anyString(), anyString())).thenReturn(oss));
    }

    private MockMvc controller() {
        UploadController controller = new UploadController();
        ReflectionTestUtils.setField(controller, "aliOssUtil",
                new AliOssUtil("oss.example.test", "test-access", "test-secret", "test-bucket"));
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "image.png", "image/png", new byte[]{1, 2, 3});
    }
}
