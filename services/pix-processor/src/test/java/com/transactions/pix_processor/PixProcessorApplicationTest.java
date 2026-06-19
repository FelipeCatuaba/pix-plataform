package com.transactions.pix_processor;

import static org.mockito.Mockito.mockStatic;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class PixProcessorApplicationTest {

    @Test
    void shouldDelegateStartupToSpringApplication() {
        // arrange
        String[] args = {"--spring.main.web-application-type=none"};
        PixProcessorApplication application = new PixProcessorApplication();

        // act
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            PixProcessorApplication.main(args);

            // assert
            assertEquals(PixProcessorApplication.class, application.getClass());
            springApplication.verify(() -> SpringApplication.run(PixProcessorApplication.class, args));
        }
    }
}
