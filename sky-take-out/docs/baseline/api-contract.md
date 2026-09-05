# API contract baseline

This is a source-derived inventory of controller mappings at commit `552be2ce32a0d35dc638bc691ea789fdecabe8b5`. `token` is the configured admin JWT header and `authentication` is the configured user JWT header. “None” means the route is excluded from the applicable JWT interceptor; notification routes instead require the listed WeChat signature headers. The notification controller also reads the optional `Wechatpay-Signature-Type` header for logging. `Result` without a generic parameter is the controller's declared raw response type.

## Admin controllers

| HTTP method | Path | Auth header | Request type | Success response type |
| --- | --- | --- | --- | --- |
| POST | `/admin/category` | `token` | JSON `CategoryDTO` | `Result<String>` |
| GET | `/admin/category/page` | `token` | query `CategoryPageQueryDTO` | `Result<PageResult>` |
| DELETE | `/admin/category` | `token` | query `id: Long` | `Result<String>` |
| PUT | `/admin/category` | `token` | JSON `CategoryDTO` | `Result<String>` |
| POST | `/admin/category/status/{status}` | `token` | path `status: Integer`; query `id: Long` | `Result<String>` |
| GET | `/admin/category/list` | `token` | none | `Result<List<Category>>` |
| POST | `/admin/employee/login` | none | JSON `EmployeeLoginDTO` | `Result<EmployeeLoginVO>` |
| POST | `/admin/employee/logout` | `token` | servlet request | `Result<String>` |
| POST | `/admin/employee` | `token` | JSON `EmployeeDTO` | `Result` |
| GET | `/admin/employee/page` | `token` | query `EmployeePageQueryDTO` | `Result<PageResult>` |
| POST | `/admin/employee/status/{status}` | `token` | path `status: Integer`; query `id: Long` | `Result` |
| GET | `/admin/employee/{id}` | `token` | path `id: Long` | `Result<Employee>` |
| PUT | `/admin/employee` | `token` | JSON `EmployeeDTO` | `Result` |
| POST | `/admin/employee/editPassword` | `token` | JSON `PasswordEditDTO` | `Result` |
| POST | `/admin/memo` | `token` | JSON `MemoDTO` | `Result<MemoVO>` |
| PUT | `/admin/memo` | `token` | JSON `MemoDTO` | `Result<String>` |
| PUT | `/admin/memo/{id}/status` | `token` | path `id: Long`; query `status: Integer` | `Result<String>` |
| DELETE | `/admin/memo/{id}` | `token` | path `id: Long` | `Result<String>` |
| GET | `/admin/memo/{id}` | `token` | path `id: Long` | `Result<MemoVO>` |
| GET | `/admin/memo/page` | `token` | query `MemoPageQueryDTO` | `Result<PageResult>` |
| POST | `/admin/memo/parse` | `token` | JSON string content | `Result<MemoParseResultVO>` |
| GET | `/admin/memo/stats` | `token` | none | `Result<MemoService.MemoStatsVO>` |
| PUT | `/admin/memo/{id}/complete` | `token` | path `id: Long` | `Result<String>` |
| PUT | `/admin/memo/{id}/cancel` | `token` | path `id: Long` | `Result<String>` |
| any | `/admin/order/conditionSearch` | `token` | query `OrdersPageQueryDTO` | `Result<PageResult>` |
| any | `/admin/order/statistics` | `token` | none | `Result<OrderStatisticsVO>` |
| GET | `/admin/order/details/{id}` | `token` | path `id: Long` | `Result<OrderVO>` |
| PUT | `/admin/order/confirm` | `token` | JSON `OrdersConfirmDTO` | `Result` |
| PUT | `/admin/order/rejection` | `token` | JSON `OrdersRejectionDTO` | `Result` |
| PUT | `/admin/order/cancel` | `token` | JSON `OrdersCancelDTO` | `Result` |
| POST | `/admin/phoneModel` | `token` | JSON `PhoneModelDTO` | `Result` |
| GET | `/admin/phoneModel/page` | `token` | query `PhoneModelPageQueryDTO` | `Result<PageResult>` |
| GET | `/admin/phoneModel/{id}` | `token` | path `id: Long` | `Result<PhoneModel>` |
| PUT | `/admin/phoneModel` | `token` | JSON `PhoneModelDTO` | `Result` |
| DELETE | `/admin/phoneModel` | `token` | query `ids: List<Long>` | `Result` |
| POST | `/admin/phoneModel/status/{status}` | `token` | path `status: Integer`; query `id: Long` | `Result` |
| GET | `/admin/phoneModel/list` | `token` | none | `Result<List<PhoneModel>>` |
| POST | `/admin/product` | `token` | JSON `ProductDTO` | `Result<Long>` |
| GET | `/admin/product/page` | `token` | query `ProductPageQueryDTO` | `Result<PageResult>` |
| DELETE | `/admin/product` | `token` | query `ids: List<Long>` | `Result` |
| GET | `/admin/product/{id}` | `token` | path `id: Long` | `Result<ProductVO>` |
| GET | `/admin/product/list` | `token` | query `categoryId: Long` | `Result<List<Product>>` |
| POST | `/admin/product/status/{status}` | `token` | path `status: Integer`; query `id: Long` | `Result` |
| PUT | `/admin/product` | `token` | JSON `ProductDTO` | `Result` |
| POST | `/admin/productLocation` | `token` | JSON `ProductLocationDTO` | `Result` |
| GET | `/admin/productLocation/{productId}` | `token` | path `productId: Long` | `Result<ProductLocation>` |
| GET | `/admin/productLocation/list` | `token` | optional query `shelfCode: String` | `Result<List<ProductLocationVO>>` |
| DELETE | `/admin/productLocation/{productId}` | `token` | path `productId: Long` | `Result` |
| GET | `/admin/report/overview` | `token` | none | `Result<OverviewVO>` |
| GET | `/admin/report/salesRanking` | `token` | optional queries `begin: LocalDate`, `end: LocalDate`, `limit: Integer` (default 10) | `Result<SalesRankingVO>` |
| GET | `/admin/report/turnover` | `token` | optional queries `begin: LocalDate`, `end: LocalDate` | `Result<TurnoverStatisticsVO>` |
| GET | `/admin/report/orderStatistics` | `token` | optional queries `begin: LocalDate`, `end: LocalDate` | `Result<OrderStatisticsReportVO>` |
| PUT | `/admin/shop/{status}` | `token` | path `status: Integer` | `Result` |
| GET | `/admin/shop/status` | `token` | none | `Result<Integer>` |
| PUT | `/admin/stock/adjust` | `token` | JSON `StockAdjustDTO` | `Result` |
| GET | `/admin/stock/logs` | `token` | query `StockLogPageQueryDTO` | `Result<PageResult>` |
| GET | `/admin/stock/alerts` | `token` | query `StockAlertPageQueryDTO` | `Result<PageResult>` |
| PUT | `/admin/stock/alerts/{id}` | `token` | path `id: Long`; query `status: Integer` | `Result` |
| POST | `/admin/stock/check/plan` | `token` | JSON `StockCheckPlan` | `Result` |
| GET | `/admin/stock/check/plans` | `token` | none | `Result<List<StockCheckPlan>>` |
| PUT | `/admin/stock/check/plan/{id}/complete` | `token` | path `id: Long` | `Result` |
| POST | `/admin/stock/check/record` | `token` | JSON `StockCheckRecordDTO` | `Result` |
| GET | `/admin/stock/check/records/{planId}` | `token` | path `planId: Long` | `Result<List<StockCheckRecord>>` |
| PUT | `/admin/stock/check/record/{id}/confirm` | `token` | path `id: Long` | `Result` |
| POST | `/admin/common/upload` | `token` | multipart `file` | `Result` |

## User controllers

| HTTP method | Path | Auth header | Request type | Success response type |
| --- | --- | --- | --- | --- |
| GET | `/user/category/list` | `authentication` | none | `Result<List<Category>>` |
| POST | `/user/memo` | `authentication` | JSON `MemoDTO` | `Result<MemoVO>` |
| PUT | `/user/memo` | `authentication` | JSON `MemoDTO` | `Result<String>` |
| PUT | `/user/memo/{id}/status` | `authentication` | path `id: Long`; query `status: Integer` | `Result<String>` |
| DELETE | `/user/memo/{id}` | `authentication` | path `id: Long` | `Result<String>` |
| GET | `/user/memo/{id}` | `authentication` | path `id: Long` | `Result<MemoVO>` |
| GET | `/user/memo/page` | `authentication` | query `MemoPageQueryDTO` | `Result<PageResult>` |
| POST | `/user/memo/parse` | `authentication` | JSON string content | `Result<MemoParseResultVO>` |
| GET | `/user/memo/stats` | `authentication` | none | `Result<MemoService.MemoStatsVO>` |
| PUT | `/user/memo/{id}/complete` | `authentication` | path `id: Long` | `Result<String>` |
| PUT | `/user/memo/{id}/cancel` | `authentication` | path `id: Long` | `Result<String>` |
| POST | `/user/order/submit` | `authentication` | JSON `OrdersSubmitDTO` | `Result<OrderSubmitVO>` |
| PUT | `/user/order/payment` | `authentication` | JSON `OrdersPaymentDTO` | `Result<OrderPaymentVO>` |
| GET | `/user/order/historyOrders` | `authentication` | query `OrdersPageQueryDTO` | `Result<PageResult>` |
| GET | `/user/order/orderDetail/{id}` | `authentication` | path `id: Long` | `Result<OrderVO>` |
| PUT | `/user/order/cancel/{id}` | `authentication` | path `id: Long` | `Result` |
| POST | `/user/order/repetition/{id}` | `authentication` | path `id: Long` | `Result` |
| GET | `/user/phoneModel/list` | `authentication` | none | `Result<List<PhoneModel>>` |
| GET | `/user/phoneModel/listByBrand` | `authentication` | query `brand: String` | `Result<List<PhoneModel>>` |
| GET | `/user/product/list` | `authentication` | query `categoryId: Long` | `Result<List<ProductVO>>` |
| GET | `/user/product/listByPhoneModel` | `authentication` | query `phoneModelId: Long` | `Result<List<ProductVO>>` |
| GET | `/user/product/search` | `authentication` | query `keyword: String` | `Result<List<ProductVO>>` |
| GET | `/user/product/location/{productId}` | `authentication` | path `productId: Long` | `Result<ProductLocation>` |
| GET | `/user/shop/status` | none | none | `Result<Integer>` |
| POST | `/user/shoppingCart/add` | `authentication` | JSON `ShoppingCartDTO` | `Result` |
| GET | `/user/shoppingCart/list` | `authentication` | none | `Result<List<ShoppingCart>>` |
| DELETE | `/user/shoppingCart/clean` | `authentication` | none | `Result` |
| POST | `/user/shoppingCart/sub` | `authentication` | JSON `ShoppingCartDTO` | `Result` |
| POST | `/user/user/login` | none | JSON `UserLoginDTO` | `Result<UserLoginVO>` |
| POST | `/user/user/logout` | `authentication` | servlet request | `Result<String>` |

## Payment notification controller

| HTTP method | Path | Auth header | Request type | Success response type |
| --- | --- | --- | --- | --- |
| any | `/notify/paySuccess` | `Wechatpay-Timestamp`, `Wechatpay-Nonce`, `Wechatpay-Signature`, `Wechatpay-Serial` | servlet request body | `void` (writes servlet response) |
| any | `/notify/refundSuccess` | `Wechatpay-Timestamp`, `Wechatpay-Nonce`, `Wechatpay-Signature`, `Wechatpay-Serial` | servlet request body | `void` (writes servlet response) |
