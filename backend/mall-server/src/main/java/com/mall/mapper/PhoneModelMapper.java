package com.mall.mapper;

import com.mall.annotation.AutoFill;
import com.mall.dto.PhoneModelPageQueryDTO;
import com.mall.entity.PhoneModel;
import com.mall.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PhoneModelMapper {

    /**
     * 新增手机型号
     * @param phoneModel
     */
    @AutoFill(OperationType.INSERT)
    void insert(PhoneModel phoneModel);

    /**
     * 分页查询手机型号
     * @param phoneModelPageQueryDTO
     * @return
     */
    List<PhoneModel> selectPage(PhoneModelPageQueryDTO phoneModelPageQueryDTO);

    /**
     * 根据ID查询手机型号
     * @param id
     * @return
     */
    @Select("select * from phone_model where id = #{id}")
    PhoneModel selectById(Long id);

    /**
     * 更新手机型号
     * @param phoneModel
     */
    @AutoFill(OperationType.UPDATE)
    void update(PhoneModel phoneModel);

    /**
     * 批量删除手机型号
     * @param ids
     */
    void deleteByIds(List<Long> ids);

    /**
     * 查询所有启用的手机型号
     * @return
     */
    @Select("select * from phone_model where status = 1 order by sort asc")
    List<PhoneModel> selectAllEnabled();

    /**
     * 根据品牌查询手机型号
     * @param brand
     * @return
     */
    @Select("select * from phone_model where brand = #{brand} and status = 1 order by sort asc")
    List<PhoneModel> selectByBrand(String brand);

    /**
     * 根据品牌和型号查询手机型号
     * @param brand 品牌
     * @param modelName 型号名称
     * @return 手机型号实体
     */
    @Select("select * from phone_model where brand = #{brand} and model_name = #{modelName}")
    PhoneModel selectByBrandAndModelName(String brand, String modelName);

    /**
     * 根据ID列表查询手机型号
     * @param ids
     * @return
     */
    List<PhoneModel> selectByIds(List<Long> ids);

}
