package com.mall.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.mall.constant.MessageConstant;
import com.mall.constant.StatusConstant;
import com.mall.context.BaseContext;
import com.mall.dto.CategoryDTO;
import com.mall.dto.CategoryPageQueryDTO;
import com.mall.entity.Category;
import com.mall.exception.DeletionNotAllowedException;
import com.mall.mapper.CategoryMapper;
import com.mall.mapper.ProductMapper;
import com.mall.result.PageResult;
import com.mall.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 分类业务层
 */
@Service
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private ProductMapper productMapper;

    /**
     * 新增分类
     * @param categoryDTO
     */
    public void save(CategoryDTO categoryDTO) {
        Category category = new Category();
        //属性拷贝
        BeanUtils.copyProperties(categoryDTO, category);
        //分类状态默认为禁用状态0
        category.setStatus(StatusConstant.DISABLE);

        categoryMapper.insert(category);
    }

    /**
     * 分页查询
     * @param categoryPageQueryDTO
     * @return
     */
    public PageResult pageQuery(CategoryPageQueryDTO categoryPageQueryDTO) {
        //设置默认值，防止前端传递空值导致类型转换失败
        Integer pageNum = categoryPageQueryDTO.getPage() == null ? 1 : categoryPageQueryDTO.getPage();
        Integer pageSize = categoryPageQueryDTO.getPageSize() == null ? 10 : categoryPageQueryDTO.getPageSize();
            
        PageHelper.startPage(pageNum, pageSize);
        //下一条 sql 进行分页，自动加入 limit 关键字分页
        Page<Category> page = categoryMapper.pageQuery(categoryPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 根据id删除分类
     * @param id
     */
    public void deleteById(Long id) {
        //查询当前分类是否关联了商品，如果关联了就抛出业务异常
        Integer count = productMapper.countByCategoryId(id);
        if(count > 0){
            //当前分类下有商品，不能删除
            throw new DeletionNotAllowedException(MessageConstant.CATEGORY_BE_RELATED_BY_DISH);
        }

        // TODO: 电子产品模式 - 已移除套餐关联校验

        //删除分类数据
        categoryMapper.deleteById(id);
    }

    /**
     * 修改分类
     * @param categoryDTO
     */
    public void update(CategoryDTO categoryDTO) {
        Category category = new Category();
        BeanUtils.copyProperties(categoryDTO,category);


        categoryMapper.update(category);
    }

    /**
     * 启用、禁用分类
     * @param status
     * @param id
     */
    public void startOrStop(Integer status, Long id) {
        Category category = Category.builder()
                .id(id)
                .status(status)
                .updateTime(LocalDateTime.now())
                .updateUser(BaseContext.getCurrentId())
                .build();
        categoryMapper.update(category);
    }

    /**
     * 查询分类列表
     * @return
     */
    public List<Category> list() {
        return categoryMapper.list();
    }
}
