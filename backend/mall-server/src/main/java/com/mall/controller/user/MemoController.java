package com.mall.controller.user;

import com.mall.dto.MemoDTO;
import com.mall.dto.MemoPageQueryDTO;
import com.mall.result.PageResult;
import com.mall.result.Result;
import com.mall.service.MemoService;
import com.mall.vo.MemoParseResultVO;
import com.mall.vo.MemoVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端 - 备忘录接口
 */
@RestController("userMemoController")
@RequestMapping("/user/memo")
@Api(tags = "用户端 - 备忘录接口")
@Slf4j
public class MemoController {
    
    @Autowired
    private MemoService memoService;
    
    /**
     * 创建备忘录
     * @param memoDTO 备忘录DTO
     * @return 创建的备忘录
     */
    @PostMapping
    @ApiOperation("创建备忘录")
    public Result<MemoVO> create(@RequestBody MemoDTO memoDTO) {
        log.info("创建备忘录，id={}, contentLength={}, enableAiParse={}",
                memoDTO == null ? null : memoDTO.getId(),
                memoDTO == null || memoDTO.getContent() == null ? null : memoDTO.getContent().length(),
                memoDTO == null ? null : memoDTO.getEnableAiParse());
        MemoVO memoVO = memoService.create(memoDTO);
        return Result.success(memoVO);
    }
    
    /**
     * 更新备忘录
     * @param memoDTO 备忘录DTO
     * @return 操作结果
     */
    @PutMapping
    @ApiOperation("更新备忘录")
    public Result<String> update(@RequestBody MemoDTO memoDTO) {
        log.info("更新备忘录：{}", memoDTO.getId());
        memoService.update(memoDTO);
        return Result.success();
    }
    
    /**
     * 更新备忘录状态
     * @param id 备忘录ID
     * @param status 新状态
     * @return 操作结果
     */
    @PutMapping("/{id}/status")
    @ApiOperation("更新备忘录状态")
    public Result<String> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        log.info("更新备忘录状态，ID：{}，状态：{}", id, status);
        memoService.updateStatus(id, status);
        return Result.success();
    }
    
    /**
     * 删除备忘录
     * @param id 备忘录ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @ApiOperation("删除备忘录")
    public Result<String> delete(@PathVariable Long id) {
        log.info("删除备忘录，ID：{}", id);
        memoService.delete(id);
        return Result.success();
    }
    
    /**
     * 查询备忘录详情
     * @param id 备忘录ID
     * @return 备忘录详情
     */
    @GetMapping("/{id}")
    @ApiOperation("查询备忘录详情")
    public Result<MemoVO> getById(@PathVariable Long id) {
        log.info("查询备忘录详情，ID：{}", id);
        MemoVO memoVO = memoService.getById(id);
        return Result.success(memoVO);
    }
    
    /**
     * 分页查询备忘录
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @ApiOperation("分页查询备忘录")
    public Result<PageResult> pageQuery(MemoPageQueryDTO queryDTO) {
        log.info("分页查询备忘录，page={}, pageSize={}, status={}",
                queryDTO == null ? null : queryDTO.getPage(),
                queryDTO == null ? null : queryDTO.getPageSize(),
                queryDTO == null ? null : queryDTO.getStatus());
        PageResult pageResult = memoService.pageQuery(queryDTO);
        return Result.success(pageResult);
    }
    
    /**
     * AI解析预览（仅解析，不保存）
     * @param content 文本内容
     * @return 解析结果
     */
    @PostMapping("/parse")
    @ApiOperation("AI解析预览")
    public Result<MemoParseResultVO> parsePreview(@RequestBody String content) {
        log.info("AI解析预览，contentLength={}", content == null ? null : content.length());
        MemoParseResultVO result = memoService.parseOnly(content);
        return Result.success(result);
    }
    
    /**
     * 获取备忘录统计
     * @return 统计数据
     */
    @GetMapping("/stats")
    @ApiOperation("获取备忘录统计")
    public Result<MemoService.MemoStatsVO> getStats() {
        log.info("获取备忘录统计");
        MemoService.MemoStatsVO stats = memoService.getStats();
        return Result.success(stats);
    }
    
    /**
     * 完成备忘录（快捷操作）
     * @param id 备忘录ID
     * @return 操作结果
     */
    @PutMapping("/{id}/complete")
    @ApiOperation("完成备忘录")
    public Result<String> complete(@PathVariable Long id) {
        log.info("完成备忘录，ID：{}", id);
        memoService.updateStatus(id, 2); // 2-已完成
        return Result.success();
    }
    
    /**
     * 取消备忘录（快捷操作）
     * @param id 备忘录ID
     * @return 操作结果
     */
    @PutMapping("/{id}/cancel")
    @ApiOperation("取消备忘录")
    public Result<String> cancel(@PathVariable Long id) {
        log.info("取消备忘录，ID：{}", id);
        memoService.updateStatus(id, 3); // 3-已取消
        return Result.success();
    }
}
