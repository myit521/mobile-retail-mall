package com.mall.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.mall.context.BaseContext;
import com.mall.dto.MemoDTO;
import com.mall.dto.MemoPageQueryDTO;
import com.mall.entity.Memo;
import com.mall.exception.BaseException;
import com.mall.mapper.MemoMapper;
import com.mall.result.PageResult;
import com.mall.service.AiParseService;
import com.mall.service.MemoService;
import com.mall.vo.MemoParseResultVO;
import com.mall.vo.MemoVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 备忘录服务实现类
 */
@Service
@Slf4j
public class MemoServiceImpl implements MemoService {
    
    @Autowired
    private MemoMapper memoMapper;
    
    @Autowired
    private AiParseService aiParseService;
    
    private static final String[] PRIORITY_DESC = {"普通", "重要", "紧急"};
    private static final String[] STATUS_DESC = {"待处理", "处理中", "已完成", "已取消"};
    
    @Override
    @Transactional
    public MemoVO create(MemoDTO memoDTO) {
        log.info("创建备忘录，userId={}, contentLength={}, enableAiParse={}",
                BaseContext.getCurrentId(),
                memoDTO == null || memoDTO.getContent() == null ? null : memoDTO.getContent().length(),
                memoDTO == null ? null : memoDTO.getEnableAiParse());
        
        Long userId = BaseContext.getCurrentId();
        
        Memo memo = Memo.builder()
                .userId(userId)
                .content(memoDTO.getContent())
                .priority(memoDTO.getPriority() != null ? memoDTO.getPriority() : Memo.PRIORITY_NORMAL)
                .status(Memo.STATUS_PENDING)
                .dueDate(memoDTO.getDueDate())
                .remindTime(memoDTO.getRemindTime())
                .isReminded(0)
                .tags(memoDTO.getTags())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        
        // AI解析
        if (Boolean.TRUE.equals(memoDTO.getEnableAiParse()) && aiParseService.isServiceAvailable()) {
            MemoParseResultVO parseResult = aiParseService.parseMemoContent(memoDTO.getContent());
            
            if (Boolean.TRUE.equals(parseResult.getSuccess())) {
                // 设置AI解析的标题
                if (StringUtils.hasText(parseResult.getTitle())) {
                    memo.setTitle(parseResult.getTitle());
                }
                
                // 如果DTO未指定截止时间，使用AI解析的
                if (memoDTO.getDueDate() == null && parseResult.getDueDate() != null) {
                    memo.setDueDate(parseResult.getDueDate());
                }
                
                // 如果DTO未指定优先级，使用AI解析的
                if (memoDTO.getPriority() == null && parseResult.getPriority() != null) {
                    memo.setPriority(parseResult.getPriority());
                }
                
                // 如果DTO未指定标签，使用AI解析的
                if (!StringUtils.hasText(memoDTO.getTags()) && parseResult.getTags() != null) {
                    memo.setTags(String.join(",", parseResult.getTags()));
                }
                
                // 保存解析的原始JSON
                memo.setParsedContent(parseResult.getRawJson());
            }
        }
        
        // 如果没有AI解析的标题，截取内容作为标题
        if (!StringUtils.hasText(memo.getTitle())) {
            String content = memoDTO.getContent();
            memo.setTitle(content.length() > 20 ? content.substring(0, 20) + "..." : content);
        }
        
        memoMapper.insert(memo);
        log.info("备忘录创建成功，ID：{}", memo.getId());
        
        return convertToVO(memo);
    }
    
    @Override
    @Transactional
    public void update(MemoDTO memoDTO) {
        log.info("更新备忘录：{}", memoDTO.getId());
        
        Long userId = BaseContext.getCurrentId();
        Memo existingMemo = memoMapper.getByIdAndUserId(memoDTO.getId(), userId);
        
        if (existingMemo == null) {
            throw new BaseException("备忘录不存在或无权限");
        }
        
        Memo memo = Memo.builder()
                .id(memoDTO.getId())
                .content(memoDTO.getContent())
                .priority(memoDTO.getPriority())
                .dueDate(memoDTO.getDueDate())
                .remindTime(memoDTO.getRemindTime())
                .tags(memoDTO.getTags())
                .build();
        
        // 如果内容有变化，重新AI解析
        if (StringUtils.hasText(memoDTO.getContent()) 
                && !memoDTO.getContent().equals(existingMemo.getContent())
                && Boolean.TRUE.equals(memoDTO.getEnableAiParse()) 
                && aiParseService.isServiceAvailable()) {
            
            MemoParseResultVO parseResult = aiParseService.parseMemoContent(memoDTO.getContent());
            if (Boolean.TRUE.equals(parseResult.getSuccess())) {
                if (StringUtils.hasText(parseResult.getTitle())) {
                    memo.setTitle(parseResult.getTitle());
                }
                memo.setParsedContent(parseResult.getRawJson());
            }
        }
        
        memoMapper.update(memo);
        log.info("备忘录更新成功");
    }
    
    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        log.info("更新备忘录状态，ID：{}，新状态：{}", id, status);
        
        Long userId = BaseContext.getCurrentId();
        Memo existingMemo = memoMapper.getByIdAndUserId(id, userId);
        
        if (existingMemo == null) {
            throw new BaseException("备忘录不存在或无权限");
        }
        
        Memo memo = Memo.builder()
                .id(id)
                .status(status)
                .build();
        
        memoMapper.update(memo);
        log.info("备忘录状态更新成功");
    }
    
    @Override
    @Transactional
    public void delete(Long id) {
        log.info("删除备忘录，ID：{}", id);
        
        Long userId = BaseContext.getCurrentId();
        memoMapper.deleteByIdAndUserId(id, userId);
        
        log.info("备忘录删除成功");
    }
    
    @Override
    public MemoVO getById(Long id) {
        Long userId = BaseContext.getCurrentId();
        Memo memo = memoMapper.getByIdAndUserId(id, userId);
        
        if (memo == null) {
            throw new BaseException("备忘录不存在或无权限");
        }
        
        return convertToVO(memo);
    }
    
    @Override
    public PageResult pageQuery(MemoPageQueryDTO queryDTO) {
        log.info("分页查询备忘录，userId={}, page={}, pageSize={}, status={}",
                BaseContext.getCurrentId(),
                queryDTO == null ? null : queryDTO.getPage(),
                queryDTO == null ? null : queryDTO.getPageSize(),
                queryDTO == null ? null : queryDTO.getStatus());
        
        Long userId = BaseContext.getCurrentId();
        
        // 设置分页参数默认值
        Integer pageNum = queryDTO.getPage() != null ? queryDTO.getPage() : 1;
        Integer pageSize = queryDTO.getPageSize() != null ? queryDTO.getPageSize() : 10;
        
        PageHelper.startPage(pageNum, pageSize);
        Page<Memo> page = (Page<Memo>) memoMapper.pageQuery(queryDTO, userId);
        
        List<MemoVO> voList = page.getResult().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        
        return new PageResult(page.getTotal(), voList);
    }
    
    @Override
    public MemoParseResultVO parseOnly(String content) {
        log.info("预览AI解析结果，contentLength={}", content == null ? null : content.length());
        
        if (!aiParseService.isServiceAvailable()) {
            return MemoParseResultVO.builder()
                    .success(false)
                    .errorMessage("AI服务不可用")
                    .build();
        }
        
        return aiParseService.parseMemoContent(content);
    }
    
    @Override
    public MemoStatsVO getStats() {
        Long userId = BaseContext.getCurrentId();
        
        MemoStatsVO stats = new MemoStatsVO();
        stats.pendingCount = memoMapper.countByUserIdAndStatus(userId, Memo.STATUS_PENDING);
        stats.processingCount = memoMapper.countByUserIdAndStatus(userId, Memo.STATUS_PROCESSING);
        stats.completedCount = memoMapper.countByUserIdAndStatus(userId, Memo.STATUS_COMPLETED);
        stats.cancelledCount = memoMapper.countByUserIdAndStatus(userId, Memo.STATUS_CANCELLED);
        stats.totalCount = memoMapper.countByUserIdAndStatus(userId, null);
        
        return stats;
    }
    
    /**
     * 实体转VO
     */
    private MemoVO convertToVO(Memo memo) {
        MemoVO vo = new MemoVO();
        BeanUtils.copyProperties(memo, vo);
        
        // 设置优先级描述
        if (memo.getPriority() != null && memo.getPriority() >= 0 && memo.getPriority() < PRIORITY_DESC.length) {
            vo.setPriorityDesc(PRIORITY_DESC[memo.getPriority()]);
        }
        
        // 设置状态描述
        if (memo.getStatus() != null && memo.getStatus() >= 0 && memo.getStatus() < STATUS_DESC.length) {
            vo.setStatusDesc(STATUS_DESC[memo.getStatus()]);
        }
        
        // 判断是否过期
        if (memo.getDueDate() != null && memo.getStatus() != null 
                && memo.getStatus() < Memo.STATUS_COMPLETED) {
            vo.setIsOverdue(LocalDateTime.now().isAfter(memo.getDueDate()));
        } else {
            vo.setIsOverdue(false);
        }
        
        return vo;
    }
}
