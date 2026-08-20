package com.rwg;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * Quy tắc kiến trúc bắt buộc (DECISIONS.md):
 * - Phân lớp api -> service -> repository: api KHÔNG được gọi thẳng repository.
 * - Controller nằm trong package ..api..; entity nằm trong ..domain..
 * - DTO đặt tên *Request/*Response (cho phép thêm *Event/*Payload cho payload WS sau này).
 * - CẤM float/double cho tiền tệ (money/wallet/game/bet).
 */
@AnalyzeClasses(packages = "com.rwg", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    @ArchTest
    static final ArchRule apiMustNotDependOnRepository =
            noClasses().that().resideInAPackage("..api..")
                    .should().dependOnClassesThat().resideInAPackage("..repository..")
                    .as("Tầng api không được gọi thẳng repository (phải qua service)");

    @ArchTest
    static final ArchRule serviceMustNotDependOnApi =
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAPackage("..api..")
                    .as("Tầng service không được phụ thuộc tầng api");

    @ArchTest
    static final ArchRule controllersResideInApiPackage =
            classes().that().areAnnotatedWith(RestController.class)
                    .should().resideInAPackage("..api..")
                    .as("Controller phải nằm trong package ..api..");

    @ArchTest
    static final ArchRule entitiesResideInDomainPackage =
            classes().that().areAnnotatedWith(Entity.class)
                    .should().resideInAPackage("..domain..")
                    .as("Entity JPA phải nằm trong package ..domain..");

    @ArchTest
    static final ArchRule dtosUseRequestResponseSuffix =
            classes().that().resideInAPackage("..dto..")
                    .should().haveSimpleNameEndingWith("Request")
                    .orShould().haveSimpleNameEndingWith("Response")
                    // Hậu tố cho payload WebSocket/realtime ở các bước sau.
                    .orShould().haveSimpleNameEndingWith("Event")
                    .orShould().haveSimpleNameEndingWith("Payload")
                    .as("DTO phải có hậu tố Request/Response/Event/Payload");

    @ArchTest
    static final ArchRule noFloatDoubleForMoney =
            noFields()
                    .that().areDeclaredInClassesThat().resideInAnyPackage(
                            "..money..", "..wallet..", "..game..", "..bet..")
                    .should().haveRawType(float.class)
                    .orShould().haveRawType(Float.class)
                    .orShould().haveRawType(double.class)
                    .orShould().haveRawType(Double.class)
                    .as("Tiền tệ cấm float/double - dùng BigDecimal (DECISIONS.md)");
}
