package com.sky.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Map;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.sky", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    private static final Set<String> MODULES = Set.of(
            "order", "payment", "inventory", "auth", "product", "notification");

    private static final Map<String, String> OWNERS_BY_SIMPLE_NAME = Map.ofEntries(
            Map.entry("OrderController", "order"),
            Map.entry("OrderApplicationService", "order"),
            Map.entry("OrderService", "order"),
            Map.entry("OrderServiceImpl", "order"),
            Map.entry("OrderMapper", "order"),
            Map.entry("OrderDetailMapper", "order"),
            Map.entry("IdSegmentService", "order"),
            Map.entry("IdSegmentServiceImpl", "order"),
            Map.entry("IdSegmentMapper", "order"),
            Map.entry("OrderNumberGenerator", "order"),
            Map.entry("OrderTask", "order"),
            Map.entry("OrderEventPublisher", "order"),
            Map.entry("OrderPaidMessage", "order"),
            Map.entry("OrderTimeoutMessage", "order"),
            Map.entry("StockController", "inventory"),
            Map.entry("InventoryService", "inventory"),
            Map.entry("StockService", "inventory"),
            Map.entry("StockServiceImpl", "inventory"),
            Map.entry("StockLogMapper", "inventory"),
            Map.entry("StockAlertMapper", "inventory"),
            Map.entry("StockCheckPlanMapper", "inventory"),
            Map.entry("StockCheckRecordMapper", "inventory"),
            Map.entry("StockAlertTask", "inventory"),
            Map.entry("InventoryProductMapper", "inventory"),
            Map.entry("EmployeeController", "auth"),
            Map.entry("UserController", "auth"),
            Map.entry("EmployeeService", "auth"),
            Map.entry("UserService", "auth"),
            Map.entry("TokenSessionService", "auth"),
            Map.entry("TokenSessionServiceImpl", "auth"),
            Map.entry("LoginAttemptService", "auth"),
            Map.entry("EmployeeServiceImpl", "auth"),
            Map.entry("UserServiceImpl", "auth"),
            Map.entry("LoginAttemptServiceImpl", "auth"),
            Map.entry("EmployeeMapper", "auth"),
            Map.entry("UserMapper", "auth"),
            Map.entry("CategoryController", "product"),
            Map.entry("ProductController", "product"),
            Map.entry("ProductLocationController", "product"),
            Map.entry("PhoneModelController", "product"),
            Map.entry("ShoppingCartController", "product"),
            Map.entry("CategoryService", "product"),
            Map.entry("ProductService", "product"),
            Map.entry("ProductLocationService", "product"),
            Map.entry("PhoneModelService", "product"),
            Map.entry("ShoppingCartService", "product"),
            Map.entry("CategoryServiceImpl", "product"),
            Map.entry("ProductServiceImpl", "product"),
            Map.entry("ProductLocationServiceImpl", "product"),
            Map.entry("PhoneModelServiceImpl", "product"),
            Map.entry("ShoppingCartServiceImpl", "product"),
            Map.entry("CategoryMapper", "product"),
            Map.entry("ProductMapper", "product"),
            Map.entry("ProductLocationMapper", "product"),
            Map.entry("PhoneModelMapper", "product"),
            Map.entry("ProductSpecMapper", "product"),
            Map.entry("ProductPhoneModelMapper", "product"),
            Map.entry("ShoppingCartMapper", "product"),
            Map.entry("PayNotifyController", "payment"),
            Map.entry("PaymentCallbackLogService", "payment"),
            Map.entry("PaymentCallbackLogMapper", "payment"),
            Map.entry("OrderEventConsumer", "notification"),
            Map.entry("OrderNotificationPort", "notification"),
            Map.entry("WebSocketServer", "notification"));

    @ArchTest
    static final ArchRule controllersMustNotDependOnMappers = classes()
            .that().resideInAPackage("..controller..")
            .should(notDependOnMappers());

    @ArchTest
    static final ArchRule declaredBusinessCodeMustLiveInItsOwningModule = classes()
            .should(liveInDeclaredOwningModule());

    @ArchTest
    static final ArchRule moduleClassesMustUseApiOrInternalPackages = classes()
            .that().resideInAnyPackage(
                    "com.sky.order..", "com.sky.payment..", "com.sky.inventory..",
                    "com.sky.auth..", "com.sky.product..", "com.sky.notification..")
            .should(useApiOrInternalPackage());

    @ArchTest
    static final ArchRule modulesMustNotDependOnOtherModuleInternals = classes()
            .that().resideInAPackage("com.sky..")
            .should(accessOtherModulesOnlyThroughApi());

    @ArchTest
    static final ArchRule legacyWireMessagesMustOnlyBeUsedByNotificationMessaging = noClasses()
            .that().resideOutsideOfPackages(
                    "com.sky.notification.internal.messaging", "com.sky.message")
            .should().dependOnClassesThat().resideInAPackage("com.sky.message");

    private static ArchCondition<JavaClass> notDependOnMappers() {
        return new ArchCondition<>("not depend directly on persistence mappers") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                javaClass.getDirectDependenciesFromSelf().stream()
                        .filter(dependency -> isMapperPackage(dependency.getTargetClass().getPackageName()))
                        .forEach(dependency -> events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription())));
            }
        };
    }

    private static ArchCondition<JavaClass> liveInDeclaredOwningModule() {
        return new ArchCondition<>("live in its declared business module") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String owner = OWNERS_BY_SIMPLE_NAME.get(javaClass.getSimpleName());
                if (owner != null
                        && !isLegacyWireCompatibilityClass(javaClass)
                        && !isInPackageTree(javaClass.getPackageName(), "com.sky." + owner)) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                            javaClass.getName() + " must be owned by com.sky." + owner));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> useApiOrInternalPackage() {
        return new ArchCondition<>("use an api or internal package below its module") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String module = moduleName(javaClass.getPackageName());
                if (module != null
                        && !isInPackageTree(javaClass.getPackageName(), "com.sky." + module + ".api")
                        && !isInPackageTree(javaClass.getPackageName(), "com.sky." + module + ".internal")) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                            javaClass.getName() + " must live below api or internal"));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> accessOtherModulesOnlyThroughApi() {
        return new ArchCondition<>("access another module only through its public api") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String originModule = moduleName(javaClass.getPackageName());
                javaClass.getDirectDependenciesFromSelf().stream()
                        .filter(dependency -> isNonApiDependencyOnOtherModule(originModule, dependency))
                        .forEach(dependency -> events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription())));
            }
        };
    }

    private static boolean isNonApiDependencyOnOtherModule(String originModule, Dependency dependency) {
        String targetPackage = dependency.getTargetClass().getPackageName();
        String targetModule = moduleName(targetPackage);
        return targetModule != null
                && !targetModule.equals(originModule)
                && !isInPackageTree(targetPackage, "com.sky." + targetModule + ".api");
    }

    private static String moduleName(String packageName) {
        String prefix = "com.sky.";
        if (!packageName.startsWith(prefix)) {
            return null;
        }
        int moduleEnd = packageName.indexOf('.', prefix.length());
        String candidate = moduleEnd == -1
                ? packageName.substring(prefix.length())
                : packageName.substring(prefix.length(), moduleEnd);
        return MODULES.contains(candidate) ? candidate : null;
    }

    private static boolean isMapperPackage(String packageName) {
        return isInPackageTree(packageName, "com.sky.mapper")
                || packageName.endsWith(".internal.persistence")
                || packageName.contains(".internal.persistence.");
    }

    private static boolean isLegacyWireCompatibilityClass(JavaClass javaClass) {
        // These exact pre-migration FQCNs must remain loadable until durable AMQP queues are drained.
        return javaClass.getName().equals("com.sky.message.OrderPaidMessage")
                || javaClass.getName().equals("com.sky.message.OrderTimeoutMessage");
    }

    private static boolean isInPackageTree(String packageName, String root) {
        return packageName.equals(root) || packageName.startsWith(root + ".");
    }
}
