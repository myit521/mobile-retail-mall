alter table product
    add constraint chk_product_stock_nonnegative check (stock >= 0);

alter table stock_check_record
    add constraint chk_check_actual_stock_nonnegative check (actual_stock >= 0);
