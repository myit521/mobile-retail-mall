-- Duplicate historical records must be reviewed before this constraint is applied.
ALTER TABLE stock_check_record ADD CONSTRAINT uk_stock_check_plan_product UNIQUE (plan_id, product_id);
