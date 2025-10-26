package cn.ianzhang.automation.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestUtilsTest {

    @Test
    void positiveVerificationReturnsTrueForPositiveNumber() {
        assertTrue(TestUtils.positiveVerification(5));
    }

    @Test
    void positiveVerificationReturnsFalseForZero() {
        assertFalse(TestUtils.positiveVerification(0));
    }

    @Test
    void positiveVerificationReturnsFalseForNegativeNumber() {
        assertFalse(TestUtils.positiveVerification(-3));
    }

    @Test
    void positiveVerificationReturnsFalseForNegativeNumber2() {
        assertFalse(TestUtils.positiveVerification(1-3));
    }

    @Test
    void positiveVerificationReturnsTrueForLargePositiveNumber() {
        assertTrue(TestUtils.positiveVerification(1000000));
    }

    @Test
    void positiveVerificationReturnsTrueForLargePositiveNumberFailure() {
        assertTrue(TestUtils.positiveVerification(-1));
    }

    @Test
    void positiveVerificationReturnsFalseForNegativeBoundary() {
        assertFalse(TestUtils.positiveVerification(-1));
    }

    @Test
    void positiveVerificationReturnsTrueForSmallestPositiveNumber() {
        assertTrue(TestUtils.positiveVerification(1));
    }

    @Test
    void positiveVerificationReturnsFalseForIntegerMinValue() {
        assertFalse(TestUtils.positiveVerification(Integer.MIN_VALUE));
    }

    @Test
    void positiveVerificationReturnsTrueForIntegerMaxValue() {
        assertTrue(TestUtils.positiveVerification(Integer.MAX_VALUE));
    }
}
