package uk.gov.hmcts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import uk.gov.hmcts.payment.api.logging.Markers;

import javax.servlet.ServletContextListener;
import java.util.Arrays;

@EnableCaching
@EnableFeignClients
@EnableAsync
@SpringBootApplication
public class PaymentApiApplication {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentApiApplication.class);

    public static void main(String[] args) {
        try {
            SpringApplication.run(PaymentApiApplication.class, args);
        } catch (RuntimeException ex) {
            LOG.error(Markers.fatal, "Application crashed with error message: ", ex);
        }
    }

    @Bean
    ServletListenerRegistrationBean<ServletContextListener> myServletListener() {
        ServletListenerRegistrationBean<ServletContextListener> srb = new ServletListenerRegistrationBean<>();
        srb.setListener(new PaymentServletContextListener());
        return srb;
    }

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
            new ConcurrentMapCache("feesDtoMap")
        ));

        return cacheManager;
    }
}
