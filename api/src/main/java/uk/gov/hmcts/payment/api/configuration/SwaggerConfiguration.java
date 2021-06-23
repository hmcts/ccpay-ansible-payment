package uk.gov.hmcts.payment.api.configuration;

import com.google.common.base.Predicate;
import io.swagger.annotations.ApiParam;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import springfox.documentation.RequestHandler;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.ParameterBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Parameter;
import springfox.documentation.service.ResolvedMethodParameter;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.ParameterBuilderPlugin;
import springfox.documentation.spi.service.contexts.ParameterContext;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;
import uk.gov.hmcts.payment.api.controllers.PaymentExternalAPI;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.apache.commons.lang3.StringUtils.join;
import static org.apache.commons.lang3.StringUtils.splitByCharacterTypeCamelCase;

@Configuration
@EnableSwagger2
public class SwaggerConfiguration {

    private List<Parameter> getGlobalOperationParameters() {
        return Arrays.asList(
            new ParameterBuilder()
                .name("Authorization")
                .description("User authorization header")
                .required(true)
                .parameterType("header")
                .modelRef(new ModelRef("string"))
                .build(),
            new ParameterBuilder()
                .name("ServiceAuthorization")
                .description("Service authorization header")
                .required(true)
                .parameterType("header")
                .modelRef(new ModelRef("string"))
                .build());
    }

    @Bean
    public Docket newsApi() {
        return new Docket(DocumentationType.SWAGGER_2)
            .groupName("payment")
            .globalOperationParameters(getGlobalOperationParameters())
            .useDefaultResponseMessages(false)
            .apiInfo(apiInfo())
            .select()
            .apis(packagesLike("uk.gov.hmcts.payment.api.v1.controllers"))
            .paths(PathSelectors.any())
            .build();
    }

    @Bean
    public Docket Payment2Api() {
        return new Docket(DocumentationType.SWAGGER_2)
            .groupName("payment2")
            .globalOperationParameters(getGlobalOperationParameters())
            .useDefaultResponseMessages(false)
            .apiInfo(paymentApiInfo())
            .select()
            .apis(packagesLike("uk.gov.hmcts.payment.api.controllers"))
            .paths(PathSelectors.any())
            .build();
    }

    @Bean
    public Docket RefundApi() {
        return new Docket(DocumentationType.SWAGGER_2)
            .groupName("refund")
            .globalOperationParameters(getGlobalOperationParameters())
            .useDefaultResponseMessages(false)
            .apiInfo(paymentApiInfo())
            .select()
            .apis(packagesLike("uk.gov.hmcts.payment.api.refund.controller"))
            .paths(PathSelectors.any())
            .build();
    }

    @Bean
    public Docket paymentReferenceDataApi() {
        return new Docket(DocumentationType.SWAGGER_2)
            .groupName("reference-data")
            .useDefaultResponseMessages(false)
            .apiInfo(paymentReferenceDataApiInfo())
            .select()
            .apis(packagesLike("uk.gov.hmcts.payment.referencedata.controllers"))
            .paths(PathSelectors.any())
            .build();
    }

    @Bean
    public Docket externalApi() {
        return new Docket(DocumentationType.SWAGGER_2)
            .groupName("payment-external-api")
            .globalOperationParameters(Collections.singletonList(
                new ParameterBuilder()
                    .name("ServiceAuthorization")
                    .description("Service authorization header")
                    .required(true)
                    .parameterType("header")
                    .modelRef(new ModelRef("string"))
                    .build())
            )
            .useDefaultResponseMessages(false)
            .apiInfo(publicApiInfo())
            .select()
            .apis(RequestHandlerSelectors.withMethodAnnotation(PaymentExternalAPI.class))
            .paths(PathSelectors.any())
            .build();
    }

    private static Predicate<RequestHandler> packagesLike(final String pkg) {
        return input -> input.declaringClass().getPackage().getName().equals(pkg);
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
            .title("PaymentOld API documentation")
            .description("PaymentOld API documentation")
            .build();
    }

    private ApiInfo paymentApiInfo() {
        return new ApiInfoBuilder()
            .title("Payment API v2 documentation")
            .description("Payment API v2 documentation")
            .build();
    }

    private ApiInfo paymentReferenceDataApiInfo() {
        return new ApiInfoBuilder()
            .title("Payment Reference Data documentation")
            .description("Payment Reference Data documentation")
            .build();
    }

    private ApiInfo publicApiInfo() {
        return new ApiInfoBuilder()
            .title("Payment public API documentation")
            .description("Payment API exposed to external systems")
            .build();
    }

    @Component
    public static class CamelCaseToSpacesDocumentationPlugin implements ParameterBuilderPlugin {

        @Override
        public void apply(ParameterContext context) {
            ResolvedMethodParameter methodParameter = context.resolvedMethodParameter();

            if (methodParameter.hasParameterAnnotation(ApiParam.class)) {
                return;
            }

            String description = capitalize(join(splitByCharacterTypeCamelCase(methodParameter.defaultName().get()), ' '));
            context.parameterBuilder().description(description);
        }

        @Override
        public boolean supports(DocumentationType delimiter) {
            return DocumentationType.SWAGGER_2.equals(delimiter);
        }
    }
}
