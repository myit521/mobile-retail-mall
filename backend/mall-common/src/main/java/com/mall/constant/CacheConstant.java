package com.mall.constant;

/**
 * 缓存常量
 */
public class CacheConstant {

    // 缓存名称
    public static final String PRODUCT_CACHE = "product";
    public static final String PHONE_MODEL_CACHE = "phoneModel";
    public static final String PRODUCT_LOCATION_CACHE = "productLocation";
    public static final String CATEGORY_CACHE = "category";
    public static final String SHOP_STATUS_CACHE = "shopStatus";

    // 缓存 Key 前缀
    public static final String PRODUCT_BY_CATEGORY = "'category:' + #categoryId";
    public static final String PRODUCT_BY_PHONE_MODEL = "'phoneModel:' + #phoneModelId";
    public static final String PHONE_MODEL_ALL = "'all'";
    public static final String PHONE_MODEL_BY_BRAND = "'brand:' + #brand";
    public static final String LOCATION_BY_PRODUCT = "'product:' + #productId";
    public static final String CATEGORY_BY_TYPE = "'type:' + #type";
}
