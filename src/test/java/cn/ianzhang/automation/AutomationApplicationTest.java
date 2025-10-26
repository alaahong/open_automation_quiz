package cn.ianzhang.automation;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AutomationApplicationTest {

    @Test
    void testMainMethod() {
        try (MockedStatic<SpringApplication> mockedSpringApplication = Mockito.mockStatic(SpringApplication.class)) {
            AutomationApplication.main(new String[]{});
            mockedSpringApplication.verify(() -> SpringApplication.run(AutomationApplication.class, new String[]{}));
        }
    }

    @Test
    void testMainMethodWithArgs() {
        try (MockedStatic<SpringApplication> mockedSpringApplication = Mockito.mockStatic(SpringApplication.class)) {
            String[] args = {"test"};
            AutomationApplication.main(args);
            mockedSpringApplication.verify(() -> SpringApplication.run(AutomationApplication.class, args));
        }
    }

    @Test
    void testClassInitialization() {
        assertDoesNotThrow(AutomationApplication::new);
    }

    @Test
    void testClassExists() {
        assertDoesNotThrow(() -> Class.forName("cn.ianzhang.automation.AutomationApplication"));
    }
}
