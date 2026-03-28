package com.mall.mapper;

import com.github.pagehelper.Page;
import com.mall.annotation.AutoFill;
import com.mall.enumeration.OperationType;
import com.mall.dto.CategoryPageQueryDTO;
import com.mall.entity.Category;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface CategoryMapper {

    /**
     * 插入数据
     * @param category
     */
    @Insert("insert into category(name, sort, status, create_time, update_time, create_user, update_user)" +
            " VALUES" +
            " (#{name}, #{sort}, #{status}, #{createTime}, #{updateTime}, #{createUser}, #{updateUser})")
    @AutoFill(OperationType.INSERT)
    void insert(Category category);

    /**
     * 分页查询
     * @param categoryPageQueryDTO
     * @return
     */
    Page<Category> pageQuery(CategoryPageQueryDTO categoryPageQueryDTO);

    /**
     * 根据id删除分类
     * @param id
     */
    @Delete("delete from category where id = #{id}")
    void deleteById(Long id);

    /**
     * 根据id修改分类
     * @param category
     */
    @AutoFill(OperationType.UPDATE)
    void update(Category category);

    List<Category> list();
}
