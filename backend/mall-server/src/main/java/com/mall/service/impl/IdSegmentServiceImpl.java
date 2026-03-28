package com.mall.service.impl;

import com.mall.entity.IdSegment;
import com.mall.mapper.IdSegmentMapper;
import com.mall.service.IdSegmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class IdSegmentServiceImpl implements IdSegmentService {

    private static final int DEFAULT_STEP = 1000;

    @Autowired
    private IdSegmentMapper idSegmentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SegmentRange nextSegment(String bizTag) {
        IdSegment current = idSegmentMapper.getByBizTag(bizTag);
        if (current == null) {
            IdSegment init = IdSegment.builder()
                    .bizTag(bizTag)
                    .maxId(0L)
                    .step(DEFAULT_STEP)
                    .build();
            idSegmentMapper.insert(init);
        }

        int updated = idSegmentMapper.updateMaxId(bizTag);
        if (updated <= 0) {
            throw new IllegalStateException("号段更新失败: " + bizTag);
        }

        IdSegment latest = idSegmentMapper.getByBizTag(bizTag);
        if (latest == null) {
            throw new IllegalStateException("号段获取失败: " + bizTag);
        }

        long end = latest.getMaxId();
        long start = end - latest.getStep() + 1;
        log.info("号段分配完成，bizTag={}, start={}, end={}, step={}", bizTag, start, end, latest.getStep());
        return new SegmentRange(start, end);
    }
}
