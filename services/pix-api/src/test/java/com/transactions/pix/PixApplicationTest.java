package com.transactions.pix;

import static org.mockito.Mockito.mockStatic;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class PixApplicationTest {

    @Test
    void shouldDelegateStartupToSpringApplication() {
        // arrange
        String[] args = {"--spring.main.web-application-type=none"};
        PixApplication application = new PixApplication();

        // act
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            PixApplication.main(args);

            // assert
            assertEquals(PixApplication.class, application.getClass());
            springApplication.verify(() -> SpringApplication.run(PixApplication.class, args));
        }
    }
}
