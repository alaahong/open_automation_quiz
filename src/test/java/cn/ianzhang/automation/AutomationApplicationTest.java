package cn.ianzhang.automation;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

class AutomationApplicationTest {
    @Test
    void testMainMethod() {
        try (MockedStatic<SpringApplication> mockedSpringApplication = Mockito.mockStatic(SpringApplication.class)) {
            AutomationApplication.main(new String[]{});
            mockedSpringApplication.verify(() -> SpringApplication.run(AutomationApplication.class, new String[]{}));
        }
    }
}

