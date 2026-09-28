-- Caller must set validated @PERF_RUN_ID and @PERF_USERS first.
SET @PERF_CATEGORY_NAME = CONCAT('PERF-', @PERF_RUN_ID);
SET @PERF_PRODUCT_NAME = CONCAT('PERF-PRODUCT-', @PERF_RUN_ID);
CREATE TEMPORARY TABLE IF NOT EXISTS perf_worker(worker_no INT PRIMARY KEY);
DELETE FROM perf_worker;
INSERT INTO perf_worker(worker_no)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @PERF_USERS)
SELECT n FROM seq;

INSERT INTO category(name, sort, status, create_time, update_time, create_user, update_user)
SELECT @PERF_CATEGORY_NAME, 999, 1, NOW(), NOW(), 1, 1
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = @PERF_CATEGORY_NAME);
SET @PERF_CATEGORY_ID = (SELECT id FROM category WHERE name = @PERF_CATEGORY_NAME LIMIT 1);

INSERT INTO product(name, brand, model, category_id, price, stock, description, status,
                    create_time, update_time, create_user, update_user, alert_threshold)
SELECT @PERF_PRODUCT_NAME, 'PERF', @PERF_RUN_ID, @PERF_CATEGORY_ID, 1.00, 1000000,
       CONCAT('isolated performance fixture ', @PERF_RUN_ID), 1, NOW(), NOW(), 1, 1, 10
WHERE NOT EXISTS (SELECT 1 FROM product WHERE name = @PERF_PRODUCT_NAME);
UPDATE product SET category_id=@PERF_CATEGORY_ID, price=1.00, stock=1000000, status=1, update_time=NOW()
WHERE name=@PERF_PRODUCT_NAME AND model=@PERF_RUN_ID;
SET @PERF_PRODUCT_ID = (SELECT id FROM product WHERE name=@PERF_PRODUCT_NAME AND model=@PERF_RUN_ID LIMIT 1);

INSERT INTO user(openid, name, create_time)
SELECT CONCAT('perf-', @PERF_RUN_ID, '-', worker_no), CONCAT('PERF-', @PERF_RUN_ID, '-', worker_no), NOW()
FROM perf_worker w
WHERE NOT EXISTS (SELECT 1 FROM user u WHERE u.openid=CONCAT('perf-', @PERF_RUN_ID, '-', w.worker_no));

-- Reset only this run's disposable workload rows so an explicit RUN_ID rerun is stable.
DELETE od FROM order_detail od JOIN orders o ON o.id=od.order_id JOIN user u ON u.id=o.user_id
WHERE o.remark LIKE CONCAT('PERF:', @PERF_RUN_ID, ':%') AND u.openid LIKE CONCAT('perf-', @PERF_RUN_ID, '-%');
DELETE sc FROM shopping_cart sc JOIN user u ON u.id=sc.user_id
WHERE u.openid LIKE CONCAT('perf-', @PERF_RUN_ID, '-%');
DELETE o FROM orders o JOIN user u ON u.id=o.user_id
WHERE o.remark LIKE CONCAT('PERF:', @PERF_RUN_ID, ':%') AND u.openid LIKE CONCAT('perf-', @PERF_RUN_ID, '-%');

SELECT 'VALIDATION',
       (SELECT COUNT(*) FROM category WHERE name=@PERF_CATEGORY_NAME),
       (SELECT COUNT(*) FROM product WHERE name=@PERF_PRODUCT_NAME AND model=@PERF_RUN_ID),
       (SELECT COUNT(*) FROM user WHERE openid LIKE CONCAT('perf-', @PERF_RUN_ID, '-%'));
SELECT 'WORKER', w.worker_no, u.id, @PERF_CATEGORY_ID, @PERF_PRODUCT_ID
FROM perf_worker w JOIN user u ON u.openid=CONCAT('perf-', @PERF_RUN_ID, '-', w.worker_no)
ORDER BY w.worker_no;
