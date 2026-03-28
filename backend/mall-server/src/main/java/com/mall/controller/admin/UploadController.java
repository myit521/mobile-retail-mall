package com.mall.controller.admin;

import com.mall.constant.MessageConstant;
import com.mall.exception.BaseException;
import com.mall.result.Result;
import com.mall.utils.AliOssUtil;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/admin/common")
@Api(tags = "通用接口")
@Slf4j
public class UploadController {

    @Autowired
    private AliOssUtil aliOssUtil;
    /**
     * 文件上传
     * @param file
     * @return
     */
    @PostMapping("/upload")
    public Result upload(MultipartFile file){
        //将文件上传到阿里云
        try {
            log.info("文件上传开始，上传的文件：{}",file.getOriginalFilename());
            //获取原始文件名
            String originalFilename = file.getOriginalFilename();
            //获取当前系统日期的字符串,格式为 yyyy/MM
            String dir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
            //生成一个新的不重复的文件名
            String newFileName = UUID.randomUUID() + originalFilename.substring(originalFilename.lastIndexOf("."));
            String objectName = dir + "/" + newFileName;
            //上传文件到阿里云
            String url = aliOssUtil.upload(file.getBytes(), objectName);
            log.info("文件上传成功，上传的文件名：{}",objectName);
            return Result.success(url);
        } catch (IOException e) {
            throw new BaseException(MessageConstant.UPLOAD_FAILED);
        }
    }
}
