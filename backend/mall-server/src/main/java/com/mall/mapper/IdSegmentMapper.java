package com.mall.mapper;

import com.mall.entity.IdSegment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IdSegmentMapper {

    IdSegment getByBizTag(@Param("bizTag") String bizTag);

    int insert(IdSegment segment);

    int updateMaxId(@Param("bizTag") String bizTag);
}
